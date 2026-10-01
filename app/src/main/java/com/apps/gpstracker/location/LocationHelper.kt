package com.apps.gpstracker.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.apps.gpstracker.permission.LocationPermissions
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
    private val gpsListener = LocationListener { location -> notifyLive(location) }
    private val networkListener = LocationListener { location -> notifyLive(location) }
    private var onLiveFix: ((LocationFix) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun getCurrentFix(onResult: (LocationFix?) -> Unit) {
        if (!LocationPermissions.hasAnyLocation(appContext)) {
            onResult(null)
            return
        }

        val precise = LocationPermissions.hasPreciseLocation(appContext)
        val request = CurrentLocationRequest.Builder()
            .setPriority(if (precise) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(15_000L)
            .build()

        fused.getCurrentLocation(request, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    onResult(LocationFix.from(location))
                } else {
                    onResult(lastKnownFix())
                }
            }
            .addOnFailureListener {
                onResult(lastKnownFix())
            }
    }

    @SuppressLint("MissingPermission")
    fun startLiveUpdates(onFix: (LocationFix) -> Unit) {
        stopLiveUpdates()
        if (!LocationPermissions.hasAnyLocation(appContext)) return

        onLiveFix = onFix
        val precise = LocationPermissions.hasPreciseLocation(appContext)
        val priority = if (precise) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val request = LocationRequest.Builder(priority, 3_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setMinUpdateDistanceMeters(1f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { notifyLive(it) }
            }
        }
        liveCallback = callback
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())

        try {
            if (precise && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3_000L,
                    1f,
                    gpsListener,
                    Looper.getMainLooper(),
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5_000L,
                    5f,
                    networkListener,
                    Looper.getMainLooper(),
                )
            }
        } catch (_: SecurityException) {
            // Permission was turned off while we were starting.
        }
    }

    fun stopLiveUpdates() {
        liveCallback?.let { fused.removeLocationUpdates(it) }
        liveCallback = null
        runCatching { locationManager.removeUpdates(gpsListener) }
        runCatching { locationManager.removeUpdates(networkListener) }
        onLiveFix = null
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownFix(): LocationFix? {
        if (!LocationPermissions.hasAnyLocation(appContext)) return null
        val gps = if (LocationPermissions.hasPreciseLocation(appContext)) {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } else {
            null
        }
        val network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val best = listOfNotNull(gps, network).maxByOrNull { it.time }
        return best?.let { LocationFix.from(it) }
    }

    @SuppressLint("MissingPermission")
    fun readSystemLastKnown(onResult: (LocationFix?) -> Unit) {
        if (!LocationPermissions.hasAnyLocation(appContext)) {
            onResult(null)
            return
        }
        fused.lastLocation
            .addOnSuccessListener { location ->
                val system = lastKnownFix()
                val fusedFix = location?.let { LocationFix.from(it) }
                val best = listOfNotNull(fusedFix, system).maxByOrNull { it.timeMillis }
                onResult(best)
            }
            .addOnFailureListener { onResult(lastKnownFix()) }
    }

    private fun notifyLive(location: Location) {
        onLiveFix?.invoke(LocationFix.from(location))
    }
}
