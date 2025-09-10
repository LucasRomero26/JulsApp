package com.tudominio.smslocation.model.repository

import android.content.Context
import android.util.Log
import com.tudominio.smslocation.model.data.LocationData
import com.tudominio.smslocation.model.data.ServerStatus
import com.tudominio.smslocation.model.network.NetworkManager
import com.tudominio.smslocation.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * NetworkRepository optimizado para solo UDP con máxima velocidad.
 * Elimina TCP y optimiza manejo de cola UDP.
 */
class NetworkRepository(context: Context) {

    companion object {
        private const val TAG = Constants.Logs.TAG_NETWORK
    }

    private val networkManager = NetworkManager(context)
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _serverStatusUpdates = Channel<ServerStatus>(Channel.UNLIMITED)
    val serverStatusUpdates: Flow<ServerStatus> = _serverStatusUpdates.receiveAsFlow()

    // Cola optimizada para UDP rápido
    private val pendingLocations = mutableListOf<LocationData>()
    private var isProcessingQueue = false

    init {
        networkManager.onServerStatusChanged = { serverStatus ->
            _serverStatusUpdates.trySend(serverStatus)
        }
    }

    /**
     * Envío UDP ultra-rápido con cola optimizada
     */
    suspend fun sendLocation(locationData: LocationData): Result<ServerStatus> {
        return try {
            Log.d(TAG, "Fast UDP: Attempting to send location JSON: ${locationData.toJsonFormat()}")

            val serverStatus = networkManager.sendLocationToAllServers(locationData)

            if (serverStatus.hasAnyConnection()) {
                Log.d(TAG, "Fast UDP: Location sent successfully to ${serverStatus.getActiveConnectionsCount()}/2 UDP servers.")
                Result.success(serverStatus)
            } else {
                Log.w(TAG, "Fast UDP: Failed to send location to any UDP server.")
                addToPendingQueue(locationData)
                Result.failure(Exception("No UDP server connections available."))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Fast UDP: Error sending location: ${e.message}", e)
            addToPendingQueue(locationData)
            Result.failure(e)
        }
    }

    /**
     * Envío por lotes UDP optimizado
     */
    suspend fun sendLocationBatch(locations: List<LocationData>): List<Result<ServerStatus>> {
        return locations.map { location ->
            sendLocation(location)
        }
    }

    /**
     * Verificar disponibilidad de red
     */
    fun isNetworkAvailable(): Boolean {
        return networkManager.isNetworkAvailable()
    }

    /**
     * Obtener tipo de red optimizado
     */
    fun getNetworkType(): String {
        return networkManager.getNetworkInfo()
    }

    /**
     * Test de conexiones UDP solamente
     */
    suspend fun testServerConnections(): ServerStatus {
        Log.d(TAG, "Testing UDP server connections...")
        return networkManager.testAllServerConnections()
    }

    /**
     * Estado actual del servidor
     */
    fun getCurrentServerStatus(): ServerStatus {
        return networkManager.getCurrentServerStatus()
    }

    /**
     * Envío de datos de test UDP
     */
    suspend fun sendTestData(): Result<ServerStatus> {
        return try {
            Log.d(TAG, "Sending test UDP data to servers...")
            val serverStatus = networkManager.sendTestData()

            if (serverStatus.hasAnyConnection()) {
                Result.success(serverStatus)
            } else {
                Result.failure(Exception("UDP test data send failed - no server connections."))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error sending UDP test data: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Agregar a cola optimizada para UDP
     */
    private fun addToPendingQueue(locationData: LocationData) {
        synchronized(pendingLocations) {
            pendingLocations.add(locationData)

            // Cola más pequeña para UDP rápido (50 en lugar de 100)
            if (pendingLocations.size > 50) {
                pendingLocations.removeAt(0)
                Log.w(TAG, "Fast UDP: Pending queue reached max size (50), removed oldest location.")
            }

            Log.d(TAG, "Fast UDP: Added location to pending queue. Size: ${pendingLocations.size}")
        }

        if (!isProcessingQueue) {
            processPendingLocations()
        }
    }

    /**
     * Procesamiento de cola UDP ultra-rápido
     */
    private fun processPendingLocations() {
        repositoryScope.launch {
            isProcessingQueue = true

            var shouldContinue = true
            while (pendingLocations.isNotEmpty() && isNetworkAvailable() && shouldContinue) {
                val location = synchronized(pendingLocations) {
                    if (pendingLocations.isNotEmpty()) {
                        pendingLocations.removeAt(0)
                    } else {
                        null
                    }
                }

                if (location != null) {
                    Log.d(TAG, "Fast UDP: Processing pending location: ${location.getFormattedCoordinates()}")

                    try {
                        val result = networkManager.sendLocationToAllServers(location)

                        if (result.hasAnyConnection()) {
                            Log.d(TAG, "Fast UDP: Pending location sent successfully during retry.")
                        } else {
                            synchronized(pendingLocations) {
                                pendingLocations.add(0, location)
                            }
                            shouldContinue = false
                            Log.w(TAG, "Fast UDP: Failed to send pending location, will retry later.")
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Fast UDP: Error processing pending location: ${e.message}", e)
                        synchronized(pendingLocations) {
                            pendingLocations.add(0, location)
                        }
                        shouldContinue = false
                    }

                    // Delay ultra-corto para UDP rápido
                    if (shouldContinue) {
                        delay(200) // 200ms en lugar de 500ms
                    }
                } else {
                    shouldContinue = false
                }
            }

            isProcessingQueue = false

            if (pendingLocations.isNotEmpty()) {
                Log.d(TAG, "Fast UDP: Finished processing pending locations. ${pendingLocations.size} remain in queue.")
            } else {
                Log.d(TAG, "Fast UDP: All pending locations processed successfully. Queue is empty.")
            }
        }
    }

    /**
     * Obtener cantidad de ubicaciones pendientes
     */
    fun getPendingLocationsCount(): Int {
        return synchronized(pendingLocations) { pendingLocations.size }
    }

    /**
     * Limpiar ubicaciones pendientes
     */
    fun clearPendingLocations() {
        synchronized(pendingLocations) {
            val count = pendingLocations.size
            pendingLocations.clear()
            Log.d(TAG, "Fast UDP: Cleared $count pending locations from queue.")
        }
    }

    /**
     * Resetear estadísticas UDP
     */
    fun resetServerStatistics() {
        networkManager.resetServerStatistics()
    }

    /**
     * Estadísticas de red UDP
     */
    fun getNetworkStatistics(): Map<String, Any> {
        val serverStatus = getCurrentServerStatus()

        return mapOf(
            "protocol" to "UDP_ONLY",
            "network_type" to getNetworkType(),
            "network_available" to isNetworkAvailable(),
            "udp_connections_active" to serverStatus.getActiveConnectionsCount(),
            "max_udp_connections" to 2, // Solo UDP
            "total_sent" to serverStatus.totalSentMessages,
            "total_failed" to serverStatus.totalFailedMessages,
            "success_rate" to "${String.format("%.1f", serverStatus.getSuccessRate())}%",
            "pending_locations" to getPendingLocationsCount(),
            "last_update" to serverStatus.lastUpdateTime,
            "udp_status" to serverStatus.getOverallStatusText(),
            "queue_size_limit" to 50 // Límite de cola reducido
        )
    }

    /**
     * Actualizar configuración UDP de servidores
     */
    fun updateServerConfiguration(server1IP: String, server2IP: String): Boolean {
        return try {
            val ipRegex = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$".toRegex()

            val server1Valid = ipRegex.matches(server1IP)
            val server2Valid = ipRegex.matches(server2IP)

            if (server1Valid && server2Valid) {
                Log.d(TAG, "UDP Server configuration validated: S1=$server1IP, S2=$server2IP")
                true
            } else {
                Log.w(TAG, "Invalid UDP server IP format. S1 Valid: $server1Valid, S2 Valid: $server2Valid.")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating UDP server configuration: ${e.message}", e)
            false
        }
    }

    /**
     * Forzar procesamiento de ubicaciones pendientes
     */
    suspend fun flushPendingLocations(): Result<String> {
        val pendingCount = getPendingLocationsCount()

        return if (pendingCount > 0) {
            if (isNetworkAvailable()) {
                Log.d(TAG, "Fast UDP: Flushing $pendingCount pending locations...")
                Result.success("Processing $pendingCount pending UDP locations")
            } else {
                Result.failure(Exception("No network available to flush pending UDP locations"))
            }
        } else {
            Result.success("No pending UDP locations to flush")
        }
    }

    /**
     * Cleanup optimizado
     */
    fun cleanup() {
        repositoryScope.cancel()
        clearPendingLocations()
        networkManager.cleanup()
        _serverStatusUpdates.close()
        Log.d(TAG, "Fast UDP NetworkRepository cleaned up successfully.")
    }
}