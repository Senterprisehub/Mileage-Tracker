package com.example

import com.example.data.fuel.LiveFuelPriceManager
import com.example.data.model.CountryRegistry
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
        val thresholdKmh = 10.0

        // 1. Stationary GPS drift (high Doppler spike but only 1.8m physical displacement) -> must NOT trigger
        val stationaryDrift = com.example.service.MileageTrackingService.verifyVehicularThresholdCrossing(
            dopplerSpeedKmh = 12.0,
            displacementSpeedKmh = 11.0,
            thresholdKmh = thresholdKmh,
            totalDisplacementMeters = 1.8,
            sustainedDurationMs = 3000L
        )
        assertEquals(false, stationaryDrift)

        // 2. Walking below preset threshold (4.5 km/h over 8 meters and 6 seconds) -> must NOT trigger
        val walkingBelowThreshold = com.example.service.MileageTrackingService.verifyVehicularThresholdCrossing(
            dopplerSpeedKmh = 4.5,
            displacementSpeedKmh = 4.5,
            thresholdKmh = thresholdKmh,
            totalDisplacementMeters = 8.0,
            sustainedDurationMs = 6000L
        )
        assertEquals(false, walkingBelowThreshold)

        // 3. Brief 1-second speed spike (< 2.5s sustained window) -> must NOT trigger
        val briefSpike = com.example.service.MileageTrackingService.verifyVehicularThresholdCrossing(
            dopplerSpeedKmh = 15.0,
            displacementSpeedKmh = 14.0,
            thresholdKmh = thresholdKmh,
            totalDisplacementMeters = 6.5,
            sustainedDurationMs = 1000L
        )
        assertEquals(false, briefSpike)

        // 4. Genuine vehicle crossing preset threshold (>= 10 km/h, > 5m physical displacement, >= 2.5s sustained) -> MUST trigger
        val genuineVehicleStart = com.example.service.MileageTrackingService.verifyVehicularThresholdCrossing(
            dopplerSpeedKmh = 14.5,
            displacementSpeedKmh = 13.8,
            thresholdKmh = thresholdKmh,
            totalDisplacementMeters = 9.6,
            sustainedDurationMs = 2600L
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
}




