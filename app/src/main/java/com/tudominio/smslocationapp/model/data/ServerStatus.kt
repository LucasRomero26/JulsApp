package com.tudominio.smslocation.model.data

/**
 * ServerStatus optimizado para solo UDP (2 conexiones en lugar de 4).
 * TCP eliminado completamente.
 */
data class ServerStatus(
    val server1TCP: ConnectionStatus = ConnectionStatus.DISCONNECTED, // Siempre desconectado (eliminado)
    val server1UDP: ConnectionStatus = ConnectionStatus.DISCONNECTED, // UDP Server 1
    val server2TCP: ConnectionStatus = ConnectionStatus.DISCONNECTED, // Siempre desconectado (eliminado)
    val server2UDP: ConnectionStatus = ConnectionStatus.DISCONNECTED, // UDP Server 2
    val lastUpdateTime: Long = 0L,
    val lastSuccessfulSend: Long = 0L,
    val totalSentMessages: Int = 0,
    val totalFailedMessages: Int = 0
) {

    enum class ConnectionStatus {
        CONNECTED,
        DISCONNECTED,
        CONNECTING,
        ERROR,
        TIMEOUT
    }

    /**
     * Verificar si hay alguna conexión UDP activa
     */
    fun hasAnyConnection(): Boolean {
        // Solo considerar UDP (TCP siempre será DISCONNECTED)
        return server1UDP == ConnectionStatus.CONNECTED ||
                server2UDP == ConnectionStatus.CONNECTED
    }

    /**
     * Verificar si todas las conexiones UDP están activas
     */
    fun hasAllConnections(): Boolean {
        // Solo UDP debe estar conectado (TCP eliminado)
        return server1UDP == ConnectionStatus.CONNECTED &&
                server2UDP == ConnectionStatus.CONNECTED
    }

    /**
     * Contar conexiones UDP activas (máximo 2)
     */
    fun getActiveConnectionsCount(): Int {
        var count = 0
        // Solo contar UDP
        if (server1UDP == ConnectionStatus.CONNECTED) count++
        if (server2UDP == ConnectionStatus.CONNECTED) count++
        return count
    }

    /**
     * Tasa de éxito de envíos
     */
    fun getSuccessRate(): Float {
        val total = totalSentMessages + totalFailedMessages
        return if (total > 0) {
            (totalSentMessages.toFloat() / total.toFloat()) * 100f
        } else {
            0f
        }
    }

    /**
     * Actualizar estado de servidor específico
     * Solo UDP es relevante ahora
     */
    fun updateServerStatus(
        serverNumber: Int,
        protocol: String,
        status: ConnectionStatus
    ): ServerStatus {
        return when (serverNumber to protocol.uppercase()) {
            1 to "UDP" -> copy(server1UDP = status, lastUpdateTime = System.currentTimeMillis())
            2 to "UDP" -> copy(server2UDP = status, lastUpdateTime = System.currentTimeMillis())
            // TCP ignorado completamente
            else -> this
        }
    }

    /**
     * Incrementar contador de envíos exitosos
     */
    fun incrementSuccessfulSend(): ServerStatus {
        return copy(
            totalSentMessages = totalSentMessages + 1,
            lastSuccessfulSend = System.currentTimeMillis(),
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    /**
     * Incrementar contador de envíos fallidos
     */
    fun incrementFailedSend(): ServerStatus {
        return copy(
            totalFailedMessages = totalFailedMessages + 1,
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    /**
     * Resetear contadores
     */
    fun resetCounters(): ServerStatus {
        return copy(
            totalSentMessages = 0,
            totalFailedMessages = 0,
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    /**
     * Texto de estado general optimizado para UDP
     */
    fun getOverallStatusText(): String {
        val activeConnections = getActiveConnectionsCount()
        return when (activeConnections) {
            0 -> "All UDP servers disconnected"
            2 -> "All UDP servers connected"
            else -> "$activeConnections of 2 UDP connections active"
        }
    }

    /**
     * Verificar actividad reciente
     */
    fun hasRecentActivity(): Boolean {
        val currentTime = System.currentTimeMillis()
        val tenSecondsMillis = 10 * 1000L
        return (currentTime - lastUpdateTime) < tenSecondsMillis
    }

    /**
     * Información detallada UDP
     */
    fun getUdpStatusDetails(): String {
        return "UDP Status - Server1: $server1UDP, Server2: $server2UDP, " +
                "Active: ${getActiveConnectionsCount()}/2, " +
                "Success Rate: ${String.format("%.1f", getSuccessRate())}%"
    }

    /**
     * Verificar si el estado es óptimo (todas las conexiones UDP activas)
     */
    fun isOptimalState(): Boolean {
        return hasAllConnections() && hasRecentActivity()
    }

    /**
     * Tiempo desde la última actualización en segundos
     */
    fun getSecondsSinceLastUpdate(): Long {
        return if (lastUpdateTime > 0) {
            (System.currentTimeMillis() - lastUpdateTime) / 1000
        } else {
            0
        }
    }

    /**
     * Verificar si necesita reconexión
     */
    fun needsReconnection(): Boolean {
        return !hasAnyConnection() && getSecondsSinceLastUpdate() > 5
    }
}