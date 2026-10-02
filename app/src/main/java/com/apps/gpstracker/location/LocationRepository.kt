package com.apps.gpstracker.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.apps.gpstracker.permission.LocationPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val store = LocationStore(appContext)
    val helper = LocationHelper(appContext)

    private val _cachedLocation = MutableStateFlow(store.load())
    val cachedLocation: StateFlow<DeviceLocation?> = _cachedLocation.asStateFlow()

    private val _latestLocation = MutableStateFlow<DeviceLocation?>(null)
    val latestLocation: StateFlow<DeviceLocation?> = _latestLocation.asStateFlow()

    private val _liveLocation = MutableStateFlow<DeviceLocation?>(null)
    val liveLocation: StateFlow<DeviceLocation?> = _liveLocation.asStateFlow()

    private val _isLiveTracking = MutableStateFlow(false)
    val isLiveTracking: StateFlow<Boolean> = _isLiveTracking.asStateFlow()

    private val _gpsOn = MutableStateFlow(LocationPermissions.isGpsOn(appContext))
    val gpsOn: StateFlow<Boolean> = _gpsOn.asStateFlow()

    private val gpsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshGps()
            if (_gpsOn.value && LocationPermissions.hasAnyLocation(appContext)) {
                loadCurrentLocation()
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(
            appContext,
            gpsReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun refreshGps() {
        _gpsOn.value = LocationPermissions.isGpsOn(appContext)
    }

    fun loadCurrentLocation() {
        refreshGps()
        if (!LocationPermissions.hasAnyLocation(appContext) || !_gpsOn.value) return
        helper.getLatestLocation { latest ->
            if (latest != null) {
                _latestLocation.value = latest
                save(latest)
            } else {
                helper.readSystemLastKnown { lastKnown ->
                    if (lastKnown != null) {
                        save(lastKnown)
                    }
                }
            }
        }
    }

    fun onLiveLocation(location: DeviceLocation) {
        _liveLocation.value = location
        _latestLocation.value = location
        save(location)
    }

    fun setLiveTracking(on: Boolean) {
        _isLiveTracking.value = on
        if (!on) {
            _liveLocation.value = null
        }
    }

    private fun save(location: DeviceLocation) {
        store.save(location)
        _cachedLocation.value = location
    }

    companion object {
        @Volatile
        private var instance: LocationRepository? = null

        fun get(context: Context): LocationRepository {
            return instance ?: synchronized(this) {
                instance ?: LocationRepository(context).also { instance = it }
            }
        }
    }
}
