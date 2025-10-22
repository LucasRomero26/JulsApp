package com.tudominio.smslocation.util

import android.content.Context
import android.provider.Settings
import java.util.*

/**
 * Utility class for device identification and information.
 */
object DeviceUtils {

    private const val DEVICE_PREFS = "device_prefs"
    private const val UNIQUE_DEVICE_ID_KEY = "unique_device_id"
    private const val DEVICE_PREFIX = "device_"

    /**
     * Gets a unique device ID for this device.
     * First tries to use Android ID, then falls back to generating and storing a UUID.
     */
    fun getDeviceId(context: Context): String {
        // Opción 1: Usar Android ID (recomendado)
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        return if (androidId != null && androidId != "9774d56d682e549c") {
            "${DEVICE_PREFIX}$androidId"
        } else {
            // Opción 2: Generar UUID único y guardarlo en SharedPreferences
            generateAndSaveUniqueId(context)
        }
    }

    /**
     * Generates a unique ID and saves it to SharedPreferences for future use.
     */
    private fun generateAndSaveUniqueId(context: Context): String {
        val prefs = context.getSharedPreferences(DEVICE_PREFS, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(UNIQUE_DEVICE_ID_KEY, null)

        if (deviceId == null) {
            deviceId = "${DEVICE_PREFIX}${UUID.randomUUID().toString().replace("-", "").substring(0, 12)}"
            prefs.edit().putString(UNIQUE_DEVICE_ID_KEY, deviceId).apply()
        }

        return deviceId
    }

    /**
     * Gets a human-readable device name.
     */
    fun getDeviceName(context: Context): String {
        return android.os.Build.MODEL ?: "Unknown Device"
    }

    /**
     * Gets device manufacturer and model.
     */
    fun getDeviceFullName(): String {
        val manufacturer = android.os.Build.MANUFACTURER
        val model = android.os.Build.MODEL

        return if (model.startsWith(manufacturer)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    /**
     * Gets Android version information.
     */
    fun getAndroidVersion(): String {
        return "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
    }
}