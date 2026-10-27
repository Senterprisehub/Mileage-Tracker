package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveTrackingData(
    val isTracking: Boolean = false,
    val tripId: String? = null,
    val distanceKm: Double = 0.0,
    val speedKmh: Float = 0f,
    val elapsedSeconds: Long = 0L,
    val startAddress: String = "Detecting start location...",
    val currentAddress: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val vehicleId: String? = null,
    val vehicleName: String = "",
    val vehicleMileageKmpl: Double = 14.0,
    val vehicleEfficiencyUnit: String = "km/L",
    val fuelPrice: Double = 0.0,
    val currencySymbol: String = "₹",
    val liveFuelCost: Double = 0.0,
    val categoryName: String = "Business",
    val isIdle: Boolean = false,
    val idleSecondsRemaining: Long = 0L,
    val idleTimeoutTotalSeconds: Long = 480L,
    val isWalkingDetected: Boolean = false,
    val activityStatus: String = "Driving",
    val linearAccelMps2: Float = 0f,
    val rmsAccelMps2: Float = 0f
)

object TrackingStateManager {
    private val _trackingData = MutableStateFlow(LiveTrackingData())
    val trackingData: StateFlow<LiveTrackingData> = _trackingData.asStateFlow()

    fun update(transform: (LiveTrackingData) -> LiveTrackingData) {
        _trackingData.value = transform(_trackingData.value)
    }

    fun reset() {
        _trackingData.value = LiveTrackingData()
    }
}
