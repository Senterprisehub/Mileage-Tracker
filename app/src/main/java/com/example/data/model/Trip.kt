package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class TripCategory(val displayName: String, val colorHex: Long) {
    BUSINESS("Business", 0xFF059669), // Emerald
    PERSONAL("Personal", 0xFFD97706), // Amber
    MEDICAL("Medical", 0xFF0891B2),  // Cyan
    CHARITY("Charity", 0xFF7C3AED)   // Purple
}

@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["vehicleId"]), Index(value = ["startTime"])]
)
data class TripEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val vehicleId: String? = null,
    val category: String = TripCategory.BUSINESS.name,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val startLat: Double = 0.0,
    val startLng: Double = 0.0,
    val startAddress: String = "Start Location",
    val endLat: Double? = null,
    val endLng: Double? = null,
    val endAddress: String? = null,
    val startOdometer: Double? = null,
    val endOdometer: Double? = null,
    val distanceKm: Double = 0.0,
    val reimbursementAmount: Double = 0.0, // Business tax write-off
    val fuelCost: Double = 0.0,           // Fuel cost out-of-pocket
    val tolls: Double = 0.0,
    val parking: Double = 0.0,
    val maintenanceCost: Double = 0.0,     // Wear and tear
    val totalPersonalCost: Double = 0.0,   // Fuel + Maintenance + Tolls + Parking
    val notes: String = "",
    val isManual: Boolean = false
) {
    fun getParsedCategory(): TripCategory {
        return try {
            TripCategory.valueOf(category)
        } catch (_: Exception) {
            TripCategory.BUSINESS
        }
    }
}
