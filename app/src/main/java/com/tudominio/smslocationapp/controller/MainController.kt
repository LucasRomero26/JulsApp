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
 * MainController optimizado para solo UDP.
 */
class MainController(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = Constants.Logs.TAG_MAIN
    }

    private val locationController = LocationController(context)
    val themePreferences = ThemePreferences(context)
    val appState: StateFlow<AppState> = locationController.appState

    init {
        Log.d(TAG, "MainController initialized for UDP")
        checkPermissions()
    }

    fun checkPermissions() {
        locationController.checkPermissions()
        Log.d(TAG, "UDP permissions checked")
    }

    fun onPermissionsGranted() {
        checkPermissions()
        val currentState = appState.value
        if (currentState.hasAllPermissions()) {
            Log.d(TAG, "All permissions granted for UDP")
            viewModelScope.launch {
                locationController.getCurrentAppState().let { state ->
                    if (state.hasAllPermissions()) {
                        locationController.getCurrentLocation()
                    }
                }
            }
        }
    }

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

    private suspend fun startTracking() {
        Log.d(TAG, "Starting UDP tracking from MainController")

        if (!appState.value.hasAllPermissions()) {
            Log.w(TAG, "Cannot start UDP tracking - missing permissions")
            return
        }

        try {
            startLocationService()
            val result = locationController.startLocationTracking()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "UDP tracking started successfully: $message")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to start UDP tracking: ${error.message}")
                    stopLocationService()
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error starting UDP tracking", e)
        }
    }

    private fun stopTracking() {
        Log.d(TAG, "Stopping UDP tracking from MainController")

        try {
            val result = locationController.stopLocationTracking()
            stopLocationService()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "UDP tracking stopped successfully: $message")
                },
                onFailure = { error ->
                    Log.e(TAG, "Error stopping UDP tracking: ${error.message}")
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping UDP tracking", e)
        }
    }

    private fun startLocationService() {
        val intent = Intent(context, LocationService::class.java).apply {
            action = Constants.ServiceActions.START_TRACKING
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "UDP location service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting UDP location service", e)
        }
    }

    private fun stopLocationService() {
        val intent = Intent(context, LocationService::class.java).apply {
            action = Constants.ServiceActions.STOP_TRACKING
        }

        try {
            context.startService(intent)
            Log.d(TAG, "UDP location service stop requested")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping UDP location service", e)
        }
    }

    fun getCurrentLocation() {
        viewModelScope.launch {
            val result = locationController.getCurrentLocation()

            result.fold(
                onSuccess = { location ->
                    Log.d(TAG, "Current UDP location obtained: ${location.getFormattedCoordinates()}")
                },
                onFailure = { error ->
                    Log.w(TAG, "Failed to get current UDP location: ${error.message}")
                }
            )
        }
    }

    fun testServerConnections() {
        viewModelScope.launch {
            val result = locationController.testServerConnections()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "UDP server test successful: $message")
                },
                onFailure = { error ->
                    Log.w(TAG, "UDP server test failed: ${error.message}")
                }
            )
        }
    }

    fun sendTestData() {
        viewModelScope.launch {
            val result = locationController.sendTestData()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "UDP test data sent successfully: $message")
                },
                onFailure = { error ->
                    Log.w(TAG, "Failed to send UDP test data: ${error.message}")
                }
            )
        }
    }

    fun clearMessages() {
        locationController.clearMessages()
    }

    fun resetStatistics() {
        locationController.resetStatistics()
    }

    fun getDiagnosticInfo(): Map<String, String> {
        return locationController.getDiagnosticInfo() + mapOf(
            "app_version" to "1.0.0",
            "protocol" to "UDP_ONLY"
        )
    }

    fun canStartTracking(): Boolean {
        return locationController.canStartTracking()
    }

    fun getNetworkInfo(): String {
        return locationController.getNetworkInfo()
    }

    fun processPendingLocations() {
        viewModelScope.launch {
            val result = locationController.flushPendingLocations()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Pending UDP locations processed: $message")
                },
                onFailure = { error ->
                    Log.w(TAG, "Failed to process pending UDP locations: ${error.message}")
                }
            )
        }
    }

    fun getAppStatus(): String {
        val state = appState.value
        return state.getStatusSummary()
    }

    fun onAppPaused() {
        Log.d(TAG, "App paused - UDP continues")
    }

    fun onAppResumed() {
        Log.d(TAG, "App resumed - UDP check")
        checkPermissions()
        viewModelScope.launch {
            if (appState.value.isTrackingEnabled) {
                locationController.testServerConnections()
            }
        }
    }

    fun onNetworkChanged(isConnected: Boolean) {
        Log.d(TAG, "Network changed - Connected: $isConnected (UDP)")

        if (isConnected && appState.value.isTrackingEnabled) {
            testServerConnections()
            processPendingLocations()
        }
    }

    fun handleEmergency() {
        viewModelScope.launch {
            Log.w(TAG, "Emergency mode activated - UDP")

            val locationResult = locationController.getCurrentLocation()

            locationResult.fold(
                onSuccess = { location ->
                    repeat(3) {
                        locationController.sendTestData()
                    }
                    Log.w(TAG, "Emergency UDP location sent multiple times")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to get emergency UDP location: ${error.message}")
                }
            )
        }
    }

    fun getCurrentConfiguration(): Map<String, String> {
        return mapOf(
            "server_1_ip" to Constants.SERVER_IP_1,
            "server_2_ip" to Constants.SERVER_IP_2,
            "udp_port" to Constants.UDP_PORT.toString(), // Solo UDP
            "protocol" to "UDP_ONLY",
            "update_interval" to "${Constants.LOCATION_UPDATE_INTERVAL}ms",
            "network_timeout" to "${Constants.NETWORK_TIMEOUT}ms",
            "max_retries" to Constants.MAX_RETRY_ATTEMPTS.toString()
        )
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "MainController being cleared - UDP")

        if (appState.value.isTrackingEnabled) {
            stopTracking()
        }

        locationController.cleanup()
        Log.d(TAG, "MainController UDP cleared successfully")
    }
}