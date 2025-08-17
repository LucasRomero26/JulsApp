package com.tudominio.smslocation.model.data

/**
 * Data class que representa el estado de conexión de los servidores
 */
data class ServerStatus(
    val server1TCP: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val server1UDP: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val server2TCP: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val server2UDP: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val lastUpdateTime: Long = 0L,
    val lastSuccessfulSend: Long = 0L,
    val totalSentMessages: Int = 0,
    val totalFailedMessages: Int = 0
) {

    /**
     * Enum para representar el estado de conexión
     */
    enum class ConnectionStatus {
        CONNECTED,      // Conexión exitosa
        DISCONNECTED,   // Sin conexión
        CONNECTING,     // Intentando conectar
        ERROR,          // Error en la conexión
        TIMEOUT         // Timeout en la conexión
    }

    /**
     * Verificar si al menos un servidor está conectado
     */
    fun hasAnyConnection(): Boolean {
        return server1TCP == ConnectionStatus.CONNECTED ||
                server1UDP == ConnectionStatus.CONNECTED ||
                server2TCP == ConnectionStatus.CONNECTED ||
                server2UDP == ConnectionStatus.CONNECTED
    }

    /**
     * Verificar si todos los servidores están conectados
     */
    fun hasAllConnections(): Boolean {
        return server1TCP == ConnectionStatus.CONNECTED &&
                server1UDP == ConnectionStatus.CONNECTED &&
                server2TCP == ConnectionStatus.CONNECTED &&
                server2UDP == ConnectionStatus.CONNECTED
    }

    /**
     * Obtener número de conexiones activas
     */
    fun getActiveConnectionsCount(): Int {
        var count = 0
        if (server1TCP == ConnectionStatus.CONNECTED) count++
        if (server1UDP == ConnectionStatus.CONNECTED) count++
        if (server2TCP == ConnectionStatus.CONNECTED) count++
        if (server2UDP == ConnectionStatus.CONNECTED) count++
        return count
    }

    /**
     * Obtener porcentaje de éxito en envíos
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
     */
    fun updateServerStatus(
        serverNumber: Int,
        protocol: String,
        status: ConnectionStatus
    ): ServerStatus {
        return when (serverNumber to protocol.uppercase()) {
            1 to "TCP" -> copy(server1TCP = status, lastUpdateTime = System.currentTimeMillis())
            1 to "UDP" -> copy(server1UDP = status, lastUpdateTime = System.currentTimeMillis())
            2 to "TCP" -> copy(server2TCP = status, lastUpdateTime = System.currentTimeMillis())
            2 to "UDP" -> copy(server2UDP = status, lastUpdateTime = System.currentTimeMillis())
            else -> this
        }
    }

    /**
     * Incrementar contador de mensajes exitosos
     */
    fun incrementSuccessfulSend(): ServerStatus {
        return copy(
            totalSentMessages = totalSentMessages + 1,
            lastSuccessfulSend = System.currentTimeMillis(),
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    /**
     * Incrementar contador de mensajes fallidos
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
     * Obtener estado general como string
     */
    fun getOverallStatusText(): String {
        val activeConnections = getActiveConnectionsCount()
        return when (activeConnections) {
            0 -> "All servers disconnected"
            4 -> "All servers connected"
            else -> "$activeConnections of 4 connections active"
        }
    }

    /**
     * Verificar si hubo actividad reciente (últimos 10 segundos)
     */
    fun hasRecentActivity(): Boolean {
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastUpdateTime) < 10000 // 10 segundos
    }
}