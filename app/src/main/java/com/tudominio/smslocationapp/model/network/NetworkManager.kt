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
 * Gestor central de todas las operaciones de red
 * Coordina el envío de datos a múltiples servidores via TCP y UDP
 */
class NetworkManager(private val context: Context) {

    companion object {
        private const val TAG = Constants.Logs.TAG_NETWORK
    }

    private val tcpClient = TcpClient()
    private val udpClient = UdpClient()
    private val networkScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Callback para notificar cambios en el estado de los servidores
    var onServerStatusChanged: ((ServerStatus) -> Unit)? = null

    private var currentServerStatus = ServerStatus()

    /**
     * Enviar datos de ubicación a todos los servidores configurados
     *
     * @param locationData Datos de ubicación a enviar
     * @return ServerStatus actualizado con los resultados
     */
    suspend fun sendLocationToAllServers(locationData: LocationData): ServerStatus {
        if (!isNetworkAvailable()) {
            Log.w(TAG, "No network connection available")
            return updateServerStatus(allDisconnected = true)
        }

        val jsonData = locationData.toJsonFormat()
        Log.d(TAG, "Sending location data as JSON: $jsonData")

        // Enviar a todos los servidores en paralelo
        return coroutineScope {
            val server1TcpDeferred = async {
                sendToServer(Constants.SERVER_IP_1, Constants.TCP_PORT, jsonData, "TCP")
            }
            val server1UdpDeferred = async {
                sendToServer(Constants.SERVER_IP_1, Constants.UDP_PORT, jsonData, "UDP")
            }
            val server2TcpDeferred = async {
                sendToServer(Constants.SERVER_IP_2, Constants.TCP_PORT, jsonData, "TCP")
            }
            val server2UdpDeferred = async {
                sendToServer(Constants.SERVER_IP_2, Constants.UDP_PORT, jsonData, "UDP")
            }

            // Esperar todos los resultados
            val server1TcpResult = server1TcpDeferred.await()
            val server1UdpResult = server1UdpDeferred.await()
            val server2TcpResult = server2TcpDeferred.await()
            val server2UdpResult = server2UdpDeferred.await()

            // Actualizar estado de servidores
            val newStatus = currentServerStatus.copy(
                server1TCP = server1TcpResult,
                server1UDP = server1UdpResult,
                server2TCP = server2TcpResult,
                server2UDP = server2UdpResult,
                lastUpdateTime = System.currentTimeMillis()
            )

            // Incrementar contadores
            val successCount = listOf(server1TcpResult, server1UdpResult, server2TcpResult, server2UdpResult)
                .count { it == ServerStatus.ConnectionStatus.CONNECTED }

            val finalStatus = if (successCount > 0) {
                newStatus.incrementSuccessfulSend()
            } else {
                newStatus.incrementFailedSend()
            }

            updateServerStatus(finalStatus)
            finalStatus
        }
    }

    /**
     * Enviar datos a un servidor específico con protocolo específico
     */
    private suspend fun sendToServer(
        serverIP: String,
        port: Int,
        data: String,
        protocol: String
    ): ServerStatus.ConnectionStatus {

        return try {
            val result = when (protocol.uppercase()) {
                "TCP" -> {
                    when (val tcpResult = tcpClient.sendDataWithRetry(serverIP, port, data)) {
                        is TcpClient.TcpResult.Success -> ServerStatus.ConnectionStatus.CONNECTED
                        is TcpClient.TcpResult.Timeout -> ServerStatus.ConnectionStatus.TIMEOUT
                        is TcpClient.TcpResult.Error -> ServerStatus.ConnectionStatus.ERROR
                    }
                }
                "UDP" -> {
                    when (val udpResult = udpClient.sendDataWithRetry(serverIP, port, data)) {
                        is UdpClient.UdpResult.Success -> ServerStatus.ConnectionStatus.CONNECTED
                        is UdpClient.UdpResult.Timeout -> ServerStatus.ConnectionStatus.TIMEOUT
                        is UdpClient.UdpResult.Error -> ServerStatus.ConnectionStatus.ERROR
                    }
                }
                else -> {
                    Log.e(TAG, "Unknown protocol: $protocol")
                    ServerStatus.ConnectionStatus.ERROR
                }
            }

            Log.d(TAG, "$protocol to $serverIP:$port - Result: $result")
            result

        } catch (e: Exception) {
            Log.e(TAG, "Error sending $protocol to $serverIP:$port", e)
            ServerStatus.ConnectionStatus.ERROR
        }
    }

    /**
     * Verificar conectividad de red del dispositivo
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
     * Testear conectividad a todos los servidores
     */
    suspend fun testAllServerConnections(): ServerStatus = coroutineScope {
        Log.d(TAG, "Testing connections to all servers...")

        val server1TcpDeferred = async {
            tcpClient.testConnection(Constants.SERVER_IP_1, Constants.TCP_PORT)
        }
        val server1UdpDeferred = async {
            udpClient.testConnection(Constants.SERVER_IP_1, Constants.UDP_PORT)
        }
        val server2TcpDeferred = async {
            tcpClient.testConnection(Constants.SERVER_IP_2, Constants.TCP_PORT)
        }
        val server2UdpDeferred = async {
            udpClient.testConnection(Constants.SERVER_IP_2, Constants.UDP_PORT)
        }

        val server1TcpOk = server1TcpDeferred.await()
        val server1UdpOk = server1UdpDeferred.await()
        val server2TcpOk = server2TcpDeferred.await()
        val server2UdpOk = server2UdpDeferred.await()

        val newStatus = currentServerStatus.copy(
            server1TCP = if (server1TcpOk) ServerStatus.ConnectionStatus.CONNECTED else ServerStatus.ConnectionStatus.DISCONNECTED,
            server1UDP = if (server1UdpOk) ServerStatus.ConnectionStatus.CONNECTED else ServerStatus.ConnectionStatus.DISCONNECTED,
            server2TCP = if (server2TcpOk) ServerStatus.ConnectionStatus.CONNECTED else ServerStatus.ConnectionStatus.DISCONNECTED,
            server2UDP = if (server2UdpOk) ServerStatus.ConnectionStatus.CONNECTED else ServerStatus.ConnectionStatus.DISCONNECTED,
            lastUpdateTime = System.currentTimeMillis()
        )

        Log.d(TAG, "Connection test results: S1-TCP:$server1TcpOk, S1-UDP:$server1UdpOk, S2-TCP:$server2TcpOk, S2-UDP:$server2UdpOk")

        updateServerStatus(newStatus)
        newStatus
    }

    /**
     * Obtener estado actual de los servidores
     */
    fun getCurrentServerStatus(): ServerStatus = currentServerStatus

    /**
     * Actualizar estado de servidores y notificar cambios
     */
    private fun updateServerStatus(
        newStatus: ServerStatus? = null,
        allDisconnected: Boolean = false
    ): ServerStatus {

        currentServerStatus = when {
            allDisconnected -> ServerStatus() // Estado inicial con todo desconectado
            newStatus != null -> newStatus
            else -> currentServerStatus
        }

        // Notificar cambios
        onServerStatusChanged?.invoke(currentServerStatus)

        return currentServerStatus
    }

    /**
     * Resetear estadísticas de los servidores
     */
    fun resetServerStatistics() {
        currentServerStatus = currentServerStatus.resetCounters()
        onServerStatusChanged?.invoke(currentServerStatus)
        Log.d(TAG, "Server statistics reset")
    }

    /**
     * Obtener información de conectividad de red
     */
    fun getNetworkInfo(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return try {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile Data"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                else -> "No Connection"
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting network info", e)
            "Unknown"
        }
    }

    /**
     * Limpiar recursos
     */
    fun cleanup() {
        networkScope.cancel()
        onServerStatusChanged = null
        Log.d(TAG, "NetworkManager cleaned up")
    }

    /**
     * Enviar datos de prueba a todos los servidores
     * CORREGIDO: Usar el método createTestLocation que incluye systemTimestamp
     */
    suspend fun sendTestData(): ServerStatus {
        val testLocation = LocationData.createTestLocation(
            lat = 4.123456,
            lon = -74.123456
        )

        Log.d(TAG, "Sending test JSON data to all servers: ${testLocation.toJsonFormat()}")
        return sendLocationToAllServers(testLocation)
    }
}