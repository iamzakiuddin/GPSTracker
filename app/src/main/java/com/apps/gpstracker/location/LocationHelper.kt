package com.apps.gpstracker.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class LocationHelper(context: Context) {
    private val appContext = context.applicationContext
    private val fused = LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private var liveCallback: LocationCallback? = null
    private val gpsListener = LocationListener { location -> publishIfBetter(location) }
    private val networkListener = LocationListener { location -> publishIfBetter(location) }
    private var onLiveLocation: ((DeviceLocation) -> Unit)? = null
    private var lastPublished: DeviceLocation? = null

    fun getLatestLocation(onResult: (DeviceLocation?) -> Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            onResult(null)
            return
        }

        val request = CurrentLocationRequest.Builder()
            .setPriority(
                if (fineGranted) Priority.PRIORITY_HIGH_ACCURACY
                else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            )
            .setMaxUpdateAgeMillis(15_000L)
            .build()

        try {
            fused.getCurrentLocation(request, CancellationTokenSource().token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        onResult(DeviceLocation.from(location))
                    } else {
                        onResult(lastKnownLocation())
                    }
                }
                .addOnFailureListener {
                    onResult(lastKnownLocation())
                }
        } catch (_: SecurityException) {
            onResult(lastKnownLocation())
        }
    }

    fun startLiveUpdates(onLocation: (DeviceLocation) -> Unit) {
        stopLiveUpdates()
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return

        onLiveLocation = onLocation
        lastPublished = null
        val settings = LocationUpdatePolicy.current(appContext, lastAccuracyMeters = null)
        val request = LocationRequest.Builder(settings.priority, settings.intervalMs)
            .setMinUpdateIntervalMillis(settings.minIntervalMs)
            .setMinUpdateDistanceMeters(settings.minDistanceMeters)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { publishIfBetter(it) }
            }
        }
        liveCallback = callback

        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())

            if (fineGranted &&
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            ) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    settings.intervalMs,
                    settings.minDistanceMeters,
                    gpsListener,
                    Looper.getMainLooper(),
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    maxOf(settings.intervalMs, 5_000L),
                    maxOf(settings.minDistanceMeters, 5f),
                    networkListener,
                    Looper.getMainLooper(),
                )
            }
        } catch (_: SecurityException) {
            // Permission was revoked while updates were starting.
        }
    }

    fun stopLiveUpdates() {
        liveCallback?.let { fused.removeLocationUpdates(it) }
        liveCallback = null
        runCatching { locationManager.removeUpdates(gpsListener) }
        runCatching { locationManager.removeUpdates(networkListener) }
        onLiveLocation = null
        lastPublished = null
    }

    fun readSystemLastKnown(onResult: (DeviceLocation?) -> Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            onResult(null)
            return
        }
        try {
            fused.lastLocation
                .addOnSuccessListener { location ->
                    val system = lastKnownLocation()
                    val fusedLocation = location?.let { DeviceLocation.from(it) }
                    val newest = listOfNotNull(fusedLocation, system).maxByOrNull { it.timeMillis }
                    onResult(newest)
                }
                .addOnFailureListener { onResult(lastKnownLocation()) }
        } catch (_: SecurityException) {
            onResult(lastKnownLocation())
        }
    }

    private fun lastKnownLocation(): DeviceLocation? {
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return null
        return try {
            val gps = if (fineGranted) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else {
                null
            }
            val network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            listOfNotNull(gps, network).maxByOrNull { it.time }?.let { DeviceLocation.from(it) }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun publishIfBetter(location: Location) {
        if (!location.hasAccuracy()) return
        val candidate = DeviceLocation.from(location)
        if (!isBetterLocation(candidate, lastPublished)) return
        lastPublished = candidate
        onLiveLocation?.invoke(candidate)
    }

    private fun isBetterLocation(
        candidate: DeviceLocation,
        current: DeviceLocation?,
    ): Boolean {
        if (current == null) return candidate.accuracyMeters <= MAX_USABLE_ACCURACY_METERS
        if (candidate.accuracyMeters > MAX_USABLE_ACCURACY_METERS &&
            current.accuracyMeters <= MAX_USABLE_ACCURACY_METERS
        ) {
            return false
        }
        val ageMs = candidate.timeMillis - current.timeMillis
        if (ageMs < -20_000L) return false
        if (ageMs in 0 until 45_000 &&
            current.accuracyMeters <= 50f &&
            candidate.accuracyMeters > current.accuracyMeters * 2.5f
        ) {
            return false
        }
        if (ageMs > 20_000L && candidate.accuracyMeters - current.accuracyMeters <= 50f) {
            return true
        }
        return candidate.accuracyMeters < current.accuracyMeters || ageMs > 0
    }

    companion object {
        private const val MAX_USABLE_ACCURACY_METERS = 250f
    }
}
