package com.example

import com.example.data.fuel.LiveFuelPriceManager
import com.example.data.model.CountryRegistry
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MileageExpenseTest {

    @Test
    fun testPersonalTripCostCalculation() {
        // Distance = 100 km, Efficiency = 20 km/L, Fuel Price = 100 /L, Wear rate = 0.50 /km, Tolls = 50, Parking = 30
        val distanceKm = 100.0
        val efficiency = 20.0
        val fuelPrice = 100.0
        val wearRate = 0.50
        val tolls = 50.0
        val parking = 30.0

        val fuelCost = (distanceKm / efficiency) * fuelPrice // (100 / 20) * 100 = 500.0
        val maintenanceCost = distanceKm * wearRate // 100 * 0.50 = 50.0
        val totalPersonalCost = fuelCost + maintenanceCost + tolls + parking // 500 + 50 + 50 + 30 = 630.0

        assertEquals(500.0, fuelCost, 0.01)
        assertEquals(50.0, maintenanceCost, 0.01)
        assertEquals(630.0, totalPersonalCost, 0.01)
    }

    @Test
    fun testBusinessTaxDeductionCalculation() {
        val distanceKm = 45.0
        val taxRatePerKm = 10.0 // standard Indian tax rate ₹10/km

        val reimbursement = distanceKm * taxRatePerKm
        assertEquals(450.0, reimbursement, 0.01)
    }

    @Test
    fun testCityFuelRates() {
        val mumbaiPetrol = LiveFuelPriceManager.getPriceForCity("IN", "Mumbai", "PETROL")
        assertTrue("Mumbai petrol price should be > 90", mumbaiPetrol > 90.0)

        val delhiPetrol = LiveFuelPriceManager.getPriceForCity("IN", "New Delhi", "PETROL")
        assertTrue("Delhi petrol price should be > 90", delhiPetrol > 90.0)
    }

    @Test
    fun testCountryRegistryProfiles() {
        val india = CountryRegistry.getProfile("IN")
        assertEquals("INR", india.currencyCode)
        assertEquals("₹", india.currencySymbol)
        assertEquals("km", india.unitDistance)

        val us = CountryRegistry.getProfile("US")
        assertEquals("USD", us.currencyCode)
        assertEquals("$", us.currencySymbol)
        assertEquals("mi", us.unitDistance)
    }

    @Test
    fun testGoodReturnsCitySlugMapping() {
        assertEquals("new-delhi", LiveFuelPriceManager.getCitySlugForGoodReturns("New Delhi"))
        assertEquals("mumbai", LiveFuelPriceManager.getCitySlugForGoodReturns("Mumbai"))
        assertEquals("bangalore", LiveFuelPriceManager.getCitySlugForGoodReturns("Bengaluru"))
        assertEquals("pune", LiveFuelPriceManager.getCitySlugForGoodReturns("Pune"))
        assertEquals("gurgaon", LiveFuelPriceManager.getCitySlugForGoodReturns("Gurugram"))
    }

    @Test
    fun testGoodReturnsTodayRate() {
        val rate = LiveFuelPriceManager.getTodayFuelRate("IN", "New Delhi", "PETROL")
        assertEquals(102.12, rate.price, 0.01)
        assertEquals("/L", rate.unit)
        assertEquals("www.goodreturns.in", rate.source)
    }

    @Test
    fun test12HourDayScheduleConversion() {
        val schedule = com.example.data.model.DaySchedule(
            dayIndex = 0,
            dayName = "Monday",
            dayShort = "Mon",
            enabled = true,
            startHour12 = 9,
            startMinute = 30,
            startAmPm = "AM",
            endHour12 = 6,
            endMinute = 45,
            endAmPm = "PM",
            category = "BUSINESS"
        )

        assertEquals("09:30 AM", schedule.formatStartTime())
        assertEquals("06:45 PM", schedule.formatEndTime())
        assertEquals(9 * 60 + 30, schedule.toStartMinutes24())
        assertEquals(18 * 60 + 45, schedule.toEndMinutes24())
    }

    @Test
    fun testScheduleSerialization() {
        val defaults = com.example.data.model.ScheduleSerializer.defaultSchedules()
        assertEquals(7, defaults.size)

        val json = com.example.data.model.ScheduleSerializer.toJson(defaults)
        val deserialized = com.example.data.model.ScheduleSerializer.fromJson(json)
        assertEquals(7, deserialized.size)
        assertEquals("Monday", deserialized[0].dayName)
        assertEquals(true, deserialized[0].enabled)
        assertEquals(false, deserialized[5].enabled) // Saturday default false
    }

    @Test
    fun testPauseAutoTrackingCondition() {
        val futureTime = System.currentTimeMillis() + 3600 * 1000L // 1 hr future
        val pastTime = System.currentTimeMillis() - 1000L

        val pausedSettings = com.example.data.model.AppSettingsEntity(autoTrackingPausedUntil = futureTime)
        assertTrue("Should be paused when target time is in future", pausedSettings.isAutoTrackingPaused())

        val resumedSettings = com.example.data.model.AppSettingsEntity(autoTrackingPausedUntil = pastTime)
        assertTrue("Should NOT be paused when target time has passed", !resumedSettings.isAutoTrackingPaused())

        // Test "Pause until I resume" (-1L)
        val indefiniteSettings = com.example.data.model.AppSettingsEntity(autoTrackingPausedUntil = -1L)
        assertTrue("Should be paused indefinitely", indefiniteSettings.isAutoTrackingPaused())
        assertTrue("isPausedIndefinitely should return true", indefiniteSettings.isPausedIndefinitely())
    }

    @Test
    fun testVehicleSpecificLiveFuelCostCalculation() {
        val fuelPrice = 104.21

        // Motorcycle (45 km/L)
        val motorcycleMileage = 45.0
        val motoDistance = 90.0
        val motoFuelCost = (motoDistance / motorcycleMileage) * fuelPrice
        assertEquals(2.0 * fuelPrice, motoFuelCost, 0.01)

        // Car (14 km/L)
        val carMileage = 14.0
        val carDistance = 70.0
        val carFuelCost = (carDistance / carMileage) * fuelPrice
        assertEquals(5.0 * fuelPrice, carFuelCost, 0.01)

        // Scooter (50 km/L)
        val scooterMileage = 50.0
        val scooterDistance = 25.0
        val scooterFuelCost = (scooterDistance / scooterMileage) * fuelPrice
        assertEquals(0.5 * fuelPrice, scooterFuelCost, 0.01)
    }

    @Test
    fun testDayWiseCategoryBreakdown() {
        val trip1 = com.example.data.model.TripEntity(
            id = "t1",
            category = "BUSINESS",
            startTime = 1000L,
            distanceKm = 20.0,
            fuelCost = 150.0,
            totalPersonalCost = 160.0
        )
        val trip2 = com.example.data.model.TripEntity(
            id = "t2",
            category = "BUSINESS",
            startTime = 2000L,
            distanceKm = 10.0,
            fuelCost = 75.0,
            totalPersonalCost = 80.0
        )
        val trip3 = com.example.data.model.TripEntity(
            id = "t3",
            category = "PERSONAL",
            startTime = 3000L,
            distanceKm = 15.0,
            fuelCost = 112.5,
            totalPersonalCost = 120.0
        )

        val trips = listOf(trip1, trip2, trip3)
        val breakdown = trips.groupBy { it.category }.map { (cat, catTrips) ->
            com.example.ui.viewmodel.CategoryStat(
                categoryName = cat,
                distanceKm = catTrips.sumOf { it.distanceKm },
                fuelCost = catTrips.sumOf { it.fuelCost },
                totalCost = catTrips.sumOf { it.totalPersonalCost },
                tripCount = catTrips.size
            )
        }

        val biz = breakdown.find { it.categoryName == "BUSINESS" }
        val personal = breakdown.find { it.categoryName == "PERSONAL" }

        assertTrue(biz != null)
        assertEquals(30.0, biz!!.distanceKm, 0.01)
        assertEquals(225.0, biz.fuelCost, 0.01)
        assertEquals(2, biz.tripCount)

        assertTrue(personal != null)
        assertEquals(15.0, personal!!.distanceKm, 0.01)
        assertEquals(112.5, personal.fuelCost, 0.01)
        assertEquals(1, personal.tripCount)
    }

    @Test
    fun testVehicularThresholdCrossingVerification() {
        val thresholdKmh = 12.0

        // 1. Stationary GPS drift (high Doppler spike but only 1.8m physical displacement) -> must NOT trigger
        val stationaryDrift = com.example.service.MileageTrackingService.verifyVehicularThresholdCrossing(
            dopplerSpeedKmh = 14.0,
            displacementSpeedKmh = 12.5,
            thresholdKmh = thresholdKmh,
            totalDisplacementMeters = 1.8,
            sustainedDurationMs = 6000L
        )
        assertEquals(false, stationaryDrift)

        // 2. Walking below 12 km/h vehicle floor (even over 15 meters and 10 seconds) -> must NOT trigger
        val walkingBelowThreshold = com.example.service.MileageTrackingService.verifyVehicularThresholdCrossing(
            dopplerSpeedKmh = 5.5,
            displacementSpeedKmh = 5.5,
            thresholdKmh = 10.0,
            totalDisplacementMeters = 15.0,
            sustainedDurationMs = 10_000L
        )
        assertEquals(false, walkingBelowThreshold)

        // 3. Old short-window movement (9.6m over 2.6s) -> rejected under 10m / 5s rule
        val tooShortDurationOrDist = com.example.service.MileageTrackingService.verifyVehicularThresholdCrossing(
            dopplerSpeedKmh = 14.5,
            displacementSpeedKmh = 13.8,
            thresholdKmh = thresholdKmh,
            totalDisplacementMeters = 9.6,
            sustainedDurationMs = 2600L
        )
        assertEquals(false, tooShortDurationOrDist)

        // 4. Too few consecutive moving fixes (single jump) or zig-zag indoor drift (linearity < 0.70) -> must NOT trigger
        val singleJumpSpike = com.example.service.MileageTrackingService.verifyVehicularThresholdCrossing(
            dopplerSpeedKmh = 18.0,
            displacementSpeedKmh = 16.0,
            thresholdKmh = thresholdKmh,
            totalDisplacementMeters = 18.0,
            sustainedDurationMs = 5500L,
            consecutiveMovingFixes = 1,
            pathLinearityRatio = 0.95
        )
        assertEquals(false, singleJumpSpike)

        val zigZagIndoorScatter = com.example.service.MileageTrackingService.verifyVehicularThresholdCrossing(
            dopplerSpeedKmh = 16.0,
            displacementSpeedKmh = 15.0,
            thresholdKmh = thresholdKmh,
            totalDisplacementMeters = 14.0,
            sustainedDurationMs = 5500L,
            consecutiveMovingFixes = 3,
            pathLinearityRatio = 0.45
        )
        assertEquals(false, zigZagIndoorScatter)

        // 5. Genuine sustained vehicle departure (>= 12 km/h, >= 10m linear displacement, >= 5s, >= 3 fixes, linear >= 0.70) -> MUST trigger
        val genuineVehicleStart = com.example.service.MileageTrackingService.verifyVehicularThresholdCrossing(
            dopplerSpeedKmh = 16.5,
            displacementSpeedKmh = 15.2,
            thresholdKmh = thresholdKmh,
            totalDisplacementMeters = 21.0,
            sustainedDurationMs = 5100L,
            consecutiveMovingFixes = 3,
            pathLinearityRatio = 0.94
        )
        assertEquals(true, genuineVehicleStart)
    }

    @Test
    fun testPrecisionAccelerometerFilteringAndRms() {
        val gravityState = floatArrayOf(0f, 0f, 9.81f)

        // Stationary phone resting flat on 9.81 m/s² gravity -> net linear acceleration should be 0.0 (noise floor filtered)
        val stationaryMag = com.example.service.MileageTrackingService.computeLinearAccelerationMagnitude(
            rawX = 0.02f,
            rawY = -0.01f,
            rawZ = 9.81f,
            gravityState = gravityState,
            isLinearSensor = false
        )
        assertEquals(0.0f, stationaryMag, 0.001f)

        // Genuine vehicle forward acceleration (1.8 m/s² on X axis)
        val vehicleAccelMag = com.example.service.MileageTrackingService.computeLinearAccelerationMagnitude(
            rawX = 1.8f,
            rawY = 0.6f,
            rawZ = 0.0f,
            gravityState = gravityState,
            isLinearSensor = true
        )
        assertTrue("Vehicle linear acceleration should be > 1.8 m/s²", vehicleAccelMag > 1.8f)

        // RMS computation over sample window
        val rms = com.example.service.MileageTrackingService.computeRmsAcceleration(
            listOf(1.0f, 2.0f, 2.0f, 3.0f)
        )
        // sqrt((1 + 4 + 4 + 9) / 4) = sqrt(4.5) ≈ 2.1213
        assertEquals(2.1213f, rms, 0.01f)
    }

    @Test
    fun testIdleToDrivingResumeRequires10KmhFor5Seconds() {
        // 1. Sub-threshold movement (< 10 km/h, e.g. walking at 5.2 km/h for 8s) -> must remain Idle (false)
        val subThresholdWalking = com.example.service.MileageTrackingService.verifyIdleToDrivingResume(
            dopplerSpeedKmh = 5.2,
            displacementSpeedKmh = 5.0,
            totalDisplacementMeters = 11.5,
            sustainedDurationMs = 8000L
        )
        assertEquals(false, subThresholdWalking)

        // 2. Brief speed spike >= 10 km/h lasting < 5 seconds (e.g. 13.5 km/h for 3.2 seconds) -> must remain Idle (false)
        val briefSpikeDuringIdle = com.example.service.MileageTrackingService.verifyIdleToDrivingResume(
            dopplerSpeedKmh = 13.5,
            displacementSpeedKmh = 12.0,
            totalDisplacementMeters = 14.0,
            sustainedDurationMs = 3200L
        )
        assertEquals(false, briefSpikeDuringIdle)

        // 3. Stationary GPS drift with Doppler spike (12 km/h for 6s but only 3.5m physical movement) -> must remain Idle (false)
        val stationaryDrift = com.example.service.MileageTrackingService.verifyIdleToDrivingResume(
            dopplerSpeedKmh = 12.0,
            displacementSpeedKmh = 11.0,
            totalDisplacementMeters = 3.5,
            sustainedDurationMs = 6000L
        )
        assertEquals(false, stationaryDrift)

        // 4. Genuine vehicle resuming >= 10 km/h (e.g. 10.0 km/h) for >= 5 seconds (5000 ms) with >= 12m displacement -> resumes Driving (true)
        val genuineResumeAtExactThreshold = com.example.service.MileageTrackingService.verifyIdleToDrivingResume(
            dopplerSpeedKmh = 10.0,
            displacementSpeedKmh = 9.5,
            totalDisplacementMeters = 14.0,
            sustainedDurationMs = 5000L
        )
        assertEquals(true, genuineResumeAtExactThreshold)
    }

    @Test
    fun testFuelVolumeUnitNormalization() {
        assertEquals("L", com.example.service.MileageTrackingService.normalizeFuelVolumeUnit("/L"))
        assertEquals("L", com.example.service.MileageTrackingService.normalizeFuelVolumeUnit(" / L "))
        assertEquals("L", com.example.service.MileageTrackingService.normalizeFuelVolumeUnit("L"))
        assertEquals("L", com.example.service.MileageTrackingService.normalizeFuelVolumeUnit(""))
    }

    @Test
    fun testLiveActiveDriveNotificationFormatting() {
        val drivingTitle = com.example.service.MileageTrackingService.formatActiveDriveNotificationTitle(
            distanceKm = 12.45,
            unitDistance = "km",
            currencySymbol = "₹",
            liveFuelCost = 90.82,
            isIdle = false
        )
        assertEquals("Live Drive • 12.45 km • Fuel: ₹90.82", drivingTitle)

        val idleTitle = com.example.service.MileageTrackingService.formatActiveDriveNotificationTitle(
            distanceKm = 12.45,
            unitDistance = "km",
            currencySymbol = "₹",
            liveFuelCost = 90.82,
            isIdle = true
        )
        assertEquals("Idle • 12.45 km • Fuel: ₹90.82", idleTitle)

        val drivingBody = com.example.service.MileageTrackingService.formatActiveDriveNotificationBody(
            speedKmh = 42f,
            elapsedSeconds = 125L,
            vehicleName = "Honda City",
            categoryName = "Business",
            isIdle = false,
            idleSecondsRemaining = 480L
        )
        assertEquals("Speed: 42 km/h • Time: 02:05 • Honda City (Business)", drivingBody)
    }

    @Test
    fun testChangeRecordedTripVehicleRecalculatesCosts() {
        val settings = com.example.data.model.AppSettingsEntity(
            countryCode = "IN",
            currentCity = "New Delhi",
            currentFuelPrice = 100.0
        )

        // Recorded trip initially logged on a Car (14 km/L) over 90 km
        val recordedTrip = com.example.data.model.TripEntity(
            id = "trip_1",
            vehicleId = "veh_car",
            category = "BUSINESS",
            distanceKm = 90.0,
            fuelCost = (90.0 / 14.0) * 100.0,
            maintenanceCost = 90.0 * 1.5,
            tolls = 25.0,
            parking = 15.0,
            totalPersonalCost = ((90.0 / 14.0) * 100.0) + (90.0 * 1.5) + 40.0
        )

        // Switch vehicle to a Motorcycle (45 km/L, wear rate 0.50/km)
        val motorcycle = com.example.data.model.VehicleEntity(
            id = "veh_moto",
            name = "Royal Enfield",
            vehicleType = "MOTORCYCLE",
            fuelType = "PETROL",
            efficiencyVal = 45.0,
            maintenanceCostPerKm = 0.50
        )

        val updatedTrip = com.example.data.local.MileageRepository.calculateTripWithVehicle(
            trip = recordedTrip,
            vehicle = motorcycle,
            settings = settings
        )

        assertEquals("veh_moto", updatedTrip.vehicleId)
        // Since settings.selectedFuelType defaults to PETROL and settings.currentFuelPrice is 100.0:
        val expectedFuelCost = (90.0 / 45.0) * 100.0 // 2.0 L * 100.0 = 200.0
        val expectedMaintCost = 90.0 * 0.50 // 45.0
        val expectedTotalCost = expectedFuelCost + expectedMaintCost + 40.0

        assertEquals(expectedFuelCost, updatedTrip.fuelCost, 0.01)
        assertEquals(expectedMaintCost, updatedTrip.maintenanceCost, 0.01)
        assertEquals(expectedTotalCost, updatedTrip.totalPersonalCost, 0.01)
    }

    @Test
    fun testPermissionReadinessStrictEnabledGating() {
        // Case 1: Only "While using the app" granted (hasFineLocation = true, hasBackgroundLocation = false)
        val whileUsingOnly = com.example.ui.settings.PermissionReadinessState(
            hasFineLocation = true,
            hasCoarseLocation = true,
            hasBackgroundLocation = false,
            isIgnoringBatteryOptimizations = true,
            hasActivityRecognition = true,
            hasNotifications = true
        )
        assertEquals(false, whileUsingOnly.isAllTimePreciseLocationEnabled)
        assertEquals(false, whileUsingOnly.areAllRequirementsMatched)
        assertEquals(false, whileUsingOnly.isSystemSettingShownAsEnabled(true))
        assertTrue(whileUsingOnly.locationDiagnosticStatus().contains("Not Enabled"))

        // Case 2: Approximate location only (hasFineLocation = false, hasCoarseLocation = true)
        val approximateOnly = com.example.ui.settings.PermissionReadinessState(
            hasFineLocation = false,
            hasCoarseLocation = true,
            hasBackgroundLocation = true,
            isIgnoringBatteryOptimizations = true,
            hasActivityRecognition = true,
            hasNotifications = true
        )
        assertEquals(false, approximateOnly.isAllTimePreciseLocationEnabled)
        assertEquals(false, approximateOnly.areAllRequirementsMatched)
        assertEquals(false, approximateOnly.isSystemSettingShownAsEnabled(true))

        // Case 3: Battery optimization still active (isIgnoringBatteryOptimizations = false)
        val batteryRestricted = com.example.ui.settings.PermissionReadinessState(
            hasFineLocation = true,
            hasCoarseLocation = true,
            hasBackgroundLocation = true,
            isIgnoringBatteryOptimizations = false,
            hasActivityRecognition = true,
            hasNotifications = true
        )
        assertEquals(true, batteryRestricted.isAllTimePreciseLocationEnabled)
        assertEquals(false, batteryRestricted.isUnrestrictedBatteryEnabled)
        assertEquals(false, batteryRestricted.areAllRequirementsMatched)
        assertEquals(false, batteryRestricted.isSystemSettingShownAsEnabled(true))

        // Case 4: All 4 requirements matched ("Allow all the time" + Precise + Unrestricted Battery + Activity + Notifications)
        val allMatched = com.example.ui.settings.PermissionReadinessState(
            hasFineLocation = true,
            hasCoarseLocation = true,
            hasBackgroundLocation = true,
            isIgnoringBatteryOptimizations = true,
            hasActivityRecognition = true,
            hasNotifications = true
        )
        assertEquals(true, allMatched.isAllTimePreciseLocationEnabled)
        assertEquals(true, allMatched.isUnrestrictedBatteryEnabled)
        assertEquals(4, allMatched.matchedRequirementsCount)
        assertEquals(true, allMatched.areAllRequirementsMatched)
        assertEquals(true, allMatched.isSystemSettingShownAsEnabled(true))
    }

    @Test
    fun testActiveDriveStepAccumulatesMicroStepsAndRecoversHighwayGaps() {
        // 1. Sub-second / steady slow city fix (< 2.5m delta) while Doppler shows active driving speed (12 km/h)
        // -> must ACCUMULATE_PENDING_ANCHOR instead of discarding the point & advancing anchor!
        val microStepDecision = com.example.service.MileageTrackingService.evaluateActiveMovementStep(
            deltaMeters = 1.9,
            deltaSeconds = 0.6,
            dopplerSpeedKmh = 11.4,
            hasDopplerSpeed = true,
            smoothedSpeedKmh = 12.0,
            anchorDisplacementMeters = 1.9,
            anchorElapsedSeconds = 0.6
        )
        assertEquals(
            com.example.service.MileageTrackingService.ActiveStepDecision.ACCUMULATE_PENDING_ANCHOR,
            microStepDecision
        )

        // 2. Normal steady city driving step (4.5m over 1.0s = 16.2 km/h) -> must COMMIT_DISTANCE
        val cityStepDecision = com.example.service.MileageTrackingService.evaluateActiveMovementStep(
            deltaMeters = 4.5,
            deltaSeconds = 1.0,
            dopplerSpeedKmh = 16.2,
            hasDopplerSpeed = true,
            smoothedSpeedKmh = 15.8,
            anchorDisplacementMeters = 4.5,
            anchorElapsedSeconds = 1.0
        )
        assertEquals(
            com.example.service.MileageTrackingService.ActiveStepDecision.COMMIT_DISTANCE,
            cityStepDecision
        )

        // 3. Highway gap > 500m over an 25s overpass stretch (690m at ~100 km/h) -> dynamic max step must COMMIT_DISTANCE
        val highwayGapDecision = com.example.service.MileageTrackingService.evaluateActiveMovementStep(
            deltaMeters = 690.0,
            deltaSeconds = 25.0,
            dopplerSpeedKmh = 98.0,
            hasDopplerSpeed = true,
            smoothedSpeedKmh = 95.0,
            anchorDisplacementMeters = 690.0,
            anchorElapsedSeconds = 25.0
        )
        assertEquals(
            com.example.service.MileageTrackingService.ActiveStepDecision.COMMIT_DISTANCE,
            highwayGapDecision
        )

        // 4. True parked jitter (1.8m random drift at 1.2 km/h) -> SUB_THRESHOLD
        val parkedJitterDecision = com.example.service.MileageTrackingService.evaluateActiveMovementStep(
            deltaMeters = 1.8,
            deltaSeconds = 5.0,
            dopplerSpeedKmh = 1.2,
            hasDopplerSpeed = true,
            smoothedSpeedKmh = 1.0,
            anchorDisplacementMeters = 2.0,
            anchorElapsedSeconds = 10.0
        )
        assertEquals(
            com.example.service.MileageTrackingService.ActiveStepDecision.SUB_THRESHOLD,
            parkedJitterDecision
        )
    }

    @Test
    fun testTrueStationaryVerificationRequiresGpsAnchorAndSensorStillness() {
        // 1. Driving continuously on highway with recent smoothed speed -> NOT stationary
        assertEquals(
            false,
            com.example.service.MileageTrackingService.isTrueStationaryState(
                idleSeconds = 16L,
                minIdleSeconds = 15L,
                smoothedSpeedKmh = 54.0,
                anchorDisplacementMeters = 120.0,
                anchorElapsedSeconds = 10.0,
                hasFreshSensorSamples = true,
                rmsAccelMps2 = 0.55f,
                timeSinceLastGpsFixMs = 1000L
            )
        )

        // 2. Brief highway tunnel GPS dropout (35s) while previous speed was 75 km/h & active vehicle vibration -> NOT stationary
        assertEquals(
            false,
            com.example.service.MileageTrackingService.isTrueStationaryState(
                idleSeconds = 35L,
                minIdleSeconds = 15L,
                smoothedSpeedKmh = 75.0,
                anchorDisplacementMeters = 0.0,
                anchorElapsedSeconds = 35.0,
                hasFreshSensorSamples = true,
                rmsAccelMps2 = 0.48f,
                timeSinceLastGpsFixMs = 35_000L
            )
        )

        // 3. Parked car at home: GPS settled (~0.4 km/h), small drift (2m), low accel RMS -> TRUE stationary (latch idle & allow stop)
        assertEquals(
            true,
            com.example.service.MileageTrackingService.isTrueStationaryState(
                idleSeconds = 480L,
                minIdleSeconds = 480L,
                smoothedSpeedKmh = 0.4,
                anchorDisplacementMeters = 2.0,
                anchorElapsedSeconds = 480.0,
                hasFreshSensorSamples = true,
                rmsAccelMps2 = 0.04f,
                timeSinceLastGpsFixMs = 2000L
            )
        )

        // 4. Parked outdoors and walked 45m slowly at 4.2 km/h into a store -> still TRUE stationary for driving (allows auto-stop)
        assertEquals(
            true,
            com.example.service.MileageTrackingService.isTrueStationaryState(
                idleSeconds = 480L,
                minIdleSeconds = 480L,
                smoothedSpeedKmh = 4.2,
                anchorDisplacementMeters = 45.0,
                anchorElapsedSeconds = 480.0,
                hasFreshSensorSamples = true,
                rmsAccelMps2 = 1.4f,
                timeSinceLastGpsFixMs = 5000L
            )
        )
    }

    @Test
    fun testDurationClockFormattingAndLongDriveNotification() {
        assertEquals("00:00", com.example.util.UnitConverter.formatDurationClock(0L))
        assertEquals("02:05", com.example.util.UnitConverter.formatDurationClock(125L))
        assertEquals("59:59", com.example.util.UnitConverter.formatDurationClock(3599L))
        assertEquals("01:00:00", com.example.util.UnitConverter.formatDurationClock(3600L))
        assertEquals("01:15:12", com.example.util.UnitConverter.formatDurationClock(4512L))

        val longBody = com.example.service.MileageTrackingService.formatActiveDriveNotificationBody(
            speedKmh = 68f,
            elapsedSeconds = 4512L,
            vehicleName = "Honda City",
            categoryName = "Business",
            isIdle = false,
            idleSecondsRemaining = 480L
        )
        assertEquals("Speed: 68 km/h • Time: 01:15:12 • Honda City (Business)", longBody)
    }

    @Test
    fun testAutoStopEndTimeAndLiveElapsedExcludesIdleWait() {
        val driveStartWall = 1_700_000_000_000L
        val lastMovedWall = driveStartWall + 720_000L // 12m active driving
        val autoStopNowWall = lastMovedWall + 480_000L // +8m parked idle wait

        // Auto-stop ends cleanly at last movement (excludes idle wait)
        val savedEndTime = com.example.service.MileageTrackingService.computeAutoStopEndTimeMillis(
            isAutoStop = true,
            driveStartTimeMillis = driveStartWall,
            lastMovedTimeMillis = lastMovedWall,
            nowMs = autoStopNowWall
        )
        assertEquals(lastMovedWall, savedEndTime)

        // Manual stop ends at user tap time (`now`)
        val manualEndTime = com.example.service.MileageTrackingService.computeAutoStopEndTimeMillis(
            isAutoStop = false,
            driveStartTimeMillis = driveStartWall,
            lastMovedTimeMillis = lastMovedWall,
            nowMs = autoStopNowWall
        )
        assertEquals(autoStopNowWall, manualEndTime)

        // Live display drive time pauses at last movement while stationary idle so it matches saved duration
        val startElapsed = 10_000L
        val lastMovedElapsed = startElapsed + 720_000L
        val idleNowElapsed = lastMovedElapsed + 300_000L

        assertEquals(
            1020L,
            com.example.service.MileageTrackingService.computeDisplayDriveElapsedSeconds(
                isIdle = false,
                driveStartElapsedMs = startElapsed,
                lastMovedElapsedMs = lastMovedElapsed,
                nowElapsedMs = idleNowElapsed
            )
        )
        assertEquals(
            720L,
            com.example.service.MileageTrackingService.computeDisplayDriveElapsedSeconds(
                isIdle = true,
                driveStartElapsedMs = startElapsed,
                lastMovedElapsedMs = lastMovedElapsed,
                nowElapsedMs = idleNowElapsed
            )
        )
    }

    @Test
    fun testStaleGpsVelocityDecaysWhenParkedIndoors() {
        // Fresh fix (< 6s): uses live smoothed speed
        assertEquals(
            2.1,
            com.example.service.MileageTrackingService.resolveEffectiveSpeedForStationaryCheck(
                liveSmoothedSpeedKmh = 2.1,
                lastKnownValidGpsSpeedKmh = 45.0,
                timeSinceLastGpsFixMs = 3_000L,
                hasFreshSensorSamples = true,
                rmsAccelMps2 = 0.05f
            ),
            0.01
        )

        // Highway tunnel (< 90s) with real vehicle vibration: retains prior highway speed
        assertEquals(
            75.0,
            com.example.service.MileageTrackingService.resolveEffectiveSpeedForStationaryCheck(
                liveSmoothedSpeedKmh = 0.0,
                lastKnownValidGpsSpeedKmh = 75.0,
                timeSinceLastGpsFixMs = 35_000L,
                hasFreshSensorSamples = true,
                rmsAccelMps2 = 0.48f
            ),
            0.01
        )

        // Indoor garage parking (> 20s no GPS + motionless accelerometer < 0.18): decays stale speed to 0.0
        assertEquals(
            0.0,
            com.example.service.MileageTrackingService.resolveEffectiveSpeedForStationaryCheck(
                liveSmoothedSpeedKmh = 0.0,
                lastKnownValidGpsSpeedKmh = 22.0,
                timeSinceLastGpsFixMs = 22_000L,
                hasFreshSensorSamples = true,
                rmsAccelMps2 = 0.04f
            ),
            0.01
        )
    }

    @Test
    fun testAutoStopTimerCompletionSavesTripAndResetsStateWithoutSelfCancellation() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.data.local.MileageDatabase.getInstance(context)
        val repo = com.example.data.local.MileageRepository(db)

        val driveStartMs = System.currentTimeMillis() - 900_000L // started 15 min ago
        val lastMovedMs = driveStartMs + 420_000L // drove 7 min, then parked 8 min
        val tripId: String = kotlinx.coroutines.runBlocking {
            repo.updateSettings(com.example.data.model.AppSettingsEntity(autoTrackingEnabled = false, stopTimeoutMinutes = 8))
            repo.startTrip(
                vehicleId = null,
                category = com.example.data.model.TripCategory.BUSINESS,
                startTime = driveStartMs,
                startLat = 28.6139,
                startLng = 77.2090,
                startAddress = "Start Point"
            )
        }

        val controller = org.robolectric.Robolectric.buildService(com.example.service.MileageTrackingService::class.java).create()
        val service = controller.get()

        try {
            // Configure active drive state via reflection so we execute the exact auto-stop timer completion path
            val cls = com.example.service.MileageTrackingService::class.java
            val modeEnum = cls.declaredClasses.first { it.simpleName == "ServiceMode" }
            val activeDriveEnumVal = modeEnum.enumConstants!!.first { it.toString() == "ACTIVE_DRIVE" }

            fun setField(name: String, value: Any?) {
                val f = cls.getDeclaredField(name)
                f.isAccessible = true
                f.set(service, value)
            }

            setField("currentMode", activeDriveEnumVal)
            setField("activeTripId", tripId)
            setField("totalDistanceMeters", 3_500.0) // 3.5 km (> 0.05 km auto-save floor)
            setField("driveStartTimeMillis", driveStartMs)
            setField("lastMovedTimeMillis", lastMovedMs)
            setField("lastDistanceAddedTimeMillis", lastMovedMs)
            setField("isIdleLatched", true)

            @Suppress("UNCHECKED_CAST")
            val pendingField = cls.getDeclaredField("pendingTripPoints").apply { isAccessible = true }
            val pendingList = pendingField.get(service) as MutableList<com.example.data.model.TripPointEntity>
            pendingList.add(
                com.example.data.model.TripPointEntity(
                    tripId = tripId,
                    latitude = 28.6300,
                    longitude = 77.2200,
                    speed = 35f,
                    timestamp = lastMovedMs
                )
            )

            com.example.service.TrackingStateManager.update {
                it.copy(
                    isTracking = true,
                    tripId = tripId,
                    distanceKm = 3.5,
                    isIdle = true,
                    idleSecondsRemaining = 0L,
                    currentAddress = "End Point"
                )
            }

            // Simulate finalizeActiveDriveCompletion invoked inside `timerJob` (cancelTimerJob = false)
            val scopeField = cls.getDeclaredField("serviceScope").apply { isAccessible = true }
            val scope = scopeField.get(service) as kotlinx.coroutines.CoroutineScope
            val finalizeMethod = cls.getDeclaredMethod(
                "finalizeActiveDriveCompletion",
                Double::class.java,
                Double::class.java,
                java.lang.Double::class.java,
                String::class.java,
                Boolean::class.java,
                Boolean::class.java,
                kotlin.coroutines.Continuation::class.java
            ).apply { isAccessible = true }

            val timerJobField = cls.getDeclaredField("timerJob").apply { isAccessible = true }
            val simulatedTimerJob = scope.launch {
                kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Any?> { cont ->
                    finalizeMethod.invoke(
                        service,
                        0.0,
                        0.0,
                        null,
                        "Auto-completed after 8m stationary",
                        true,
                        false,
                        cont
                    )
                }
            }
            timerJobField.set(service, simulatedTimerJob)

            kotlinx.coroutines.runBlocking {
                simulatedTimerJob.join()
            }

            val savedTrip = kotlinx.coroutines.runBlocking { repo.getTripById(tripId) }
            val activeTrip = kotlinx.coroutines.runBlocking { repo.getActiveTrip() }
            val pointsCount = kotlinx.coroutines.runBlocking { repo.getTripPointsCount(tripId) }
            val latestPointTs = kotlinx.coroutines.runBlocking { repo.getLatestTripPointTimestamp(tripId) }

            assertTrue("Trip must exist in DB after auto-save", savedTrip != null)
            assertEquals("Trip endTime must exclude idle wait and equal lastMovedMs", lastMovedMs, savedTrip!!.endTime)
            assertEquals("Active trip must be cleared after auto-stop", null, activeTrip)
            assertEquals("Pending GPS points must be flushed on auto-stop", 1, pointsCount)
            assertEquals("Latest point timestamp query must match", lastMovedMs, latestPointTs)
            assertEquals("Live HUD tracking state must reset to false", false, com.example.service.TrackingStateManager.trackingData.value.isTracking)
        } finally {
            com.example.service.TrackingStateManager.reset()
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
        }
    }

    @Test
    fun testPlaceholderOrCoordinateAddressDetection() {
        assertEquals(true, com.example.service.MileageTrackingService.isPlaceholderOrCoordinateAddress(null))
        assertEquals(true, com.example.service.MileageTrackingService.isPlaceholderOrCoordinateAddress(""))
        assertEquals(true, com.example.service.MileageTrackingService.isPlaceholderOrCoordinateAddress("   "))
        assertEquals(true, com.example.service.MileageTrackingService.isPlaceholderOrCoordinateAddress("Detecting start location..."))
        assertEquals(true, com.example.service.MileageTrackingService.isPlaceholderOrCoordinateAddress("Resolving road name..."))
        assertEquals(true, com.example.service.MileageTrackingService.isPlaceholderOrCoordinateAddress("Start Location"))
        assertEquals(true, com.example.service.MileageTrackingService.isPlaceholderOrCoordinateAddress("Unnamed Road"))
        assertEquals(true, com.example.service.MileageTrackingService.isPlaceholderOrCoordinateAddress("28.6139, 77.2090"))
        assertEquals(true, com.example.service.MileageTrackingService.isPlaceholderOrCoordinateAddress("-33.8688, 151.2093"))

        assertEquals(false, com.example.service.MileageTrackingService.isPlaceholderOrCoordinateAddress("MG Road, Indiranagar, Bengaluru"))
        assertEquals(false, com.example.service.MileageTrackingService.isPlaceholderOrCoordinateAddress("Home"))
        assertEquals(false, com.example.service.MileageTrackingService.isPlaceholderOrCoordinateAddress("Sector 18, Noida"))
    }

    @Test
    fun testRoadAreaCityAddressFormatting() {
        // 1. Full hierarchy: Road, Area, City
        assertEquals(
            "MG Road, Indiranagar, Bengaluru",
            com.example.service.MileageTrackingService.formatResolvedAddressComponents(
                thoroughfare = "MG Road",
                featureName = "42",
                subLocality = "Indiranagar",
                locality = "Bengaluru",
                subAdminArea = "Bengaluru Urban",
                fullAddressLine = "42, MG Road, Indiranagar, Bengaluru, Karnataka 560001",
                lat = 12.9716,
                lng = 77.5946
            )
        )

        // 2. Deduplicates identical area & city and skips Unnamed Road / numeric house number
        assertEquals(
            "Outer Ring Road, Noida",
            com.example.service.MileageTrackingService.formatResolvedAddressComponents(
                thoroughfare = "Unnamed Road",
                featureName = "Outer Ring Road",
                subLocality = "Noida",
                locality = "Noida",
                subAdminArea = "Gautam Buddha Nagar",
                fullAddressLine = null,
                lat = 28.5355,
                lng = 77.3910
            )
        )

        // 3. Ignores Plus Code / house number featureName when thoroughfare is absent
        assertEquals(
            "Connaught Place, New Delhi",
            com.example.service.MileageTrackingService.formatResolvedAddressComponents(
                thoroughfare = null,
                featureName = "J6JJ+9R",
                subLocality = "Connaught Place",
                locality = "New Delhi",
                subAdminArea = "New Delhi",
                fullAddressLine = null,
                lat = 28.6315,
                lng = 77.2167
            )
        )

        // 4. Falls back cleanly to address line or formatted coordinates if all segments are blank
        assertEquals(
            "28.6139, 77.2090",
            com.example.service.MileageTrackingService.formatResolvedAddressComponents(
                thoroughfare = null,
                featureName = null,
                subLocality = null,
                locality = null,
                subAdminArea = null,
                fullAddressLine = null,
                lat = 28.6139,
                lng = 77.2090
            )
        )
    }

    @Test
    fun testStartAddressOverwriteAndPreSaveFallback() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.data.local.MileageDatabase.getInstance(context)
        val repo = com.example.data.local.MileageRepository(db)

        try {
            val startMs = System.currentTimeMillis() - 300_000L
            val tripId = kotlinx.coroutines.runBlocking {
                val id = repo.startTrip(
                    vehicleId = null,
                    category = com.example.data.model.TripCategory.BUSINESS,
                    startTime = startMs,
                    startLat = 12.9716,
                    startLng = 77.5946,
                    startAddress = "12.9716, 77.5946"
                )
                val initial = repo.getTripById(id)!!
                assertEquals(true, com.example.service.MileageTrackingService.isPlaceholderOrCoordinateAddress(initial.startAddress))

                // Simulate delayed pre-save start address resolution before saving the completed trip
                repo.completeTrip(
                    tripId = id,
                    endTime = startMs + 240_000L,
                    endLat = 12.9352,
                    endLng = 77.6245,
                    endAddress = "80 Feet Road, Koramangala, Bengaluru",
                    distanceKm = 4.2,
                    notes = "Completed",
                    startAddressOverride = "MG Road, Indiranagar, Bengaluru"
                )
                id
            }

            val savedTrip = kotlinx.coroutines.runBlocking { repo.getTripById(tripId) }
            assertTrue(savedTrip != null)
            assertEquals("MG Road, Indiranagar, Bengaluru", savedTrip!!.startAddress)
            assertEquals("80 Feet Road, Koramangala, Bengaluru", savedTrip.endAddress)
        } finally {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
        }
    }

    @Test
    fun testPassiveDetectorBlocksIndoorWifiDriftAndBurstJumps() {
        // 1. Non-Doppler Wi-Fi / cell snap at 26m accuracy must be ignored (exceeds 20m non-Doppler cap)
        val wifiJumpBlocked = com.example.service.MileageTrackingService.evaluatePassiveAutoStartCandidate(
            accuracyMeters = 26f,
            deltaMs = 3000L,
            stepDistMeters = 22.0,
            dopplerSpeedKmh = 26.4, // synthetic fallback speed computed from displacement
            displacementSpeedKmh = 26.4,
            thresholdKmh = 15.0,
            hasMotionSensor = true,
            hasFreshSensorSamples = true,
            rmsAccelMps2 = 0.0f,
            hasHardwareDopplerSpeed = false
        )
        assertEquals(
            com.example.service.MileageTrackingService.PassiveCandidateDecision.IGNORE_BAD_ACCURACY,
            wifiJumpBlocked
        )

        // 2. Non-Doppler snap with 15m accuracy while phone is motionless on desk must NOT trigger strongGpsConsensus
        val deskMotionlessBlocked = com.example.service.MileageTrackingService.evaluatePassiveAutoStartCandidate(
            accuracyMeters = 15f,
            deltaMs = 3000L,
            stepDistMeters = 18.0,
            dopplerSpeedKmh = 21.6,
            displacementSpeedKmh = 21.6,
            thresholdKmh = 15.0,
            hasMotionSensor = true,
            hasFreshSensorSamples = true,
            rmsAccelMps2 = 0.0f,
            hasHardwareDopplerSpeed = false
        )
        assertEquals(
            com.example.service.MileageTrackingService.PassiveCandidateDecision.BLOCKED_STATIONARY_SENSOR,
            deskMotionlessBlocked
        )
    }

    @Test
    fun testActivityRecognitionVetoAndFastSustainedOverride() {
        val walkingTypes = listOf(
            com.google.android.gms.location.DetectedActivity.WALKING,
            com.google.android.gms.location.DetectedActivity.ON_BICYCLE,
            com.google.android.gms.location.DetectedActivity.RUNNING,
            com.google.android.gms.location.DetectedActivity.STILL
        )

        // Every non-vehicular activity blocks passive steps & crossing at 14-16 km/h / 15m
        walkingTypes.forEach { activityType ->
            val passiveDecision = com.example.service.MileageTrackingService.evaluatePassiveAutoStartCandidate(
                accuracyMeters = 8f,
                deltaMs = 3000L,
                stepDistMeters = 12.5,
                dopplerSpeedKmh = 15.0,
                displacementSpeedKmh = 15.0,
                thresholdKmh = 12.0,
                hasMotionSensor = true,
                hasFreshSensorSamples = true,
                rmsAccelMps2 = 0.45f,
                hasHardwareDopplerSpeed = true,
                latestActivityType = activityType,
                latestActivityConfidence = 85,
                isActivityFresh = true
            )
            assertEquals(
                "Expected OS activity $activityType to block moderate step",
                com.example.service.MileageTrackingService.PassiveCandidateDecision.BLOCKED_STATIONARY_SENSOR,
                passiveDecision
            )

            val crossingBlocked = com.example.service.MileageTrackingService.verifyVehicularThresholdCrossing(
                dopplerSpeedKmh = 16.0,
                displacementSpeedKmh = 15.0,
                thresholdKmh = 12.0,
                totalDisplacementMeters = 15.0,
                sustainedDurationMs = 5500L,
                consecutiveMovingFixes = 3,
                pathLinearityRatio = 0.92,
                hasHardwareDopplerSpeed = true,
                latestActivityType = activityType,
                latestActivityConfidence = 85,
                isActivityFresh = true
            )
            assertEquals("Expected OS activity $activityType to block moderate crossing", false, crossingBlocked)
        }

        // Fast sustained vehicle override (>= 22 km/h Doppler, >= 18 km/h displacement, >= 35m linear, hardware Doppler) overrides stale STILL/WALKING
        val overriddenStep = com.example.service.MileageTrackingService.evaluatePassiveAutoStartCandidate(
            accuracyMeters = 7f,
            deltaMs = 3000L,
            stepDistMeters = 21.0,
            dopplerSpeedKmh = 26.0,
            displacementSpeedKmh = 25.2,
            thresholdKmh = 15.0,
            hasMotionSensor = true,
            hasFreshSensorSamples = true,
            rmsAccelMps2 = 0.25f,
            hasHardwareDopplerSpeed = true,
            latestActivityType = com.google.android.gms.location.DetectedActivity.STILL,
            latestActivityConfidence = 85,
            isActivityFresh = true
        )
        assertEquals(
            com.example.service.MileageTrackingService.PassiveCandidateDecision.ACCUMULATE_MOVING_STEP,
            overriddenStep
        )

        val overriddenCrossing = com.example.service.MileageTrackingService.verifyVehicularThresholdCrossing(
            dopplerSpeedKmh = 26.0,
            displacementSpeedKmh = 22.0,
            thresholdKmh = 15.0,
            totalDisplacementMeters = 40.0,
            sustainedDurationMs = 5500L,
            consecutiveMovingFixes = 3,
            pathLinearityRatio = 0.96,
            hasHardwareDopplerSpeed = true,
            latestActivityType = com.google.android.gms.location.DetectedActivity.WALKING,
            latestActivityConfidence = 80,
            isActivityFresh = true
        )
        assertEquals(true, overriddenCrossing)

        // IN_VEHICLE allows standard >= 12 km/h, >= 10m, >= 5s auto-start without needing override speed
        val inVehicleStart = com.example.service.MileageTrackingService.verifyVehicularThresholdCrossing(
            dopplerSpeedKmh = 15.0,
            displacementSpeedKmh = 13.8,
            thresholdKmh = 12.0,
            totalDisplacementMeters = 18.0,
            sustainedDurationMs = 5200L,
            consecutiveMovingFixes = 3,
            pathLinearityRatio = 0.90,
            hasHardwareDopplerSpeed = true,
            latestActivityType = com.google.android.gms.location.DetectedActivity.IN_VEHICLE,
            latestActivityConfidence = 90,
            isActivityFresh = true
        )
        assertEquals(true, inVehicleStart)
    }

    @Test
    fun testEarly45SecondFalseAutoStartCircuitBreaker() {
        // 1. Auto-started trip stops moving within 45s and < 25m -> MUST abort & discard
        assertEquals(
            true,
            com.example.service.MileageTrackingService.shouldAbortEarlyFalseAutoStart(
                isAutoStarted = true,
                driveAgeFromLaunchMs = 22_000L,
                idleSeconds = 12L,
                isIdleLatched = true,
                totalDistanceMeters = 18.0,
                smoothedSpeedKmh = 0.0,
                hasRecentGpsFix = true,
                latestActivityType = null,
                latestActivityConfidence = 0,
                isActivityFresh = false
            )
        )

        // 2. High-confidence OS WALKING/STILL while under 25m within 45s -> MUST abort & discard
        assertEquals(
            true,
            com.example.service.MileageTrackingService.shouldAbortEarlyFalseAutoStart(
                isAutoStarted = true,
                driveAgeFromLaunchMs = 18_000L,
                idleSeconds = 10L,
                isIdleLatched = false,
                totalDistanceMeters = 12.0,
                smoothedSpeedKmh = 3.5,
                hasRecentGpsFix = true,
                latestActivityType = com.google.android.gms.location.DetectedActivity.WALKING,
                latestActivityConfidence = 85,
                isActivityFresh = true
            )
        )

        // 3. Manual drive (`isAutoStarted = false`) must NEVER auto-discard even if under 25m
        assertEquals(
            false,
            com.example.service.MileageTrackingService.shouldAbortEarlyFalseAutoStart(
                isAutoStarted = false,
                driveAgeFromLaunchMs = 22_000L,
                idleSeconds = 20L,
                isIdleLatched = true,
                totalDistanceMeters = 5.0,
                smoothedSpeedKmh = 0.0,
                hasRecentGpsFix = true,
                latestActivityType = com.google.android.gms.location.DetectedActivity.STILL,
                latestActivityConfidence = 95,
                isActivityFresh = true
            )
        )

        // 4. Genuine departure >= 25m within 45s -> must NOT abort
        assertEquals(
            false,
            com.example.service.MileageTrackingService.shouldAbortEarlyFalseAutoStart(
                isAutoStarted = true,
                driveAgeFromLaunchMs = 22_000L,
                idleSeconds = 12L,
                isIdleLatched = true,
                totalDistanceMeters = 32.0,
                smoothedSpeedKmh = 0.0,
                hasRecentGpsFix = true,
                latestActivityType = null,
                latestActivityConfidence = 0,
                isActivityFresh = false
            )
        )

        // 5. Past 45s grace window -> handled by normal idle timeout instead of early discard
        assertEquals(
            false,
            com.example.service.MileageTrackingService.shouldAbortEarlyFalseAutoStart(
                isAutoStarted = true,
                driveAgeFromLaunchMs = 46_000L,
                idleSeconds = 20L,
                isIdleLatched = true,
                totalDistanceMeters = 14.0,
                smoothedSpeedKmh = 0.0,
                hasRecentGpsFix = true,
                latestActivityType = com.google.android.gms.location.DetectedActivity.STILL,
                latestActivityConfidence = 95,
                isActivityFresh = true
            )
        )
    }

    @Test
    fun testPhantomAutoStartedTripDeletionAndReset() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.data.local.MileageDatabase.getInstance(context)
        val repo = com.example.data.local.MileageRepository(db)

        try {
            kotlinx.coroutines.runBlocking {
                val id = repo.startTrip(
                    vehicleId = null,
                    category = com.example.data.model.TripCategory.BUSINESS,
                    startTime = System.currentTimeMillis() - 15_000L,
                    startLat = 28.6139,
                    startLng = 77.2090,
                    startAddress = "Janpath, Connaught Place, New Delhi"
                )
                assertTrue(repo.getTripById(id) != null)
                assertTrue(repo.getActiveTrip() != null)

                // Simulate early false-start discard cleanup
                repo.deleteTripById(id)
                com.example.service.TrackingStateManager.reset()

                assertEquals(null, repo.getTripById(id))
                assertEquals(null, repo.getActiveTrip())
                assertEquals(false, com.example.service.TrackingStateManager.trackingData.value.isTracking)
            }
        } finally {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
        }
    }
}
