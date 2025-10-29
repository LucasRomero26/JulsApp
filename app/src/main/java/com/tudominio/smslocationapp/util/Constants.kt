package com.tudominio.smslocation.util

/**
 * Global constants for the Juls application.
 * Optimizado para UDP con 4 servidores para máxima redundancia.
 */
object Constants {

    // Server Configuration - 4 SERVIDORES UDP
    const val SERVER_IP_1 = "julstracker.app" // IP Lucas
    const val SERVER_IP_2 = "julstracker.online"  // IP Nikolas
    const val SERVER_IP_3 = "alvaromugno.online"  // IP Alvaro
    const val SERVER_IP_4 = "electronicdesign.app"  //  IP Bermejo
    const val UDP_PORT = 6001 // Puerto UDP para comunicación rápida

    // GPS Location Configuration - MÁS RÁPIDO
    const val LOCATION_UPDATE_INTERVAL = 5000L // 2 segundos (más rápido)
    const val LOCATION_FASTEST_INTERVAL = 5000L // 2 segundos
    const val LOCATION_TIMEOUT = 5000L // 3 segundos (reducido)

    // Network Configuration - OPTIMIZADO PARA UDP RÁPIDO CON 4 SERVIDORES
    const val NETWORK_TIMEOUT = 5000 // 1.5 segundos (más rápido)
    const val MAX_RETRY_ATTEMPTS = 5 // Menos reintentos para velocidad
    const val RETRY_DELAY = 1000L // 0.5 segundos entre reintentos

    // ✨ NUEVO: WebRTC Video Configuration
    const val WEBRTC_ENABLED = true
    const val VIDEO_WIDTH = 640
    const val VIDEO_HEIGHT = 480
    const val VIDEO_FPS = 15

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

    // ✨ NUEVO: Permisos para cámara y video
    val CAMERA_PERMISSIONS = arrayOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO
    )

    // Application Messages
    object Messages {
        const val TRACKING_STARTED = "Location tracking started (UDP - 4 servers)"
        const val TRACKING_STOPPED = "Location tracking stopped"
        const val LOCATION_PERMISSION_REQUIRED = "Location permissions required"
        const val BACKGROUND_PERMISSION_REQUIRED = "Background location permission required"
        const val GPS_NOT_AVAILABLE = "GPS not available or signal lost"
        const val NETWORK_ERROR = "Network connection error"
        const val SERVER_CONNECTION_ERROR = "UDP server connection error"
        const val LOCATION_SENT_SUCCESS = "Location sent via UDP successfully"

        // ✨ NUEVO: Mensajes de video
        const val VIDEO_STREAMING_STARTED = "Video streaming started"
        const val VIDEO_STREAMING_STOPPED = "Video streaming stopped"
        const val CAMERA_PERMISSION_REQUIRED = "Camera permissions required"
        const val VIDEO_CONNECTION_ERROR = "Video connection error"
    }

    // Service Action Codes
    object ServiceActions {
        const val START_TRACKING = "START_TRACKING"
        const val STOP_TRACKING = "STOP_TRACKING"
        const val UPDATE_LOCATION = "UPDATE_LOCATION"
        // ✨ NUEVO: Acciones de video
        const val START_VIDEO = "START_VIDEO"
        const val STOP_VIDEO = "STOP_VIDEO"
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
        const val TAG_NETWORK = "Juls_Network_UDP_4" // Especificar UDP con 4 servidores
        const val TAG_SERVICE = "Juls_Service"
        const val TAG_CONTROLLER = "Juls_Controller"
        const val TAG_WEBRTC = "Juls_WebRTC" // ✨ NUEVO
    }
}