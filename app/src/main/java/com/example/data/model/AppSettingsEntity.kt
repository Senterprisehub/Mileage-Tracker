package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val countryCode: String = "IN",
    val countryName: String = "India",
    val currencyCode: String = "INR",
    val currencySymbol: String = "₹",
    val unitDistance: String = "km",
    val selectedFuelType: String = "PETROL",
    val currentFuelPrice: Double = 102.12,
    val fuelRateUnit: String = "L",
    val currentCity: String = "New Delhi",
    val lastFuelRateFetchTime: Long = 0L,
    val lastFuelRateSource: String = "GoodReturns.in",
    val standardTaxRatePerKm: Double = 10.0,
    val workHoursEnabled: Boolean = false,
    val dayWiseScheduleJson: String = "",
    val autoTrackingEnabled: Boolean = true,
    val autoDriveDetectionSpeedKmh: Int = 15,
    val autoTrackingPausedUntil: Long? = null,
    val stopTimeoutMinutes: Int = 8
) {
    fun isAutoTrackingPaused(): Boolean {
        val until = autoTrackingPausedUntil ?: return false
        if (until == -1L) return true
        return System.currentTimeMillis() < until
    }

    fun isPausedIndefinitely(): Boolean {
        return autoTrackingPausedUntil == -1L
    }

    fun getParsedSchedules(): List<DaySchedule> {
        return ScheduleSerializer.fromJson(dayWiseScheduleJson)
    }
}
