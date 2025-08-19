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
 * Main Composable function for the application's primary screen.
 * This screen displays the app's status, controls for location tracking,
 * and relevant information such as location and server status.
 * It uses a [MainController] for business logic and state management.
 */
@OptIn(ExperimentalPermissionsApi::class) // Opt-in for Accompanist Permissions API.
@Composable
fun MainScreen(
    controller: MainController = viewModel() // Inject MainController using ViewModel Hilt.
) {
    val context = LocalContext.current // Get the current Android context for various operations.

    // Observe the application state from the controller as a Compose State.
    // This recomposes the UI whenever the appState changes.
    val appState by controller.appState.collectAsState()

    // Manage basic location permissions (ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
    // using Accompanist's rememberMultiplePermissionsState.
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Manage background location permission (ACCESS_BACKGROUND_LOCATION) separately.
    // This permission is only relevant for Android 10 (API level 29) and above.
    val backgroundLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        null // Not needed on older Android versions.
    }

    // Effect to check permissions when the composable is first launched.
    LaunchedEffect(Unit) {
        controller.checkPermissions() // Trigger permission check in the controller.
    }

    // Effect to react to changes in basic location permissions.
    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            // If basic permissions are granted, notify the controller.
            controller.onPermissionsGranted()
        }
    }

    // Effect to react to changes in background location permission status.
    LaunchedEffect(backgroundLocationPermission?.status) {
        backgroundLocationPermission?.let {
            if (it.status == PermissionStatus.Granted) {
                // If background permission is granted, notify the controller.
                controller.onPermissionsGranted()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize() // Occupy the entire screen.
            .background(MaterialTheme.colorScheme.background) // Set background color from theme.
    ) {
        // Background decorations as the first element to appear behind other content.
        Box(modifier = Modifier.fillMaxSize()) {
            BackgroundDecorations() // Composable for visual background elements.

            // Main content column laid out over the background decorations.
            Column {
                // Top header section including "Welcome" text and theme toggle button.
                TopHeader(controller = controller)

                // Main scrollable content area.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()) // Enable vertical scrolling.
                        .padding(horizontal = 20.dp, vertical = 16.dp), // Apply padding.
                    horizontalAlignment = Alignment.CenterHorizontally // Center content horizontally.
                ) {
                    ModernHeaderSection(controller = controller) // Displays app logo and name.
                    Spacer(modifier = Modifier.height(32.dp)) // Vertical spacing.
                    MainControlCard( // Card for controlling tracking and displaying permission status.
                        appState = appState,
                        controller = controller,
                        locationPermissions = locationPermissions,
                        backgroundLocationPermission = backgroundLocationPermission
                    )
                    Spacer(modifier = Modifier.height(24.dp)) // Vertical spacing.
                    // Conditionally display location and server status cards only if tracking is enabled.
                    if (appState.isTrackingEnabled) {
                        LocationStatusCard(appState = appState) // Card for displaying current location status.
                        Spacer(modifier = Modifier.height(16.dp)) // Vertical spacing.
                        ServerStatusCard(appState = appState) // Card for displaying server connection status.
                    }
                    Spacer(modifier = Modifier.height(24.dp)) // Vertical spacing at the bottom.
                }
            }
        }
    }
}

/**
 * Composable for drawing decorative circles in the background using gradient brushes.
 * These add a subtle visual flair to the screen.
 */
@Composable
private fun BackgroundDecorations() {
    val primaryColor = MaterialTheme.colorScheme.primary // Get primary color from current theme.

    Box(modifier = Modifier.fillMaxSize()) {
        // Large circle at the top-right.
        Box(
            modifier = Modifier
                .size(200.dp) // Size of the circle.
                .offset(x = 150.dp, y = (-50).dp) // Offset from the top-left corner.
                .background(
                    brush = Brush.radialGradient( // Radial gradient for a soft, fading effect.
                        colors = listOf(
                            primaryColor.copy(alpha = 0.08f), // Inner color, semi-transparent.
                            Color.Transparent // Outer color, fully transparent.
                        )
                    ),
                    shape = CircleShape // Shape of the background element.
                )
        )
        // Smaller circle at the bottom-left.
        Box(
            modifier = Modifier
                .size(150.dp) // Size of the circle.
                .align(Alignment.BottomStart) // Align to the bottom-left.
                .offset(x = (-30).dp, y = 50.dp) // Offset to push it slightly out of bounds.
                .background(
                    brush = Brush.radialGradient( // Radial gradient for a soft, fading effect.
                        colors = listOf(
                            primaryColor.copy(alpha = 0.06f), // Inner color, semi-transparent.
                            Color.Transparent // Outer color, fully transparent.
                        )
                    ),
                    shape = CircleShape // Shape of the background element.
                )
        )
    }
}

/**
 * Composable for the top header section of the screen.
 * Displays a "Welcome" text and a dynamic theme toggle button.
 * The theme toggle button features animations and dynamic colors based on the current theme.
 * @param controller The [MainController] to interact with for theme toggling.
 */
@Composable
private fun TopHeader(
    controller: MainController
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 80.dp, // Large top padding for visual separation.
                start = 20.dp,
                end = 20.dp,
                bottom = 16.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween, // Space elements evenly horizontally.
        verticalAlignment = Alignment.CenterVertically // Center elements vertically.
    ) {
        // "Welcome" text.
        Text(
            text = "Welcome",
            style = MaterialTheme.typography.titleLarge.copy( // Custom typography.
                fontWeight = FontWeight.Medium,
                fontSize = 30.sp
            ),
            color = MaterialTheme.colorScheme.onSurface // Text color from theme.
        )

        // Premium theme button with visual effects and toggle functionality.
        Box(
            modifier = Modifier
                .size(56.dp) // Size of the outer button container.
                .shadow( // Apply a shadow for depth.
                    elevation = if (ThemeState.isDarkTheme) 8.dp else 4.dp, // Dynamic elevation based on theme.
                    shape = CircleShape,
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
                .clip(CircleShape) // Clip content to a circular shape.
                .background( // Outer background with a linear gradient.
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
                    ThemeState.toggleTheme() // Toggle the theme when clicked.
                },
            contentAlignment = Alignment.Center // Center content within the box.
        ) {
            // Inner circle with a radial gradient for a "premium" look.
            Box(
                modifier = Modifier
                    .size(44.dp) // Size of the inner circle.
                    .clip(CircleShape) // Clip to circle.
                    .background(
                        brush = if (ThemeState.isDarkTheme) {
                            Brush.radialGradient( // Dark theme gradient colors.
                                colors = listOf(
                                    Color(0xFF6C7B95), // Dark grayish blue.
                                    Color(0xFF4A5568)  // Darker bluish gray.
                                )
                            )
                        } else {
                            Brush.radialGradient( // Light theme gradient colors (white/pale yellow for sun).
                                colors = listOf(
                                    Color(0xFFFFFFFF), // Very light yellow.
                                    Color(0xFFFFFFFF)  // Pale yellow.
                                )
                            )
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center // Center content within this box.
            ) {
                // Icon with smooth transition and theme-adaptive colors.
                AnimatedContent(
                    targetState = ThemeState.isDarkTheme, // Animate based on `isDarkTheme` state.
                    transitionSpec = {
                        // Define entry and exit transitions for the icon.
                        // ScaleIn and FadeIn on entry with a bouncy spring animation.
                        (scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ) + fadeIn(animationSpec = tween(400))) togetherWith // Combine transitions for entry.
                                // ScaleOut and FadeOut on exit.
                                (scaleOut(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)))
                    },
                    label = "theme_icon_transition" // Label for animation debugging.
                ) { isDark ->
                    Icon(
                        imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode, // Dynamic icon.
                        contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode",
                        tint = if (isDark) {
                            Color(0xFFF7FAFC) // Light blue tint for dark mode icon.
                        } else {
                            Color(0xFF4A5568) // Golden yellow for light mode icon (sun).
                        },
                        modifier = Modifier.size(22.dp) // Size of the icon.
                    )
                }
            }
        }
    }
}

/**
 * Composable for the modern header section, displaying the app's logo and name.
 * The logo dynamically changes based on the current theme.
 * @param controller The [MainController] (though not directly used here for data, kept for consistency).
 */
@Composable
private fun ModernHeaderSection(
    controller: MainController
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally // Center content horizontally.
    ) {
        // App logo, dynamically selected based on `ThemeState.isDarkTheme`.
        Image(
            painter = painterResource(
                id = if (ThemeState.isDarkTheme) R.drawable.logo_dark else R.drawable.logo_light
            ),
            contentDescription = "Juls Logo",
            modifier = Modifier.size(200.dp) // Size of the logo.
        )

        Spacer(modifier = Modifier.height(5.dp)) // Small vertical spacing.
        // App title "Juls".
        Text(
            text = "Juls",
            style = MaterialTheme.typography.headlineLarge.copy( // Custom typography.
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp
            ),
            color = MaterialTheme.colorScheme.secondary // Text color from theme.
        )
        // App tagline.
        Text(
            text = "Just Urgent Location Services",
            style = MaterialTheme.typography.titleMedium.copy( // Custom typography.
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            ),
            color = MaterialTheme.colorScheme.primary // Text color from theme.
        )
    }
}

/**
 * Composable for the main control card, which includes permission status,
 * the main tracking toggle button, and status/error messages.
 * @param appState The current [AppState] to display status and determine button enablement.
 * @param controller The [MainController] for interacting with business logic.
 * @param locationPermissions Accompanist's [MultiplePermissionsState] for basic location permissions.
 * @param backgroundLocationPermission Accompanist's [PermissionState] for background location (nullable for older Android).
 */
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
            .shadow(12.dp, RoundedCornerShape(28.dp)), // Apply a deep shadow for a card-like effect.
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // Explicitly set elevation for consistency with shadow.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), // Card background color.
        shape = RoundedCornerShape(28.dp) // Rounded corners for the card.
    ) {
        Column(
            modifier = Modifier.padding(28.dp), // Internal padding.
            horizontalAlignment = Alignment.CenterHorizontally // Center content horizontally.
        ) {
            // Display modern permission status section.
            ModernPermissionsStatus(
                hasLocationPermission = appState.hasLocationPermission,
                hasBackgroundLocationPermission = appState.hasBackgroundLocationPermission,
                locationPermissions = locationPermissions,
                backgroundLocationPermission = backgroundLocationPermission
            )
            Spacer(modifier = Modifier.height(32.dp)) // Vertical spacing.
            // Main button to start/stop tracking.
            MainTrackingButton(
                isTracking = appState.isTrackingEnabled,
                canStart = controller.canStartTracking(), // Check if tracking can be started.
                onToggleTracking = { controller.toggleTracking() } // Callback for button click.
            )
            Spacer(modifier = Modifier.height(24.dp)) // Vertical spacing.
            // Display status and error messages.
            StatusMessages(appState = appState, controller = controller)
        }
    }
}

/**
 * Composable to display the status of location permissions with action buttons.
 * It adapts based on Android version and permission status.
 * @param hasLocationPermission Boolean indicating if basic location permissions are granted.
 * @param hasBackgroundLocationPermission Boolean indicating if background location permission is granted.
 * @param locationPermissions Accompanist's [MultiplePermissionsState] for basic location permissions.
 * @param backgroundLocationPermission Accompanist's [PermissionState] for background location (nullable).
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun ModernPermissionsStatus(
    hasLocationPermission: Boolean,
    hasBackgroundLocationPermission: Boolean,
    locationPermissions: com.google.accompanist.permissions.MultiplePermissionsState,
    backgroundLocationPermission: com.google.accompanist.permissions.PermissionState?
) {
    val context = LocalContext.current // Get context for launching settings intent.
    // Determine overall permission status.
    val allPermissionsGranted = hasLocationPermission && hasBackgroundLocationPermission

    // Define colors based on overall permission status.
    val successColor = LightBlueGray // Custom color for success state.
    val containerColor = if (allPermissionsGranted)
        successColor.copy(alpha = 0.1f) // Light background for success.
    else
        MaterialTheme.colorScheme.errorContainer // Error container color for warning.
    val contentColor = if (allPermissionsGranted)
        successColor // Text/icon color for success.
    else
        MaterialTheme.colorScheme.error // Error color for text/icons.

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor), // Dynamic container color.
        shape = RoundedCornerShape(20.dp) // Rounded corners for the status card.
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically // Center items vertically.
            ) {
                // Icon indicating status (shield for granted, warning for required).
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = contentColor, // Dynamic background color for the icon.
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (allPermissionsGranted) Icons.Default.Shield else Icons.Default.Warning, // Dynamic icon.
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (allPermissionsGranted) Lightest else MaterialTheme.colorScheme.onError // Dynamic icon tint.
                    )
                }
                Spacer(modifier = Modifier.width(16.dp)) // Horizontal spacing.
                Column(modifier = Modifier.weight(1f)) { // Column for status text.
                    Text(
                        text = if (allPermissionsGranted) "Ready to Track" else "Permissions Required", // Dynamic main text.
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = contentColor // Dynamic text color.
                    )
                    Text(
                        text = when {
                            allPermissionsGranted -> "All location permissions granted"
                            !hasLocationPermission -> "Basic location access needed"
                            !hasBackgroundLocationPermission -> "Background location access needed"
                            else -> "Location permissions required" // Fallback message.
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.8f) // Slightly faded text.
                    )
                }
            }

            // Display permission request buttons if not all permissions are granted.
            if (!allPermissionsGranted) {
                Spacer(modifier = Modifier.height(16.dp))

                // Button to request basic location permissions.
                if (!hasLocationPermission) {
                    Button(
                        onClick = {
                            locationPermissions.launchMultiplePermissionRequest() // Launch the permission request.
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

                // Button to request background location permission (only for Android Q+).
                if (hasLocationPermission && !hasBackgroundLocationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (!hasLocationPermission) { // Add spacing only if basic permission button isn't shown above it.
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Button(
                        onClick = {
                            // If rationale should be shown (permission previously denied but not permanently),
                            // direct user to app settings.
                            if (backgroundLocationPermission?.status is PermissionStatus.Denied &&
                                (backgroundLocationPermission.status as PermissionStatus.Denied).shouldShowRationale) {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            } else {
                                // Otherwise, launch the standard permission request.
                                backgroundLocationPermission?.launchPermissionRequest()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LightBlueGray, // Use custom color for background permission button.
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
                                "Open Settings" // Text changes if permanent denial is suspected.
                            } else {
                                "Grant Background Access"
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Information card explaining background location requirement for Android Q+.
                if (hasLocationPermission && !hasBackgroundLocationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) // Subtle background.
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.Top) { // Align icon to top of text.
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary // Info icon tint.
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

/**
 * Composable for the main tracking toggle button (Start/Stop).
 * Its appearance and enabled state dynamically adjust based on tracking status and permissions.
 * @param isTracking Boolean indicating if tracking is currently active.
 * @param canStart Boolean indicating if tracking can be started (all permissions met).
 * @param onToggleTracking Lambda to be invoked when the button is clicked.
 */
@Composable
private fun MainTrackingButton(
    isTracking: Boolean,
    canStart: Boolean,
    onToggleTracking: () -> Unit
) {
    val buttonColor = if (isTracking) MaterialTheme.colorScheme.error else LightBlueGray // Red for Stop, Blue for Start.
    val buttonText = if (isTracking) "Stop Tracking" else "Start Tracking" // Dynamic button text.
    val buttonIcon = if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow // Dynamic button icon.

    Button(
        onClick = onToggleTracking, // Set the click listener.
        enabled = canStart || isTracking, // Enable if can start OR if currently tracking (to allow stopping).
        modifier = Modifier
            .fillMaxWidth() // Fill width.
            .height(64.dp), // Fixed height.
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor, // Dynamic background color.
            contentColor = Lightest, // Text/icon color (white).
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant, // Color when disabled.
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) // Text/icon color when disabled.
        ),
        shape = RoundedCornerShape(16.dp) // Rounded corners for the button.
    ) {
        Icon(
            imageVector = buttonIcon,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp)) // Horizontal spacing between icon and text.
        Text(
            text = buttonText,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold) // Bold text.
        )
    }
}

/**
 * Composable for displaying status and error messages with animated visibility.
 * Messages appear and disappear with vertical slide and fade animations.
 * @param appState The current [AppState] containing `statusMessage` and `errorMessage`.
 * @param controller The [MainController] (not directly used for messages here, but often in similar patterns).
 */
@Composable
private fun StatusMessages(
    appState: AppState,
    controller: MainController
) {
    val successColor = LightBlueGray // Custom color for success messages.

    // Animated visibility for success messages.
    AnimatedVisibility(
        visible = appState.statusMessage.isNotEmpty(), // Visible if statusMessage is not empty.
        enter = slideInVertically() + fadeIn(), // Slide in from top and fade in.
        exit = slideOutVertically() + fadeOut() // Slide out to top and fade out.
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = successColor.copy(alpha = 0.1f)), // Light background.
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle, // Checkmark icon for success.
                    contentDescription = null,
                    tint = successColor, // Tint the icon with success color.
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = appState.statusMessage, // Display the status message.
                    style = MaterialTheme.typography.bodyMedium,
                    color = successColor.copy(alpha = 0.9f) // Slightly faded text.
                )
            }
        }
    }

    // Animated visibility for error messages.
    AnimatedVisibility(
        visible = appState.errorMessage.isNotEmpty(), // Visible if errorMessage is not empty.
        enter = slideInVertically() + fadeIn(), // Slide in from top and fade in.
        exit = slideOutVertically() + fadeOut() // Slide out to top and fade out.
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), // Error background.
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error, // Error icon.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error, // Tint the icon with error color.
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = appState.errorMessage, // Display the error message.
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer // Text color for error container.
                )
            }
        }
    }
}

// Solo la función LocationStatusCard actualizada con el nuevo intervalo

/**
 * Composable for displaying the current GPS tracking status and last received location data.
 * Features an animated icon to indicate active tracking.
 * @param appState The current [AppState] to get location data and tracking status.
 */
@Composable
private fun LocationStatusCard(
    appState: AppState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), // Primary container color.
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Infinite transition for pulsing animation of the location icon.
                val infiniteTransition = rememberInfiniteTransition(label = "tracking_animation")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f, // Starting alpha (more transparent).
                    targetValue = 1f, // Ending alpha (fully opaque).
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = EaseInOutSine), // 1-second sine wave animation.
                        repeatMode = RepeatMode.Reverse // Reverse animation direction for pulsing effect.
                    ),
                    label = "alpha_animation"
                )
                val successColor = LightBlueGray // Custom color for success indication.

                // Animated icon container.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = successColor.copy(alpha = alpha), // Apply pulsing alpha to background color.
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation, // My location icon.
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Lightest // Tint with lightest color (white).
                    )
                }
                Spacer(modifier = Modifier.width(16.dp)) // Horizontal spacing.
                Column {
                    Text(
                        text = "GPS Tracking Active",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer // Text color for primary container.
                    )
                    Text(
                        text = "Sending location every 5 seconds", // ACTUALIZADO: 2 segundos -> 5 segundos
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) // Slightly faded text.
                    )
                }
            }
            // Display location details if a current location is available.
            appState.currentLocation?.let { location ->
                LocationDisplaySection(location)
            }
        }
    }
}

/**
 * Composable to display detailed location information within the [LocationStatusCard].
 * Shows latitude, longitude, and last update time.
 * @param location The [LocationData] object to display.
 */
@Composable
private fun LocationDisplaySection(location: LocationData) {
    Spacer(modifier = Modifier.height(16.dp)) // Vertical spacing.
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) // A thin divider.
    Spacer(modifier = Modifier.height(16.dp)) // Vertical spacing.

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween // Space items evenly horizontally.
    ) {
        // Latitude display.
        Column {
            Text(
                text = "Latitude",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = String.format("%.6f", location.latitude), // Format latitude to 6 decimal places.
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        // Longitude display.
        Column {
            Text(
                text = "Longitude",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = String.format("%.6f", location.longitude), // Format longitude to 6 decimal places.
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp)) // Vertical spacing.

    // Last update timestamp display.
    Text(
        text = "Last Update: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(location.timestamp))}", // Format timestamp to HH:mm:ss.
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    )
}

/**
 * Composable for displaying the connection status of configured servers (TCP and UDP).
 * @param appState The current [AppState] to get server status information.
 */
@Composable
private fun ServerStatusCard(
    appState: AppState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), // Secondary container color.
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon representing cloud/server status.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondary, // Secondary color for the icon background.
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud, // Cloud icon.
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondary // Tint with on-secondary color.
                    )
                }
                Spacer(modifier = Modifier.width(16.dp)) // Horizontal spacing.
                Column {
                    Text(
                        text = "Server Status",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer // Text color.
                    )
                    Text(
                        text = "TCP & UDP transmission status",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f) // Slightly faded text.
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp)) // Vertical spacing.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { // Space rows vertically.
                // Display status for Server 1.
                ServerConnectionRow(
                    serverName = "Server 1",
                    tcpStatus = appState.serverStatus.server1TCP,
                    udpStatus = appState.serverStatus.server1UDP
                )
                // Display status for Server 2.
                ServerConnectionRow(
                    serverName = "Server 2",
                    tcpStatus = appState.serverStatus.server2TCP,
                    udpStatus = appState.serverStatus.server2UDP
                )
            }
        }
    }
}

/**
 * Composable for a single row displaying the connection status for TCP and UDP protocols of a server.
 * @param serverName The name of the server (e.g., "Server 1").
 * @param tcpStatus The [ServerStatus.ConnectionStatus] for TCP.
 * @param udpStatus The [ServerStatus.ConnectionStatus] for UDP.
 */
@Composable
private fun ServerConnectionRow(
    serverName: String,
    tcpStatus: com.tudominio.smslocation.model.data.ServerStatus.ConnectionStatus,
    udpStatus: com.tudominio.smslocation.model.data.ServerStatus.ConnectionStatus
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween, // Space elements evenly.
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = serverName,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f) // Text takes available width.
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { // Space indicators horizontally.
            // TCP connection indicator.
            ConnectionIndicator(
                label = "TCP",
                isConnected = tcpStatus == com.tudominio.smslocation.model.data.ServerStatus.ConnectionStatus.CONNECTED
            )
            // UDP connection indicator.
            ConnectionIndicator(
                label = "UDP",
                isConnected = udpStatus == com.tudominio.smslocation.model.data.ServerStatus.ConnectionStatus.CONNECTED
            )
        }
    }
}

/**
 * Composable for a small visual indicator of a connection's status.
 * Displays a colored circle and text label.
 * @param label The text label for the connection type (e.g., "TCP", "UDP").
 * @param isConnected Boolean indicating if the connection is active.
 */
@Composable
private fun ConnectionIndicator(
    label: String,
    isConnected: Boolean
) {
    val successColor = LightBlueGray // Custom color for connected state.
    val errorColor = MaterialTheme.colorScheme.error // Error color for disconnected state.

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp) // Small spacing between circle and text.
    ) {
        Box(
            modifier = Modifier
                .size(8.dp) // Size of the status circle.
                .background(
                    color = if (isConnected) successColor else errorColor, // Dynamic color based on status.
                    shape = CircleShape // Circular shape.
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isConnected) successColor else errorColor // Dynamic text color.
        )
    }
}