package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.local.MileageDatabase
import com.example.data.local.MileageRepository
import com.example.data.model.TripCategory
import com.example.data.model.TripEntity
import com.example.data.model.TripPointEntity
import com.example.util.UnitConverter
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.sqrt

class MileageTrackingService : Service(), SensorEventListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locationMutex = Mutex()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var repository: MileageRepository
    private var sensorManager: SensorManager? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var locationCallback: LocationCallback? = null
    private var timerJob: Job? = null
    private var pauseExpiryJob: Job? = null
    private var geocodeJob: Job? = null
    private var startGeocodeJob: Job? = null

    private enum class ServiceMode {
        IDLE,
        PASSIVE_MONITORING,
        PAUSED_MONITORING,
        ACTIVE_DRIVE
    }

    @Volatile
    private var currentMode: ServiceMode = ServiceMode.IDLE

    @Volatile
    private var currentPausedUntilMillis: Long? = null

    // Adaptive passive GPS polling state (Standby-Only Battery Optimization when NO trip is active)
    @Volatile
    private var currentLocationIntervalMs: Long = 0L

    @Volatile
    private var lastPassiveMotionDetectedMs: Long = 0L

    // Passive auto-detection state (multi-fix linear >= 10m over >= 5s + OS Activity Recognition)
    private var lastPassiveLocation: Location? = null
    private var lastPassiveLocationWallMs: Long = 0L
    private var movingStartAnchorLocation: Location? = null
    private var movingAnchorWallTimestamp: Long = 0L
    private var firstMovingTimestamp: Long = 0L
    private var consecutiveMovingReadings: Int = 0
    private var cumulativeCandidatePathMeters: Double = 0.0

    // Google Play Services Activity Recognition & Transition state
    private var activityReceiver: BroadcastReceiver? = null
    private var activityTransitionPendingIntent: PendingIntent? = null
    private var activityUpdatePendingIntent: PendingIntent? = null
    private var activityRecognitionRegistered: Boolean = false
    @Volatile
    private var latestOsActivityType: Int = DetectedActivity.UNKNOWN
    @Volatile
    private var latestOsActivityConfidence: Int = 0
    @Volatile
    private var latestOsActivityTimestampMs: Long = 0L

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
    private var lastActiveLocationWallMs: Long = 0L
    private var lastActiveGpsFixWallMs: Long = 0L
    private var lastActiveGpsFixElapsedMs: Long = 0L
    private var driveStartTimeMillis: Long = 0L
    private var driveStartElapsedMs: Long = 0L
    private var lastDistanceAddedTimeMillis: Long = 0L
    private var lastDistanceAddedElapsedMs: Long = 0L
    private var lastMovedTimeMillis: Long = 0L
    private var lastMovedElapsedMs: Long = 0L
    private var stopTimeoutSeconds: Long = 480L
    @Volatile
    private var isAutoStartedActiveDrive: Boolean = false
    @Volatile
    private var autoStartLaunchElapsedMs: Long = 0L

    // Batched DB write state for TripPointEntity & throttled TripEntity row persistence
    private val pendingTripPoints = mutableListOf<TripPointEntity>()
    private var lastPointFlushWallMs: Long = 0L
    private var lastDbTripUpdateMeters: Double = 0.0
    private var lastDbTripUpdateWallMs: Long = 0L

    // Active drive continuity & true stationary detection state
    private val recentActiveSpeedSamples = ArrayDeque<Pair<Long, Float>>() // (wallTimestampMs, speedKmh)
    private var lastKnownValidGpsSpeedKmh: Double = 0.0
    private var idleAnchorLocation: Location? = null
    private var idleAnchorWallMs: Long = 0L

    // Throttled reverse-geocoding cache (never blocks location processing mutex)
    @Volatile
    private var lastGeocodedLat: Double = 0.0
    @Volatile
    private var lastGeocodedLng: Double = 0.0
    @Volatile
    private var lastGeocodedWallMs: Long = 0L
    @Volatile
    private var lastResolvedAddressCache: String = ""
    @Volatile
    private var hasResolvedStartAddress: Boolean = false
    @Volatile
    private var startCoordsLat: Double = 0.0
    @Volatile
    private var startCoordsLng: Double = 0.0
    @Volatile
    private var lastStartGeocodeAttemptWallMs: Long = 0L

    // Latched Idle Countdown & 5-Second >= 10 km/h Resume Verification State
    @Volatile
    private var isIdleLatched: Boolean = false
    private var idleResumeAnchorLocation: Location? = null
    private var idleResumeFirstMovingTimestamp: Long = 0L
    private var idleResumeConsecutiveMisses: Int = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        repository = MileageRepository(MileageDatabase.getInstance(applicationContext))
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_RESUME_MONITORING && currentMode != ServiceMode.ACTIVE_DRIVE) {
            currentPausedUntilMillis = null
            currentMode = ServiceMode.PASSIVE_MONITORING
        }
        if (action != ACTION_STOP_MONITORING && action != ACTION_STOP_ACTIVE_DRIVE) {
            if (!canStartLocationForegroundService(this)) {
                serviceScope.launch {
                    locationMutex.withLock { stopPassiveMonitoringLocked() }
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            if (!ensureImmediateForegroundNotification()) {
                serviceScope.launch {
                    locationMutex.withLock { stopPassiveMonitoringLocked() }
                    stopSelf()
                }
                return START_NOT_STICKY
            }
        }

        when (action) {
            null,
            ACTION_ENSURE_RUNNING,
            ACTION_START_MONITORING -> {
                restoreOrStartMonitoring(forceClearPause = false)
            }
            ACTION_RESUME_MONITORING -> {
                restoreOrStartMonitoring(forceClearPause = true)
            }
            ACTION_PAUSE_MONITORING -> {
                serviceScope.launch {
                    locationMutex.withLock {
                        if (currentMode != ServiceMode.ACTIVE_DRIVE) {
                            val settings = repository.getSettings()
                            enterPausedMonitoringStateLocked(settings.autoTrackingPausedUntil)
                        }
                    }
                }
            }
            ACTION_STOP_MONITORING -> {
                serviceScope.launch {
                    locationMutex.withLock {
                        if (currentMode != ServiceMode.ACTIVE_DRIVE) {
                            stopPassiveMonitoringLocked()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
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
        } else if (currentMode == ServiceMode.PAUSED_MONITORING &&
            hasBackgroundLocationPermission(applicationContext)
        ) {
            val targetPauseEnd = currentPausedUntilMillis
            if (targetPauseEnd != null && targetPauseEnd > 0L) {
                if (System.currentTimeMillis() >= targetPauseEnd) {
                    AutoResumeManager.scheduleImmediateWatchdogRestart(applicationContext, 1000L)
                } else {
                    AutoResumeManager.scheduleResume(applicationContext, targetPauseEnd)
                }
            }
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
                content = "Standby paused • Will resume automatically",
                ongoing = false
            )
            else -> buildNotification(
                title = "Auto-Drive Detection Active",
                content = "Background drive detection ready",
                ongoing = true
            )
        }
        return startForegroundWithNotification(notification)
    }

    private fun restoreOrStartMonitoring(forceClearPause: Boolean = false) {
        serviceScope.launch {
            locationMutex.withLock {
                val activeTrip = repository.getActiveTrip()
                if (activeTrip != null) {
                    if (currentMode != ServiceMode.ACTIVE_DRIVE || activeTripId != activeTrip.id) {
                        resumeExistingActiveDriveLocked(activeTrip)
                    }
                    return@withLock
                }
                if (currentMode != ServiceMode.ACTIVE_DRIVE) {
                    startPassiveMonitoringLocked(forceClearPause = forceClearPause)
                }
            }
        }
    }

    private suspend fun startPassiveMonitoringLocked(forceClearPause: Boolean = false) {
        pauseExpiryJob?.cancel()
        if (!canStartLocationForegroundService(this@MileageTrackingService)) {
            stopPassiveMonitoringLocked()
            stopSelf()
            return
        }
        var settings = repository.getSettings()
        if (!settings.autoTrackingEnabled) {
            stopPassiveMonitoringLocked()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val pausedUntil = settings.autoTrackingPausedUntil
        val pauseExpired = pausedUntil != null && pausedUntil > 0L && System.currentTimeMillis() >= pausedUntil
        if (forceClearPause || pauseExpired) {
            if (pausedUntil != null) {
                settings = settings.copy(autoTrackingPausedUntil = null)
                repository.updateSettings(settings)
            }
            AutoResumeManager.cancelPendingResume(applicationContext)
        }

        if (settings.isAutoTrackingPaused()) {
            enterPausedMonitoringStateLocked(settings.autoTrackingPausedUntil)
            return
        }

        // Standby-only battery optimization: release CPU WakeLock when no trip is active
        releaseWakeLock()
        currentPausedUntilMillis = null
        currentMode = ServiceMode.PASSIVE_MONITORING
        resetPassiveDetectionState()
        val effectiveFloorKmh = settings.autoDriveDetectionSpeedKmh.coerceIn(
            MIN_VEHICULAR_AUTO_START_FLOOR_KMH.toInt(),
            25
        )
        val thresholdDisplay = UnitConverter.toDisplaySpeed(
            effectiveFloorKmh.toDouble(),
            settings.unitDistance
        ).toInt().coerceAtLeast(1)
        val speedUnit = UnitConverter.speedUnitLabel(settings.unitDistance)
        val activeNotification = buildNotification(
            title = "Auto-Drive Detection Active",
            content = "Vehicle monitoring active (≥$thresholdDisplay $speedUnit • ≥10m / 5s)",
            ongoing = true
        )
        val promoted = startForegroundWithNotification(activeNotification)
        if (!promoted) return
        // Also explicitly notify NotificationManager so if the paused notification was swiped away,
        // the active notification is guaranteed to reappear immediately.
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, activeNotification)
        } catch (_: Exception) {}
        registerMotionSensors()
        registerActivityRecognitionUpdates()
        // Start in low-power passive standby gear (3000ms); shifts immediately to 1000ms on first motion
        registerLocationUpdates(
            intervalMs = PASSIVE_STANDBY_INTERVAL_MS,
            minUpdateIntervalMs = PASSIVE_STANDBY_MIN_INTERVAL_MS
        )
    }

    private fun enterPausedMonitoringStateLocked(pausedUntilMillis: Long?) {
        pauseExpiryJob?.cancel()
        currentPausedUntilMillis = pausedUntilMillis
        currentMode = ServiceMode.PAUSED_MONITORING
        resetPassiveDetectionState()
        unregisterLocationUpdates()
        unregisterMotionSensors()
        unregisterActivityRecognitionUpdates()
        releaseWakeLock()

        val subtitle = if (pausedUntilMillis == null || pausedUntilMillis == -1L) {
            "Paused until manually resumed"
        } else {
            val fmt = java.text.SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            "Paused • Auto-resumes at ${fmt.format(java.util.Date(pausedUntilMillis))}"
        }
        val pausedNotification = buildNotification(
            title = "Auto-Drive Detection Paused",
            content = subtitle,
            ongoing = false
        )
        startForegroundWithNotification(pausedNotification)
        // Detach foreground lock and post as non-ongoing so if the user swipes away the paused notification,
        // it stays hidden during the pause and cleanly reappears when the pause expires.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, pausedNotification)
        } catch (_: Exception) {}

        if (pausedUntilMillis != null && pausedUntilMillis > 0L) {
            AutoResumeManager.scheduleResume(applicationContext, pausedUntilMillis)
            pauseExpiryJob = serviceScope.launch {
                // Poll wall-clock time in short intervals so CPU sleep does not skew expiry detection
                while (System.currentTimeMillis() < pausedUntilMillis) {
                    val remainingMs = (pausedUntilMillis - System.currentTimeMillis()).coerceAtLeast(500L)
                    delay(remainingMs.coerceAtMost(5_000L))
                }
                locationMutex.withLock {
                    val latest = repository.getSettings()
                    if (latest.autoTrackingPausedUntil != null &&
                        latest.autoTrackingPausedUntil > 0L &&
                        System.currentTimeMillis() >= latest.autoTrackingPausedUntil
                    ) {
                        repository.updateSettings(latest.copy(autoTrackingPausedUntil = null))
                        AutoResumeManager.cancelPendingResume(applicationContext)
                        if (latest.autoTrackingEnabled && currentMode != ServiceMode.ACTIVE_DRIVE) {
                            startPassiveMonitoringLocked(forceClearPause = true)
                        }
                    }
                }
            }
        }
    }

    private fun stopPassiveMonitoringLocked() {
        pauseExpiryJob?.cancel()
        currentMode = ServiceMode.IDLE
        resetPassiveDetectionState()
        unregisterLocationUpdates()
        unregisterMotionSensors()
        unregisterActivityRecognitionUpdates()
        releaseWakeLock()
    }

    private fun resetPassiveDetectionState() {
        lastPassiveLocation = null
        lastPassiveLocationWallMs = 0L
        movingStartAnchorLocation = null
        movingAnchorWallTimestamp = 0L
        firstMovingTimestamp = 0L
        consecutiveMovingReadings = 0
        cumulativeCandidatePathMeters = 0.0
        lastPassiveMotionDetectedMs = 0L
    }

    private fun resetIdleResumeState() {
        idleResumeAnchorLocation = null
        idleResumeFirstMovingTimestamp = 0L
        idleResumeConsecutiveMisses = 0
    }

    private fun resetActiveContinuityState() {
        lastActiveLocation = null
        lastActiveLocationWallMs = 0L
        lastActiveGpsFixWallMs = 0L
        lastActiveGpsFixElapsedMs = 0L
        driveStartTimeMillis = 0L
        driveStartElapsedMs = 0L
        lastDistanceAddedTimeMillis = 0L
        lastDistanceAddedElapsedMs = 0L
        lastMovedTimeMillis = 0L
        lastMovedElapsedMs = 0L
        pendingTripPoints.clear()
        lastPointFlushWallMs = 0L
        lastDbTripUpdateMeters = 0.0
        lastDbTripUpdateWallMs = 0L
        recentActiveSpeedSamples.clear()
        lastKnownValidGpsSpeedKmh = 0.0
        idleAnchorLocation = null
        idleAnchorWallMs = 0L
        lastGeocodedLat = 0.0
        lastGeocodedLng = 0.0
        lastGeocodedWallMs = 0L
        lastResolvedAddressCache = ""
        hasResolvedStartAddress = false
        startCoordsLat = 0.0
        startCoordsLng = 0.0
        lastStartGeocodeAttemptWallMs = 0L
        isAutoStartedActiveDrive = false
        autoStartLaunchElapsedMs = 0L
        geocodeJob?.cancel()
        geocodeJob = null
        startGeocodeJob?.cancel()
        startGeocodeJob = null
    }

    private suspend fun bufferTripPointLocked(
        tripId: String,
        lat: Double,
        lng: Double,
        speed: Float,
        timestamp: Long
    ) {
        pendingTripPoints.add(
            TripPointEntity(
                tripId = tripId,
                latitude = lat,
                longitude = lng,
                speed = speed,
                timestamp = timestamp
            )
        )
        if (pendingTripPoints.size >= TRIP_POINT_BATCH_SIZE ||
            (timestamp - lastPointFlushWallMs) >= TRIP_POINT_FLUSH_INTERVAL_MS
        ) {
            flushPendingTripPointsLocked(timestamp)
        }
    }

    private suspend fun flushPendingTripPointsLocked(nowMs: Long = System.currentTimeMillis()) {
        if (pendingTripPoints.isEmpty()) return
        val batch = pendingTripPoints.toList()
        pendingTripPoints.clear()
        lastPointFlushWallMs = nowMs
        repository.addTripPointsBatch(batch)
    }

    private fun shouldPersistTripRowUpdate(nowMs: Long): Boolean {
        return (totalDistanceMeters - lastDbTripUpdateMeters) >= DB_TRIP_UPDATE_MIN_METERS ||
            (nowMs - lastDbTripUpdateWallMs) >= DB_TRIP_UPDATE_INTERVAL_MS
    }

    private suspend fun resumeExistingActiveDriveLocked(trip: TripEntity) {
        pauseExpiryJob?.cancel()
        acquireWakeLock()
        currentMode = ServiceMode.ACTIVE_DRIVE
        resetPassiveDetectionState()
        resetActiveContinuityState()
        isAutoStartedActiveDrive = false
        autoStartLaunchElapsedMs = 0L
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
        val nowElapsed = SystemClock.elapsedRealtime()
        val latestPointTs = repository.getLatestTripPointTimestamp(trip.id)
        val restoredLastMovedMs = latestPointTs?.takeIf { it in trip.startTime..now } ?: trip.startTime

        driveStartTimeMillis = trip.startTime
        driveStartElapsedMs = nowElapsed - (now - trip.startTime).coerceAtLeast(0L)
        lastMovedTimeMillis = restoredLastMovedMs
        lastMovedElapsedMs = nowElapsed - (now - restoredLastMovedMs).coerceAtLeast(0L)

        val isStaleMicroRestored = (now - trip.startTime) > (stopTimeoutSeconds * 1000L) &&
            trip.distanceKm < MIN_AUTO_SAVE_DISTANCE_KM
        val restoredIdleRefMs = when {
            isStaleMicroRestored -> restoredLastMovedMs
            latestPointTs != null && latestPointTs in trip.startTime..now -> restoredLastMovedMs
            else -> now
        }
        lastDistanceAddedTimeMillis = restoredIdleRefMs
        lastDistanceAddedElapsedMs = nowElapsed - (now - restoredIdleRefMs).coerceAtLeast(0L)
        lastActiveLocationWallMs = now
        lastActiveGpsFixWallMs = restoredIdleRefMs
        lastActiveGpsFixElapsedMs = lastDistanceAddedElapsedMs
        lastPointFlushWallMs = now
        lastDbTripUpdateMeters = totalDistanceMeters
        lastDbTripUpdateWallMs = now
        startCoordsLat = trip.startLat
        startCoordsLng = trip.startLng
        hasResolvedStartAddress = !isPlaceholderOrCoordinateAddress(trip.startAddress)
        lastResolvedAddressCache = trip.endAddress?.takeIf { !isPlaceholderOrCoordinateAddress(it) } ?: ""

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

        val initialIdleSecs = ((nowElapsed - lastDistanceAddedElapsedMs) / 1000L).coerceAtLeast(0L)
        val shouldLatchRestoredIdle = initialIdleSecs >= IDLE_START_DELAY_SECONDS
        isIdleLatched = shouldLatchRestoredIdle
        val remainingIdleSecs = (stopTimeoutSeconds - initialIdleSecs).coerceAtLeast(0L)
        val elapsedSecs = computeDisplayDriveElapsedSeconds(
            isIdle = shouldLatchRestoredIdle,
            driveStartElapsedMs = driveStartElapsedMs,
            lastMovedElapsedMs = lastMovedElapsedMs,
            nowElapsedMs = nowElapsed
        )
        val distKm = totalDistanceMeters / 1000.0
        val liveFuelCost = UnitConverter.calculateFuelCost(
            distanceKm = distKm,
            efficiencyVal = activeVehicleEfficiency,
            fuelPrice = activeFuelPrice,
            unitDistance = activeUnitDistance
        )

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
            isIdle = shouldLatchRestoredIdle,
            idleSecondsRemaining = remainingIdleSecs,
            idleTimeoutTotalSeconds = stopTimeoutSeconds,
            isWalkingDetected = false,
            activityStatus = if (shouldLatchRestoredIdle) "Stationary (Idle)" else "Driving",
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
        if (!hasResolvedStartAddress && (trip.startLat != 0.0 || trip.startLng != 0.0)) {
            lastStartGeocodeAttemptWallMs = now
            launchStartAddressRefresh(trip.id, trip.startLat, trip.startLng)
        }
    }

    private fun startActiveDrive(vehicleId: String?, categoryName: String, vehicleName: String) {
        serviceScope.launch {
            locationMutex.withLock {
                startActiveDriveLocked(vehicleId, categoryName, vehicleName)
            }
        }
    }

    private suspend fun startActiveDriveLocked(
        vehicleId: String?,
        categoryName: String,
        vehicleName: String,
        initialAnchorLocation: Location? = null,
        initialHandoffLocation: Location? = null,
        initialStartTimeMs: Long? = null,
        isAutoStarted: Boolean = false
    ) {
        // Idempotent guard against duplicate concurrent auto-start or manual start requests
        if (currentMode == ServiceMode.ACTIVE_DRIVE && activeTripId != null) {
            return
        }

        pauseExpiryJob?.cancel()
        acquireWakeLock()
        currentMode = ServiceMode.ACTIVE_DRIVE
        resetPassiveDetectionState()
        resetActiveContinuityState()
        isIdleLatched = false
        resetIdleResumeState()

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

        val now = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        isAutoStartedActiveDrive = isAutoStarted
        autoStartLaunchElapsedMs = if (isAutoStarted) nowElapsed else 0L
        val startMs = initialStartTimeMs?.takeIf { it in (now - 120_000L)..now } ?: now
        val handoffDistMeters = if (initialAnchorLocation != null && initialHandoffLocation != null) {
            initialAnchorLocation.distanceTo(initialHandoffLocation).toDouble().coerceIn(0.0, 2000.0)
        } else {
            0.0
        }

        totalDistanceMeters = handoffDistMeters
        driveStartTimeMillis = startMs
        driveStartElapsedMs = nowElapsed - (now - startMs).coerceAtLeast(0L)
        lastDistanceAddedTimeMillis = now
        lastDistanceAddedElapsedMs = nowElapsed
        lastMovedTimeMillis = if (handoffDistMeters > 0.0 || initialHandoffLocation != null) now else startMs
        lastMovedElapsedMs = if (handoffDistMeters > 0.0 || initialHandoffLocation != null) nowElapsed else driveStartElapsedMs
        lastActiveLocation = initialHandoffLocation ?: initialAnchorLocation
        lastActiveLocationWallMs = now
        lastActiveGpsFixWallMs = now
        lastActiveGpsFixElapsedMs = nowElapsed
        lastPointFlushWallMs = now
        lastDbTripUpdateMeters = handoffDistMeters
        lastDbTripUpdateWallMs = now

        val startLoc = initialAnchorLocation ?: initialHandoffLocation
        val startLat = startLoc?.latitude ?: 0.0
        val startLng = startLoc?.longitude ?: 0.0
        startCoordsLat = startLat
        startCoordsLng = startLng
        hasResolvedStartAddress = false
        val startAddr = if (startLat != 0.0 || startLng != 0.0) {
            String.format(Locale.US, "%.4f, %.4f", startLat, startLng)
        } else {
            "Detecting start location..."
        }

        val newTripId = repository.startTrip(
            vehicleId = activeVehicleId,
            category = activeCategory,
            startTime = startMs,
            startLat = startLat,
            startLng = startLng,
            startAddress = startAddr
        )
        activeTripId = newTripId

        if (initialAnchorLocation != null) {
            val anchorSpeed = if (initialAnchorLocation.hasSpeed()) initialAnchorLocation.speed * 3.6f else 0f
            bufferTripPointLocked(newTripId, initialAnchorLocation.latitude, initialAnchorLocation.longitude, anchorSpeed, startMs)
        }
        if (initialHandoffLocation != null) {
            val handoffSpeed = if (initialHandoffLocation.hasSpeed()) initialHandoffLocation.speed * 3.6f else 0f
            if (handoffSpeed > 0f) {
                appendActiveSpeedSample(now, handoffSpeed)
                lastKnownValidGpsSpeedKmh = handoffSpeed.toDouble()
            }
            bufferTripPointLocked(newTripId, initialHandoffLocation.latitude, initialHandoffLocation.longitude, handoffSpeed, now)
        }

        val distKm = totalDistanceMeters / 1000.0
        val liveFuelCost = UnitConverter.calculateFuelCost(
            distanceKm = distKm,
            efficiencyVal = activeVehicleEfficiency,
            fuelPrice = activeFuelPrice,
            unitDistance = activeUnitDistance
        )
        val initialSpeedKmh = if (initialHandoffLocation?.hasSpeed() == true) {
            (initialHandoffLocation.speed * 3.6f)
        } else {
            0f
        }
        val elapsedInitialSecs = ((now - startMs) / 1000L).coerceAtLeast(0L)

        val initialData = LiveTrackingData(
            isTracking = true,
            tripId = newTripId,
            distanceKm = distKm,
            speedKmh = initialSpeedKmh,
            elapsedSeconds = elapsedInitialSecs,
            latitude = initialHandoffLocation?.latitude ?: startLat,
            longitude = initialHandoffLocation?.longitude ?: startLng,
            startAddress = startAddr,
            currentAddress = if (startLat != 0.0 || startLng != 0.0) startAddr else "",
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

        TrackingStateManager.update { initialData }

        startForegroundWithNotification(
            buildActiveDriveNotification(initialData, activeUnitDistance)
        )

        if (startLat != 0.0 || startLng != 0.0) {
            lastStartGeocodeAttemptWallMs = now
            launchStartAddressRefresh(newTripId, startLat, startLng)
            if (initialHandoffLocation != null) {
                lastGeocodedLat = initialHandoffLocation.latitude
                lastGeocodedLng = initialHandoffLocation.longitude
                lastGeocodedWallMs = now
                launchActiveAddressRefresh(
                    newTripId,
                    initialHandoffLocation.latitude,
                    initialHandoffLocation.longitude
                )
            }
        }

        registerMotionSensors()
        registerLocationUpdates(
            intervalMs = ACTIVE_DRIVE_INTERVAL_MS,
            minUpdateIntervalMs = ACTIVE_DRIVE_MIN_INTERVAL_MS
        )
        startDriveTimerLoop()
    }

    private fun startDriveTimerLoop() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            var nextTickElapsedMs = SystemClock.elapsedRealtime() + 1000L
            while (currentMode == ServiceMode.ACTIVE_DRIVE) {
                val sleepMs = (nextTickElapsedMs - SystemClock.elapsedRealtime()).coerceIn(50L, 1000L)
                delay(sleepMs)
                val wakeElapsed = SystemClock.elapsedRealtime()
                while (nextTickElapsedMs <= wakeElapsed) {
                    nextTickElapsedMs += 1000L
                }

                val settings = repository.getSettings()
                var autoStopNote: String? = null
                var earlyAbortFalseStart = false
                var retryStartTripId: String? = null
                var retryStartLat = 0.0
                var retryStartLng = 0.0

                locationMutex.withLock {
                    if (currentMode != ServiceMode.ACTIVE_DRIVE || activeTripId == null) {
                        return@withLock
                    }

                    val now = System.currentTimeMillis()
                    val nowElapsed = SystemClock.elapsedRealtime()
                    val idleSecs = ((nowElapsed - lastDistanceAddedElapsedMs) / 1000L).coerceAtLeast(0L)

                    stopTimeoutSeconds = (settings.stopTimeoutMinutes.coerceIn(3, 30) * 60).toLong()
                    activeUnitDistance = settings.unitDistance

                    val hasFreshSensorSamples = pruneAndRefreshSensorMetrics(now)
                    val timeSinceLastGpsFixMs = if (lastActiveGpsFixElapsedMs > 0L) {
                        (nowElapsed - lastActiveGpsFixElapsedMs).coerceAtLeast(0L)
                    } else {
                        idleSecs * 1000L
                    }
                    val liveSmoothedSpeedKmh = computeSmoothedSpeedKmh(recentActiveSpeedSamples.toList(), now)
                    val effectiveSpeedForCheck = resolveEffectiveSpeedForStationaryCheck(
                        liveSmoothedSpeedKmh = liveSmoothedSpeedKmh,
                        lastKnownValidGpsSpeedKmh = lastKnownValidGpsSpeedKmh,
                        timeSinceLastGpsFixMs = timeSinceLastGpsFixMs,
                        hasFreshSensorSamples = hasFreshSensorSamples,
                        rmsAccelMps2 = latestRmsAccelMps2
                    )
                    val anchorDisplacementMeters = if (idleAnchorLocation != null && lastActiveLocation != null) {
                        idleAnchorLocation!!.distanceTo(lastActiveLocation!!).toDouble()
                    } else {
                        0.0
                    }
                    val anchorElapsedSecs = if (idleAnchorWallMs > 0L) {
                        ((now - idleAnchorWallMs) / 1000.0).coerceAtLeast(0.0)
                    } else {
                        0.0
                    }

                    if (idleSecs >= IDLE_START_DELAY_SECONDS && !isIdleLatched) {
                        val trueStationaryForLatch = isTrueStationaryState(
                            idleSeconds = idleSecs,
                            minIdleSeconds = IDLE_START_DELAY_SECONDS,
                            smoothedSpeedKmh = effectiveSpeedForCheck,
                            anchorDisplacementMeters = anchorDisplacementMeters,
                            anchorElapsedSeconds = anchorElapsedSecs,
                            hasFreshSensorSamples = hasFreshSensorSamples,
                            rmsAccelMps2 = latestRmsAccelMps2,
                            timeSinceLastGpsFixMs = timeSinceLastGpsFixMs
                        )
                        if (trueStationaryForLatch) {
                            isIdleLatched = true
                            if (idleAnchorLocation == null) {
                                idleAnchorLocation = lastActiveLocation
                                idleAnchorWallMs = now
                            }
                        } else if (timeSinceLastGpsFixMs <= RECENT_GPS_FIX_STALE_MS &&
                            effectiveSpeedForCheck >= MIN_ACTIVE_DRIVING_SPEED_KMH
                        ) {
                            // Fresh GPS telemetry confirms active vehicular motion -> keep active driving window fresh
                            lastDistanceAddedTimeMillis = now
                            lastDistanceAddedElapsedMs = nowElapsed
                            lastMovedTimeMillis = now
                            lastMovedElapsedMs = nowElapsed
                            idleAnchorLocation = null
                            idleAnchorWallMs = 0L
                        }
                    }

                    val driveAgeFromLaunchMs = if (isAutoStartedActiveDrive && autoStartLaunchElapsedMs > 0L) {
                        (nowElapsed - autoStartLaunchElapsedMs).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                    val isActivityFreshNow = latestOsActivityTimestampMs > 0L &&
                        (now - latestOsActivityTimestampMs) <= ACTIVITY_FRESH_WINDOW_MS
                    if (shouldAbortEarlyFalseAutoStart(
                            isAutoStarted = isAutoStartedActiveDrive,
                            driveAgeFromLaunchMs = driveAgeFromLaunchMs,
                            idleSeconds = idleSecs,
                            isIdleLatched = isIdleLatched,
                            totalDistanceMeters = totalDistanceMeters,
                            smoothedSpeedKmh = effectiveSpeedForCheck,
                            hasRecentGpsFix = timeSinceLastGpsFixMs <= RECENT_GPS_FIX_STALE_MS,
                            latestActivityType = latestOsActivityType,
                            latestActivityConfidence = latestOsActivityConfidence,
                            isActivityFresh = isActivityFreshNow
                        )
                    ) {
                        earlyAbortFalseStart = true
                    }

                    if (!earlyAbortFalseStart) {
                        val effectiveIdleSecs = ((nowElapsed - lastDistanceAddedElapsedMs) / 1000L).coerceAtLeast(0L)
                        val isStationaryIdle = isIdleLatched
                        val remainingIdleSecs = (stopTimeoutSeconds - effectiveIdleSecs).coerceAtLeast(0L)
                        val elapsedSecs = computeDisplayDriveElapsedSeconds(
                            isIdle = isStationaryIdle,
                            driveStartElapsedMs = driveStartElapsedMs,
                            lastMovedElapsedMs = lastMovedElapsedMs,
                            nowElapsedMs = nowElapsed
                        )

                        val distKm = totalDistanceMeters / 1000.0
                        val liveFuelCost = UnitConverter.calculateFuelCost(
                            distanceKm = distKm,
                            efficiencyVal = activeVehicleEfficiency,
                            fuelPrice = activeFuelPrice,
                            unitDistance = activeUnitDistance
                        )

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

                        if (effectiveIdleSecs >= stopTimeoutSeconds) {
                            val trueStationaryForAutoStop = isTrueStationaryState(
                                idleSeconds = effectiveIdleSecs,
                                minIdleSeconds = stopTimeoutSeconds,
                                smoothedSpeedKmh = effectiveSpeedForCheck,
                                anchorDisplacementMeters = anchorDisplacementMeters,
                                anchorElapsedSeconds = anchorElapsedSecs,
                                hasFreshSensorSamples = hasFreshSensorSamples,
                                rmsAccelMps2 = latestRmsAccelMps2,
                                timeSinceLastGpsFixMs = timeSinceLastGpsFixMs
                            )
                            if (trueStationaryForAutoStop) {
                                Log.d(TAG, "True stationary auto-stop timeout reached ($stopTimeoutSeconds s). Completing trip.")
                                autoStopNote = "Auto-completed after ${settings.stopTimeoutMinutes}m stationary"
                            } else {
                                Log.d(TAG, "Suppressed false auto-stop at $stopTimeoutSeconds s; smoothed GPS/sensors indicate motion.")
                                isIdleLatched = false
                                lastDistanceAddedTimeMillis = now
                                lastDistanceAddedElapsedMs = nowElapsed
                                if (timeSinceLastGpsFixMs <= RECENT_GPS_FIX_STALE_MS &&
                                    effectiveSpeedForCheck >= MIN_ACTIVE_DRIVING_SPEED_KMH
                                ) {
                                    lastMovedTimeMillis = now
                                    lastMovedElapsedMs = nowElapsed
                                }
                                idleAnchorLocation = null
                                idleAnchorWallMs = 0L
                                resetIdleResumeState()
                            }
                        }
                        if (autoStopNote == null &&
                            !hasResolvedStartAddress &&
                            (startCoordsLat != 0.0 || startCoordsLng != 0.0) &&
                            (now - lastStartGeocodeAttemptWallMs) >= START_GEOCODE_RETRY_INTERVAL_MS &&
                            startGeocodeJob?.isActive != true
                        ) {
                            lastStartGeocodeAttemptWallMs = now
                            retryStartTripId = activeTripId
                            retryStartLat = startCoordsLat
                            retryStartLng = startCoordsLng
                        }
                    }
                }

                if (earlyAbortFalseStart) {
                    abortEarlyFalseStartDrive()
                    break
                } else if (autoStopNote != null) {
                    finalizeActiveDriveCompletion(
                        tolls = 0.0,
                        parking = 0.0,
                        endOdometer = null,
                        notes = autoStopNote!!,
                        isAutoStop = true,
                        cancelTimerJob = false
                    )
                    break
                } else {
                    retryStartTripId?.let { sTripId ->
                        launchStartAddressRefresh(sTripId, retryStartLat, retryStartLng)
                    }
                }
            }
        }
    }

    private suspend fun abortEarlyFalseStartDrive() {
        withContext(NonCancellable) {
            val tripIdToDiscard: String?
            locationMutex.withLock {
                timerJob = null
                val currentTripId = activeTripId
                if (currentMode != ServiceMode.ACTIVE_DRIVE || currentTripId == null) {
                    return@withContext
                }
                tripIdToDiscard = currentTripId
                currentMode = ServiceMode.IDLE
                activeTripId = null
                isIdleLatched = false
                resetIdleResumeState()
                resetActiveContinuityState()
                TrackingStateManager.reset()
                releaseWakeLock()
            }

            if (tripIdToDiscard != null) {
                Log.d(TAG, "Discarding early false-start auto-drive $tripIdToDiscard (<25m within 45s).")
                repository.deleteTripById(tripIdToDiscard)
            }

            locationMutex.withLock {
                if (currentMode == ServiceMode.IDLE) {
                    val settings = repository.getSettings()
                    when {
                        !settings.autoTrackingEnabled -> {
                            stopPassiveMonitoringLocked()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                        settings.isAutoTrackingPaused() -> {
                            enterPausedMonitoringStateLocked(settings.autoTrackingPausedUntil)
                        }
                        else -> {
                            startPassiveMonitoringLocked(forceClearPause = false)
                        }
                    }
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
        serviceScope.launch {
            finalizeActiveDriveCompletion(
                tolls = tolls,
                parking = parking,
                endOdometer = endOdometer,
                notes = notes,
                isAutoStop = false,
                cancelTimerJob = true
            )
        }
    }

    private suspend fun finalizeActiveDriveCompletion(
        tolls: Double,
        parking: Double,
        endOdometer: Double?,
        notes: String,
        isAutoStop: Boolean = false,
        cancelTimerJob: Boolean = true
    ) {
        withContext(NonCancellable) {
            val tripIdToFinish: String?
            val finalDistKm: Double
            val finalLoc: Location?
            val currentState: LiveTrackingData
            val endTimeMs: Long
            val capturedStartLat: Double
            val capturedStartLng: Double
            val capturedNeedsStartResolve: Boolean

            locationMutex.withLock {
                if (cancelTimerJob) {
                    timerJob?.cancel()
                    timerJob = null
                } else {
                    timerJob = null
                }
                val currentTripId = activeTripId
                if (currentMode != ServiceMode.ACTIVE_DRIVE || currentTripId == null) {
                    return@withContext
                }
                tripIdToFinish = currentTripId
                val now = System.currentTimeMillis()
                flushPendingTripPointsLocked(now)
                finalDistKm = totalDistanceMeters / 1000.0
                finalLoc = lastActiveLocation
                currentState = TrackingStateManager.trackingData.value
                endTimeMs = computeAutoStopEndTimeMillis(
                    isAutoStop = isAutoStop,
                    driveStartTimeMillis = driveStartTimeMillis,
                    lastMovedTimeMillis = lastMovedTimeMillis,
                    nowMs = now
                )
                capturedStartLat = startCoordsLat
                capturedStartLng = startCoordsLng
                capturedNeedsStartResolve = !hasResolvedStartAddress ||
                    isPlaceholderOrCoordinateAddress(currentState.startAddress)

                currentMode = ServiceMode.IDLE
                activeTripId = null
                isIdleLatched = false
                resetIdleResumeState()
                resetActiveContinuityState()
                TrackingStateManager.reset()
                releaseWakeLock()
            }

            if (tripIdToFinish != null) {
                if (isAutoStop && finalDistKm < MIN_AUTO_SAVE_DISTANCE_KM) {
                    Log.d(TAG, "Discarding micro auto-stop trip ($finalDistKm km < $MIN_AUTO_SAVE_DISTANCE_KM km).")
                    repository.deleteTripById(tripIdToFinish)
                } else {
                    val endLat = finalLoc?.latitude ?: currentState.latitude
                    val endLng = finalLoc?.longitude ?: currentState.longitude
                    val existingBeforeComplete = repository.getTripById(tripIdToFinish)
                    val endCoordFallback = String.format(Locale.US, "%.4f, %.4f", endLat, endLng)
                    val fallbackEndAddr = currentState.currentAddress
                        .takeIf { !isPlaceholderOrCoordinateAddress(it) }
                        ?: existingBeforeComplete?.endAddress?.takeIf { !isPlaceholderOrCoordinateAddress(it) }
                        ?: currentState.startAddress.takeIf { !isPlaceholderOrCoordinateAddress(it) }
                        ?: endCoordFallback
                    val resolvedEndAddress = if (endLat != 0.0 || endLng != 0.0) {
                        withTimeoutOrNull(3000L) {
                            resolveAddress(endLat, endLng)
                        }?.takeIf { !isPlaceholderOrCoordinateAddress(it) } ?: fallbackEndAddr
                    } else {
                        fallbackEndAddr
                    }

                    val startLatForSave = existingBeforeComplete?.startLat?.takeIf { it != 0.0 } ?: capturedStartLat
                    val startLngForSave = existingBeforeComplete?.startLng?.takeIf { it != 0.0 } ?: capturedStartLng
                    val needsStartResolve = (capturedNeedsStartResolve ||
                        existingBeforeComplete == null ||
                        isPlaceholderOrCoordinateAddress(existingBeforeComplete.startAddress)) &&
                        (startLatForSave != 0.0 || startLngForSave != 0.0)
                    val preSaveStartAddress = if (needsStartResolve) {
                        withTimeoutOrNull(3000L) {
                            resolveAddress(startLatForSave, startLngForSave)
                        }?.takeIf { !isPlaceholderOrCoordinateAddress(it) }
                            ?: currentState.startAddress.takeIf { !isPlaceholderOrCoordinateAddress(it) }
                    } else {
                        null
                    }

                    repository.completeTrip(
                        tripId = tripIdToFinish,
                        endTime = endTimeMs,
                        endLat = endLat,
                        endLng = endLng,
                        endAddress = resolvedEndAddress,
                        distanceKm = finalDistKm,
                        tolls = tolls,
                        parking = parking,
                        endOdometer = endOdometer,
                        notes = notes,
                        startAddressOverride = preSaveStartAddress
                    )
                }
            }

            locationMutex.withLock {
                // Only transition back to passive monitoring if no new drive was started while geocoding completed
                if (currentMode == ServiceMode.IDLE) {
                    val settings = repository.getSettings()
                    if (settings.autoTrackingEnabled) {
                        startPassiveMonitoringLocked(forceClearPause = false)
                    } else {
                        stopPassiveMonitoringLocked()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
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

    private fun registerActivityRecognitionUpdates() {
        if (activityRecognitionRegistered || !hasActivityRecognitionPermission(this)) return
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null) return
                    val now = System.currentTimeMillis()
                    if (ActivityTransitionResult.hasResult(intent)) {
                        val transitionResult = ActivityTransitionResult.extractResult(intent)
                        transitionResult?.transitionEvents?.forEach { event ->
                            if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                                latestOsActivityType = event.activityType
                                latestOsActivityConfidence = 100
                                latestOsActivityTimestampMs = now
                            } else if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT &&
                                latestOsActivityType == event.activityType
                            ) {
                                latestOsActivityConfidence = 0
                            }
                        }
                    }
                    if (ActivityRecognitionResult.hasResult(intent)) {
                        val recognitionResult = ActivityRecognitionResult.extractResult(intent)
                        if (recognitionResult != null) {
                            val inVehicleConf = recognitionResult.getActivityConfidence(DetectedActivity.IN_VEHICLE)
                            val best = recognitionResult.mostProbableActivity
                            if (inVehicleConf >= 60) {
                                latestOsActivityType = DetectedActivity.IN_VEHICLE
                                latestOsActivityConfidence = inVehicleConf
                                latestOsActivityTimestampMs = now
                            } else if (best.type != DetectedActivity.TILTING && best.type != DetectedActivity.UNKNOWN) {
                                latestOsActivityType = best.type
                                latestOsActivityConfidence = best.confidence
                                latestOsActivityTimestampMs = now
                            }
                        }
                    }
                }
            }
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(ACTION_ACTIVITY_RECOGNITION_UPDATE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            activityReceiver = receiver

            val mutableFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val transIntent = Intent(ACTION_ACTIVITY_RECOGNITION_UPDATE).setPackage(packageName)
            val transPi = PendingIntent.getBroadcast(
                this,
                REQUEST_CODE_ACTIVITY_TRANSITION,
                transIntent,
                mutableFlags
            )
            activityTransitionPendingIntent = transPi

            val updateIntent = Intent(ACTION_ACTIVITY_RECOGNITION_UPDATE).setPackage(packageName)
            val updatePi = PendingIntent.getBroadcast(
                this,
                REQUEST_CODE_ACTIVITY_UPDATES,
                updateIntent,
                mutableFlags
            )
            activityUpdatePendingIntent = updatePi

            val transitions = listOf(
                DetectedActivity.IN_VEHICLE,
                DetectedActivity.WALKING,
                DetectedActivity.RUNNING,
                DetectedActivity.ON_BICYCLE,
                DetectedActivity.STILL
            ).flatMap { activityType ->
                listOf(
                    ActivityTransition.Builder()
                        .setActivityType(activityType)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build(),
                    ActivityTransition.Builder()
                        .setActivityType(activityType)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                        .build()
                )
            }

            val client = ActivityRecognition.getClient(this)
            client.requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), transPi)
            client.requestActivityUpdates(ACTIVITY_UPDATE_INTERVAL_MS, updatePi)
            activityRecognitionRegistered = true
        } catch (se: SecurityException) {
            Log.w(TAG, "Activity recognition permission denied: ${se.message}")
            unregisterActivityRecognitionUpdates()
        } catch (e: Exception) {
            Log.w(TAG, "Activity recognition unavailable: ${e.message}")
            unregisterActivityRecognitionUpdates()
        }
    }

    private fun unregisterActivityRecognitionUpdates() {
        activityRecognitionRegistered = false
        try {
            val client = ActivityRecognition.getClient(this)
            activityTransitionPendingIntent?.let { pi ->
                client.removeActivityTransitionUpdates(pi)
                pi.cancel()
            }
            activityUpdatePendingIntent?.let { pi ->
                client.removeActivityUpdates(pi)
                pi.cancel()
            }
        } catch (_: Exception) {}
        activityTransitionPendingIntent = null
        activityUpdatePendingIntent = null
        activityReceiver?.let { r ->
            try {
                unregisterReceiver(r)
            } catch (_: Exception) {}
        }
        activityReceiver = null
        latestOsActivityType = DetectedActivity.UNKNOWN
        latestOsActivityConfidence = 0
        latestOsActivityTimestampMs = 0L
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val ev = event ?: return
        val now = System.currentTimeMillis()

        if (ev.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION || ev.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            if (ev.values.size < 3) return
            val isLinear = ev.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION
            val currentRms: Float
            synchronized(accelSampleLock) {
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
            locationMutex.withLock {
                if (currentMode != ServiceMode.PASSIVE_MONITORING) return@withLock

                val settings = repository.getSettings()
                if (!settings.autoTrackingEnabled) return@withLock
                if (settings.isAutoTrackingPaused()) {
                    enterPausedMonitoringStateLocked(settings.autoTrackingPausedUntil)
                    return@withLock
                }

                val nowWallMs = System.currentTimeMillis()
                val rawSettingKmh = settings.autoDriveDetectionSpeedKmh.coerceIn(1, 25).toDouble()
                val thresholdKmh = maxOf(rawSettingKmh, MIN_VEHICULAR_AUTO_START_FLOOR_KMH)

                val prev = lastPassiveLocation
                val prevWallMs = lastPassiveLocationWallMs
                if (prev == null) {
                    if (!location.hasAccuracy() || location.accuracy <= MAX_PASSIVE_GPS_ACCURACY_METERS) {
                        lastPassiveLocation = location
                        lastPassiveLocationWallMs = nowWallMs
                    }
                    return@withLock
                }

                // Ignore out-of-order cached fixes
                if (location.time > 0L && prev.time > 0L && location.time <= prev.time) {
                    return@withLock
                }

                val now = if (location.time > 0L) location.time else nowWallMs
                val deltaMs = if (location.time > 0L && prev.time > 0L) {
                    (location.time - prev.time).coerceAtLeast(1L)
                } else {
                    (nowWallMs - prevWallMs).coerceAtLeast(1L)
                }

                // Sub-second burst guard: ignore duplicate fused fixes arriving too fast (< 800 ms)
                // without resetting `prev` so displacement speed cannot spike from millisecond jitter.
                if (deltaMs < MIN_PASSIVE_FIX_DELTA_MS) {
                    return@withLock
                }

                val stepDistMeters = prev.distanceTo(location).toDouble()
                val deltaSeconds = deltaMs / 1000.0
                val displacementSpeedKmh = (stepDistMeters / deltaSeconds) * 3.6
                val hasHardwareDoppler = location.hasSpeed() && location.speed >= 0f
                val dopplerSpeedKmh = if (hasHardwareDoppler) (location.speed * 3.6) else displacementSpeedKmh
                val hasFreshSensorSamples = pruneAndRefreshSensorMetrics(nowWallMs)
                val isActivityFresh = latestOsActivityTimestampMs > 0L &&
                    (nowWallMs - latestOsActivityTimestampMs) <= ACTIVITY_FRESH_WINDOW_MS

                val candidateDecision = evaluatePassiveAutoStartCandidate(
                    accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                    deltaMs = deltaMs,
                    stepDistMeters = stepDistMeters,
                    dopplerSpeedKmh = dopplerSpeedKmh,
                    displacementSpeedKmh = displacementSpeedKmh,
                    thresholdKmh = thresholdKmh,
                    hasMotionSensor = hasMotionSensorHardware,
                    hasFreshSensorSamples = hasFreshSensorSamples,
                    rmsAccelMps2 = latestRmsAccelMps2,
                    hasHardwareDopplerSpeed = hasHardwareDoppler,
                    latestActivityType = latestOsActivityType,
                    latestActivityConfidence = latestOsActivityConfidence,
                    isActivityFresh = isActivityFresh
                )

                if (candidateDecision == PassiveCandidateDecision.IGNORE_BAD_ACCURACY) {
                    return@withLock
                }

                lastPassiveLocation = location
                lastPassiveLocationWallMs = nowWallMs

                if (candidateDecision == PassiveCandidateDecision.RESET_STALE_ANCHOR) {
                    movingStartAnchorLocation = null
                    movingAnchorWallTimestamp = 0L
                    firstMovingTimestamp = 0L
                    consecutiveMovingReadings = 0
                    cumulativeCandidatePathMeters = 0.0
                    return@withLock
                }

                // Adaptive passive GPS gear management (Standby-Only Battery Optimization):
                // Shift to 1-second High-Accuracy gear on confirmed candidate movement (or high Doppler),
                // and return to 3-second standby gear after 30 seconds of zero movement.
                val shouldEngageHighFreqPassive = candidateDecision == PassiveCandidateDecision.ACCUMULATE_MOVING_STEP ||
                    (hasHardwareDoppler && dopplerSpeedKmh >= 8.0 && stepDistMeters >= 2.0)
                if (shouldEngageHighFreqPassive) {
                    lastPassiveMotionDetectedMs = nowWallMs
                    if (currentMode == ServiceMode.PASSIVE_MONITORING && currentLocationIntervalMs != PASSIVE_ACTIVE_INTERVAL_MS) {
                        serviceScope.launch(Dispatchers.Main) {
                            if (currentMode == ServiceMode.PASSIVE_MONITORING && currentLocationIntervalMs != PASSIVE_ACTIVE_INTERVAL_MS) {
                                registerLocationUpdates(
                                    intervalMs = PASSIVE_ACTIVE_INTERVAL_MS,
                                    minUpdateIntervalMs = PASSIVE_ACTIVE_MIN_INTERVAL_MS
                                )
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

                if (candidateDecision == PassiveCandidateDecision.ACCUMULATE_MOVING_STEP) {
                    if (movingStartAnchorLocation == null) {
                        movingStartAnchorLocation = prev
                        movingAnchorWallTimestamp = if (prev.time > 0L) prev.time else (now - deltaMs)
                        // Start the 5-second sustained confirmation clock at the first moving fix (not backdated to `prev`),
                        // preventing a single 3s standby jump from immediately clearing the timer.
                        firstMovingTimestamp = now
                        consecutiveMovingReadings = 1
                        cumulativeCandidatePathMeters = stepDistMeters
                    } else {
                        consecutiveMovingReadings++
                        cumulativeCandidatePathMeters += stepDistMeters
                    }

                    val anchorLoc = movingStartAnchorLocation ?: prev
                    val totalDisplacementFromAnchorMeters = anchorLoc.distanceTo(location).toDouble()
                    val sustainedDurationMs = (now - firstMovingTimestamp).coerceAtLeast(0L)
                    val pathLinearityRatio = if (cumulativeCandidatePathMeters > 0.0) {
                        (totalDisplacementFromAnchorMeters / cumulativeCandidatePathMeters).coerceIn(0.0, 1.0)
                    } else {
                        0.0
                    }

                    if (verifyVehicularThresholdCrossing(
                            dopplerSpeedKmh = dopplerSpeedKmh,
                            displacementSpeedKmh = displacementSpeedKmh,
                            thresholdKmh = thresholdKmh,
                            totalDisplacementMeters = totalDisplacementFromAnchorMeters,
                            sustainedDurationMs = sustainedDurationMs,
                            consecutiveMovingFixes = consecutiveMovingReadings,
                            pathLinearityRatio = pathLinearityRatio,
                            hasHardwareDopplerSpeed = hasHardwareDoppler,
                            latestActivityType = latestOsActivityType,
                            latestActivityConfidence = latestOsActivityConfidence,
                            isActivityFresh = isActivityFresh
                        )
                    ) {
                        val defaultVeh = repository.getDefaultVehicle()
                        val category = repository.resolveCategoryBySchedule(System.currentTimeMillis())
                        if (currentMode == ServiceMode.PASSIVE_MONITORING) {
                            val anchorTimestamp = movingAnchorWallTimestamp.takeIf { it > 0L } ?: (now - deltaMs)
                            startActiveDriveLocked(
                                vehicleId = defaultVeh?.id,
                                categoryName = category.name,
                                vehicleName = defaultVeh?.name ?: "Default Vehicle",
                                initialAnchorLocation = anchorLoc,
                                initialHandoffLocation = location,
                                initialStartTimeMs = anchorTimestamp,
                                isAutoStarted = true
                            )
                        }
                    }
                } else {
                    movingStartAnchorLocation = null
                    movingAnchorWallTimestamp = 0L
                    firstMovingTimestamp = 0L
                    consecutiveMovingReadings = 0
                    cumulativeCandidatePathMeters = 0.0
                }
            }
        }
    }

    private fun handleActiveDriveLocation(location: Location) {
        if (location.hasAccuracy() && location.accuracy > MAX_ACTIVE_GPS_ACCURACY_METERS) return

        serviceScope.launch {
            var geocodeTripId: String? = null
            var geocodeLat = 0.0
            var geocodeLng = 0.0
            var startGeocodeTripId: String? = null
            var startGeoLat = 0.0
            var startGeoLng = 0.0

            locationMutex.withLock {
                if (currentMode != ServiceMode.ACTIVE_DRIVE || activeTripId == null) return@withLock

                val tripId = activeTripId ?: return@withLock
                val now = System.currentTimeMillis()
                val nowElapsed = SystemClock.elapsedRealtime()
                lastActiveGpsFixWallMs = now
                lastActiveGpsFixElapsedMs = nowElapsed

                val prev = lastActiveLocation
                val rawSpeedKmh = if (location.hasSpeed()) (location.speed * 3.6f) else 0f

                if (prev == null) {
                    lastActiveLocation = location
                    lastActiveLocationWallMs = now
                    val coordFallback = String.format(Locale.US, "%.4f, %.4f", location.latitude, location.longitude)
                    if (rawSpeedKmh > 0f) {
                        appendActiveSpeedSample(now, rawSpeedKmh)
                    }

                    val existing = repository.getTripById(tripId)
                    val keepExistingStartCoords = existing != null && (existing.startLat != 0.0 || existing.startLng != 0.0)
                    val effectiveStartLat = if (keepExistingStartCoords) existing!!.startLat else location.latitude
                    val effectiveStartLng = if (keepExistingStartCoords) existing!!.startLng else location.longitude
                    startCoordsLat = effectiveStartLat
                    startCoordsLng = effectiveStartLng

                    val existingStartResolved = existing?.startAddress?.takeIf { !isPlaceholderOrCoordinateAddress(it) }
                    hasResolvedStartAddress = existingStartResolved != null
                    val initialStartAddr = existingStartResolved ?: String.format(
                        Locale.US, "%.4f, %.4f", effectiveStartLat, effectiveStartLng
                    )
                    val initialCurrentAddr = lastResolvedAddressCache.takeIf { !isPlaceholderOrCoordinateAddress(it) }
                        ?: coordFallback

                    if (existing != null && !keepExistingStartCoords) {
                        repository.updateTrip(
                            existing.copy(
                                startLat = effectiveStartLat,
                                startLng = effectiveStartLng,
                                startAddress = initialStartAddr
                            )
                        )
                    }
                    bufferTripPointLocked(tripId, location.latitude, location.longitude, rawSpeedKmh, now)

                    var firstSnapshot = LiveTrackingData()
                    TrackingStateManager.update { current ->
                        val bestStartAddr = current.startAddress.takeIf { !isPlaceholderOrCoordinateAddress(it) }
                            ?: initialStartAddr
                        firstSnapshot = current.copy(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            startAddress = bestStartAddr,
                            currentAddress = initialCurrentAddr,
                            speedKmh = if (rawSpeedKmh >= MIN_ACTIVE_DRIVING_SPEED_KMH) rawSpeedKmh else 0f,
                            isWalkingDetected = false,
                            activityStatus = "Driving",
                            linearAccelMps2 = latestLinearAccelMps2,
                            rmsAccelMps2 = latestRmsAccelMps2
                        )
                        firstSnapshot
                    }
                    updateActiveDriveNotification(firstSnapshot, activeUnitDistance)

                    if (!hasResolvedStartAddress && (effectiveStartLat != 0.0 || effectiveStartLng != 0.0)) {
                        lastStartGeocodeAttemptWallMs = now
                        startGeocodeTripId = tripId
                        startGeoLat = effectiveStartLat
                        startGeoLng = effectiveStartLng
                    }
                    geocodeTripId = tripId
                    geocodeLat = location.latitude
                    geocodeLng = location.longitude
                    lastGeocodedLat = location.latitude
                    lastGeocodedLng = location.longitude
                    lastGeocodedWallMs = now
                    return@withLock
                }

                // Discard duplicate or out-of-order cached fixes that otherwise distort speed/step math
                if (location.time > 0L && prev.time > 0L && location.time <= prev.time) {
                    return@withLock
                }

                val deltaMs = if (location.time > 0L && prev.time > 0L) {
                    (location.time - prev.time).coerceAtLeast(250L)
                } else {
                    (now - lastActiveLocationWallMs).coerceAtLeast(250L)
                }
                val deltaSeconds = deltaMs / 1000.0
                val deltaMeters = prev.distanceTo(location).toDouble()
                val displacementSpeedKmh = (deltaMeters / deltaSeconds) * 3.6
                val hasDoppler = location.hasSpeed()
                val dopplerSpeedKmh = if (hasDoppler) (location.speed * 3.6) else displacementSpeedKmh

                val sampleSpeedKmh = when {
                    hasDoppler && dopplerSpeedKmh > 0.0 -> dopplerSpeedKmh.toFloat()
                    deltaMeters >= MIN_COMMIT_STEP_METERS && deltaSeconds >= 0.5 -> displacementSpeedKmh.toFloat()
                    else -> rawSpeedKmh
                }
                appendActiveSpeedSample(now, sampleSpeedKmh)
                val smoothedSpeedKmh = computeSmoothedSpeedKmh(recentActiveSpeedSamples.toList(), now)
                lastKnownValidGpsSpeedKmh = smoothedSpeedKmh

                val effectiveSpeedKmh = maxOf(dopplerSpeedKmh, smoothedSpeedKmh).toFloat()
                val hasFreshSensorSamples = pruneAndRefreshSensorMetrics(now)

                if (idleAnchorLocation == null && isIdleLatched) {
                    idleAnchorLocation = prev
                    idleAnchorWallMs = now
                }
                val anchorLocForStep = idleAnchorLocation ?: prev
                val anchorDisplacementMeters = anchorLocForStep.distanceTo(location).toDouble()
                val anchorElapsedSeconds = if (idleAnchorLocation != null && idleAnchorWallMs > 0L) {
                    ((now - idleAnchorWallMs) / 1000.0).coerceAtLeast(deltaSeconds)
                } else {
                    deltaSeconds
                }

                val idleSecsNow = ((nowElapsed - lastDistanceAddedElapsedMs) / 1000L).coerceAtLeast(0L)
                if (idleSecsNow >= IDLE_START_DELAY_SECONDS && !isIdleLatched) {
                    val trueStationary = isTrueStationaryState(
                        idleSeconds = idleSecsNow,
                        minIdleSeconds = IDLE_START_DELAY_SECONDS,
                        smoothedSpeedKmh = smoothedSpeedKmh,
                        anchorDisplacementMeters = anchorDisplacementMeters,
                        anchorElapsedSeconds = anchorElapsedSeconds,
                        hasFreshSensorSamples = hasFreshSensorSamples,
                        rmsAccelMps2 = latestRmsAccelMps2,
                        timeSinceLastGpsFixMs = 0L
                    )
                    if (trueStationary) {
                        isIdleLatched = true
                        if (idleAnchorLocation == null) {
                            idleAnchorLocation = prev
                            idleAnchorWallMs = now
                        }
                    }
                }

                // -----------------------------------------------------------------
                // CASE 1: Vehicle is currently in LATCHED IDLE COUNTDOWN mode.
                // Cancels idle only on verified >= 25m sustained driving escape OR >= 5s >= 10 km/h resume.
                // -----------------------------------------------------------------
                if (isIdleLatched) {
                    val anchorSpeedKmh = if (anchorElapsedSeconds > 0.0) {
                        (anchorDisplacementMeters / anchorElapsedSeconds) * 3.6
                    } else 0.0
                    val maxResumeStepMeters = max(250.0, deltaSeconds * MAX_PLAUSIBLE_SPEED_MPS)
                    val positiveDopplerForEscape = !hasDoppler || dopplerSpeedKmh >= (MIN_ACTIVE_DRIVING_SPEED_KMH * 0.85)
                    val anchorEscapeTriggered = anchorDisplacementMeters >= ANCHOR_ESCAPE_DISPLACEMENT_METERS &&
                        anchorSpeedKmh >= (MIN_ACTIVE_DRIVING_SPEED_KMH * 0.85) &&
                        positiveDopplerForEscape &&
                        deltaMeters <= maxResumeStepMeters

                    val meetsResumeStep = dopplerSpeedKmh >= MIN_ACTIVE_DRIVING_SPEED_KMH &&
                        displacementSpeedKmh >= (MIN_ACTIVE_DRIVING_SPEED_KMH * 0.70) &&
                        deltaMeters in 1.0..maxResumeStepMeters

                    val isSlowMicroStepWhileMoving = idleResumeAnchorLocation != null &&
                        deltaMeters < 1.0 &&
                        (dopplerSpeedKmh >= MIN_ACTIVE_DRIVING_SPEED_KMH ||
                            smoothedSpeedKmh >= MIN_ACTIVE_DRIVING_SPEED_KMH)

                    if (anchorEscapeTriggered || meetsResumeStep || isSlowMicroStepWhileMoving) {
                        idleResumeConsecutiveMisses = 0
                        if (idleResumeAnchorLocation == null) {
                            idleResumeAnchorLocation = prev
                            idleResumeFirstMovingTimestamp = if (prev.time > 0L) prev.time else (now - deltaMs)
                        }
                        val locTimestamp = if (location.time > 0L) location.time else now
                        val totalDisplacementFromIdleAnchor = (idleResumeAnchorLocation?.distanceTo(location) ?: deltaMeters.toFloat()).toDouble()
                        val sustainedResumeMs = (locTimestamp - idleResumeFirstMovingTimestamp).coerceAtLeast(0L)

                        val verifiedResume = anchorEscapeTriggered || verifyIdleToDrivingResume(
                            dopplerSpeedKmh = dopplerSpeedKmh,
                            displacementSpeedKmh = displacementSpeedKmh,
                            totalDisplacementMeters = totalDisplacementFromIdleAnchor,
                            sustainedDurationMs = sustainedResumeMs
                        )

                        if (verifiedResume) {
                            isIdleLatched = false
                            val maxCommit = max(500.0, maxResumeStepMeters)
                            val resumedDistMeters = if (anchorEscapeTriggered) {
                                deltaMeters.coerceIn(0.0, maxCommit)
                            } else {
                                totalDisplacementFromIdleAnchor.coerceIn(0.0, maxCommit)
                            }
                            totalDistanceMeters += resumedDistMeters
                            lastActiveLocation = location
                            lastActiveLocationWallMs = now
                            lastDistanceAddedTimeMillis = now
                            lastDistanceAddedElapsedMs = nowElapsed
                            lastMovedTimeMillis = now
                            lastMovedElapsedMs = nowElapsed
                            idleAnchorLocation = null
                            idleAnchorWallMs = 0L
                            resetIdleResumeState()

                            val distKm = totalDistanceMeters / 1000.0
                            val liveFuelCost = UnitConverter.calculateFuelCost(
                                distanceKm = distKm,
                                efficiencyVal = activeVehicleEfficiency,
                                fuelPrice = activeFuelPrice,
                                unitDistance = activeUnitDistance
                            )

                            val displayAddr = lastResolvedAddressCache.ifBlank {
                                String.format(Locale.US, "%.4f, %.4f", location.latitude, location.longitude)
                            }
                            bufferTripPointLocked(tripId, location.latitude, location.longitude, effectiveSpeedKmh, now)
                            if (shouldPersistTripRowUpdate(now)) {
                                val existing = repository.getTripById(tripId)
                                if (existing != null) {
                                    repository.updateTrip(
                                        existing.copy(
                                            distanceKm = distKm,
                                            endLat = location.latitude,
                                            endLng = location.longitude,
                                            endAddress = displayAddr
                                        )
                                    )
                                    lastDbTripUpdateMeters = totalDistanceMeters
                                    lastDbTripUpdateWallMs = now
                                }
                            }

                            var resumedSnapshot = LiveTrackingData()
                            TrackingStateManager.update { current ->
                                resumedSnapshot = current.copy(
                                    distanceKm = distKm,
                                    speedKmh = effectiveSpeedKmh,
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    currentAddress = displayAddr,
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

                            if (shouldRefreshActiveGeocode(location.latitude, location.longitude, now)) {
                                geocodeTripId = tripId
                                geocodeLat = location.latitude
                                geocodeLng = location.longitude
                                lastGeocodedLat = location.latitude
                                lastGeocodedLng = location.longitude
                                lastGeocodedWallMs = now
                            }
                            return@withLock
                        } else {
                            // Still accumulating the 5-second verification window; hold anchor if micro-step
                            if (!isSlowMicroStepWhileMoving) {
                                lastActiveLocation = location
                                lastActiveLocationWallMs = now
                            }
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
                            return@withLock
                        }
                    } else {
                        // Allow 1 noisy dip before resetting the 5s resume tracker; absorb true low-speed jitter
                        idleResumeConsecutiveMisses++
                        if (idleResumeConsecutiveMisses >= 2 || (dopplerSpeedKmh < 6.0 && displacementSpeedKmh < 6.0)) {
                            resetIdleResumeState()
                        }
                        lastActiveLocation = location
                        lastActiveLocationWallMs = now
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
                        return@withLock
                    }
                }

                // -----------------------------------------------------------------
                // CASE 2: Normal Active Driving (not yet latched in Idle).
                // Uses Pending Anchor Accumulation so sub-second / steady city steps never drop distance,
                // plus dynamic highway step bounds and smoothed velocity continuity.
                // -----------------------------------------------------------------
                val stepDecision = evaluateActiveMovementStep(
                    deltaMeters = deltaMeters,
                    deltaSeconds = deltaSeconds,
                    dopplerSpeedKmh = dopplerSpeedKmh,
                    hasDopplerSpeed = hasDoppler,
                    smoothedSpeedKmh = smoothedSpeedKmh,
                    anchorDisplacementMeters = anchorDisplacementMeters,
                    anchorElapsedSeconds = anchorElapsedSeconds
                )

                when (stepDecision) {
                    ActiveStepDecision.COMMIT_DISTANCE -> {
                        totalDistanceMeters += deltaMeters
                        lastActiveLocation = location
                        lastActiveLocationWallMs = now
                        lastDistanceAddedTimeMillis = now
                        lastDistanceAddedElapsedMs = nowElapsed
                        lastMovedTimeMillis = now
                        lastMovedElapsedMs = nowElapsed
                        idleAnchorLocation = null
                        idleAnchorWallMs = 0L

                        val distKm = totalDistanceMeters / 1000.0
                        val liveFuelCost = UnitConverter.calculateFuelCost(
                            distanceKm = distKm,
                            efficiencyVal = activeVehicleEfficiency,
                            fuelPrice = activeFuelPrice,
                            unitDistance = activeUnitDistance
                        )

                        val displayAddr = lastResolvedAddressCache.ifBlank {
                            String.format(Locale.US, "%.4f, %.4f", location.latitude, location.longitude)
                        }
                        bufferTripPointLocked(tripId, location.latitude, location.longitude, effectiveSpeedKmh, now)
                        if (shouldPersistTripRowUpdate(now)) {
                            val existing = repository.getTripById(tripId)
                            if (existing != null) {
                                repository.updateTrip(
                                    existing.copy(
                                        distanceKm = distKm,
                                        endLat = location.latitude,
                                        endLng = location.longitude,
                                        endAddress = displayAddr
                                    )
                                )
                                lastDbTripUpdateMeters = totalDistanceMeters
                                lastDbTripUpdateWallMs = now
                            }
                        }

                        var drivingSnapshot = LiveTrackingData()
                        val elapsedDriveSecs = computeDisplayDriveElapsedSeconds(
                            isIdle = false,
                            driveStartElapsedMs = driveStartElapsedMs,
                            lastMovedElapsedMs = lastMovedElapsedMs,
                            nowElapsedMs = nowElapsed
                        )
                        TrackingStateManager.update { current ->
                            drivingSnapshot = current.copy(
                                distanceKm = distKm,
                                speedKmh = effectiveSpeedKmh,
                                elapsedSeconds = elapsedDriveSecs,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                currentAddress = displayAddr,
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

                        if (shouldRefreshActiveGeocode(location.latitude, location.longitude, now)) {
                            geocodeTripId = tripId
                            geocodeLat = location.latitude
                            geocodeLng = location.longitude
                            lastGeocodedLat = location.latitude
                            lastGeocodedLng = location.longitude
                            lastGeocodedWallMs = now
                        }
                    }

                    ActiveStepDecision.ACCUMULATE_PENDING_ANCHOR -> {
                        // Hold `lastActiveLocation` anchored so consecutive small steps combine >= 2.5m cleanly!
                        if (idleAnchorLocation == null) {
                            idleAnchorLocation = prev
                            idleAnchorWallMs = now
                        }
                        var accumulatingSnapshot = LiveTrackingData()
                        TrackingStateManager.update { current ->
                            accumulatingSnapshot = current.copy(
                                speedKmh = smoothedSpeedKmh.toFloat().takeIf { it >= MIN_ACTIVE_DRIVING_SPEED_KMH * 0.7f } ?: current.speedKmh,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                isWalkingDetected = false,
                                activityStatus = "Driving",
                                linearAccelMps2 = latestLinearAccelMps2,
                                rmsAccelMps2 = latestRmsAccelMps2
                            )
                            accumulatingSnapshot
                        }
                        updateActiveDriveNotification(accumulatingSnapshot, activeUnitDistance)
                    }

                    ActiveStepDecision.SUB_THRESHOLD -> {
                        if (idleAnchorLocation == null) {
                            idleAnchorLocation = prev
                            idleAnchorWallMs = now
                        }
                        // Absorb stationary/walking drift so indoor wobble cannot accumulate fake mileage
                        lastActiveLocation = location
                        lastActiveLocationWallMs = now

                        val keepAliveByVelocityOrMotion = smoothedSpeedKmh >= MIN_ACTIVE_DRIVING_SPEED_KMH ||
                            (hasFreshSensorSamples && latestRmsAccelMps2 >= TRUE_STATIONARY_MAX_RMS_MPS2 && smoothedSpeedKmh >= 6.0)
                        if (keepAliveByVelocityOrMotion) {
                            lastDistanceAddedTimeMillis = now
                            lastDistanceAddedElapsedMs = nowElapsed
                            if (deltaMeters >= 1.5 && dopplerSpeedKmh >= MIN_ACTIVE_DRIVING_SPEED_KMH) {
                                lastMovedTimeMillis = now
                                lastMovedElapsedMs = nowElapsed
                            }
                        }

                        var subThresholdSnapshot = LiveTrackingData()
                        TrackingStateManager.update { current ->
                            subThresholdSnapshot = current.copy(
                                speedKmh = if (smoothedSpeedKmh >= 6.0) smoothedSpeedKmh.toFloat() else 0f,
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
                if (!hasResolvedStartAddress &&
                    (startCoordsLat != 0.0 || startCoordsLng != 0.0) &&
                    (now - lastStartGeocodeAttemptWallMs) >= START_GEOCODE_RETRY_INTERVAL_MS &&
                    startGeocodeJob?.isActive != true
                ) {
                    lastStartGeocodeAttemptWallMs = now
                    startGeocodeTripId = tripId
                    startGeoLat = startCoordsLat
                    startGeoLng = startCoordsLng
                }
            }

            // Perform reverse geocoding outside `locationMutex` so slow network IPC never stalls GPS tracking
            startGeocodeTripId?.let { sTripId ->
                launchStartAddressRefresh(sTripId, startGeoLat, startGeoLng)
            }
            val targetTripId = geocodeTripId
            if (targetTripId != null) {
                launchActiveAddressRefresh(targetTripId, geocodeLat, geocodeLng)
            }
        }
    }

    private fun appendActiveSpeedSample(nowMs: Long, speedKmh: Float) {
        recentActiveSpeedSamples.addLast(nowMs to speedKmh.coerceAtLeast(0f))
        val cutoff = nowMs - SPEED_SMOOTHING_WINDOW_MS
        while (recentActiveSpeedSamples.isNotEmpty() && recentActiveSpeedSamples.first().first < cutoff) {
            recentActiveSpeedSamples.removeFirst()
        }
        while (recentActiveSpeedSamples.size > 12) {
            recentActiveSpeedSamples.removeFirst()
        }
    }

    private fun shouldRefreshActiveGeocode(lat: Double, lng: Double, nowMs: Long): Boolean {
        if (isPlaceholderOrCoordinateAddress(lastResolvedAddressCache)) return true
        if ((nowMs - lastGeocodedWallMs) < ACTIVE_GEOCODE_THROTTLE_MS) return false
        val results = FloatArray(1)
        Location.distanceBetween(lastGeocodedLat, lastGeocodedLng, lat, lng, results)
        return results[0] >= ACTIVE_GEOCODE_MIN_DISTANCE_METERS
    }

    private fun launchStartAddressRefresh(
        tripId: String,
        lat: Double,
        lng: Double
    ) {
        if (lat == 0.0 && lng == 0.0) return
        startGeocodeJob?.cancel()
        startGeocodeJob = serviceScope.launch {
            val resolved = withTimeoutOrNull(3500L) {
                resolveAddress(lat, lng)
            } ?: return@launch

            if (isPlaceholderOrCoordinateAddress(resolved)) {
                return@launch
            }

            locationMutex.withLock {
                if (currentMode != ServiceMode.ACTIVE_DRIVE || activeTripId != tripId) {
                    return@withLock
                }
                hasResolvedStartAddress = true
                if (isPlaceholderOrCoordinateAddress(lastResolvedAddressCache)) {
                    lastResolvedAddressCache = resolved
                }
                val existing = repository.getTripById(tripId)
                if (existing != null) {
                    val shouldUpdateStart = isPlaceholderOrCoordinateAddress(existing.startAddress)
                    val shouldUpdateEnd = isPlaceholderOrCoordinateAddress(existing.endAddress)
                    if (shouldUpdateStart || shouldUpdateEnd) {
                        repository.updateTrip(
                            existing.copy(
                                startAddress = if (shouldUpdateStart) resolved else existing.startAddress,
                                endAddress = if (shouldUpdateEnd) resolved else existing.endAddress
                            )
                        )
                    }
                }
                TrackingStateManager.update { current ->
                    if (current.tripId != tripId) current
                    else current.copy(
                        startAddress = if (isPlaceholderOrCoordinateAddress(current.startAddress)) resolved else current.startAddress,
                        currentAddress = if (isPlaceholderOrCoordinateAddress(current.currentAddress)) resolved else current.currentAddress
                    )
                }
            }
        }
    }

    private fun launchActiveAddressRefresh(
        tripId: String,
        lat: Double,
        lng: Double
    ) {
        if (lat == 0.0 && lng == 0.0) return
        geocodeJob?.cancel()
        geocodeJob = serviceScope.launch {
            val resolved = withTimeoutOrNull(3500L) {
                resolveAddress(lat, lng)
            } ?: return@launch

            val isRealRoad = !isPlaceholderOrCoordinateAddress(resolved)
            if (!isRealRoad && !isPlaceholderOrCoordinateAddress(lastResolvedAddressCache)) {
                return@launch
            }

            locationMutex.withLock {
                if (currentMode != ServiceMode.ACTIVE_DRIVE || activeTripId != tripId) {
                    return@withLock
                }
                if (isRealRoad) {
                    lastResolvedAddressCache = resolved
                }
                val existing = repository.getTripById(tripId)
                if (existing != null && (isRealRoad || isPlaceholderOrCoordinateAddress(existing.endAddress))) {
                    repository.updateTrip(existing.copy(endAddress = resolved))
                }
                TrackingStateManager.update { current ->
                    if (current.tripId != tripId) current
                    else if (isRealRoad || isPlaceholderOrCoordinateAddress(current.currentAddress)) {
                        current.copy(currentAddress = resolved)
                    } else {
                        current
                    }
                }
            }
        }
    }

    private suspend fun resolveAddress(lat: Double, lng: Double): String {
        repository.findMatchingPlaceName(lat, lng)?.let { return it }
        val coordFallback = String.format(Locale.US, "%.4f, %.4f", lat, lng)
        if (!Geocoder.isPresent()) return coordFallback
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val first: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    try {
                        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
            }
            formatResolvedAddress(first, lat, lng)
        } catch (_: Exception) {
            coordFallback
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
            idleSecondsRemaining = data.idleSecondsRemaining,
            unitDistance = unitDistance
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

    private fun buildNotification(title: String, content: String, ongoing: Boolean = true): Notification {
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
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
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
        } else if (currentMode == ServiceMode.PAUSED_MONITORING &&
            hasBackgroundLocationPermission(applicationContext)
        ) {
            val targetPauseEnd = currentPausedUntilMillis
            if (targetPauseEnd != null && targetPauseEnd > 0L) {
                if (System.currentTimeMillis() >= targetPauseEnd) {
                    AutoResumeManager.scheduleImmediateWatchdogRestart(applicationContext, 1000L)
                } else {
                    AutoResumeManager.scheduleResume(applicationContext, targetPauseEnd)
                }
            }
        }
        unregisterLocationUpdates()
        unregisterMotionSensors()
        unregisterActivityRecognitionUpdates()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    enum class PassiveCandidateDecision {
        IGNORE_BAD_ACCURACY,
        RESET_STALE_ANCHOR,
        BLOCKED_STATIONARY_SENSOR,
        ACCUMULATE_MOVING_STEP,
        SUB_THRESHOLD_RESET
    }

    enum class ActiveStepDecision {
        COMMIT_DISTANCE,
        ACCUMULATE_PENDING_ANCHOR,
        SUB_THRESHOLD
    }

    companion object {
        private const val TAG = "MileageTrackingService"
        private const val CHANNEL_ID = "mileage_priority_tracking_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_PASSIVE_GPS_ACCURACY_METERS = 35.0f
        private const val MAX_PASSIVE_NON_DOPPLER_ACCURACY_METERS = 20.0f
        private const val MAX_PASSIVE_FIX_DELTA_MS = 45_000L
        private const val MIN_PASSIVE_FIX_DELTA_MS = 800L
        private const val MAX_ACTIVE_GPS_ACCURACY_METERS = 50.0f
        const val MIN_VEHICULAR_AUTO_START_FLOOR_KMH = 12.0
        const val MIN_VEHICLE_DISPLACEMENT_METERS = 10.0
        const val MIN_SUSTAINED_DURATION_MS = 5000L
        const val MIN_PASSIVE_CONSECUTIVE_FIXES = 3
        const val MIN_PASSIVE_PATH_LINEARITY_RATIO = 0.70
        const val ACTIVITY_OVERRIDE_MIN_DOPPLER_KMH = 22.0
        const val ACTIVITY_OVERRIDE_MIN_DISPLACEMENT_KMH = 18.0
        const val ACTIVITY_OVERRIDE_MIN_DISPLACEMENT_METERS = 35.0
        const val EARLY_ABORT_WINDOW_MS = 45_000L
        const val EARLY_ABORT_MAX_DISTANCE_METERS = 25.0
        private const val ACTIVITY_UPDATE_INTERVAL_MS = 5_000L
        private const val ACTIVITY_FRESH_WINDOW_MS = 120_000L
        private const val ACTIVITY_MIN_VETO_CONFIDENCE = 50
        private const val ACTION_ACTIVITY_RECOGNITION_UPDATE = "com.example.action.ACTIVITY_RECOGNITION_UPDATE"
        private const val REQUEST_CODE_ACTIVITY_TRANSITION = 9101
        private const val REQUEST_CODE_ACTIVITY_UPDATES = 9102
        private const val MIN_AUTO_SAVE_DISTANCE_KM = 0.05

        // Batched TripPoint & throttled TripEntity persistence thresholds
        private const val TRIP_POINT_BATCH_SIZE = 10
        private const val TRIP_POINT_FLUSH_INTERVAL_MS = 15_000L
        private const val DB_TRIP_UPDATE_MIN_METERS = 25.0
        private const val DB_TRIP_UPDATE_INTERVAL_MS = 10_000L

        // Adaptive Standby-Only GPS Polling Intervals (When NO trip is active)
        private const val PASSIVE_STANDBY_INTERVAL_MS = 3000L
        private const val PASSIVE_STANDBY_MIN_INTERVAL_MS = 1500L
        private const val PASSIVE_ACTIVE_INTERVAL_MS = 1000L
        private const val PASSIVE_ACTIVE_MIN_INTERVAL_MS = 500L
        private const val PASSIVE_HIGH_FREQ_WINDOW_MS = 30_000L

        // Active Drive Required Power GPS Polling Intervals (When a trip IS active)
        private const val ACTIVE_DRIVE_INTERVAL_MS = 1000L
        private const val ACTIVE_DRIVE_MIN_INTERVAL_MS = 1000L

        // Active Driving, Pending Anchor & True Stationary Constants
        const val IDLE_START_DELAY_SECONDS = 15L
        const val MIN_ACTIVE_DRIVING_SPEED_KMH = 10.0f
        const val MIN_IDLE_RESUME_DURATION_MS = 5000L
        const val MIN_IDLE_RESUME_DISPLACEMENT_METERS = 12.0
        const val MIN_COMMIT_STEP_METERS = 2.5
        const val ANCHOR_ESCAPE_DISPLACEMENT_METERS = 25.0
        const val SPEED_SMOOTHING_WINDOW_MS = 6000L
        private const val RECENT_GPS_FIX_STALE_MS = 20_000L
        private const val MAX_PLAUSIBLE_SPEED_MPS = 75.0
        private const val ACTIVE_GEOCODE_THROTTLE_MS = 30_000L
        private const val ACTIVE_GEOCODE_MIN_DISTANCE_METERS = 250f
        private const val START_GEOCODE_RETRY_INTERVAL_MS = 10_000L

        // Accelerometer Constants
        private const val GRAVITY_LOW_PASS_ALPHA = 0.85f
        private const val ACCEL_NOISE_FLOOR_MPS2 = 0.12f
        private const val ACCEL_RMS_WINDOW_MS = 1500L
        const val MIN_VEHICLE_RMS_ACCEL_MPS2 = 0.18f
        const val TRUE_STATIONARY_MAX_RMS_MPS2 = 0.35f

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

        private val COORDINATE_PAIR_REGEX = Regex("^-?\\d{1,3}(?:\\.\\d+)?\\s*,\\s*-?\\d{1,3}(?:\\.\\d+)?$")
        private val HOUSE_NUMBER_OR_PLUS_CODE_REGEX = Regex(
            "^(?:\\d+[A-Za-z]?(?:[/-]\\d+[A-Za-z]?)?|[23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{2,4})$"
        )

        /**
         * Returns true if [address] is blank, a known placeholder string, or a raw "lat, lng"
         * coordinate fallback that should still be overwritten once a real road name resolves.
         */
        fun isPlaceholderOrCoordinateAddress(address: String?): Boolean {
            val trimmed = address?.trim().orEmpty()
            if (trimmed.isBlank()) return true
            val normalized = trimmed.lowercase(Locale.US)
            if (normalized == "detecting start location..." ||
                normalized == "resolving road name..." ||
                normalized == "start location" ||
                normalized == "origin" ||
                normalized == "unknown" ||
                normalized == "unnamed road"
            ) {
                return true
            }
            return COORDINATE_PAIR_REGEX.matches(trimmed)
        }

        /**
         * Formats an Android Geocoder Address prioritizing "Road, Area, City",
         * filtering out unnamed roads, numeric-only house tokens, and duplicate segments.
         */
        fun formatResolvedAddress(address: Address?, lat: Double, lng: Double): String {
            if (address == null) {
                return String.format(Locale.US, "%.4f, %.4f", lat, lng)
            }
            return formatResolvedAddressComponents(
                thoroughfare = address.thoroughfare,
                featureName = address.featureName,
                subLocality = address.subLocality,
                locality = address.locality,
                subAdminArea = address.subAdminArea,
                fullAddressLine = if (address.maxAddressLineIndex >= 0) address.getAddressLine(0) else null,
                lat = lat,
                lng = lng
            )
        }

        fun formatResolvedAddressComponents(
            thoroughfare: String?,
            featureName: String?,
            subLocality: String?,
            locality: String?,
            subAdminArea: String?,
            fullAddressLine: String?,
            lat: Double,
            lng: Double
        ): String {
            fun cleanSegment(raw: String?): String? {
                val trimmed = raw?.trim().orEmpty()
                if (trimmed.isBlank()) return null
                if (trimmed.equals("Unnamed Road", ignoreCase = true)) return null
                if (COORDINATE_PAIR_REGEX.matches(trimmed)) return null
                return trimmed
            }

            val cleanRoad = cleanSegment(thoroughfare)
                ?: cleanSegment(featureName)?.takeIf { !HOUSE_NUMBER_OR_PLUS_CODE_REGEX.matches(it) }
            val cleanArea = cleanSegment(subLocality)
            val cleanCity = cleanSegment(locality) ?: cleanSegment(subAdminArea)

            val parts = mutableListOf<String>()
            fun addUnique(part: String?) {
                if (part.isNullOrBlank()) return
                if (parts.none { it.equals(part, ignoreCase = true) }) {
                    parts.add(part)
                }
            }

            addUnique(cleanRoad)
            addUnique(cleanArea)
            addUnique(cleanCity)

            if (parts.isNotEmpty()) {
                return parts.joinToString(", ")
            }

            val line = cleanSegment(fullAddressLine)
            if (!line.isNullOrBlank()) {
                return line
            }
            return String.format(Locale.US, "%.4f, %.4f", lat, lng)
        }

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
         * Computes smoothed recent GPS speed (km/h) over the sliding active driving window.
         */
        fun computeSmoothedSpeedKmh(
            samples: List<Pair<Long, Float>>,
            nowMs: Long,
            windowMs: Long = SPEED_SMOOTHING_WINDOW_MS
        ): Double {
            if (samples.isEmpty()) return 0.0
            val cutoff = nowMs - windowMs
            var sum = 0.0
            var count = 0
            for ((ts, speed) in samples) {
                if (ts >= cutoff) {
                    sum += speed.coerceAtLeast(0f).toDouble()
                    count++
                }
            }
            return if (count > 0) sum / count else 0.0
        }

        /**
         * Evaluates whether an incoming active-drive GPS fix should commit distance immediately,
         * preserve the prior location as a pending anchor so small steady-driving steps sum cleanly,
         * or be treated as sub-threshold movement/stationary drift.
         */
        fun evaluateActiveMovementStep(
            deltaMeters: Double,
            deltaSeconds: Double,
            dopplerSpeedKmh: Double,
            hasDopplerSpeed: Boolean,
            smoothedSpeedKmh: Double,
            anchorDisplacementMeters: Double,
            anchorElapsedSeconds: Double
        ): ActiveStepDecision {
            val safeDeltaSecs = deltaSeconds.coerceAtLeast(0.25)
            val displacementSpeedKmh = (deltaMeters / safeDeltaSecs) * 3.6
            val maxPlausibleStepMeters = max(500.0, safeDeltaSecs * MAX_PLAUSIBLE_SPEED_MPS)

            if (deltaMeters > maxPlausibleStepMeters) {
                return ActiveStepDecision.SUB_THRESHOLD
            }

            if (deltaMeters >= MIN_COMMIT_STEP_METERS) {
                val effectiveDoppler = if (hasDopplerSpeed) dopplerSpeedKmh else displacementSpeedKmh
                val standardDriving = effectiveDoppler >= MIN_ACTIVE_DRIVING_SPEED_KMH &&
                    displacementSpeedKmh >= (MIN_ACTIVE_DRIVING_SPEED_KMH * 0.65)
                val smoothedGapRecovery = smoothedSpeedKmh >= MIN_ACTIVE_DRIVING_SPEED_KMH &&
                    displacementSpeedKmh >= (MIN_ACTIVE_DRIVING_SPEED_KMH * 0.70)
                val strongDisplacementWithoutDoppler = displacementSpeedKmh >= (MIN_ACTIVE_DRIVING_SPEED_KMH * 1.15) &&
                    deltaMeters >= 6.0

                if (standardDriving || smoothedGapRecovery || strongDisplacementWithoutDoppler) {
                    return ActiveStepDecision.COMMIT_DISTANCE
                }
            } else {
                // Micro-step (< 2.5m): keep previous anchor intact when Doppler or smoothed velocity shows driving
                if ((hasDopplerSpeed && dopplerSpeedKmh >= MIN_ACTIVE_DRIVING_SPEED_KMH) ||
                    smoothedSpeedKmh >= MIN_ACTIVE_DRIVING_SPEED_KMH
                ) {
                    return ActiveStepDecision.ACCUMULATE_PENDING_ANCHOR
                }
            }

            val anchorSpeedKmh = if (anchorElapsedSeconds > 0.0) {
                (anchorDisplacementMeters / anchorElapsedSeconds) * 3.6
            } else 0.0
            if (anchorDisplacementMeters >= ANCHOR_ESCAPE_DISPLACEMENT_METERS &&
                (anchorSpeedKmh >= MIN_ACTIVE_DRIVING_SPEED_KMH * 0.85 ||
                    smoothedSpeedKmh >= MIN_ACTIVE_DRIVING_SPEED_KMH)
            ) {
                return ActiveStepDecision.COMMIT_DISTANCE
            }

            return ActiveStepDecision.SUB_THRESHOLD
        }

        /**
         * Returns true when recent high-confidence Activity Recognition indicates a non-vehicular state
         * (walking, running, bicycling, or stationary stillness).
         */
        fun isNonVehicularOsActivity(
            latestActivityType: Int?,
            latestActivityConfidence: Int,
            isActivityFresh: Boolean
        ): Boolean {
            if (!isActivityFresh || latestActivityType == null || latestActivityConfidence < ACTIVITY_MIN_VETO_CONFIDENCE) {
                return false
            }
            return latestActivityType == DetectedActivity.WALKING ||
                latestActivityType == DetectedActivity.RUNNING ||
                latestActivityType == DetectedActivity.ON_BICYCLE ||
                latestActivityType == DetectedActivity.STILL ||
                latestActivityType == DetectedActivity.ON_FOOT
        }

        /**
         * Evaluates passive standby GPS, hardware Doppler, OS Activity Recognition, and accelerometer
         * readings for auto-drive initiation:
         * - Blocks coarse non-Doppler indoor/Wi-Fi drift (>20m without hardware speed)
         * - Enforces vehicle-only speed floor (>=12 km/h)
         * - Separates hardware Doppler from displacement math so Wi-Fi snaps never bypass sensor guards
         * - Vetoes WALKING/RUNNING/ON_BICYCLE/STILL unless sustained high-speed vehicular travel overrides
         */
        fun evaluatePassiveAutoStartCandidate(
            accuracyMeters: Float?,
            deltaMs: Long,
            stepDistMeters: Double,
            dopplerSpeedKmh: Double,
            displacementSpeedKmh: Double,
            thresholdKmh: Double,
            hasMotionSensor: Boolean,
            hasFreshSensorSamples: Boolean,
            rmsAccelMps2: Float,
            hasHardwareDopplerSpeed: Boolean = true,
            latestActivityType: Int? = null,
            latestActivityConfidence: Int = 0,
            isActivityFresh: Boolean = false
        ): PassiveCandidateDecision {
            if (accuracyMeters != null && accuracyMeters > MAX_PASSIVE_GPS_ACCURACY_METERS) {
                return PassiveCandidateDecision.IGNORE_BAD_ACCURACY
            }
            if (!hasHardwareDopplerSpeed &&
                accuracyMeters != null &&
                accuracyMeters > MAX_PASSIVE_NON_DOPPLER_ACCURACY_METERS
            ) {
                return PassiveCandidateDecision.IGNORE_BAD_ACCURACY
            }
            if (deltaMs > MAX_PASSIVE_FIX_DELTA_MS) {
                return PassiveCandidateDecision.RESET_STALE_ANCHOR
            }
            val safeDeltaSecs = (deltaMs / 1000.0).coerceAtLeast(0.25)
            val maxStepMeters = max(150.0, safeDeltaSecs * MAX_PLAUSIBLE_SPEED_MPS)
            val effectiveThresholdKmh = maxOf(thresholdKmh, MIN_VEHICULAR_AUTO_START_FLOOR_KMH)

            val strongGpsConsensus = hasHardwareDopplerSpeed &&
                dopplerSpeedKmh >= max(effectiveThresholdKmh + 3.0, 18.0) &&
                displacementSpeedKmh >= max(effectiveThresholdKmh, 14.0) &&
                stepDistMeters in 3.0..maxStepMeters

            if (isNonVehicularOsActivity(latestActivityType, latestActivityConfidence, isActivityFresh)) {
                val fastSustainedOverride = hasHardwareDopplerSpeed &&
                    dopplerSpeedKmh >= ACTIVITY_OVERRIDE_MIN_DOPPLER_KMH &&
                    displacementSpeedKmh >= ACTIVITY_OVERRIDE_MIN_DISPLACEMENT_KMH &&
                    stepDistMeters in 4.0..maxStepMeters
                if (!fastSustainedOverride) {
                    return PassiveCandidateDecision.BLOCKED_STATIONARY_SENSOR
                }
            }

            if (hasMotionSensor && hasFreshSensorSamples &&
                rmsAccelMps2 < MIN_VEHICLE_RMS_ACCEL_MPS2 &&
                !strongGpsConsensus
            ) {
                return PassiveCandidateDecision.BLOCKED_STATIONARY_SENSOR
            }

            val isStepSpeedCrossing = if (hasHardwareDopplerSpeed) {
                dopplerSpeedKmh >= effectiveThresholdKmh &&
                    displacementSpeedKmh >= (effectiveThresholdKmh * 0.85) &&
                    stepDistMeters in 1.5..maxStepMeters
            } else {
                // Without hardware Doppler, require higher clean displacement velocity and distance
                displacementSpeedKmh >= max(effectiveThresholdKmh, 14.0) &&
                    stepDistMeters in 3.0..maxStepMeters
            }

            return if (isStepSpeedCrossing) {
                PassiveCandidateDecision.ACCUMULATE_MOVING_STEP
            } else {
                PassiveCandidateDecision.SUB_THRESHOLD_RESET
            }
        }

        /**
         * Resolves effective speed for stationary checks, decaying stale prior GPS speeds to 0.0
         * when GPS fixes pause in garages/indoors and motion sensors show a motionless device.
         */
        fun resolveEffectiveSpeedForStationaryCheck(
            liveSmoothedSpeedKmh: Double,
            lastKnownValidGpsSpeedKmh: Double,
            timeSinceLastGpsFixMs: Long,
            hasFreshSensorSamples: Boolean,
            rmsAccelMps2: Float
        ): Double {
            if (timeSinceLastGpsFixMs <= SPEED_SMOOTHING_WINDOW_MS) {
                return liveSmoothedSpeedKmh
            }
            if (timeSinceLastGpsFixMs <= RECENT_GPS_FIX_STALE_MS) {
                return lastKnownValidGpsSpeedKmh
            }
            if (timeSinceLastGpsFixMs <= 90_000L && (!hasFreshSensorSamples || rmsAccelMps2 >= MIN_VEHICLE_RMS_ACCEL_MPS2)) {
                return lastKnownValidGpsSpeedKmh
            }
            return 0.0
        }

        /**
         * Computes the persisted trip end timestamp. On auto-stop, ends at the last confirmed
         * vehicle movement so trailing stationary idle wait is excluded from trip duration.
         */
        fun computeAutoStopEndTimeMillis(
            isAutoStop: Boolean,
            driveStartTimeMillis: Long,
            lastMovedTimeMillis: Long,
            nowMs: Long
        ): Long {
            if (!isAutoStop) return nowMs
            return when {
                lastMovedTimeMillis > driveStartTimeMillis && lastMovedTimeMillis <= nowMs -> lastMovedTimeMillis
                driveStartTimeMillis in 1..nowMs -> driveStartTimeMillis
                else -> nowMs
            }
        }

        /**
         * Computes live drive elapsed seconds shown in the HUD/Notification.
         * While stationary idle, freezes elapsed drive time at last movement so live display
         * matches the saved trip duration (excluding idle wait).
         */
        fun computeDisplayDriveElapsedSeconds(
            isIdle: Boolean,
            driveStartElapsedMs: Long,
            lastMovedElapsedMs: Long,
            nowElapsedMs: Long
        ): Long {
            val endElapsed = if (isIdle) {
                lastMovedElapsedMs.coerceAtLeast(driveStartElapsedMs).coerceAtMost(nowElapsedMs)
            } else {
                nowElapsedMs
            }
            return ((endElapsed - driveStartElapsedMs) / 1000L).coerceAtLeast(0L)
        }

        /**
         * Verifies true stationary state across smoothed GPS velocity, anchor displacement,
         * and motion sensor RMS before allowing idle latching or automatic trip completion.
         */
        fun isTrueStationaryState(
            idleSeconds: Long,
            minIdleSeconds: Long,
            smoothedSpeedKmh: Double,
            anchorDisplacementMeters: Double,
            anchorElapsedSeconds: Double,
            hasFreshSensorSamples: Boolean,
            rmsAccelMps2: Float,
            timeSinceLastGpsFixMs: Long
        ): Boolean {
            if (idleSeconds < minIdleSeconds) return false

            val hasRecentGpsFix = timeSinceLastGpsFixMs <= RECENT_GPS_FIX_STALE_MS
            if (hasRecentGpsFix && smoothedSpeedKmh >= MIN_ACTIVE_DRIVING_SPEED_KMH * 0.75) {
                return false
            }

            val anchorSpeedKmh = if (anchorElapsedSeconds > 0.0) {
                (anchorDisplacementMeters / anchorElapsedSeconds) * 3.6
            } else 0.0
            if (hasRecentGpsFix &&
                anchorDisplacementMeters >= ANCHOR_ESCAPE_DISPLACEMENT_METERS &&
                anchorSpeedKmh >= (MIN_ACTIVE_DRIVING_SPEED_KMH * 0.85)
            ) {
                return false
            }

            // Tunnel / overpass GPS blackout grace for active highway or arterial travel:
            // Applies even on damped phone mounts or smooth EVs where RMS accel < 0.35 m/s².
            val isTunnelOrOverpassGrace = minIdleSeconds <= 90L &&
                timeSinceLastGpsFixMs <= 90_000L &&
                smoothedSpeedKmh >= (MIN_ACTIVE_DRIVING_SPEED_KMH * 1.5)
            if (isTunnelOrOverpassGrace) {
                return false
            }

            if (hasFreshSensorSamples && rmsAccelMps2 >= TRUE_STATIONARY_MAX_RMS_MPS2) {
                val isShortIdleWindow = minIdleSeconds <= IDLE_START_DELAY_SECONDS + 5L
                if (isShortIdleWindow && (!hasRecentGpsFix || smoothedSpeedKmh >= 4.0)) {
                    return false
                }
            }

            return true
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
            val displayDist = UnitConverter.toDisplayDistance(distanceKm, unitDistance)
            val distStr = String.format(Locale.US, "%.2f %s", displayDist, unitDistance)
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
            idleSecondsRemaining: Long,
            unitDistance: String = "km"
        ): String {
            val elapsedStr = UnitConverter.formatDurationClock(elapsedSeconds)
            val displaySpeed = UnitConverter.toDisplaySpeed(speedKmh.toDouble(), unitDistance).toInt()
            val speedUnit = UnitConverter.speedUnitLabel(unitDistance)
            val speedStr = "$displaySpeed $speedUnit"

            val statusSuffix = if (isIdle) {
                val countdownStr = UnitConverter.formatDurationClock(idleSecondsRemaining)
                "Stationary • Auto-stop in $countdownStr"
            } else {
                "$vehicleName ($categoryName)"
            }

            return "Speed: $speedStr • Time: $elapsedStr • $statusSuffix"
        }

        /**
         * Early 45s / <25m false-start circuit breaker: if an auto-started trip becomes stationary
         * or OS activity confirms walking/still within the opening 45 seconds before reaching 25m
         * of real travel, immediately abort and discard instead of waiting for the full idle timeout.
         */
        fun shouldAbortEarlyFalseAutoStart(
            isAutoStarted: Boolean,
            driveAgeFromLaunchMs: Long,
            idleSeconds: Long,
            isIdleLatched: Boolean,
            totalDistanceMeters: Double,
            smoothedSpeedKmh: Double,
            hasRecentGpsFix: Boolean,
            latestActivityType: Int?,
            latestActivityConfidence: Int,
            isActivityFresh: Boolean
        ): Boolean {
            if (!isAutoStarted) return false
            if (driveAgeFromLaunchMs <= 0L || driveAgeFromLaunchMs > EARLY_ABORT_WINDOW_MS) return false
            if (totalDistanceMeters >= EARLY_ABORT_MAX_DISTANCE_METERS) return false

            val stoppedAfterLaunch = isIdleLatched ||
                (idleSeconds >= IDLE_START_DELAY_SECONDS && smoothedSpeedKmh < 5.0)
            if (stoppedAfterLaunch && (!hasRecentGpsFix || smoothedSpeedKmh < 6.0)) {
                return true
            }

            val highConfWalkingOrStill = isNonVehicularOsActivity(
                latestActivityType = latestActivityType,
                latestActivityConfidence = latestActivityConfidence.coerceAtLeast(0),
                isActivityFresh = isActivityFresh
            ) && latestActivityConfidence >= 70
            return highConfWalkingOrStill && idleSeconds >= 10L && smoothedSpeedKmh < 8.0
        }

        /**
         * Multi-layer auto-start confirmation rule:
         * - Enforces vehicle-only floor (>=12 km/h)
         * - Requires >= 3 consecutive moving fixes
         * - Requires >= 10.0m linear anchor displacement sustained over >= 5.0 seconds (5000ms)
         * - Requires path linearity ratio >= 0.70 to reject zig-zag indoor drift
         * - Vetoes WALKING/RUNNING/ON_BICYCLE/STILL unless sustained high-speed vehicle override
         *   (>= 22 km/h Doppler, >= 18 km/h displacement, >= 35m linear distance) confirms driving
         */
        fun verifyVehicularThresholdCrossing(
            dopplerSpeedKmh: Double,
            displacementSpeedKmh: Double,
            thresholdKmh: Double,
            totalDisplacementMeters: Double,
            sustainedDurationMs: Long,
            consecutiveMovingFixes: Int = MIN_PASSIVE_CONSECUTIVE_FIXES,
            pathLinearityRatio: Double = 1.0,
            hasHardwareDopplerSpeed: Boolean = true,
            latestActivityType: Int? = null,
            latestActivityConfidence: Int = 0,
            isActivityFresh: Boolean = false
        ): Boolean {
            if (thresholdKmh <= 0.0) return false
            val effectiveThreshold = maxOf(thresholdKmh, MIN_VEHICULAR_AUTO_START_FLOOR_KMH)

            val speedMeetsThreshold = if (hasHardwareDopplerSpeed) {
                dopplerSpeedKmh >= effectiveThreshold &&
                    displacementSpeedKmh >= (effectiveThreshold * 0.85)
            } else {
                displacementSpeedKmh >= max(effectiveThreshold, 14.0)
            }
            val displacementConfirmed = totalDisplacementMeters >= MIN_VEHICLE_DISPLACEMENT_METERS
            val durationConfirmed = sustainedDurationMs >= MIN_SUSTAINED_DURATION_MS
            val consecutiveConfirmed = consecutiveMovingFixes >= MIN_PASSIVE_CONSECUTIVE_FIXES
            val linearityConfirmed = pathLinearityRatio >= MIN_PASSIVE_PATH_LINEARITY_RATIO

            if (!(speedMeetsThreshold && displacementConfirmed && durationConfirmed && consecutiveConfirmed && linearityConfirmed)) {
                return false
            }

            if (isNonVehicularOsActivity(latestActivityType, latestActivityConfidence, isActivityFresh)) {
                return hasHardwareDopplerSpeed &&
                    dopplerSpeedKmh >= ACTIVITY_OVERRIDE_MIN_DOPPLER_KMH &&
                    displacementSpeedKmh >= ACTIVITY_OVERRIDE_MIN_DISPLACEMENT_KMH &&
                    totalDisplacementMeters >= ACTIVITY_OVERRIDE_MIN_DISPLACEMENT_METERS
            }
            return true
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

        fun hasActivityRecognitionPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
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
