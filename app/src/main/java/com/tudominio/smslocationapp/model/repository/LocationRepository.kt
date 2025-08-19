package com.tudominio.smslocation.model.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import com.tudominio.smslocation.model.data.LocationData
import com.tudominio.smslocation.util.Constants
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Repository for handling GPS location data.
 * This class interacts with Android's Location Services (FusedLocationProviderClient)
 * to provide location updates, manage permissions, and retrieve last known/current locations.
 */
class LocationRepository(private val context: Context) {

    companion object {
        // Tag for logging messages from LocationRepository.
        private const val TAG = Constants.Logs.TAG_LOCATION
    }

    // Lazy initialization of FusedLocationProviderClient, ensuring it's created only when first accessed.
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // A Kotlin Channel to emit real-time location updates.
    // Channel.UNLIMITED allows for a buffer that grows indefinitely, preventing backpressure issues.
    private val _locationUpdates = Channel<LocationData>(Channel.UNLIMITED)
    // Exposed as a Flow, which is a cold stream that can be collected by multiple consumers.
    val locationUpdates: Flow<LocationData> = _locationUpdates.receiveAsFlow()

    private var locationCallback: LocationCallback? = null // Reference to the active LocationCallback.
    private var isRequestingUpdates = false // Flag to track if location updates are currently active.

    /**
     * Checks if the necessary foreground location permissions (ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION)
     * are granted to the application.
     * @return `true` if permissions are granted, `false` otherwise.
     */
    fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION // High-accuracy location permission.
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION // Coarse location permission.
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if the background location permission (ACCESS_BACKGROUND_LOCATION) is granted.
     * This permission is required for Android 10 (API level 29) and above to access location
     * when the app is in the background.
     * @return `true` if permission is granted or not required (on older Android versions), `false` otherwise.
     */
    fun hasBackgroundLocationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // For Android Q (API 29) and higher, explicitly check ACCESS_BACKGROUND_LOCATION.
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // For Android versions below Q, background location permission is implicitly covered
            // by foreground permissions, so return true.
            true
        }
    }

    /**
     * Retrieves the last known location from the Fused Location Provider.
     * This method is fast as it typically returns a cached location.
     * @return A [LocationData] object if a last known location is available and permissions are granted,
     * otherwise `null`.
     */
    suspend fun getLastKnownLocation(): LocationData? {
        if (!hasLocationPermissions()) {
            Log.w(TAG, "Location permissions not granted, cannot get last known location.")
            return null
        }

        return try {
            // Use `withTimeoutOrNull` to ensure the operation doesn't block indefinitely.
            withTimeoutOrNull(Constants.LOCATION_TIMEOUT) {
                // `await()` suspends the coroutine until the FusedLocationProviderClient's task completes.
                val location = fusedLocationClient.lastLocation.await()
                // Convert the Android Location object to our custom LocationData, if not null.
                location?.let { LocationData.fromAndroidLocation(it) }
            }
        } catch (e: SecurityException) {
            // Catch SecurityException if permissions are revoked just before the call.
            Log.e(TAG, "Security exception getting last location", e)
            null
        } catch (e: Exception) {
            // Catch any other general exceptions.
            Log.e(TAG, "Error getting last location", e)
            null
        }
    }

    /**
     * Retrieves the current location, actively requesting a fresh GPS fix if necessary.
     * This method might take longer than `getLastKnownLocation` as it may involve sensor usage.
     * @return A [LocationData] object representing the current location if successful and permissions are granted,
     * otherwise `null`.
     */
    suspend fun getCurrentLocation(): LocationData? {
        if (!hasLocationPermissions()) {
            Log.w(TAG, "Location permissions not granted, cannot get current location.")
            return null
        }

        return try {
            // Apply a timeout for the current location request.
            withTimeoutOrNull(Constants.LOCATION_TIMEOUT) {
                val cancellationTokenSource = CancellationTokenSource() // Allows cancelling the location request.

                // Request the current location with high accuracy priority.
                val location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY, // Request the most accurate location possible.
                    cancellationTokenSource.token // Token for cancellation.
                ).await() // Await the result of the task.

                location?.let {
                    val locationData = LocationData.fromAndroidLocation(it)
                    Log.d(TAG, "Current location obtained: ${locationData.getFormattedCoordinates()}")
                    locationData // Return the converted LocationData.
                }
            }
        } catch (e: SecurityException) {
            // Handle security exceptions due to permission issues.
            Log.e(TAG, "Security exception getting current location", e)
            null
        } catch (e: Exception) {
            // Handle any other exceptions during location retrieval.
            Log.e(TAG, "Error getting current location", e)
            null
        }
    }

    /**
     * Starts continuous real-time location updates.
     * This configures the Fused Location Provider to deliver updates at specified intervals.
     * @return `true` if location updates were successfully started, `false` otherwise.
     */
    suspend fun startLocationUpdates(): Boolean {
        if (!hasLocationPermissions()) {
            Log.w(TAG, "Cannot start location updates - permissions not granted.")
            return false
        }

        if (isRequestingUpdates) {
            Log.d(TAG, "Location updates are already started.")
            return true // Already running, so consider it a success.
        }

        return try {
            // Build the LocationRequest with desired parameters.
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, // Request high accuracy location.
                Constants.LOCATION_UPDATE_INTERVAL // Desired interval for location updates in milliseconds.
            ).apply {
                setMinUpdateIntervalMillis(Constants.LOCATION_FASTEST_INTERVAL) // Fastest interval for updates.
                setMaxUpdateDelayMillis(Constants.LOCATION_UPDATE_INTERVAL * 2) // Max time between updates.
                setWaitForAccurateLocation(false) // Do not wait for an accurate location before delivering the first one.
            }.build()

            // Define the LocationCallback to handle incoming location results.
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    // Extract the last (most recent) location from the result.
                    locationResult.lastLocation?.let { location ->
                        val locationData = LocationData.fromAndroidLocation(location)

                        // Only process and emit valid location data.
                        if (locationData.isValid()) {
                            Log.d(TAG, "New location update received: ${locationData.getFormattedCoordinates()}")
                            // Send the valid location data to the channel for consumers.
                            _locationUpdates.trySend(locationData)
                        } else {
                            Log.w(TAG, "Invalid location data received, discarding.")
                        }
                    }
                }

                override fun onLocationAvailability(availability: LocationAvailability) {
                    // Called when location availability changes (e.g., GPS turned off/on).
                    Log.d(TAG, "Location availability changed: ${availability.isLocationAvailable}")
                }
            }

            // Request location updates from FusedLocationProviderClient.
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!, // Use the non-null assertion as `locationCallback` is set above.
                Looper.getMainLooper() // Specify the Looper for the callback (main thread for simplicity).
            )

            isRequestingUpdates = true // Set flag to true as updates are now active.
            Log.d(TAG, "Location updates started successfully.")
            true

        } catch (e: SecurityException) {
            // Handle permission-related exceptions.
            Log.e(TAG, "Security exception starting location updates", e)
            false
        } catch (e: Exception) {
            // Handle any other general exceptions.
            Log.e(TAG, "Error starting location updates", e)
            false
        }
    }

    /**
     * Stops continuous location updates.
     * This releases the resources held by the Fused Location Provider.
     */
    fun stopLocationUpdates() {
        if (!isRequestingUpdates) {
            Log.d(TAG, "Location updates are not active, no need to stop.")
            return
        }

        try {
            locationCallback?.let { callback ->
                fusedLocationClient.removeLocationUpdates(callback) // Remove the registered callback.
                Log.d(TAG, "Location updates stopped successfully.")
            }

            locationCallback = null // Clear the callback reference.
            isRequestingUpdates = false // Update flag.

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates", e)
        }
    }

    /**
     * Checks if location updates are currently being requested.
     * @return `true` if updates are active, `false` otherwise.
     */
    fun isLocationUpdatesActive(): Boolean = isRequestingUpdates

    /**
     * Provides information about the current location request configuration.
     * @return A string detailing the update interval, fastest interval, and priority.
     */
    fun getLocationRequestInfo(): String {
        return "Update interval: ${Constants.LOCATION_UPDATE_INTERVAL}ms, " +
                "Fastest interval: ${Constants.LOCATION_FASTEST_INTERVAL}ms, " +
                "Priority: HIGH_ACCURACY"
    }

    /**
     * Checks if GPS (or any location provider that satisfies high accuracy) is enabled on the device.
     * This performs a check using LocationSettingsApi to verify device settings.
     * @return `true` if GPS is enabled and meets the high accuracy requirement, `false` otherwise.
     */
    suspend fun isGpsEnabled(): Boolean {
        return try {
            // Build a LocationSettingsRequest to check if device settings meet our needs.
            val locationSettingsRequest = LocationSettingsRequest.Builder()
                .addLocationRequest(
                    LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY, // Check for settings supporting high accuracy.
                        Constants.LOCATION_UPDATE_INTERVAL
                    ).build()
                )
                .setAlwaysShow(true) // Prompts the user to enable GPS if it's off.
                .build()

            val settingsClient = LocationServices.getSettingsClient(context)
            // Check location settings asynchronously.
            val locationSettingsResponse = settingsClient.checkLocationSettings(locationSettingsRequest).await()

            Log.d(TAG, "GPS is enabled (settings check passed).")
            true

        } catch (e: Exception) {
            // Catch exceptions, which often indicate that GPS is not enabled or settings are not met.
            Log.w(TAG, "GPS settings check failed or GPS is not enabled.", e)
            false
        }
    }

    /**
     * Obtener información del proveedor de ubicación
     */
    fun getLocationProviderInfo(): String {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            "GPS: ${if (gpsEnabled) "Enabled" else "Disabled"}, " +
                    "Network: ${if (networkEnabled) "Enabled" else "Disabled"}"

        } catch (e: Exception) {
            Log.e(TAG, "Error getting provider info", e)
            "Provider info unavailable"
        }
    }

    /**
     * Calcular precisión promedio de las ubicaciones recientes
     */
    private val recentAccuracies = mutableListOf<Float>()

    fun updateAccuracyStats(accuracy: Float?) {
        accuracy?.let {
            recentAccuracies.add(it)
            // Mantener solo las últimas 10 lecturas
            if (recentAccuracies.size > 10) {
                recentAccuracies.removeAt(0)
            }
        }
    }

    fun getAverageAccuracy(): Float? {
        return if (recentAccuracies.isNotEmpty()) {
            recentAccuracies.average().toFloat()
        } else {
            null
        }
    }

    /**
     * Limpiar recursos
     */
    fun cleanup() {
        stopLocationUpdates()
        _locationUpdates.close()
        recentAccuracies.clear()
        Log.d(TAG, "LocationRepository cleaned up")
    }
}