package com.sismored.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.sismored.app.mesh.SosForegroundService
import com.sismored.app.mesh.SosMeshManager
import com.sismored.app.ui.theme.*

class MainActivity : ComponentActivity() {

    private lateinit var meshManager: SosMeshManager
    private var ultimaUbicacion: Location? = null

    private val permisosNecesarios: Array<String>
        get() {
            val lista = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                lista += Manifest.permission.BLUETOOTH_SCAN
                lista += Manifest.permission.BLUETOOTH_ADVERTISE
                lista += Manifest.permission.BLUETOOTH_CONNECT
                lista += Manifest.permission.NEARBY_WIFI_DEVICES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lista += Manifest.permission.POST_NOTIFICATIONS
            }
            return lista.toTypedArray()
        }

    private val estadoPermisos = mutableStateOf(false)
    private val vecinosConectados = mutableStateOf(0)
    private val sosActivo = mutableStateOf(false)

    private val lanzadorPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultados ->
        val todosConcedidos = resultados.values.all { it }
        estadoPermisos.value = todosConcedidos
        if (todosConcedidos) iniciarRedYServicio()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        meshManager = SosMeshManager(applicationContext)
        meshManager.onVecinosActualizados = { cantidad -> vecinosConectados.value = cantidad }

        setContent {
            SismoRedTheme {
                PantallaPrincipal(
                    permisosConcedidos = estadoPermisos.value,
                    vecinos = vecinosConectados.value,
                    sosActivo = sosActivo.value,
                    onPedirPermisos = { lanzadorPermisos.launch(permisosNecesarios) },
                    onEnviarSos = { enviarSos() }
                )
            }
        }

        if (permisosYaConcedidos()) {
            estadoPermisos.value = true
            iniciarRedYServicio()
        }
    }

    private fun permisosYaConcedidos(): Boolean =
        permisosNecesarios.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun iniciarRedYServicio() {
        meshManager.iniciarRed()
        startForegroundService(Intent(this, SosForegroundService::class.java))
        obtenerUltimaUbicacion()
    }

    private fun obtenerUltimaUbicacion() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return
        LocationServices.getFusedLocationProviderClient(this).lastLocation
            .addOnSuccessListener { ubicacion -> ultimaUbicacion = ubicacion }
    }

    private fun enviarSos() {
        val lat = ultimaUbicacion?.latitude ?: 0.0
        val lon = ultimaUbicacion?.longitude ?: 0.0
        meshManager.enviarSos(lat, lon)
        sosActivo.value = true
    }

    override fun onDestroy() {
        meshManager.detenerRed()
        super.onDestroy()
    }
}

@Composable
fun PantallaPrincipal(
    permisosConcedidos: Boolean,
    vecinos: Int,
    sosActivo: Boolean,
    onPedirPermisos: () -> Unit,
    onEnviarSos: () -> Unit
) {
    Scaffold(containerColor = Fondo) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "SISMORED",
                color = Verde,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (permisosConcedidos) "Sin señal celular" else "Faltan permisos",
                color = Texto,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp
            )
            Spacer(Modifier.height(20.dp))

            if (!permisosConcedidos) {
                TarjetaPermisos(onPedirPermisos)
            } else {
                TarjetaEstado(vecinos)
                Spacer(Modifier.height(28.dp))
                BotonSos(sosActivo, onEnviarSos)
                if (sosActivo) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "SOS enviado a la red. Retransmitiendo hasta encontrar señal…",
                        color = TextoTenue,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaPermisos(onPedirPermisos: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Superficie, RoundedCornerShape(18.dp))
            .padding(20.dp)
    ) {
        Text(
            "SismoRed necesita permisos de ubicación y Bluetooth para formar la red de emergencia con otros teléfonos cercanos.",
            color = TextoTenue, fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onPedirPermisos,
            colors = ButtonDefaults.buttonColors(containerColor = Verde),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Activar red de emergencia", color = Fondo, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TarjetaEstado(vecinos: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (vecinos > 0) Color(0xFF1C3329) else Color(0xFF4A1E22), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (vecinos > 0) "Red activa — $vecinos dispositivos cerca" else "Buscando dispositivos cercanos…",
            color = Texto, fontSize = 13.sp
        )
    }
}

@Composable
private fun BotonSos(sosActivo: Boolean, onEnviarSos: () -> Unit) {
    Button(
        onClick = onEnviarSos,
        enabled = !sosActivo,
        colors = ButtonDefaults.buttonColors(containerColor = Rojo, disabledContainerColor = Rojo.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Text(
            if (sosActivo) "SOS ENVIADO" else "ENVIAR SOS",
            color = Texto,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}
