package com.tudominio.smslocation.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Manejo simple y directo de preferencias del tema
 */
class ThemePreferences(context: Context) {

    companion object {
        private const val PREF_NAME = "juls_theme_preferences"
        private const val KEY_IS_DARK_THEME = "is_dark_theme"
    }

    private val preferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // Estado interno privado
    private var _isDarkTheme by mutableStateOf(preferences.getBoolean(KEY_IS_DARK_THEME, false))

    // Propiedad pública de solo lectura
    val isDarkTheme: Boolean
        get() = _isDarkTheme

    /**
     * Alternar entre tema claro y oscuro
     */
    fun toggleTheme() {
        _isDarkTheme = !_isDarkTheme
        saveToPreferences()
    }

    /**
     * Cambiar tema programáticamente
     */
    fun changeToDarkTheme(dark: Boolean) {
        _isDarkTheme = dark
        saveToPreferences()
    }

    /**
     * Guardar en SharedPreferences
     */
    private fun saveToPreferences() {
        preferences.edit()
            .putBoolean(KEY_IS_DARK_THEME, _isDarkTheme)
            .apply()
    }

    /**
     * Obtener estado actual como string para debug
     */
    fun getCurrentThemeState(): String {
        return if (_isDarkTheme) "DARK" else "LIGHT"
    }
}