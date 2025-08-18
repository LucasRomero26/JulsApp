package com.tudominio.smslocation.controller

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tudominio.smslocation.model.data.AppState
import com.tudominio.smslocation.service.LocationService
import com.tudominio.smslocation.util.Constants
import com.tudominio.smslocation.util.ThemePreferences
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Controlador principal de la aplicación que coordina todos los componentes
 * Actúa como el punto central de control para la UI y la lógica de negocio
 */
class MainController(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = Constants.Logs.TAG_MAIN
    }

    private val locationController = LocationController(context)

    // Preferencias de tema
    val themePreferences = ThemePreferences(context)

    // Exponer el estado de la aplicación
    val appState: StateFlow<AppState> = locationController.appState

    init {
        Log.d(TAG, "MainController initialized")

        // Verificar permisos inicialmente
        checkPermissions()
    }

    /**
     * Verificar y actualizar permisos
     */
    fun checkPermissions() {
        locationController.checkPermissions()
        Log.d(TAG, "Permissions checked")
    }

    /**
     * Manejar solicitud de permisos concedidos
     */
    fun onPermissionsGranted() {
        checkPermissions()

        val currentState = appState.value
        if (currentState.hasAllPermissions()) {
            Log.d(TAG, "All permissions granted")

            // Mostrar mensaje de éxito
            viewModelScope.launch {
                locationController.getCurrentAppState().let { state ->
                    if (state.hasAllPermissions()) {
                        // Opcional: obtener ubicación actual para verificar GPS
                        locationController.getCurrentLocation()
                    }
                }
            }
        }
    }

    /**
     * Alternar estado de tracking (iniciar/detener)
     */
    fun toggleTracking() {
        viewModelScope.launch {
            val currentState = appState.value

            if (currentState.isTrackingEnabled) {
                stopTracking()
            } else {
                startTracking()
            }
        }
    }

    /**
     * Alternar tema oscuro/claro - CORREGIDO
     */
    fun toggleTheme() {
        // Obtener el estado actual del sistema para tomar la decisión correcta
        val currentSystemDarkTheme = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        themePreferences.toggleThemeMode(currentSystemDarkTheme)

        val newState = themePreferences.getCurrentThemeState()
        Log.d(TAG, "Theme toggled to: $newState")
    }

    /**
     * Establecer tema específico
     */
    fun setTheme(isDark: Boolean) {
        themePreferences.setDarkTheme(isDark)
        Log.d(TAG, "Theme set to: ${if (isDark) "Dark" else "Light"}")
    }

    /**
     * Establecer seguimiento del tema del sistema
     */
    fun setFollowSystemTheme(follow: Boolean) {
        themePreferences.setFollowSystemTheme(follow)
        Log.d(TAG, "Follow system theme: $follow")
    }

    /**
     * Iniciar tracking de ubicación
     */
    private suspend fun startTracking() {
        Log.d(TAG, "Starting tracking from MainController")

        // Verificar permisos antes de iniciar
        if (!appState.value.hasAllPermissions()) {
            Log.w(TAG, "Cannot start tracking - missing permissions")
            return
        }

        try {
            // Iniciar servicio en primer plano
            startLocationService()

            // Iniciar tracking en el controlador
            val result = locationController.startLocationTracking()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Tracking started successfully: $message")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to start tracking: ${error.message}")
                    stopLocationService() // Limpiar servicio si falló el tracking
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error starting tracking", e)
        }
    }

    /**
     * Detener tracking de ubicación
     */
    private fun stopTracking() {
        Log.d(TAG, "Stopping tracking from MainController")

        try {
            // Detener tracking en el controlador
            val result = locationController.stopLocationTracking()

            // Detener servicio
            stopLocationService()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Tracking stopped successfully: $message")
                },
                onFailure = { error ->
                    Log.e(TAG, "Error stopping tracking: ${error.message}")
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tracking", e)
        }
    }

    /**
     * Iniciar servicio de ubicación en primer plano
     */
    private fun startLocationService() {
        val intent = Intent(context, LocationService::class.java).apply {
            action = Constants.ServiceActions.START_TRACKING
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "Location service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location service", e)
        }
    }

    /**
     * Detener servicio de ubicación
     */
    private fun stopLocationService() {
        val intent = Intent(context, LocationService::class.java).apply {
            action = Constants.ServiceActions.STOP_TRACKING
        }

        try {
            context.startService(intent)
            Log.d(TAG, "Location service stop requested")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location service", e)
        }
    }

    /**
     * Obtener ubicación actual sin iniciar tracking
     */
    fun getCurrentLocation() {
        viewModelScope.launch {
            val result = locationController.getCurrentLocation()

            result.fold(
                onSuccess = { location ->
                    Log.d(TAG, "Current location obtained: ${location.getFormattedCoordinates()}")
                },
                onFailure = { error ->
                    Log.w(TAG, "Failed to get current location: ${error.message}")
                }
            )
        }
    }

    /**
     * Testear conexiones a servidores
     */
    fun testServerConnections() {
        viewModelScope.launch {
            val result = locationController.testServerConnections()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Server test successful: $message")
                },
                onFailure = { error ->
                    Log.w(TAG, "Server test failed: ${error.message}")
                }
            )
        }
    }

    /**
     * Enviar datos de prueba
     */
    fun sendTestData() {
        viewModelScope.launch {
            val result = locationController.sendTestData()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Test data sent successfully: $message")
                },
                onFailure = { error ->
                    Log.w(TAG, "Failed to send test data: ${error.message}")
                }
            )
        }
    }

    /**
     * Limpiar mensajes de estado
     */
    fun clearMessages() {
        locationController.clearMessages()
    }

    /**
     * Resetear estadísticas
     */
    fun resetStatistics() {
        locationController.resetStatistics()
    }

    /**
     * Obtener información de diagnóstico
     */
    fun getDiagnosticInfo(): Map<String, String> {
        return locationController.getDiagnosticInfo() + mapOf(
            "current_theme" to themePreferences.getCurrentThemeState().name,
            "follow_system_theme" to themePreferences.followSystemTheme.value.toString()
        )
    }

    /**
     * Verificar si se puede iniciar tracking
     */
    fun canStartTracking(): Boolean {
        return locationController.canStartTracking()
    }

    /**
     * Obtener información de red
     */
    fun getNetworkInfo(): String {
        return locationController.getNetworkInfo()
    }

    /**
     * Procesar ubicaciones pendientes
     */
    fun processPendingLocations() {
        viewModelScope.launch {
            val result = locationController.flushPendingLocations()

            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Pending locations processed: $message")
                },
                onFailure = { error ->
                    Log.w(TAG, "Failed to process pending locations: ${error.message}")
                }
            )
        }
    }

    /**
     * Verificar estado de la aplicación
     */
    fun getAppStatus(): String {
        val state = appState.value
        return state.getStatusSummary()
    }

    /**
     * Manejar eventos del ciclo de vida de la aplicación
     */
    fun onAppPaused() {
        Log.d(TAG, "App paused")
        // La aplicación puede seguir funcionando en segundo plano
        // No detenemos el tracking automáticamente
    }

    fun onAppResumed() {
        Log.d(TAG, "App resumed")

        // Verificar permisos al volver a la app
        checkPermissions()

        // Verificar estado de red
        viewModelScope.launch {
            if (appState.value.isTrackingEnabled) {
                locationController.testServerConnections()
            }
        }
    }

    /**
     * Manejar cambios en la conectividad de red
     */
    fun onNetworkChanged(isConnected: Boolean) {
        Log.d(TAG, "Network changed - Connected: $isConnected")

        if (isConnected && appState.value.isTrackingEnabled) {
            // Testear conexiones cuando se restaure la red
            testServerConnections()

            // Procesar ubicaciones pendientes
            processPendingLocations()
        }
    }

    /**
     * Manejar emergencias (envío prioritario)
     */
    fun handleEmergency() {
        viewModelScope.launch {
            Log.w(TAG, "Emergency mode activated")

            // Obtener ubicación actual inmediatamente
            val locationResult = locationController.getCurrentLocation()

            locationResult.fold(
                onSuccess = { location ->
                    // Enviar múltiples veces para asegurar recepción
                    repeat(3) {
                        locationController.sendTestData()
                    }
                    Log.w(TAG, "Emergency location sent multiple times")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to get emergency location: ${error.message}")
                }
            )
        }
    }

    /**
     * Obtener configuración actual
     */
    fun getCurrentConfiguration(): Map<String, String> {
        return mapOf(
            "server_1_ip" to Constants.SERVER_IP_1,
            "server_2_ip" to Constants.SERVER_IP_2,
            "tcp_port" to Constants.TCP_PORT.toString(),
            "udp_port" to Constants.UDP_PORT.toString(),
            "update_interval" to "${Constants.LOCATION_UPDATE_INTERVAL}ms",
            "network_timeout" to "${Constants.NETWORK_TIMEOUT}ms",
            "max_retries" to Constants.MAX_RETRY_ATTEMPTS.toString(),
            "theme_mode" to themePreferences.getCurrentThemeState().name
        )
    }

    /**
     * Limpiar recursos cuando se destruye el ViewModel
     */
    override fun onCleared() {
        super.onCleared()

        Log.d(TAG, "MainController being cleared")

        // Detener tracking si está activo
        if (appState.value.isTrackingEnabled) {
            stopTracking()
        }

        // Limpiar controlador de ubicación
        locationController.cleanup()

        Log.d(TAG, "MainController cleared successfully")
    }
}