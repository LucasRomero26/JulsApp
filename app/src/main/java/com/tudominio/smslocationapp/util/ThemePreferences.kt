package com.tudominio.smslocation.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manejo de preferencias del tema de la aplicación
 */
class ThemePreferences(context: Context) {

    companion object {
        private const val PREF_NAME = "juls_theme_preferences"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_FOLLOW_SYSTEM = "follow_system"
    }

    private val preferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // Estados observables
    private val _isDarkTheme = MutableStateFlow(getStoredDarkTheme())
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _followSystemTheme = MutableStateFlow(getStoredFollowSystem())
    val followSystemTheme: StateFlow<Boolean> = _followSystemTheme.asStateFlow()

    /**
     * Obtener preferencia de tema oscuro almacenada
     */
    private fun getStoredDarkTheme(): Boolean {
        return preferences.getBoolean(KEY_DARK_THEME, false)
    }

    /**
     * Obtener preferencia de seguir tema del sistema
     */
    private fun getStoredFollowSystem(): Boolean {
        return preferences.getBoolean(KEY_FOLLOW_SYSTEM, true) // Por defecto seguir sistema
    }

    /**
     * Establecer tema oscuro
     */
    fun setDarkTheme(isDark: Boolean) {
        preferences.edit()
            .putBoolean(KEY_DARK_THEME, isDark)
            .putBoolean(KEY_FOLLOW_SYSTEM, false) // Desactivar seguimiento del sistema
            .apply()

        _isDarkTheme.value = isDark
        _followSystemTheme.value = false
    }

    /**
     * Establecer seguimiento del tema del sistema
     */
    fun setFollowSystemTheme(follow: Boolean) {
        preferences.edit()
            .putBoolean(KEY_FOLLOW_SYSTEM, follow)
            .apply()

        _followSystemTheme.value = follow
    }

    /**
     * Alternar tema oscuro
     */
    fun toggleDarkTheme() {
        setDarkTheme(!_isDarkTheme.value)
    }

    /**
     * Obtener el estado actual del tema
     */
    fun getCurrentThemeState(): ThemeState {
        return when {
            _followSystemTheme.value -> ThemeState.SYSTEM
            _isDarkTheme.value -> ThemeState.DARK
            else -> ThemeState.LIGHT
        }
    }

    /**
     * Estados del tema
     */
    enum class ThemeState {
        LIGHT,
        DARK,
        SYSTEM
    }
}