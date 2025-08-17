package com.tudominio.smslocation.model.data

import android.location.Location
import com.tudominio.smslocation.util.Constants

/**
 * Data class que representa los datos de ubicación GPS
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val accuracy: Float? = null,
    val altitude: Double? = null,
    val speed: Float? = null
) {

    companion object {
        /**
         * Crear LocationData desde Android Location
         */
        fun fromAndroidLocation(location: Location): LocationData {
            return LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = if (location.time > 0) location.time else System.currentTimeMillis(),
                accuracy = if (location.hasAccuracy()) location.accuracy else null,
                altitude = if (location.hasAltitude()) location.altitude else null,
                speed = if (location.hasSpeed()) location.speed else null
            )
        }

        /**
         * Crear LocationData vacío para inicialización
         */
        fun empty(): LocationData {
            return LocationData(
                latitude = 0.0,
                longitude = 0.0,
                timestamp = 0L
            )
        }
    }

    /**
     * Convertir a formato JSON para envío a servidores
     * Formato: {"latitude":4.123456,"longitude":-74.123456,"timestamp":1692123456789,"accuracy":5.0,"altitude":2640.0,"speed":0.0}
     */
    fun toJsonFormat(): String {
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{")
        jsonBuilder.append("\"latitude\":$latitude,")
        jsonBuilder.append("\"longitude\":$longitude,")
        jsonBuilder.append("\"timestamp\":$timestamp")

        // Agregar campos opcionales si están disponibles
        accuracy?.let {
            jsonBuilder.append(",\"accuracy\":$it")
        }
        altitude?.let {
            jsonBuilder.append(",\"altitude\":$it")
        }
        speed?.let {
            jsonBuilder.append(",\"speed\":$it")
        }

        jsonBuilder.append("}")
        return jsonBuilder.toString()
    }

    /**
     * Convertir a formato de string para envío a servidores (DEPRECATED - usar toJsonFormat)
     * Formato: LAT:4.123456|LON:-74.123456|TIME:1692123456789
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
                longitude >= -180.0 && longitude <= 180.0
    }

    /**
     * Obtener coordenadas formateadas para mostrar en UI
     */
    fun getFormattedCoordinates(): String {
        return "LAT: ${String.format("%.6f", latitude)}, LON: ${String.format("%.6f", longitude)}"
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
     * Convertir timestamp a fecha legible
     */
    fun getFormattedTimestamp(): String {
        return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}