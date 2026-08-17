package com.sismored.app.mesh

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SosForegroundService : Service() {

    companion object {
        const val CANAL_ID = "sismored_red_activa"
        const val NOTIF_ID = 1
    }

    lateinit var meshManager: SosMeshManager
        private set

    override fun onCreate() {
        super.onCreate()
        meshManager = SosMeshManager(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanalNotificacion()
        val notificacion = construirNotificacion(0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notificacion,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIF_ID, notificacion)
        }

        meshManager.onVecinosActualizados = { cantidad ->
            actualizarNotificacion(cantidad)
        }
        meshManager.iniciarRed()

        return START_STICKY
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_ID,
                "Red de emergencia activa",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canal)
        }
    }

    private fun construirNotificacion(vecinos: Int): Notification {
        return NotificationCompat.Builder(this, CANAL_ID)
            .setContentTitle("SismoRed activo")
            .setContentText("$vecinos dispositivos cerca en la red")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .build()
    }

    private fun actualizarNotificacion(vecinos: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, construirNotificacion(vecinos))
    }

    override fun onDestroy() {
        meshManager.detenerRed()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
