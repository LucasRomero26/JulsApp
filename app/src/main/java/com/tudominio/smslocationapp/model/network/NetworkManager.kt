package com.tudominio.smslocation.model.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.tudominio.smslocation.model.data.LocationData
import com.tudominio.smslocation.model.data.ServerStatus
import com.tudominio.smslocation.util.Constants
import kotlinx.coroutines.*

/**
 * NetworkManager optimizado para solo UDP con máxima velocidad.
 * Elimina TCP completamente y optimiza UDP.
 */
class NetworkManager(private val context: Context) {

    companion object {
        private const val TAG = Constants.Logs.TAG_NETWORK
    }

    // Solo cliente UDP - TCP eliminado
    private val udpClient = UdpClient()

    private val networkScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onServerStatusChanged: ((ServerStatus) -> Unit)? = null

    private var currentServerStatus = ServerStatus()

    /**
     * Envío UDP ultra-rápido a ambos servidores.
     * Solo 2 conexiones UDP en paralelo.
     */
    suspend fun sendLocationToAllServers(locationData: LocationData): ServerStatus {
        if (!isNetworkAvailable()) {
            Log.w(TAG, "No network connection available")
            return updateServerStatus(allDisconnected = true)
        }

        val jsonData = locationData.toJsonFormat()
        Log.d(TAG, "Fast UDP: Sending location JSON to 2 servers: $jsonData")

        return coroutineScope {
            // Solo envío UDP - máxima velocidad en paralelo
            val server1UdpDeferred = async {
                sendToUdpServer(Constants.SERVER_IP_1, Constants.UDP_PORT, jsonData)
            }
            val server2UdpDeferred = async {
                sendToUdpServer(Constants.SERVER_IP_2, Constants.UDP_PORT, jsonData)
            }

            // Esperar resultados UDP
            val server1UdpResult = server1UdpDeferred.await()
            val server2UdpResult = server2UdpDeferred.await()

            // Crear estado con TCP siempre desconectado
            val newStatus = currentServerStatus.copy(
                server1TCP = ServerStatus.ConnectionStatus.DISCONNECTED, // TCP eliminado
                server1UDP = server1UdpResult,
                server2TCP = ServerStatus.ConnectionStatus.DISCONNECTED, // TCP eliminado
                server2UDP = server2UdpResult,
                lastUpdateTime = System.currentTimeMillis()
            )

            // Contar éxitos UDP únicamente
            val successCount = listOf(server1UdpResult, server2UdpResult)
                .count { it == ServerStatus.ConnectionStatus.CONNECTED }

            val finalStatus = if (successCount > 0) {
                newStatus.incrementSuccessfulSend()
            } else {
                newStatus.incrementFailedSend()
            }

            Log.d(TAG, "Fast UDP: Results - S1: $server1UdpResult, S2: $server2UdpResult ($successCount/2 success)")

            updateServerStatus(finalStatus)
            finalStatus
        }
    }

    /**
     * Envío UDP optimizado con manejo de resultados
     */
    private suspend fun sendToUdpServer(
        serverIP: String,
        port: Int,
        data: String
    ): ServerStatus.ConnectionStatus {

        return try {
            when (val udpResult = udpClient.sendDataWithRetry(serverIP, port, data)) {
                is UdpClient.UdpResult.Success -> {
                    Log.d(TAG, "Fast UDP: SUCCESS to $serverIP:$port")
                    ServerStatus.ConnectionStatus.CONNECTED
                }
                is UdpClient.UdpResult.Timeout -> {
                    Log.w(TAG, "Fast UDP: TIMEOUT to $serverIP:$port")
                    ServerStatus.ConnectionStatus.TIMEOUT
                }
                is UdpClient.UdpResult.Error -> {
                    Log.e(TAG, "Fast UDP: ERROR to $serverIP:$port - ${udpResult.message}")
                    ServerStatus.ConnectionStatus.ERROR
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Fast UDP: Exception sending to $serverIP:$port", e)
            ServerStatus.ConnectionStatus.ERROR
        }
    }

    /**
     * Test de conectividad solo UDP
     */
    suspend fun testAllServerConnections(): ServerStatus = coroutineScope {
        Log.d(TAG, "Testing UDP connections to both servers...")

        val server1UdpDeferred = async {
            udpClient.testConnection(Constants.SERVER_IP_1, Constants.UDP_PORT)
        }
        val server2UdpDeferred = async {
            udpClient.testConnection(Constants.SERVER_IP_2, Constants.UDP_PORT)
        }

        val server1UdpOk = server1UdpDeferred.await()
        val server2UdpOk = server2UdpDeferred.await()

        val newStatus = currentServerStatus.copy(
            server1TCP = ServerStatus.ConnectionStatus.DISCONNECTED, // TCP siempre desconectado
            server1UDP = if (server1UdpOk) ServerStatus.ConnectionStatus.CONNECTED else ServerStatus.ConnectionStatus.DISCONNECTED,
            server2TCP = ServerStatus.ConnectionStatus.DISCONNECTED, // TCP siempre desconectado
            server2UDP = if (server2UdpOk) ServerStatus.ConnectionStatus.CONNECTED else ServerStatus.ConnectionStatus.DISCONNECTED,
            lastUpdateTime = System.currentTimeMillis()
        )

        Log.d(TAG, "UDP connection test results: S1-UDP:$server1UdpOk, S2-UDP:$server2UdpOk")

        updateServerStatus(newStatus)
        newStatus
    }

    /**
     * Verificación de red estándar
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        } catch (e: Exception) {
            Log.e(TAG, "Error checking network availability", e)
            false
        }
    }

    /**
     * Estado actual del servidor
     */
    fun getCurrentServerStatus(): ServerStatus = currentServerStatus

    /**
     * Actualizar estado del servidor
     */
    private fun updateServerStatus(
        newStatus: ServerStatus? = null,
        allDisconnected: Boolean = false
    ): ServerStatus {
        currentServerStatus = when {
            allDisconnected -> ServerStatus() // Estado inicial desconectado
            newStatus != null -> newStatus
            else -> currentServerStatus
        }

        onServerStatusChanged?.invoke(currentServerStatus)
        return currentServerStatus
    }

    /**
     * Resetear estadísticas
     */
    fun resetServerStatistics() {
        currentServerStatus = currentServerStatus.resetCounters()
        onServerStatusChanged?.invoke(currentServerStatus)
        Log.d(TAG, "UDP server statistics reset")
    }

    /**
     * Información de red
     */
    fun getNetworkInfo(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return try {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi (UDP optimized)"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile Data (UDP optimized)"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet (UDP optimized)"
                else -> "No Connection"
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting network info", e)
            "Unknown Network"
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        networkScope.cancel()
        onServerStatusChanged = null
        Log.d(TAG, "NetworkManager (UDP only) cleaned up")
    }

    /**
     * Envío de datos de test UDP
     */
    suspend fun sendTestData(): ServerStatus {
        val testLocation = LocationData.createTestLocation(
            lat = 4.123456,
            lon = -74.123456
        )

        Log.d(TAG, "Sending test UDP data to both servers: ${testLocation.toJsonFormat()}")
        return sendLocationToAllServers(testLocation)
    }
}