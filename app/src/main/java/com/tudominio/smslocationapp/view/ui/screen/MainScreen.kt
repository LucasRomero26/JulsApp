package com.tudominio.smslocation.view.ui.screen

import android.Manifest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tudominio.smslocation.R
import com.tudominio.smslocation.controller.MainController
import com.tudominio.smslocation.model.data.AppState
import com.tudominio.smslocation.model.data.LocationData
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.text.SimpleDateFormat
import java.util.*

// Custom color palette for Juls
private val PrimaryBlue = Color(0xFF2196F3)
private val SecondaryBlue = Color(0xFF1976D2)
private val DarkBlue = Color(0xFF0D47A1)
private val MediumBlue = Color(0xFF1565C0)
private val LightBlue = Color(0xFF42A5F5)
private val AccentGreen = Color(0xFF4CAF50)
private val AccentRed = Color(0xFFF44336)
private val BackgroundLight = Color(0xFFF8F9FA)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    controller: MainController = viewModel()
) {
    val context = LocalContext.current

    // Observar el estado de la aplicación desde el controller
    val appState by controller.appState.collectAsState()

    // Permission management - incluye permisos de ubicación en segundo plano
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    val permissionsState = rememberMultiplePermissionsState(permissions = permissions)

    // Initialize controller
    LaunchedEffect(Unit) {
        controller.checkPermissions()
    }

    // Handle permission changes
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            controller.onPermissionsGranted()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        // Background gradient circles
        BackgroundDecorations()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            ModernHeaderSection(controller = controller)

            Spacer(modifier = Modifier.height(32.dp))

            // Main control card
            MainControlCard(
                appState = appState,
                controller = controller,
                permissionsState = permissionsState
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Status cards
            if (appState.isTrackingEnabled) {
                LocationStatusCard(appState = appState)
                Spacer(modifier = Modifier.height(16.dp))
                ServerStatusCard(appState = appState)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BackgroundDecorations() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top right gradient circle
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = 150.dp, y = (-50).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PrimaryBlue.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Bottom left gradient circle
        Box(
            modifier = Modifier
                .size(150.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-30).dp, y = 50.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            LightBlue.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun ModernHeaderSection(
    controller: MainController
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Theme toggle button in top right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            ThemeToggleButton(controller = controller)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Image(
            painter = painterResource(id = R.drawable.location_icon),
            contentDescription = null,
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Juls",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp
            ),
            color = DarkBlue
        )

        Text(
            text = "Just Urgent Location Services",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            ),
            color = MediumBlue.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Automatic GPS tracking to TCP & UDP servers",
            style = MaterialTheme.typography.bodyLarge,
            color = DarkBlue.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ThemeToggleButton(
    controller: MainController
) {
    val followSystemTheme by controller.themePreferences.followSystemTheme.collectAsState()
    val isDarkTheme by controller.themePreferences.isDarkTheme.collectAsState()
    val systemDarkTheme = isSystemInDarkTheme()

    // Determinar el tema actual
    val currentlyDark = if (followSystemTheme) systemDarkTheme else isDarkTheme

    IconButton(
        onClick = { controller.toggleTheme() },
        modifier = Modifier
            .size(48.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = if (currentlyDark) Icons.Default.LightMode else Icons.Default.DarkMode,
            contentDescription = if (currentlyDark) "Switch to Light Mode" else "Switch to Dark Mode",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun MainControlCard(
    appState: AppState,
    controller: MainController,
    permissionsState: MultiplePermissionsState
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(28.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Permissions status
            ModernPermissionsStatus(
                hasLocationPermission = appState.hasLocationPermission,
                hasBackgroundLocationPermission = appState.hasBackgroundLocationPermission,
                onRequestPermissions = {
                    permissionsState.launchMultiplePermissionRequest()
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Main control button
            MainTrackingButton(
                isTracking = appState.isTrackingEnabled,
                canStart = controller.canStartTracking(),
                onToggleTracking = { controller.toggleTracking() }
            )

            // Status messages
            Spacer(modifier = Modifier.height(24.dp))
            StatusMessages(appState = appState, controller = controller)
        }
    }
}

@Composable
private fun ModernPermissionsStatus(
    hasLocationPermission: Boolean,
    hasBackgroundLocationPermission: Boolean,
    onRequestPermissions: () -> Unit
) {
    val allPermissionsGranted = hasLocationPermission && hasBackgroundLocationPermission

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (allPermissionsGranted)
                AccentGreen.copy(alpha = 0.1f)
            else
                AccentRed.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (allPermissionsGranted) AccentGreen else AccentRed,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (allPermissionsGranted) Icons.Default.Shield else Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (allPermissionsGranted) "Ready to Track" else "Permissions Required",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (allPermissionsGranted) AccentGreen else AccentRed
                )

                Text(
                    text = when {
                        allPermissionsGranted -> "All location permissions granted"
                        !hasLocationPermission -> "Basic location access needed"
                        !hasBackgroundLocationPermission -> "Background location access needed"
                        else -> "Location permissions required"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (allPermissionsGranted)
                        MediumBlue.copy(alpha = 0.8f)
                    else
                        AccentRed.copy(alpha = 0.8f)
                )
            }

            if (!allPermissionsGranted) {
                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("Grant", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun MainTrackingButton(
    isTracking: Boolean,
    canStart: Boolean,
    onToggleTracking: () -> Unit
) {
    val buttonColor = if (isTracking) AccentRed else AccentGreen
    val buttonText = if (isTracking) "Stop Tracking" else "Start Tracking"
    val buttonIcon = if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow

    Button(
        onClick = onToggleTracking,
        enabled = canStart || isTracking,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            contentColor = Color.White,
            disabledContainerColor = MediumBlue.copy(alpha = 0.3f),
            disabledContentColor = Color.White.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(
            imageVector = buttonIcon,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = buttonText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun StatusMessages(
    appState: AppState,
    controller: MainController
) {
    // Success message
    AnimatedVisibility(
        visible = appState.statusMessage.isNotEmpty(),
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = AccentGreen.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = appState.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccentGreen.copy(alpha = 0.9f)
                )
            }
        }
    }

    // Error message
    AnimatedVisibility(
        visible = appState.errorMessage.isNotEmpty(),
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = AccentRed.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = AccentRed,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = appState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccentRed.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun LocationStatusCard(
    appState: AppState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = PrimaryBlue.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Animated tracking indicator
                val infiniteTransition = rememberInfiniteTransition(label = "tracking")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = AccentGreen.copy(alpha = alpha),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "GPS Tracking Active",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = DarkBlue
                    )
                    Text(
                        text = "Sending location every 2 seconds",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MediumBlue.copy(alpha = 0.8f)
                    )
                }
            }

            // Current location display
            appState.currentLocation?.let { location ->
                LocationDisplaySection(location)
            }
        }
    }
}

@Composable
private fun LocationDisplaySection(location: LocationData) {
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MediumBlue.copy(alpha = 0.3f))
    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Latitude",
                style = MaterialTheme.typography.bodySmall,
                color = MediumBlue.copy(alpha = 0.7f)
            )
            Text(
                text = String.format("%.6f", location.latitude),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = DarkBlue
            )
        }
        Column {
            Text(
                text = "Longitude",
                style = MaterialTheme.typography.bodySmall,
                color = MediumBlue.copy(alpha = 0.7f)
            )
            Text(
                text = String.format("%.6f", location.longitude),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = DarkBlue
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Last Update: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(location.timestamp))}",
        style = MaterialTheme.typography.bodySmall,
        color = MediumBlue.copy(alpha = 0.7f)
    )
}

@Composable
private fun ServerStatusCard(
    appState: AppState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = SecondaryBlue.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = SecondaryBlue,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Server Status",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = DarkBlue
                    )
                    Text(
                        text = "TCP & UDP transmission status",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MediumBlue.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Server connection indicators
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ServerConnectionRow(
                    serverName = "Server 1",
                    tcpStatus = appState.serverStatus.server1TCP,
                    udpStatus = appState.serverStatus.server1UDP
                )

                ServerConnectionRow(
                    serverName = "Server 2",
                    tcpStatus = appState.serverStatus.server2TCP,
                    udpStatus = appState.serverStatus.server2UDP
                )
            }
        }
    }
}

@Composable
private fun ServerConnectionRow(
    serverName: String,
    tcpStatus: com.tudominio.smslocation.model.data.ServerStatus.ConnectionStatus,
    udpStatus: com.tudominio.smslocation.model.data.ServerStatus.ConnectionStatus
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = serverName,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = DarkBlue,
            modifier = Modifier.weight(1f)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ConnectionIndicator(
                label = "TCP",
                isConnected = tcpStatus == com.tudominio.smslocation.model.data.ServerStatus.ConnectionStatus.CONNECTED
            )
            ConnectionIndicator(
                label = "UDP",
                isConnected = udpStatus == com.tudominio.smslocation.model.data.ServerStatus.ConnectionStatus.CONNECTED
            )
        }
    }
}

@Composable
private fun ConnectionIndicator(
    label: String,
    isConnected: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isConnected) AccentGreen else AccentRed,
                    shape = CircleShape
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isConnected) AccentGreen else AccentRed
        )
    }
}