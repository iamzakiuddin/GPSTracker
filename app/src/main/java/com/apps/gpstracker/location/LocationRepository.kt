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

    private val _savedFix = MutableStateFlow(store.load())
    val savedFix: StateFlow<LocationFix?> = _savedFix.asStateFlow()

    private val _currentFix = MutableStateFlow<LocationFix?>(null)
    val currentFix: StateFlow<LocationFix?> = _currentFix.asStateFlow()

    private val _liveFix = MutableStateFlow<LocationFix?>(null)
    val liveFix: StateFlow<LocationFix?> = _liveFix.asStateFlow()

    private val _isLiveTracking = MutableStateFlow(false)
    val isLiveTracking: StateFlow<Boolean> = _isLiveTracking.asStateFlow()

    private val _gpsOn = MutableStateFlow(LocationPermissions.isGpsOn(appContext))
    val gpsOn: StateFlow<Boolean> = _gpsOn.asStateFlow()

    private val gpsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshGps()
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
        helper.getCurrentFix { fresh ->
            if (fresh != null) {
                _currentFix.value = fresh
                save(fresh)
            } else {
                helper.readSystemLastKnown { lastKnown ->
                    if (lastKnown != null) {
                        save(lastKnown)
                    }
                }
            }
        }
    }

    fun onLiveLocation(fix: LocationFix) {
        _liveFix.value = fix
        _currentFix.value = fix
        save(fix)
    }

    fun setLiveTracking(on: Boolean) {
        _isLiveTracking.value = on
        if (!on) {
            _liveFix.value = null
        }
    }

    private fun save(fix: LocationFix) {
        store.save(fix)
        _savedFix.value = fix
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
