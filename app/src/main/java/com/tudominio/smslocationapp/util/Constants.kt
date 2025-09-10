package com.tudominio.smslocation.util

/**
 * Global constants for the Juls application.
 * Optimizado para solo UDP con velocidad mejorada.
 */
object Constants {

    // Server Configuration - SOLO UDP
    const val SERVER_IP_1 = "3.136.116.100" // IP pública del servidor principal
    const val SERVER_IP_2 = "55.15.55.222" // IP pública del servidor secundario
    const val UDP_PORT = 6001 // Puerto UDP para comunicación rápida (TCP eliminado)

    // GPS Location Configuration - MÁS RÁPIDO
    const val LOCATION_UPDATE_INTERVAL = 2000L // 2 segundos (más rápido)
    const val LOCATION_FASTEST_INTERVAL = 2000L // 2 segundos
    const val LOCATION_TIMEOUT = 3000L // 3 segundos (reducido)

    // Network Configuration - OPTIMIZADO PARA UDP RÁPIDO
    const val NETWORK_TIMEOUT = 1500 // 1.5 segundos (más rápido)
    const val MAX_RETRY_ATTEMPTS = 2 // Menos reintentos para velocidad
    const val RETRY_DELAY = 500L // 0.5 segundos entre reintentos

    // Notification Configuration
    const val NOTIFICATION_CHANNEL_ID = "JulsLocationChannel"
    const val NOTIFICATION_CHANNEL_NAME = "Juls Location Service"
    const val NOTIFICATION_ID = 1001

    // Permission Configuration
    val REQUIRED_PERMISSIONS = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val BACKGROUND_PERMISSIONS = arrayOf(
        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )

    // Application Messages
    object Messages {
        const val TRACKING_STARTED = "Location tracking started (UDP only)"
        const val TRACKING_STOPPED = "Location tracking stopped"
        const val LOCATION_PERMISSION_REQUIRED = "Location permissions required"
        const val BACKGROUND_PERMISSION_REQUIRED = "Background location permission required"
        const val GPS_NOT_AVAILABLE = "GPS not available or signal lost"
        const val NETWORK_ERROR = "Network connection error"
        const val SERVER_CONNECTION_ERROR = "UDP server connection error"
        const val LOCATION_SENT_SUCCESS = "Location sent via UDP successfully"
    }

    // Service Action Codes
    object ServiceActions {
        const val START_TRACKING = "START_TRACKING"
        const val STOP_TRACKING = "STOP_TRACKING"
        const val UPDATE_LOCATION = "UPDATE_LOCATION"
    }

    // UDP Data Format Configuration
    object DataFormat {
        const val CONTENT_TYPE = "application/json"
        const val CHARSET = "UTF-8"

        // Legacy format constants (mantenidas para compatibilidad)
        const val LOCATION_SEPARATOR = "|"
        const val LAT_PREFIX = "LAT:"
        const val LON_PREFIX = "LON:"
        const val TIME_PREFIX = "TIME:"

        // JSON formatting específico para UDP
        object JsonConfig {
            const val USE_HTTP_HEADERS = false // UDP no usa headers HTTP
            const val PRETTY_PRINT = false // Compacto para UDP
            const val INCLUDE_METADATA = true
        }
    }

    // Log Tags
    object Logs {
        const val TAG_MAIN = "Juls_Main"
        const val TAG_LOCATION = "Juls_Location"
        const val TAG_NETWORK = "Juls_Network_UDP" // Especificar UDP
        const val TAG_SERVICE = "Juls_Service"
        const val TAG_CONTROLLER = "Juls_Controller"
    }
}