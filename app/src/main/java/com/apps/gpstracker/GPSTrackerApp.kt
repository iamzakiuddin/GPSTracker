package com.apps.gpstracker

import android.app.Application
import com.apps.gpstracker.location.LocationRepository
import com.apps.gpstracker.location.TrackingNotifier

class GPSTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TrackingNotifier.ensureChannel(this)
        LocationRepository.get(this)
    }
}
