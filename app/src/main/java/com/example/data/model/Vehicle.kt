package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class VehicleType(val displayName: String, val defaultEfficiency: Double, val efficiencyUnit: String) {
    CAR("Car", 14.0, "km/L"),
    MOTORCYCLE("Motorcycle", 45.0, "km/L"),
    SCOOTER("Scooter", 50.0, "km/L"),
    SUV("SUV", 10.5, "km/L"),
    VAN("Van", 9.0, "km/L"),
    TRUCK("Truck", 6.5, "km/L")
}

enum class FuelType(val displayName: String, val unitLabel: String) {
    PETROL("Petrol", "L"),
    GASOLINE("Gasoline", "gal"),
    DIESEL("Diesel", "L"),
    EV("EV (Electric)", "kWh"),
    HYBRID("Hybrid", "L"),
    CNG("CNG", "kg")
}

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val vehicleType: String = VehicleType.CAR.name,
    val fuelType: String = FuelType.PETROL.name,
    val efficiencyVal: Double = 14.0, // km/L or km/kWh
    val maintenanceCostPerKm: Double = 0.50, // wear and tear cost per km
    val isDefault: Boolean = false
) {
    fun getParsedVehicleType(): VehicleType {
        return try {
            VehicleType.valueOf(vehicleType)
        } catch (_: Exception) {
            VehicleType.CAR
        }
    }

    fun getParsedFuelType(): FuelType {
        return try {
            FuelType.valueOf(fuelType)
        } catch (_: Exception) {
            FuelType.PETROL
        }
    }
}
