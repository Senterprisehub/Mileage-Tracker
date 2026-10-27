package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.fuel.LiveFuelPriceManager
import com.example.data.local.MileageDatabase
import com.example.data.local.MileageRepository
import com.example.data.model.AppSettingsEntity
import com.example.data.model.CountryProfile
import com.example.data.model.CountryRegistry
import com.example.data.model.FuelType
import com.example.data.model.SavedPlaceEntity
import com.example.data.model.TripCategory
import com.example.data.model.TripEntity
import com.example.data.model.VehicleEntity
import com.example.data.model.VehicleType
import com.example.service.AutoResumeManager
import com.example.service.LiveTrackingData
import com.example.service.MileageTrackingService
import com.example.service.TrackingStateManager
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MileageViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MileageRepository(MileageDatabase.getInstance(application))

    init {
        // Continuous in-app monitor: automatically resumes tracking the exact moment paused interval expires
        viewModelScope.launch {
            while (true) {
                delay(3000L)
                try {
                    val current = repository.getSettings()
                    val pausedUntil = current.autoTrackingPausedUntil
                    if (pausedUntil != null && pausedUntil != -1L && System.currentTimeMillis() >= pausedUntil) {
                        resumeAutoTracking()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val liveTracking: StateFlow<LiveTrackingData> = TrackingStateManager.trackingData

    val vehicles: StateFlow<List<VehicleEntity>> = repository.getAllVehicles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettingsEntity> = repository.getSettingsFlow()
        .map { it ?: AppSettingsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsEntity())

    val savedPlaces: StateFlow<List<SavedPlaceEntity>> = repository.getAllPlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTrips: StateFlow<List<TripEntity>> = repository.getAllCompletedTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedVehicleId = MutableStateFlow<String?>(null)
    val selectedVehicleId: StateFlow<String?> = _selectedVehicleId.asStateFlow()

    private val _isFetchingFuelRate = MutableStateFlow(false)
    val isFetchingFuelRate: StateFlow<Boolean> = _isFetchingFuelRate.asStateFlow()

    // Day-wise grouped trips for the dashboard
    val dayWiseGroups: StateFlow<List<DayTripsGroup>> = completedTrips.combine(settings) { trips, _ ->
        groupTripsByDay(trips)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val currentCal = Calendar.getInstance()
    private val _selectedMonthFilter = MutableStateFlow<MonthFilter>(
        MonthFilter.Month(currentCal.get(Calendar.YEAR), currentCal.get(Calendar.MONTH))
    )
    val selectedMonthFilter: StateFlow<MonthFilter> = _selectedMonthFilter.asStateFlow()

    // Available months list for month picker (includes All Time)
    val availableMonths: StateFlow<List<MonthFilter>> = completedTrips.map { trips ->
        val cal = Calendar.getInstance()
        val nowYear = cal.get(Calendar.YEAR)
        val nowMonth = cal.get(Calendar.MONTH)

        val set = mutableSetOf<Pair<Int, Int>>()
        set.add(Pair(nowYear, nowMonth))
        trips.forEach {
            cal.timeInMillis = it.startTime
            set.add(Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)))
        }
        val sorted = set.sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })
        val list = mutableListOf<MonthFilter>()
        list.add(MonthFilter.AllTime)
        sorted.forEach { (y, m) ->
            list.add(MonthFilter.Month(y, m))
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(MonthFilter.AllTime))

    // Monthly summary metrics based on selected month filter
    val monthlyMetrics: StateFlow<MonthlyMetrics> = combine(
        completedTrips,
        vehicles,
        settings,
        _selectedMonthFilter
    ) { trips, vehiList, sett, filter ->
        calculateMonthlyMetrics(trips, vehiList, sett, filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthlyMetrics())

    val currentPeriodFilteredTrips: StateFlow<List<TripEntity>> = combine(completedTrips, _selectedMonthFilter) { trips, filter ->
        when (filter) {
            is MonthFilter.AllTime -> trips
            is MonthFilter.Month -> {
                val cal = Calendar.getInstance()
                trips.filter { trip ->
                    cal.timeInMillis = trip.startTime
                    cal.get(Calendar.MONTH) == filter.month && cal.get(Calendar.YEAR) == filter.year
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectPreviousMonth() {
        val current = _selectedMonthFilter.value
        when (current) {
            is MonthFilter.AllTime -> {
                val cal = Calendar.getInstance()
                _selectedMonthFilter.value = MonthFilter.Month(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
            }
            is MonthFilter.Month -> {
                val newMonth = if (current.month == 0) 11 else current.month - 1
                val newYear = if (current.month == 0) current.year - 1 else current.year
                _selectedMonthFilter.value = MonthFilter.Month(newYear, newMonth)
            }
        }
    }

    fun selectNextMonth() {
        val current = _selectedMonthFilter.value
        val nowCal = Calendar.getInstance()
        val nowYear = nowCal.get(Calendar.YEAR)
        val nowMonth = nowCal.get(Calendar.MONTH)

        when (current) {
            is MonthFilter.AllTime -> { /* Already at all time */ }
            is MonthFilter.Month -> {
                if (current.year < nowYear || (current.year == nowYear && current.month < nowMonth)) {
                    val newMonth = if (current.month == 11) 0 else current.month + 1
                    val newYear = if (current.month == 11) current.year + 1 else current.year
                    _selectedMonthFilter.value = MonthFilter.Month(newYear, newMonth)
                }
            }
        }
    }

    fun selectMonthFilter(filter: MonthFilter) {
        _selectedMonthFilter.value = filter
    }

    fun setSelectedVehicle(vehicleId: String) {
        _selectedVehicleId.value = vehicleId
        viewModelScope.launch {
            val vehicle = repository.getVehicleById(vehicleId)
            if (vehicle != null) {
                updateFuelType(vehicle.fuelType)
            }
        }
    }

    fun startDrive(context: Context, category: TripCategory? = null) {
        viewModelScope.launch {
            val defaultVehicle = repository.getDefaultVehicle()
            val chosenVehicleId = _selectedVehicleId.value ?: defaultVehicle?.id
            val vehicle = chosenVehicleId?.let { repository.getVehicleById(it) } ?: defaultVehicle
            val vehicleName = vehicle?.name ?: "Default Vehicle"

            val resolvedCategory = category ?: repository.resolveCategoryBySchedule(System.currentTimeMillis())

            MileageTrackingService.start(
                context = context,
                vehicleId = chosenVehicleId,
                categoryName = resolvedCategory.name,
                vehicleName = vehicleName
            )
        }
    }

    fun stopDrive(
        context: Context,
        tolls: Double = 0.0,
        parking: Double = 0.0,
        endOdometer: Double? = null,
        notes: String = ""
    ) {
        MileageTrackingService.stop(context, tolls, parking, endOdometer, notes)
    }

    fun addManualTrip(
        vehicleId: String?,
        category: TripCategory,
        startTime: Long,
        endTime: Long,
        startAddress: String,
        endAddress: String,
        distanceKm: Double,
        tolls: Double = 0.0,
        parking: Double = 0.0,
        startOdometer: Double? = null,
        endOdometer: Double? = null,
        notes: String = ""
    ) {
        viewModelScope.launch {
            val tripId = repository.startTrip(
                vehicleId = vehicleId,
                category = category,
                startTime = startTime,
                startLat = 0.0,
                startLng = 0.0,
                startAddress = startAddress,
                startOdometer = startOdometer,
                isManual = true
            )
            repository.completeTrip(
                tripId = tripId,
                endTime = endTime,
                endLat = 0.0,
                endLng = 0.0,
                endAddress = endAddress,
                distanceKm = distanceKm,
                tolls = tolls,
                parking = parking,
                endOdometer = endOdometer,
                notes = notes
            )
        }
    }

    fun updateTripCategory(tripId: String, newCategory: TripCategory) {
        viewModelScope.launch {
            val trip = repository.getTripById(tripId) ?: return@launch
            val appSettings = repository.getSettings()
            val reimbursement = if (newCategory == TripCategory.BUSINESS) {
                trip.distanceKm * appSettings.standardTaxRatePerKm
            } else {
                0.0
            }
            repository.updateTrip(
                trip.copy(
                    category = newCategory.name,
                    reimbursementAmount = reimbursement
                )
            )
        }
    }

    fun updateTripVehicle(
        tripId: String,
        newVehicleId: String,
        onUpdated: (TripEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            val updated = repository.updateTripVehicle(tripId, newVehicleId)
            if (updated != null) {
                onUpdated(updated)
            }
        }
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch {
            repository.deleteTripById(tripId)
        }
    }

    init {
        viewModelScope.launch {
            vehicles.collect { list ->
                if (_selectedVehicleId.value == null && list.isNotEmpty()) {
                    val def = list.find { it.isDefault } ?: list.first()
                    _selectedVehicleId.value = def.id
                }
            }
        }
        // Fetch live fuel rate from www.goodreturns.in on app launch
        refreshLiveFuelPrice()
    }

    // Vehicle operations
    fun addVehicle(
        name: String,
        vehicleType: VehicleType,
        fuelType: FuelType,
        efficiencyVal: Double,
        maintenanceCostPerKm: Double,
        isDefault: Boolean
    ) {
        viewModelScope.launch {
            val currentVehicles = vehicles.value
            val isFirstVehicle = currentVehicles.isEmpty()
            val shouldBeDefault = isDefault || isFirstVehicle
            val newVehicle = VehicleEntity(
                name = name,
                vehicleType = vehicleType.name,
                fuelType = fuelType.name,
                efficiencyVal = efficiencyVal,
                maintenanceCostPerKm = maintenanceCostPerKm,
                isDefault = shouldBeDefault
            )
            repository.insertVehicle(newVehicle)
            if (shouldBeDefault) {
                _selectedVehicleId.value = newVehicle.id
                updateFuelType(fuelType.name)
            }
        }
    }

    fun updateVehicle(vehicle: VehicleEntity) {
        viewModelScope.launch {
            repository.updateVehicle(vehicle)
            if (vehicle.id == _selectedVehicleId.value || vehicle.isDefault) {
                updateFuelType(vehicle.fuelType)
            }
        }
    }

    fun deleteVehicle(vehicle: VehicleEntity) {
        viewModelScope.launch {
            repository.deleteVehicle(vehicle)
        }
    }

    fun setDefaultVehicle(vehicleId: String) {
        viewModelScope.launch {
            repository.setDefaultVehicle(vehicleId)
            _selectedVehicleId.value = vehicleId
            val vehicle = repository.getVehicleById(vehicleId)
            if (vehicle != null) {
                updateFuelType(vehicle.fuelType)
            }
        }
    }

    // Saved places
    fun addSavedPlace(name: String, latitude: Double, longitude: Double, radiusMeters: Float = 150f) {
        viewModelScope.launch {
            repository.insertPlace(
                SavedPlaceEntity(
                    name = name,
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = radiusMeters
                )
            )
        }
    }

    fun deleteSavedPlace(place: SavedPlaceEntity) {
        viewModelScope.launch {
            repository.deletePlace(place)
        }
    }

    // Settings & Country
    fun applyCountry(profile: CountryProfile) {
        viewModelScope.launch {
            repository.applyCountryProfile(profile)
        }
    }

    fun updateFuelType(fuelType: String) {
        viewModelScope.launch {
            _isFetchingFuelRate.value = true
            try {
                val current = repository.getSettings()
                val liveRate = LiveFuelPriceManager.fetchLiveFuelRate(
                    countryCode = current.countryCode,
                    cityName = current.currentCity,
                    fuelType = fuelType
                )
                repository.updateSettings(
                    current.copy(
                        selectedFuelType = liveRate.fuelType,
                        currentFuelPrice = liveRate.price,
                        fuelRateUnit = liveRate.unit,
                        lastFuelRateFetchTime = System.currentTimeMillis(),
                        lastFuelRateSource = liveRate.source
                    )
                )
            } finally {
                _isFetchingFuelRate.value = false
            }
        }
    }

    fun updateCurrentCity(cityName: String) {
        viewModelScope.launch {
            _isFetchingFuelRate.value = true
            try {
                val current = repository.getSettings()
                val liveRate = LiveFuelPriceManager.fetchLiveFuelRate(
                    countryCode = current.countryCode,
                    cityName = cityName,
                    fuelType = current.selectedFuelType
                )
                repository.updateSettings(
                    current.copy(
                        currentCity = liveRate.cityName,
                        currentFuelPrice = liveRate.price,
                        fuelRateUnit = liveRate.unit,
                        lastFuelRateFetchTime = System.currentTimeMillis(),
                        lastFuelRateSource = liveRate.source
                    )
                )
            } finally {
                _isFetchingFuelRate.value = false
            }
        }
    }

    fun updateFuelPrice(price: Double) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.updateSettings(current.copy(currentFuelPrice = price, lastFuelRateSource = "Custom Manual Rate"))
        }
    }

    fun refreshLiveFuelPrice() {
        viewModelScope.launch {
            _isFetchingFuelRate.value = true
            try {
                val current = repository.getSettings()
                val liveRate = LiveFuelPriceManager.fetchLiveFuelRate(
                    countryCode = current.countryCode,
                    cityName = current.currentCity,
                    fuelType = current.selectedFuelType
                )
                repository.updateSettings(
                    current.copy(
                        currentFuelPrice = liveRate.price,
                        fuelRateUnit = liveRate.unit,
                        lastFuelRateFetchTime = System.currentTimeMillis(),
                        lastFuelRateSource = liveRate.source
                    )
                )
            } finally {
                _isFetchingFuelRate.value = false
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun detectLocationAndRefreshRate(context: Context, onResult: (String) -> Unit = {}) {
        viewModelScope.launch {
            _isFetchingFuelRate.value = true
            val current = repository.getSettings()
            try {
                val fusedClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                val cts = CancellationTokenSource()

                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).addOnSuccessListener { loc ->
                    viewModelScope.launch {
                        try {
                            var actualLoc = loc
                            if (actualLoc == null) {
                                // Fallback 1: check LocationManager GPS and Network providers
                                val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                                actualLoc = locManager?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                                    ?: locManager?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                            }

                            if (actualLoc != null) {
                                val detectedCity = LiveFuelPriceManager.resolveCityFromLocation(context, actualLoc.latitude, actualLoc.longitude)
                                val targetCity = detectedCity ?: current.currentCity
                                val liveRate = LiveFuelPriceManager.fetchLiveFuelRate(
                                    countryCode = current.countryCode,
                                    cityName = targetCity,
                                    fuelType = current.selectedFuelType
                                )

                                repository.updateSettings(
                                    current.copy(
                                        currentCity = liveRate.cityName,
                                        currentFuelPrice = liveRate.price,
                                        fuelRateUnit = liveRate.unit,
                                        lastFuelRateFetchTime = System.currentTimeMillis(),
                                        lastFuelRateSource = liveRate.source
                                    )
                                )
                                val statusTag = if (liveRate.isLiveFetched) "Live from GoodReturns.in" else "GoodReturns.in"
                                val cityTag = if (detectedCity != null) "Detected: $detectedCity" else "City: ${liveRate.cityName}"
                                val msg = "$cityTag • Today's ${liveRate.fuelType} rate: ${current.currencySymbol}${liveRate.price} ${liveRate.unit} ($statusTag)"
                                onResult(msg)
                            } else {
                                // If still null, try fusedClient.lastLocation
                                fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
                                    viewModelScope.launch {
                                        try {
                                            if (lastLoc != null) {
                                                val detectedCity = LiveFuelPriceManager.resolveCityFromLocation(context, lastLoc.latitude, lastLoc.longitude)
                                                val targetCity = detectedCity ?: current.currentCity
                                                val liveRate = LiveFuelPriceManager.fetchLiveFuelRate(
                                                    countryCode = current.countryCode,
                                                    cityName = targetCity,
                                                    fuelType = current.selectedFuelType
                                                )
                                                repository.updateSettings(
                                                    current.copy(
                                                        currentCity = liveRate.cityName,
                                                        currentFuelPrice = liveRate.price,
                                                        fuelRateUnit = liveRate.unit,
                                                        lastFuelRateFetchTime = System.currentTimeMillis(),
                                                        lastFuelRateSource = liveRate.source
                                                    )
                                                )
                                                val statusTag = if (liveRate.isLiveFetched) "Live from GoodReturns.in" else "GoodReturns.in"
                                                val msg = "Detected: ${liveRate.cityName} • Today's ${liveRate.fuelType}: ${current.currencySymbol}${liveRate.price} ${liveRate.unit} ($statusTag)"
                                                onResult(msg)
                                            } else {
                                                onResult("GPS signal unavailable. Please ensure device location is enabled.")
                                            }
                                        } finally {
                                            _isFetchingFuelRate.value = false
                                        }
                                    }
                                }.addOnFailureListener {
                                    onResult("GPS location request failed. Please check location permissions.")
                                    _isFetchingFuelRate.value = false
                                }
                                return@launch
                            }
                        } finally {
                            _isFetchingFuelRate.value = false
                        }
                    }
                }.addOnFailureListener { err ->
                    viewModelScope.launch {
                        try {
                            onResult("GPS Location error: ${err.localizedMessage ?: "Unknown error"}")
                        } finally {
                            _isFetchingFuelRate.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                try {
                    onResult("Error detecting location: ${e.localizedMessage}")
                } finally {
                    _isFetchingFuelRate.value = false
                }
            }
        }
    }

    fun updateGeneralSettings(newSettings: AppSettingsEntity) {
        viewModelScope.launch {
            val old = repository.getSettings()
            repository.updateSettings(newSettings)
            if (old.autoTrackingPausedUntil != newSettings.autoTrackingPausedUntil) {
                if (newSettings.autoTrackingPausedUntil != null && newSettings.autoTrackingPausedUntil > 0) {
                    AutoResumeManager.scheduleResume(getApplication(), newSettings.autoTrackingPausedUntil)
                } else if (newSettings.autoTrackingPausedUntil == null) {
                    AutoResumeManager.cancelPendingResume(getApplication())
                    if (newSettings.autoTrackingEnabled) {
                        MileageTrackingService.startMonitoring(getApplication())
                    }
                }
            }
        }
    }

    fun pauseAutoTracking(untilMillis: Long) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.updateSettings(current.copy(autoTrackingPausedUntil = untilMillis))
            if (untilMillis > 0) {
                AutoResumeManager.scheduleResume(getApplication(), untilMillis)
            }
        }
    }

    fun resumeAutoTracking() {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.updateSettings(current.copy(autoTrackingPausedUntil = null))
            AutoResumeManager.cancelPendingResume(getApplication())
            if (current.autoTrackingEnabled) {
                MileageTrackingService.startMonitoring(getApplication())
            }
        }
    }

    fun updateAutoDetectionSpeed(speedKmh: Int) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.updateSettings(current.copy(autoDriveDetectionSpeedKmh = speedKmh.coerceIn(1, 15)))
        }
    }

    fun updateStopTimeoutMinutes(minutes: Int) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.updateSettings(current.copy(stopTimeoutMinutes = minutes.coerceIn(3, 30)))
        }
    }

    fun updateDaySchedules(schedules: com.example.data.model.DaySchedule) {
        // Overload if needed
    }

    fun updateDaySchedulesList(schedules: List<com.example.data.model.DaySchedule>) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.updateSettings(current.copy(dayWiseScheduleJson = com.example.data.model.ScheduleSerializer.toJson(schedules)))
        }
    }

    // CSV Report Generator
    fun generateCsvReport(): String {
        val tripsList = completedTrips.value
        val appSettings = settings.value
        val sb = StringBuilder()
        sb.append("Date,Start Time,End Time,Vehicle,Category,Start Location,End Location,Distance (${appSettings.unitDistance}),Fuel Cost (${appSettings.currencySymbol}),Maintenance Cost (${appSettings.currencySymbol}),Tolls (${appSettings.currencySymbol}),Parking (${appSettings.currencySymbol}),Total Personal Cost (${appSettings.currencySymbol}),Tax Deduction (${appSettings.currencySymbol}),Notes\n")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

        for (trip in tripsList) {
            val dateStr = dateFormat.format(Date(trip.startTime))
            val startStr = timeFormat.format(Date(trip.startTime))
            val endStr = trip.endTime?.let { timeFormat.format(Date(it)) } ?: ""
            val vehicle = vehicles.value.find { it.id == trip.vehicleId }?.name ?: "Unknown"

            sb.append("\"$dateStr\",")
            sb.append("\"$startStr\",")
            sb.append("\"$endStr\",")
            sb.append("\"$vehicle\",")
            sb.append("\"${trip.category}\",")
            sb.append("\"${trip.startAddress.replace("\"", "\"\"")}\",")
            sb.append("\"${(trip.endAddress ?: "").replace("\"", "\"\"")}\",")
            sb.append("${String.format(Locale.US, "%.2f", trip.distanceKm)},")
            sb.append("${String.format(Locale.US, "%.2f", trip.fuelCost)},")
            sb.append("${String.format(Locale.US, "%.2f", trip.maintenanceCost)},")
            sb.append("${String.format(Locale.US, "%.2f", trip.tolls)},")
            sb.append("${String.format(Locale.US, "%.2f", trip.parking)},")
            sb.append("${String.format(Locale.US, "%.2f", trip.totalPersonalCost)},")
            sb.append("${String.format(Locale.US, "%.2f", trip.reimbursementAmount)},")
            sb.append("\"${trip.notes.replace("\"", "\"\"")}\"\n")
        }
        return sb.toString()
    }

    fun generateCsvReportForTrips(tripsList: List<TripEntity>): String {
        val appSettings = settings.value
        val sb = StringBuilder()
        sb.append("Date,Start Time,End Time,Vehicle,Category,Start Location,End Location,Distance (${appSettings.unitDistance}),Fuel Cost (${appSettings.currencySymbol}),Maintenance Cost (${appSettings.currencySymbol}),Tolls (${appSettings.currencySymbol}),Parking (${appSettings.currencySymbol}),Total Personal Cost (${appSettings.currencySymbol}),Tax Deduction (${appSettings.currencySymbol}),Notes\n")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

        for (trip in tripsList) {
            val dateStr = dateFormat.format(Date(trip.startTime))
            val startStr = timeFormat.format(Date(trip.startTime))
            val endStr = trip.endTime?.let { timeFormat.format(Date(it)) } ?: ""
            val vehicle = vehicles.value.find { it.id == trip.vehicleId }?.name ?: "Unknown"

            sb.append("\"$dateStr\",")
            sb.append("\"$startStr\",")
            sb.append("\"$endStr\",")
            sb.append("\"$vehicle\",")
            sb.append("\"${trip.category}\",")
            sb.append("\"${trip.startAddress.replace("\"", "\"\"")}\",")
            sb.append("\"${(trip.endAddress ?: "").replace("\"", "\"\"")}\",")
            sb.append("${String.format(Locale.US, "%.2f", trip.distanceKm)},")
            sb.append("${String.format(Locale.US, "%.2f", trip.fuelCost)},")
            sb.append("${String.format(Locale.US, "%.2f", trip.maintenanceCost)},")
            sb.append("${String.format(Locale.US, "%.2f", trip.tolls)},")
            sb.append("${String.format(Locale.US, "%.2f", trip.parking)},")
            sb.append("${String.format(Locale.US, "%.2f", trip.totalPersonalCost)},")
            sb.append("${String.format(Locale.US, "%.2f", trip.reimbursementAmount)},")
            sb.append("\"${trip.notes.replace("\"", "\"\"")}\"\n")
        }
        return sb.toString()
    }

    fun exportPdfToUri(context: Context, uri: Uri, trips: List<TripEntity>, periodLabel: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                com.example.util.PdfReportGenerator.generatePdf(
                    trips = trips,
                    vehicles = vehicles.value,
                    settings = settings.value,
                    periodLabel = periodLabel,
                    outputStream = outputStream
                )
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun exportCsvToUri(context: Context, uri: Uri, trips: List<TripEntity>): Boolean {
        return try {
            val csvContent = generateCsvReportForTrips(trips)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(csvContent.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun groupTripsByDay(trips: List<TripEntity>): List<DayTripsGroup> {
        val groupsMap = LinkedHashMap<String, MutableList<TripEntity>>()
        val dayTimestampMap = HashMap<String, Long>()

        val nowCal = Calendar.getInstance()
        val tripCal = Calendar.getInstance()
        val dateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

        for (trip in trips) {
            val key = dateOnlyFormat.format(Date(trip.startTime))
            groupsMap.getOrPut(key) { mutableListOf() }.add(trip)
            if (!dayTimestampMap.containsKey(key)) {
                dayTimestampMap[key] = trip.startTime
            }
        }

        return groupsMap.map { (key, tripList) ->
            val timestamp = dayTimestampMap[key] ?: 0L
            tripCal.timeInMillis = timestamp

            val isToday = nowCal.get(Calendar.YEAR) == tripCal.get(Calendar.YEAR) &&
                    nowCal.get(Calendar.DAY_OF_YEAR) == tripCal.get(Calendar.DAY_OF_YEAR)
            nowCal.add(Calendar.DAY_OF_YEAR, -1)
            val isYesterday = nowCal.get(Calendar.YEAR) == tripCal.get(Calendar.YEAR) &&
                    nowCal.get(Calendar.DAY_OF_YEAR) == tripCal.get(Calendar.DAY_OF_YEAR)
            nowCal.add(Calendar.DAY_OF_YEAR, 1) // restore

            val dateLabel = when {
                isToday -> "Today • " + SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
                isYesterday -> "Yesterday • " + SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
                else -> displayFormat.format(Date(timestamp))
            }

            val totalDist = tripList.sumOf { it.distanceKm }
            val totalCost = tripList.sumOf { it.totalPersonalCost }
            val totalReimb = tripList.sumOf { it.reimbursementAmount }

            val breakdown = tripList.groupBy { it.category }.map { (cat, catTrips) ->
                CategoryStat(
                    categoryName = cat,
                    distanceKm = catTrips.sumOf { it.distanceKm },
                    fuelCost = catTrips.sumOf { it.fuelCost },
                    totalCost = catTrips.sumOf { it.totalPersonalCost },
                    tripCount = catTrips.size
                )
            }.sortedByDescending { it.distanceKm }

            DayTripsGroup(
                dateLabel = dateLabel,
                timestamp = timestamp,
                trips = tripList.sortedByDescending { it.startTime },
                totalDistanceKm = totalDist,
                totalPersonalCost = totalCost,
                totalReimbursement = totalReimb,
                categoryBreakdown = breakdown
            )
        }
    }

    private fun calculateMonthlyMetrics(
        trips: List<TripEntity>,
        vehicles: List<VehicleEntity>,
        settings: AppSettingsEntity,
        filter: MonthFilter
    ): MonthlyMetrics {
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH)

        val (filteredTrips, periodLabel, hasNext, hasPrev) = when (filter) {
            is MonthFilter.AllTime -> {
                FilteredPeriodResult(trips, "All Time", false, true)
            }
            is MonthFilter.Month -> {
                val matches = trips.filter { trip ->
                    cal.timeInMillis = trip.startTime
                    cal.get(Calendar.MONTH) == filter.month && cal.get(Calendar.YEAR) == filter.year
                }
                val mCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, filter.year)
                    set(Calendar.MONTH, filter.month)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val monthName = SimpleDateFormat("MMMM yyyy", Locale.US).format(mCal.time)
                val isFutureOrCurrent = filter.year > currentYear || (filter.year == currentYear && filter.month >= currentMonth)
                FilteredPeriodResult(matches, monthName, !isFutureOrCurrent, true)
            }
        }

        val totalDist = filteredTrips.sumOf { it.distanceKm }
        val totalDeduction = filteredTrips.sumOf { it.reimbursementAmount }
        val totalCost = filteredTrips.sumOf { it.totalPersonalCost }
        val totalFuelCost = filteredTrips.sumOf { if (it.fuelCost > 0.0) it.fuelCost else it.totalPersonalCost }

        val vehicleMap = vehicles.associateBy { it.id }
        val fuelPrice = if (settings.currentFuelPrice > 0.0) settings.currentFuelPrice else 104.21

        // Calculate actual total fuel consumed per trip from vehicle efficiency or fuel cost
        val totalFuelConsumed = filteredTrips.sumOf { trip ->
            val veh = trip.vehicleId?.let { vehicleMap[it] }
            val eff = veh?.efficiencyVal ?: 0.0
            if (eff > 0.0) {
                trip.distanceKm / eff
            } else if (fuelPrice > 0.0 && trip.fuelCost > 0.0) {
                trip.fuelCost / fuelPrice
            } else {
                0.0
            }
        }

        // Only calculate avgEfficiency if there are actual drives and fuel consumed; otherwise null so UI hides it
        val avgEfficiency = if (totalFuelConsumed > 0.0 && totalDist > 0.0) {
            totalDist / totalFuelConsumed
        } else {
            null
        }

        val tagBreakdown = filteredTrips.groupBy { it.category }.map { (cat, catTrips) ->
            CategoryStat(
                categoryName = cat,
                distanceKm = catTrips.sumOf { it.distanceKm },
                fuelCost = catTrips.sumOf { if (it.fuelCost > 0.0) it.fuelCost else it.totalPersonalCost },
                totalCost = catTrips.sumOf { it.totalPersonalCost },
                tripCount = catTrips.size
            )
        }.sortedByDescending { it.distanceKm }

        return MonthlyMetrics(
            totalDistanceKm = totalDist,
            totalBusinessDeduction = totalDeduction,
            totalPersonalCost = totalCost,
            totalFuelCost = totalFuelCost,
            totalFuelConsumed = totalFuelConsumed,
            avgEfficiency = avgEfficiency,
            tripCount = filteredTrips.size,
            tagBreakdown = tagBreakdown,
            periodLabel = periodLabel,
            hasNextPeriod = hasNext,
            hasPreviousPeriod = hasPrev,
            currentFilter = filter
        )
    }

    private data class FilteredPeriodResult(
        val trips: List<TripEntity>,
        val label: String,
        val hasNext: Boolean,
        val hasPrev: Boolean
    )
}
