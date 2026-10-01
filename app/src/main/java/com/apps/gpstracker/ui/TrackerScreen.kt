package com.apps.gpstracker.ui

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
import com.apps.gpstracker.location.LocationFix
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    viewModel: TrackerViewModel,
    isPrecise: Boolean,
    onTurnOnGps: () -> Unit,
    onAllowPrecise: () -> Unit,
) {
    val saved by viewModel.savedFix.collectAsStateWithLifecycle()
    val current by viewModel.currentFix.collectAsStateWithLifecycle()
    val live by viewModel.liveFix.collectAsStateWithLifecycle()
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
                    body = "Please turn on location / GPS to get a current fix and live updates.",
                    buttonText = "Turn on GPS",
                    onButton = onTurnOnGps,
                    warning = true,
                )
            }

            if (!isPrecise) {
                InfoCard(
                    title = "Approximate location",
                    body = "You allowed Approximate location. The app still works, but GPS is less accurate. Allow Precise for a better fix near buildings.",
                    buttonText = "Allow precise location",
                    onButton = onAllowPrecise,
                    warning = false,
                )
            }

            if (!tracking) {
                CurrentOrSavedCard(currentFix = current, savedFix = saved)
            }

            LiveTrackingCard(
                tracking = tracking,
                liveFix = live,
                gpsOn = gpsOn,
                onStart = { viewModel.startLiveTracking() },
                onStop = { viewModel.stopLiveTracking() },
            )
        }
    }
}

@Composable
private fun CurrentOrSavedCard(currentFix: LocationFix?, savedFix: LocationFix?) {
    val showCurrent = currentFix != null
    val fix = currentFix ?: savedFix
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = when {
                    showCurrent -> "Current location"
                    fix != null -> "Saved location"
                    else -> "Current location"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            if (fix == null) {
                Text("No location yet. Waiting for GPS or a last known location.")
            } else {
                if (!showCurrent) {
                    Text(
                        "Could not get a fresh location. Showing the last known saved location.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Coordinates(fix, big = false)
            }
        }
    }
}

@Composable
private fun LiveTrackingCard(
    tracking: Boolean,
    liveFix: LocationFix?,
    gpsOn: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
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
                Text("Continuous updates are running.")
                if (liveFix == null) {
                    Text("Waiting for the next GPS update…")
                } else {
                    Coordinates(liveFix, big = true)
                }
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop live tracking")
                }
            } else {
                Text("Tap the button to start continuous location tracking.")
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

@Composable
private fun Coordinates(fix: LocationFix, big: Boolean) {
    val context = LocalContext.current
    val size = if (big) 28.sp else 20.sp
    Text(
        text = String.format(Locale.US, "%.6f", fix.latitude),
        fontSize = size,
        fontFamily = FontFamily.Monospace,
    )
    Text(
        text = String.format(Locale.US, "%.6f", fix.longitude),
        fontSize = size,
        fontFamily = FontFamily.Monospace,
    )
    Text("Accuracy ±${fix.accuracyMeters.toInt()} m")
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
                "Location is blocked. Open Settings and allow location (Precise is best)."
            } else {
                "Please allow location so the app can show your current position. Precise location is more accurate."
            },
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) {
            Text("Allow location")
        }
        if (blockedForever) {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("Open settings")
            }
        }
    }
}
