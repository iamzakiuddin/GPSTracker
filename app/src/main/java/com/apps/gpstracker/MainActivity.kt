package com.apps.gpstracker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apps.gpstracker.permission.LocationPermissions
import com.apps.gpstracker.ui.AskLocationScreen
import com.apps.gpstracker.ui.TrackerScreen
import com.apps.gpstracker.ui.TrackerViewModel
import com.apps.gpstracker.ui.theme.GPSTrackerTheme
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GPSTrackerTheme {
                val viewModel: TrackerViewModel = viewModel()
                val context = LocalContext.current
                val activity = context as Activity
                val lifecycleOwner = LocalLifecycleOwner.current

                var refresh by remember { mutableIntStateOf(0) }
                var askedOnce by remember { mutableStateOf(false) }

                val hasAny = remember(refresh) { LocationPermissions.hasAnyLocation(context) }
                val isPrecise = remember(refresh) { LocationPermissions.hasPreciseLocation(context) }
                val blockedForever = remember(refresh, askedOnce, hasAny) {
                    askedOnce &&
                        !hasAny &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(
                            activity,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ) &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(
                            activity,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    askedOnce = true
                    refresh++
                    viewModel.refreshOnResume()
                }
                val gpsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult(),
                ) {
                    refresh++
                    viewModel.refreshOnResume()
                }

                fun askLocationPermission() {
                    permissionLauncher.launch(LocationPermissions.requestList())
                }

                fun openAppSettings() {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null),
                        ),
                    )
                }

                fun turnOnGps() {
                    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L).build()
                    val settings = LocationSettingsRequest.Builder()
                        .addLocationRequest(request)
                        .setAlwaysShow(true)
                        .build()
                    LocationServices.getSettingsClient(this)
                        .checkLocationSettings(settings)
                        .addOnFailureListener { error ->
                            if (error is ResolvableApiException) {
                                gpsLauncher.launch(
                                    IntentSenderRequest.Builder(error.resolution).build(),
                                )
                            } else {
                                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            }
                        }
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            refresh++
                            viewModel.refreshOnResume()
                            if (!LocationPermissions.hasAnyLocation(context)) {
                                val blocked = askedOnce &&
                                    !ActivityCompat.shouldShowRequestPermissionRationale(
                                        activity,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                    ) &&
                                    !ActivityCompat.shouldShowRequestPermissionRationale(
                                        activity,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                if (!blocked) {
                                    askLocationPermission()
                                }
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                if (!hasAny) {
                    AskLocationScreen(
                        blockedForever = blockedForever,
                        onAllow = {
                            if (blockedForever) openAppSettings() else askLocationPermission()
                        },
                        onOpenSettings = { openAppSettings() },
                    )
                } else {
                    TrackerScreen(
                        viewModel = viewModel,
                        isPrecise = isPrecise,
                        onTurnOnGps = { turnOnGps() },
                        onAllowPrecise = { askLocationPermission() },
                    )
                }
            }
        }
    }
}
