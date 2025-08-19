package com.tudominio.smslocation.model.data

/**
 * Data class representing the global application state.
 * This immutable data class holds all relevant UI and operational states of the application,
 * making it easy to manage and observe changes.
 */
data class AppState(
    // Tracking status
    val isTrackingEnabled: Boolean = false, // Indicates if location tracking is currently enabled by the user.
    val isLocationServiceRunning: Boolean = false, // Indicates if the background location service is actively running.

    // Location data
    val currentLocation: LocationData? = null, // The most recently obtained location data.
    val lastKnownLocation: LocationData? = null, // The last successfully obtained location, used for display even if current is null.
    val isLoadingLocation: Boolean = false, // True when the app is actively trying to get a location.

    // Permission status
    val hasLocationPermission: Boolean = false, // True if foreground location permission is granted.
    val hasBackgroundLocationPermission: Boolean = false, // True if background location permission is granted (for Android 10+).
    val permissionsRequested: Boolean = false, // True if permissions have been requested at least once.

    // Server status
    val serverStatus: ServerStatus = ServerStatus(), // Holds detailed status of connections to various servers.

    // Messages and errors for UI display
    val statusMessage: String = "", // A general status message to display to the user (e.g., "Tracking started").
    val errorMessage: String = "", // An error message to display to the user.
    val isShowingError: Boolean = false, // True if an error message is currently active and should be shown.

    // Configuration flags
    val isFirstLaunch: Boolean = true, // True if this is the very first time the app is launched.
    val isDebugging: Boolean = false, // True if the app is in debug mode, potentially showing extra info.

    // Statistics
    val sessionStartTime: Long = 0L, // Timestamp (in milliseconds) when the current tracking session started.
    val totalLocationsSent: Int = 0, // Count of successful location data transmissions.
    val sessionDuration: Long = 0L // Stores the total duration of the last completed session.
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
        if (sessionStartTime == 0L) return "00:00:00" // If session hasn't started, duration is zero.

        // Calculate duration based on whether tracking is active or stopped.
        val duration = if (isTrackingEnabled) {
            System.currentTimeMillis() - sessionStartTime // Current session duration.
        } else {
            sessionDuration // Duration of the last completed session.
        }

        // Convert milliseconds to hours, minutes, and seconds.
        val hours = duration / 3600000
        val minutes = (duration % 3600000) / 60000
        val seconds = (duration % 60000) / 1000

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * Updates the AppState to reflect that tracking has started.
     * Resets error messages and sets the session start time if not already set.
     */
    fun startTracking(): AppState {
        return copy(
            isTrackingEnabled = true,
            isLocationServiceRunning = true,
            // Set sessionStartTime only if it's currently 0L (first start of a new session).
            sessionStartTime = if (sessionStartTime == 0L) System.currentTimeMillis() else sessionStartTime,
            statusMessage = "Location tracking started",
            errorMessage = "",
            isShowingError = false
        )
    }

    /**
     * Updates the AppState to reflect that tracking has stopped.
     * Calculates and stores the final session duration.
     */
    fun stopTracking(): AppState {
        val finalDuration = if (sessionStartTime > 0L) {
            System.currentTimeMillis() - sessionStartTime // Calculate final duration.
        } else {
            sessionDuration // Use previous duration if session wasn't actively running.
        }

        return copy(
            isTrackingEnabled = false,
            isLocationServiceRunning = false,
            sessionDuration = finalDuration, // Store the calculated duration.
            statusMessage = "Location tracking stopped",
            errorMessage = "",
            isShowingError = false
        )
    }

    /**
     * Updates the current location and increments the total locations sent count.
     * Also updates `lastKnownLocation` to preserve the previous `currentLocation`.
     * @param location The new [LocationData] received.
     */
    fun updateLocation(location: LocationData): AppState {
        return copy(
            currentLocation = location,
            lastKnownLocation = currentLocation ?: location, // Keep previous current if new is first.
            isLoadingLocation = false, // Location has been received, so no longer loading.
            totalLocationsSent = totalLocationsSent + 1 // Increment counter for sent locations.
        )
    }

    /**
     * Updates the permission status in the AppState.
     * @param locationPermission Boolean indicating foreground location permission status.
     * @param backgroundPermission Boolean indicating background location permission status.
     */
    fun updatePermissions(
        locationPermission: Boolean,
        backgroundPermission: Boolean
    ): AppState {
        return copy(
            hasLocationPermission = locationPermission,
            hasBackgroundLocationPermission = backgroundPermission,
            permissionsRequested = true // Mark that permissions have been checked/requested.
        )
    }

    /**
     * Sets a success message to be displayed to the user.
     * Clears any active error messages.
     * @param message The success message string.
     */
    fun showSuccessMessage(message: String): AppState {
        return copy(
            statusMessage = message,
            errorMessage = "", // Clear any existing error message.
            isShowingError = false // Indicate no error is currently active.
        )
    }

    /**
     * Sets an error message to be displayed to the user.
     * Clears any active status messages and sets the error flag.
     * @param message The error message string.
     */
    fun showErrorMessage(message: String): AppState {
        return copy(
            errorMessage = message,
            statusMessage = "", // Clear any existing status message.
            isShowingError = true // Indicate an error is currently active.
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
     * @param newServerStatus The latest [ServerStatus] object.
     */
    fun updateServerStatus(newServerStatus: ServerStatus): AppState {
        return copy(serverStatus = newServerStatus)
    }

    /**
     * Provides a summary string of the application's current operational status.
     * This is useful for displaying a concise status to the user.
     */
    fun getStatusSummary(): String {
        return when {
            !hasAllPermissions() -> "Permissions required"
            isTrackingEnabled -> "Tracking active - ${serverStatus.getActiveConnectionsCount()}/4 servers connected"
            hasValidLocation() -> "Ready to track"
            else -> "Waiting for GPS signal" // Default status if none of the above conditions met.
        }
    }

    /**
     * Checks if the application is in an active operational state,
     * meaning tracking is enabled, a valid location is available, and there's server connectivity.
     */
    fun isActive(): Boolean {
        return isTrackingEnabled && hasValidLocation() && serverStatus.hasAnyConnection()
    }
}