package com.tudominio.smslocation.view.ui.theme

import androidx.compose.ui.graphics.Color

// New custom color palette for the Juls application.
// These are base colors that will be used to define the light and dark themes.
val Lightest = Color(0xFFF2F2F2)        // Very light gray, almost white.
val LightBlueGray = Color(0xFF8097A6)   // A muted blue-gray tone.
val MediumBlue = Color(0xFF052940)      // A deep, dark blue.
val DarkBlue = Color(0xFF052940)        // Duplicate of MediumBlue as per specification.
val Darkest = Color(0xFF011640)         // A very dark, almost black blue.

// Colors for Light Theme, mapping custom palette colors to Material Design 3 roles.
val LightPrimary = MediumBlue           // Main accent color for light theme.
val LightOnPrimary = Lightest           // Color for text/icons displayed on `primary` color.
val LightSecondary = LightBlueGray      // Secondary accent color for light theme.
val LightOnSecondary = Lightest         // Color for text/icons displayed on `secondary` color.
val LightBackground = Lightest          // Background color of the whole screen.
val LightOnBackground = Darkest         // Color for text/icons displayed on `background` color.
val LightSurface = Lightest             // Color for cards, sheets, and dialogs.
val LightOnSurface = MediumBlue         // Color for text/icons displayed on `surface` color.

// Colors for Dark Theme, mapping custom palette colors to Material Design 3 roles.
val DarkPrimary = LightBlueGray         // Main accent color for dark theme.
val DarkOnPrimary = Darkest             // Color for text/icons displayed on `primary` color in dark theme.
val DarkSecondary = Lightest            // Secondary accent color for dark theme.
val DarkOnSecondary = Darkest           // Color for text/icons displayed on `secondary` color in dark theme.
val DarkBackground = Darkest            // Background color of the whole screen in dark theme.
val DarkOnBackground = Lightest         // Color for text/icons displayed on `background` color in dark theme.
val DarkSurface = MediumBlue            // Color for cards, sheets, and dialogs in dark theme.
val DarkOnSurface = Lightest            // Color for text/icons displayed on `surface` color in dark theme.

// Error Colors (following standard Material Design error palette for consistency).
val ErrorLight = Color(0xFFBA1A1A)          // Standard red for error in light theme.
val ErrorDark = Color(0xFFFFB4AB)           // Lighter red for error in dark theme.
val ErrorContainerLight = Color(0xFFFFDAD6)  // Light background for error messages/components in light theme.
val ErrorContainerDark = Color(0xFF93000A)   // Dark background for error messages/components in dark theme.
val OnErrorLight = Color(0xFFFFFFFF)        // Color for text/icons on `error` color in light theme.
val OnErrorDark = Color(0xFF690005)         // Color for text/icons on `error` color in dark theme.
val OnErrorContainerLight = Color(0xFF410002) // Color for text/icons on `errorContainer` in light theme.
val OnErrorContainerDark = Color(0xFFFFDAD6) // Color for text/icons on `errorContainer` in dark theme.

// Outline Colors, used for borders and dividers.
val OutlineLight = LightBlueGray        // Outline color for light theme.
val OutlineDark = MediumBlue            // Outline color for dark theme.
val OutlineVariantLight = MediumBlue    // Variant outline color for light theme.
val OutlineVariantDark = LightBlueGray  // Variant outline color for dark theme.

// Primary Container Colors, used for themed containers that are distinct from primary.
val PrimaryContainerLight = LightBlueGray.copy(alpha = 0.1f)  // Soft tint of blue-gray for light theme primary containers.
val PrimaryContainerDark = MediumBlue                          // Dark blue for dark theme primary containers.
val OnPrimaryContainerLight = MediumBlue                       // Color for text/icons on `primaryContainer` in light theme.
val OnPrimaryContainerDark = Lightest                          // Color for text/icons on `primaryContainer` in dark theme.

// Secondary Container Colors, used for themed containers that are distinct from secondary.
val SecondaryContainerLight = LightBlueGray.copy(alpha = 0.1f) // Soft tint for light theme secondary containers.
val SecondaryContainerDark = DarkBlue                          // Dark blue for dark theme secondary containers.
val OnSecondaryContainerLight = MediumBlue                     // Color for text/icons on `secondaryContainer` in light theme.
val OnSecondaryContainerDark = Lightest                        // Color for text/icons on `secondaryContainer` in dark theme.

// Surface Variant Colors, used for less prominent surfaces than `surface`.
val SurfaceVariantLight = LightBlueGray.copy(alpha = 0.1f)    // Very soft tint for light theme surface variant.
val SurfaceVariantDark = MediumBlue                           // Dark blue for dark theme surface variant.
val OnSurfaceVariantLight = MediumBlue                        // Color for text/icons on `surfaceVariant` in light theme.
val OnSurfaceVariantDark = LightBlueGray                      // Color for text/icons on `surfaceVariant` in dark theme.