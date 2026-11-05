package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.local.MileageDatabase
import com.example.data.local.MileageRepository
import com.example.data.model.TripCategory
import com.example.data.model.TripEntity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sqrt

class MileageTrackingService : Service(), SensorEventListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var repository: MileageRepository
    private var sensorManager: SensorManager? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var locationCallback: LocationCallback? = null
    private var timerJob: Job? = null
    private var pauseExpiryJob: Job? = null

    private enum class ServiceMode {
        IDLE,
        PASSIVE_MONITORING,
        PAUSED_MONITORING,
        ACTIVE_DRIVE
    }

    private var currentMode: ServiceMode = ServiceMode.IDLE

    // Adaptive passive GPS polling state (Standby-Only Battery Optimization when NO trip is active)
    @Volatile
    private var currentLocationIntervalMs: Long = 0L

    @Volatile
    private var lastPassiveMotionDetectedMs: Long = 0L

    // Passive auto-detection state (strictly verifies vehicle crossing preset threshold with >5m displacement for 2-3s)
    private var lastPassiveLocation: Location? = null
    private var movingStartAnchorLocation: Location? = null
    private var firstMovingTimestamp: Long = 0L
    private var consecutiveMovingReadings: Int = 0

    // Precision 3-axis accelerometer state
    private var hasMotionSensorHardware: Boolean = false
    private var usingLinearAccelerationSensor: Boolean = false
    private val gravityFilterState = FloatArray(3) { 0f }
    private var isGravityInitialized: Boolean = false

    private val accelSampleLock = Any()
    private val recentAccelSamples = ArrayDeque<Pair<Long, Float>>() // (timestampMs, netLinearAccelMps2)

    @Volatile
    private var latestLinearAccelMps2: Float = 0f

    @Volatile
    private var latestRmsAccelMps2: Float = 0f

    // Active drive tracking state
    private var activeTripId: String? = null
    private var activeVehicleId: String? = null
    private var activeVehicleName: String = "Default Vehicle"
    private var activeCategory: TripCategory = TripCategory.BUSINESS
    private var activeVehicleEfficiency: Double = 14.0
    private var activeEfficiencyUnit: String = "km/L"
    private var activeFuelPrice: Double = 102.12
    private var activeCurrencySymbol: String = "₹"
    private var activeUnitDistance: String = "km"

    private var totalDistanceMeters: Double = 0.0
    private var lastActiveLocation: Location? = null
    private var driveStartTimeMillis: Long = 0L
    private var lastDistanceAddedTimeMillis: Long = 0L
    private var stopTimeoutSeconds: Long = 480L

    // Latched Idle Countdown & 5-Second >= 10 km/h Resume Verification State
    @Volatile
    private var isIdleLatched: Boolean = false
    private var idleResumeAnchorLocation: Location? = null
    private var idleResumeFirstMovingTimestamp: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        repository = MileageRepository(MileageDatabase.getInstance(applicationContext))
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != ACTION_STOP_MONITORING && action != ACTION_STOP_ACTIVE_DRIVE) {
            if (!canStartLocationForegroundService(this)) {
                stopPassiveMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }
            if (!ensureImmediateForegroundNotification()) {
                stopPassiveMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        when (action) {
            null,
            ACTION_ENSURE_RUNNING,
            ACTION_START_MONITORING,
            ACTION_RESUME_MONITORING -> {
                restoreOrStartMonitoring()
            }
            ACTION_PAUSE_MONITORING -> {
                if (currentMode != ServiceMode.ACTIVE_DRIVE) {
                    serviceScope.launch {
                        val settings = repository.getSettings()
                        enterPausedMonitoringState(settings.autoTrackingPausedUntil)
                    }
                }
            }
            ACTION_STOP_MONITORING -> {
                if (currentMode != ServiceMode.ACTIVE_DRIVE) {
                    stopPassiveMonitoring()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_START_ACTIVE_DRIVE -> {
                val vehId = intent.getStringExtra(EXTRA_VEHICLE_ID)
                val catName = intent.getStringExtra(EXTRA_CATEGORY_NAME) ?: TripCategory.BUSINESS.name
                val vehName = intent.getStringExtra(EXTRA_VEHICLE_NAME) ?: "Default Vehicle"
                startActiveDrive(vehId, catName, vehName)
            }
            ACTION_STOP_ACTIVE_DRIVE -> {
                val tolls = intent.getDoubleExtra(EXTRA_TOLLS, 0.0)
                val parking = intent.getDoubleExtra(EXTRA_PARKING, 0.0)
                val hasEndOdo = intent.hasExtra(EXTRA_END_ODOMETER)
                val endOdo = if (hasEndOdo) intent.getDoubleExtra(EXTRA_END_ODOMETER, 0.0) else null
                val notes = intent.getStringExtra(EXTRA_NOTES) ?: ""
                stopActiveDrive(tolls, parking, endOdo, notes)
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // If user swipes the app away from Recent Apps while auto-tracking or an active drive is running
        // and background location permission is granted, schedule an immediate watchdog restart.
        if ((currentMode == ServiceMode.ACTIVE_DRIVE || currentMode == ServiceMode.PASSIVE_MONITORING) &&
            hasBackgroundLocationPermission(applicationContext)
        ) {
            AutoResumeManager.scheduleImmediateWatchdogRestart(applicationContext, 1500L)
        }
    }

    private fun ensureImmediateForegroundNotification(): Boolean {
        val notification = when (currentMode) {
            ServiceMode.ACTIVE_DRIVE -> buildActiveDriveNotification(
                TrackingStateManager.trackingData.value,
                activeUnitDistance
            )
            ServiceMode.PAUSED_MONITORING -> buildNotification(
                title = "Auto-Drive Detection Paused",
                content = "Standby paused • Will resume automatically"
            )
            else -> buildNotification(
                title = "Auto-Drive Detection Active",
                content = "Background drive detection ready"
            )
        }
        return startForegroundWithNotification(notification)
    }

    private fun restoreOrStartMonitoring() {
        serviceScope.launch {
            val activeTrip = repository.getActiveTrip()
            if (activeTrip != null) {
                if (currentMode != ServiceMode.ACTIVE_DRIVE || activeTripId != activeTrip.id) {
                    resumeExistingActiveDrive(activeTrip)
                }
                return@launch
            }
            if (currentMode != ServiceMode.ACTIVE_DRIVE) {
                startPassiveMonitoring()
            }
        }
    }

    private fun startPassiveMonitoring() {
        pauseExpiryJob?.cancel()
        serviceScope.launch {
            if (!canStartLocationForegroundService(this@MileageTrackingService)) {
                stopPassiveMonitoring()
                stopSelf()
                return@launch
            }
            val settings = repository.getSettings()
            if (!settings.autoTrackingEnabled) {
                stopPassiveMonitoring()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            if (settings.isAutoTrackingPaused()) {
                enterPausedMonitoringState(settings.autoTrackingPausedUntil)
                return@launch
            }

            // Standby-only battery optimization: release CPU WakeLock when no trip is active
            releaseWakeLock()
            currentMode = ServiceMode.PASSIVE_MONITORING
            resetPassiveDetectionState()
            val promoted = startForegroundWithNotification(
                buildNotification(
                    title = "Auto-Drive Detection Active",
                    content = "Monitoring vehicle speed threshold (${settings.autoDriveDetectionSpeedKmh} km/h) & motion sensors"
                )
            )
            if (!promoted) return@launch
            registerMotionSensors()
            // Start in low-power passive standby gear (3000ms); shifts immediately to 1000ms on first motion
            registerLocationUpdates(
                intervalMs = PASSIVE_STANDBY_INTERVAL_MS,
                minUpdateIntervalMs = PASSIVE_STANDBY_MIN_INTERVAL_MS
            )
        }
    }

    private fun enterPausedMonitoringState(pausedUntilMillis: Long?) {
        pauseExpiryJob?.cancel()
        currentMode = ServiceMode.PAUSED_MONITORING
        resetPassiveDetectionState()
        unregisterLocationUpdates()
        unregisterMotionSensors()
        releaseWakeLock()

        val subtitle = if (pausedUntilMillis == null || pausedUntilMillis == -1L) {
            "Paused until manually resumed"
        } else {
            val fmt = java.text.SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            "Paused • Auto-resumes at ${fmt.format(java.util.Date(pausedUntilMillis))}"
        }
        startForegroundWithNotification(
            buildNotification(
                title = "Auto-Drive Detection Paused",
                content = subtitle
            )
        )

        if (pausedUntilMillis != null && pausedUntilMillis > 0L) {
            AutoResumeManager.scheduleResume(applicationContext, pausedUntilMillis)
            pauseExpiryJob = serviceScope.launch {
                val waitMs = (pausedUntilMillis - System.currentTimeMillis()).coerceAtLeast(1000L)
                delay(waitMs)
                val latest = repository.getSettings()
                if (latest.autoTrackingPausedUntil != null &&
                    latest.autoTrackingPausedUntil > 0L &&
                    System.currentTimeMillis() >= latest.autoTrackingPausedUntil
                ) {
                    repository.updateSettings(latest.copy(autoTrackingPausedUntil = null))
                    AutoResumeManager.cancelPendingResume(applicationContext)
                    if (latest.autoTrackingEnabled && currentMode != ServiceMode.ACTIVE_DRIVE) {
                        startPassiveMonitoring()
                    }
                }
            }
        }
    }

    private fun stopPassiveMonitoring() {
        pauseExpiryJob?.cancel()
        currentMode = ServiceMode.IDLE
        resetPassiveDetectionState()
        unregisterLocationUpdates()
        unregisterMotionSensors()
        releaseWakeLock()
    }

    private fun resetPassiveDetectionState() {
        lastPassiveLocation = null
        movingStartAnchorLocation = null
        firstMovingTimestamp = 0L
        consecutiveMovingReadings = 0
        lastPassiveMotionDetectedMs = 0L
    }

    private fun resetIdleResumeState() {
        idleResumeAnchorLocation = null
        idleResumeFirstMovingTimestamp = 0L
    }

    private suspend fun resumeExistingActiveDrive(trip: TripEntity) {
        pauseExpiryJob?.cancel()
        acquireWakeLock()
        currentMode = ServiceMode.ACTIVE_DRIVE
        resetPassiveDetectionState()
        isIdleLatched = false
        resetIdleResumeState()

        val settings = repository.getSettings()
        val vehicle = trip.vehicleId?.let { repository.getVehicleById(it) } ?: repository.getDefaultVehicle()

        activeTripId = trip.id
        activeVehicleId = vehicle?.id ?: trip.vehicleId
        activeVehicleName = vehicle?.name ?: "Default Vehicle"
        activeCategory = try {
            TripCategory.valueOf(trip.category)
        } catch (_: Exception) {
            TripCategory.BUSINESS
        }
        activeVehicleEfficiency = vehicle?.efficiencyVal?.takeIf { it > 0.0 } ?: 14.0
        activeEfficiencyUnit = vehicle?.getParsedVehicleType()?.efficiencyUnit ?: "km/L"
        activeFuelPrice = if (settings.currentFuelPrice > 0.0) settings.currentFuelPrice else 102.12
        activeCurrencySymbol = settings.currencySymbol
        activeUnitDistance = settings.unitDistance
        stopTimeoutSeconds = (settings.stopTimeoutMinutes.coerceIn(3, 30) * 60).toLong()

        totalDistanceMeters = (trip.distanceKm * 1000.0).coerceAtLeast(0.0)
        val now = System.currentTimeMillis()
        driveStartTimeMillis = trip.startTime
        lastDistanceAddedTimeMillis = now

        val lastLat = trip.endLat ?: trip.startLat
        val lastLng = trip.endLng ?: trip.startLng
        lastActiveLocation = if (lastLat != 0.0 || lastLng != 0.0) {
            Location("restored").apply {
                latitude = lastLat
                longitude = lastLng
                time = now
            }
        } else {
            null
        }

        val elapsedSecs = ((now - driveStartTimeMillis) / 1000L).coerceAtLeast(0L)
        val distKm = totalDistanceMeters / 1000.0
        val liveFuelCost = if (activeVehicleEfficiency > 0.0) {
            (distKm / activeVehicleEfficiency) * activeFuelPrice
        } else 0.0

        val restoredData = LiveTrackingData(
            isTracking = true,
            tripId = trip.id,
            distanceKm = distKm,
            speedKmh = 0f,
            elapsedSeconds = elapsedSecs,
            latitude = lastLat,
            longitude = lastLng,
            startAddress = trip.startAddress,
            currentAddress = trip.endAddress ?: trip.startAddress,
            vehicleId = activeVehicleId,
            vehicleName = activeVehicleName,
            vehicleMileageKmpl = activeVehicleEfficiency,
            vehicleEfficiencyUnit = activeEfficiencyUnit,
            fuelPrice = activeFuelPrice,
            currencySymbol = activeCurrencySymbol,
            liveFuelCost = liveFuelCost,
            categoryName = activeCategory.displayName,
            isIdle = false,
            idleSecondsRemaining = stopTimeoutSeconds,
            idleTimeoutTotalSeconds = stopTimeoutSeconds,
            isWalkingDetected = false,
            activityStatus = "Driving",
            linearAccelMps2 = latestLinearAccelMps2,
            rmsAccelMps2 = latestRmsAccelMps2
        )

        TrackingStateManager.update { restoredData }
        startForegroundWithNotification(buildActiveDriveNotification(restoredData, activeUnitDistance))
        registerMotionSensors()
        registerLocationUpdates(
            intervalMs = ACTIVE_DRIVE_INTERVAL_MS,
            minUpdateIntervalMs = ACTIVE_DRIVE_MIN_INTERVAL_MS
        )
        startDriveTimerLoop()
    }

    private fun startActiveDrive(vehicleId: String?, categoryName: String, vehicleName: String) {
        pauseExpiryJob?.cancel()
        acquireWakeLock()
        currentMode = ServiceMode.ACTIVE_DRIVE
        resetPassiveDetectionState()
        isIdleLatched = false
        resetIdleResumeState()

        serviceScope.launch {
            val settings = repository.getSettings()
            val vehicle = vehicleId?.let { repository.getVehicleById(it) } ?: repository.getDefaultVehicle()

            activeVehicleId = vehicle?.id ?: vehicleId
            activeVehicleName = vehicle?.name ?: vehicleName
            activeCategory = try {
                TripCategory.valueOf(categoryName)
            } catch (_: Exception) {
                repository.resolveCategoryBySchedule(System.currentTimeMillis())
            }
            activeVehicleEfficiency = vehicle?.efficiencyVal?.takeIf { it > 0.0 } ?: 14.0
            activeEfficiencyUnit = vehicle?.getParsedVehicleType()?.efficiencyUnit ?: "km/L"
            activeFuelPrice = if (settings.currentFuelPrice > 0.0) settings.currentFuelPrice else 102.12
            activeCurrencySymbol = settings.currencySymbol
            activeUnitDistance = settings.unitDistance
            stopTimeoutSeconds = (settings.stopTimeoutMinutes.coerceIn(3, 30) * 60).toLong()

            totalDistanceMeters = 0.0
            lastActiveLocation = null
            val now = System.currentTimeMillis()
            driveStartTimeMillis = now
            lastDistanceAddedTimeMillis = now

            val newTripId = repository.startTrip(
                vehicleId = activeVehicleId,
                category = activeCategory,
                startTime = now,
                startLat = 0.0,
                startLng = 0.0,
                startAddress = "Detecting start location..."
            )
            activeTripId = newTripId

            val initialData = LiveTrackingData(
                isTracking = true,
                tripId = newTripId,
                distanceKm = 0.0,
                speedKmh = 0f,
                elapsedSeconds = 0L,
                startAddress = "Detecting start location...",
                currentAddress = "",
                vehicleId = activeVehicleId,
                vehicleName = activeVehicleName,
                vehicleMileageKmpl = activeVehicleEfficiency,
                vehicleEfficiencyUnit = activeEfficiencyUnit,
                fuelPrice = activeFuelPrice,
                currencySymbol = activeCurrencySymbol,
                liveFuelCost = 0.0,
                categoryName = activeCategory.displayName,
                isIdle = false,
                idleSecondsRemaining = stopTimeoutSeconds,
                idleTimeoutTotalSeconds = stopTimeoutSeconds,
                isWalkingDetected = false,
                activityStatus = "Driving",
                linearAccelMps2 = latestLinearAccelMps2,
                rmsAccelMps2 = latestRmsAccelMps2
            )

            TrackingStateManager.update { initialData }

            startForegroundWithNotification(
                buildActiveDriveNotification(initialData, activeUnitDistance)
            )

            registerMotionSensors()
            registerLocationUpdates(intervalMs = 1000L, minUpdateIntervalMs = 500L)
            startDriveTimerLoop()
        }
    }

    private fun startDriveTimerLoop() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (currentMode == ServiceMode.ACTIVE_DRIVE) {
                delay(1000L)
                val now = System.currentTimeMillis()
                val elapsedSecs = ((now - driveStartTimeMillis) / 1000L).coerceAtLeast(0L)
                val idleSecs = ((now - lastDistanceAddedTimeMillis) / 1000L).coerceAtLeast(0L)

                val settings = repository.getSettings()
                stopTimeoutSeconds = (settings.stopTimeoutMinutes.coerceIn(3, 30) * 60).toLong()
                activeUnitDistance = settings.unitDistance

                if (idleSecs >= IDLE_START_DELAY_SECONDS) {
                    isIdleLatched = true
                }
                val isStationaryIdle = isIdleLatched || idleSecs >= IDLE_START_DELAY_SECONDS
                val remainingIdleSecs = (stopTimeoutSeconds - idleSecs).coerceAtLeast(0L)

                val distKm = totalDistanceMeters / 1000.0
                val liveFuelCost = if (activeVehicleEfficiency > 0.0) {
                    (distKm / activeVehicleEfficiency) * activeFuelPrice
                } else 0.0

                val resolvedActivityStatus = if (isStationaryIdle) {
                    "Stationary (Idle)"
                } else {
                    "Driving"
                }

                var updatedSnapshot = LiveTrackingData()
                TrackingStateManager.update { current ->
                    updatedSnapshot = current.copy(
                        distanceKm = distKm,
                        liveFuelCost = liveFuelCost,
                        speedKmh = if (isStationaryIdle && idleResumeFirstMovingTimestamp == 0L) 0f else current.speedKmh,
                        elapsedSeconds = elapsedSecs,
                        isIdle = isStationaryIdle,
                        idleSecondsRemaining = remainingIdleSecs,
                        idleTimeoutTotalSeconds = stopTimeoutSeconds,
                        isWalkingDetected = false,
                        activityStatus = resolvedActivityStatus,
                        linearAccelMps2 = latestLinearAccelMps2,
                        rmsAccelMps2 = latestRmsAccelMps2
                    )
                    updatedSnapshot
                }

                updateActiveDriveNotification(updatedSnapshot, activeUnitDistance)

                if (idleSecs >= stopTimeoutSeconds) {
                    Log.d(TAG, "Stationary auto-stop timeout reached ($stopTimeoutSeconds s). Completing trip.")
                    stopActiveDrive(0.0, 0.0, null, "Auto-completed after ${settings.stopTimeoutMinutes}m stationary")
                    break
                }
            }
        }
    }

    private fun stopActiveDrive(
        tolls: Double,
        parking: Double,
        endOdometer: Double?,
        notes: String
    ) {
        timerJob?.cancel()
        val tripIdToFinish = activeTripId
        val finalDistKm = totalDistanceMeters / 1000.0
        val finalLoc = lastActiveLocation
        val currentState = TrackingStateManager.trackingData.value

        activeTripId = null
        isIdleLatched = false
        resetIdleResumeState()
        TrackingStateManager.reset()
        releaseWakeLock()

        serviceScope.launch {
            if (tripIdToFinish != null) {
                val endLat = finalLoc?.latitude ?: currentState.latitude
                val endLng = finalLoc?.longitude ?: currentState.longitude
                val resolvedEndAddress = if (endLat != 0.0 && endLng != 0.0) {
                    resolveAddress(endLat, endLng)
                } else {
                    currentState.currentAddress.ifBlank { currentState.startAddress }
                }

                repository.completeTrip(
                    tripId = tripIdToFinish,
                    endTime = System.currentTimeMillis(),
                    endLat = endLat,
                    endLng = endLng,
                    endAddress = resolvedEndAddress,
                    distanceKm = finalDistKm,
                    tolls = tolls,
                    parking = parking,
                    endOdometer = endOdometer,
                    notes = notes
                )
            }

            val settings = repository.getSettings()
            if (settings.autoTrackingEnabled) {
                startPassiveMonitoring()
            } else {
                stopPassiveMonitoring()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    // -------------------------------------------------------------------------
    // High-Precision 3-Axis Accelerometer Implementation
    // -------------------------------------------------------------------------

    private fun registerMotionSensors() {
        val sm = sensorManager ?: return
        unregisterMotionSensors()

        synchronized(accelSampleLock) {
            recentAccelSamples.clear()
            isGravityInitialized = false
            latestLinearAccelMps2 = 0f
            latestRmsAccelMps2 = 0f
        }

        val linearSensor = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val rawAccelSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Standby-Only Battery Optimization:
        // Use SENSOR_DELAY_NORMAL (~5Hz) when no trip is active (PASSIVE_MONITORING),
        // and required high-precision SENSOR_DELAY_GAME when a trip IS active (ACTIVE_DRIVE).
        val sensorDelay = if (currentMode == ServiceMode.ACTIVE_DRIVE) {
            SensorManager.SENSOR_DELAY_GAME
        } else {
            SensorManager.SENSOR_DELAY_NORMAL
        }

        var registeredAny = false
        if (linearSensor != null) {
            usingLinearAccelerationSensor = true
            registeredAny = sm.registerListener(this, linearSensor, sensorDelay)
        } else if (rawAccelSensor != null) {
            usingLinearAccelerationSensor = false
            registeredAny = sm.registerListener(this, rawAccelSensor, sensorDelay)
        }

        hasMotionSensorHardware = registeredAny
    }

    private fun unregisterMotionSensors() {
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val ev = event ?: return
        val now = System.currentTimeMillis()

        if (ev.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION || ev.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            if (ev.values.size < 3) return
            val isLinear = ev.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION
            if (!isLinear && !isGravityInitialized) {
                gravityFilterState[0] = ev.values[0]
                gravityFilterState[1] = ev.values[1]
                gravityFilterState[2] = ev.values[2]
                isGravityInitialized = true
            }

            val netMag = computeLinearAccelerationMagnitude(
                rawX = ev.values[0],
                rawY = ev.values[1],
                rawZ = ev.values[2],
                gravityState = gravityFilterState,
                isLinearSensor = isLinear
            )

            val currentRms: Float
            synchronized(accelSampleLock) {
                recentAccelSamples.addLast(now to netMag)
                pruneSensorWindowsLocked(now)

                latestLinearAccelMps2 = netMag
                currentRms = computeRmsAcceleration(recentAccelSamples.map { it.second })
                latestRmsAccelMps2 = currentRms
            }

            // Standby-only adaptive GPS gear shift:
            // If no trip is active (PASSIVE_MONITORING) and physical motion energy is detected,
            // immediately shift passive GPS from 3000ms standby gear to 1000ms high-accuracy gear
            // so the 2.5s vehicular threshold verification has full 1-second resolution.
            if (currentMode == ServiceMode.PASSIVE_MONITORING && currentRms >= MIN_VEHICLE_RMS_ACCEL_MPS2) {
                lastPassiveMotionDetectedMs = now
                if (currentLocationIntervalMs != PASSIVE_ACTIVE_INTERVAL_MS) {
                    registerLocationUpdates(
                        intervalMs = PASSIVE_ACTIVE_INTERVAL_MS,
                        minUpdateIntervalMs = PASSIVE_ACTIVE_MIN_INTERVAL_MS
                    )
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private fun pruneSensorWindowsLocked(nowMs: Long) {
        val accelCutoff = nowMs - ACCEL_RMS_WINDOW_MS
        while (recentAccelSamples.isNotEmpty() && recentAccelSamples.first().first < accelCutoff) {
            recentAccelSamples.removeFirst()
        }
    }

    /**
     * Prunes stale accelerometer readings before evaluating GPS callbacks in the background.
     * When the app is closed or the screen is off and Android throttles background sensor events,
     * this prevents stale 0.0 m/s² readings from blocking GPS speed threshold auto-start.
     */
    private fun pruneAndRefreshSensorMetrics(nowMs: Long): Boolean {
        synchronized(accelSampleLock) {
            pruneSensorWindowsLocked(nowMs)
            return if (recentAccelSamples.isEmpty()) {
                latestLinearAccelMps2 = 0f
                latestRmsAccelMps2 = 0f
                false
            } else {
                latestRmsAccelMps2 = computeRmsAcceleration(recentAccelSamples.map { it.second })
                true
            }
        }
    }

    // -------------------------------------------------------------------------
    // Location Updates & Strict Idle-Latched Distance Tracking
    // -------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun registerLocationUpdates(intervalMs: Long, minUpdateIntervalMs: Long) {
        if (locationCallback != null && currentLocationIntervalMs == intervalMs) return
        unregisterLocationUpdates()
        currentLocationIntervalMs = intervalMs
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(minUpdateIntervalMs)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                if (currentMode == ServiceMode.PASSIVE_MONITORING) {
                    handlePassiveMonitoringLocation(loc)
                } else if (currentMode == ServiceMode.ACTIVE_DRIVE) {
                    handleActiveDriveLocation(loc)
                }
            }
        }
        locationCallback = callback
        try {
            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
        }
    }

    private fun unregisterLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
        currentLocationIntervalMs = 0L
    }

    private fun handlePassiveMonitoringLocation(location: Location) {
        serviceScope.launch {
            val settings = repository.getSettings()
            if (!settings.autoTrackingEnabled) return@launch
            if (settings.isAutoTrackingPaused()) {
                enterPausedMonitoringState(settings.autoTrackingPausedUntil)
                return@launch
            }

            // Reject low-accuracy GPS fixes to eliminate stationary indoor/multipath drift
            if (location.hasAccuracy() && location.accuracy > MAX_PASSIVE_GPS_ACCURACY_METERS) {
                return@launch
            }

            val prev = lastPassiveLocation
            lastPassiveLocation = location
            if (prev == null) return@launch

            val deltaMs = (location.time - prev.time).coerceAtLeast(1L)
            if (deltaMs > 10_000L) {
                movingStartAnchorLocation = null
                firstMovingTimestamp = 0L
                consecutiveMovingReadings = 0
                return@launch
            }

            val stepDistMeters = prev.distanceTo(location)
            val deltaSeconds = deltaMs / 1000.0
            val displacementSpeedKmh = (stepDistMeters / deltaSeconds) * 3.6
            val dopplerSpeedKmh = if (location.hasSpeed()) (location.speed * 3.6) else displacementSpeedKmh

            val nowWallMs = System.currentTimeMillis()
            val now = if (location.time > 0L) location.time else nowWallMs

            // Adaptive passive GPS gear management (Standby-Only Battery Optimization):
            // Shift to 1-second High-Accuracy gear immediately when incipient movement is observed,
            // and return to 3-second standby gear after 30 seconds of zero movement.
            val hasIncipientGpsMovement = dopplerSpeedKmh >= 2.0 || displacementSpeedKmh >= 2.0 || stepDistMeters >= 1.5f
            if (hasIncipientGpsMovement) {
                lastPassiveMotionDetectedMs = nowWallMs
                if (currentMode == ServiceMode.PASSIVE_MONITORING && currentLocationIntervalMs != PASSIVE_ACTIVE_INTERVAL_MS) {
                    Looper.getMainLooper().let {
                        serviceScope.launch(Dispatchers.Main) {
                            if (currentMode == ServiceMode.PASSIVE_MONITORING && currentLocationIntervalMs != PASSIVE_ACTIVE_INTERVAL_MS) {
                                registerLocationUpdates(
                                    intervalMs = PASSIVE_ACTIVE_INTERVAL_MS,
                                    minUpdateIntervalMs = PASSIVE_ACTIVE_MIN_INTERVAL_MS
                                )
                            }
                        }
                    }
                }
            } else if (movingStartAnchorLocation == null &&
                currentLocationIntervalMs == PASSIVE_ACTIVE_INTERVAL_MS &&
                (nowWallMs - lastPassiveMotionDetectedMs) >= PASSIVE_HIGH_FREQ_WINDOW_MS
            ) {
                serviceScope.launch(Dispatchers.Main) {
                    if (currentMode == ServiceMode.PASSIVE_MONITORING &&
                        movingStartAnchorLocation == null &&
                        currentLocationIntervalMs == PASSIVE_ACTIVE_INTERVAL_MS
                    ) {
                        registerLocationUpdates(
                            intervalMs = PASSIVE_STANDBY_INTERVAL_MS,
                            minUpdateIntervalMs = PASSIVE_STANDBY_MIN_INTERVAL_MS
                        )
                    }
                }
            }

            // Prune stale accelerometer samples so background OS sensor throttling when app is closed
            // never leaves stale 0.0 m/s² readings blocking GPS speed threshold auto-start.
            val hasFreshSensorSamples = pruneAndRefreshSensorMetrics(nowWallMs)
            if (hasMotionSensorHardware && hasFreshSensorSamples && latestRmsAccelMps2 < MIN_VEHICLE_RMS_ACCEL_MPS2) {
                movingStartAnchorLocation = null
                firstMovingTimestamp = 0L
                consecutiveMovingReadings = 0
                return@launch
            }

            val thresholdKmh = settings.autoDriveDetectionSpeedKmh.coerceIn(1, 15).toDouble()

            val isStepSpeedCrossing = dopplerSpeedKmh >= thresholdKmh &&
                displacementSpeedKmh >= (thresholdKmh * 0.85) &&
                stepDistMeters in 0.5f..150.0f

            if (isStepSpeedCrossing) {
                if (movingStartAnchorLocation == null) {
                    movingStartAnchorLocation = prev
                    firstMovingTimestamp = if (prev.time > 0L) prev.time else (now - deltaMs)
                }
                consecutiveMovingReadings++

                val totalDisplacementFromAnchorMeters = movingStartAnchorLocation?.distanceTo(location) ?: stepDistMeters
                val sustainedDurationMs = (now - firstMovingTimestamp).coerceAtLeast(0L)

                if (verifyVehicularThresholdCrossing(
                        dopplerSpeedKmh = dopplerSpeedKmh,
                        displacementSpeedKmh = displacementSpeedKmh,
                        thresholdKmh = thresholdKmh,
                        totalDisplacementMeters = totalDisplacementFromAnchorMeters.toDouble(),
                        sustainedDurationMs = sustainedDurationMs
                    )
                ) {
                    val defaultVeh = repository.getDefaultVehicle()
                    val category = repository.resolveCategoryBySchedule(System.currentTimeMillis())
                    startActiveDrive(
                        vehicleId = defaultVeh?.id,
                        categoryName = category.name,
                        vehicleName = defaultVeh?.name ?: "Default Vehicle"
                    )
                }
            } else {
                movingStartAnchorLocation = null
                firstMovingTimestamp = 0L
                consecutiveMovingReadings = 0
            }
        }
    }

    private fun handleActiveDriveLocation(location: Location) {
        if (location.hasAccuracy() && location.accuracy > 20.0f) return

        serviceScope.launch {
            val prev = lastActiveLocation
            val now = System.currentTimeMillis()
            val rawSpeedKmh = if (location.hasSpeed()) (location.speed * 3.6f) else 0f

            if (prev == null) {
                lastActiveLocation = location
                val startAddr = resolveAddress(location.latitude, location.longitude)
                activeTripId?.let { id ->
                    val existing = repository.getTripById(id)
                    if (existing != null) {
                        repository.updateTrip(
                            existing.copy(
                                startLat = location.latitude,
                                startLng = location.longitude,
                                startAddress = startAddr
                            )
                        )
                    }
                    repository.addTripPoint(id, location.latitude, location.longitude, rawSpeedKmh, now)
                }
                var firstSnapshot = LiveTrackingData()
                TrackingStateManager.update { current ->
                    firstSnapshot = current.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        startAddress = startAddr,
                        currentAddress = startAddr,
                        speedKmh = if (rawSpeedKmh >= MIN_ACTIVE_DRIVING_SPEED_KMH) rawSpeedKmh else 0f,
                        isWalkingDetected = false,
                        activityStatus = "Driving",
                        linearAccelMps2 = latestLinearAccelMps2,
                        rmsAccelMps2 = latestRmsAccelMps2
                    )
                    firstSnapshot
                }
                updateActiveDriveNotification(firstSnapshot, activeUnitDistance)
                return@launch
            }

            val deltaMs = (location.time - prev.time).coerceAtLeast(500L)
            val deltaSeconds = deltaMs / 1000.0
            val deltaMeters = prev.distanceTo(location).toDouble()
            val displacementSpeedKmh = (deltaMeters / deltaSeconds) * 3.6
            val dopplerSpeedKmh = if (location.hasSpeed()) (location.speed * 3.6) else displacementSpeedKmh
            val effectiveSpeedKmh = dopplerSpeedKmh.toFloat()

            val idleSecsNow = ((now - lastDistanceAddedTimeMillis) / 1000L).coerceAtLeast(0L)
            if (idleSecsNow >= IDLE_START_DELAY_SECONDS) {
                isIdleLatched = true
            }

            // -----------------------------------------------------------------
            // CASE 1: Vehicle is currently in LATCHED IDLE COUNTDOWN mode.
            // Must sustain speed >= 10.0 km/h for >= 5 seconds (5000 ms) with
            // >= 12m physical displacement to cancel the countdown and resume!
            // -----------------------------------------------------------------
            if (isIdleLatched) {
                val meetsResumeStep = dopplerSpeedKmh >= MIN_ACTIVE_DRIVING_SPEED_KMH &&
                    displacementSpeedKmh >= (MIN_ACTIVE_DRIVING_SPEED_KMH * 0.85) &&
                    deltaMeters in 1.0..200.0

                if (meetsResumeStep) {
                    if (idleResumeAnchorLocation == null) {
                        idleResumeAnchorLocation = prev
                        idleResumeFirstMovingTimestamp = if (prev.time > 0L) prev.time else (now - deltaMs)
                    }
                    val locTimestamp = if (location.time > 0L) location.time else now
                    val totalDisplacementFromIdleAnchor = (idleResumeAnchorLocation?.distanceTo(location) ?: deltaMeters.toFloat()).toDouble()
                    val sustainedResumeMs = (locTimestamp - idleResumeFirstMovingTimestamp).coerceAtLeast(0L)

                    if (verifyIdleToDrivingResume(
                            dopplerSpeedKmh = dopplerSpeedKmh,
                            displacementSpeedKmh = displacementSpeedKmh,
                            totalDisplacementMeters = totalDisplacementFromIdleAnchor,
                            sustainedDurationMs = sustainedResumeMs
                        )
                    ) {
                        // Confirmed >= 10 km/h for >= 5 seconds! Exit Idle and resume Active Driving.
                        isIdleLatched = false
                        val resumedDistMeters = totalDisplacementFromIdleAnchor.coerceIn(0.0, 500.0)
                        totalDistanceMeters += resumedDistMeters
                        lastActiveLocation = location
                        lastDistanceAddedTimeMillis = now
                        resetIdleResumeState()

                        val distKm = totalDistanceMeters / 1000.0
                        val liveFuelCost = if (activeVehicleEfficiency > 0.0) {
                            (distKm / activeVehicleEfficiency) * activeFuelPrice
                        } else 0.0

                        val currentAddr = resolveAddress(location.latitude, location.longitude)
                        activeTripId?.let { id ->
                            repository.addTripPoint(id, location.latitude, location.longitude, effectiveSpeedKmh, now)
                            val existing = repository.getTripById(id)
                            if (existing != null) {
                                repository.updateTrip(
                                    existing.copy(
                                        distanceKm = distKm,
                                        endLat = location.latitude,
                                        endLng = location.longitude,
                                        endAddress = currentAddr
                                    )
                                )
                            }
                        }

                        var resumedSnapshot = LiveTrackingData()
                        TrackingStateManager.update { current ->
                            resumedSnapshot = current.copy(
                                distanceKm = distKm,
                                speedKmh = effectiveSpeedKmh,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                currentAddress = currentAddr,
                                liveFuelCost = liveFuelCost,
                                isIdle = false,
                                idleSecondsRemaining = stopTimeoutSeconds,
                                isWalkingDetected = false,
                                activityStatus = "Driving",
                                linearAccelMps2 = latestLinearAccelMps2,
                                rmsAccelMps2 = latestRmsAccelMps2
                            )
                            resumedSnapshot
                        }
                        updateActiveDriveNotification(resumedSnapshot, activeUnitDistance)
                        return@launch
                    } else {
                        // Still accumulating the 5-second verification window; keep countdown running!
                        lastActiveLocation = location
                        val remainingIdleSecs = (stopTimeoutSeconds - idleSecsNow).coerceAtLeast(0L)
                        var verifyingSnapshot = LiveTrackingData()
                        TrackingStateManager.update { current ->
                            verifyingSnapshot = current.copy(
                                speedKmh = effectiveSpeedKmh,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                isIdle = true,
                                idleSecondsRemaining = remainingIdleSecs,
                                isWalkingDetected = false,
                                activityStatus = "Stationary (Idle)",
                                linearAccelMps2 = latestLinearAccelMps2,
                                rmsAccelMps2 = latestRmsAccelMps2
                            )
                            verifyingSnapshot
                        }
                        updateActiveDriveNotification(verifyingSnapshot, activeUnitDistance)
                        return@launch
                    }
                } else {
                    // Sub-threshold movement (< 10 km/h) or GPS drift while idle:
                    // Reset 5s resume tracker, absorb location so drift never builds up, keep countdown running!
                    resetIdleResumeState()
                    lastActiveLocation = location
                    val remainingIdleSecs = (stopTimeoutSeconds - idleSecsNow).coerceAtLeast(0L)
                    var idleSnapshot = LiveTrackingData()
                    TrackingStateManager.update { current ->
                        idleSnapshot = current.copy(
                            speedKmh = 0f,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            isIdle = true,
                            idleSecondsRemaining = remainingIdleSecs,
                            isWalkingDetected = false,
                            activityStatus = "Stationary (Idle)",
                            linearAccelMps2 = latestLinearAccelMps2,
                            rmsAccelMps2 = latestRmsAccelMps2
                        )
                        idleSnapshot
                    }
                    updateActiveDriveNotification(idleSnapshot, activeUnitDistance)
                    return@launch
                }
            }

            // -----------------------------------------------------------------
            // CASE 2: Normal Active Driving (not yet latched in Idle).
            // All sub-threshold movement (< 10.0 km/h, including walking or drift)
            // is treated as Idle and does NOT add distance or reset idle timer.
            // -----------------------------------------------------------------
            val isDrivingAboveThreshold = dopplerSpeedKmh >= MIN_ACTIVE_DRIVING_SPEED_KMH &&
                displacementSpeedKmh >= (MIN_ACTIVE_DRIVING_SPEED_KMH * 0.85) &&
                deltaMeters >= 2.5 && deltaMeters < 500.0

            if (isDrivingAboveThreshold) {
                totalDistanceMeters += deltaMeters
                lastActiveLocation = location
                lastDistanceAddedTimeMillis = now

                val distKm = totalDistanceMeters / 1000.0
                val liveFuelCost = if (activeVehicleEfficiency > 0.0) {
                    (distKm / activeVehicleEfficiency) * activeFuelPrice
                } else 0.0

                val currentAddr = resolveAddress(location.latitude, location.longitude)
                activeTripId?.let { id ->
                    repository.addTripPoint(id, location.latitude, location.longitude, effectiveSpeedKmh, now)
                    val existing = repository.getTripById(id)
                    if (existing != null) {
                        repository.updateTrip(
                            existing.copy(
                                distanceKm = distKm,
                                endLat = location.latitude,
                                endLng = location.longitude,
                                endAddress = currentAddr
                            )
                        )
                    }
                }

                var drivingSnapshot = LiveTrackingData()
                TrackingStateManager.update { current ->
                    drivingSnapshot = current.copy(
                        distanceKm = distKm,
                        speedKmh = effectiveSpeedKmh,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        currentAddress = currentAddr,
                        liveFuelCost = liveFuelCost,
                        isIdle = false,
                        idleSecondsRemaining = stopTimeoutSeconds,
                        isWalkingDetected = false,
                        activityStatus = "Driving",
                        linearAccelMps2 = latestLinearAccelMps2,
                        rmsAccelMps2 = latestRmsAccelMps2
                    )
                    drivingSnapshot
                }
                updateActiveDriveNotification(drivingSnapshot, activeUnitDistance)
            } else {
                // Sub-threshold (< 10 km/h): Absorb location to prevent drift accumulation, do NOT reset lastDistanceAddedTimeMillis
                lastActiveLocation = location
                var subThresholdSnapshot = LiveTrackingData()
                TrackingStateManager.update { current ->
                    subThresholdSnapshot = current.copy(
                        speedKmh = 0f,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        isWalkingDetected = false,
                        activityStatus = if (current.isIdle) "Stationary (Idle)" else "Driving",
                        linearAccelMps2 = latestLinearAccelMps2,
                        rmsAccelMps2 = latestRmsAccelMps2
                    )
                    subThresholdSnapshot
                }
                updateActiveDriveNotification(subThresholdSnapshot, activeUnitDistance)
            }
        }
    }

    private suspend fun resolveAddress(lat: Double, lng: Double): String {
        repository.findMatchingPlaceName(lat, lng)?.let { return it }
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            val first = addresses?.firstOrNull()
            when {
                first == null -> String.format(Locale.US, "%.4f, %.4f", lat, lng)
                !first.subLocality.isNullOrBlank() && !first.locality.isNullOrBlank() ->
                    "${first.subLocality}, ${first.locality}"
                !first.thoroughfare.isNullOrBlank() && !first.locality.isNullOrBlank() ->
                    "${first.thoroughfare}, ${first.locality}"
                !first.locality.isNullOrBlank() -> first.locality
                else -> first.getAddressLine(0) ?: String.format(Locale.US, "%.4f, %.4f", lat, lng)
            }
        } catch (_: Exception) {
            String.format(Locale.US, "%.4f, %.4f", lat, lng)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MileageTracker::DriveWakeLock").apply {
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun startForegroundWithNotification(notification: Notification): Boolean {
        if (!canStartLocationForegroundService(this)) {
            return false
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Deferred foreground location service promotion until permissions/state are eligible: ${e.message}")
            false
        }
    }

    private fun updateActiveDriveNotification(data: LiveTrackingData, unitDistance: String) {
        if (currentMode != ServiceMode.ACTIVE_DRIVE) return
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.notify(NOTIFICATION_ID, buildActiveDriveNotification(data, unitDistance))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to update active drive notification: ${e.message}")
        }
    }

    private fun buildActiveDriveNotification(data: LiveTrackingData, unitDistance: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MileageTrackingService::class.java).apply {
            action = ACTION_STOP_ACTIVE_DRIVE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = formatActiveDriveNotificationTitle(
            distanceKm = data.distanceKm,
            unitDistance = unitDistance,
            currencySymbol = data.currencySymbol,
            liveFuelCost = data.liveFuelCost,
            isIdle = data.isIdle
        )

        val body = formatActiveDriveNotificationBody(
            speedKmh = data.speedKmh,
            elapsedSeconds = data.elapsedSeconds,
            vehicleName = data.vehicleName,
            categoryName = data.categoryName,
            isIdle = data.isIdle,
            idleSecondsRemaining = data.idleSecondsRemaining
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop & Save Trip",
                stopPendingIntent
            )
            .build()
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mileage Live GPS Priority Tracking",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority live GPS drive telemetry and lock-screen status updates"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        timerJob?.cancel()
        pauseExpiryJob?.cancel()
        if ((currentMode == ServiceMode.ACTIVE_DRIVE || currentMode == ServiceMode.PASSIVE_MONITORING) &&
            hasBackgroundLocationPermission(applicationContext)
        ) {
            AutoResumeManager.scheduleImmediateWatchdogRestart(applicationContext, 1500L)
        }
        unregisterLocationUpdates()
        unregisterMotionSensors()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MileageTrackingService"
        private const val CHANNEL_ID = "mileage_priority_tracking_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_PASSIVE_GPS_ACCURACY_METERS = 15.0f
        const val MIN_VEHICLE_DISPLACEMENT_METERS = 5.0
        const val MIN_SUSTAINED_DURATION_MS = 2500L

        // Adaptive Standby-Only GPS Polling Intervals (When NO trip is active)
        private const val PASSIVE_STANDBY_INTERVAL_MS = 3000L
        private const val PASSIVE_STANDBY_MIN_INTERVAL_MS = 1500L
        private const val PASSIVE_ACTIVE_INTERVAL_MS = 1000L
        private const val PASSIVE_ACTIVE_MIN_INTERVAL_MS = 500L
        private const val PASSIVE_HIGH_FREQ_WINDOW_MS = 30_000L

        // Active Drive Required Power GPS Polling Intervals (When a trip IS active)
        private const val ACTIVE_DRIVE_INTERVAL_MS = 1000L
        private const val ACTIVE_DRIVE_MIN_INTERVAL_MS = 500L

        // Active Driving & Idle Resume Constants
        const val IDLE_START_DELAY_SECONDS = 15L
        const val MIN_ACTIVE_DRIVING_SPEED_KMH = 10.0f
        const val MIN_IDLE_RESUME_DURATION_MS = 5000L
        const val MIN_IDLE_RESUME_DISPLACEMENT_METERS = 12.0

        // Accelerometer Constants
        private const val GRAVITY_LOW_PASS_ALPHA = 0.85f
        private const val ACCEL_NOISE_FLOOR_MPS2 = 0.12f
        private const val ACCEL_RMS_WINDOW_MS = 1500L
        const val MIN_VEHICLE_RMS_ACCEL_MPS2 = 0.18f

        const val ACTION_ENSURE_RUNNING = "com.example.action.ENSURE_RUNNING"
        const val ACTION_START_MONITORING = "com.example.action.START_MONITORING"
        const val ACTION_RESUME_MONITORING = "com.example.action.RESUME_MONITORING"
        const val ACTION_PAUSE_MONITORING = "com.example.action.PAUSE_MONITORING"
        const val ACTION_STOP_MONITORING = "com.example.action.STOP_MONITORING"
        const val ACTION_START_ACTIVE_DRIVE = "com.example.action.START_ACTIVE_DRIVE"
        const val ACTION_STOP_ACTIVE_DRIVE = "com.example.action.STOP_ACTIVE_DRIVE"

        private const val EXTRA_VEHICLE_ID = "extra_vehicle_id"
        private const val EXTRA_CATEGORY_NAME = "extra_category_name"
        private const val EXTRA_VEHICLE_NAME = "extra_vehicle_name"
        private const val EXTRA_TOLLS = "extra_tolls"
        private const val EXTRA_PARKING = "extra_parking"
        private const val EXTRA_END_ODOMETER = "extra_end_odometer"
        private const val EXTRA_NOTES = "extra_notes"

        /**
         * Normalizes any fuel rate unit (e.g. "/L", " / L", "/gal") into a clean volume unit ("L", "gal").
         */
        fun normalizeFuelVolumeUnit(rawUnit: String?): String {
            val cleaned = rawUnit?.trim()?.removePrefix("/")?.trim().orEmpty()
            return cleaned.ifBlank { "L" }
        }

        /**
         * Computes filtered 3-axis net linear acceleration magnitude (in m/s²), removing gravity
         * via a low-pass filter when using a raw accelerometer, and zeroing sub-threshold sensor noise.
         */
        fun computeLinearAccelerationMagnitude(
            rawX: Float,
            rawY: Float,
            rawZ: Float,
            gravityState: FloatArray,
            isLinearSensor: Boolean
        ): Float {
            val linX: Float
            val linY: Float
            val linZ: Float

            if (isLinearSensor) {
                linX = rawX
                linY = rawY
                linZ = rawZ
            } else {
                gravityState[0] = GRAVITY_LOW_PASS_ALPHA * gravityState[0] + (1f - GRAVITY_LOW_PASS_ALPHA) * rawX
                gravityState[1] = GRAVITY_LOW_PASS_ALPHA * gravityState[1] + (1f - GRAVITY_LOW_PASS_ALPHA) * rawY
                gravityState[2] = GRAVITY_LOW_PASS_ALPHA * gravityState[2] + (1f - GRAVITY_LOW_PASS_ALPHA) * rawZ
                linX = rawX - gravityState[0]
                linY = rawY - gravityState[1]
                linZ = rawZ - gravityState[2]
            }

            val magnitude = sqrt(linX * linX + linY * linY + linZ * linZ)
            return if (magnitude < ACCEL_NOISE_FLOOR_MPS2) 0f else magnitude
        }

        /**
         * Computes Root-Mean-Square (RMS) linear acceleration from a rolling window of samples.
         */
        fun computeRmsAcceleration(samples: List<Float>): Float {
            if (samples.isEmpty()) return 0f
            var sumSq = 0.0
            for (sample in samples) {
                sumSq += (sample * sample).toDouble()
            }
            return sqrt(sumSq / samples.size.toDouble()).toFloat()
        }

        /**
         * Verifies whether a vehicle in Latched Idle Countdown mode has genuinely resumed driving:
         * Requires speed >= 10.0 km/h sustained continuously for >= 5 seconds (5000 ms)
         * and physical displacement >= 12.0 meters.
         */
        fun verifyIdleToDrivingResume(
            dopplerSpeedKmh: Double,
            displacementSpeedKmh: Double,
            totalDisplacementMeters: Double,
            sustainedDurationMs: Long
        ): Boolean {
            val speedMeetsRequirement = dopplerSpeedKmh >= MIN_ACTIVE_DRIVING_SPEED_KMH &&
                displacementSpeedKmh >= (MIN_ACTIVE_DRIVING_SPEED_KMH * 0.85)
            val displacementConfirmed = totalDisplacementMeters >= MIN_IDLE_RESUME_DISPLACEMENT_METERS
            val durationConfirmed = sustainedDurationMs >= MIN_IDLE_RESUME_DURATION_MS
            return speedMeetsRequirement && displacementConfirmed && durationConfirmed
        }

        /**
         * Formats the live foreground notification title showing real-time distance (km) and fuel cost.
         */
        fun formatActiveDriveNotificationTitle(
            distanceKm: Double,
            unitDistance: String,
            currencySymbol: String,
            liveFuelCost: Double,
            isIdle: Boolean = false
        ): String {
            val distStr = String.format(Locale.US, "%.2f %s", distanceKm, unitDistance)
            val costStr = String.format(Locale.US, "%s%.2f", currencySymbol, liveFuelCost)
            return if (isIdle) {
                "Idle • $distStr • Fuel: $costStr"
            } else {
                "Live Drive • $distStr • Fuel: $costStr"
            }
        }

        /**
         * Formats the live foreground notification body showing speed, elapsed duration, vehicle, and idle countdown.
         */
        fun formatActiveDriveNotificationBody(
            speedKmh: Float,
            elapsedSeconds: Long,
            vehicleName: String,
            categoryName: String,
            isIdle: Boolean,
            idleSecondsRemaining: Long
        ): String {
            val mins = elapsedSeconds / 60
            val secs = elapsedSeconds % 60
            val elapsedStr = String.format(Locale.US, "%02d:%02d", mins, secs)
            val speedStr = "${speedKmh.toInt()} km/h"

            val statusSuffix = if (isIdle) {
                val remMins = idleSecondsRemaining / 60
                val remSecs = idleSecondsRemaining % 60
                "Stationary • Auto-stop in ${String.format(Locale.US, "%02d:%02d", remMins, remSecs)}"
            } else {
                "$vehicleName ($categoryName)"
            }

            return "Speed: $speedStr • Time: $elapsedStr • $statusSuffix"
        }

        /**
         * Pure verification rule: confirms that a vehicle is genuinely crossing the pre-set
         * motion detection threshold limit with both GPS speed and physical displacement > 5m
         * sustained for at least 2 to 3 seconds (>= 2500ms).
         */
        fun verifyVehicularThresholdCrossing(
            dopplerSpeedKmh: Double,
            displacementSpeedKmh: Double,
            thresholdKmh: Double,
            totalDisplacementMeters: Double,
            sustainedDurationMs: Long
        ): Boolean {
            if (thresholdKmh <= 0.0) return false
            val speedMeetsThreshold = dopplerSpeedKmh >= thresholdKmh &&
                displacementSpeedKmh >= (thresholdKmh * 0.85)
            val displacementConfirmed = totalDisplacementMeters > MIN_VEHICLE_DISPLACEMENT_METERS
            val durationConfirmed = sustainedDurationMs >= MIN_SUSTAINED_DURATION_MS
            return speedMeetsThreshold && displacementConfirmed && durationConfirmed
        }

        fun hasLocationPermission(context: Context): Boolean {
            val hasFine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            return hasFine || hasCoarse
        }

        fun hasBackgroundLocationPermission(context: Context): Boolean {
            if (!hasLocationPermission(context)) return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        private fun isAppInForeground(context: Context): Boolean {
            return try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
                val processes = am.runningAppProcesses ?: return false
                val pkg = context.packageName
                processes.any { proc ->
                    proc.processName == pkg &&
                        proc.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
                }
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Verifies Android 14+ (targetSDK 36) eligibility for starting a `location` Foreground Service:
         * Requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION, AND either ACCESS_BACKGROUND_LOCATION
         * ("Allow all the time") or the app currently in a foreground-eligible state.
         */
        fun canStartLocationForegroundService(context: Context): Boolean {
            if (!hasLocationPermission(context)) return false
            if (hasBackgroundLocationPermission(context)) return true
            return try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
                val processes = am.runningAppProcesses ?: return false
                val pkg = context.packageName
                processes.any { proc ->
                    proc.processName == pkg &&
                        proc.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                }
            } catch (_: Exception) {
                false
            }
        }

        fun ensureRunning(context: Context) {
            val intent = Intent(context, MileageTrackingService::class.java).apply {
                action = ACTION_ENSURE_RUNNING
            }
            startServiceSafely(context, intent)
        }

        fun startMonitoring(context: Context) {
            val intent = Intent(context, MileageTrackingService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            startServiceSafely(context, intent)
        }

        fun resumeMonitoring(context: Context) {
            val intent = Intent(context, MileageTrackingService::class.java).apply {
                action = ACTION_RESUME_MONITORING
            }
            startServiceSafely(context, intent)
        }

        fun pauseMonitoring(context: Context) {
            val intent = Intent(context, MileageTrackingService::class.java).apply {
                action = ACTION_PAUSE_MONITORING
            }
            startServiceSafely(context, intent)
        }

        fun stopMonitoring(context: Context) {
            val intent = Intent(context, MileageTrackingService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun start(
            context: Context,
            vehicleId: String?,
            categoryName: String,
            vehicleName: String
        ) {
            val intent = Intent(context, MileageTrackingService::class.java).apply {
                action = ACTION_START_ACTIVE_DRIVE
                putExtra(EXTRA_VEHICLE_ID, vehicleId)
                putExtra(EXTRA_CATEGORY_NAME, categoryName)
                putExtra(EXTRA_VEHICLE_NAME, vehicleName)
            }
            startServiceSafely(context, intent)
        }

        fun stop(
            context: Context,
            tolls: Double = 0.0,
            parking: Double = 0.0,
            endOdometer: Double? = null,
            notes: String = ""
        ) {
            val intent = Intent(context, MileageTrackingService::class.java).apply {
                action = ACTION_STOP_ACTIVE_DRIVE
                putExtra(EXTRA_TOLLS, tolls)
                putExtra(EXTRA_PARKING, parking)
                if (endOdometer != null) {
                    putExtra(EXTRA_END_ODOMETER, endOdometer)
                }
                putExtra(EXTRA_NOTES, notes)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        private fun startServiceSafely(context: Context, intent: Intent) {
            if (!canStartLocationForegroundService(context)) {
                return
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Deferred starting MileageTrackingService: ${e.message}")
            }
        }
    }
}
