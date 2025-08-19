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

/**
 * Foreground service for location tracking.
 * This service runs in the foreground to ensure continuous location updates
 * and integrates with the MVC architecture by interacting with the [LocationController].
 */
class LocationService : Service() {

    companion object {
        // Tag for logging messages from LocationService.
        private const val TAG = Constants.Logs.TAG_SERVICE
        // Unique ID for the foreground notification.
        const val NOTIFICATION_ID = Constants.NOTIFICATION_ID
        // ID for the notification channel.
        const val CHANNEL_ID = Constants.NOTIFICATION_CHANNEL_ID
    }

    // Coroutine scope for operations within this service.
    // Uses Dispatchers.Main for UI-related tasks (like updating notification) and SupervisorJob for fault tolerance.
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var locationController: LocationController? = null // Reference to the LocationController.
    private var trackingJob: Job? = null // Job for the location tracking coroutine.

    /**
     * Called when the service is first created.
     * Initializes the notification channel and the LocationController.
     */
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LocationService created")

        // Create the notification channel, essential for Android Oreo (API 26) and above.
        createNotificationChannel()

        // Initialize the LocationController, passing the service context.
        locationController = LocationController(this)

        // Start observing changes in the application's state to update the notification.
        observeAppState()
    }

    /**
     * Called by the system every time a client starts the service using `startService(Intent)`.
     * This is where the service handles incoming commands based on the intent's action.
     * @param intent The Intent supplied to `startService(Intent)`, as given by the client.
     * @param flags Additional data about this start request.
     * @param startId A unique integer representing this specific request to start.
     * @return The return value indicates how the system should handle the service if it's killed.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "LocationService onStartCommand: ${intent?.action}")

        when (intent?.action) {
            // Action to start location tracking.
            Constants.ServiceActions.START_TRACKING -> {
                startLocationTracking()
            }
            // Action to stop location tracking.
            Constants.ServiceActions.STOP_TRACKING -> {
                stopLocationTracking()
                stopSelf() // Stop the service itself after stopping tracking.
            }
            // Handle any unknown or unhandled actions.
            else -> {
                Log.w(TAG, "Received unknown action: ${intent?.action}")
            }
        }

        // START_STICKY: If the service is killed by the system, it will be re-created
        // but the last intent will not be re-delivered. Useful for services that
        // are explicitly started and stopped as needed.
        return START_STICKY
    }

    /**
     * Called when a client is attempting to bind to the service.
     * This service is not designed for binding, so it returns null.
     * @param intent The Intent that was used to bind to this service.
     * @return An IBinder object, or null if clients cannot bind to this service.
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Called when the service is no longer used and is being destroyed.
     * This is the final cleanup step for the service.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "LocationService destroyed")

        // Clean up resources to prevent leaks.
        stopLocationTracking() // Ensure tracking is stopped.
        serviceScope.cancel() // Cancel all coroutines launched in this service's scope.
        locationController?.cleanup() // Clean up the LocationController's resources.
        locationController = null // Nullify the controller reference.
    }

    /**
     * Observes changes in the application state from the [LocationController].
     * This allows the service to dynamically update its foreground notification
     * based on tracking status and location data.
     */
    private fun observeAppState() {
        serviceScope.launch {
            // Collect updates from the appState Flow provided by LocationController.
            locationController?.appState?.collect { appState ->
                // Create or update the foreground notification with the current status.
                val notification = createNotification(
                    when {
                        // If tracking is enabled and a current location is available, show coordinates.
                        appState.isTrackingEnabled && appState.currentLocation != null -> {
                            "Active - Last: ${appState.currentLocation.getFormattedCoordinates()}"
                        }
                        // If tracking is enabled but no location yet, indicate waiting for GPS.
                        appState.isTrackingEnabled -> {
                            "Active - Waiting for GPS..."
                        }
                        // If tracking is not enabled, show a stopped status.
                        else -> {
                            "Stopped"
                        }
                    }
                )

                // Get NotificationManager and update the existing notification.
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    /**
     * Initiates location tracking by starting the service in the foreground
     * and instructing the [LocationController] to begin updates.
     */
    private fun startLocationTracking() {
        Log.d(TAG, "Starting location tracking in service.")

        // Start the service in the foreground immediately with an initial notification.
        // This makes the service less likely to be killed by the system.
        val initialNotification = createNotification("Starting location tracking...")
        startForeground(NOTIFICATION_ID, initialNotification)

        // Launch a coroutine to handle the tracking logic.
        trackingJob = serviceScope.launch {
            try {
                locationController?.let { controller ->
                    // First, check for necessary permissions.
                    controller.checkPermissions()

                    // Then, tell the controller to start location tracking.
                    val result = controller.startLocationTracking()

                    result.fold(
                        onSuccess = { message ->
                            Log.d(TAG, "Location tracking started successfully: $message")
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to start location tracking in service: ${error.message}")
                            // If tracking fails to start (e.g., due to missing network), stop the service.
                            stopSelf()
                        }
                    )
                }
            } catch (e: Exception) {
                // Catch any unexpected exceptions during the tracking initiation.
                Log.e(TAG, "Error encountered during location tracking initiation within service.", e)
                handleServiceError(e) // Handle the error and potentially stop the service.
            }
        }
    }

    /**
     * Stops location tracking, canceling any active tracking jobs
     * and instructing the [LocationController] to cease updates.
     */
    private fun stopLocationTracking() {
        Log.d(TAG, "Stopping location tracking in service.")

        // Cancel the coroutine job responsible for tracking to stop emissions.
        trackingJob?.cancel()
        trackingJob = null // Clear the job reference.

        locationController?.let { controller ->
            // Tell the controller to stop location updates.
            val result = controller.stopLocationTracking()
            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Location tracking stopped successfully: $message")
                },
                onFailure = { error ->
                    Log.e(TAG, "Error stopping location tracking in service: ${error.message}")
                }
            )
        }
    }

    /**
     * Creates and configures the notification channel for the foreground service.
     * This is crucial for Android Oreo (API 26) and newer versions, as foreground services
     * require an associated notification channel.
     */
    private fun createNotificationChannel() {
        // Only create channel on Android O and above.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, // Unique ID for the channel.
                Constants.NOTIFICATION_CHANNEL_NAME, // User-visible name of the channel.
                NotificationManager.IMPORTANCE_LOW // Importance level (low for ongoing background tasks).
            ).apply {
                description = "Notification channel for Juls location tracking service"
                setSound(null, null) // No sound for this notification.
                enableVibration(false) // No vibration.
                setShowBadge(false) // Do not show notification badge on app icon.
            }

            // Get NotificationManager and create the channel.
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)

            Log.d(TAG, "Notification channel created.")
        }
    }

    /**
     * Creates a [Notification] object for the foreground service.
     * This notification is visible to the user and indicates that the service is running.
     * It includes actions to open the app or stop the service.
     * @param contentText The dynamic text to display in the notification's content area.
     * @return The configured [Notification] object.
     */
    private fun createNotification(contentText: String): Notification {
        // Intent to launch MainActivity when the notification is tapped.
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            // Clear existing task and start new task for consistent navigation.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        // PendingIntent for the notification tap action.
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // Use FLAG_IMMUTABLE for security.
        )

        // Intent to stop the service when the "Stop" action button is tapped.
        val stopIntent = Intent(this, LocationService::class.java).apply {
            action = Constants.ServiceActions.STOP_TRACKING
        }
        // PendingIntent for the "Stop" action button.
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification using NotificationCompat.Builder for backward compatibility.
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Juls - Location Tracking") // Title of the notification.
            .setContentText(contentText) // Dynamic content text (e.g., last location).
            .setSmallIcon(R.drawable.logo_dark) // Small icon displayed in the status bar (using existing dark logo).
            .setContentIntent(pendingIntent) // Set the intent when notification is tapped.
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel, // Icon for the action button.
                "Stop", // Text for the action button.
                stopPendingIntent // PendingIntent to execute when the button is tapped.
            )
            .setOngoing(true) // Makes the notification non-dismissable by the user.
            .setSilent(true) // No sound or vibration for this notification.
            .setAutoCancel(false) // Notification persists until explicitly dismissed or service stops.
            .setCategory(NotificationCompat.CATEGORY_SERVICE) // Categorize as a service notification.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible on lock screen.
            .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority to be less intrusive.
            .build()
    }

    /**
     * Handles service-level errors, typically by logging them, creating an error notification,
     * and stopping the service to prevent further issues.
     * @param error The [Throwable] representing the error that occurred.
     */
    private fun handleServiceError(error: Throwable) {
        Log.e(TAG, "Service error occurred, attempting to recover.", error)

        // Create a separate, high-priority error notification.
        val errorNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Juls - Service Error")
            .setContentText("Location tracking stopped due to an unexpected error.")
            .setSmallIcon(R.drawable.logo_dark) // Using the existing dark logo for consistency.
            .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority to alert the user.
            .setAutoCancel(true) // Allows the user to dismiss the error notification.
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Notify with a different ID to display alongside the main tracking notification.
        notificationManager.notify(NOTIFICATION_ID + 1, errorNotification)

        // Stop the service to prevent continuous errors or resource consumption.
        stopSelf()
    }

    /**
     * Checks whether the service should continue running based on the app's state.
     * This is a utility function to determine if tracking conditions are still met.
     * @return `true` if tracking is enabled and all permissions are granted, `false` otherwise.
     */
    private fun shouldContinueService(): Boolean {
        val appState = locationController?.getCurrentAppState()
        // The service should continue if tracking is enabled and all permissions are held.
        return appState?.isTrackingEnabled == true && appState.hasAllPermissions()
    }

    /**
     * Handles changes in network connectivity.
     * If the network becomes connected, it triggers a server connection test.
     * @param isConnected `true` if the device is now connected to a network, `false` otherwise.
     */
    fun onNetworkChanged(isConnected: Boolean) {
        Log.d(TAG, "Network connectivity status changed: Connected = $isConnected")

        if (isConnected) {
            // If network is restored, attempt to test server connections.
            serviceScope.launch {
                locationController?.testServerConnections()
            }
        }
    }

    /**
     * Retrieves various statistics and status information about the service's operation.
     * @return A [Map] containing key-value pairs of service statistics.
     */
    fun getServiceStatistics(): Map<String, String> {
        val appState = locationController?.getCurrentAppState() // Get the current app state from the controller.

        return mapOf(
            "service_running" to "true", // Indicates that the service itself is active.
            "tracking_active" to (appState?.isTrackingEnabled?.toString() ?: "false"), // Whether location tracking is active.
            "current_location" to (appState?.currentLocation?.getFormattedCoordinates() ?: "None"), // Formatted last location.
            "session_duration" to (appState?.getSessionDurationFormatted() ?: "00:00:00"), // Formatted session duration.
            "total_locations_sent" to (appState?.totalLocationsSent?.toString() ?: "0") // Count of total locations sent.
        )
    }
}