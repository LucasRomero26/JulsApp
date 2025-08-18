package com.tudominio.smslocation.view.ui.screen

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tudominio.smslocation.R
import com.tudominio.smslocation.controller.MainController
import com.tudominio.smslocation.model.data.AppState
import com.tudominio.smslocation.model.data.LocationData
import com.tudominio.smslocation.view.ui.theme.*
import com.tudominio.smslocation.util.ThemeState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    controller: MainController = viewModel()
) {
    val context = LocalContext.current

    // Observar el estado de la aplicación desde el controller
    val appState by controller.appState.collectAsState()

    // Permisos de ubicación básicos
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Permiso de ubicación en segundo plano (separado para Android 10+)
    val backgroundLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        null
    }

    // Verificar permisos al inicio
    LaunchedEffect(Unit) {
        controller.checkPermissions()
    }

    // Observar cambios en permisos básicos
    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            controller.onPermissionsGranted()
        }
    }

    // Observar cambios en permiso de segundo plano
    LaunchedEffect(backgroundLocationPermission?.status) {
        backgroundLocationPermission?.let {
            if (it.status == PermissionStatus.Granted) {
                controller.onPermissionsGranted()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Background decorations como primer elemento
        Box(modifier = Modifier.fillMaxSize()) {
            BackgroundDecorations()

            Column {
                // Header superior con Welcome y toggle de tema
                TopHeader(controller = controller)

                // Contenido principal con scroll
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ModernHeaderSection(controller = controller)
                    Spacer(modifier = Modifier.height(32.dp))
                    MainControlCard(
                        appState = appState,
                        controller = controller,
                        locationPermissions = locationPermissions,
                        backgroundLocationPermission = backgroundLocationPermission
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    if (appState.isTrackingEnabled) {
                        LocationStatusCard(appState = appState)
                        Spacer(modifier = Modifier.height(16.dp))
                        ServerStatusCard(appState = appState)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun BackgroundDecorations() {
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = 150.dp, y = (-50).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(150.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-30).dp, y = 50.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun TopHeader(
    controller: MainController
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 80.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = 16.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Texto "Welcome"
        Text(
            text = "Welcome",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 30.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        // Botón de tema premium con efectos
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(
                    elevation = if (ThemeState.isDarkTheme) 8.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = if (ThemeState.isDarkTheme) {
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        } else {
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    ),
                    shape = CircleShape
                )
                .clickable {
                    ThemeState.toggleTheme()
                },
            contentAlignment = Alignment.Center
        ) {
            // Círculo interno con gradiente
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        brush = if (ThemeState.isDarkTheme) {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF6C7B95), // Azul grisáceo oscuro
                                    Color(0xFF4A5568)  // Gris azulado más oscuro
                                )
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFFFFF), // Amarillo muy claro
                                    Color(0xFFFFFFFF)  // Amarillo pálido
                                )
                            )
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Ícono con transición suave y colores temáticos
                AnimatedContent(
                    targetState = ThemeState.isDarkTheme,
                    transitionSpec = {
                        (scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ) + fadeIn(animationSpec = tween(400))) togetherWith
                                (scaleOut(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)))
                    },
                    label = "theme_icon_transition"
                ) { isDark ->
                    Icon(
                        imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode",
                        tint = if (isDark) {
                            Color(0xFFF7FAFC) // Blanco con tinte azul claro
                        } else {
                            Color(0xFF4A5568) // Amarillo dorado para el sol
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
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
        // Logo según el tema usando ThemeState directamente
        Image(
            painter = painterResource(
                id = if (ThemeState.isDarkTheme) R.drawable.logo_dark else R.drawable.logo_light
            ),
            contentDescription = "Juls Logo",
            modifier = Modifier.size(200.dp)
        )

        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = "Juls",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp
            ),
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = "Just Urgent Location Services",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun MainControlCard(
    appState: AppState,
    controller: MainController,
    locationPermissions: com.google.accompanist.permissions.MultiplePermissionsState,
    backgroundLocationPermission: com.google.accompanist.permissions.PermissionState?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(28.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ModernPermissionsStatus(
                hasLocationPermission = appState.hasLocationPermission,
                hasBackgroundLocationPermission = appState.hasBackgroundLocationPermission,
                locationPermissions = locationPermissions,
                backgroundLocationPermission = backgroundLocationPermission
            )
            Spacer(modifier = Modifier.height(32.dp))
            MainTrackingButton(
                isTracking = appState.isTrackingEnabled,
                canStart = controller.canStartTracking(),
                onToggleTracking = { controller.toggleTracking() }
            )
            Spacer(modifier = Modifier.height(24.dp))
            StatusMessages(appState = appState, controller = controller)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun ModernPermissionsStatus(
    hasLocationPermission: Boolean,
    hasBackgroundLocationPermission: Boolean,
    locationPermissions: com.google.accompanist.permissions.MultiplePermissionsState,
    backgroundLocationPermission: com.google.accompanist.permissions.PermissionState?
) {
    val context = LocalContext.current
    val allPermissionsGranted = hasLocationPermission && hasBackgroundLocationPermission

    val successColor = LightBlueGray
    val containerColor = if (allPermissionsGranted)
        successColor.copy(alpha = 0.1f)
    else
        MaterialTheme.colorScheme.errorContainer
    val contentColor = if (allPermissionsGranted)
        successColor
    else
        MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = contentColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (allPermissionsGranted) Icons.Default.Shield else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (allPermissionsGranted) Lightest else MaterialTheme.colorScheme.onError
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (allPermissionsGranted) "Ready to Track" else "Permissions Required",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = contentColor
                    )
                    Text(
                        text = when {
                            allPermissionsGranted -> "All location permissions granted"
                            !hasLocationPermission -> "Basic location access needed"
                            !hasBackgroundLocationPermission -> "Background location access needed"
                            else -> "Location permissions required"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }

            if (!allPermissionsGranted) {
                Spacer(modifier = Modifier.height(16.dp))

                if (!hasLocationPermission) {
                    Button(
                        onClick = {
                            locationPermissions.launchMultiplePermissionRequest()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grant Location Access", fontWeight = FontWeight.Medium)
                    }
                }

                if (hasLocationPermission && !hasBackgroundLocationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (!hasLocationPermission) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Button(
                        onClick = {
                            if (backgroundLocationPermission?.status is PermissionStatus.Denied &&
                                (backgroundLocationPermission.status as PermissionStatus.Denied).shouldShowRationale) {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            } else {
                                backgroundLocationPermission?.launchPermissionRequest()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LightBlueGray,
                            contentColor = Lightest
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (backgroundLocationPermission?.status is PermissionStatus.Denied &&
                                (backgroundLocationPermission.status as PermissionStatus.Denied).shouldShowRationale) {
                                "Open Settings"
                            } else {
                                "Grant Background Access"
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (hasLocationPermission && !hasBackgroundLocationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Background Location Required",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "For continuous GPS tracking, please select \"Allow all the time\" in the next screen.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
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
    val buttonColor = if (isTracking) MaterialTheme.colorScheme.error else LightBlueGray
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
            contentColor = Lightest,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun StatusMessages(
    appState: AppState,
    controller: MainController
) {
    val successColor = LightBlueGray

    AnimatedVisibility(
        visible = appState.statusMessage.isNotEmpty(),
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = successColor.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = successColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = appState.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = successColor.copy(alpha = 0.9f)
                )
            }
        }
    }

    AnimatedVisibility(
        visible = appState.errorMessage.isNotEmpty(),
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = appState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
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
                val successColor = LightBlueGray

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = successColor.copy(alpha = alpha),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Lightest
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "GPS Tracking Active",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Sending location every 2 seconds",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            appState.currentLocation?.let { location ->
                LocationDisplaySection(location)
            }
        }
    }
}

@Composable
private fun LocationDisplaySection(location: LocationData) {
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Latitude",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = String.format("%.6f", location.latitude),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column {
            Text(
                text = "Longitude",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = String.format("%.6f", location.longitude),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Last Update: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(location.timestamp))}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    )
}

@Composable
private fun ServerStatusCard(
    appState: AppState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Server Status",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "TCP & UDP transmission status",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
    val successColor = LightBlueGray
    val errorColor = MaterialTheme.colorScheme.error

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isConnected) successColor else errorColor,
                    shape = CircleShape
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isConnected) successColor else errorColor
        )
    }
}