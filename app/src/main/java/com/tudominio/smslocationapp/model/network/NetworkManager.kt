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
 * Central manager for all network operations.
 * Coordinates data transmission to multiple servers via TCP and UDP protocols.
 */
class NetworkManager(private val context: Context) {

    companion object {
        // Tag for logging messages from NetworkManager.
        private const val TAG = Constants.Logs.TAG_NETWORK
    }

    // Instances of TCP and UDP clients for communication.
    private val tcpClient = TcpClient()
    private val udpClient = UdpClient()
    private val cloudflareTunnel = CloudflareTunnel(context)

    // Coroutine scope for network-related asynchronous operations.
    // Uses Dispatchers.IO for off-main-thread network tasks and SupervisorJob for fault tolerance.
    private val networkScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Callback function to notify listeners about changes in server status.
    var onServerStatusChanged: ((ServerStatus) -> Unit)? = null

    // Private variable to hold the current server status.
    private var currentServerStatus = ServerStatus()

    init {
        networkScope.launch { cloudflareTunnel.ensureTunnel() }
    }

    /**
     * Sends location data to all configured servers (Server 1 TCP/UDP, Server 2 TCP/UDP, Cloudflare Tunnel TCP).
     * This operation runs asynchronously and in parallel for each server/protocol combination.
     *
     * @param locationData The [LocationData] object to be sent.
     * @return The updated [ServerStatus] reflecting the results of the transmission attempts.
     */
    suspend fun sendLocationToAllServers(locationData: LocationData): ServerStatus {
        // First, check if network is available on the device.
        if (!isNetworkAvailable()) {
            Log.w(TAG, "No network connection available")
            // If no network, update all server statuses to DISCONNECTED and notify.
            return updateServerStatus(allDisconnected = true)
        }

        // Convert the location data to JSON format for transmission.
        val jsonData = locationData.toJsonFormat()
        Log.d(TAG, "Attempting to send location data as JSON: $jsonData")

        // Use `coroutineScope` to ensure all child coroutines (async calls) complete before returning.
        return coroutineScope {
            // Launch asynchronous tasks for each server and protocol.
            // `async` starts a new coroutine that will return a Deferred result.
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
            val cloudflareTcpDeferred = async {
                sendToServer(
                    Constants.CLOUDFLARE_LOCAL_IP,
                    Constants.CLOUDFLARE_LOCAL_PORT,
                    jsonData,
                    "TCP"
                )
            }

            // Await all deferred results. This will pause the current coroutine until all sends are attempted.
            val server1TcpResult = server1TcpDeferred.await()
            val server1UdpResult = server1UdpDeferred.await()
            val server2TcpResult = server2TcpDeferred.await()
            val server2UdpResult = server2UdpDeferred.await()
            val cloudflareTcpResult = cloudflareTcpDeferred.await()

            // Create a new ServerStatus object with the results of this send operation.
            val newStatus = currentServerStatus.copy(
                server1TCP = server1TcpResult,
                server1UDP = server1UdpResult,
                server2TCP = server2TcpResult,
                server2UDP = server2UdpResult,
                cloudflareTCP = cloudflareTcpResult,
                lastUpdateTime = System.currentTimeMillis() // Update timestamp of last activity.
            )

            // Count successful transmissions to update the total counters.
            val successCount = listOf(
                server1TcpResult,
                server1UdpResult,
                server2TcpResult,
                server2UdpResult,
                cloudflareTcpResult
            )
                .count { it == ServerStatus.ConnectionStatus.CONNECTED }

            // Increment successful or failed message counters based on at least one success.
            val finalStatus = if (successCount > 0) {
                newStatus.incrementSuccessfulSend()
            } else {
                newStatus.incrementFailedSend()
            }

            // Update the internal status and notify any observers.
            updateServerStatus(finalStatus)
            finalStatus // Return the final updated status.
        }
    }

    /**
     * Sends data to a specific server using the specified protocol (TCP or UDP).
     * This method handles the retry logic internally through the client implementations.
     *
     * @param serverIP The IP address of the target server.
     * @param port The port number for the connection.
     * @param data The string data to send.
     * @param protocol The protocol to use ("TCP" or "UDP").
     * @return The [ServerStatus.ConnectionStatus] indicating the outcome of the send operation.
     */
    private suspend fun sendToServer(
        serverIP: String,
        port: Int,
        data: String,
        protocol: String
    ): ServerStatus.ConnectionStatus {

        return try {
            val result = when (protocol.uppercase()) { // Convert protocol to uppercase for consistent matching.
                "TCP" -> {
                    // Use TcpClient to send data with retry logic.
                    when (val tcpResult = tcpClient.sendDataWithRetry(serverIP, port, data)) {
                        is TcpClient.TcpResult.Success -> ServerStatus.ConnectionStatus.CONNECTED // Data sent successfully.
                        is TcpClient.TcpResult.Timeout -> ServerStatus.ConnectionStatus.TIMEOUT // Operation timed out.
                        is TcpClient.TcpResult.Error -> ServerStatus.ConnectionStatus.ERROR     // Generic error.
                    }
                }
                "UDP" -> {
                    // Use UdpClient to send data with retry logic.
                    when (val udpResult = udpClient.sendDataWithRetry(serverIP, port, data)) {
                        is UdpClient.UdpResult.Success -> ServerStatus.ConnectionStatus.CONNECTED // Data sent successfully.
                        is UdpClient.UdpResult.Timeout -> ServerStatus.ConnectionStatus.TIMEOUT // Operation timed out.
                        is UdpClient.UdpResult.Error -> ServerStatus.ConnectionStatus.ERROR     // Generic error.
                    }
                }
                else -> {
                    Log.e(TAG, "Unknown protocol specified: $protocol")
                    ServerStatus.ConnectionStatus.ERROR // Return error for unsupported protocols.
                }
            }

            Log.d(TAG, "$protocol to $serverIP:$port - Result: $result")
            result // Return the determined connection status.

        } catch (e: Exception) {
            // Catch any unexpected exceptions during the send process.
            Log.e(TAG, "Error sending $protocol to $serverIP:$port", e)
            ServerStatus.ConnectionStatus.ERROR
        }
    }

    /**
     * Checks the device's network connectivity status.
     * It uses `ConnectivityManager` to determine if any active network connection (Wi-Fi, Cellular, Ethernet) is available.
     *
     * @return `true` if network is available, `false` otherwise.
     */
    fun isNetworkAvailable(): Boolean {
        // Get the ConnectivityManager system service.
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return try {
            // Get the currently active network. If null, no network is available.
            val network = connectivityManager.activeNetwork ?: return false
            // Get capabilities of the active network. If null, no network capabilities.
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            // Check if the network has capabilities for Wi-Fi, Cellular, or Ethernet transport.
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        } catch (e: Exception) {
            // Log any exceptions that occur during the network availability check.
            Log.e(TAG, "Error checking network availability", e)
            false // Assume network is not available if an error occurs.
        }
    }

    /**
     * Tests connectivity to all configured servers (Server 1 TCP/UDP, Server 2 TCP/UDP, Cloudflare Tunnel TCP).
     * This operation performs a lightweight connection test without sending actual data.
     *
     * @return The updated [ServerStatus] reflecting the results of the connection tests.
     */
    suspend fun testAllServerConnections(): ServerStatus = coroutineScope {
        Log.d(TAG, "Testing connections to all servers...")

        // Launch asynchronous connection tests for each server and protocol.
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
        val cloudflareTcpDeferred = async {
            tcpClient.testConnection(
                Constants.CLOUDFLARE_LOCAL_IP,
                Constants.CLOUDFLARE_LOCAL_PORT
            )
        }

        // Await the results of all connection tests.
        val server1TcpOk = server1TcpDeferred.await()
        val server1UdpOk = server1UdpDeferred.await()
        val server2TcpOk = server2TcpDeferred.await()
        val server2UdpOk = server2UdpDeferred.await()
        val cloudflareTcpOk = cloudflareTcpDeferred.await()

        // Create a new ServerStatus based on the test results.
        val newStatus = currentServerStatus.copy(
            server1TCP = if (server1TcpOk) ServerStatus.ConnectionStatus.CONNECTED else ServerStatus.ConnectionStatus.DISCONNECTED,
            server1UDP = if (server1UdpOk) ServerStatus.ConnectionStatus.CONNECTED else ServerStatus.ConnectionStatus.DISCONNECTED,
            server2TCP = if (server2TcpOk) ServerStatus.ConnectionStatus.CONNECTED else ServerStatus.ConnectionStatus.DISCONNECTED,
            server2UDP = if (server2UdpOk) ServerStatus.ConnectionStatus.CONNECTED else ServerStatus.ConnectionStatus.DISCONNECTED,
            cloudflareTCP = if (cloudflareTcpOk) ServerStatus.ConnectionStatus.CONNECTED else ServerStatus.ConnectionStatus.DISCONNECTED,
            lastUpdateTime = System.currentTimeMillis() // Update the timestamp of the last status update.
        )

        Log.d(
            TAG,
            "Connection test results: S1-TCP:$server1TcpOk, S1-UDP:$server1UdpOk, S2-TCP:$server2TcpOk, S2-UDP:$server2UdpOk, CF-TCP:$cloudflareTcpOk"
        )

        // Update the internal status and notify observers.
        updateServerStatus(newStatus)
        newStatus // Return the newly updated status.
    }

    /**
     * Retrieves the current server connection status.
     * @return The current [ServerStatus] object.
     */
    fun getCurrentServerStatus(): ServerStatus = currentServerStatus

    /**
     * Updates the internal [currentServerStatus] and notifies any registered listeners.
     * This is a private helper function to centralize status updates.
     *
     * @param newStatus An optional [ServerStatus] object to set as the new status. If null, the existing status is used.
     * @param allDisconnected If `true`, resets all server statuses to [ConnectionStatus.DISCONNECTED].
     * @return The updated [ServerStatus] object.
     */
    private fun updateServerStatus(
        newStatus: ServerStatus? = null,
        allDisconnected: Boolean = false
    ): ServerStatus {
        currentServerStatus = when {
            allDisconnected -> ServerStatus() // Set to initial disconnected state.
            newStatus != null -> newStatus   // Use the provided new status.
            else -> currentServerStatus      // Otherwise, keep the current status.
        }

        // Invoke the callback to notify listeners about the status change.
        onServerStatusChanged?.invoke(currentServerStatus)

        return currentServerStatus
    }

    /**
     * Resets the statistics related to server communication (total sent/failed messages).
     * Also notifies listeners about the status change.
     */
    fun resetServerStatistics() {
        currentServerStatus = currentServerStatus.resetCounters() // Call the reset method on the data class.
        onServerStatusChanged?.invoke(currentServerStatus) // Notify listeners.
        Log.d(TAG, "Server statistics reset")
    }

    /**
     * Retrieves the type of the currently active network connection.
     * Examples: "WiFi", "Mobile Data", "Ethernet", "No Connection", "Unknown".
     *
     * @return A string representing the network type.
     */
    fun getNetworkInfo(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return try {
            val network = connectivityManager.activeNetwork // Get the active network.
            val capabilities = connectivityManager.getNetworkCapabilities(network) // Get its capabilities.

            // Determine network type based on transport capabilities.
            when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile Data"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                else -> "No Connection" // If no specific transport is identified.
            }

        } catch (e: Exception) {
            // Log errors during network info retrieval.
            Log.e(TAG, "Error getting network info", e)
            "Unknown" // Return unknown if an error occurs.
        }
    }

    /**
     * Cleans up resources held by the `NetworkManager`.
     * This includes cancelling the coroutine scope and clearing the callback.
     * Should be called when the manager is no longer needed (e.g., app shutdown).
     */
    fun cleanup() {
        networkScope.cancel() // Cancel all coroutines launched in this scope.
        onServerStatusChanged = null // Clear the callback to prevent memory leaks.
        Log.d(TAG, "NetworkManager cleaned up")
    }

    /**
     * Sends a predefined test location data to all configured servers.
     * This is useful for verifying data transmission without real GPS data.
     *
     * @return The [ServerStatus] after attempting to send the test data.
     */
    suspend fun sendTestData(): ServerStatus {
        // Create a sample test location. The user requested to use `createTestLocation`
        // which includes `systemTimestamp` internally.
        val testLocation = LocationData.createTestLocation(
            lat = 4.123456,  // Sample latitude
            lon = -74.123456 // Sample longitude
        )

        Log.d(TAG, "Sending test JSON data to all servers: ${testLocation.toJsonFormat()}")
        // Delegate the actual sending to the `sendLocationToAllServers` method.
        return sendLocationToAllServers(testLocation)
    }
}