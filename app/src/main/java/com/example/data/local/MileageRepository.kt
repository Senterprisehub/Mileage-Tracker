package com.example.data.local

import android.content.Context
import android.location.Location
import com.example.data.model.AppSettingsEntity
import com.example.data.model.CountryProfile
import com.example.data.model.SavedPlaceEntity
import com.example.data.model.TripCategory
import com.example.data.model.TripEntity
import com.example.data.model.TripPointEntity
import com.example.data.model.VehicleEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.UUID

class MileageRepository(private val database: MileageDatabase) {

    private val vehicleDao = database.vehicleDao()
    private val tripDao = database.tripDao()
    private val tripPointDao = database.tripPointDao()
    private val savedPlaceDao = database.savedPlaceDao()
    private val settingsDao = database.settingsDao()

    // Vehicles
    fun getAllVehicles(): Flow<List<VehicleEntity>> = vehicleDao.getAllVehicles()

    suspend fun getVehicleById(id: String): VehicleEntity? = vehicleDao.getVehicleById(id)

    suspend fun getDefaultVehicle(): VehicleEntity? = vehicleDao.getDefaultVehicle()

    suspend fun insertVehicle(vehicle: VehicleEntity) {
        if (vehicle.isDefault) {
            vehicleDao.clearDefaultVehicles()
        }
        vehicleDao.insertVehicle(vehicle)
    }

    suspend fun updateVehicle(vehicle: VehicleEntity) {
        if (vehicle.isDefault) {
            vehicleDao.clearDefaultVehicles()
        }
        vehicleDao.updateVehicle(vehicle)
        recalculateTripsForVehicle(vehicle.id, vehicle.efficiencyVal, vehicle.maintenanceCostPerKm)
    }

    suspend fun recalculateTripsForVehicle(vehicleId: String, newEfficiency: Double, newMaintenanceCost: Double) {
        val trips = tripDao.getTripsByVehicleId(vehicleId)
        val settings = getSettings()
        val updatedTrips = trips.map { trip ->
            if (trip.endTime != null) {
                val eff = if (newEfficiency > 0.0) newEfficiency else 15.0
                val fuelCost = (trip.distanceKm / eff) * settings.currentFuelPrice
                val maintenanceCost = trip.distanceKm * newMaintenanceCost
                val totalCost = fuelCost + maintenanceCost + trip.tolls + trip.parking
                val reimbursement = if (trip.category == TripCategory.BUSINESS.name) {
                    trip.distanceKm * settings.standardTaxRatePerKm
                } else {
                    0.0
                }
                trip.copy(
                    fuelCost = fuelCost,
                    maintenanceCost = maintenanceCost,
                    totalPersonalCost = totalCost,
                    reimbursementAmount = reimbursement
                )
            } else {
                trip
            }
        }
        if (updatedTrips.isNotEmpty()) {
            tripDao.updateTrips(updatedTrips)
        }
    }

    suspend fun deleteVehicle(vehicle: VehicleEntity) = vehicleDao.deleteVehicle(vehicle)

    suspend fun setDefaultVehicle(vehicleId: String) {
        vehicleDao.clearDefaultVehicles()
        vehicleDao.setDefaultVehicle(vehicleId)
    }

    // Trips
    fun getAllCompletedTrips(): Flow<List<TripEntity>> = tripDao.getAllCompletedTrips()

    fun getActiveTripFlow(): Flow<TripEntity?> = tripDao.getActiveTripFlow()

    suspend fun getActiveTrip(): TripEntity? = tripDao.getActiveTrip()

    suspend fun getTripById(id: String): TripEntity? = tripDao.getTripById(id)

    suspend fun insertTrip(trip: TripEntity) = tripDao.insertTrip(trip)

    suspend fun updateTrip(trip: TripEntity) = tripDao.updateTrip(trip)

    suspend fun deleteTripById(id: String) = tripDao.deleteTripById(id)

    // Start a new trip
    suspend fun startTrip(
        vehicleId: String?,
        category: TripCategory,
        startTime: Long,
        startLat: Double,
        startLng: Double,
        startAddress: String,
        startOdometer: Double? = null,
        isManual: Boolean = false
    ): String {
        val tripId = UUID.randomUUID().toString()
        val newTrip = TripEntity(
            id = tripId,
            vehicleId = vehicleId,
            category = category.name,
            startTime = startTime,
            startLat = startLat,
            startLng = startLng,
            startAddress = startAddress,
            startOdometer = startOdometer,
            isManual = isManual
        )
        tripDao.insertTrip(newTrip)
        return tripId
    }

    // Add a GPS coordinate to the trip
    suspend fun addTripPoint(tripId: String, lat: Double, lng: Double, speed: Float, timestamp: Long) {
        tripPointDao.insertPoint(
            TripPointEntity(
                tripId = tripId,
                latitude = lat,
                longitude = lng,
                speed = speed,
                timestamp = timestamp
            )
        )
    }

    fun getPointsForTrip(tripId: String): Flow<List<TripPointEntity>> =
        tripPointDao.getPointsForTrip(tripId)

    suspend fun getPointsForTripDirect(tripId: String): List<TripPointEntity> =
        tripPointDao.getPointsForTripDirect(tripId)

    // Complete active trip and compute financial figures
    suspend fun completeTrip(
        tripId: String,
        endTime: Long,
        endLat: Double,
        endLng: Double,
        endAddress: String,
        distanceKm: Double,
        tolls: Double = 0.0,
        parking: Double = 0.0,
        endOdometer: Double? = null,
        notes: String = ""
    ) {
        val trip = tripDao.getTripById(tripId) ?: return
        val settings = settingsDao.getSettings() ?: AppSettingsEntity()
        val vehicle = trip.vehicleId?.let { vehicleDao.getVehicleById(it) } ?: vehicleDao.getDefaultVehicle()

        val efficiency = vehicle?.efficiencyVal?.takeIf { it > 0.0 } ?: 15.0
        val wearRate = vehicle?.maintenanceCostPerKm ?: 0.50
        val fuelType = vehicle?.fuelType ?: settings.selectedFuelType
        val todayRate = com.example.data.fuel.LiveFuelPriceManager.getTodayFuelRate(
            settings.countryCode,
            settings.currentCity,
            fuelType
        )
        val fuelPrice = if (settings.selectedFuelType.equals(fuelType, ignoreCase = true) && settings.currentFuelPrice > 0.0) {
            settings.currentFuelPrice
        } else {
            todayRate.price
        }
        val taxRate = settings.standardTaxRatePerKm

        // Mathematical Expense & Deduction calculations
        // Fuel Cost = (Distance / Efficiency) * Fuel Price
        val fuelCost = (distanceKm / efficiency) * fuelPrice
        // Maintenance / Wear Cost = Distance * Maintenance Cost per Km
        val maintenanceCost = distanceKm * wearRate
        // Total Personal Out-of-Pocket Cost
        val totalPersonalCost = fuelCost + maintenanceCost + tolls + parking
        // Business Tax Write-off / Reimbursement
        val reimbursementAmount = if (trip.category == TripCategory.BUSINESS.name) {
            distanceKm * taxRate
        } else {
            0.0
        }

        val completedTrip = trip.copy(
            endTime = endTime,
            endLat = endLat,
            endLng = endLng,
            endAddress = endAddress,
            endOdometer = endOdometer,
            distanceKm = distanceKm,
            reimbursementAmount = reimbursementAmount,
            fuelCost = fuelCost,
            tolls = tolls,
            parking = parking,
            maintenanceCost = maintenanceCost,
            totalPersonalCost = totalPersonalCost,
            notes = notes.ifEmpty { trip.notes }
        )
        tripDao.updateTrip(completedTrip)
    }

    // Reassign a saved trip to a different vehicle and immediately recalculate fuel and personal costs
    suspend fun updateTripVehicle(tripId: String, newVehicleId: String): TripEntity? {
        val trip = tripDao.getTripById(tripId) ?: return null
        val vehicle = vehicleDao.getVehicleById(newVehicleId) ?: return null
        val settings = settingsDao.getSettings() ?: AppSettingsEntity()
        val updatedTrip = calculateTripWithVehicle(trip, vehicle, settings)
        tripDao.updateTrip(updatedTrip)
        return updatedTrip
    }

    companion object {
        fun calculateTripWithVehicle(
            trip: TripEntity,
            vehicle: VehicleEntity,
            settings: AppSettingsEntity
        ): TripEntity {
            val efficiency = if (vehicle.efficiencyVal > 0.0) vehicle.efficiencyVal else 14.0
            val wearRate = vehicle.maintenanceCostPerKm
            val fuelType = vehicle.fuelType
            val todayRate = com.example.data.fuel.LiveFuelPriceManager.getTodayFuelRate(
                settings.countryCode,
                settings.currentCity,
                fuelType
            )
            val fuelPrice = if (settings.selectedFuelType.equals(fuelType, ignoreCase = true) && settings.currentFuelPrice > 0.0) {
                settings.currentFuelPrice
            } else {
                todayRate.price
            }
            val fuelCost = (trip.distanceKm / efficiency) * fuelPrice
            val maintenanceCost = trip.distanceKm * wearRate
            val totalPersonalCost = fuelCost + maintenanceCost + trip.tolls + trip.parking
            val reimbursementAmount = if (trip.category == TripCategory.BUSINESS.name) {
                trip.distanceKm * settings.standardTaxRatePerKm
            } else {
                0.0
            }
            return trip.copy(
                vehicleId = vehicle.id,
                fuelCost = fuelCost,
                maintenanceCost = maintenanceCost,
                totalPersonalCost = totalPersonalCost,
                reimbursementAmount = reimbursementAmount
            )
        }
    }

    // Saved places
    fun getAllPlaces(): Flow<List<SavedPlaceEntity>> = savedPlaceDao.getAllPlaces()

    suspend fun insertPlace(place: SavedPlaceEntity) = savedPlaceDao.insertPlace(place)

    suspend fun deletePlace(place: SavedPlaceEntity) = savedPlaceDao.deletePlace(place)

    suspend fun findMatchingPlaceName(lat: Double, lng: Double): String? {
        val places = savedPlaceDao.getAllPlacesList()
        val results = FloatArray(1)
        for (place in places) {
            Location.distanceBetween(lat, lng, place.latitude, place.longitude, results)
            if (results[0] <= place.radiusMeters) {
                return place.name
            }
        }
        return null
    }

    // Settings
    fun getSettingsFlow(): Flow<AppSettingsEntity?> = settingsDao.getSettingsFlow()

    suspend fun getSettings(): AppSettingsEntity = settingsDao.getSettings() ?: AppSettingsEntity().also {
        settingsDao.insertOrUpdate(it)
    }

    suspend fun updateSettings(settings: AppSettingsEntity) = settingsDao.insertOrUpdate(settings)

    suspend fun applyCountryProfile(profile: CountryProfile) {
        val current = getSettings()
        val cityName = profile.cities.firstOrNull()?.cityName ?: "Capital"
        val todayRate = com.example.data.fuel.LiveFuelPriceManager.getTodayFuelRate(
            profile.code,
            cityName,
            current.selectedFuelType
        )
        val updated = current.copy(
            countryCode = profile.code,
            countryName = profile.name,
            currencyCode = profile.currencyCode,
            currencySymbol = profile.currencySymbol,
            unitDistance = profile.unitDistance,
            currentFuelPrice = todayRate.price,
            currentCity = cityName,
            fuelRateUnit = todayRate.unit,
            lastFuelRateFetchTime = System.currentTimeMillis(),
            lastFuelRateSource = todayRate.source,
            standardTaxRatePerKm = profile.standardTaxRatePerDistance
        )
        settingsDao.insertOrUpdate(updated)
    }

    // Auto classify category based on 12-hour day-wise schedule
    suspend fun resolveCategoryBySchedule(timestamp: Long): TripCategory {
        val settings = getSettings()
        if (!settings.workHoursEnabled) {
            return TripCategory.BUSINESS
        }
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        // Calendar: SUNDAY=1, MONDAY=2, ... SATURDAY=7
        val dayIndex = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
        val schedules = settings.getParsedSchedules()
        val todaySchedule = schedules.find { it.dayIndex == dayIndex } ?: schedules.getOrNull(dayIndex)
        if (todaySchedule != null && todaySchedule.enabled) {
            val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val startMinutes = todaySchedule.toStartMinutes24()
            val endMinutes = todaySchedule.toEndMinutes24()
            if (currentMinutes in startMinutes..endMinutes) {
                return try {
                    TripCategory.valueOf(todaySchedule.category)
                } catch (_: Exception) {
                    TripCategory.BUSINESS
                }
            }
        }
        return TripCategory.PERSONAL
    }
}
