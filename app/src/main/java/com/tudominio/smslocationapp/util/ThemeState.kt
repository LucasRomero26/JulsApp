package com.tudominio.smslocation.util

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Estado global simple para el tema de la aplicación
 */
object ThemeState {
    private const val PREF_NAME = "theme_preferences"
    private const val KEY_DARK_THEME = "is_dark_theme"

    private var context: Context? = null

    var isDarkTheme by mutableStateOf(false)
        private set

    fun initialize(appContext: Context) {
        context = appContext
        // Cargar el tema guardado
        val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        isDarkTheme = prefs.getBoolean(KEY_DARK_THEME, false)
    }

    fun toggleTheme() {
        isDarkTheme = !isDarkTheme
        saveTheme()
    }

    private fun saveTheme() {
        context?.let {
            val prefs = it.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_DARK_THEME, isDarkTheme).apply()
        }
    }
}