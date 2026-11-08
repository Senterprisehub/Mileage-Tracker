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
import kotlinx.coroutines.flow.first
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

    /**
     * Updates post-trip details (addresses, tolls, parking, odometer readings, notes) and
     * automatically recalculates fuel cost, maintenance cost, total personal out-of-pocket cost,
     * and business tax deduction.
     */
    suspend fun updateTripDetails(
        tripId: String,
        startAddress: String,
        endAddress: String,
        tolls: Double,
        parking: Double,
        startOdometer: Double?,
        endOdometer: Double?,
        notes: String
    ): TripEntity? {
        val trip = tripDao.getTripById(tripId) ?: return null
        val settings = settingsDao.getSettings() ?: AppSettingsEntity()
        val vehicle = trip.vehicleId?.let { vehicleDao.getVehicleById(it) } ?: vehicleDao.getDefaultVehicle()

        val validTolls = tolls.coerceAtLeast(0.0)
        val validParking = parking.coerceAtLeast(0.0)

        // If both start and end odometer are entered and end > start, update distanceKm accordingly
        val resolvedDistanceKm = if (startOdometer != null && endOdometer != null && endOdometer > startOdometer) {
            (endOdometer - startOdometer).coerceAtLeast(0.0)
        } else {
            trip.distanceKm
        }

        val efficiency = vehicle?.efficiencyVal?.takeIf { it > 0.0 } ?: 14.0
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

        val fuelCost = (resolvedDistanceKm / efficiency) * fuelPrice
        val maintenanceCost = resolvedDistanceKm * wearRate
        val totalPersonalCost = fuelCost + maintenanceCost + validTolls + validParking
        val reimbursementAmount = if (trip.category == TripCategory.BUSINESS.name) {
            resolvedDistanceKm * settings.standardTaxRatePerKm
        } else {
            0.0
        }

        val updatedTrip = trip.copy(
            startAddress = startAddress.trim().ifBlank { trip.startAddress },
            endAddress = endAddress.trim().ifBlank { trip.endAddress ?: "Destination" },
            tolls = validTolls,
            parking = validParking,
            startOdometer = startOdometer,
            endOdometer = endOdometer,
            distanceKm = resolvedDistanceKm,
            fuelCost = fuelCost,
            maintenanceCost = maintenanceCost,
            totalPersonalCost = totalPersonalCost,
            reimbursementAmount = reimbursementAmount,
            notes = notes.trim()
        )
        tripDao.updateTrip(updatedTrip)
        return updatedTrip
    }

    /**
     * Exports a complete JSON backup of all Vehicles, Saved Places, App Settings, and Completed Trips.
     */
    suspend fun exportFullBackupJson(): String {
        val vehiclesList = vehicleDao.getAllVehicles().first()
        val placesList = savedPlaceDao.getAllPlacesList()
        val currentSettings = getSettings()
        val tripsList = tripDao.getAllCompletedTrips().first()

        val root = org.json.JSONObject()
        root.put("backupVersion", 1)
        root.put("appVersion", "3.1")
        root.put("exportedAtMillis", System.currentTimeMillis())

        val settingsJson = org.json.JSONObject().apply {
            put("countryCode", currentSettings.countryCode)
            put("countryName", currentSettings.countryName)
            put("currencyCode", currentSettings.currencyCode)
            put("currencySymbol", currentSettings.currencySymbol)
            put("unitDistance", currentSettings.unitDistance)
            put("selectedFuelType", currentSettings.selectedFuelType)
            put("currentFuelPrice", currentSettings.currentFuelPrice)
            put("fuelRateUnit", currentSettings.fuelRateUnit)
            put("currentCity", currentSettings.currentCity)
            put("standardTaxRatePerKm", currentSettings.standardTaxRatePerKm)
            put("workHoursEnabled", currentSettings.workHoursEnabled)
            put("dayWiseScheduleJson", currentSettings.dayWiseScheduleJson)
            put("autoTrackingEnabled", currentSettings.autoTrackingEnabled)
            put("autoDriveDetectionSpeedKmh", currentSettings.autoDriveDetectionSpeedKmh)
            put("stopTimeoutMinutes", currentSettings.stopTimeoutMinutes)
        }
        root.put("settings", settingsJson)

        val vehiclesArray = org.json.JSONArray()
        for (v in vehiclesList) {
            vehiclesArray.put(
                org.json.JSONObject().apply {
                    put("id", v.id)
                    put("name", v.name)
                    put("vehicleType", v.vehicleType)
                    put("fuelType", v.fuelType)
                    put("efficiencyVal", v.efficiencyVal)
                    put("maintenanceCostPerKm", v.maintenanceCostPerKm)
                    put("isDefault", v.isDefault)
                }
            )
        }
        root.put("vehicles", vehiclesArray)

        val placesArray = org.json.JSONArray()
        for (p in placesList) {
            placesArray.put(
                org.json.JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("latitude", p.latitude)
                    put("longitude", p.longitude)
                    put("radiusMeters", p.radiusMeters.toDouble())
                }
            )
        }
        root.put("savedPlaces", placesArray)

        val tripsArray = org.json.JSONArray()
        for (t in tripsList) {
            tripsArray.put(
                org.json.JSONObject().apply {
                    put("id", t.id)
                    put("vehicleId", t.vehicleId ?: org.json.JSONObject.NULL)
                    put("category", t.category)
                    put("startTime", t.startTime)
                    put("endTime", t.endTime ?: org.json.JSONObject.NULL)
                    put("startLat", t.startLat)
                    put("startLng", t.startLng)
                    put("startAddress", t.startAddress)
                    put("endLat", t.endLat ?: org.json.JSONObject.NULL)
                    put("endLng", t.endLng ?: org.json.JSONObject.NULL)
                    put("endAddress", t.endAddress ?: org.json.JSONObject.NULL)
                    put("startOdometer", t.startOdometer ?: org.json.JSONObject.NULL)
                    put("endOdometer", t.endOdometer ?: org.json.JSONObject.NULL)
                    put("distanceKm", t.distanceKm)
                    put("reimbursementAmount", t.reimbursementAmount)
                    put("fuelCost", t.fuelCost)
                    put("tolls", t.tolls)
                    put("parking", t.parking)
                    put("maintenanceCost", t.maintenanceCost)
                    put("totalPersonalCost", t.totalPersonalCost)
                    put("notes", t.notes)
                    put("isManual", t.isManual)
                }
            )
        }
        root.put("trips", tripsArray)

        return root.toString(2)
    }

    /**
     * Restores Vehicles, Saved Places, Settings, and Completed Trips from a JSON backup string.
     */
    suspend fun restoreFullBackupJson(jsonString: String): String {
        val root = org.json.JSONObject(jsonString)
        if (!root.has("backupVersion")) {
            throw IllegalArgumentException("Invalid backup file format")
        }

        var vehiclesRestored = 0
        var placesRestored = 0
        var tripsRestored = 0

        root.optJSONObject("settings")?.let { sObj ->
            val existing = getSettings()
            val updated = existing.copy(
                countryCode = sObj.optString("countryCode", existing.countryCode),
                countryName = sObj.optString("countryName", existing.countryName),
                currencyCode = sObj.optString("currencyCode", existing.currencyCode),
                currencySymbol = sObj.optString("currencySymbol", existing.currencySymbol),
                unitDistance = sObj.optString("unitDistance", existing.unitDistance),
                selectedFuelType = sObj.optString("selectedFuelType", existing.selectedFuelType),
                currentFuelPrice = sObj.optDouble("currentFuelPrice", existing.currentFuelPrice),
                fuelRateUnit = sObj.optString("fuelRateUnit", existing.fuelRateUnit),
                currentCity = sObj.optString("currentCity", existing.currentCity),
                standardTaxRatePerKm = sObj.optDouble("standardTaxRatePerKm", existing.standardTaxRatePerKm),
                workHoursEnabled = sObj.optBoolean("workHoursEnabled", existing.workHoursEnabled),
                dayWiseScheduleJson = sObj.optString("dayWiseScheduleJson", existing.dayWiseScheduleJson),
                autoTrackingEnabled = sObj.optBoolean("autoTrackingEnabled", existing.autoTrackingEnabled),
                autoDriveDetectionSpeedKmh = sObj.optInt("autoDriveDetectionSpeedKmh", existing.autoDriveDetectionSpeedKmh),
                stopTimeoutMinutes = sObj.optInt("stopTimeoutMinutes", existing.stopTimeoutMinutes)
            )
            settingsDao.insertOrUpdate(updated)
        }

        root.optJSONArray("vehicles")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
                val vehicle = VehicleEntity(
                    id = id,
                    name = name,
                    vehicleType = obj.optString("vehicleType", "CAR"),
                    fuelType = obj.optString("fuelType", "PETROL"),
                    efficiencyVal = obj.optDouble("efficiencyVal", 14.0),
                    maintenanceCostPerKm = obj.optDouble("maintenanceCostPerKm", 0.50),
                    isDefault = obj.optBoolean("isDefault", false)
                )
                vehicleDao.insertVehicle(vehicle)
                vehiclesRestored++
            }
        }

        root.optJSONArray("savedPlaces")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
                val place = SavedPlaceEntity(
                    id = id,
                    name = name,
                    latitude = obj.optDouble("latitude", 0.0),
                    longitude = obj.optDouble("longitude", 0.0),
                    radiusMeters = obj.optDouble("radiusMeters", 150.0).toFloat()
                )
                savedPlaceDao.insertPlace(place)
                placesRestored++
            }
        }

        root.optJSONArray("trips")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                val vehicleId = if (obj.isNull("vehicleId")) null else obj.optString("vehicleId").takeIf { it.isNotBlank() }
                val endTime = if (obj.isNull("endTime")) System.currentTimeMillis() else obj.optLong("endTime")
                val endLat = if (obj.isNull("endLat")) null else obj.optDouble("endLat")
                val endLng = if (obj.isNull("endLng")) null else obj.optDouble("endLng")
                val endAddress = if (obj.isNull("endAddress")) null else obj.optString("endAddress")
                val startOdo = if (obj.isNull("startOdometer")) null else obj.optDouble("startOdometer")
                val endOdo = if (obj.isNull("endOdometer")) null else obj.optDouble("endOdometer")

                val trip = TripEntity(
                    id = id,
                    vehicleId = vehicleId,
                    category = obj.optString("category", TripCategory.BUSINESS.name),
                    startTime = obj.optLong("startTime", System.currentTimeMillis()),
                    endTime = endTime,
                    startLat = obj.optDouble("startLat", 0.0),
                    startLng = obj.optDouble("startLng", 0.0),
                    startAddress = obj.optString("startAddress", "Start Location"),
                    endLat = endLat,
                    endLng = endLng,
                    endAddress = endAddress,
                    startOdometer = startOdo,
                    endOdometer = endOdo,
                    distanceKm = obj.optDouble("distanceKm", 0.0),
                    reimbursementAmount = obj.optDouble("reimbursementAmount", 0.0),
                    fuelCost = obj.optDouble("fuelCost", 0.0),
                    tolls = obj.optDouble("tolls", 0.0),
                    parking = obj.optDouble("parking", 0.0),
                    maintenanceCost = obj.optDouble("maintenanceCost", 0.0),
                    totalPersonalCost = obj.optDouble("totalPersonalCost", 0.0),
                    notes = obj.optString("notes", ""),
                    isManual = obj.optBoolean("isManual", false)
                )
                tripDao.insertTrip(trip)
                tripsRestored++
            }
        }

        return "Restored $tripsRestored trips, $vehiclesRestored vehicles, and $placesRestored saved places."
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
