package com.tudominio.smslocation.model.data

/**
 * Data class que representa el estado global de la aplicación
 */
data class AppState(
    // Estado de tracking
    val isTrackingEnabled: Boolean = false,
    val isLocationServiceRunning: Boolean = false,

    // Datos de ubicación
    val currentLocation: LocationData? = null,
    val lastKnownLocation: LocationData? = null,
    val isLoadingLocation: Boolean = false,

    // Estado de permisos
    val hasLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val permissionsRequested: Boolean = false,

    // Estado de servidores
    val serverStatus: ServerStatus = ServerStatus(),

    // Mensajes y errores
    val statusMessage: String = "",
    val errorMessage: String = "",
    val isShowingError: Boolean = false,

    // Configuración
    val isFirstLaunch: Boolean = true,
    val isDebugging: Boolean = false,

    // Estadísticas
    val sessionStartTime: Long = 0L,
    val totalLocationsSent: Int = 0,
    val sessionDuration: Long = 0L
) {

    /**
     * Verificar si la aplicación está lista para iniciar tracking
     */
    fun canStartTracking(): Boolean {
        return hasLocationPermission &&
                hasBackgroundLocationPermission &&
                !isTrackingEnabled &&
                !isLocationServiceRunning
    }

    /**
     * Verificar si todos los permisos están concedidos
     */
    fun hasAllPermissions(): Boolean {
        return hasLocationPermission && hasBackgroundLocationPermission
    }

    /**
     * Verificar si hay ubicación válida disponible
     */
    fun hasValidLocation(): Boolean {
        return currentLocation?.isValid() == true
    }

    /**
     * Obtener tiempo transcurrido desde el inicio de sesión
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
     * Actualizar estado de tracking iniciado
     */
    fun startTracking(): AppState {
        return copy(
            isTrackingEnabled = true,
            isLocationServiceRunning = true,
            sessionStartTime = if (sessionStartTime == 0L) System.currentTimeMillis() else sessionStartTime,
            statusMessage = "Location tracking started",
            errorMessage = "",
            isShowingError = false
        )
    }

    /**
     * Actualizar estado de tracking detenido
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
     * Actualizar ubicación actual
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
     * Actualizar estado de permisos
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
     * Mostrar mensaje de éxito
     */
    fun showSuccessMessage(message: String): AppState {
        return copy(
            statusMessage = message,
            errorMessage = "",
            isShowingError = false
        )
    }

    /**
     * Mostrar mensaje de error
     */
    fun showErrorMessage(message: String): AppState {
        return copy(
            errorMessage = message,
            statusMessage = "",
            isShowingError = true
        )
    }

    /**
     * Limpiar mensajes
     */
    fun clearMessages(): AppState {
        return copy(
            statusMessage = "",
            errorMessage = "",
            isShowingError = false
        )
    }

    /**
     * Actualizar estado del servidor
     */
    fun updateServerStatus(newServerStatus: ServerStatus): AppState {
        return copy(serverStatus = newServerStatus)
    }

    /**
     * Obtener resumen del estado actual
     */
    fun getStatusSummary(): String {
        return when {
            !hasAllPermissions() -> "Permissions required"
            isTrackingEnabled -> "Tracking active - ${serverStatus.getActiveConnectionsCount()}/4 servers connected"
            hasValidLocation() -> "Ready to track"
            else -> "Waiting for GPS signal"
        }
    }

    /**
     * Verificar si la app está en estado activo
     */
    fun isActive(): Boolean {
        return isTrackingEnabled && hasValidLocation() && serverStatus.hasAnyConnection()
    }
}