package com.tudominio.smslocation.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Helper mejorado para manejo de permisos de la aplicación
 * Con soporte especial para Android 10+ y permisos de ubicación en segundo plano
 */
object PermissionHelper {

    /**
     * Permisos básicos de ubicación (requeridos para cualquier función GPS)
     */
    val BASIC_LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /**
     * Permiso de ubicación en segundo plano (solo Android 10+)
     */
    const val BACKGROUND_LOCATION_PERMISSION = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    /**
     * Verificar si los permisos básicos de ubicación están concedidos
     */
    fun hasBasicLocationPermissions(context: Context): Boolean {
        return BASIC_LOCATION_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Verificar si el permiso de ubicación en segundo plano está concedido
     * En Android 9 y anteriores, este permiso no existe, por lo que devuelve true
     */
    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                BACKGROUND_LOCATION_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No requerido en versiones anteriores a Android 10
        }
    }

    /**
     * Verificar si todos los permisos necesarios están concedidos
     */
    fun hasAllLocationPermissions(context: Context): Boolean {
        return hasBasicLocationPermissions(context) && hasBackgroundLocationPermission(context)
    }

    /**
     * Verificar si se requiere el permiso de ubicación en segundo plano
     * basado en la versión de Android
     */
    fun requiresBackgroundLocationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    /**
     * Obtener lista de permisos básicos faltantes
     */
    fun getMissingBasicPermissions(context: Context): List<String> {
        return BASIC_LOCATION_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Verificar si falta el permiso de ubicación en segundo plano
     */
    fun isMissingBackgroundPermission(context: Context): Boolean {
        return requiresBackgroundLocationPermission() && !hasBackgroundLocationPermission(context)
    }

    /**
     * Obtener texto descriptivo del estado de permisos
     */
    fun getPermissionStatusText(context: Context): String {
        val hasBasic = hasBasicLocationPermissions(context)
        val hasBackground = hasBackgroundLocationPermission(context)

        return when {
            hasBasic && hasBackground -> "All permissions granted"
            hasBasic && !hasBackground && requiresBackgroundLocationPermission() ->
                "Background location permission needed"
            !hasBasic -> "Basic location permissions needed"
            else -> "Location permissions required"
        }
    }

    /**
     * Verificar si la app puede funcionar con los permisos actuales
     * (permisos básicos son suficientes para funcionamiento básico)
     */
    fun canFunctionWithCurrentPermissions(context: Context): Boolean {
        return hasBasicLocationPermissions(context)
    }

    /**
     * Verificar si la app puede funcionar en segundo plano
     */
    fun canFunctionInBackground(context: Context): Boolean {
        return hasBasicLocationPermissions(context) && hasBackgroundLocationPermission(context)
    }

    /**
     * Obtener el siguiente permiso que se debe solicitar
     * Devuelve null si todos los permisos están concedidos
     */
    fun getNextPermissionToRequest(context: Context): String? {
        return when {
            !hasBasicLocationPermissions(context) -> {
                // Primero solicitar permisos básicos
                BASIC_LOCATION_PERMISSIONS.firstOrNull { permission ->
                    ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
                }
            }
            requiresBackgroundLocationPermission() && !hasBackgroundLocationPermission(context) -> {
                // Después solicitar permiso de segundo plano
                BACKGROUND_LOCATION_PERMISSION
            }
            else -> null // Todos los permisos concedidos
        }
    }

    /**
     * Verificar si se debe mostrar una explicación para un permiso específico
     * (Este método debe ser llamado desde una Activity)
     */
    fun shouldShowPermissionRationale(activity: android.app.Activity, permission: String): Boolean {
        return androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    /**
     * Obtener mensaje explicativo para cada permiso
     */
    fun getPermissionExplanation(permission: String): String {
        return when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION ->
                "Location access is required to track your GPS position and send it to servers."

            Manifest.permission.ACCESS_BACKGROUND_LOCATION ->
                "Background location access is required to continue tracking when the app is not visible. " +
                        "Please select 'Allow all the time' in the next screen."

            else -> "This permission is required for the app to function properly."
        }
    }

    /**
     * Verificar si los permisos están en estado "denegado permanentemente"
     */
    fun arePermissionsPermanentlyDenied(
        activity: android.app.Activity,
        permissions: Array<String>
    ): Boolean {
        return permissions.any { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED &&
                    !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }

    /**
     * Obtener configuración recomendada para solicitud de permisos en Android 10+
     */
    fun getRecommendedPermissionStrategy(): PermissionStrategy {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PermissionStrategy.TWO_STEP_ANDROID_10_PLUS
        } else {
            PermissionStrategy.SINGLE_STEP_LEGACY
        }
    }

    /**
     * Enum para estrategias de solicitud de permisos
     */
    enum class PermissionStrategy {
        SINGLE_STEP_LEGACY,        // Android 9 y anteriores: solicitar todos a la vez
        TWO_STEP_ANDROID_10_PLUS   // Android 10+: primero básicos, luego segundo plano
    }
}