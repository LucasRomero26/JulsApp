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
import com.tudominio.smslocation.util.ThemeState

class MainActivity : ComponentActivity() {

    private lateinit var mainController: MainController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar el estado del tema PRIMERO
        ThemeState.initialize(this)

        // Inicializar controlador principal
        mainController = MainController(this)

        enableEdgeToEdge()
        setContent {
            SMSLocationAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(controller = mainController)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainController.onAppResumed()
    }

    override fun onPause() {
        super.onPause()
        mainController.onAppPaused()
    }
}