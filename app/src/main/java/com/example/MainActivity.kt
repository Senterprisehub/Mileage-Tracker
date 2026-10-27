package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TwoWheeler
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.MileageTrackingService
import com.example.data.model.TripCategory
import com.example.data.model.TripEntity
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.export.ExportReportDialog
import com.example.ui.garage.GarageScreen
import com.example.ui.settings.MandatoryPermissionsEnforcementDialog
import com.example.ui.settings.PermissionReadinessState
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.launchUnrestrictedBatteryRequest
import com.example.ui.settings.openAppDetailsSystemSettings
import com.example.ui.settings.rememberPermissionReadinessState
import com.example.ui.theme.BlueCobalt
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateLight
import com.example.ui.tracking.ActiveDriveDialog
import com.example.ui.trips.ManualTripDialog
import com.example.ui.trips.TripDetailDialog
import com.example.ui.viewmodel.MileageViewModel

enum class MainTab(val title: String) {
    DASHBOARD("Drives"),
    GARAGE("Vehicles"),
    SETTINGS("Settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun MainAppScreen(
    viewModel: MileageViewModel = viewModel()
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(MainTab.DASHBOARD) }

    val liveTracking by viewModel.liveTracking.collectAsStateWithLifecycle()
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val dayGroups by viewModel.dayWiseGroups.collectAsStateWithLifecycle()
    val monthlyMetrics by viewModel.monthlyMetrics.collectAsStateWithLifecycle()
    val isFetchingFuelRate by viewModel.isFetchingFuelRate.collectAsStateWithLifecycle()
    val availableMonths by viewModel.availableMonths.collectAsStateWithLifecycle()
    val allCompletedTrips by viewModel.completedTrips.collectAsStateWithLifecycle()
    val filteredTrips by viewModel.currentPeriodFilteredTrips.collectAsStateWithLifecycle()

    var showActiveHUD by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var selectedDetailTrip by remember { mutableStateOf<TripEntity?>(null) }

    val (permissionReadiness, refreshReadiness) = rememberPermissionReadinessState(context)
    var showPermissionsEnforcementModal by remember { mutableStateOf(false) }
    var hasShownLaunchEnforcement by remember { mutableStateOf(false) }

    var isManualDriveLaunchRequested by remember { mutableStateOf(false) }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        refreshReadiness()
        if (!isGranted) {
            openAppDetailsSystemSettings(context)
        }
    }

    // Location Permission Request Launcher for Drive Start & Auto-Tracking
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        refreshReadiness()
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            if (isManualDriveLaunchRequested) {
                isManualDriveLaunchRequested = false
                viewModel.startDrive(context)
                showActiveHUD = true
            }
        } else if (isManualDriveLaunchRequested) {
            isManualDriveLaunchRequested = false
            Toast.makeText(context, "Precise Location permission ('Allow all the time') is required", Toast.LENGTH_SHORT).show()
        }
    }

    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        refreshReadiness()
        if (!isGranted) openAppDetailsSystemSettings(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        refreshReadiness()
        if (!isGranted) openAppDetailsSystemSettings(context)
    }

    val requestAllTimePreciseLocation = {
        val current = PermissionReadinessState.evaluate(context)
        if (!current.hasFineLocation) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else if (!current.hasBackgroundLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            openAppDetailsSystemSettings(context)
        }
    }

    // Proactively enforce all 4 requirements on launch when not paused
    LaunchedEffect(permissionReadiness.areAllRequirementsMatched, settings.autoTrackingPausedUntil) {
        if (!permissionReadiness.areAllRequirementsMatched && !settings.isAutoTrackingPaused() && !hasShownLaunchEnforcement) {
            hasShownLaunchEnforcement = true
            showPermissionsEnforcementModal = true
        }
    }

    // Auto Background Motion Monitoring (Only active when all 4 requirements are matched)
    LaunchedEffect(
        permissionReadiness.areAllRequirementsMatched,
        settings.autoTrackingEnabled,
        settings.autoTrackingPausedUntil
    ) {
        if (permissionReadiness.areAllRequirementsMatched && settings.autoTrackingEnabled) {
            if (!liveTracking.isTracking) {
                MileageTrackingService.startMonitoring(context)
            }
        } else {
            if (!liveTracking.isTracking) {
                MileageTrackingService.stopMonitoring(context)
            }
        }
    }

    if (showPermissionsEnforcementModal && !permissionReadiness.areAllRequirementsMatched) {
        MandatoryPermissionsEnforcementDialog(
            readiness = permissionReadiness,
            onRequestLocation = requestAllTimePreciseLocation,
            onRequestBattery = {
                launchUnrestrictedBatteryRequest(context)
                refreshReadiness()
            },
            onRequestActivity = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                } else {
                    openAppDetailsSystemSettings(context)
                }
            },
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openAppDetailsSystemSettings(context)
                }
            },
            onOpenPermissionsHub = {
                showPermissionsEnforcementModal = false
                currentTab = MainTab.SETTINGS
            },
            onDismiss = {
                showPermissionsEnforcementModal = false
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == MainTab.DASHBOARD,
                    onClick = { currentTab = MainTab.DASHBOARD },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == MainTab.DASHBOARD) Icons.Filled.DirectionsCar else Icons.Outlined.DirectionsCar,
                            contentDescription = "Drives"
                        )
                    },
                    label = { Text("Drives") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BlueCobalt,
                        selectedTextColor = BlueCobalt,
                        indicatorColor = BlueCobalt.copy(alpha = 0.12f),
                        unselectedIconColor = SlateLight,
                        unselectedTextColor = SlateLight
                    )
                )

                NavigationBarItem(
                    selected = currentTab == MainTab.GARAGE,
                    onClick = { currentTab = MainTab.GARAGE },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == MainTab.GARAGE) Icons.Filled.TwoWheeler else Icons.Outlined.TwoWheeler,
                            contentDescription = "Vehicles"
                        )
                    },
                    label = { Text("Vehicles") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BlueCobalt,
                        selectedTextColor = BlueCobalt,
                        indicatorColor = BlueCobalt.copy(alpha = 0.12f),
                        unselectedIconColor = SlateLight,
                        unselectedTextColor = SlateLight
                    )
                )

                NavigationBarItem(
                    selected = currentTab == MainTab.SETTINGS,
                    onClick = { currentTab = MainTab.SETTINGS },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == MainTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BlueCobalt,
                        selectedTextColor = BlueCobalt,
                        indicatorColor = BlueCobalt.copy(alpha = 0.12f),
                        unselectedIconColor = SlateLight,
                        unselectedTextColor = SlateLight
                    )
                )
            }
        }
    ) { innerPadding ->
        when (currentTab) {
            MainTab.DASHBOARD -> {
                DashboardScreen(
                    dayGroups = dayGroups,
                    metrics = monthlyMetrics,
                    settings = settings,
                    vehicles = vehicles,
                    selectedVehicleId = selectedVehicleId,
                    onSelectVehicle = { viewModel.setSelectedVehicle(it) },
                    liveTracking = liveTracking,
                    onStartDrive = {
                        if (permissionReadiness.hasFineLocation) {
                            viewModel.startDrive(context)
                            showActiveHUD = true
                        } else {
                            isManualDriveLaunchRequested = true
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    onStopDrive = {
                        viewModel.stopDrive(context)
                    },
                    onOpenLiveHUD = { showActiveHUD = true },
                    onOpenManualEntry = { showManualDialog = true },
                    onTripClick = { selectedDetailTrip = it },
                    onToggleCategory = { trip ->
                        val currentCat = trip.getParsedCategory()
                        val nextCat = when (currentCat) {
                            TripCategory.BUSINESS -> TripCategory.PERSONAL
                            TripCategory.PERSONAL -> TripCategory.MEDICAL
                            TripCategory.MEDICAL -> TripCategory.CHARITY
                            TripCategory.CHARITY -> TripCategory.BUSINESS
                        }
                        viewModel.updateTripCategory(trip.id, nextCat)
                    },
                    onDeleteTrip = { trip ->
                        viewModel.deleteTrip(trip.id)
                    },
                    onChangeTripVehicle = { trip, newVehicleId ->
                        viewModel.updateTripVehicle(trip.id, newVehicleId)
                    },
                    onResumeAutoTracking = { viewModel.resumeAutoTracking() },
                    onPauseAutoTracking = { viewModel.pauseAutoTracking(it) },
                    availableMonths = availableMonths,
                    onPreviousMonth = { viewModel.selectPreviousMonth() },
                    onNextMonth = { viewModel.selectNextMonth() },
                    onSelectMonthFilter = { viewModel.selectMonthFilter(it) },
                    onOpenExportDialog = { showExportDialog = true },
                    permissionReadiness = permissionReadiness,
                    onOpenPermissionsHub = {
                        showPermissionsEnforcementModal = true
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            MainTab.GARAGE -> {
                GarageScreen(
                    vehicles = vehicles,
                    settings = settings,
                    onAddVehicle = { name, type, fuel, eff, wear, isDef ->
                        viewModel.addVehicle(name, type, fuel, eff, wear, isDef)
                    },
                    onUpdateVehicle = { viewModel.updateVehicle(it) },
                    onSetDefault = { viewModel.setDefaultVehicle(it) },
                    onDeleteVehicle = { viewModel.deleteVehicle(it) },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            MainTab.SETTINGS -> {
                SettingsScreen(
                    settings = settings,
                    onUpdateSettings = { viewModel.updateGeneralSettings(it) },
                    onApplyCountry = { viewModel.applyCountry(it) },
                    onUpdateCity = { viewModel.updateCurrentCity(it) },
                    onUpdateFuelPrice = { viewModel.updateFuelPrice(it) },
                    onRefreshFuelPrice = { viewModel.refreshLiveFuelPrice() },
                    onUpdateFuelType = { viewModel.updateFuelType(it) },
                    onDetectLocation = { ctx, callback -> viewModel.detectLocationAndRefreshRate(ctx, callback) },
                    onOpenExportDialog = { showExportDialog = true },
                    onExportCsv = { showExportDialog = true },
                    isFetchingFuelPrice = isFetchingFuelRate,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        // Active Drive HUD Dialog: Automatically pops up when auto tracking is on, cannot be minimized until completed
        if (liveTracking.isTracking) {
            ActiveDriveDialog(
                trackingData = liveTracking,
                settings = settings,
                vehicles = vehicles,
                onDismiss = { /* Non-minimizable per requirement 2 */ },
                onStopDrive = { tolls, parking, endOdo, notes ->
                    viewModel.stopDrive(context, tolls, parking, endOdo, notes)
                }
            )
        }

        // Manual Trip Dialog
        if (showManualDialog) {
            ManualTripDialog(
                vehicles = vehicles,
                selectedVehicleId = selectedVehicleId,
                settings = settings,
                onDismiss = { showManualDialog = false },
                onSaveTrip = { vehId, cat, dist, startAddr, endAddr, tolls, parking, startOdo, endOdo, notes ->
                    val now = System.currentTimeMillis()
                    viewModel.addManualTrip(
                        vehicleId = vehId,
                        category = cat,
                        startTime = now - (dist * 2.5 * 60 * 1000).toLong(), // approx duration
                        endTime = now,
                        startAddress = startAddr,
                        endAddress = endAddr,
                        distanceKm = dist,
                        tolls = tolls,
                        parking = parking,
                        startOdometer = startOdo,
                        endOdometer = endOdo,
                        notes = notes
                    )
                    showManualDialog = false
                }
            )
        }

        // Trip Detail Dialog
        selectedDetailTrip?.let { trip ->
            val veh = vehicles.find { it.id == trip.vehicleId }
            TripDetailDialog(
                trip = trip,
                vehicle = veh,
                vehicles = vehicles,
                settings = settings,
                onChangeVehicle = { newVehicleId ->
                    viewModel.updateTripVehicle(trip.id, newVehicleId) { updatedTrip ->
                        selectedDetailTrip = updatedTrip
                    }
                },
                onDismiss = { selectedDetailTrip = null }
            )
        }

        // Export Report Dialog (PDF & CSV with custom destination folder picker)
        if (showExportDialog) {
            ExportReportDialog(
                allTrips = allCompletedTrips,
                filteredTrips = filteredTrips,
                currentFilterLabel = monthlyMetrics.periodLabel,
                vehicles = vehicles,
                settings = settings,
                onDismiss = { showExportDialog = false },
                onExportPdfToUri = { ctx, uri, trips, period ->
                    viewModel.exportPdfToUri(ctx, uri, trips, period)
                },
                onExportCsvToUri = { ctx, uri, trips ->
                    viewModel.exportCsvToUri(ctx, uri, trips)
                }
            )
        }
    }
}
