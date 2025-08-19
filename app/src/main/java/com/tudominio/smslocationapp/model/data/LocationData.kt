package com.tudominio.smslocation.model.data

import android.location.Location
import com.tudominio.smslocation.util.Constants

/**
 * Data class representing GPS location data.
 * Updated to correctly use the GPS timestamp.
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,        // GPS timestamp (location.time) - when the GPS fix was acquired
    val systemTimestamp: Long,  // System timestamp for comparison - when the data was processed by the device
    val accuracy: Float? = null, // Estimated horizontal accuracy of this location, in meters.
    val altitude: Double? = null, // Altitude in meters above the WGS84 ellipsoid.
    val speed: Float? = null,    // Speed if it is available, in meters/second over ground.
    val provider: String? = null // The name of the provider that generated this fix (e.g., "gps", "network").
) {

    companion object {
        /**
         * Creates a [LocationData] object from an Android [Location] object.
         * Prioritizes the GPS timestamp over the system timestamp for the `timestamp` field.
         */
        fun fromAndroidLocation(location: Location): LocationData {
            // Use the GPS time if available and valid, otherwise fallback to current system time.
            val gpsTimestamp = if (location.time > 0) location.time else System.currentTimeMillis()
            val systemTimestamp = System.currentTimeMillis() // Capture current system time for context.

            return LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = gpsTimestamp,  // Use the GPS timestamp for the primary timestamp.
                systemTimestamp = systemTimestamp,
                accuracy = if (location.hasAccuracy()) location.accuracy else null,
                altitude = if (location.hasAltitude()) location.altitude else null,
                speed = if (location.hasSpeed()) location.speed else null,
                provider = location.provider
            )
        }

        /**
         * Creates an empty [LocationData] object, typically used for initialization
         * or when no valid location is available yet.
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
         * Creates a test [LocationData] object with predefined values.
         * Useful for debugging and testing purposes without requiring actual GPS hardware.
         * @param lat Optional latitude for the test location. Defaults to a sample value.
         * @param lon Optional longitude for the test location. Defaults to a sample value.
         */
        fun createTestLocation(
            lat: Double = 4.123456, // Default test latitude
            lon: Double = -74.123456 // Default test longitude
        ): LocationData {
            val currentTime = System.currentTimeMillis()
            return LocationData(
                latitude = lat,
                longitude = lon,
                timestamp = currentTime,
                systemTimestamp = currentTime,
                accuracy = 5.0f, // Sample accuracy in meters
                altitude = 2640.0, // Sample altitude in meters
                speed = 0.0f, // Sample speed in m/s
                provider = "gps" // Sample provider name
            )
        }
    }

    /**
     * Converts the [LocationData] object to a compact JSON string format for server transmission.
     * Format optimized for minimal size: {"lat":4.123456,"lon":-74.123456,"time":1692123456789,"acc":5.0}
     */
    fun toJsonFormat(): String {
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{")
        jsonBuilder.append("\"lat\":$latitude,")
        jsonBuilder.append("\"lon\":$longitude,")
        jsonBuilder.append("\"time\":$timestamp")  // Use GPS timestamp as the primary time key.

        // Append optional fields only if they are not null, for a more compact JSON.
        accuracy?.let {
            jsonBuilder.append(",\"acc\":$it") // "acc" for accuracy
        }
        altitude?.let {
            jsonBuilder.append(",\"alt\":$it") // "alt" for altitude
        }
        speed?.let {
            jsonBuilder.append(",\"spd\":$it") // "spd" for speed
        }
        provider?.let {
            jsonBuilder.append(",\"prov\":\"$it\"") // "prov" for provider
        }

        jsonBuilder.append("}")
        return jsonBuilder.toString()
    }

    /**
     * Converts the [LocationData] object to an extended, more human-readable JSON string format.
     * This format includes all fields and is better suited for debugging or logging.
     */
    fun toJsonFormatExtended(): String {
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{\n")
        jsonBuilder.append("  \"latitude\": $latitude,\n")
        jsonBuilder.append("  \"longitude\": $longitude,\n")
        jsonBuilder.append("  \"gps_timestamp\": $timestamp,\n") // Explicitly named GPS timestamp.
        jsonBuilder.append("  \"system_timestamp\": $systemTimestamp") // System timestamp for comparison.

        // Append optional fields with full names for clarity.
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
     * Converts to a string format for server transmission. This method is now deprecated.
     * It's kept for backward compatibility with older server implementations.
     * @deprecated Use [toJsonFormat] instead for better server compatibility and a more standard format.
     */
    @Deprecated("Use toJsonFormat() instead for better server compatibility")
    fun toTransmissionFormat(): String {
        return "${Constants.DataFormat.LAT_PREFIX}${String.format("%.6f", latitude)}${Constants.DataFormat.LOCATION_SEPARATOR}" +
                "${Constants.DataFormat.LON_PREFIX}${String.format("%.6f", longitude)}${Constants.DataFormat.LOCATION_SEPARATOR}" +
                "${Constants.DataFormat.TIME_PREFIX}$timestamp"
    }

    /**
     * Checks if the location data is considered valid.
     * A valid location must have non-zero latitude/longitude, a positive timestamp,
     * coordinates within valid ranges, and a reasonable timestamp (not too old or in the future).
     */
    fun isValid(): Boolean {
        return latitude != 0.0 &&
                longitude != 0.0 &&
                timestamp > 0 && // Ensure timestamp is positive.
                latitude >= -90.0 && latitude <= 90.0 && // Latitude must be within valid range.
                longitude >= -180.0 && longitude <= 180.0 && // Longitude must be within valid range.
                isTimestampReasonable() // Check if timestamp is plausible.
    }

    /**
     * Checks if the GPS timestamp is reasonable (i.e., not too far in the past or future).
     * This helps filter out potentially erroneous GPS fixes.
     */
    private fun isTimestampReasonable(): Boolean {
        val currentTime = System.currentTimeMillis()
        val oneHourAgo = currentTime - (60 * 60 * 1000) // 1 hour ago in milliseconds.
        val oneHourFuture = currentTime + (60 * 60 * 1000) // 1 hour into the future in milliseconds.

        // The timestamp should fall within a reasonable window around the current system time.
        return timestamp in oneHourAgo..oneHourFuture
    }

    /**
     * Gets formatted coordinates string for display in the UI.
     * Format: "LAT: X.XXXXXX, LON: Y.YYYYYY"
     */
    fun getFormattedCoordinates(): String {
        return "LAT: ${String.format("%.6f", latitude)}, LON: ${String.format("%.6f", longitude)}"
    }

    /**
     * Gets formatted coordinates string including accuracy, for UI display.
     * Format: "LAT: X.XXXXXX, LON: Y.YYYYYY (±Z.Z m)" if accuracy is available.
     */
    fun getFormattedCoordinatesWithAccuracy(): String {
        val coords = getFormattedCoordinates()
        return if (accuracy != null) {
            "$coords (±${String.format("%.1f", accuracy)}m)" // Append accuracy if available.
        } else {
            coords // Return just coordinates if accuracy is null.
        }
    }

    /**
     * Calculates the distance to another [LocationData] object in meters.
     * Uses Android's built-in `Location.distanceBetween` for accurate geodetic distance.
     * @param other The other [LocationData] object to calculate the distance to.
     * @return The distance in meters.
     */
    fun distanceTo(other: LocationData): Float {
        val results = FloatArray(1) // Array to hold the distance result.
        Location.distanceBetween(
            this.latitude, this.longitude,
            other.latitude, other.longitude,
            results
        )
        return results[0] // The distance in meters.
    }

    /**
     * Checks if this location is significantly different from another location.
     * Useful for filtering out very small movements to avoid unnecessary data transmission.
     * @param other The other [LocationData] object to compare against.
     * @param minDistanceMeters The minimum distance (in meters) to be considered significantly different. Defaults to 5.0m.
     * @return `true` if the distance between the two locations is greater than `minDistanceMeters`, `false` otherwise.
     */
    fun isSignificantlyDifferentFrom(other: LocationData, minDistanceMeters: Float = 5.0f): Boolean {
        return distanceTo(other) > minDistanceMeters
    }

    /**
     * Checks if this location is newer than another [LocationData] based on their timestamps.
     * @param other The other [LocationData] object to compare against.
     * @return `true` if this location's timestamp is greater than the other's, `false` otherwise.
     */
    fun isNewerThan(other: LocationData): Boolean {
        return this.timestamp > other.timestamp
    }

    /**
     * Checks if this location is more accurate than another [LocationData] (i.e., has a smaller accuracy value).
     * Handles cases where accuracy might be null.
     * @param other The other [LocationData] object to compare against.
     * @return `true` if this location is more accurate, `false` otherwise.
     */
    fun isMoreAccurateThan(other: LocationData): Boolean {
        return when {
            this.accuracy == null && other.accuracy == null -> false // Both have no accuracy, not more accurate.
            this.accuracy == null -> false // This has no accuracy, cannot be more accurate.
            other.accuracy == null -> true // Other has no accuracy, this is more accurate if it has one.
            else -> this.accuracy < other.accuracy // Compare accuracies (smaller value means better accuracy).
        }
    }

    /**
     * Gets the GPS timestamp formatted into a human-readable time string (HH:mm:ss.SSS).
     */
    fun getFormattedGpsTimestamp(): String {
        return java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }

    /**
     * Gets the system timestamp formatted into a human-readable time string (HH:mm:ss.SSS).
     */
    fun getFormattedSystemTimestamp(): String {
        return java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date(systemTimestamp))
    }

    /**
     * Calculates the difference between the system timestamp and the GPS timestamp in milliseconds.
     * A positive value means the system processed the location after the GPS fix was acquired.
     */
    fun getTimestampDifference(): Long {
        return systemTimestamp - timestamp
    }

    /**
     * Provides a comprehensive string containing detailed debug information about the location.
     * This is highly useful for logging and troubleshooting.
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Location Debug Info ===")
            appendLine("Coordinates: ${getFormattedCoordinatesWithAccuracy()}")
            appendLine("GPS Time: ${getFormattedGpsTimestamp()} ($timestamp)")
            appendLine("System Time: ${getFormattedSystemTimestamp()} ($systemTimestamp)")
            appendLine("Time Diff: ${getTimestampDifference()}ms")
            appendLine("Provider: ${provider ?: "unknown"}") // Display "unknown" if provider is null.
            appendLine("Altitude: ${altitude?.let { "${String.format("%.1f", it)}m" } ?: "N/A"}") // Format altitude if present.
            appendLine("Speed: ${speed?.let { "${String.format("%.1f", it)} m/s" } ?: "N/A"}") // Format speed if present.
            appendLine("Valid: ${isValid()}") // Indicates if the location data is considered valid.
            appendLine("JSON: ${toJsonFormat()}") // Show the compact JSON representation.
        }
    }

    /**
     * Creates a copy of the [LocationData] object with an updated system timestamp.
     * Useful when re-sending a location and you want to record the new attempt's time.
     */
    fun withUpdatedSystemTimestamp(): LocationData {
        return copy(systemTimestamp = System.currentTimeMillis())
    }
}