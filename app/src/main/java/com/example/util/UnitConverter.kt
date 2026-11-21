package com.example.util

import com.example.data.fuel.LiveFuelPriceManager
import com.example.data.model.AppSettingsEntity
import com.example.data.model.TripCategory
import java.util.Locale

/**
 * Central metric/imperial conversion and financial calculation layer.
 * Canonical database storage uses kilometers (`distanceKm`) so GPS paths remain unit-invariant,
 * while entry, display, fuel consumption, wear, and tax reimbursement convert to the active CountryProfile.
 */
object UnitConverter {

    const val KM_PER_MILE = 1.609344
    const val MILES_PER_KM = 1.0 / KM_PER_MILE

    fun isImperial(unitDistance: String?): Boolean =
        unitDistance.equals("mi", ignoreCase = true)

    fun toDisplayDistance(distanceKm: Double, unitDistance: String?): Double {
        return if (isImperial(unitDistance)) distanceKm * MILES_PER_KM else distanceKm
    }

    fun toCanonicalKm(displayDistance: Double, unitDistance: String?): Double {
        return if (isImperial(unitDistance)) displayDistance * KM_PER_MILE else displayDistance
    }

    fun toDisplaySpeed(speedKmh: Double, unitDistance: String?): Double {
        return if (isImperial(unitDistance)) speedKmh * MILES_PER_KM else speedKmh
    }

    fun speedUnitLabel(unitDistance: String?): String =
        if (isImperial(unitDistance)) "mph" else "km/h"

    fun fuelVolumeUnit(settings: AppSettingsEntity, fuelType: String = settings.selectedFuelType): String {
        val norm = LiveFuelPriceManager.normalizeFuelType(fuelType, settings.countryCode)
        return when (norm) {
            "EV" -> "kWh"
            "CNG" -> "kg"
            else -> if (isImperial(settings.unitDistance)) "gal" else "L"
        }
    }

    fun efficiencyUnitLabel(unitDistance: String?, fuelType: String?): String {
        val norm = fuelType?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val unitDist = if (isImperial(unitDistance)) "mi" else "km"
        return when {
            norm.contains("EV") || norm.contains("ELECTRIC") -> "$unitDist/kWh"
            norm.contains("CNG") -> "$unitDist/kg"
            isImperial(unitDistance) -> "MPG"
            else -> "$unitDist/L"
        }
    }

    fun calculateFuelConsumed(
        distanceKm: Double,
        efficiencyVal: Double,
        unitDistance: String?
    ): Double {
        if (efficiencyVal <= 0.0 || distanceKm <= 0.0) return 0.0
        val displayDistance = toDisplayDistance(distanceKm, unitDistance)
        return displayDistance / efficiencyVal
    }

    fun calculateFuelCost(
        distanceKm: Double,
        efficiencyVal: Double,
        fuelPrice: Double,
        unitDistance: String?
    ): Double {
        if (fuelPrice <= 0.0) return 0.0
        return calculateFuelConsumed(distanceKm, efficiencyVal, unitDistance) * fuelPrice
    }

    fun calculateMaintenanceCost(
        distanceKm: Double,
        wearRatePerDisplayUnit: Double,
        unitDistance: String?
    ): Double {
        if (wearRatePerDisplayUnit <= 0.0 || distanceKm <= 0.0) return 0.0
        return toDisplayDistance(distanceKm, unitDistance) * wearRatePerDisplayUnit
    }

    fun calculateReimbursement(
        distanceKm: Double,
        category: String,
        settings: AppSettingsEntity
    ): Double {
        if (!category.equals(TripCategory.BUSINESS.name, ignoreCase = true)) return 0.0
        if (distanceKm <= 0.0 || settings.standardTaxRatePerKm <= 0.0) return 0.0
        return toDisplayDistance(distanceKm, settings.unitDistance) * settings.standardTaxRatePerKm
    }

    /**
     * Formats duration in seconds as `MM:SS` under 1 hour and `HH:MM:SS` for 1 hour or longer.
     */
    fun formatDurationClock(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val hours = safe / 3600L
        val minutes = (safe % 3600L) / 60L
        val seconds = safe % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
