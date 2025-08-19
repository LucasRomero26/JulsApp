package com.tudominio.smslocation.view.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tudominio.smslocation.util.ThemeState

// Color schemes, mapping the custom color palette to Material Design 3 roles.
// This color scheme is used for light theme.
private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = LightBlueGray,
    onTertiary = Lightest,
    tertiaryContainer = LightBlueGray.copy(alpha = 0.1f),
    onTertiaryContainer = MediumBlue,
    error = ErrorLight,
    errorContainer = ErrorContainerLight,
    onError = OnErrorLight,
    onErrorContainer = OnErrorContainerLight,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)

// This color scheme is used for dark theme.
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = Lightest,
    onTertiary = Darkest,
    tertiaryContainer = MediumBlue,
    onTertiaryContainer = Lightest,
    error = ErrorDark,
    errorContainer = ErrorContainerDark,
    onError = OnErrorDark,
    onErrorContainer = OnErrorContainerDark,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)

/**
 * Main theme composable for the application.
 * This composable applies the correct color scheme (light or dark) based on the
 * global `ThemeState` and sets the status bar color and appearance accordingly.
 *
 * @param content The composable content to which the theme will be applied.
 */
@Composable
fun SMSLocationAppTheme(
    content: @Composable () -> Unit
) {
    // Read the global dark theme state.
    val darkTheme = ThemeState.isDarkTheme
    // Select the appropriate color scheme based on the theme state.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Get the current view, which is needed to access the window.
    val view = LocalView.current
    // Skip this logic in preview mode to avoid issues.
    if (!view.isInEditMode) {
        // Use `SideEffect` to apply changes to the Android window outside of Compose's
        // recomposition, which is necessary for manipulating the system UI.
        SideEffect {
            val window = (view.context as Activity).window

            // Handle system bars appearance for Android 11 (API 30) and above.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    // Set the appearance of the system bars (status bar and navigation bar).
                    // This sets the appearance to light if `darkTheme` is false.
                    controller.setSystemBarsAppearance(
                        if (darkTheme) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                }
                // Also set the status bar color, a separate action.
                @Suppress("DEPRECATION") // Suppress deprecation warning for statusBarColor.
                window.statusBarColor = if (darkTheme) {
                    android.graphics.Color.parseColor("#011640") // Dark color for dark theme.
                } else {
                    android.graphics.Color.parseColor("#F2F2F2") // Light color for light theme.
                }
            }

            // Fallback for Android versions below 11 (API 30).
            // Use WindowCompat to manage the light/dark status bar icons.
            // `isAppearanceLightStatusBars = !darkTheme` ensures light icons on a dark status bar and vice versa.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // Apply the chosen Material Theme to the content.
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Apply the custom typography defined elsewhere.
        content = content // The content of the application.
    )
}