package com.tudominio.smslocation.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Helper para manejo de permisos de la aplicación
 */
object PermissionHelper {

    /**
     * Permisos básicos de ubicación
     */
    val LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /**
     * Permisos de ubicación en segundo plano (Android 10+)
     */
    val BACKGROUND_LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )

    /**
     * Verificar si los permisos básicos de ubicación están concedidos
     */
    fun hasLocationPermissions(context: Context): Boolean {
        return LOCATION_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Verificar si el permiso de ubicación en segundo plano está concedido
     */
    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No requerido en versiones anteriores
        }
    }

    /**
     * Verificar si todos los permisos necesarios están concedidos
     */
    fun hasAllPermissions(context: Context): Boolean {
        return hasLocationPermissions(context) && hasBackgroundLocationPermission(context)
    }

    /**
     * Obtener lista de permisos necesarios según la versión de Android
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            LOCATION_PERMISSIONS + BACKGROUND_LOCATION_PERMISSIONS
        } else {
            LOCATION_PERMISSIONS
        }
    }

    /**
     * Obtener permisos faltantes
     */
    fun getMissingPermissions(context: Context): List<String> {
        val missingPermissions = mutableListOf<String>()

        LOCATION_PERMISSIONS.forEach { permission ->
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!hasBackgroundLocationPermission(context)) {
                missingPermissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        return missingPermissions
    }
}