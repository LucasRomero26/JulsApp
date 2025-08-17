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
 * Repository para manejo de operaciones de red y comunicación con servidores
 */
class NetworkRepository(context: Context) {

    companion object {
        private const val TAG = Constants.Logs.TAG_NETWORK
    }

    private val networkManager = NetworkManager(context)
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Canal para emisión de cambios en el estado de servidores
    private val _serverStatusUpdates = Channel<ServerStatus>(Channel.UNLIMITED)
    val serverStatusUpdates: Flow<ServerStatus> = _serverStatusUpdates.receiveAsFlow()

    // Cola de ubicaciones pendientes de envío
    private val pendingLocations = mutableListOf<LocationData>()
    private var isProcessingQueue = false

    init {
        // Configurar callback para cambios de estado de servidor
        networkManager.onServerStatusChanged = { serverStatus ->
            _serverStatusUpdates.trySend(serverStatus)
        }
    }

    /**
     * Enviar ubicación a todos los servidores configurados
     */
    suspend fun sendLocation(locationData: LocationData): Result<ServerStatus> {
        return try {
            Log.d(TAG, "Sending location as JSON: ${locationData.toJsonFormat()}")

            val serverStatus = networkManager.sendLocationToAllServers(locationData)

            // Verificar si el envío fue exitoso
            if (serverStatus.hasAnyConnection()) {
                Log.d(TAG, "JSON location sent successfully to ${serverStatus.getActiveConnectionsCount()} connections")
                Result.success(serverStatus)
            } else {
                Log.w(TAG, "Failed to send JSON location to any server")
                // Agregar a cola de pendientes si falló completamente
                addToPendingQueue(locationData)
                Result.failure(Exception("No server connections available"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error sending JSON location", e)
            addToPendingQueue(locationData)
            Result.failure(e)
        }
    }

    /**
     * Enviar múltiples ubicaciones en lote
     */
    suspend fun sendLocationBatch(locations: List<LocationData>): List<Result<ServerStatus>> {
        return locations.map { location ->
            sendLocation(location)
        }
    }

    /**
     * Verificar conectividad de red
     */
    fun isNetworkAvailable(): Boolean {
        return networkManager.isNetworkAvailable()
    }

    /**
     * Obtener información del tipo de red
     */
    fun getNetworkType(): String {
        return networkManager.getNetworkInfo()
    }

    /**
     * Testear conexiones a todos los servidores
     */
    suspend fun testServerConnections(): ServerStatus {
        Log.d(TAG, "Testing all server connections")
        return networkManager.testAllServerConnections()
    }

    /**
     * Obtener estado actual de los servidores
     */
    fun getCurrentServerStatus(): ServerStatus {
        return networkManager.getCurrentServerStatus()
    }

    /**
     * Enviar datos de prueba a todos los servidores
     */
    suspend fun sendTestData(): Result<ServerStatus> {
        return try {
            Log.d(TAG, "Sending test data to all servers")
            val serverStatus = networkManager.sendTestData()

            if (serverStatus.hasAnyConnection()) {
                Result.success(serverStatus)
            } else {
                Result.failure(Exception("Test failed - no server connections"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error sending test data", e)
            Result.failure(e)
        }
    }

    /**
     * Agregar ubicación a la cola de pendientes
     */
    private fun addToPendingQueue(locationData: LocationData) {
        synchronized(pendingLocations) {
            pendingLocations.add(locationData)

            // Limitar tamaño de la cola para evitar consumo excesivo de memoria
            if (pendingLocations.size > 100) {
                pendingLocations.removeAt(0) // Eliminar el más antiguo
                Log.w(TAG, "Pending queue full, removed oldest location")
            }

            Log.d(TAG, "Added location to pending queue. Queue size: ${pendingLocations.size}")
        }

        // Intentar procesar la cola si no se está procesando
        if (!isProcessingQueue) {
            processPendingLocations()
        }
    }

    /**
     * Procesar ubicaciones pendientes cuando la red esté disponible
     * CORREGIDO: Removidos break/continue en lambda inline
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
                    Log.d(TAG, "Processing pending location: ${location.getFormattedCoordinates()}")

                    try {
                        val result = networkManager.sendLocationToAllServers(location)

                        if (result.hasAnyConnection()) {
                            Log.d(TAG, "Pending location sent successfully")
                        } else {
                            // Si sigue fallando, volver a agregar a la cola
                            synchronized(pendingLocations) {
                                pendingLocations.add(0, location) // Agregar al inicio
                            }
                            shouldContinue = false // Salir del loop si no puede enviar
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing pending location", e)
                        // Volver a agregar a la cola
                        synchronized(pendingLocations) {
                            pendingLocations.add(0, location)
                        }
                        shouldContinue = false
                    }

                    // Pequeña pausa entre envíos para no saturar
                    if (shouldContinue) {
                        delay(500)
                    }
                }
            }

            isProcessingQueue = false

            if (pendingLocations.isNotEmpty()) {
                Log.d(TAG, "Finished processing pending locations. ${pendingLocations.size} remain")
            } else {
                Log.d(TAG, "All pending locations processed successfully")
            }
        }
    }

    /**
     * Obtener número de ubicaciones pendientes
     */
    fun getPendingLocationsCount(): Int {
        return synchronized(pendingLocations) { pendingLocations.size }
    }

    /**
     * Limpiar cola de ubicaciones pendientes
     */
    fun clearPendingLocations() {
        synchronized(pendingLocations) {
            val count = pendingLocations.size
            pendingLocations.clear()
            Log.d(TAG, "Cleared $count pending locations")
        }
    }

    /**
     * Resetear estadísticas de servidores
     */
    fun resetServerStatistics() {
        networkManager.resetServerStatistics()
    }

    /**
     * Obtener estadísticas de red
     */
    fun getNetworkStatistics(): Map<String, Any> {
        val serverStatus = getCurrentServerStatus()

        return mapOf(
            "network_type" to getNetworkType(),
            "network_available" to isNetworkAvailable(),
            "active_connections" to serverStatus.getActiveConnectionsCount(),
            "total_sent" to serverStatus.totalSentMessages,
            "total_failed" to serverStatus.totalFailedMessages,
            "success_rate" to "${String.format("%.1f", serverStatus.getSuccessRate())}%",
            "pending_locations" to getPendingLocationsCount(),
            "last_update" to serverStatus.lastUpdateTime,
            "server_status" to serverStatus.getOverallStatusText()
        )
    }

    /**
     * Configurar IPs de servidores dinámicamente (opcional)
     */
    fun updateServerConfiguration(server1IP: String, server2IP: String): Boolean {
        return try {
            // Aquí podrías implementar actualización dinámica de IPs
            // Por ahora solo validamos el formato
            val ipRegex = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$".toRegex()

            val server1Valid = ipRegex.matches(server1IP)
            val server2Valid = ipRegex.matches(server2IP)

            if (server1Valid && server2Valid) {
                Log.d(TAG, "Server configuration updated: S1=$server1IP, S2=$server2IP")
                true
            } else {
                Log.w(TAG, "Invalid server IP format")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating server configuration", e)
            false
        }
    }

    /**
     * Limpiar recursos
     */
    fun cleanup() {
        repositoryScope.cancel()
        clearPendingLocations()
        networkManager.cleanup()
        _serverStatusUpdates.close()
        Log.d(TAG, "NetworkRepository cleaned up")
    }
}