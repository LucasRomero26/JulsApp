package com.tudominio.smslocation.controller

import android.content.Context
import android.util.Log
import com.tudominio.smslocation.model.data.AppState
import com.tudominio.smslocation.model.data.LocationData
import com.tudominio.smslocation.model.repository.LocationRepository
import com.tudominio.smslocation.model.repository.NetworkRepository
import com.tudominio.smslocation.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * LocationController optimizado para solo UDP con máxima velocidad.
 */
class LocationController(private val context: Context) {

    companion object {
        private const val TAG = Constants.Logs.TAG_CONTROLLER
    }

    private val locationRepository = LocationRepository(context)
    private val networkRepository = NetworkRepository(context)
    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _appState = MutableStateFlow(AppState())
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private var locationTrackingJob: Job? = null

    init {
        controllerScope.launch {
            networkRepository.serverStatusUpdates.collect { serverStatus ->
                updateAppState { currentState ->
                    currentState.updateServerStatus(serverStatus)
                }
            }
        }
        checkPermissions()
    }

    fun checkPermissions() {
        val hasLocation = locationRepository.hasLocationPermissions()
        val hasBackground = locationRepository.hasBackgroundLocationPermission()

        updateAppState { currentState ->
            currentState.updatePermissions(hasLocation, hasBackground)
        }

        Log.d(TAG, "UDP Permissions - Location: $hasLocation, Background: $hasBackground")
    }

    suspend fun startLocationTracking(): Result<String> {
        Log.d(TAG, "Starting fast UDP location tracking...")

        if (!_appState.value.hasAllPermissions()) {
            return Result.failure(Exception(Constants.Messages.LOCATION_PERMISSION_REQUIRED))
        }

        if (!networkRepository.isNetworkAvailable()) {
            return Result.failure(Exception(Constants.Messages.NETWORK_ERROR))
        }

        if (_appState.value.isTrackingEnabled) {
            return Result.success("UDP tracking already active")
        }

        return try {
            val locationStarted = locationRepository.startLocationUpdates()

            if (!locationStarted) {
                return Result.failure(Exception("Failed to start UDP location updates"))
            }

            updateAppState { it.startTracking() }
            startLocationProcessingJob()
            testServerConnections()

            Log.d(TAG, "Fast UDP location tracking started successfully")
            Result.success("Fast UDP tracking started")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting UDP location tracking", e)
            Result.failure(e)
        }
    }

    fun stopLocationTracking(): Result<String> {
        Log.d(TAG, "Stopping UDP location tracking...")

        try {
            locationRepository.stopLocationUpdates()
            locationTrackingJob?.cancel()
            locationTrackingJob = null
            updateAppState { it.stopTracking() }

            Log.d(TAG, "UDP location tracking stopped successfully")
            return Result.success(Constants.Messages.TRACKING_STOPPED)

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping UDP location tracking", e)
            return Result.failure(e)
        }
    }

    suspend fun getCurrentLocation(): Result<LocationData> {
        if (!_appState.value.hasLocationPermission) {
            return Result.failure(Exception(Constants.Messages.LOCATION_PERMISSION_REQUIRED))
        }

        updateAppState { it.copy(isLoadingLocation = true) }

        return try {
            val location = locationRepository.getCurrentLocation()

            if (location != null && location.isValid()) {
                updateAppState {
                    it.copy(
                        currentLocation = location,
                        isLoadingLocation = false
                    )
                }

                Log.d(TAG, "Current location obtained for UDP: ${location.getFormattedCoordinates()}")
                Result.success(location)
            } else {
                updateAppState {
                    it.copy(isLoadingLocation = false)
                        .showErrorMessage(Constants.Messages.GPS_NOT_AVAILABLE)
                }
                Result.failure(Exception("Invalid location data for UDP"))
            }

        } catch (e: Exception) {
            updateAppState {
                it.copy(isLoadingLocation = false)
                    .showErrorMessage("Error getting location for UDP: ${e.message}")
            }

            Log.e(TAG, "Error getting current location for UDP", e)
            Result.failure(e)
        }
    }

    private fun startLocationProcessingJob() {
        locationTrackingJob = controllerScope.launch {
            locationRepository.locationUpdates.collect { locationData ->
                processNewLocationUdp(locationData)
            }
        }
    }

    private suspend fun processNewLocationUdp(locationData: LocationData) {
        Log.d(TAG, "Processing new location via UDP: ${locationData.getFormattedCoordinates()}")

        updateAppState { it.updateLocation(locationData) }

        val sendResult = networkRepository.sendLocation(locationData)

        sendResult.fold(
            onSuccess = { serverStatus ->
                Log.d(TAG, "UDP location sent successfully - ${serverStatus.getActiveConnectionsCount()}/2 UDP connections")
            },
            onFailure = { error ->
                Log.w(TAG, "Failed to send UDP location: ${error.message}")

                val currentServerStatus = networkRepository.getCurrentServerStatus()
                if (!currentServerStatus.hasAnyConnection()) {
                    updateAppState {
                        it.showErrorMessage("Connection lost to all UDP servers")
                    }
                }
            }
        )
    }

    suspend fun testServerConnections(): Result<String> {
        Log.d(TAG, "Testing UDP server connections...")

        return try {
            val serverStatus = networkRepository.testServerConnections()
            val activeConnections = serverStatus.getActiveConnectionsCount()

            val message = when {
                activeConnections == 2 -> "All UDP servers connected (2/2)"
                activeConnections > 0 -> "Partial UDP connectivity ($activeConnections/2 servers)"
                else -> "No UDP server connections available"
            }

            if (activeConnections > 0) {
                updateAppState { it.showSuccessMessage(message) }
                Result.success(message)
            } else {
                updateAppState { it.showErrorMessage(message) }
                Result.failure(Exception("No UDP server connections"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error testing UDP server connections", e)
            val errorMessage = "UDP connection test failed: ${e.message}"
            updateAppState { it.showErrorMessage(errorMessage) }
            Result.failure(e)
        }
    }

    suspend fun sendTestData(): Result<String> {
        Log.d(TAG, "Sending test data to UDP servers...")

        if (!networkRepository.isNetworkAvailable()) {
            return Result.failure(Exception(Constants.Messages.NETWORK_ERROR))
        }

        return try {
            val result = networkRepository.sendTestData()

            result.fold(
                onSuccess = { serverStatus ->
                    val message = "Test data sent to ${serverStatus.getActiveConnectionsCount()}/2 UDP servers"
                    updateAppState { it.showSuccessMessage(message) }
                    Result.success(message)
                },
                onFailure = { error ->
                    val errorMessage = "UDP test failed: ${error.message}"
                    updateAppState { it.showErrorMessage(errorMessage) }
                    Result.failure(error)
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error sending UDP test data", e)
            val errorMessage = "UDP test error: ${e.message}"
            updateAppState { it.showErrorMessage(errorMessage) }
            Result.failure(e)
        }
    }

    fun getDiagnosticInfo(): Map<String, String> {
        val appState = _appState.value
        val networkStats = networkRepository.getNetworkStatistics()

        return mapOf(
            "protocol" to "UDP_ONLY",
            "tracking_active" to appState.isTrackingEnabled.toString(),
            "location_permission" to appState.hasLocationPermission.toString(),
            "background_permission" to appState.hasBackgroundLocationPermission.toString(),
            "current_location" to (appState.currentLocation?.getFormattedCoordinates() ?: "None"),
            "session_duration" to appState.getSessionDurationFormatted(),
            "locations_sent" to appState.totalLocationsSent.toString(),
            "location_provider" to locationRepository.getLocationProviderInfo(),
            "location_updates_active" to locationRepository.isLocationUpdatesActive().toString(),
            "average_accuracy" to (locationRepository.getAverageAccuracy()?.toString() ?: "N/A"),
            "pending_udp_locations" to networkRepository.getPendingLocationsCount().toString()
        ) + networkStats.mapValues { it.value.toString() }
    }

    fun clearMessages() {
        updateAppState { it.clearMessages() }
    }

    fun resetStatistics() {
        networkRepository.resetServerStatistics()
        updateAppState {
            it.copy(
                totalLocationsSent = 0,
                sessionStartTime = if (it.isTrackingEnabled) System.currentTimeMillis() else 0L
            )
        }
        Log.d(TAG, "UDP statistics reset")
    }

    fun getCurrentAppState(): AppState = _appState.value

    fun canStartTracking(): Boolean {
        return _appState.value.canStartTracking()
    }

    private fun updateAppState(update: (AppState) -> AppState) {
        _appState.value = update(_appState.value)
    }

    fun getNetworkInfo(): String {
        return "Type: ${networkRepository.getNetworkType()}, " +
                "Available: ${networkRepository.isNetworkAvailable()}, " +
                "Protocol: UDP Only"
    }

    suspend fun flushPendingLocations(): Result<String> {
        val pendingCount = networkRepository.getPendingLocationsCount()

        return if (pendingCount > 0) {
            if (networkRepository.isNetworkAvailable()) {
                Result.success("Processing $pendingCount pending UDP locations")
            } else {
                Result.failure(Exception("No network available to flush pending UDP locations"))
            }
        } else {
            Result.success("No pending UDP locations to flush")
        }
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up UDP LocationController...")

        if (_appState.value.isTrackingEnabled) {
            stopLocationTracking()
        }

        locationTrackingJob?.cancel()
        controllerScope.cancel()

        locationRepository.cleanup()
        networkRepository.cleanup()

        Log.d(TAG, "UDP LocationController cleaned up")
    }
}