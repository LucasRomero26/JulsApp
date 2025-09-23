package com.tudominio.smslocation.model.data

/**
 * ServerStatus optimizado para 4 servidores UDP.
 * TCP eliminado completamente, solo UDP con 4 conexiones.
 */
data class ServerStatus(
    val server1UDP: ConnectionStatus = ConnectionStatus.DISCONNECTED, // UDP Server 1
    val server2UDP: ConnectionStatus = ConnectionStatus.DISCONNECTED, // UDP Server 2
    val server3UDP: ConnectionStatus = ConnectionStatus.DISCONNECTED, // UDP Server 3 (nuevo)
    val server4UDP: ConnectionStatus = ConnectionStatus.DISCONNECTED, // UDP Server 4 (nuevo)
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
        return server1UDP == ConnectionStatus.CONNECTED ||
                server2UDP == ConnectionStatus.CONNECTED ||
                server3UDP == ConnectionStatus.CONNECTED ||
                server4UDP == ConnectionStatus.CONNECTED
    }

    /**
     * Verificar si todas las conexiones UDP están activas
     */
    fun hasAllConnections(): Boolean {
        return server1UDP == ConnectionStatus.CONNECTED &&
                server2UDP == ConnectionStatus.CONNECTED &&
                server3UDP == ConnectionStatus.CONNECTED &&
                server4UDP == ConnectionStatus.CONNECTED
    }

    /**
     * Contar conexiones UDP activas (máximo 4)
     */
    fun getActiveConnectionsCount(): Int {
        var count = 0
        if (server1UDP == ConnectionStatus.CONNECTED) count++
        if (server2UDP == ConnectionStatus.CONNECTED) count++
        if (server3UDP == ConnectionStatus.CONNECTED) count++
        if (server4UDP == ConnectionStatus.CONNECTED) count++
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
            3 to "UDP" -> copy(server3UDP = status, lastUpdateTime = System.currentTimeMillis())
            4 to "UDP" -> copy(server4UDP = status, lastUpdateTime = System.currentTimeMillis())
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
     * Texto de estado general optimizado para 4 servidores UDP
     */
    fun getOverallStatusText(): String {
        val activeConnections = getActiveConnectionsCount()
        return when (activeConnections) {
            0 -> "All 4 UDP servers disconnected"
            4 -> "All 4 UDP servers connected"
            else -> "$activeConnections of 4 UDP connections active"
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
     * Información detallada UDP para 4 servidores
     */
    fun getUdpStatusDetails(): String {
        return "UDP Status - S1: $server1UDP, S2: $server2UDP, S3: $server3UDP, S4: $server4UDP, " +
                "Active: ${getActiveConnectionsCount()}/4, " +
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

    /**
     * Obtener estado de conexión por servidor
     */
    fun getServerStatus(serverNumber: Int): ConnectionStatus {
        return when (serverNumber) {
            1 -> server1UDP
            2 -> server2UDP
            3 -> server3UDP
            4 -> server4UDP
            else -> ConnectionStatus.DISCONNECTED
        }
    }

    /**
     * Verificar si hay al menos 2 conexiones activas (redundancia mínima)
     */
    fun hasMinimumRedundancy(): Boolean {
        return getActiveConnectionsCount() >= 2
    }

    /**
     * Obtener porcentaje de conectividad
     */
    fun getConnectivityPercentage(): Float {
        return (getActiveConnectionsCount().toFloat() / 4.0f) * 100f
    }
}