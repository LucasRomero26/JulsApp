package com.tudominio.smslocation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tudominio.smslocation.controller.MainController
import com.tudominio.smslocation.view.ui.screen.MainScreen
import com.tudominio.smslocation.view.ui.theme.SMSLocationAppTheme
import com.tudominio.smslocation.util.ThemeState

/**
 * The main entry point for the Juls Android application.
 * This activity is responsible for setting up the Compose UI, initializing the
 * [MainController], and handling basic activity lifecycle events relevant to the controller.
 *
 * ✨ Updated to support WebRTC video streaming with camera permissions.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val LOCATION_PERMISSION_CODE = 101
        private const val CAMERA_PERMISSION_CODE = 102
    }

    // Declare a lateinit variable for the MainController.
    // It will be initialized in onCreate.
    private lateinit var mainController: MainController

    /**
     * Called when the activity is first created.
     * This is where the main setup for the activity should occur.
     * @param savedInstanceState If the activity is being re-initialized after
     * previously being shut down then this Bundle contains the data it most
     * recently supplied in [onSaveInstanceState].
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the global theme state FIRST. This ensures the theme is loaded
        // from preferences before any UI components that rely on it are composed.
        ThemeState.initialize(this)

        // Initialize the MainController, passing the activity context.
        // The controller will manage the application's business logic and state.
        mainController = MainController(this)

        // ✨ NUEVO: Verificar y solicitar permisos de ubicación
        checkLocationPermissions()

        // ✨ NUEVO: Verificar y solicitar permisos de cámara
        checkCameraPermissions()

        // Enable edge-to-edge display for a more immersive full-screen experience.
        // This allows the app content to extend behind system bars.
        enableEdgeToEdge()

        // Set the Composable content for this activity.
        setContent {
            // Apply the custom theme for the application.
            SMSLocationAppTheme {
                // A Material Design surface that fills the entire screen.
                // It uses the background color from the current theme.
                Surface(
                    modifier = Modifier.fillMaxSize(), // Make the surface fill the available space.
                    color = MaterialTheme.colorScheme.background // Set the background color from the theme.
                ) {
                    // Display the main screen of the application, injecting the MainController.
                    MainScreen(controller = mainController)
                }
            }
        }
    }

    /**
     * ✨ NUEVO: Verificar y solicitar permisos de ubicación
     */
    private fun checkLocationPermissions() {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (fineLocationPermission != PackageManager.PERMISSION_GRANTED ||
            coarseLocationPermission != PackageManager.PERMISSION_GRANTED) {

            Log.d(TAG, "Requesting location permissions...")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_CODE
            )
        } else {
            Log.d(TAG, "Location permissions already granted")
        }
    }

    /**
     * ✨ NUEVO: Verificar y solicitar permisos de cámara y audio
     */
    private fun checkCameraPermissions() {
        val cameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        )

        val audioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        )

        if (cameraPermission != PackageManager.PERMISSION_GRANTED ||
            audioPermission != PackageManager.PERMISSION_GRANTED) {

            Log.d(TAG, "Requesting camera and audio permissions...")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                ),
                CAMERA_PERMISSION_CODE
            )
        } else {
            Log.d(TAG, "Camera permissions already granted")
        }
    }

    /**
     * ✨ NUEVO: Manejar respuesta de solicitud de permisos
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d(TAG, "✅ Location permissions granted")
                } else {
                    Log.w(TAG, "⚠️ Location permissions denied")
                }
            }

            CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d(TAG, "✅ Camera and audio permissions granted")
                } else {
                    Log.w(TAG, "⚠️ Camera or audio permissions denied")
                }
            }
        }
    }

    /**
     * Called when the activity is becoming visible to the user.
     * This is often where you'll resume operations that should be active only when
     * the user is interacting with the app.
     */
    override fun onResume() {
        super.onResume()
        // Notify the MainController that the app has resumed.
        mainController.onAppResumed()
    }

    /**
     * Called when the activity is no longer in the foreground.
     * This is typically where you'll pause operations that shouldn't run in the background.
     */
    override fun onPause() {
        super.onPause()
        // Notify the MainController that the app has paused.
        mainController.onAppPaused()
    }
}