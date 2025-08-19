package com.tudominio.smslocation.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Improved helper for managing application permissions.
 * Provides specialized support for Android 10+ and background location permissions,
 * offering methods to check, request, and explain permissions effectively.
 */
object PermissionHelper {

    /**
     * Basic location permissions (required for any GPS functionality).
     * These are ACCESS_FINE_LOCATION (precise) and ACCESS_COARSE_LOCATION (approximate).
     */
    val BASIC_LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /**
     * The background location permission string (only applicable for Android 10+).
     * This permission allows the app to access location when in the background.
     */
    const val BACKGROUND_LOCATION_PERMISSION = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    /**
     * Checks if the basic (foreground) location permissions are granted.
     * This method iterates through the [BASIC_LOCATION_PERMISSIONS] array and ensures all are granted.
     * @param context The application context.
     * @return `true` if all basic location permissions are granted, `false` otherwise.
     */
    fun hasBasicLocationPermissions(context: Context): Boolean {
        return BASIC_LOCATION_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if the background location permission is granted.
     * For Android versions prior to 10 (API 29), this permission does not exist,
     * so it always returns `true` as it's not required.
     * @param context The application context.
     * @return `true` if background location permission is granted (or not required), `false` otherwise.
     */
    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // On Android 10 (API 29) and above, explicitly check for ACCESS_BACKGROUND_LOCATION.
            ContextCompat.checkSelfPermission(
                context,
                BACKGROUND_LOCATION_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // On Android versions below 10, background location is covered by foreground permissions.
            true
        }
    }

    /**
     * Checks if all necessary location permissions (basic and background if applicable) are granted.
     * @param context The application context.
     * @return `true` if all required location permissions are granted, `false` otherwise.
     */
    fun hasAllLocationPermissions(context: Context): Boolean {
        return hasBasicLocationPermissions(context) && hasBackgroundLocationPermission(context)
    }

    /**
     * Checks if the background location permission is required based on the Android version.
     * It is required for Android 10 (API 29) and above.
     * @return `true` if background location permission needs to be considered, `false` otherwise.
     */
    fun requiresBackgroundLocationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    /**
     * Gets a list of basic location permissions that are currently missing.
     * @param context The application context.
     * @return A [List] of [String] containing the names of missing basic permissions.
     */
    fun getMissingBasicPermissions(context: Context): List<String> {
        return BASIC_LOCATION_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if the background location permission is missing and required for the current Android version.
     * @param context The application context.
     * @return `true` if background permission is required but not granted, `false` otherwise.
     */
    fun isMissingBackgroundPermission(context: Context): Boolean {
        return requiresBackgroundLocationPermission() && !hasBackgroundLocationPermission(context)
    }

    /**
     * Provides a descriptive text summarizing the current state of location permissions.
     * This can be used for UI feedback to the user.
     * @param context The application context.
     * @return A [String] describing the permission status.
     */
    fun getPermissionStatusText(context: Context): String {
        val hasBasic = hasBasicLocationPermissions(context)
        val hasBackground = hasBackgroundLocationPermission(context)

        return when {
            hasBasic && hasBackground -> "All permissions granted"
            hasBasic && !hasBackground && requiresBackgroundLocationPermission() ->
                "Background location permission needed"
            !hasBasic -> "Basic location permissions needed"
            else -> "Location permissions required" // Generic message if conditions don't match exactly.
        }
    }

    /**
     * Checks if the application can function with the currently granted permissions.
     * For basic functionality, only basic location permissions are sufficient.
     * @param context The application context.
     * @return `true` if basic location permissions are granted, `false` otherwise.
     */
    fun canFunctionWithCurrentPermissions(context: Context): Boolean {
        return hasBasicLocationPermissions(context)
    }

    /**
     * Checks if the application has all necessary permissions to function correctly in the background.
     * This includes both basic and background location permissions (if applicable).
     * @param context The application context.
     * @return `true` if all required permissions for background operation are granted, `false` otherwise.
     */
    fun canFunctionInBackground(context: Context): Boolean {
        return hasBasicLocationPermissions(context) && hasBackgroundLocationPermission(context)
    }

    /**
     * Determines the next permission that needs to be requested from the user.
     * This method prioritizes basic location permissions over background location permission.
     * @param context The application context.
     * @return The string name of the next permission to request, or `null` if all permissions are granted.
     */
    fun getNextPermissionToRequest(context: Context): String? {
        return when {
            !hasBasicLocationPermissions(context) -> {
                // First, check for any missing basic permissions and return the first one found.
                BASIC_LOCATION_PERMISSIONS.firstOrNull { permission ->
                    ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
                }
            }
            requiresBackgroundLocationPermission() && !hasBackgroundLocationPermission(context) -> {
                // If basic permissions are granted but background is missing (and required), request it.
                BACKGROUND_LOCATION_PERMISSION
            }
            else -> null // All necessary permissions are granted.
        }
    }

    /**
     * Checks if the system recommends showing a rationale for a specific permission.
     * This method should be called from an `Activity` context as it relies on `ActivityCompat.shouldShowRequestPermissionRationale`.
     * @param activity The hosting Activity.
     * @param permission The string name of the permission to check.
     * @return `true` if an explanation should be shown to the user before requesting the permission, `false` otherwise.
     */
    fun shouldShowPermissionRationale(activity: android.app.Activity, permission: String): Boolean {
        return androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    /**
     * Provides a user-friendly explanation message for a given permission.
     * These messages help inform the user why a specific permission is needed.
     * @param permission The string name of the permission.
     * @return A descriptive string explaining the purpose of the permission.
     */
    fun getPermissionExplanation(permission: String): String {
        return when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION ->
                "Location access is required to track your GPS position and send it to servers."

            Manifest.permission.ACCESS_BACKGROUND_LOCATION ->
                "Background location access is required to continue tracking when the app is not visible. " +
                        "Please select 'Allow all the time' in the next screen to ensure continuous tracking."

            else -> "This permission is required for the app to function properly."
        }
    }

    /**
     * Checks if any of the given permissions are permanently denied by the user.
     * A permission is permanently denied if it's not granted and `shouldShowRequestPermissionRationale` returns `false`.
     * In such cases, the user needs to manually enable the permission from app settings.
     * @param activity The hosting Activity.
     * @param permissions An array of permission strings to check.
     * @return `true` if any of the permissions are permanently denied, `false` otherwise.
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
     * Provides the recommended permission request strategy based on the Android version.
     * On Android 10 (API 29) and above, a two-step approach is generally recommended
     * (basic first, then background). On older versions, a single-step approach is fine.
     * @return A [PermissionStrategy] enum indicating the recommended approach.
     */
    fun getRecommendedPermissionStrategy(): PermissionStrategy {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PermissionStrategy.TWO_STEP_ANDROID_10_PLUS
        } else {
            PermissionStrategy.SINGLE_STEP_LEGACY
        }
    }

    /**
     * Enum defining different strategies for requesting permissions.
     */
    enum class PermissionStrategy {
        SINGLE_STEP_LEGACY,        // For Android 9 and older: Request all necessary permissions at once.
        TWO_STEP_ANDROID_10_PLUS   // For Android 10 and newer: Request basic permissions first, then background.
    }
}