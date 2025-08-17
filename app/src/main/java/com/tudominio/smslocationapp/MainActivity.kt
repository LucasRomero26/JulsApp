package com.tudominio.smslocation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.tudominio.smslocation.controller.MainController
import com.tudominio.smslocation.view.ui.screen.MainScreen
import com.tudominio.smslocation.view.ui.theme.SMSLocationAppTheme

class MainActivity : ComponentActivity() {

    private lateinit var mainController: MainController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar controlador principal
        mainController = MainController(this)

        enableEdgeToEdge()
        setContent {
            SMSLocationAppTheme(themePreferences = mainController.themePreferences) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Pasar el controller a MainScreen
                    MainScreen(controller = mainController)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Notificar al controller que la app se reanudó
        mainController.onAppResumed()
    }

    override fun onPause() {
        super.onPause()
        // Notificar al controller que la app se pausó
        mainController.onAppPaused()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar recursos del controller
        // No llamamos cleanup aquí porque el ViewModel se encarga automáticamente
        // cuando se destruye
    }
}