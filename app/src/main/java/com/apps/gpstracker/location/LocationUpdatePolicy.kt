package com.apps.gpstracker.location

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.google.android.gms.location.Priority

data class LocationUpdateSettings(
    val intervalMs: Long,
    val minIntervalMs: Long,
    val minDistanceMeters: Float,
    val priority: Int,
)

object LocationUpdatePolicy {
    private const val WEAK_ACCURACY_METERS = 60f

    fun current(context: Context, lastAccuracyMeters: Float?): LocationUpdateSettings {
        val battery = batteryState(context)
        val weakSignal = lastAccuracyMeters != null && lastAccuracyMeters > WEAK_ACCURACY_METERS
        val critical = battery.percent <= 15 && !battery.charging
        val low = battery.percent <= 30 && !battery.charging

        return when {
            critical -> LocationUpdateSettings(
                intervalMs = if (weakSignal) 30_000L else 60_000L,
                minIntervalMs = 20_000L,
                minDistanceMeters = 25f,
                priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            )
            low -> LocationUpdateSettings(
                intervalMs = if (weakSignal) 12_000L else 20_000L,
                minIntervalMs = 8_000L,
                minDistanceMeters = 12f,
                priority = if (weakSignal) {
                    Priority.PRIORITY_HIGH_ACCURACY
                } else {
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY
                },
            )
            else -> LocationUpdateSettings(
                intervalMs = if (weakSignal) 3_000L else 5_000L,
                minIntervalMs = 1_000L,
                minDistanceMeters = 5f,
                priority = Priority.PRIORITY_HIGH_ACCURACY,
            )
        }
    }

    private fun batteryState(context: Context): BatteryState {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else 50
        return BatteryState(percent, charging)
    }

    private data class BatteryState(val percent: Int, val charging: Boolean)
}
