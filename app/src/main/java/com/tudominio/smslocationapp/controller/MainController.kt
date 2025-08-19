package com.tudominio.smslocation.controller

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tudominio.smslocation.model.data.AppState
import com.tudominio.smslocation.service.LocationService
import com.tudominio.smslocation.util.Constants
import com.tudominio.smslocation.util.ThemePreferences
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The main application controller that coordinates all components.
 * It acts as the central control point for the UI and business logic.
 */
class MainController(private val context: Context) : ViewModel() {

    companion object {
        // Log tag for this class.
        private const val TAG = Constants.Logs.TAG_MAIN
    }

    // Instance of the LocationController for handling location and network logic.
    private val locationController = LocationController(context)

    // Theme preferences instance to manage UI themes.
    val themePreferences = ThemePreferences(context)

    // Exposes the application state as a read-only StateFlow.
    val appState: StateFlow<AppState> = locationController.appState

    init {
        Log.d(TAG, "MainController initialized")

        // Initial check for permissions when the controller is created.
        checkPermissions()
    }

    /**
     * Checks and updates the permissions status via the LocationController.
     */
    fun checkPermissions() {
        locationController.checkPermissions()
        Log.d(TAG, "Permissions checked")
    }

    /**
     * Handles the event when permissions are granted by the user.
     */
    fun onPermissionsGranted() {
        // Re-check permissions to update the app state.
        checkPermissions()

        val currentState = appState.value
        if (currentState.hasAllPermissions()) {
            Log.d(TAG, "All permissions granted")

            // Get current location to verify GPS availability and update UI.
            viewModelScope.launch {
                locationController.getCurrentAppState().let { state ->
                    if (state.hasAllPermissions()) {
                        locationController.getCurrentLocation()
                    }
                }
            }
        }
    }

    /**
     * Toggles the tracking state (starts or stops it).
     */
    fun toggleTracking() {
        viewModelScope.launch {
            val currentState = appState.value

            if (currentState.isTrackingEnabled) {
                stopTracking()
            } else {
                startTracking()
            }
        }
    }

    // The user requested to remove the `toggleTheme` function.

    // The user requested to remove the `setTheme` function.

    // The user requested to remove the `setSystemTheme` function.

    /**
     * Starts location tracking.
     */
    private suspend fun startTracking() {
        Log.d(TAG, "Starting tracking from MainController")

        // Check for permissions before attempting to start.
        if (!appState.value.hasAllPermissions()) {
            Log.w(TAG, "Cannot start tracking - missing permissions")
            return
        }

        try {
            // Start the foreground service that will handle location updates.
            startLocationService()

            // Initiate the tracking process in the LocationController.
            val result = locationController.startLocationTracking()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Tracking started successfully: $message")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to start tracking: ${error.message}")
                    // Stop the service if tracking fails to start to prevent a zombie service.
                    stopLocationService()
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error starting tracking", e)
        }
    }

    /**
     * Stops location tracking.
     */
    private fun stopTracking() {
        Log.d(TAG, "Stopping tracking from MainController")

        try {
            // Stop the tracking process in the LocationController.
            val result = locationController.stopLocationTracking()

            // Stop the foreground service.
            stopLocationService()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Tracking stopped successfully: $message")
                },
                onFailure = { error ->
                    Log.e(TAG, "Error stopping tracking: ${error.message}")
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tracking", e)
        }
    }

    /**
     * Starts the location service in the foreground.
     */
    private fun startLocationService() {
        val intent = Intent(context, LocationService::class.java).apply {
            action = Constants.ServiceActions.START_TRACKING
        }

        try {
            // Use startForegroundService for Android O (API 26) and above.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "Location service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location service", e)
        }
    }

    /**
     * Stops the location service.
     */
    private fun stopLocationService() {
        val intent = Intent(context, LocationService::class.java).apply {
            action = Constants.ServiceActions.STOP_TRACKING
        }

        try {
            context.startService(intent)
            Log.d(TAG, "Location service stop requested")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location service", e)
        }
    }

    /**
     * Gets the current location without starting continuous tracking.
     */
    fun getCurrentLocation() {
        viewModelScope.launch {
            val result = locationController.getCurrentLocation()

            result.fold(
                onSuccess = { location ->
                    Log.d(TAG, "Current location obtained: ${location.getFormattedCoordinates()}")
                },
                onFailure = { error ->
                    Log.w(TAG, "Failed to get current location: ${error.message}")
                }
            )
        }
    }

    /**
     * Tests connections to the configured servers.
     */
    fun testServerConnections() {
        viewModelScope.launch {
            val result = locationController.testServerConnections()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Server test successful: $message")
                },
                onFailure = { error ->
                    Log.w(TAG, "Server test failed: ${error.message}")
                }
            )
        }
    }

    /**
     * Sends test data to the servers.
     */
    fun sendTestData() {
        viewModelScope.launch {
            val result = locationController.sendTestData()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Test data sent successfully: $message")
                },
                onFailure = { error ->
                    Log.w(TAG, "Failed to send test data: ${error.message}")
                }
            )
        }
    }

    /**
     * Clears any status messages.
     */
    fun clearMessages() {
        locationController.clearMessages()
    }

    /**
     * Resets tracking statistics.
     */
    fun resetStatistics() {
        locationController.resetStatistics()
    }

    /**
     * Gets diagnostic information.
     * The user requested to remove any reference to `themePreferences`.
     */
    fun getDiagnosticInfo(): Map<String, String> {
        return locationController.getDiagnosticInfo() + mapOf(
            "app_version" to "1.0.0"
        )
    }

    /**
     * Checks if tracking can be started.
     */
    fun canStartTracking(): Boolean {
        return locationController.canStartTracking()
    }

    /**
     * Gets network information.
     */
    fun getNetworkInfo(): String {
        return locationController.getNetworkInfo()
    }

    /**
     * Processes any pending location updates.
     */
    fun processPendingLocations() {
        viewModelScope.launch {
            val result = locationController.flushPendingLocations()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Pending locations processed: $message")
                },
                onFailure = { error ->
                    Log.w(TAG, "Failed to process pending locations: ${error.message}")
                }
            )
        }
    }

    /**
     * Gets a summary of the application's current status.
     */
    fun getAppStatus(): String {
        val state = appState.value
        return state.getStatusSummary()
    }

    /**
     * Handles app lifecycle event when the app is paused.
     */
    fun onAppPaused() {
        Log.d(TAG, "App paused")
        // The app can continue running in the background, so we don't stop tracking automatically.
    }

    /**
     * Handles app lifecycle event when the app is resumed.
     */
    fun onAppResumed() {
        Log.d(TAG, "App resumed")

        // Re-check permissions upon returning to the app.
        checkPermissions()

        // Test server connections if tracking is enabled.
        viewModelScope.launch {
            if (appState.value.isTrackingEnabled) {
                locationController.testServerConnections()
            }
        }
    }

    /**
     * Handles changes in network connectivity.
     * @param isConnected `true` if network is connected, `false` otherwise.
     */
    fun onNetworkChanged(isConnected: Boolean) {
        Log.d(TAG, "Network changed - Connected: $isConnected")

        if (isConnected && appState.value.isTrackingEnabled) {
            // Test connections when network is restored.
            testServerConnections()

            // Process any pending locations that were queued while offline.
            processPendingLocations()
        }
    }

    /**
     * Handles an emergency event by immediately sending the current location multiple times.
     */
    fun handleEmergency() {
        viewModelScope.launch {
            Log.w(TAG, "Emergency mode activated")

            // Get the current location immediately.
            val locationResult = locationController.getCurrentLocation()

            locationResult.fold(
                onSuccess = { location ->
                    // Send test data multiple times to ensure reception.
                    repeat(3) {
                        locationController.sendTestData()
                    }
                    Log.w(TAG, "Emergency location sent multiple times")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to get emergency location: ${error.message}")
                }
            )
        }
    }

    /**
     * Gets the current application configuration.
     * The user requested to remove the `theme_mode` line.
     */
    fun getCurrentConfiguration(): Map<String, String> {
        return mapOf(
            "server_1_ip" to Constants.SERVER_IP_1,
            "server_2_ip" to Constants.SERVER_IP_2,
            "tcp_port" to Constants.TCP_PORT.toString(),
            "udp_port" to Constants.UDP_PORT.toString(),
            "update_interval" to "${Constants.LOCATION_UPDATE_INTERVAL}ms",
            "network_timeout" to "${Constants.NETWORK_TIMEOUT}ms",
            "max_retries" to Constants.MAX_RETRY_ATTEMPTS.toString()
        )
    }

    /**
     * Cleans up resources when the ViewModel is destroyed.
     */
    override fun onCleared() {
        super.onCleared()

        Log.d(TAG, "MainController being cleared")

        // Stop tracking if it's still active to prevent resource leaks.
        if (appState.value.isTrackingEnabled) {
            stopTracking()
        }

        // Clean up the location controller's resources.
        locationController.cleanup()

        Log.d(TAG, "MainController cleared successfully")
    }
}