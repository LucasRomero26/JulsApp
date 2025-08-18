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
     * Alternar tema oscuro - CORREGIDO
     */
    fun toggleDarkTheme() {
        val currentlyFollowingSystem = _followSystemTheme.value

        if (currentlyFollowingSystem) {
            // Si estamos siguiendo el sistema, cambiar a modo manual con tema opuesto al actual
            val currentEffectiveTheme = _isDarkTheme.value // Este debería reflejar el tema actual del sistema
            setDarkTheme(!currentEffectiveTheme)
        } else {
            // Si ya estamos en modo manual, simplemente alternar
            setDarkTheme(!_isDarkTheme.value)
        }
    }

    /**
     * Alternar entre seguir sistema y modo manual - NUEVO MÉTODO
     */
    fun toggleThemeMode(currentSystemDarkTheme: Boolean) {
        if (_followSystemTheme.value) {
            // Cambiar de seguir sistema a modo manual con el tema opuesto al sistema actual
            setDarkTheme(!currentSystemDarkTheme)
        } else {
            // Cambiar de modo manual a seguir sistema
            setFollowSystemTheme(true)
        }
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
     * Verificar si el tema actual es oscuro (considerando sistema)
     */
    fun isCurrentlyDark(systemDarkTheme: Boolean): Boolean {
        return if (_followSystemTheme.value) {
            systemDarkTheme
        } else {
            _isDarkTheme.value
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