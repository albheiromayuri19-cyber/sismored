package com.sismored.app.mesh

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import java.util.UUID

class SosMeshManager(
    context: Context,
    private val miIdDispositivo: String = UUID.randomUUID().toString().take(8)
) {
    private val SERVICE_ID = "com.sismored.SOS_MESH"
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val mensajesVistos = mutableSetOf<String>()
    private val vecinosConectados = mutableMapOf<String, String>()

    var onPaqueteRecibido: ((PaqueteSos) -> Unit)? = null
    var onVecinosActualizados: ((Int) -> Unit)? = null

    fun iniciarRed() {
        val opcionesAdvertising = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()
        connectionsClient.startAdvertising(
            miIdDispositivo, SERVICE_ID, connectionLifecycleCallback, opcionesAdvertising
        )

        val opcionesDiscovery = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, opcionesDiscovery)
    }

    fun detenerRed() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        vecinosConectados.clear()
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            connectionsClient.requestConnection(miIdDispositivo, endpointId, connectionLifecycleCallback)
        }
        override fun onEndpointLost(endpointId: String) {
            vecinosConectados.remove(endpointId)
            onVecinosActualizados?.invoke(vecinosConectados.size)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                vecinosConectados[endpointId] = endpointId
                onVecinosActualizados?.invoke(vecinosConectados.size)
            }
        }
        override fun onDisconnected(endpointId: String) {
            vecinosConectados.remove(endpointId)
            onVecinosActualizados?.invoke(vecinosConectados.size)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val paquete = PaqueteSos.desdeJson(String(bytes)) ?: return

            if (mensajesVistos.contains(paquete.idMensaje)) return
            mensajesVistos.add(paquete.idMensaje)
            onPaqueteRecibido?.invoke(paquete)

            if (paquete.ttlSaltos > 0) {
                val siguiente = paquete.copy(
                    ttlSaltos = paquete.ttlSaltos - 1,
                    ruta = paquete.ruta + miIdDispositivo
                )
                retransmitir(siguiente, excluirEndpoint = endpointId)
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    fun enviarSos(lat: Double, lon: Double, tipo: String = "sos") {
        val paquete = PaqueteSos(
            idMensaje = UUID.randomUUID().toString(),
            idOrigen = miIdDispositivo,
            tipo = tipo,
            lat = lat,
            lon = lon,
            timestamp = System.currentTimeMillis(),
            ttlSaltos = 6,
            ruta = listOf(miIdDispositivo)
        )
        mensajesVistos.add(paquete.idMensaje)
        retransmitir(paquete, excluirEndpoint = null)
    }

    private fun retransmitir(paquete: PaqueteSos, excluirEndpoint: String?) {
        val datos = Payload.fromBytes(paquete.aJson().toByteArray())
        vecinosConectados.keys
            .filter { it != excluirEndpoint }
            .forEach { endpointId -> connectionsClient.sendPayload(endpointId, datos) }
    }

    fun numeroDeVecinos(): Int = vecinosConectados.size
}

data class PaqueteSos(
    val idMensaje: String,
    val idOrigen: String,
    val tipo: String,
    val lat: Double,
    val lon: Double,
    val timestamp: Long,
    val ttlSaltos: Int,
    val ruta: List<String>
) {
    fun aJson(): String = JSONObject().apply {
        put("idMensaje", idMensaje)
        put("idOrigen", idOrigen)
        put("tipo", tipo)
        put("lat", lat)
        put("lon", lon)
        put("timestamp", timestamp)
        put("ttlSaltos", ttlSaltos)
        put("ruta", ruta.joinToString(","))
    }.toString()

    companion object {
        fun desdeJson(json: String): PaqueteSos? = try {
            val o = JSONObject(json)
            PaqueteSos(
                idMensaje = o.getString("idMensaje"),
                idOrigen = o.getString("idOrigen"),
                tipo = o.getString("tipo"),
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
                timestamp = o.getLong("timestamp"),
                ttlSaltos = o.getInt("ttlSaltos"),
                ruta = o.getString("ruta").split(",").filter { it.isNotBlank() }
            )
        } catch (e: Exception) {
            null
        }
    }
}
