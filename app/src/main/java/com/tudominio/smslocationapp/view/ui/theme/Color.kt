package com.tudominio.smslocation.view.ui.theme

import androidx.compose.ui.graphics.Color

// Nueva paleta de colores personalizada
val Lightest = Color(0xFFF2F2F2)        // #F2F2F2 - Más claro
val LightBlueGray = Color(0xFF8097A6)   // #8097A6
val MediumBlue = Color(0xFF052940)      // #052940
val DarkBlue = Color(0xFF052940)        // #052940 (duplicado como en tu especificación)
val Darkest = Color(0xFF011640)         // #011640 - Más oscuro

// Colores para modo claro
val LightPrimary = MediumBlue           // #052940 para elementos principales
val LightOnPrimary = Lightest           // #F2F2F2 para texto sobre primary
val LightSecondary = LightBlueGray      // #8097A6 para elementos secundarios
val LightOnSecondary = Lightest         // #F2F2F2 para texto sobre secondary
val LightBackground = Lightest          // #F2F2F2 para fondo
val LightOnBackground = Darkest         // #011640 para texto sobre fondo
val LightSurface = Lightest             // #F2F2F2 para superficies
val LightOnSurface = MediumBlue         // #052940 para texto sobre superficies

// Colores para modo oscuro
val DarkPrimary = LightBlueGray         // #8097A6 para elementos principales
val DarkOnPrimary = Darkest             // #011640 para texto sobre primary
val DarkSecondary = Lightest            // #F2F2F2 para elementos secundarios
val DarkOnSecondary = Darkest           // #011640 para texto sobre secondary
val DarkBackground = Darkest            // #011640 para fondo
val DarkOnBackground = Lightest         // #F2F2F2 para texto sobre fondo
val DarkSurface = MediumBlue            // #052940 para superficies
val DarkOnSurface = Lightest            // #F2F2F2 para texto sobre superficies

// Colores de error (mantener estándar Material Design)
val ErrorLight = Color(0xFFBA1A1A)
val ErrorDark = Color(0xFFFFB4AB)
val ErrorContainerLight = Color(0xFFFFDAD6)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorLight = Color(0xFFFFFFFF)
val OnErrorDark = Color(0xFF690005)
val OnErrorContainerLight = Color(0xFF410002)
val OnErrorContainerDark = Color(0xFFFFDAD6)

// Colores de contorno
val OutlineLight = LightBlueGray        // #8097A6
val OutlineDark = MediumBlue            // #052940
val OutlineVariantLight = MediumBlue    // #052940
val OutlineVariantDark = LightBlueGray  // #8097A6

// Colores para contenedores primarios
val PrimaryContainerLight = LightBlueGray.copy(alpha = 0.1f)  // Tint suave del gris azulado
val PrimaryContainerDark = MediumBlue                          // #052940
val OnPrimaryContainerLight = MediumBlue                       // #052940
val OnPrimaryContainerDark = Lightest                          // #F2F2F2

// Colores para contenedores secundarios
val SecondaryContainerLight = LightBlueGray.copy(alpha = 0.1f) // Tint suave
val SecondaryContainerDark = DarkBlue                          // #052940
val OnSecondaryContainerLight = MediumBlue                     // #052940
val OnSecondaryContainerDark = Lightest                        // #F2F2F2

// Colores de superficie variante
val SurfaceVariantLight = LightBlueGray.copy(alpha = 0.1f)    // Tint muy suave
val SurfaceVariantDark = MediumBlue                           // #052940
val OnSurfaceVariantLight = MediumBlue                        // #052940
val OnSurfaceVariantDark = LightBlueGray                      // #8097A6