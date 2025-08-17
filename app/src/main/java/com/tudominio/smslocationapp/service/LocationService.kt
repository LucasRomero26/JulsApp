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
import com.tudominio.smslocation.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * Servicio en primer plano para tracking de ubicación
 * Integrado con la arquitectura MVC
 */
class LocationService : Service() {

    companion object {
        private const val TAG = Constants.Logs.TAG_SERVICE
        const val NOTIFICATION_ID = Constants.NOTIFICATION_ID
        const val CHANNEL_ID = Constants.NOTIFICATION_CHANNEL_ID
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var locationController: LocationController? = null
    private var trackingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LocationService created")

        // Crear canal de notificación
        createNotificationChannel()

        // Inicializar controlador
        locationController = LocationController(this)

        // Observar cambios en el estado de la aplicación
        observeAppState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "LocationService onStartCommand: ${intent?.action}")

        when (intent?.action) {
            Constants.ServiceActions.START_TRACKING -> {
                startLocationTracking()
            }
            Constants.ServiceActions.STOP_TRACKING -> {
                stopLocationTracking()
                stopSelf()
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
            }
        }

        return START_STICKY // Reiniciar servicio si es terminado por el sistema
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "LocationService destroyed")

        // Limpiar recursos
        stopLocationTracking()
        serviceScope.cancel()
        locationController?.cleanup()
        locationController = null
    }

    /**
     * Observar cambios en el estado de la aplicación
     */
    private fun observeAppState() {
        serviceScope.launch {
            locationController?.appState?.collect { appState ->
                // Actualizar notificación con el estado actual
                val notification = createNotification(
                    when {
                        appState.isTrackingEnabled && appState.currentLocation != null -> {
                            "Active - Last: ${appState.currentLocation.getFormattedCoordinates()}"
                        }
                        appState.isTrackingEnabled -> {
                            "Active - Waiting for GPS..."
                        }
                        else -> {
                            "Stopped"
                        }
                    }
                )

                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    /**
     * Iniciar tracking de ubicación
     */
    private fun startLocationTracking() {
        Log.d(TAG, "Starting location tracking in service")

        // Iniciar servicio en primer plano
        val notification = createNotification("Starting location tracking...")
        startForeground(NOTIFICATION_ID, notification)

        trackingJob = serviceScope.launch {
            try {
                locationController?.let { controller ->
                    // Verificar permisos
                    controller.checkPermissions()

                    // Iniciar tracking
                    val result = controller.startLocationTracking()

                    result.fold(
                        onSuccess = { message ->
                            Log.d(TAG, "Location tracking started successfully: $message")
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to start location tracking: ${error.message}")
                            // Detener servicio si no se puede iniciar tracking
                            stopSelf()
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in location tracking", e)
                stopSelf()
            }
        }
    }

    /**
     * Detener tracking de ubicación
     */
    private fun stopLocationTracking() {
        Log.d(TAG, "Stopping location tracking in service")

        trackingJob?.cancel()
        trackingJob = null

        locationController?.let { controller ->
            val result = controller.stopLocationTracking()
            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Location tracking stopped successfully: $message")
                },
                onFailure = { error ->
                    Log.e(TAG, "Error stopping location tracking: ${error.message}")
                }
            )
        }
    }

    /**
     * Crear canal de notificación
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification channel for Juls location tracking service"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)

            Log.d(TAG, "Notification channel created")
        }
    }

    /**
     * Crear notificación para el servicio en primer plano
     */
    private fun createNotification(contentText: String): Notification {
        // Intent para abrir la aplicación al tocar la notificación
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent para detener el servicio
        val stopIntent = Intent(this, LocationService::class.java).apply {
            action = Constants.ServiceActions.STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Juls - Location Tracking")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.location_icon)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.location_icon,
                "Stop",
                stopPendingIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Manejar errores del servicio
     */
    private fun handleServiceError(error: Throwable) {
        Log.e(TAG, "Service error occurred", error)

        // Crear notificación de error
        val errorNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Juls - Service Error")
            .setContentText("Location tracking stopped due to error")
            .setSmallIcon(R.drawable.location_icon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, errorNotification)

        // Detener servicio
        stopSelf()
    }

    /**
     * Verificar si el servicio debe continuar ejecutándose
     */
    private fun shouldContinueService(): Boolean {
        val appState = locationController?.getCurrentAppState()
        return appState?.isTrackingEnabled == true && appState.hasAllPermissions()
    }

    /**
     * Manejar cambios en la conectividad
     */
    fun onNetworkChanged(isConnected: Boolean) {
        Log.d(TAG, "Network connectivity changed: $isConnected")

        if (isConnected) {
            // Testear conexiones cuando se restaure la red
            serviceScope.launch {
                locationController?.testServerConnections()
            }
        }
    }

    /**
     * Obtener estadísticas del servicio
     */
    fun getServiceStatistics(): Map<String, String> {
        val appState = locationController?.getCurrentAppState()

        return mapOf(
            "service_running" to "true",
            "tracking_active" to (appState?.isTrackingEnabled?.toString() ?: "false"),
            "current_location" to (appState?.currentLocation?.getFormattedCoordinates() ?: "None"),
            "session_duration" to (appState?.getSessionDurationFormatted() ?: "00:00:00"),
            "total_locations_sent" to (appState?.totalLocationsSent?.toString() ?: "0")
        )
    }
}