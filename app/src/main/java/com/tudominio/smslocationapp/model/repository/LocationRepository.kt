package com.tudominio.smslocation.model.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import com.tudominio.smslocation.model.data.LocationData
import com.tudominio.smslocation.util.Constants
import com.tudominio.smslocation.util.DeviceUtils
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Repository ultra-simple - NO acumula memoria, solo obtiene y pasa ubicaciones
 * Updated to support device identification for multiple devices
 */
class SimpleLocationRepository(private val context: Context) {

    companion object {
        private const val TAG = Constants.Logs.TAG_LOCATION
    }

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private var locationCallback: LocationCallback? = null

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

    fun hasBackgroundLocationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    suspend fun getCurrentLocationOnce(): LocationData? {
        if (!hasLocationPermissions()) return null

        return try {
            withTimeoutOrNull(5000) {
                val cancellationTokenSource = CancellationTokenSource()
                val location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).await()

                location?.let {
                    LocationData.fromAndroidLocation(
                        it,
                        deviceId = DeviceUtils.getDeviceId(context),
                        deviceName = DeviceUtils.getDeviceName(context)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }

    fun startLocationUpdates(onLocation: (LocationData) -> Unit): Boolean {
        if (!hasLocationPermissions()) return false

        return try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                Constants.LOCATION_UPDATE_INTERVAL
            ).build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        val locationData = LocationData.fromAndroidLocation(
                            location,
                            deviceId = DeviceUtils.getDeviceId(context),
                            deviceName = DeviceUtils.getDeviceName(context)
                        )
                        if (locationData.isValid()) {
                            // Llamar inmediatamente sin acumular
                            onLocation(locationData)
                        }
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )

            Log.d(TAG, "Simple location updates started for device: ${DeviceUtils.getDeviceId(context)}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error starting updates", e)
            false
        }
    }

    fun stopLocationUpdates() {
        try {
            locationCallback?.let { callback ->
                fusedLocationClient.removeLocationUpdates(callback)
            }
            locationCallback = null
            Log.d(TAG, "Location updates stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping updates", e)
        }
    }

    fun cleanup() {
        stopLocationUpdates()
    }

    /**
     * Gets device information for debugging purposes
     */
    fun getDeviceInfo(): String {
        return buildString {
            appendLine("=== Device Information ===")
            appendLine("Device ID: ${DeviceUtils.getDeviceId(context)}")
            appendLine("Device Name: ${DeviceUtils.getDeviceName(context)}")
            appendLine("Android Version: ${DeviceUtils.getAndroidVersion()}")
            appendLine("Full Device Name: ${DeviceUtils.getDeviceFullName()}")
        }
    }
}