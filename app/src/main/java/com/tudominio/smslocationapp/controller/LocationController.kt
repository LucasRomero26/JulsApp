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

 * Controlador para manejo de ubicaciones GPS

 * Coordina entre LocationRepository y NetworkRepository.

 */

class LocationController(private val context: Context) {



    companion object {

        private const val TAG = Constants.Logs.TAG_CONTROLLER

    }



    private val locationRepository = LocationRepository(context)

    private val networkRepository = NetworkRepository(context)

    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())



// Flujos observables

    private val _appState = MutableStateFlow(AppState())

    val appState: StateFlow<AppState> = _appState.asStateFlow()



    private var locationTrackingJob: Job? = null



    init {

// Observar cambios en el estado de servidores

        controllerScope.launch {

            networkRepository.serverStatusUpdates.collect { serverStatus ->

                updateAppState { currentState ->

                    currentState.updateServerStatus(serverStatus)

                }

            }

        }



// Verificar permisos inicialmente

        checkPermissions()

    }



    /**

     * Verificar y actualizar estado de permisos

     */

    fun checkPermissions() {

        val hasLocation = locationRepository.hasLocationPermissions()

        val hasBackground = locationRepository.hasBackgroundLocationPermission()



        updateAppState { currentState ->

            currentState.updatePermissions(hasLocation, hasBackground)

        }



        Log.d(TAG, "Permissions - Location: $hasLocation, Background: $hasBackground")

    }



    /**

     * Iniciar seguimiento de ubicación

     */

    suspend fun startLocationTracking(): Result<String> {

        Log.d(TAG, "Starting location tracking...")



// Verificar permisos

        if (!_appState.value.hasAllPermissions()) {

            return Result.failure(Exception(Constants.Messages.LOCATION_PERMISSION_REQUIRED))

        }



// Verificar conectividad de red

        if (!networkRepository.isNetworkAvailable()) {

            return Result.failure(Exception(Constants.Messages.NETWORK_ERROR))

        }



// Verificar si ya está activo

        if (_appState.value.isTrackingEnabled) {

            return Result.success("Tracking already active")

        }



        return try {

// Iniciar actualizaciones de ubicación

            val locationStarted = locationRepository.startLocationUpdates()



            if (!locationStarted) {

                return Result.failure(Exception("Failed to start location updates"))

            }



// Actualizar estado

            updateAppState { it.startTracking() }



// Iniciar job de procesamiento de ubicaciones

            startLocationProcessingJob()



// Testear conexiones iniciales

            testServerConnections()



            Log.d(TAG, "Location tracking started successfully")

            Result.success(Constants.Messages.TRACKING_STARTED)



        } catch (e: Exception) {

            Log.e(TAG, "Error starting location tracking", e)

            Result.failure(e)

        }

    }



    /**

     * Detener seguimiento de ubicación

     */

    fun stopLocationTracking(): Result<String> {

        Log.d(TAG, "Stopping location tracking...")



        try {

// Detener actualizaciones de ubicación

            locationRepository.stopLocationUpdates()



// Cancelar job de procesamiento

            locationTrackingJob?.cancel()

            locationTrackingJob = null



// Actualizar estado

            updateAppState { it.stopTracking() }



            Log.d(TAG, "Location tracking stopped successfully")

            return Result.success(Constants.Messages.TRACKING_STOPPED)



        } catch (e: Exception) {

            Log.e(TAG, "Error stopping location tracking", e)

            return Result.failure(e)

        }

    }



    /**

     * Obtener ubicación actual (sin iniciar tracking)

     */

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



                Log.d(TAG, "Current location obtained: ${location.getFormattedCoordinates()}")

                Result.success(location)

            } else {

                updateAppState {

                    it.copy(isLoadingLocation = false)

                        .showErrorMessage(Constants.Messages.GPS_NOT_AVAILABLE)

                }

                Result.failure(Exception("Invalid location data"))

            }



        } catch (e: Exception) {

            updateAppState {

                it.copy(isLoadingLocation = false)

                    .showErrorMessage("Error getting location: ${e.message}")

            }



            Log.e(TAG, "Error getting current location", e)

            Result.failure(e)

        }

    }



    /**

     * Iniciar job de procesamiento de ubicaciones en tiempo real

     */

    private fun startLocationProcessingJob() {

        locationTrackingJob = controllerScope.launch {

            locationRepository.locationUpdates.collect { locationData ->

                processNewLocation(locationData)

            }

        }

    }



    /**

     * Procesar nueva ubicación recibida

     */

    private suspend fun processNewLocation(locationData: LocationData) {

        Log.d(TAG, "Processing new location: ${locationData.getFormattedCoordinates()}")



// Actualizar estado con nueva ubicación

        updateAppState { it.updateLocation(locationData) }



// Enviar ubicación a servidores

        val sendResult = networkRepository.sendLocation(locationData)



        sendResult.fold(

            onSuccess = { serverStatus ->

                Log.d(TAG, "Location sent successfully - ${serverStatus.getActiveConnectionsCount()} connections")



// No mostrar mensaje de éxito en cada envío para evitar spam

// Solo actualizar el estado del servidor

            },

            onFailure = { error ->

                Log.w(TAG, "Failed to send location: ${error.message}")



// Mostrar error solo si no hay conexiones activas

                val currentServerStatus = networkRepository.getCurrentServerStatus()

                if (!currentServerStatus.hasAnyConnection()) {

                    updateAppState {

                        it.showErrorMessage("Connection lost to all servers")

                    }

                }

            }

        )

    }



    /**

     * Testear conexiones a servidores

     */

    suspend fun testServerConnections(): Result<String> {

        Log.d(TAG, "Testing server connections...")



        return try {

            val serverStatus = networkRepository.testServerConnections()

            val activeConnections = serverStatus.getActiveConnectionsCount()



            val message = when {

                activeConnections == 4 -> "All servers connected (4/4)"

                activeConnections > 0 -> "Partial connectivity ($activeConnections/4 servers)"

                else -> "No server connections available"

            }



            if (activeConnections > 0) {

                updateAppState { it.showSuccessMessage(message) }

                Result.success(message)

            } else {

                updateAppState { it.showErrorMessage(message) }

                Result.failure(Exception("No server connections"))

            }



        } catch (e: Exception) {

            Log.e(TAG, "Error testing server connections", e)

            val errorMessage = "Connection test failed: ${e.message}"

            updateAppState { it.showErrorMessage(errorMessage) }

            Result.failure(e)

        }

    }



    /**

     * Enviar datos de prueba a servidores

     */

    suspend fun sendTestData(): Result<String> {

        Log.d(TAG, "Sending test data to servers...")



        if (!networkRepository.isNetworkAvailable()) {

            return Result.failure(Exception(Constants.Messages.NETWORK_ERROR))

        }



        return try {

            val result = networkRepository.sendTestData()



            result.fold(

                onSuccess = { serverStatus ->

                    val message = "Test data sent to ${serverStatus.getActiveConnectionsCount()}/4 servers"

                    updateAppState { it.showSuccessMessage(message) }

                    Result.success(message)

                },

                onFailure = { error ->

                    val errorMessage = "Test failed: ${error.message}"

                    updateAppState { it.showErrorMessage(errorMessage) }

                    Result.failure(error)

                }

            )



        } catch (e: Exception) {

            Log.e(TAG, "Error sending test data", e)

            val errorMessage = "Test error: ${e.message}"

            updateAppState { it.showErrorMessage(errorMessage) }

            Result.failure(e)

        }

    }



    /**

     * Obtener información de diagnóstico

     */

    fun getDiagnosticInfo(): Map<String, String> {

        val appState = _appState.value

        val networkStats = networkRepository.getNetworkStatistics()



        return mapOf(

            "tracking_active" to appState.isTrackingEnabled.toString(),

            "location_permission" to appState.hasLocationPermission.toString(),

            "background_permission" to appState.hasBackgroundLocationPermission.toString(),

            "current_location" to (appState.currentLocation?.getFormattedCoordinates() ?: "None"),

            "session_duration" to appState.getSessionDurationFormatted(),

            "locations_sent" to appState.totalLocationsSent.toString(),

            "location_provider" to locationRepository.getLocationProviderInfo(),

            "location_updates_active" to locationRepository.isLocationUpdatesActive().toString(),

            "average_accuracy" to (locationRepository.getAverageAccuracy()?.toString() ?: "N/A"),

            "pending_locations" to networkRepository.getPendingLocationsCount().toString()

        ) + networkStats.mapValues { it.value.toString() }

    }



    /**

     * Limpiar mensajes de estado

     */

    fun clearMessages() {

        updateAppState { it.clearMessages() }

    }



    /**

     * Resetear estadísticas

     */

    fun resetStatistics() {

        networkRepository.resetServerStatistics()

        updateAppState {

            it.copy(

                totalLocationsSent = 0,

                sessionStartTime = if (it.isTrackingEnabled) System.currentTimeMillis() else 0L

            )

        }

        Log.d(TAG, "Statistics reset")

    }



    /**

     * Obtener estado actual de la aplicación

     */

    fun getCurrentAppState(): AppState = _appState.value



    /**

     * Verificar si se puede iniciar el tracking

     */

    fun canStartTracking(): Boolean {

        return _appState.value.canStartTracking()

    }



    /**

     * Actualizar estado de la aplicación de forma thread-safe

     */

    private fun updateAppState(update: (AppState) -> AppState) {

        _appState.value = update(_appState.value)

    }



    /**

     * Obtener información de red

     */

    fun getNetworkInfo(): String {

        return "Type: ${networkRepository.getNetworkType()}, " +

                "Available: ${networkRepository.isNetworkAvailable()}"

    }



    /**

     * Forzar envío de ubicaciones pendientes

     */

    suspend fun flushPendingLocations(): Result<String> {

        val pendingCount = networkRepository.getPendingLocationsCount()



        return if (pendingCount > 0) {

            if (networkRepository.isNetworkAvailable()) {

// El NetworkRepository automáticamente procesará las pendientes cuando detecte red

                Result.success("Processing $pendingCount pending locations")

            } else {

                Result.failure(Exception("No network available to flush pending locations"))

            }

        } else {

            Result.success("No pending locations to flush")

        }

    }



    /**

     * Limpiar recursos

     */

    fun cleanup() {

        Log.d(TAG, "Cleaning up LocationController...")



// Detener tracking si está activo

        if (_appState.value.isTrackingEnabled) {

            stopLocationTracking()

        }



// Cancelar jobs

        locationTrackingJob?.cancel()

        controllerScope.cancel()



// Limpiar repositories

        locationRepository.cleanup()

        networkRepository.cleanup()



        Log.d(TAG, "LocationController cleaned up")

    }

}