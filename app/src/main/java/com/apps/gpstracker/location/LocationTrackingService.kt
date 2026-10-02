package com.apps.gpstracker.location

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.apps.gpstracker.permission.LocationPermissions

class LocationTrackingService : Service() {
    private val repository by lazy { LocationRepository.get(this) }

    private val gpsReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            repository.refreshGps()
            if (LocationPermissions.isGpsOn(this@LocationTrackingService) &&
                hasLocationPermission()
            ) {
                startUpdates()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        TrackingNotifier.ensureChannel(this)
        val filter = android.content.IntentFilter().apply {
            addAction(android.location.LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(android.location.LocationManager.MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(this, gpsReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        if (!hasLocationPermission()) {
            stopEverything()
            return START_NOT_STICKY
        }

        val notification = TrackingNotifier.build(this, repository.liveLocation.value)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    TrackingNotifier.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(TrackingNotifier.NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        repository.setLiveTracking(true)
        startUpdates()
        return START_STICKY
    }

    private fun startUpdates() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return

        try {
            repository.helper.startLiveUpdates { location ->
                repository.onLiveLocation(location)
                updateNotification(location)
            }
        } catch (_: SecurityException) {
        }
    }

    private fun updateNotification(location: DeviceLocation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationsGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!notificationsGranted) return
        }
        try {
            NotificationManagerCompat.from(this)
                .notify(TrackingNotifier.NOTIFICATION_ID, TrackingNotifier.build(this, location))
        } catch (_: SecurityException) {
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopEverything()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(gpsReceiver) }
        repository.helper.stopLiveUpdates()
        repository.setLiveTracking(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopEverything() {
        repository.helper.stopLiveUpdates()
        repository.setLiveTracking(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    companion object {
        const val ACTION_STOP = "com.apps.gpstracker.STOP_LIVE"

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {

            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            intent.action = ACTION_STOP
            context.startService(intent)
        }
    }
}
