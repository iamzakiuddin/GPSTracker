package com.apps.gpstracker.ui

import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apps.gpstracker.location.DeviceLocation
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    viewModel: TrackerViewModel,
    isPrecise: Boolean,
    hasNotificationPermission: Boolean,
    blockedNotificationForever: Boolean,
    onTurnOnGps: () -> Unit,
    onAllowPrecise: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val cached by viewModel.cachedLocation.collectAsStateWithLifecycle()
    val latest by viewModel.latestLocation.collectAsStateWithLifecycle()
    val live by viewModel.liveLocation.collectAsStateWithLifecycle()
    val tracking by viewModel.isLiveTracking.collectAsStateWithLifecycle()
    val gpsOn by viewModel.gpsOn.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("GPS Tracker") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!gpsOn) {
                InfoCard(
                    title = "GPS is off",
                    body = "Please turn on GPS to get your current position and live updates.",
                    buttonText = "Turn on GPS",
                    onButton = onTurnOnGps,
                    warning = true,
                )
            }

            if (!isPrecise) {
                InfoCard(
                    title = "Approximate location",
                    body = "You allowed Approximate location. The app still works, but GPS is less accurate. Allow Precise for a better reading near tall buildings.",
                    buttonText = "Allow precise location",
                    onButton = onAllowPrecise,
                    warning = false,
                )
            }

            if (!tracking) {
                CurrentOrSavedCard(latestLocation = latest, cachedLocation = cached)
            }

            LiveTrackingCard(
                tracking = tracking,
                liveLocation = live,
                gpsOn = gpsOn,
                hasNotificationPermission = hasNotificationPermission,
                blockedNotificationForever = blockedNotificationForever,
                onStart = { viewModel.startLiveTracking() },
                onStop = { viewModel.stopLiveTracking() },
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

@Composable
private fun CurrentOrSavedCard(
    latestLocation: DeviceLocation?,
    cachedLocation: DeviceLocation?,
) {
    val showLatest = latestLocation != null
    val location = latestLocation ?: cachedLocation
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = when {
                    showLatest -> "Current location"
                    location != null -> "Saved location"
                    else -> "Current location"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            if (location == null) {
                Text("No location yet. Waiting for GPS or a last known location.")
            } else {
                if (!showLatest) {
                    Text(
                        "Could not get a new location. Showing the last known saved location.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Coordinates(location, big = false)
            }
        }
    }
}

@Composable
private fun LiveTrackingCard(
    tracking: Boolean,
    liveLocation: DeviceLocation?,
    gpsOn: Boolean,
    hasNotificationPermission: Boolean,
    blockedNotificationForever: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (tracking) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Live location", style = MaterialTheme.typography.titleLarge)
            if (tracking) {
                Text("Continuous updates are running in the background.")
                if (liveLocation == null) {
                    Text("Waiting for the next location update…")
                } else {
                    Coordinates(liveLocation, big = true)
                }
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop live tracking")
                }
            } else {
                Text("Tap the button to start continuous location tracking.")

                val needsNotificationWarning =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission

                if (needsNotificationWarning) {
                    Text(
                        text = if (blockedNotificationForever) {
                            "App needs notification permission to work properly for live tracking. Permission is blocked in settings."
                        } else {
                            "App needs notification permission to work properly for live background tracking."
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (needsNotificationWarning) {
                    if (blockedNotificationForever) {
                        Button(
                            onClick = onOpenSettings,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open Settings for Notifications")
                        }
                    } else {
                        Button(
                            onClick = onRequestNotificationPermission,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Allow notification permission")
                        }
                    }
                } else {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = gpsOn,
                    ) {
                        Text("Start live GPS tracking")
                    }
                }
            }
        }
    }
}

@Composable
private fun Coordinates(location: DeviceLocation, big: Boolean) {
    val context = LocalContext.current
    val size = if (big) 28.sp else 20.sp
    Text(
        text = String.format(Locale.US, "%.6f", location.latitude),
        fontSize = size,
        fontFamily = FontFamily.Monospace,
    )
    Text(
        text = String.format(Locale.US, "%.6f", location.longitude),
        fontSize = size,
        fontFamily = FontFamily.Monospace,
    )
    Text("Accuracy ±${location.accuracyMeters.toInt()} m")
    if (location.timeMillis > 0L) {
        val time = DateFormat.getTimeFormat(context).format(Date(location.timeMillis))
        Text("Updated $time")
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
    buttonText: String,
    onButton: () -> Unit,
    warning: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body)
            Button(onClick = onButton, modifier = Modifier.fillMaxWidth()) {
                Text(buttonText)
            }
        }
    }
}

@Composable
fun AskLocationScreen(
    blockedForever: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Location permission needed", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (blockedForever) {
                "Location permission is blocked. Please open App Settings and grant location permission (Precise is recommended)."
            } else {
                "Please allow location permission so the app can show your current position. Precise location is more accurate."
            },
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        if (blockedForever) {
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Open Settings")
            }
        } else {
            Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) {
                Text("Allow location permission")
            }
        }
    }
}
