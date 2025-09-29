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
 * LocationController ultra-simplificado - ENVÍA ubicaciones a 4 SERVIDORES UDP
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
        Log.d(TAG, "Starting SIMPLE UDP tracking to 4 servers...")

        if (!_appState.value.hasAllPermissions()) {
            return Result.failure(Exception("Permissions required"))
        }

        if (_appState.value.isTrackingEnabled) {
            return Result.success("Already tracking")
        }

        return try {
            _appState.value = _appState.value.startTracking()
            startSimpleTracking()
            Result.success("Simple tracking started - 4 UDP servers")
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
                    // Enviar inmediatamente a LOS 4 SERVIDORES sin guardar nada en memoria
                    sendLocationToAllServers(locationData)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in tracking", e)
            }
        }
    }

    private fun sendLocationToAllServers(locationData: LocationData) {
        try {
            // Actualizar estado UI
            _appState.value = _appState.value.updateLocation(locationData)

            Log.d(TAG, "Sending to ALL 4 UDP servers: ${locationData.getFormattedCoordinates()}")

            // Enviar a LOS 4 servidores en paralelo usando un scope controlado
            CoroutineScope(Dispatchers.IO).launch {
                async { networkRepository.sendToServer1(locationData) }
                async { networkRepository.sendToServer2(locationData) }
                async { networkRepository.sendToServer3(locationData) }
                async { networkRepository.sendToServer4(locationData) }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in immediate send to 4 servers", e)
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
            Log.d(TAG, "Testing connections to all 4 UDP servers...")

            val results = networkRepository.testAllServers()

            val server1Ok = results["server1"] ?: false
            val server2Ok = results["server2"] ?: false
            val server3Ok = results["server3"] ?: false
            val server4Ok = results["server4"] ?: false

            val activeCount = listOf(server1Ok, server2Ok, server3Ok, server4Ok).count { it }

            Log.d(TAG, "Server test results: S1=$server1Ok, S2=$server2Ok, S3=$server3Ok, S4=$server4Ok")

            val message = when (activeCount) {
                4 -> "All 4 servers OK (Optimal)"
                3 -> "3 of 4 servers OK (Good)"
                2 -> "2 of 4 servers OK (Minimum)"
                1 -> "Only 1 of 4 servers OK (Risk)"
                else -> "No servers available (Critical)"
            }

            if (activeCount > 0) {
                Result.success(message)
            } else {
                Result.failure(Exception("No servers available"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendTestData(): Result<String> {
        return try {
            val testLocation = LocationData.createTestLocation()
            sendLocationToAllServers(testLocation)
            Result.success("Test data sent to all 4 UDP servers")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearMessages() {
        _appState.value = _appState.value.clearMessages()
    }

    fun resetStatistics() {
        Log.d(TAG, "No statistics to reset in simple mode")
    }

    fun getDiagnosticInfo(): Map<String, String> {
        val appState = _appState.value
        return mapOf(
            "protocol" to "UDP_4_SERVERS",
            "server_1_ip" to Constants.SERVER_IP_1,
            "server_2_ip" to Constants.SERVER_IP_2,
            "server_3_ip" to Constants.SERVER_IP_3,
            "server_4_ip" to Constants.SERVER_IP_4,
            "udp_port" to Constants.UDP_PORT.toString(),
            "tracking_active" to appState.isTrackingEnabled.toString(),
            "location_permission" to appState.hasLocationPermission.toString(),
            "background_permission" to appState.hasBackgroundLocationPermission.toString(),
            "current_location" to (appState.currentLocation?.getFormattedCoordinates() ?: "None"),
            "locations_sent" to appState.totalLocationsSent.toString(),
            "mode" to "SIMPLE_4_UDP_SERVERS"
        )
    }

    fun getNetworkInfo(): String {
        return "UDP Simple Mode - 4 Servers (${Constants.SERVER_IP_1}, ${Constants.SERVER_IP_2}, ${Constants.SERVER_IP_3}, ${Constants.SERVER_IP_4})"
    }

    suspend fun flushPendingLocations(): Result<String> {
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