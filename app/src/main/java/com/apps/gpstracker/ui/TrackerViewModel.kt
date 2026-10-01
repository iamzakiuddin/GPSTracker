package com.apps.gpstracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.apps.gpstracker.location.LocationRepository
import com.apps.gpstracker.location.LocationTrackingService
import com.apps.gpstracker.permission.LocationPermissions

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocationRepository.get(application)

    val savedFix = repository.savedFix
    val currentFix = repository.currentFix
    val liveFix = repository.liveFix
    val isLiveTracking = repository.isLiveTracking
    val gpsOn = repository.gpsOn

    fun refreshOnResume() {
        val context = getApplication<Application>()
        repository.refreshGps()
        if (!LocationPermissions.hasAnyLocation(context)) {
            stopLiveTracking()
            return
        }
        repository.loadCurrentLocation()
    }

    fun startLiveTracking() {
        val context = getApplication<Application>()
        if (!LocationPermissions.hasAnyLocation(context)) return
        if (!LocationPermissions.isGpsOn(context)) return
        LocationTrackingService.start(context)
    }

    fun stopLiveTracking() {
        LocationTrackingService.stop(getApplication())
    }
}
