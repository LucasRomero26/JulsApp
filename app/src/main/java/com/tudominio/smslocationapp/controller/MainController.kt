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
 * MainController ultra-simplificado
 */
class MainController(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = Constants.Logs.TAG_MAIN
    }

    private val locationController = LocationController(context)
    val themePreferences = ThemePreferences(context)
    val appState: StateFlow<AppState> = locationController.appState

    init {
        Log.d(TAG, "Simple MainController initialized")
        checkPermissions()
    }

    fun checkPermissions() {
        locationController.checkPermissions()
    }

    fun onPermissionsGranted() {
        checkPermissions()
        val currentState = appState.value
        if (currentState.hasAllPermissions()) {
            Log.d(TAG, "All permissions granted")
            viewModelScope.launch {
                locationController.getCurrentLocation()
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
        Log.d(TAG, "Starting simple tracking")

        if (!appState.value.hasAllPermissions()) {
            Log.w(TAG, "Cannot start tracking - missing permissions")
            return
        }

        try {
            startLocationService()
            val result = locationController.startLocationTracking()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Tracking started: $message")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to start tracking: ${error.message}")
                    stopLocationService()
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error starting tracking", e)
        }
    }

    private fun stopTracking() {
        Log.d(TAG, "Stopping simple tracking")

        try {
            val result = locationController.stopLocationTracking()
            stopLocationService()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Tracking stopped: $message")
                },
                onFailure = { error ->
                    Log.e(TAG, "Error stopping tracking: ${error.message}")
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tracking", e)
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
            Log.d(TAG, "Simple location service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting simple location service", e)
        }
    }

    private fun stopLocationService() {
        val intent = Intent(context, LocationService::class.java).apply {
            action = Constants.ServiceActions.STOP_TRACKING
        }

        try {
            context.startService(intent)
            Log.d(TAG, "Simple location service stop requested")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping simple location service", e)
        }
    }

    fun getCurrentLocation() {
        viewModelScope.launch {
            val result = locationController.getCurrentLocation()

            result.fold(
                onSuccess = { location ->
                    Log.d(TAG, "Current location: ${location.getFormattedCoordinates()}")
                },
                onFailure = { error ->
                    Log.w(TAG, "Failed to get current location: ${error.message}")
                }
            )
        }
    }

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

    fun sendTestData() {
        viewModelScope.launch {
            val result = locationController.sendTestData()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Test data sent: $message")
                },
                onFailure = { error ->
                    Log.w(TAG, "Failed to send test data: ${error.message}")
                }
            )
        }
    }

    // Funciones simplificadas que no hacen nada (para compatibilidad con UI)
    fun clearMessages() {
        // No hay mensajes que limpiar en versión simple
    }

    fun resetStatistics() {
        // No hay estadísticas que resetear en versión simple
    }

    fun getDiagnosticInfo(): Map<String, String> {
        return mapOf(
            "app_version" to "1.0.0-simple",
            "protocol" to "UDP_ONLY",
            "mode" to "SIMPLE"
        )
    }

    fun canStartTracking(): Boolean {
        return locationController.canStartTracking()
    }

    fun getNetworkInfo(): String {
        return "Simple UDP Mode"
    }

    fun processPendingLocations() {
        // No hay ubicaciones pendientes en versión simple
    }

    fun getAppStatus(): String {
        val state = appState.value
        return when {
            !state.hasAllPermissions() -> "Permissions required"
            state.isTrackingEnabled -> "Simple tracking active"
            else -> "Ready to track"
        }
    }

    fun onAppPaused() {
        Log.d(TAG, "App paused - simple mode")
    }

    fun onAppResumed() {
        Log.d(TAG, "App resumed - simple mode")
        checkPermissions()
    }

    fun onNetworkChanged(isConnected: Boolean) {
        Log.d(TAG, "Network changed - Connected: $isConnected (simple mode)")
    }

    fun handleEmergency() {
        Log.w(TAG, "Emergency mode - simple")
        sendTestData()
    }

    fun getCurrentConfiguration(): Map<String, String> {
        return mapOf(
            "server_1_ip" to Constants.SERVER_IP_1,
            "server_2_ip" to Constants.SERVER_IP_2,
            "udp_port" to Constants.UDP_PORT.toString(),
            "protocol" to "UDP_SIMPLE",
            "update_interval" to "${Constants.LOCATION_UPDATE_INTERVAL}ms",
            "mode" to "SIMPLE"
        )
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "Simple MainController being cleared")

        if (appState.value.isTrackingEnabled) {
            viewModelScope.launch { stopTracking() }
        }

        locationController.cleanup()
        Log.d(TAG, "Simple MainController cleared")
    }
}