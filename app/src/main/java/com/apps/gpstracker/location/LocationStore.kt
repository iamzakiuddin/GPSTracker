package com.apps.gpstracker.location

import android.content.Context

class LocationStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("saved_location", Context.MODE_PRIVATE)

    fun save(fix: LocationFix) {
        prefs.edit()
            .putString("lat", fix.latitude.toString())
            .putString("lon", fix.longitude.toString())
            .putFloat("accuracy", fix.accuracyMeters)
            .putLong("time", fix.timeMillis)
            .apply()
    }

    fun load(): LocationFix? {
        val lat = prefs.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString("lon", null)?.toDoubleOrNull() ?: return null
        return LocationFix(
            latitude = lat,
            longitude = lon,
            accuracyMeters = prefs.getFloat("accuracy", 0f),
            timeMillis = prefs.getLong("time", 0L),
        )
    }
}
