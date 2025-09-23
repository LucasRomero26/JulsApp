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

/**
 * Main Screen optimizada para 4 servidores UDP con velocidad mejorada
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    controller: MainController = viewModel()
) {
    val context = LocalContext.current
    val appState by controller.appState.collectAsState()

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val backgroundLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        null
    }

    LaunchedEffect(Unit) {
        controller.checkPermissions()
    }

    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            controller.onPermissionsGranted()
        }
    }

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
        Box(modifier = Modifier.fillMaxSize()) {
            BackgroundDecorations()

            Column {
                TopHeader(controller = controller)

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
                        Spacer(modifier = Modifier.height(16.dp))
                        RedundancyStatusCard(appState = appState)
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
        Text(
            text = "Welcome",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 30.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

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
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        brush = if (ThemeState.isDarkTheme) {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF6C7B95),
                                    Color(0xFF4A5568)
                                )
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFFFFF),
                                    Color(0xFFFFFFFF)
                                )
                            )
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
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
                            Color(0xFFF7FAFC)
                        } else {
                            Color(0xFF4A5568)
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
            text = "Just UDP Location Service",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
        // CAMBIO: Actualizado para 4 servidores
        Text(
            text = "Ultra-Fast UDP Protocol - 4 Servers",
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            ),
            color = LightBlueGray
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
                    // CAMBIO: Texto actualizado para 4 servidores
                    Text(
                        text = if (allPermissionsGranted) "Ready for 4-Server UDP Tracking" else "Permissions Required",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = contentColor
                    )
                    Text(
                        text = when {
                            allPermissionsGranted -> "All permissions granted - 4 UDP servers ready"
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
                                    // CAMBIO: Texto actualizado para 4 servidores
                                    Text(
                                        text = "For continuous UDP tracking to 4 servers, please select \"Allow all the time\" in the next screen.",
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
    // CAMBIO: Texto del botón actualizado para 4 servidores
    val buttonText = if (isTracking) "Stop UDP Tracking" else "Start UDP Tracking"
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
                val infiniteTransition = rememberInfiniteTransition(label = "tracking_animation")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha_animation"
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
                    // CAMBIO: Texto actualizado para 4 servidores
                    Text(
                        text = "4-Server UDP GPS Tracking Active",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Sending location every 2 seconds via UDP to 4 servers",
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
                    // CAMBIO: Título actualizado para 4 servidores
                    Text(
                        text = "4-Server UDP Status",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Fast UDP transmission to 4 servers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            // CAMBIO: Agregados Server 3 y Server 4
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ServerConnectionRow(
                    serverName = "Server 1",
                    udpStatus = appState.serverStatus.server1UDP
                )
                ServerConnectionRow(
                    serverName = "Server 2",
                    udpStatus = appState.serverStatus.server2UDP
                )
                ServerConnectionRow(
                    serverName = "Server 3",
                    udpStatus = appState.serverStatus.server3UDP
                )
                ServerConnectionRow(
                    serverName = "Server 4",
                    udpStatus = appState.serverStatus.server4UDP
                )
            }
        }
    }
}

// NUEVO: Tarjeta de estado de redundancia
@Composable
private fun RedundancyStatusCard(
    appState: AppState
) {
    val activeConnections = appState.serverStatus.getActiveConnectionsCount()
    val connectivityPercentage = appState.serverStatus.getConnectivityPercentage()

    val statusColor = when {
        activeConnections == 4 -> LightBlueGray
        activeConnections >= 2 -> MaterialTheme.colorScheme.primary
        activeConnections == 1 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    val statusText = when {
        activeConnections == 4 -> "Optimal Redundancy"
        activeConnections >= 2 -> "Good Redundancy"
        activeConnections == 1 -> "Limited Redundancy"
        else -> "No Redundancy"
    }

    val statusIcon = when {
        activeConnections == 4 -> Icons.Default.SecurityUpdateGood
        activeConnections >= 2 -> Icons.Default.Security
        activeConnections == 1 -> Icons.Default.SecurityUpdateWarning
        else -> Icons.Default.Error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = statusColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Lightest
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = statusColor
                    )
                    Text(
                        text = "$activeConnections of 4 servers connected (${String.format("%.0f", connectivityPercentage)}%)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor.copy(alpha = 0.8f)
                    )
                }

                // Indicador visual de conectividad
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$activeConnections/4",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = statusColor
                    )
                }
            }

            if (activeConnections < 4) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = statusColor.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (activeConnections < 2) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (activeConnections < 2) MaterialTheme.colorScheme.error else statusColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            activeConnections < 2 -> "Critical: Risk of data loss if remaining server fails"
                            activeConnections == 2 -> "Acceptable: Minimum redundancy maintained"
                            activeConnections == 3 -> "Good: One server offline, redundancy maintained"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (activeConnections < 2) MaterialTheme.colorScheme.error else statusColor.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerConnectionRow(
    serverName: String,
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
        // Solo mostrar UDP
        ConnectionIndicator(
            label = "UDP",
            isConnected = udpStatus == com.tudominio.smslocation.model.data.ServerStatus.ConnectionStatus.CONNECTED,
            status = udpStatus
        )
    }
}

@Composable
private fun ConnectionIndicator(
    label: String,
    isConnected: Boolean,
    status: com.tudominio.smslocation.model.data.ServerStatus.ConnectionStatus
) {
    val successColor = LightBlueGray
    val errorColor = MaterialTheme.colorScheme.error
    val warningColor = MaterialTheme.colorScheme.tertiary

    val (indicatorColor, statusText) = when (status) {
        com.tudominio.smslocation.model.data.ServerStatus.ConnectionStatus.CONNECTED ->
            successColor to "Connected"
        com.tudominio.smslocation.model.data.ServerStatus.ConnectionStatus.CONNECTING ->
            warningColor to "Connecting"
        com.tudominio.smslocation.model.data.ServerStatus.ConnectionStatus.TIMEOUT ->
            warningColor to "Timeout"
        com.tudominio.smslocation.model.data.ServerStatus.ConnectionStatus.ERROR ->
            errorColor to "Error"
        else -> errorColor to "Disconnected"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = indicatorColor,
                    shape = CircleShape
                )
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = indicatorColor
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = indicatorColor.copy(alpha = 0.7f)
            )
        }
    }
}