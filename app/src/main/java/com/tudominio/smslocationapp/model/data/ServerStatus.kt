package com.tudominio.smslocation.model.data

/**
 * Data class representing the connection status of the servers.
 * This class provides a comprehensive overview of the connectivity to different
 * server endpoints (TCP/UDP for two servers) and keeps track of transmission statistics.
 */
data class ServerStatus(
    val server1TCP: ConnectionStatus = ConnectionStatus.DISCONNECTED, // Status of Server 1 TCP connection
    val server1UDP: ConnectionStatus = ConnectionStatus.DISCONNECTED, // Status of Server 1 UDP connection
    val server2TCP: ConnectionStatus = ConnectionStatus.DISCONNECTED, // Status of Server 2 TCP connection
    val server2UDP: ConnectionStatus = ConnectionStatus.DISCONNECTED, // Status of Server 2 UDP connection
    val cloudflareTCP: ConnectionStatus = ConnectionStatus.DISCONNECTED, // Status of Cloudflare tunnel TCP connection
    val lastUpdateTime: Long = 0L,     // Timestamp of the last status update for any connection.
    val lastSuccessfulSend: Long = 0L, // Timestamp of the last successful data transmission.
    val totalSentMessages: Int = 0,    // Counter for successfully sent messages.
    val totalFailedMessages: Int = 0   // Counter for failed message transmissions.
) {

    /**
     * Enum to represent the state of a network connection.
     */
    enum class ConnectionStatus {
        CONNECTED,      // Connection is successfully established and active.
        DISCONNECTED,   // No active connection.
        CONNECTING,     // Attempting to establish a connection.
        ERROR,          // An error occurred during connection or communication.
        TIMEOUT         // The connection attempt or operation timed out.
    }

    /**
     * Checks if at least one server connection is active (in a CONNECTED state).
     * @return `true` if any connection is active, `false` otherwise.
     */
    fun hasAnyConnection(): Boolean {
        return server1TCP == ConnectionStatus.CONNECTED ||
                server1UDP == ConnectionStatus.CONNECTED ||
                server2TCP == ConnectionStatus.CONNECTED ||
                server2UDP == ConnectionStatus.CONNECTED ||
                cloudflareTCP == ConnectionStatus.CONNECTED
    }

    /**
     * Checks if all configured server connections are active (in a CONNECTED state).
     * @return `true` if all four connections are active, `false` otherwise.
     */
    fun hasAllConnections(): Boolean {
        return server1TCP == ConnectionStatus.CONNECTED &&
                server1UDP == ConnectionStatus.CONNECTED &&
                server2TCP == ConnectionStatus.CONNECTED &&
                server2UDP == ConnectionStatus.CONNECTED &&
                cloudflareTCP == ConnectionStatus.CONNECTED
    }

    /**
     * Returns the number of currently active (CONNECTED) server connections.
     * @return An integer representing the count of active connections.
     */
    fun getActiveConnectionsCount(): Int {
        var count = 0
        if (server1TCP == ConnectionStatus.CONNECTED) count++
        if (server1UDP == ConnectionStatus.CONNECTED) count++
        if (server2TCP == ConnectionStatus.CONNECTED) count++
        if (server2UDP == ConnectionStatus.CONNECTED) count++
        if (cloudflareTCP == ConnectionStatus.CONNECTED) count++
        return count
    }

    /**
     * Calculates the success rate of message transmissions as a percentage.
     * @return A float value representing the success rate (0-100), or 0 if no messages have been attempted.
     */
    fun getSuccessRate(): Float {
        val total = totalSentMessages + totalFailedMessages
        return if (total > 0) {
            (totalSentMessages.toFloat() / total.toFloat()) * 100f
        } else {
            0f // Avoid division by zero if no messages have been sent or failed.
        }
    }

    /**
     * Updates the connection status for a specific server and protocol.
     * This is an immutable update, returning a new `ServerStatus` instance.
     * @param serverNumber The identifier for the server (1 or 2).
     * @param protocol The protocol used ("TCP" or "UDP"). Case-insensitive.
     * @param status The new [ConnectionStatus] to set for the specified connection.
     * @return A new `ServerStatus` object with the updated connection status and `lastUpdateTime`.
     */
    fun updateServerStatus(
        serverNumber: Int,
        protocol: String,
        status: ConnectionStatus
    ): ServerStatus {
        // Use `when` expression for concise conditional updates.
        return when (serverNumber to protocol.uppercase()) { // Convert protocol to uppercase for robust matching.
            1 to "TCP" -> copy(server1TCP = status, lastUpdateTime = System.currentTimeMillis())
            1 to "UDP" -> copy(server1UDP = status, lastUpdateTime = System.currentTimeMillis())
            2 to "TCP" -> copy(server2TCP = status, lastUpdateTime = System.currentTimeMillis())
            2 to "UDP" -> copy(server2UDP = status, lastUpdateTime = System.currentTimeMillis())
            3 to "TCP" -> copy(cloudflareTCP = status, lastUpdateTime = System.currentTimeMillis())
            else -> this // If no match, return the current instance unchanged.
        }
    }

    /**
     * Increments the counter for successfully sent messages.
     * Also updates `lastSuccessfulSend` and `lastUpdateTime`.
     * @return A new `ServerStatus` object with updated counters and timestamps.
     */
    fun incrementSuccessfulSend(): ServerStatus {
        return copy(
            totalSentMessages = totalSentMessages + 1,
            lastSuccessfulSend = System.currentTimeMillis(), // Record the time of this success.
            lastUpdateTime = System.currentTimeMillis() // Update the general last activity time.
        )
    }

    /**
     * Increments the counter for failed message transmissions.
     * Also updates `lastUpdateTime`.
     * @return A new `ServerStatus` object with updated counters and timestamps.
     */
    fun incrementFailedSend(): ServerStatus {
        return copy(
            totalFailedMessages = totalFailedMessages + 1,
            lastUpdateTime = System.currentTimeMillis() // Update the general last activity time.
        )
    }

    /**
     * Resets the message counters (successful and failed sends) to zero.
     * Also updates `lastUpdateTime`.
     * @return A new `ServerStatus` object with reset counters.
     */
    fun resetCounters(): ServerStatus {
        return copy(
            totalSentMessages = 0,
            totalFailedMessages = 0,
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    /**
     * Provides a summary string of the overall server connection status.
     * @return A descriptive string like "All servers connected", "1 of 5 connections active", etc.
     */
    fun getOverallStatusText(): String {
        val activeConnections = getActiveConnectionsCount()
        val totalConnections = 5
        return when (activeConnections) {
            0 -> "All servers disconnected"
            totalConnections -> "All servers connected"
            else -> "$activeConnections of $totalConnections connections active" // E.g., "1 of 5 connections active"
        }
    }

    /**
     * Checks if there has been any recent activity (status update or send attempt) within the last 10 seconds.
     * This can be used to determine if the connection status information is fresh.
     * @return `true` if `lastUpdateTime` is within the last 10 seconds, `false` otherwise.
     */
    fun hasRecentActivity(): Boolean {
        val currentTime = System.currentTimeMillis()
        val tenSecondsMillis = 10 * 1000L // 10 seconds in milliseconds.
        return (currentTime - lastUpdateTime) < tenSecondsMillis
    }
}