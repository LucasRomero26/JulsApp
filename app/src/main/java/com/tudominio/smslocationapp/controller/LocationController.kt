package com.tudominio.smslocation.controller

import android.content.Context
import android.util.Log
import com.tudominio.smslocation.model.data.AppState
import com.tudominio.smslocation.model.data.LocationData
import com.tudominio.smslocation.model.repository.SimpleLocationRepository
import com.tudominio.smslocation.model.repository.SimpleNetworkRepository
import com.tudominio.smslocation.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * LocationController ultra-simplificado - SOLO envía ubicaciones
 * Sin colas, sin buffers, sin memoria acumulada
 */
class LocationController(private val context: Context) {

    companion object {
        private const val TAG = Constants.Logs.TAG_CONTROLLER
    }

    private val locationRepository = SimpleLocationRepository(context)
    private val networkRepository = SimpleNetworkRepository(context)

    private val _appState = MutableStateFlow(AppState())
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private var trackingJob: Job? = null

    fun checkPermissions() {
        val hasLocation = locationRepository.hasLocationPermissions()
        val hasBackground = locationRepository.hasBackgroundLocationPermission()

        _appState.value = _appState.value.updatePermissions(hasLocation, hasBackground)
        Log.d(TAG, "Permissions - Location: $hasLocation, Background: $hasBackground")
    }

    suspend fun startLocationTracking(): Result<String> {
        Log.d(TAG, "Starting SIMPLE UDP tracking...")

        if (!_appState.value.hasAllPermissions()) {
            return Result.failure(Exception("Permissions required"))
        }

        if (_appState.value.isTrackingEnabled) {
            return Result.success("Already tracking")
        }

        return try {
            _appState.value = _appState.value.startTracking()
            startSimpleTracking()
            Result.success("Simple tracking started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting tracking", e)
            Result.failure(e)
        }
    }

    fun stopLocationTracking(): Result<String> {
        Log.d(TAG, "Stopping simple tracking...")

        trackingJob?.cancel()
        trackingJob = null
        locationRepository.stopLocationUpdates()

        _appState.value = _appState.value.stopTracking()

        return Result.success("Tracking stopped")
    }

    private fun startSimpleTracking() {
        trackingJob = GlobalScope.launch(Dispatchers.IO) {
            try {
                locationRepository.startLocationUpdates { locationData ->
                    // Enviar inmediatamente sin guardar nada en memoria
                    sendLocationImmediately(locationData)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in tracking", e)
            }
        }
    }

    private fun sendLocationImmediately(locationData: LocationData) {
        try {
            // Actualizar estado UI
            _appState.value = _appState.value.updateLocation(locationData)

            Log.d(TAG, "Sending: ${locationData.getFormattedCoordinates()}")

            // Enviar a ambos servidores en paralelo - SIN ESPERAR respuesta
            GlobalScope.launch(Dispatchers.IO) {
                networkRepository.sendToServer1(locationData)
            }
            GlobalScope.launch(Dispatchers.IO) {
                networkRepository.sendToServer2(locationData)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in immediate send", e)
        }
    }

    suspend fun getCurrentLocation(): Result<LocationData> {
        if (!_appState.value.hasLocationPermission) {
            return Result.failure(Exception("No permission"))
        }

        return try {
            val location = locationRepository.getCurrentLocationOnce()
            if (location != null && location.isValid()) {
                _appState.value = _appState.value.copy(currentLocation = location)
                Result.success(location)
            } else {
                Result.failure(Exception("No location"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testServerConnections(): Result<String> {
        return try {
            val success1 = networkRepository.testServer1()
            val success2 = networkRepository.testServer2()

            val message = when {
                success1 && success2 -> "Both servers OK"
                success1 || success2 -> "One server OK"
                else -> "No servers available"
            }

            if (success1 || success2) {
                Result.success(message)
            } else {
                Result.failure(Exception("No servers"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendTestData(): Result<String> {
        return try {
            val testLocation = LocationData.createTestLocation()
            sendLocationImmediately(testLocation)
            Result.success("Test data sent")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearMessages() {
        _appState.value = _appState.value.clearMessages()
    }

    fun resetStatistics() {
        // No hay estadísticas en versión simple
        Log.d(TAG, "No statistics to reset in simple mode")
    }

    fun getDiagnosticInfo(): Map<String, String> {
        val appState = _appState.value
        return mapOf(
            "protocol" to "UDP_SIMPLE",
            "tracking_active" to appState.isTrackingEnabled.toString(),
            "location_permission" to appState.hasLocationPermission.toString(),
            "background_permission" to appState.hasBackgroundLocationPermission.toString(),
            "current_location" to (appState.currentLocation?.getFormattedCoordinates() ?: "None"),
            "locations_sent" to appState.totalLocationsSent.toString(),
            "mode" to "SIMPLE"
        )
    }

    fun getNetworkInfo(): String {
        return "UDP Simple Mode"
    }

    suspend fun flushPendingLocations(): Result<String> {
        // No hay ubicaciones pendientes en modo simple
        return Result.success("No pending locations in simple mode")
    }

    fun getCurrentAppState(): AppState = _appState.value

    fun canStartTracking(): Boolean = _appState.value.canStartTracking()

    fun cleanup() {
        trackingJob?.cancel()
        locationRepository.cleanup()
        networkRepository.cleanup()
    }
}