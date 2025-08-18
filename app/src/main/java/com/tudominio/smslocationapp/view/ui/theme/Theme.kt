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

// Esquema de colores claro con nueva paleta
private val LightColorScheme = lightColorScheme(
    // Colores primarios
    primary = LightPrimary,                         // #052940
    onPrimary = LightOnPrimary,                     // #F2F2F2
    primaryContainer = PrimaryContainerLight,        // #8097A6 con alpha
    onPrimaryContainer = OnPrimaryContainerLight,    // #052940

    // Colores secundarios
    secondary = LightSecondary,                      // #8097A6
    onSecondary = LightOnSecondary,                  // #F2F2F2
    secondaryContainer = SecondaryContainerLight,    // #8097A6 con alpha
    onSecondaryContainer = OnSecondaryContainerLight, // #052940

    // Colores terciarios (usando la misma paleta)
    tertiary = LightBlueGray,                        // #8097A6
    onTertiary = Lightest,                           // #F2F2F2
    tertiaryContainer = LightBlueGray.copy(alpha = 0.1f), // Tint suave
    onTertiaryContainer = MediumBlue,                // #052940

    // Colores de error
    error = ErrorLight,                              // #BA1A1A
    errorContainer = ErrorContainerLight,            // #FFDAD6
    onError = OnErrorLight,                          // #FFFFFF
    onErrorContainer = OnErrorContainerLight,        // #410002

    // Colores de fondo y superficie
    background = LightBackground,                    // #F2F2F2
    onBackground = LightOnBackground,                // #011640
    surface = LightSurface,                          // #F2F2F2
    onSurface = LightOnSurface,                      // #052940
    surfaceVariant = SurfaceVariantLight,            // #8097A6 con alpha
    onSurfaceVariant = OnSurfaceVariantLight,        // #052940

    // Contornos
    outline = OutlineLight,                          // #8097A6
    outlineVariant = OutlineVariantLight,            // #052940
)

// Esquema de colores oscuro con nueva paleta
private val DarkColorScheme = darkColorScheme(
    // Colores primarios
    primary = DarkPrimary,                          // #8097A6
    onPrimary = DarkOnPrimary,                      // #011640
    primaryContainer = PrimaryContainerDark,         // #052940
    onPrimaryContainer = OnPrimaryContainerDark,     // #F2F2F2

    // Colores secundarios
    secondary = DarkSecondary,                       // #F2F2F2
    onSecondary = DarkOnSecondary,                   // #011640
    secondaryContainer = SecondaryContainerDark,     // #052940
    onSecondaryContainer = OnSecondaryContainerDark, // #F2F2F2

    // Colores terciarios
    tertiary = Lightest,                             // #F2F2F2
    onTertiary = Darkest,                            // #011640
    tertiaryContainer = MediumBlue,                  // #052940
    onTertiaryContainer = Lightest,                  // #F2F2F2

    // Colores de error
    error = ErrorDark,                               // #FFB4AB
    errorContainer = ErrorContainerDark,             // #93000A
    onError = OnErrorDark,                           // #690005
    onErrorContainer = OnErrorContainerDark,         // #FFDAD6

    // Colores de fondo y superficie
    background = DarkBackground,                     // #011640
    onBackground = DarkOnBackground,                 // #F2F2F2
    surface = DarkSurface,                           // #052940
    onSurface = DarkOnSurface,                       // #F2F2F2
    surfaceVariant = SurfaceVariantDark,             // #052940
    onSurfaceVariant = OnSurfaceVariantDark,         // #8097A6

    // Contornos
    outline = OutlineDark,                           // #052940
    outlineVariant = OutlineVariantDark,             // #8097A6
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

    // Usar siempre nuestra paleta personalizada (sin colores dinámicos del sistema)
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

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
                // Configurar color de la barra de estado
                @Suppress("DEPRECATION")
                window.statusBarColor = if (darkTheme) {
                    Darkest.value.toInt()  // #011640 para modo oscuro
                } else {
                    Lightest.value.toInt() // #F2F2F2 para modo claro
                }
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