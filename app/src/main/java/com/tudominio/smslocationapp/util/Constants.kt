package com.tudominio.smslocation.util

/**
 * Global constants for the Juls application.
 * This object centralizes various configuration parameters, messages, and identifiers
 * used throughout the application, making them easily modifiable and accessible.
 */
object Constants {

    // Server Configuration - ACTUALIZADO CON IP PÚBLICA
    // Ambos servidores usan la misma IP pública pero diferentes puertos
    const val SERVER_IP_1 = "186.119.50.121" // IP pública del servidor principal
    const val SERVER_IP_2 = "186.119.50.121" // IP pública del servidor secundario (misma IP)
    const val TCP_PORT = 6000 // Puerto TCP para comunicación confiable
    const val UDP_PORT = 6001 // Puerto UDP para comunicación rápida

    // GPS Location Configuration
    // Interval at which location updates are requested (in milliseconds).
    const val LOCATION_UPDATE_INTERVAL = 5000L // 5 seconds.
    // Fastest interval at which location updates can be received (in milliseconds).
    const val LOCATION_FASTEST_INTERVAL = 5000L // 5 seconds.
    // Timeout for obtaining a single location fix (e.g., for getCurrentLocation) in milliseconds.
    const val LOCATION_TIMEOUT = 5000L // 5 seconds.

    // Network Configuration
    // Timeout for TCP/UDP socket connections and read/write operations in milliseconds.
    const val NETWORK_TIMEOUT = 5000 // 5 seconds.
    // Maximum number of retry attempts for network operations (e.g., sending data).
    const val MAX_RETRY_ATTEMPTS = 3
    // Delay between retry attempts for network operations in milliseconds.
    const val RETRY_DELAY = 1000L // 1 second.

    // Notification Configuration
    // ID for the Android notification channel.
    const val NOTIFICATION_CHANNEL_ID = "JulsLocationChannel"
    // User-visible name for the notification channel.
    const val NOTIFICATION_CHANNEL_NAME = "Juls Location Service"
    // Unique ID for the foreground service notification.
    const val NOTIFICATION_ID = 1001

    // Permission Configuration
    // Array of required foreground location permissions.
    val REQUIRED_PERMISSIONS = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION, // For precise location.
        android.Manifest.permission.ACCESS_COARSE_LOCATION // For approximate location.
    )

    // Array of required background location permissions (for Android Q and above).
    val BACKGROUND_PERMISSIONS = arrayOf(
        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION // For location access in background.
    )

    // Application Messages
    // Centralized strings for messages displayed to the user or used in logs.
    object Messages {
        const val TRACKING_STARTED = "Location tracking started"
        const val TRACKING_STOPPED = "Location tracking stopped"
        const val LOCATION_PERMISSION_REQUIRED = "Location permissions required"
        const val BACKGROUND_PERMISSION_REQUIRED = "Background location permission required"
        const val GPS_NOT_AVAILABLE = "GPS not available or signal lost"
        const val NETWORK_ERROR = "Network connection error"
        const val SERVER_CONNECTION_ERROR = "Server connection error"
        const val LOCATION_SENT_SUCCESS = "Location sent successfully"
    }

    // Service Action Codes
    // Defines actions that can be sent to the LocationService via Intent.
    object ServiceActions {
        const val START_TRACKING = "START_TRACKING" // Action to start location tracking.
        const val STOP_TRACKING = "STOP_TRACKING"   // Action to stop location tracking.
        const val UPDATE_LOCATION = "UPDATE_LOCATION" // Action to trigger a manual location update (if needed).
    }

    // GPS Data Format Configuration (JSON and Legacy)
    object DataFormat {
        // Content type for JSON payloads.
        const val CONTENT_TYPE = "application/json"
        // Character set for encoding data.
        const val CHARSET = "UTF-8"

        // Legacy format constants (maintained for backward compatibility if older servers expect this).
        @Deprecated("Use JSON format for new implementations for better compatibility and flexibility.")
        const val LOCATION_SEPARATOR = "|"
        @Deprecated("Use JSON format for new implementations for better compatibility and flexibility.")
        const val LAT_PREFIX = "LAT:"
        @Deprecated("Use JSON format for new implementations for better compatibility and flexibility.")
        const val LON_PREFIX = "LON:"
        @Deprecated("Use JSON format for new implementations for better compatibility and flexibility.")
        const val TIME_PREFIX = "TIME:"

        // JSON formatting specific configurations.
        object JsonConfig {
            // Set to true if servers expect HTTP headers with JSON data (e.g., a web server endpoint).
            // Set to false if sending raw JSON over a direct TCP/UDP socket.
            const val USE_HTTP_HEADERS = true
            // If true, JSON output will be human-readable with indentation; if false, it will be compact.
            const val PRETTY_PRINT = false
            // If true, additional location metadata (accuracy, altitude, speed) will be included in JSON.
            const val INCLUDE_METADATA = true
        }
    }

    // Log Tags (for debugging and filtering logs)
    object Logs {
        const val TAG_MAIN = "Juls_Main"         // Tag for main application components.
        const val TAG_LOCATION = "Juls_Location" // Tag for location-related operations.
        const val TAG_NETWORK = "Juls_Network"   // Tag for network-related operations.
        const val TAG_SERVICE = "Juls_Service"   // Tag for background service operations.
        const val TAG_CONTROLLER = "Juls_Controller" // Tag for controller-level logic.
    }
}