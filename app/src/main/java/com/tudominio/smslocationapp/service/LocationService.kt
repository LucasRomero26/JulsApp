package com.tudominio.smslocation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tudominio.smslocation.MainActivity
import com.tudominio.smslocation.R
import com.tudominio.smslocation.controller.LocationController
import com.tudominio.smslocation.streaming.WebRTCManager
import com.tudominio.smslocation.util.Constants
import kotlinx.coroutines.*

/**
 * Servicio que maneja tracking GPS y streaming de video
 */
class LocationService : Service() {

    companion object {
        private const val TAG = Constants.Logs.TAG_SERVICE
        const val NOTIFICATION_ID = Constants.NOTIFICATION_ID
        const val CHANNEL_ID = Constants.NOTIFICATION_CHANNEL_ID
    }

    private var locationController: LocationController? = null
    private var webRTCManager: WebRTCManager? = null // ✨ NUEVO
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isVideoStreaming = false // ✨ NUEVO

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LocationService created with video support")

        createNotificationChannel()
        locationController = LocationController(this)

        // ✨ NUEVO: Inicializar WebRTC Manager
        if (Constants.WEBRTC_ENABLED) {
            webRTCManager = WebRTCManager(this)
        }

        // Observar estado SOLO para actualizar notificación
        serviceScope.launch {
            locationController?.appState?.collect { appState ->
                val locationText = appState.currentLocation?.getFormattedCoordinates() ?: "Waiting..."
                val videoStatus = if (isVideoStreaming) "📹 Streaming" else "📹 Off"
                updateNotification("$locationText | $videoStatus")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service command: ${intent?.action}")

        when (intent?.action) {
            Constants.ServiceActions.START_TRACKING -> {
                startForeground(NOTIFICATION_ID, createNotification("Starting..."))

                serviceScope.launch {
                    try {
                        locationController?.startLocationTracking()

                        // ✨ NUEVO: Iniciar video automáticamente si está habilitado
                        if (Constants.WEBRTC_ENABLED) {
                            delay(2000) // Esperar 2 segundos después del GPS
                            startVideoStreaming()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting tracking", e)
                        stopSelf()
                    }
                }
            }

            // ✨ NUEVO: Iniciar solo video
            Constants.ServiceActions.START_VIDEO -> {
                serviceScope.launch {
                    startVideoStreaming()
                }
            }

            // ✨ NUEVO: Detener solo video
            Constants.ServiceActions.STOP_VIDEO -> {
                stopVideoStreaming()
            }

            Constants.ServiceActions.STOP_TRACKING -> {
                stopVideoStreaming() // ✨ Detener video primero
                locationController?.stopLocationTracking()
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")

        stopVideoStreaming() // ✨ Limpiar video
        locationController?.cleanup()
        serviceScope.cancel()
    }

    // ✨ NUEVO: Método para iniciar streaming de video
    private fun startVideoStreaming() {
        if (isVideoStreaming) {
            Log.w(TAG, "Video streaming already active")
            return
        }

        try {
            Log.d(TAG, "Starting video streaming...")
            webRTCManager?.initialize()
            isVideoStreaming = true
            Log.d(TAG, "✅ Video streaming started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting video streaming", e)
            isVideoStreaming = false
        }
    }

    // ✨ NUEVO: Método para detener streaming de video
    private fun stopVideoStreaming() {
        if (!isVideoStreaming) {
            return
        }

        try {
            Log.d(TAG, "Stopping video streaming...")
            webRTCManager?.cleanup()
            isVideoStreaming = false
            Log.d(TAG, "Video streaming stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping video streaming", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Juls Tracking + Video", // ✨ Actualizado
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationService::class.java).apply {
            action = Constants.ServiceActions.STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Juls Tracking + Video") // ✨ Actualizado
            .setContentText(content)
            .setSmallIcon(R.drawable.logo_dark)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}