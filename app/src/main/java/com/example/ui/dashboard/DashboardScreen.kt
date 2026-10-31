package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppSettingsEntity
import com.example.data.model.TripEntity
import com.example.data.model.VehicleEntity
import com.example.service.LiveTrackingData
import com.example.ui.components.ActiveVehicleSelectorBar
import com.example.ui.components.DayTripsSection
import com.example.ui.components.MonthlySummaryHeader
import com.example.ui.components.StartTrackingBanner
import androidx.compose.material.icons.filled.Warning
import com.example.ui.settings.PauseAutoTrackingDialog
import com.example.ui.settings.PermissionReadinessState
import com.example.ui.theme.AmberPersonal
import com.example.ui.theme.BlueCobalt
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyDark
import com.example.ui.theme.RedExpense
import com.example.ui.theme.SlateBackground
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateLight
import com.example.ui.viewmodel.DayTripsGroup
import com.example.ui.viewmodel.MonthFilter
import com.example.ui.viewmodel.MonthlyMetrics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    dayGroups: List<DayTripsGroup>,
    metrics: MonthlyMetrics,
    settings: AppSettingsEntity,
    vehicles: List<VehicleEntity>,
    selectedVehicleId: String?,
    onSelectVehicle: (String) -> Unit,
    liveTracking: LiveTrackingData,
    onStartDrive: () -> Unit,
    onStopDrive: () -> Unit,
    onOpenLiveHUD: () -> Unit,
    onOpenManualEntry: () -> Unit,
    onTripClick: (TripEntity) -> Unit,
    onToggleCategory: (TripEntity) -> Unit,
    onDeleteTrip: (TripEntity) -> Unit,
    onChangeTripVehicle: (TripEntity, String) -> Unit = { _, _ -> },
    onResumeAutoTracking: () -> Unit,
    onPauseAutoTracking: (Long) -> Unit,
    availableMonths: List<MonthFilter>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectMonthFilter: (MonthFilter) -> Unit,
    onOpenExportDialog: () -> Unit,
    permissionReadiness: PermissionReadinessState = PermissionReadinessState(),
    onOpenPermissionsHub: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPauseDialog by remember { mutableStateOf(false) }
    val isPaused = settings.isAutoTrackingPaused()
    val isSystemTrulyEnabled = permissionReadiness.isSystemSettingShownAsEnabled(settings.autoTrackingEnabled)

    if (showPauseDialog) {
        PauseAutoTrackingDialog(
            onDismiss = { showPauseDialog = false },
            onConfirmPause = { untilMillis ->
                onPauseAutoTracking(untilMillis)
                showPauseDialog = false
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header: Title + Auto-Detection Status + Export & Manual Entry Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mileage Tracker",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NavyDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Auto-Detection Status Pill (Strictly gated: never shows Active/Enabled until all 4 permissions match)
                    val statusColor = when {
                        !permissionReadiness.areAllRequirementsMatched -> RedExpense
                        !settings.autoTrackingEnabled -> SlateLight
                        isPaused -> AmberPersonal
                        else -> EmeraldGreen
                    }
                    val statusText = when {
                        !permissionReadiness.areAllRequirementsMatched ->
                            "Auto-Detect: Not Enabled (${permissionReadiness.matchedRequirementsCount}/4 Permissions)"
                        !settings.autoTrackingEnabled -> "Auto-Detect: OFF"
                        isPaused -> "Auto-Detect: Paused"
                        else -> "Auto-Detect: Enabled (≥${settings.autoDriveDetectionSpeedKmh} km/h)"
                    }
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (!permissionReadiness.areAllRequirementsMatched) {
                                    onOpenPermissionsHub()
                                } else if (isSystemTrulyEnabled) {
                                    if (isPaused) onResumeAutoTracking() else showPauseDialog = true
                                } else {
                                    onOpenPermissionsHub()
                                }
                            }
                            .testTag("auto_tracking_status_chip"),
                        shape = RoundedCornerShape(8.dp),
                        color = statusColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    !permissionReadiness.areAllRequirementsMatched -> Icons.Default.Warning
                                    isPaused -> Icons.Default.PauseCircle
                                    else -> Icons.Default.Sensors
                                },
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = statusText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenExportDialog,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .testTag("dashboard_export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Export Report",
                            tint = BlueCobalt,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenManualEntry,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BlueCobalt.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("manual_trip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = BlueCobalt,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Manual",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlueCobalt
                        )
                    }
                }
            }
        }

        // Strict Permission & Battery Enforcement Banner when requirements are incomplete
        if (!permissionReadiness.areAllRequirementsMatched) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = RedExpense.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, RedExpense.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = RedExpense,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Permissions Not Enabled (${permissionReadiness.matchedRequirementsCount}/4 Matched)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NavyDark
                                )
                                Text(
                                    text = "Set Location to \"Allow all the time\" + Precise & Battery to \"Unrestricted\" in Permissions Hub.",
                                    fontSize = 11.sp,
                                    color = RedExpense,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onOpenPermissionsHub,
                            colors = ButtonDefaults.buttonColors(containerColor = RedExpense),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Fix Now", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // Paused Auto-Tracking Banner
        if (settings.autoTrackingEnabled && isPaused) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = AmberPersonal.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AmberPersonal.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PauseCircle,
                                contentDescription = null,
                                tint = AmberPersonal,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Auto-Drive Detection Paused",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                                val resumeLabel = if (settings.isPausedIndefinitely()) {
                                    "Paused until you resume manually"
                                } else {
                                    val until = settings.autoTrackingPausedUntil ?: 0L
                                    "Auto-resumes ${SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(until))}"
                                }
                                Text(
                                    text = resumeLabel,
                                    fontSize = 11.sp,
                                    color = SlateLight
                                )
                            }
                        }

                        Button(
                            onClick = onResumeAutoTracking,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPersonal),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("resume_auto_tracking_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // Active Vehicle & Live Fuel Rate Bar
        item {
            ActiveVehicleSelectorBar(
                vehicles = vehicles,
                selectedVehicleId = selectedVehicleId,
                onVehicleSelect = onSelectVehicle,
                currentCity = settings.currentCity,
                currentFuelPrice = settings.currentFuelPrice,
                selectedFuelType = settings.selectedFuelType,
                fuelRateUnit = settings.fuelRateUnit,
                currencySymbol = settings.currencySymbol,
                onRefreshRate = {},
                onAddVehicleClick = {}
            )
        }

        // Start / Stop Live GPS Drive Banner
        item {
            StartTrackingBanner(
                isTracking = liveTracking.isTracking,
                onStartDrive = onStartDrive,
                onStopDrive = onStopDrive,
                onOpenLiveHUD = onOpenLiveHUD
            )
        }

        // Monthly Summary Metrics Card
        item {
            MonthlySummaryHeader(
                metrics = metrics,
                settings = settings,
                availableMonths = availableMonths,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onSelectMonthFilter = onSelectMonthFilter
            )
        }

        // Day-wise Trip History Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Day-Wise Drive History",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyDark
                )
                if (dayGroups.isNotEmpty()) {
                    Text(
                        text = "${dayGroups.size} ${if (dayGroups.size == 1) "Day" else "Days"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateLight
                    )
                }
            }
        }

        if (dayGroups.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(BlueCobalt.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = BlueCobalt,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Drives Logged Yet",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Drives will auto-start when your vehicle crosses ${settings.autoDriveDetectionSpeedKmh} km/h for 2–3s (>5m displacement), or tap Start Auto GPS Drive above.",
                            fontSize = 12.sp,
                            color = SlateLight,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(dayGroups, key = { it.dateLabel }) { group ->
                DayTripsSection(
                    dayGroup = group,
                    vehicles = vehicles,
                    settings = settings,
                    onTripClick = onTripClick,
                    onToggleCategory = onToggleCategory,
                    onDeleteTrip = onDeleteTrip,
                    onChangeTripVehicle = onChangeTripVehicle
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}
