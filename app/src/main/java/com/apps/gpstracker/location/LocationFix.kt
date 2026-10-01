package com.apps.gpstracker.location

import android.location.Location
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timeMillis: Long
) {
    companion object {
        fun from(location: Location): LocationFix {
            return LocationFix(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else 0f,
                timeMillis = location.time,
            )
        }
    }
}
