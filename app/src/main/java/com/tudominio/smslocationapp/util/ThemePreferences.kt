package com.tudominio.smslocation.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Simple and direct handling of theme preferences.
 * This class provides a way to store and retrieve the user's preferred theme (dark or light)
 * using [SharedPreferences]. It also exposes the theme state as a Compose `mutableStateOf`
 * property, allowing UI components to react to theme changes automatically.
 */
class ThemePreferences(context: Context) {

    companion object {
        // The name of the SharedPreferences file where theme preferences will be stored.
        private const val PREF_NAME = "juls_theme_preferences"
        // The key used to store the boolean value indicating if dark theme is enabled.
        private const val KEY_IS_DARK_THEME = "is_dark_theme"
    }

    // Lazily initialized SharedPreferences instance for reading and writing preferences.
    private val preferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // Private internal state for the dark theme.
    // `mutableStateOf` makes this property observable by Compose UI, triggering recomposition on change.
    // The initial value is loaded from SharedPreferences, defaulting to `false` (light theme).
    private var _isDarkTheme by mutableStateOf(preferences.getBoolean(KEY_IS_DARK_THEME, false))

    // Public read-only property to access the current dark theme state.
    // This allows external classes to observe the theme without directly modifying the mutable state.
    val isDarkTheme: Boolean
        get() = _isDarkTheme

    /**
     * Toggles the current theme between dark and light.
     * If the theme is currently dark, it becomes light, and vice-versa.
     * The new theme preference is then saved to [SharedPreferences].
     */
    fun toggleTheme() {
        _isDarkTheme = !_isDarkTheme // Invert the current theme state.
        saveToPreferences() // Persist the new state.
    }

    /**
     * Programmatically changes the theme to either dark or light.
     * @param dark A boolean value: `true` to set dark theme, `false` for light theme.
     * The new theme preference is then saved to [SharedPreferences].
     */
    fun changeToDarkTheme(dark: Boolean) {
        _isDarkTheme = dark // Set the theme to the specified value.
        saveToPreferences() // Persist the new state.
    }

    /**
     * Saves the current [_isDarkTheme] state to [SharedPreferences].
     * This private helper function ensures that any changes to the theme are persistently stored.
     */
    private fun saveToPreferences() {
        preferences.edit() // Get an editor for modifying preferences.
            .putBoolean(KEY_IS_DARK_THEME, _isDarkTheme) // Put the current dark theme state.
            .apply() // Apply the changes asynchronously.
    }

    /**
     * Gets the current theme state as a human-readable string for debugging purposes.
     * @return "DARK" if dark theme is enabled, "LIGHT" otherwise.
     */
    fun getCurrentThemeState(): String {
        return if (_isDarkTheme) "DARK" else "LIGHT"
    }
}