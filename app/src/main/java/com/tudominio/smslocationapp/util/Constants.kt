package com.tudominio.smslocation.util

/**
 * Constantes globales de la aplicación Juls
 */
object Constants {

    // Configuración de servidores
    const val SERVER_IP_1 = "192.168.1.100" // TODO: Cambiar por IP real del servidor 1
    const val SERVER_IP_2 = "192.168.1.101" // TODO: Cambiar por IP real del servidor 2
    const val TCP_PORT = 6000
    const val UDP_PORT = 6001

    // Configuración de ubicación GPS
    const val LOCATION_UPDATE_INTERVAL = 2000L // 2 segundos en millisegundos
    const val LOCATION_FASTEST_INTERVAL = 2000L // 2 segundos en millisegundos
    const val LOCATION_TIMEOUT = 5000L // 5 segundos timeout para obtener ubicación

    // Configuración de red
    const val NETWORK_TIMEOUT = 5000 // 5 segundos timeout para conexiones TCP/UDP
    const val MAX_RETRY_ATTEMPTS = 3 // Máximo número de reintentos de envío
    const val RETRY_DELAY = 1000L // 1 segundo entre reintentos

    // Configuración de notificaciones
    const val NOTIFICATION_CHANNEL_ID = "JulsLocationChannel"
    const val NOTIFICATION_CHANNEL_NAME = "Juls Location Service"
    const val NOTIFICATION_ID = 1001

    // Configuración de permisos
    val REQUIRED_PERMISSIONS = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val BACKGROUND_PERMISSIONS = arrayOf(
        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )

    // Mensajes de la aplicación
    object Messages {
        const val TRACKING_STARTED = "Location tracking started"
        const val TRACKING_STOPPED = "Location tracking stopped"
        const val LOCATION_PERMISSION_REQUIRED = "Location permissions required"
        const val BACKGROUND_PERMISSION_REQUIRED = "Background location permission required"
        const val GPS_NOT_AVAILABLE = "GPS not available"
        const val NETWORK_ERROR = "Network connection error"
        const val SERVER_CONNECTION_ERROR = "Server connection error"
        const val LOCATION_SENT_SUCCESS = "Location sent successfully"
    }

    // Códigos de acción para el servicio
    object ServiceActions {
        const val START_TRACKING = "START_TRACKING"
        const val STOP_TRACKING = "STOP_TRACKING"
        const val UPDATE_LOCATION = "UPDATE_LOCATION"
    }

    // Formato de datos GPS - JSON
    object DataFormat {
        const val CONTENT_TYPE = "application/json"
        const val CHARSET = "UTF-8"

        // Formato anterior (mantenido para compatibilidad)
        const val LOCATION_SEPARATOR = "|"
        const val LAT_PREFIX = "LAT:"
        const val LON_PREFIX = "LON:"
        const val TIME_PREFIX = "TIME:"

        // Configuración de formato JSON
        object JsonConfig {
            const val USE_HTTP_HEADERS = true  // Cambiar a false si los servidores no manejan HTTP
            const val PRETTY_PRINT = false    // true para JSON con formato, false para compacto
            const val INCLUDE_METADATA = true // Incluir accuracy, altitude, speed si están disponibles
        }
    }

    // Configuración de logs (para debug)
    object Logs {
        const val TAG_MAIN = "Juls_Main"
        const val TAG_LOCATION = "Juls_Location"
        const val TAG_NETWORK = "Juls_Network"
        const val TAG_SERVICE = "Juls_Service"
        const val TAG_CONTROLLER = "Juls_Controller"
    }
}