package com.apps.gpstracker.location

import android.location.Location

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timeMillis: Long,
) {
    companion object {
        fun from(location: Location): DeviceLocation {
            return DeviceLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else 0f,
                timeMillis = location.time,
            )
        }
    }
}
