package com.tudominio.smslocation.view.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tudominio.smslocation.util.ThemePreferences
import kotlinx.coroutines.flow.MutableStateFlow

// Esquema de colores claro
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    errorContainer = ErrorContainer,
    onError = OnError,
    onErrorContainer = OnErrorContainer,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
)

// Esquema de colores oscuro
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryContainer,
    onPrimary = OnPrimaryContainer,
    primaryContainer = Primary,
    onPrimaryContainer = OnPrimary,
    secondary = SecondaryContainer,
    onSecondary = OnSecondaryContainer,
    secondaryContainer = Secondary,
    onSecondaryContainer = OnSecondary,
    tertiary = TertiaryContainer,
    onTertiary = OnTertiaryContainer,
    tertiaryContainer = Tertiary,
    onTertiaryContainer = OnTertiary,
    error = ErrorContainer,
    errorContainer = Error,
    onError = OnErrorContainer,
    onErrorContainer = OnError,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = Outline,
    outlineVariant = OutlineVariant,
)

@Composable
fun SMSLocationAppTheme(
    themePreferences: ThemePreferences? = null,
    content: @Composable () -> Unit
) {
    // Obtener estados del tema
    val followSystemTheme by (themePreferences?.followSystemTheme ?: MutableStateFlow(true)).collectAsState()
    val savedDarkTheme by (themePreferences?.isDarkTheme ?: MutableStateFlow(false)).collectAsState()
    val systemDarkTheme = isSystemInDarkTheme()

    // Determinar si usar tema oscuro
    val darkTheme = if (followSystemTheme) systemDarkTheme else savedDarkTheme

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            // Configurar barra de estado con API moderna solamente
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // API moderna para Android 11+
                window.insetsController?.let { controller ->
                    controller.setSystemBarsAppearance(
                        if (darkTheme) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                }
                // Suprimir warning para esta API específica que necesitamos usar
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
            }

            // Para todas las versiones, configurar apariencia de iconos
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}