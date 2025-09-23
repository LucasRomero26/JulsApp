package com.tudominio.smslocation.model.data

/**
 * Data class representing the global application state.
 * Actualizado para soportar 4 servidores UDP.
 */
data class AppState(
    // Tracking status
    val isTrackingEnabled: Boolean = false,
    val isLocationServiceRunning: Boolean = false,

    // Location data
    val currentLocation: LocationData? = null,
    val lastKnownLocation: LocationData? = null,
    val isLoadingLocation: Boolean = false,

    // Permission status
    val hasLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val permissionsRequested: Boolean = false,

    // Server status - actualizado para 4 servidores UDP
    val serverStatus: ServerStatus = ServerStatus(),

    // Messages and errors for UI display
    val statusMessage: String = "",
    val errorMessage: String = "",
    val isShowingError: Boolean = false,

    // Configuration flags
    val isFirstLaunch: Boolean = true,
    val isDebugging: Boolean = false,

    // Statistics
    val sessionStartTime: Long = 0L,
    val totalLocationsSent: Int = 0,
    val sessionDuration: Long = 0L
) {

    /**
     * Checks if the application is ready to start tracking.
     * Requires all necessary permissions to be granted and tracking/service not already active.
     */
    fun canStartTracking(): Boolean {
        return hasLocationPermission &&
                hasBackgroundLocationPermission &&
                !isTrackingEnabled &&
                !isLocationServiceRunning
    }

    /**
     * Checks if both foreground and background location permissions are granted.
     */
    fun hasAllPermissions(): Boolean {
        return hasLocationPermission && hasBackgroundLocationPermission
    }

    /**
     * Checks if a valid current location is available.
     */
    fun hasValidLocation(): Boolean {
        return currentLocation?.isValid() == true
    }

    /**
     * Gets the formatted duration of the current or last tracking session.
     * Formatted as HH:MM:SS.
     */
    fun getSessionDurationFormatted(): String {
        if (sessionStartTime == 0L) return "00:00:00"

        val duration = if (isTrackingEnabled) {
            System.currentTimeMillis() - sessionStartTime
        } else {
            sessionDuration
        }

        val hours = duration / 3600000
        val minutes = (duration % 3600000) / 60000
        val seconds = (duration % 60000) / 1000

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * Updates the AppState to reflect that tracking has started.
     */
    fun startTracking(): AppState {
        return copy(
            isTrackingEnabled = true,
            isLocationServiceRunning = true,
            sessionStartTime = if (sessionStartTime == 0L) System.currentTimeMillis() else sessionStartTime,
            statusMessage = "Location tracking started (4 UDP servers)",
            errorMessage = "",
            isShowingError = false
        )
    }

    /**
     * Updates the AppState to reflect that tracking has stopped.
     */
    fun stopTracking(): AppState {
        val finalDuration = if (sessionStartTime > 0L) {
            System.currentTimeMillis() - sessionStartTime
        } else {
            sessionDuration
        }

        return copy(
            isTrackingEnabled = false,
            isLocationServiceRunning = false,
            sessionDuration = finalDuration,
            statusMessage = "Location tracking stopped",
            errorMessage = "",
            isShowingError = false
        )
    }

    /**
     * Updates the current location and increments the total locations sent count.
     */
    fun updateLocation(location: LocationData): AppState {
        return copy(
            currentLocation = location,
            lastKnownLocation = currentLocation ?: location,
            isLoadingLocation = false,
            totalLocationsSent = totalLocationsSent + 1
        )
    }

    /**
     * Updates the permission status in the AppState.
     */
    fun updatePermissions(
        locationPermission: Boolean,
        backgroundPermission: Boolean
    ): AppState {
        return copy(
            hasLocationPermission = locationPermission,
            hasBackgroundLocationPermission = backgroundPermission,
            permissionsRequested = true
        )
    }

    /**
     * Sets a success message to be displayed to the user.
     */
    fun showSuccessMessage(message: String): AppState {
        return copy(
            statusMessage = message,
            errorMessage = "",
            isShowingError = false
        )
    }

    /**
     * Sets an error message to be displayed to the user.
     */
    fun showErrorMessage(message: String): AppState {
        return copy(
            errorMessage = message,
            statusMessage = "",
            isShowingError = true
        )
    }

    /**
     * Clears all status and error messages from the AppState.
     */
    fun clearMessages(): AppState {
        return copy(
            statusMessage = "",
            errorMessage = "",
            isShowingError = false
        )
    }

    /**
     * Updates the server status in the AppState.
     */
    fun updateServerStatus(newServerStatus: ServerStatus): AppState {
        return copy(serverStatus = newServerStatus)
    }

    /**
     * Provides a summary string of the application's current operational status.
     * Actualizado para mostrar estado de 4 servidores UDP.
     */
    fun getStatusSummary(): String {
        return when {
            !hasAllPermissions() -> "Permissions required"
            isTrackingEnabled -> {
                val activeConnections = serverStatus.getActiveConnectionsCount()
                val connectivityStatus = when {
                    activeConnections == 4 -> "Optimal"
                    activeConnections >= 2 -> "Good"
                    activeConnections == 1 -> "Limited"
                    else -> "No connection"
                }
                "Tracking active - $connectivityStatus ($activeConnections/4 UDP servers)"
            }
            hasValidLocation() -> "Ready to track"
            else -> "Waiting for GPS signal"
        }
    }

    /**
     * Checks if the application is in an active operational state.
     * Considera 4 servidores UDP.
     */
    fun isActive(): Boolean {
        return isTrackingEnabled && hasValidLocation() && serverStatus.hasAnyConnection()
    }

    /**
     * Verifica si hay conectividad óptima (4/4 servidores)
     */
    fun hasOptimalConnectivity(): Boolean {
        return serverStatus.hasAllConnections()
    }

    /**
     * Verifica si hay redundancia mínima (2+ servidores)
     */
    fun hasMinimumRedundancy(): Boolean {
        return serverStatus.hasMinimumRedundancy()
    }

    /**
     * Obtiene el porcentaje de conectividad
     */
    fun getConnectivityPercentage(): Float {
        return serverStatus.getConnectivityPercentage()
    }

    /**
     * Obtiene el estado de redundancia como texto
     */
    fun getRedundancyStatusText(): String {
        val activeConnections = serverStatus.getActiveConnectionsCount()
        return when {
            activeConnections == 4 -> "Optimal redundancy (4/4)"
            activeConnections >= 2 -> "Good redundancy ($activeConnections/4)"
            activeConnections == 1 -> "Risk: Only 1/4 servers"
            else -> "Critical: No servers"
        }
    }

    /**
     * Verifica si el estado requiere atención (menos de 2 servidores)
     */
    fun requiresAttention(): Boolean {
        return isTrackingEnabled && serverStatus.getActiveConnectionsCount() < 2
    }

    /**
     * Obtiene información detallada del estado de servidores
     */
    fun getServerStatusDetails(): Map<String, String> {
        return mapOf(
            "server_1_status" to serverStatus.server1UDP.name,
            "server_2_status" to serverStatus.server2UDP.name,
            "server_3_status" to serverStatus.server3UDP.name,
            "server_4_status" to serverStatus.server4UDP.name,
            "active_connections" to serverStatus.getActiveConnectionsCount().toString(),
            "total_connections" to "4",
            "connectivity_percentage" to "${serverStatus.getConnectivityPercentage()}%",
            "redundancy_status" to getRedundancyStatusText(),
            "last_update" to if (serverStatus.lastUpdateTime > 0) {
                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(serverStatus.lastUpdateTime))
            } else {
                "Never"
            }
        )
    }

    /**
     * Calcula la eficiencia de transmisión
     */
    fun getTransmissionEfficiency(): Float {
        val total = serverStatus.totalSentMessages + serverStatus.totalFailedMessages
        return if (total > 0) {
            (serverStatus.totalSentMessages.toFloat() / total.toFloat()) * 100f
        } else {
            0f
        }
    }

    /**
     * Verifica si el estado es crítico (sin conexiones durante tracking)
     */
    fun isCriticalState(): Boolean {
        return isTrackingEnabled && !serverStatus.hasAnyConnection()
    }

    /**
     * Obtiene el tiempo desde la última actualización de servidor
     */
    fun getTimeSinceLastServerUpdate(): String {
        if (serverStatus.lastUpdateTime == 0L) return "Never"

        val timeDiff = System.currentTimeMillis() - serverStatus.lastUpdateTime
        val seconds = timeDiff / 1000

        return when {
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
            else -> "${seconds / 3600}h ago"
        }
    }

    /**
     * Genera un resumen completo del estado para diagnósticos
     */
    fun getComprehensiveStatusSummary(): String {
        val permissions = if (hasAllPermissions()) "✓" else "✗"
        val gps = if (hasValidLocation()) "✓" else "✗"
        val servers = "${serverStatus.getActiveConnectionsCount()}/4"
        val efficiency = String.format("%.1f", getTransmissionEfficiency())

        return "Permissions: $permissions | GPS: $gps | Servers: $servers | Efficiency: $efficiency%"
    }
}