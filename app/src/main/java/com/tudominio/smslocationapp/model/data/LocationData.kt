package com.tudominio.smslocation.model.data

import android.location.Location
import com.tudominio.smslocation.util.Constants

/**
 * Data class que representa los datos de ubicación GPS
 * Actualizado para usar correctamente el timestamp del GPS
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,        // Timestamp del GPS (location.time)
    val systemTimestamp: Long,  // Timestamp del sistema para comparación
    val accuracy: Float? = null,
    val altitude: Double? = null,
    val speed: Float? = null,
    val provider: String? = null
) {

    companion object {
        /**
         * Crear LocationData desde Android Location
         * Prioriza el timestamp del GPS sobre el del sistema
         */
        fun fromAndroidLocation(location: Location): LocationData {
            val gpsTimestamp = if (location.time > 0) location.time else System.currentTimeMillis()
            val systemTimestamp = System.currentTimeMillis()

            return LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = gpsTimestamp,  // Usar timestamp del GPS
                systemTimestamp = systemTimestamp,
                accuracy = if (location.hasAccuracy()) location.accuracy else null,
                altitude = if (location.hasAltitude()) location.altitude else null,
                speed = if (location.hasSpeed()) location.speed else null,
                provider = location.provider
            )
        }

        /**
         * Crear LocationData vacío para inicialización
         */
        fun empty(): LocationData {
            return LocationData(
                latitude = 0.0,
                longitude = 0.0,
                timestamp = 0L,
                systemTimestamp = 0L
            )
        }

        /**
         * Crear LocationData de prueba
         */
        fun createTestLocation(
            lat: Double = 4.123456,
            lon: Double = -74.123456
        ): LocationData {
            val currentTime = System.currentTimeMillis()
            return LocationData(
                latitude = lat,
                longitude = lon,
                timestamp = currentTime,
                systemTimestamp = currentTime,
                accuracy = 5.0f,
                altitude = 2640.0,
                speed = 0.0f,
                provider = "gps"
            )
        }
    }

    /**
     * Convertir a formato JSON para envío a servidores
     * Formato optimizado: {"lat":4.123456,"lon":-74.123456,"time":1692123456789,"acc":5.0}
     */
    fun toJsonFormat(): String {
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{")
        jsonBuilder.append("\"lat\":$latitude,")
        jsonBuilder.append("\"lon\":$longitude,")
        jsonBuilder.append("\"time\":$timestamp")  // Usar timestamp del GPS

        // Agregar campos opcionales si están disponibles (formato compacto)
        accuracy?.let {
            jsonBuilder.append(",\"acc\":$it")
        }
        altitude?.let {
            jsonBuilder.append(",\"alt\":$it")
        }
        speed?.let {
            jsonBuilder.append(",\"spd\":$it")
        }
        provider?.let {
            jsonBuilder.append(",\"prov\":\"$it\"")
        }

        jsonBuilder.append("}")
        return jsonBuilder.toString()
    }

    /**
     * Convertir a formato JSON extendido (más legible para debugging)
     */
    fun toJsonFormatExtended(): String {
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{\n")
        jsonBuilder.append("  \"latitude\": $latitude,\n")
        jsonBuilder.append("  \"longitude\": $longitude,\n")
        jsonBuilder.append("  \"gps_timestamp\": $timestamp,\n")
        jsonBuilder.append("  \"system_timestamp\": $systemTimestamp")

        accuracy?.let {
            jsonBuilder.append(",\n  \"accuracy_meters\": $it")
        }
        altitude?.let {
            jsonBuilder.append(",\n  \"altitude_meters\": $it")
        }
        speed?.let {
            jsonBuilder.append(",\n  \"speed_mps\": $it")
        }
        provider?.let {
            jsonBuilder.append(",\n  \"provider\": \"$it\"")
        }

        jsonBuilder.append("\n}")
        return jsonBuilder.toString()
    }

    /**
     * Convertir a formato de string para envío a servidores (DEPRECATED - usar toJsonFormat)
     * Mantenido para compatibilidad con versiones anteriores
     */
    @Deprecated("Use toJsonFormat() instead for better server compatibility")
    fun toTransmissionFormat(): String {
        return "${Constants.DataFormat.LAT_PREFIX}${String.format("%.6f", latitude)}${Constants.DataFormat.LOCATION_SEPARATOR}" +
                "${Constants.DataFormat.LON_PREFIX}${String.format("%.6f", longitude)}${Constants.DataFormat.LOCATION_SEPARATOR}" +
                "${Constants.DataFormat.TIME_PREFIX}$timestamp"
    }

    /**
     * Verificar si los datos de ubicación son válidos
     */
    fun isValid(): Boolean {
        return latitude != 0.0 &&
                longitude != 0.0 &&
                timestamp > 0 &&
                latitude >= -90.0 && latitude <= 90.0 &&
                longitude >= -180.0 && longitude <= 180.0 &&
                isTimestampReasonable()
    }

    /**
     * Verificar si el timestamp es razonable (no muy antiguo ni futuro)
     */
    private fun isTimestampReasonable(): Boolean {
        val currentTime = System.currentTimeMillis()
        val oneHourAgo = currentTime - (60 * 60 * 1000) // 1 hora atrás
        val oneHourFuture = currentTime + (60 * 60 * 1000) // 1 hora adelante

        return timestamp in oneHourAgo..oneHourFuture
    }

    /**
     * Obtener coordenadas formateadas para mostrar en UI
     */
    fun getFormattedCoordinates(): String {
        return "LAT: ${String.format("%.6f", latitude)}, LON: ${String.format("%.6f", longitude)}"
    }

    /**
     * Obtener coordenadas formateadas con precisión
     */
    fun getFormattedCoordinatesWithAccuracy(): String {
        val coords = getFormattedCoordinates()
        return if (accuracy != null) {
            "$coords (±${String.format("%.1f", accuracy)}m)"
        } else {
            coords
        }
    }

    /**
     * Calcular distancia a otra ubicación en metros
     */
    fun distanceTo(other: LocationData): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            this.latitude, this.longitude,
            other.latitude, other.longitude,
            results
        )
        return results[0]
    }

    /**
     * Verificar si la ubicación es significativamente diferente de otra
     * (útil para evitar enviar ubicaciones muy similares)
     */
    fun isSignificantlyDifferentFrom(other: LocationData, minDistanceMeters: Float = 5.0f): Boolean {
        return distanceTo(other) > minDistanceMeters
    }

    /**
     * Verificar si esta ubicación es más reciente que otra
     */
    fun isNewerThan(other: LocationData): Boolean {
        return this.timestamp > other.timestamp
    }

    /**
     * Verificar si esta ubicación es más precisa que otra
     */
    fun isMoreAccurateThan(other: LocationData): Boolean {
        return when {
            this.accuracy == null && other.accuracy == null -> false
            this.accuracy == null -> false
            other.accuracy == null -> true
            else -> this.accuracy < other.accuracy
        }
    }

    /**
     * Convertir timestamp GPS a fecha legible
     */
    fun getFormattedGpsTimestamp(): String {
        return java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }

    /**
     * Convertir timestamp del sistema a fecha legible
     */
    fun getFormattedSystemTimestamp(): String {
        return java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date(systemTimestamp))
    }

    /**
     * Obtener diferencia entre timestamp GPS y sistema en millisegundos
     */
    fun getTimestampDifference(): Long {
        return systemTimestamp - timestamp
    }

    /**
     * Obtener información completa de la ubicación para debugging
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Location Debug Info ===")
            appendLine("Coordinates: ${getFormattedCoordinatesWithAccuracy()}")
            appendLine("GPS Time: ${getFormattedGpsTimestamp()} ($timestamp)")
            appendLine("System Time: ${getFormattedSystemTimestamp()} ($systemTimestamp)")
            appendLine("Time Diff: ${getTimestampDifference()}ms")
            appendLine("Provider: ${provider ?: "unknown"}")
            appendLine("Altitude: ${altitude?.let { "${String.format("%.1f", it)}m" } ?: "N/A"}")
            appendLine("Speed: ${speed?.let { "${String.format("%.1f", it)} m/s" } ?: "N/A"}")
            appendLine("Valid: ${isValid()}")
            appendLine("JSON: ${toJsonFormat()}")
        }
    }

    /**
     * Crear una copia con timestamp actualizado (útil para reenvíos)
     */
    fun withUpdatedSystemTimestamp(): LocationData {
        return copy(systemTimestamp = System.currentTimeMillis())
    }
}