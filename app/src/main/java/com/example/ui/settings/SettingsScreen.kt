package com.example.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.fuel.LiveFuelPriceManager
import com.example.data.model.AppSettingsEntity
import com.example.data.model.CountryProfile
import com.example.data.model.CountryRegistry
import com.example.ui.theme.AmberPersonal
import com.example.ui.theme.BlueCobalt
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyDark
import com.example.ui.theme.RedExpense
import com.example.ui.theme.SlateBackground
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateLight
import com.example.ui.theme.SlateMedium
import android.widget.Toast
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.MyLocation
import com.example.data.model.FuelType
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettingsEntity,
    onUpdateSettings: (AppSettingsEntity) -> Unit,
    onApplyCountry: (CountryProfile) -> Unit,
    onUpdateCity: (String) -> Unit,
    onUpdateFuelPrice: (Double) -> Unit,
    onRefreshFuelPrice: () -> Unit,
    onUpdateFuelType: (String) -> Unit,
    onDetectLocation: (Context, (String) -> Unit) -> Unit,
    onOpenExportDialog: () -> Unit = {},
    onExportCsv: () -> Unit = onOpenExportDialog,
    isFetchingFuelPrice: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val (readiness, refreshReadiness) = rememberPermissionReadinessState(context)
    var showEnforcementDialog by remember { mutableStateOf(false) }

    // Background Location ("Allow all the time") Launcher
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        refreshReadiness()
        if (!isGranted) {
            Toast.makeText(
                context,
                "Please select 'Allow all the time' & 'Use precise location' in App Permissions",
                Toast.LENGTH_LONG
            ).show()
            openAppDetailsSystemSettings(context)
        }
    }

    // Foreground Precise + Coarse Location Launcher
    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        refreshReadiness()
        val fineGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bgGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!bgGranted) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        } else if (!fineGranted) {
            Toast.makeText(
                context,
                "Precise Location ('Use precise location' ON) and 'Allow all the time' are required",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val requestAllTimePreciseLocation = {
        val currentState = PermissionReadinessState.evaluate(context)
        if (!currentState.hasFineLocation) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    )
                )
            } else {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        } else if (!currentState.hasBackgroundLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            openAppDetailsSystemSettings(context)
        }
    }

    val activityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        refreshReadiness()
        if (!isGranted) {
            openAppDetailsSystemSettings(context)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        refreshReadiness()
        if (!isGranted) {
            openAppDetailsSystemSettings(context)
        }
    }

    if (showEnforcementDialog && !readiness.areAllRequirementsMatched) {
        MandatoryPermissionsEnforcementDialog(
            readiness = readiness,
            onRequestLocation = requestAllTimePreciseLocation,
            onRequestBattery = {
                launchUnrestrictedBatteryRequest(context)
                refreshReadiness()
            },
            onRequestActivity = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                } else {
                    openAppDetailsSystemSettings(context)
                }
            },
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openAppDetailsSystemSettings(context)
                }
            },
            onOpenPermissionsHub = { showEnforcementDialog = false },
            onDismiss = { showEnforcementDialog = false }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Application Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyDark
                )
                Text(
                    text = "Configure permissions, work hours, location norms & fuel pricing",
                    fontSize = 12.sp,
                    color = SlateLight
                )
            }
        }

        // Section 1: Permissions & Battery Optimization Hub
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (readiness.areAllRequirementsMatched) EmeraldGreen.copy(alpha = 0.12f)
                                        else RedExpense.copy(alpha = 0.12f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BatteryAlert,
                                    contentDescription = null,
                                    tint = if (readiness.areAllRequirementsMatched) EmeraldGreen else RedExpense,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Permissions & Battery Hub",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NavyDark
                                )
                                Text(
                                    text = "Mandatory setup for smooth 24/7 background GPS tracking",
                                    fontSize = 11.sp,
                                    color = SlateLight
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (readiness.areAllRequirementsMatched) EmeraldGreen.copy(alpha = 0.14f)
                            else RedExpense.copy(alpha = 0.14f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (readiness.areAllRequirementsMatched) EmeraldGreen.copy(alpha = 0.4f)
                                else RedExpense.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.testTag("hub_overall_status_badge")
                        ) {
                            Text(
                                text = if (readiness.areAllRequirementsMatched) {
                                    "Enabled (${readiness.matchedRequirementsCount}/${readiness.totalRequirementsCount})"
                                } else {
                                    "Not Enabled (${readiness.matchedRequirementsCount}/${readiness.totalRequirementsCount})"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (readiness.areAllRequirementsMatched) EmeraldGreen else RedExpense,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Overall System Requirement Enforcement Banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (readiness.areAllRequirementsMatched) EmeraldGreen.copy(alpha = 0.08f)
                        else RedExpense.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (readiness.areAllRequirementsMatched) EmeraldGreen.copy(alpha = 0.35f)
                            else RedExpense.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (readiness.areAllRequirementsMatched) Icons.Default.Check else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (readiness.areAllRequirementsMatched) EmeraldGreen else RedExpense,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (readiness.areAllRequirementsMatched) {
                                    "All requirements matched! Location is set to 'Allow all the time' with 'Use precise location' ON, and Battery is set to 'Unrestricted'."
                                } else {
                                    "Settings are NOT shown as Enabled until all 4 requirements below match. You must set Location to \"Allow all the time\" + \"Use precise location\" and Battery to \"Unrestricted\"."
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (readiness.areAllRequirementsMatched) EmeraldGreen else RedExpense,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. All-the-Time Precise Location ("Allow all the time" & "Use precise location")
                    DetailedPermissionGuideCard(
                        title = "All-the-Time Precise Location (\"Allow all the time\")",
                        diagnosticStatus = readiness.locationDiagnosticStatus(),
                        isEnabled = readiness.isAllTimePreciseLocationEnabled,
                        icon = Icons.Default.LocationOn,
                        whyRequiredText = "Required so the app can continuously detect when your vehicle crosses the speed threshold, track exact road kilometres with pinpoint accuracy, and record routes 24/7 even when the app is closed or your screen is locked.",
                        howToGrantSteps = listOf(
                            "Tap '${readiness.locationActionButtonText()}' below (or tap 'App Settings' → 'Permissions' → 'Location').",
                            "Select 'Allow all the time' (do NOT leave it on 'Allow only while using the app' or 'Ask every time').",
                            "Turn ON the 'Use precise location' toggle so exact GPS coordinates are tracked at all times."
                        ),
                        actionButtonText = readiness.locationActionButtonText(),
                        onActionClick = requestAllTimePreciseLocation,
                        onOpenSystemSettingsClick = { openAppDetailsSystemSettings(context) },
                        testTagPrefix = "perm_location_all_time"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Unrestricted Battery Usage
                    DetailedPermissionGuideCard(
                        title = "Unrestricted Battery Usage (\"Unrestricted\")",
                        diagnosticStatus = readiness.batteryDiagnosticStatus(),
                        isEnabled = readiness.isUnrestrictedBatteryEnabled,
                        icon = Icons.Default.BatterySaver,
                        whyRequiredText = "Prevents Android's Battery Saver, Adaptive Battery, and Doze mode from putting the background GPS tracking service to sleep or stopping it while you drive with the app closed.",
                        howToGrantSteps = listOf(
                            "Tap 'Set Unrestricted Battery' below and select 'Allow' on the system popup.",
                            "Or tap 'App Settings' → 'Battery' (or 'App battery usage').",
                            "Select 'Unrestricted' (do NOT leave it on 'Optimized' or 'Restricted')."
                        ),
                        actionButtonText = "Set Unrestricted Battery",
                        onActionClick = {
                            launchUnrestrictedBatteryRequest(context)
                            refreshReadiness()
                        },
                        onOpenSystemSettingsClick = { openAppDetailsSystemSettings(context) },
                        testTagPrefix = "perm_unrestricted_battery"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Physical Activity & Vehicle Motion Recognition
                    DetailedPermissionGuideCard(
                        title = "Physical Activity & Vehicle Motion Recognition",
                        diagnosticStatus = readiness.activityDiagnosticStatus(),
                        isEnabled = readiness.isActivityRecognitionEnabled,
                        icon = Icons.Default.DirectionsWalk,
                        whyRequiredText = "Allows the 3-axis motion sensor engine to distinguish real vehicle movement from stationary GPS drift and accurately control the idle auto-stop countdown.",
                        howToGrantSteps = listOf(
                            "Tap 'Allow Physical Activity' below and select 'Allow' on the system dialog.",
                            "If previously denied, tap 'App Settings' → 'Permissions' → 'Physical activity'.",
                            "Select 'Allow' so vehicle motion detection stays active in the background."
                        ),
                        actionButtonText = "Allow Physical Activity",
                        onActionClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                            } else {
                                openAppDetailsSystemSettings(context)
                            }
                        },
                        onOpenSystemSettingsClick = { openAppDetailsSystemSettings(context) },
                        testTagPrefix = "perm_activity_recognition"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4. Priority Lock-Screen & Foreground Notifications
                    DetailedPermissionGuideCard(
                        title = "Priority Lock-Screen & Foreground Notifications",
                        diagnosticStatus = readiness.notificationDiagnosticStatus(),
                        isEnabled = readiness.isNotificationsEnabled,
                        icon = Icons.Default.Notifications,
                        whyRequiredText = "Required by Android to keep the foreground GPS tracking service running when the app is closed, and to display live kilometres, fuel cost, and idle countdown on your lock screen.",
                        howToGrantSteps = listOf(
                            "Tap 'Allow Notifications' below and select 'Allow' on the system prompt.",
                            "If disabled in system settings, tap 'App Settings' → 'Notifications'.",
                            "Turn ON 'All notifications' and set Lock Screen visibility to 'Show all notification content'."
                        ),
                        actionButtonText = "Allow Notifications",
                        onActionClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openAppDetailsSystemSettings(context)
                            }
                        },
                        onOpenSystemSettingsClick = { openAppDetailsSystemSettings(context) },
                        testTagPrefix = "perm_notifications"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // App System Settings Shortcut
                    OutlinedButton(
                        onClick = { openAppDetailsSystemSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Full App System Settings", fontSize = 12.sp)
                    }
                }
            }
        }

        // Section 2: Country, Currency & National Norms
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(EmeraldGreen.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Country Norms & Currency",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                            Text(
                                text = "Set national norms with editable rates across the app",
                                fontSize = 11.sp,
                                color = SlateLight
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    var countryMenuOpen by remember { mutableStateOf(false) }

                    // Country Selection Dropdown
                    Text("Selected Country", fontSize = 12.sp, color = SlateLight, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { countryMenuOpen = true },
                            color = Color(0xFFF1F5F9),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${settings.countryName} (${settings.currencySymbol} ${settings.currencyCode})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = SlateMedium)
                            }
                        }

                        DropdownMenu(
                            expanded = countryMenuOpen,
                            onDismissRequest = { countryMenuOpen = false }
                        ) {
                            CountryRegistry.ALL_COUNTRIES.forEach { country ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${country.name} • ${country.currencySymbol} (${country.currencyCode})")
                                    },
                                    onClick = {
                                        onApplyCountry(country)
                                        countryMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Editable Currency Symbol & Tax Deduction Rate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var currSymbolText by remember(settings.currencySymbol) { mutableStateOf(settings.currencySymbol) }
                        OutlinedTextField(
                            value = currSymbolText,
                            onValueChange = {
                                currSymbolText = it
                                if (it.isNotEmpty()) onUpdateSettings(settings.copy(currencySymbol = it))
                            },
                            label = { Text("Currency Symbol") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        var taxRateText by remember(settings.standardTaxRatePerKm) {
                            mutableStateOf(settings.standardTaxRatePerKm.toString())
                        }
                        OutlinedTextField(
                            value = taxRateText,
                            onValueChange = {
                                taxRateText = it
                                val num = it.toDoubleOrNull()
                                if (num != null) onUpdateSettings(settings.copy(standardTaxRatePerKm = num))
                            },
                            label = { Text("Tax Write-off (${settings.currencySymbol}/${settings.unitDistance})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.5f),
                            singleLine = true
                        )
                    }
                }
            }
        }

        // Section 3: Live Location-Based Fuel Price Picker
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(AmberPersonal.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = AmberPersonal, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Live Fuel Price Detection",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                                Text(
                                    text = "Live reference site: www.goodreturns.in",
                                    fontSize = 11.sp,
                                    color = BlueCobalt,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        IconButton(
                            onClick = onRefreshFuelPrice,
                            enabled = !isFetchingFuelPrice
                        ) {
                            if (isFetchingFuelPrice) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = BlueCobalt
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Rate from GoodReturns", tint = BlueCobalt)
                            }
                        }
                    }

                    if (isFetchingFuelPrice) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = BlueCobalt.copy(alpha = 0.08f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = BlueCobalt
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Fetching today's live rate from www.goodreturns.in...",
                                    fontSize = 11.sp,
                                    color = BlueCobalt,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Fuel Type Selector Chips
                    Text(
                        text = "Fuel Type",
                        fontSize = 12.sp,
                        color = SlateLight,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val availableFuels = listOf(
                            "PETROL" to "⛽ Petrol",
                            "DIESEL" to "🛢️ Diesel",
                            "CNG" to "💨 CNG",
                            "EV" to "⚡ EV (Electric)",
                            "HYBRID" to "🔋 Hybrid",
                            "GASOLINE" to "⛽ Gasoline"
                        )
                        val currentNorm = LiveFuelPriceManager.normalizeFuelType(settings.selectedFuelType, settings.countryCode)

                        availableFuels.forEach { (typeKey, label) ->
                            val isSelected = currentNorm.equals(typeKey, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onUpdateFuelType(typeKey) },
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BlueCobalt,
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFFF1F5F9),
                                    labelColor = NavyDark
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. City Selector Dropdown (showing rate for the selected fuel type)
                    val cities = LiveFuelPriceManager.getCitiesForCountry(settings.countryCode)
                    var cityDropdownOpen by remember { mutableStateOf(false) }

                    Text(
                        text = "Selected City / Region",
                        fontSize = 12.sp,
                        color = SlateLight,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { cityDropdownOpen = true },
                            color = Color(0xFFF1F5F9),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = settings.currentCity,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark
                                    )
                                    Text(
                                        text = "Today's ${settings.selectedFuelType}: ${settings.currencySymbol}${String.format(Locale.US, "%.2f", settings.currentFuelPrice)} ${settings.fuelRateUnit}",
                                        fontSize = 11.sp,
                                        color = AmberPersonal,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = SlateMedium)
                            }
                        }

                        DropdownMenu(
                            expanded = cityDropdownOpen,
                            onDismissRequest = { cityDropdownOpen = false }
                        ) {
                            cities.forEach { city ->
                                val cityRate = LiveFuelPriceManager.getRateForCityAndFuelType(
                                    city,
                                    settings.selectedFuelType,
                                    settings.countryCode
                                )
                                val unit = LiveFuelPriceManager.getFuelRateUnit(settings.countryCode, settings.selectedFuelType)

                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(city.cityName, fontWeight = FontWeight.SemiBold)
                                                Text(city.stateOrRegion, fontSize = 10.sp, color = SlateLight)
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(
                                                "${settings.currencySymbol}${String.format(Locale.US, "%.2f", cityRate)} $unit",
                                                fontWeight = FontWeight.Bold,
                                                color = AmberPersonal
                                            )
                                        }
                                    },
                                    onClick = {
                                        onUpdateCity(city.cityName)
                                        cityDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. GPS Location Detection Button
                    var isDetectingLocation by remember { mutableStateOf(false) }
                    var locationStatusMessage by remember { mutableStateOf<String?>(null) }

                    OutlinedButton(
                        onClick = {
                            isDetectingLocation = true
                            locationStatusMessage = null
                            onDetectLocation(context) { msg ->
                                isDetectingLocation = false
                                locationStatusMessage = msg
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = BlueCobalt,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDetectingLocation) "Detecting Location..." else "Detect Location via GPS & Fetch Rate",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BlueCobalt
                        )
                    }

                    if (locationStatusMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = locationStatusMessage ?: "",
                            fontSize = 11.sp,
                            color = EmeraldGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. Rate Revision and Status Info Badge
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(EmeraldGreen)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reference: www.goodreturns.in", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BlueCobalt)
                                }
                                Text("06:00 AM IST Revision", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = EmeraldGreen)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val lastFetchedStr = if (settings.lastFuelRateFetchTime > 0) {
                                SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(settings.lastFuelRateFetchTime))
                            } else "Just now"
                            Text(
                                text = "Source: ${settings.lastFuelRateSource} • Updated: $lastFetchedStr",
                                fontSize = 10.sp,
                                color = SlateMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 5. Active Fuel Price Display & Manual Override
                    var fuelPriceText by remember(settings.currentFuelPrice) {
                        mutableStateOf(String.format(Locale.US, "%.2f", settings.currentFuelPrice))
                    }

                    OutlinedTextField(
                        value = fuelPriceText,
                        onValueChange = {
                            fuelPriceText = it
                            val price = it.toDoubleOrNull()
                            if (price != null && price > 0) {
                                onUpdateFuelPrice(price)
                            }
                        },
                        label = {
                            val cleanFuelUnit = settings.fuelRateUnit.trim().removePrefix("/").trim().ifBlank { "L" }
                            Text("Active Fuel Price (${settings.currencySymbol}/$cleanFuelUnit)")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        // Section 4: When to Track (Day-Wise 12-Hour Schedule) & Auto-Tracking
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 1. Day-Wise 12-Hour Schedule
                    DayWiseScheduleCard(
                        schedules = settings.getParsedSchedules(),
                        workHoursEnabled = settings.workHoursEnabled,
                        onToggleEnabled = { onUpdateSettings(settings.copy(workHoursEnabled = it)) },
                        onUpdateSchedule = { updatedSchedule ->
                            val currentList = settings.getParsedSchedules().toMutableList()
                            val idx = currentList.indexOfFirst { it.dayIndex == updatedSchedule.dayIndex }
                            if (idx >= 0) {
                                currentList[idx] = updatedSchedule
                            } else {
                                currentList.add(updatedSchedule)
                            }
                            val json = com.example.data.model.ScheduleSerializer.toJson(currentList)
                            onUpdateSettings(settings.copy(dayWiseScheduleJson = json))
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = SlateBorder)

                    // 2. Auto-Tracking Toggle (Strictly gated: never shown as Enabled until all 4 permissions match)
                    val isAutoDetectionTrulyEnabled = readiness.isSystemSettingShownAsEnabled(settings.autoTrackingEnabled)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Automatic Drive Detection",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isAutoDetectionTrulyEnabled) EmeraldGreen.copy(alpha = 0.14f)
                                    else RedExpense.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = if (isAutoDetectionTrulyEnabled) "Enabled" else "Not Enabled",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isAutoDetectionTrulyEnabled) EmeraldGreen else RedExpense,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (readiness.areAllRequirementsMatched) {
                                    "Auto-starts GPS tracking when sustained driving motion is detected"
                                } else {
                                    "Requires 'Allow all the time' + 'Use precise location' & 'Unrestricted' battery in Permissions Hub above before this setting is Enabled"
                                },
                                fontSize = 11.sp,
                                color = if (readiness.areAllRequirementsMatched) SlateLight else RedExpense
                            )
                        }
                        Switch(
                            checked = isAutoDetectionTrulyEnabled,
                            onCheckedChange = { wantEnabled ->
                                if (!readiness.areAllRequirementsMatched) {
                                    showEnforcementDialog = true
                                } else {
                                    onUpdateSettings(settings.copy(autoTrackingEnabled = wantEnabled))
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGreen)
                        )
                    }

                    if (!readiness.areAllRequirementsMatched) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { showEnforcementDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RedExpense)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Complete Permissions (${readiness.matchedRequirementsCount}/4) to Enable",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    if (isAutoDetectionTrulyEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))

                        // 3. Speed Threshold Dropdown (1 to 15 km/h)
                        var speedDropdownOpen by remember { mutableStateOf(false) }

                        Text(
                            text = "Motion Detection Speed Threshold",
                            fontSize = 12.sp,
                            color = SlateLight,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { speedDropdownOpen = true },
                                color = Color(0xFFF1F5F9),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val speedDesc = when (settings.autoDriveDetectionSpeedKmh) {
                                        1 -> "1 km/h (Ultra-Sensitive / Immediate)"
                                        in 2..4 -> "${settings.autoDriveDetectionSpeedKmh} km/h (Very Sensitive)"
                                        in 5..8 -> "${settings.autoDriveDetectionSpeedKmh} km/h (Bicycle / Light Motion)"
                                        10 -> "10 km/h (Recommended Default)"
                                        else -> "${settings.autoDriveDetectionSpeedKmh} km/h (High-Speed Vehicle)"
                                    }
                                    Text(
                                        text = speedDesc,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark
                                    )
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = SlateLight)
                                }
                            }
                            DropdownMenu(
                                expanded = speedDropdownOpen,
                                onDismissRequest = { speedDropdownOpen = false }
                            ) {
                                (1..15).forEach { speed ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "$speed km/h ${if (speed == 1) "(Min)" else if (speed == 10) "(Default)" else if (speed == 15) "(Max)" else ""}",
                                                fontWeight = if (settings.autoDriveDetectionSpeedKmh == speed) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            speedDropdownOpen = false
                                            onUpdateSettings(settings.copy(autoDriveDetectionSpeedKmh = speed))
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tracking will automatically start as soon as speed exceeds ${settings.autoDriveDetectionSpeedKmh} km/h, whether the app is open or closed.",
                            fontSize = 11.sp,
                            color = SlateLight
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // 4. Stationary Auto-Stop Timeout (Configurable: 5, 8, 10, 15 min)
                        var stopTimeoutDropdownOpen by remember { mutableStateOf(false) }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { stopTimeoutDropdownOpen = true },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder.copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(EmeraldGreen.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Schedule, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Auto-Stop Idle Timeout: ${settings.stopTimeoutMinutes} Minutes",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NavyDark
                                            )
                                            Text(
                                                text = "Trip auto-stops & saves if vehicle is stationary with no distance added for ${settings.stopTimeoutMinutes} min.",
                                                fontSize = 11.sp,
                                                color = SlateLight
                                            )
                                        }
                                    }
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = SlateLight)
                                }

                                DropdownMenu(
                                    expanded = stopTimeoutDropdownOpen,
                                    onDismissRequest = { stopTimeoutDropdownOpen = false }
                                ) {
                                    listOf(5, 8, 10, 15).forEach { mins ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "$mins Minutes ${if (mins == 8) "(Default)" else ""}",
                                                    fontWeight = if (settings.stopTimeoutMinutes == mins) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (settings.stopTimeoutMinutes == mins) BlueCobalt else NavyDark
                                                )
                                            },
                                            onClick = {
                                                stopTimeoutDropdownOpen = false
                                                onUpdateSettings(settings.copy(stopTimeoutMinutes = mins))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 5. Pause Auto-Tracking Option
                        var showPauseDialog by remember { mutableStateOf(false) }

                        if (settings.isAutoTrackingPaused()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = AmberPersonal.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AmberPersonal.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.PauseCircle, contentDescription = null, tint = AmberPersonal, modifier = Modifier.size(22.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Auto-Tracking Paused", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NavyDark)
                                            val dateStr = if (settings.isPausedIndefinitely()) {
                                                "Paused until you resume manually"
                                            } else {
                                                settings.autoTrackingPausedUntil?.let {
                                                    "Resumes on " + SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(it))
                                                } ?: ""
                                            }
                                            Text(dateStr, fontSize = 11.sp, color = SlateLight)
                                        }
                                    }
                                    Button(
                                        onClick = { onUpdateSettings(settings.copy(autoTrackingPausedUntil = null)) },
                                        colors = ButtonDefaults.buttonColors(containerColor = AmberPersonal),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Resume Now", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showPauseDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PauseCircle, contentDescription = null, tint = AmberPersonal, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Pause Auto-Tracking with Preset Resume...", fontSize = 12.sp, color = NavyDark)
                            }
                        }

                        if (showPauseDialog) {
                            PauseAutoTrackingDialog(
                                onDismiss = { showPauseDialog = false },
                                onConfirmPause = { resumeMillis ->
                                    onUpdateSettings(settings.copy(autoTrackingPausedUntil = resumeMillis))
                                    showPauseDialog = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Section 5: Reports Export & Open-Source Philosophy
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Data Export & Privacy",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                    Text(
                        text = "100% offline, local-first database. No subscription, no login.",
                        fontSize = 11.sp,
                        color = SlateLight
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Primary Export Action: PDF Report
                    Button(
                        onClick = onOpenExportDialog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_pdf_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueCobalt)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("EXPORT MILEAGE LOG TO PDF", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary Export Action: CSV Spreadsheet
                    OutlinedButton(
                        onClick = onOpenExportDialog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("export_csv_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, tint = SlateMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("EXPORT TO CSV (EXCEL / SHEETS)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = NavyDark)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

