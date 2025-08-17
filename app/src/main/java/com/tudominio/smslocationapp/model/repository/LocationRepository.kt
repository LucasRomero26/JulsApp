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
 * Repository para manejo de datos de ubicación GPS
 */
class LocationRepository(private val context: Context) {

    companion object {
        private const val TAG = Constants.Logs.TAG_LOCATION
    }

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // Canal para emisión de ubicaciones en tiempo real
    private val _locationUpdates = Channel<LocationData>(Channel.UNLIMITED)
    val locationUpdates: Flow<LocationData> = _locationUpdates.receiveAsFlow()

    private var locationCallback: LocationCallback? = null
    private var isRequestingUpdates = false

    /**
     * Verificar si los permisos de ubicación están concedidos
     */
    fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Verificar si los permisos de ubicación en segundo plano están concedidos
     */
    fun hasBackgroundLocationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No requerido en versiones anteriores
        }
    }

    /**
     * Obtener la última ubicación conocida
     */
    suspend fun getLastKnownLocation(): LocationData? {
        if (!hasLocationPermissions()) {
            Log.w(TAG, "Location permissions not granted")
            return null
        }

        return try {
            withTimeoutOrNull(Constants.LOCATION_TIMEOUT) {
                val location = fusedLocationClient.lastLocation.await()
                location?.let { LocationData.fromAndroidLocation(it) }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting last location", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last location", e)
            null
        }
    }

    /**
     * Obtener ubicación actual (forzar nueva lectura GPS)
     */
    suspend fun getCurrentLocation(): LocationData? {
        if (!hasLocationPermissions()) {
            Log.w(TAG, "Location permissions not granted")
            return null
        }

        return try {
            withTimeoutOrNull(Constants.LOCATION_TIMEOUT) {
                val cancellationTokenSource = CancellationTokenSource()

                val location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).await()

                location?.let {
                    val locationData = LocationData.fromAndroidLocation(it)
                    Log.d(TAG, "Current location obtained: ${locationData.getFormattedCoordinates()}")
                    locationData
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting current location", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current location", e)
            null
        }
    }

    /**
     * Iniciar actualizaciones de ubicación en tiempo real
     */
    suspend fun startLocationUpdates(): Boolean {
        if (!hasLocationPermissions()) {
            Log.w(TAG, "Cannot start location updates - permissions not granted")
            return false
        }

        if (isRequestingUpdates) {
            Log.d(TAG, "Location updates already started")
            return true
        }

        return try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                Constants.LOCATION_UPDATE_INTERVAL
            ).apply {
                setMinUpdateIntervalMillis(Constants.LOCATION_FASTEST_INTERVAL)
                setMaxUpdateDelayMillis(Constants.LOCATION_UPDATE_INTERVAL * 2)
                setWaitForAccurateLocation(false)
            }.build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        val locationData = LocationData.fromAndroidLocation(location)

                        if (locationData.isValid()) {
                            Log.d(TAG, "New location update: ${locationData.getFormattedCoordinates()}")

                            // Enviar ubicación al canal
                            _locationUpdates.trySend(locationData)
                        } else {
                            Log.w(TAG, "Invalid location data received")
                        }
                    }
                }

                override fun onLocationAvailability(availability: LocationAvailability) {
                    Log.d(TAG, "Location availability changed: ${availability.isLocationAvailable}")
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )

            isRequestingUpdates = true
            Log.d(TAG, "Location updates started successfully")
            true

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting location updates", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates", e)
            false
        }
    }

    /**
     * Detener actualizaciones de ubicación
     */
    fun stopLocationUpdates() {
        if (!isRequestingUpdates) {
            Log.d(TAG, "Location updates not active")
            return
        }

        try {
            locationCallback?.let { callback ->
                fusedLocationClient.removeLocationUpdates(callback)
                Log.d(TAG, "Location updates stopped successfully")
            }

            locationCallback = null
            isRequestingUpdates = false

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates", e)
        }
    }

    /**
     * Verificar si las actualizaciones de ubicación están activas
     */
    fun isLocationUpdatesActive(): Boolean = isRequestingUpdates

    /**
     * Obtener configuración actual de la solicitud de ubicación
     */
    fun getLocationRequestInfo(): String {
        return "Update interval: ${Constants.LOCATION_UPDATE_INTERVAL}ms, " +
                "Fastest interval: ${Constants.LOCATION_FASTEST_INTERVAL}ms, " +
                "Priority: HIGH_ACCURACY"
    }

    /**
     * Verificar si el GPS está habilitado en el dispositivo
     */
    suspend fun isGpsEnabled(): Boolean {
        return try {
            val locationSettingsRequest = LocationSettingsRequest.Builder()
                .addLocationRequest(
                    LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        Constants.LOCATION_UPDATE_INTERVAL
                    ).build()
                )
                .setAlwaysShow(true)
                .build()

            val settingsClient = LocationServices.getSettingsClient(context)
            val locationSettingsResponse = settingsClient.checkLocationSettings(locationSettingsRequest).await()

            Log.d(TAG, "GPS is enabled")
            true

        } catch (e: Exception) {
            Log.w(TAG, "GPS settings check failed", e)
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