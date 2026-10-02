package com.apps.gpstracker.location

import android.content.Context

class LocationStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("saved_location", Context.MODE_PRIVATE)

    fun save(location: DeviceLocation) {
        prefs.edit()
            .putString("lat", location.latitude.toString())
            .putString("lon", location.longitude.toString())
            .putFloat("accuracy", location.accuracyMeters)
            .putLong("time", location.timeMillis)
            .apply()
    }

    fun load(): DeviceLocation? {
        val lat = prefs.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString("lon", null)?.toDoubleOrNull() ?: return null
        return DeviceLocation(
            latitude = lat,
            longitude = lon,
            accuracyMeters = prefs.getFloat("accuracy", 0f),
            timeMillis = prefs.getLong("time", 0L),
        )
    }
}
