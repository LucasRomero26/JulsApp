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
 * Repository for handling network operations and communication with servers.
 * This class acts as an intermediary between the application's business logic
 * and the low-level network communication provided by [NetworkManager].
 * It manages sending location data, testing server connections, and queuing
 * failed transmissions for later retry.
 */
class NetworkRepository(context: Context) {

    companion object {
        // Tag for logging messages from NetworkRepository.
        private const val TAG = Constants.Logs.TAG_NETWORK
    }

    // Instance of the NetworkManager to perform actual network calls.
    private val networkManager = NetworkManager(context)
    // Coroutine scope for operations within this repository.
    // Uses Dispatchers.IO for network tasks and SupervisorJob for fault tolerance.
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // A Kotlin Channel to emit changes in the server status.
    // Channel.UNLIMITED allows for a buffer that grows indefinitely.
    private val _serverStatusUpdates = Channel<ServerStatus>(Channel.UNLIMITED)
    // Exposed as a Flow for external collectors to observe server status changes.
    val serverStatusUpdates: Flow<ServerStatus> = _serverStatusUpdates.receiveAsFlow()

    // A mutable list to store location data that failed to send, acting as a retry queue.
    private val pendingLocations = mutableListOf<LocationData>()
    private var isProcessingQueue = false // Flag to prevent multiple concurrent queue processing jobs.

    init {
        // Configure the callback for server status changes from the NetworkManager.
        // This ensures that any status updates from the underlying network layer are propagated
        // through this repository's flow.
        networkManager.onServerStatusChanged = { serverStatus ->
            _serverStatusUpdates.trySend(serverStatus) // Non-suspending send to the channel.
        }
    }

    /**
     * Sends location data to all configured servers.
     * If the transmission fails to all servers, the location data is added to a pending queue for retry.
     *
     * @param locationData The [LocationData] object to send.
     * @return A [Result] indicating success with the updated [ServerStatus], or failure with an [Exception].
     */
    suspend fun sendLocation(locationData: LocationData): Result<ServerStatus> {
        return try {
            Log.d(TAG, "Attempting to send location as JSON: ${locationData.toJsonFormat()}")

            // Delegate the actual sending to the NetworkManager.
            val serverStatus = networkManager.sendLocationToAllServers(locationData)

            // Check if at least one connection was successful.
            if (serverStatus.hasAnyConnection()) {
                Log.d(TAG, "JSON location sent successfully to ${serverStatus.getActiveConnectionsCount()} connections.")
                Result.success(serverStatus)
            } else {
                Log.w(TAG, "Failed to send JSON location to any server.")
                // If no connection was successful, add the location to the pending queue for retry.
                addToPendingQueue(locationData)
                Result.failure(Exception("No server connections available or all failed."))
            }

        } catch (e: Exception) {
            // Catch any unexpected errors during the send operation.
            Log.e(TAG, "Error sending JSON location: ${e.message}", e)
            addToPendingQueue(locationData) // Add to queue even on unexpected errors.
            Result.failure(e)
        }
    }

    /**
     * Sends a list of location data objects in a batch.
     * Each location in the list is processed individually.
     * @param locations A [List] of [LocationData] objects to send.
     * @return A [List] of [Result<ServerStatus>] for each location in the batch.
     */
    suspend fun sendLocationBatch(locations: List<LocationData>): List<Result<ServerStatus>> {
        // Map each location in the list to a send operation and collect their results.
        return locations.map { location ->
            sendLocation(location)
        }
    }

    /**
     * Checks if the device has active network connectivity.
     * @return `true` if network is available, `false` otherwise.
     */
    fun isNetworkAvailable(): Boolean {
        return networkManager.isNetworkAvailable()
    }

    /**
     * Retrieves the type of the currently active network connection (e.g., "WiFi", "Mobile Data").
     * @return A string describing the network type.
     */
    fun getNetworkType(): String {
        return networkManager.getNetworkInfo()
    }

    /**
     * Tests connectivity to all configured servers.
     * @return The updated [ServerStatus] reflecting the results of the connection tests.
     */
    suspend fun testServerConnections(): ServerStatus {
        Log.d(TAG, "Initiating test of all server connections.")
        return networkManager.testAllServerConnections()
    }

    /**
     * Retrieves the current [ServerStatus] from the [NetworkManager].
     * @return The current server connection status.
     */
    fun getCurrentServerStatus(): ServerStatus {
        return networkManager.getCurrentServerStatus()
    }

    /**
     * Sends a predefined test data packet to all configured servers.
     * This can be used to verify data transmission independent of actual GPS readings.
     * @return A [Result] indicating success with the updated [ServerStatus], or failure.
     */
    suspend fun sendTestData(): Result<ServerStatus> {
        return try {
            Log.d(TAG, "Attempting to send test data to all servers.")
            // Delegate sending test data to NetworkManager.
            val serverStatus = networkManager.sendTestData()

            // Check if test data was sent successfully to at least one server.
            if (serverStatus.hasAnyConnection()) {
                Result.success(serverStatus)
            } else {
                Result.failure(Exception("Test data send failed - no server connections."))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error sending test data: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Adds a [LocationData] object to the pending queue.
     * This queue holds locations that failed to send immediately and will be retried later.
     * The queue has a size limit to prevent excessive memory consumption.
     *
     * @param locationData The [LocationData] to add to the queue.
     */
    private fun addToPendingQueue(locationData: LocationData) {
        // Synchronized block to ensure thread-safe access to the mutable list.
        synchronized(pendingLocations) {
            pendingLocations.add(locationData)

            // Limit the queue size to avoid excessive memory usage.
            if (pendingLocations.size > 100) { // Example limit: 100 locations.
                pendingLocations.removeAt(0) // Remove the oldest location if the queue is full.
                Log.w(TAG, "Pending queue reached max size (100), removed oldest location.")
            }

            Log.d(TAG, "Added location to pending queue. Current queue size: ${pendingLocations.size}")
        }

        // Attempt to process the queue immediately if it's not already being processed.
        if (!isProcessingQueue) {
            processPendingLocations()
        }
    }

    /**
     * Processes the queue of pending location data.
     * This coroutine will attempt to send locations from the queue when network is available.
     * It stops processing if the network becomes unavailable or a transmission repeatedly fails.
     *
     * The user specifically requested: **CORREGIDO: Removidos break/continue en lambda inline**
     * This means ensuring loop control flow (like breaking from the while loop) is handled
     * without using `break` or `continue` directly within a lambda passed to collection functions.
     * Instead, a flag `shouldContinue` is used to control the `while` loop externally.
     */
    private fun processPendingLocations() {
        // Launch a coroutine in the repository's scope.
        repositoryScope.launch {
            isProcessingQueue = true // Set flag to indicate queue processing is active.

            var shouldContinue = true // Flag to control the outer while loop.
            // Loop as long as there are pending locations, network is available, and `shouldContinue` is true.
            while (pendingLocations.isNotEmpty() && isNetworkAvailable() && shouldContinue) {
                val location = synchronized(pendingLocations) {
                    // Safely remove the first location from the queue.
                    if (pendingLocations.isNotEmpty()) {
                        pendingLocations.removeAt(0)
                    } else {
                        null // If queue becomes empty during synchronization, return null.
                    }
                }

                if (location != null) {
                    Log.d(TAG, "Processing pending location: ${location.getFormattedCoordinates()}")

                    try {
                        val result = networkManager.sendLocationToAllServers(location)

                        if (result.hasAnyConnection()) {
                            Log.d(TAG, "Pending location sent successfully during retry.")
                            // If successful, `shouldContinue` remains true, and the loop proceeds to the next item.
                        } else {
                            // If still failing, re-add the location to the front of the queue
                            // so it's attempted first in the next processing cycle.
                            synchronized(pendingLocations) {
                                pendingLocations.add(0, location)
                            }
                            shouldContinue = false // Stop processing this cycle if a send fails.
                            Log.w(TAG, "Failed to send pending location, will retry later. Location re-queued.")
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing pending location: ${e.message}", e)
                        // If an error occurs, re-add and stop this cycle.
                        synchronized(pendingLocations) {
                            pendingLocations.add(0, location)
                        }
                        shouldContinue = false
                        Log.e(TAG, "Error processing pending location, re-queued for next attempt.")
                    }

                    // Introduce a small delay between sending each pending location to prevent network saturation.
                    if (shouldContinue) { // Only delay if we are continuing to the next item.
                        delay(500) // 500ms delay.
                    }
                } else {
                    // This can happen if the queue becomes empty after checking `pendingLocations.isNotEmpty()`
                    // but before acquiring the lock and removing an item.
                    shouldContinue = false // Exit the loop if no location was found to process.
                }
            }

            isProcessingQueue = false // Reset flag once processing is complete or stopped.

            if (pendingLocations.isNotEmpty()) {
                Log.d(TAG, "Finished processing pending locations. ${pendingLocations.size} locations remain in queue.")
            } else {
                Log.d(TAG, "All pending locations processed successfully. Queue is empty.")
            }
        }
    }

    /**
     * Retrieves the current number of locations waiting in the pending queue.
     * @return The size of the `pendingLocations` queue.
     */
    fun getPendingLocationsCount(): Int {
        return synchronized(pendingLocations) { pendingLocations.size } // Thread-safe access.
    }

    /**
     * Clears all locations from the pending queue.
     */
    fun clearPendingLocations() {
        synchronized(pendingLocations) {
            val count = pendingLocations.size
            pendingLocations.clear() // Remove all elements.
            Log.d(TAG, "Cleared $count pending locations from the queue.")
        }
    }

    /**
     * Resets the server communication statistics maintained by the [NetworkManager].
     */
    fun resetServerStatistics() {
        networkManager.resetServerStatistics()
    }

    /**
     * Gathers and returns various network-related statistics and status information.
     * @return A [Map] containing key-value pairs of network statistics.
     */
    fun getNetworkStatistics(): Map<String, Any> {
        val serverStatus = getCurrentServerStatus() // Get the latest server status.

        return mapOf(
            "network_type" to getNetworkType(), // Type of network (WiFi, Mobile Data, etc.).
            "network_available" to isNetworkAvailable(), // Whether network connectivity is available.
            "active_connections" to serverStatus.getActiveConnectionsCount(), // Number of currently active server connections.
            "total_sent" to serverStatus.totalSentMessages, // Total messages successfully sent.
            "total_failed" to serverStatus.totalFailedMessages, // Total messages that failed to send.
            "success_rate" to "${String.format("%.1f", serverStatus.getSuccessRate())}%", // Percentage of successful sends.
            "pending_locations" to getPendingLocationsCount(), // Number of locations in the retry queue.
            "last_update" to serverStatus.lastUpdateTime, // Timestamp of the last status update.
            "server_status" to serverStatus.getOverallStatusText() // A summary string of server connectivity.
        )
    }

    /**
     * Updates the IP addresses for the configured servers dynamically.
     * This method currently only validates the format of the provided IP addresses.
     * In a real application, this would involve updating persistent storage or `Constants`.
     *
     * @param server1IP The new IP address for Server 1.
     * @param server2IP The new IP address for Server 2.
     * @return `true` if the IPs are valid, `false` otherwise.
     */
    fun updateServerConfiguration(server1IP: String, server2IP: String): Boolean {
        return try {
            // Simple regex for basic IP address validation.
            val ipRegex = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$".toRegex()

            val server1Valid = ipRegex.matches(server1IP)
            val server2Valid = ipRegex.matches(server2IP)

            if (server1Valid && server2Valid) {
                // In a real scenario, you'd save these IPs to Preferences/Database
                // and update the Constants or inject them into NetworkManager.
                Log.d(TAG, "Server configuration updated: S1=$server1IP, S2=$server2IP. (Note: IPs not dynamically applied to Constants yet)")
                true
            } else {
                Log.w(TAG, "Invalid server IP format provided. S1 Valid: $server1Valid, S2 Valid: $server2Valid.")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating server configuration: ${e.message}", e)
            false
        }
    }

    /**
     * Cleans up resources held by the NetworkRepository.
     * This includes cancelling the repository's coroutine scope, clearing the pending queue,
     * and cleaning up the underlying [NetworkManager] and server status channel.
     * Should be called when the repository is no longer needed (e.g., app shutdown).
     */
    fun cleanup() {
        repositoryScope.cancel() // Cancel all coroutines launched in this scope.
        clearPendingLocations() // Clear any remaining pending locations.
        networkManager.cleanup() // Delegate cleanup to the NetworkManager.
        _serverStatusUpdates.close() // Close the channel to prevent further emissions.
        Log.d(TAG, "NetworkRepository cleaned up successfully.")
    }
}