package com.tudominio.smslocation.util

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Simple global state for the application's theme.
 * This object provides a centralized and observable way to manage the application's
 * dark/light theme setting across different UI components, especially useful with Jetpack Compose.
 * It persists the theme preference using [SharedPreferences].
 */
object ThemeState {
    // Name of the SharedPreferences file for theme preferences.
    private const val PREF_NAME = "theme_preferences"
    // Key for storing the dark theme boolean in SharedPreferences.
    private const val KEY_DARK_THEME = "is_dark_theme"

    // Application context, nullable to prevent memory leaks if not properly initialized.
    private var context: Context? = null

    // The observable state for the dark theme.
    // `mutableStateOf` makes this property observable by Compose UI, triggering recomposition
    // of UI elements that read this state when its value changes.
    // The `private set` ensures that `isDarkTheme` can only be modified internally within this object.
    var isDarkTheme by mutableStateOf(false)
        private set

    /**
     * Initializes the [ThemeState] with the application context.
     * This method must be called once, typically in the `Application` class's `onCreate()`,
     * to load the saved theme preference from [SharedPreferences].
     * @param appContext The application context, used to access SharedPreferences.
     */
    fun initialize(appContext: Context) {
        context = appContext // Store the application context.
        // Load the saved theme preference from SharedPreferences.
        // If no value is found for KEY_DARK_THEME, it defaults to `false` (light theme).
        val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        isDarkTheme = prefs.getBoolean(KEY_DARK_THEME, false)
    }

    /**
     * Toggles the current theme state between dark and light.
     * If the theme is currently dark, it becomes light, and vice-versa.
     * After changing the state, it calls [saveTheme] to persist the new preference.
     */
    fun toggleTheme() {
        isDarkTheme = !isDarkTheme // Invert the boolean state.
        saveTheme() // Persist the change.
    }

    /**
     * Saves the current [isDarkTheme] state to [SharedPreferences].
     * This private helper function ensures that changes to the theme are persistently stored
     * so that the preference is maintained across app launches.
     * It safely accesses the context via a null check.
     */
    private fun saveTheme() {
        context?.let {
            val prefs = it.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit() // Get an editor to modify the preferences.
                .putBoolean(KEY_DARK_THEME, isDarkTheme) // Put the current value of `isDarkTheme`.
                .apply() // Apply the changes asynchronously to avoid blocking the main thread.
        }
    }
}