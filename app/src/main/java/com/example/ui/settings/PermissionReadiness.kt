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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.AmberPersonal
import com.example.ui.theme.BlueCobalt
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyDark
import com.example.ui.theme.RedExpense
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateLight
import com.example.ui.theme.SlateMedium

/**
 * Evaluates and holds strict permission and battery readiness requirements.
 * None of the background tracking settings are shown as "Enabled" until all
 * requirements (Allow all the time + Precise location, Unrestricted battery,
 * Physical activity recognition, and Foreground notifications) are matched.
 */
data class PermissionReadinessState(
    val hasFineLocation: Boolean = false,
    val hasCoarseLocation: Boolean = false,
    val hasBackgroundLocation: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val hasActivityRecognition: Boolean = false,
    val hasNotifications: Boolean = false
) {
    /**
     * Strictly requires BOTH "Use precise location" (ACCESS_FINE_LOCATION)
     * AND "Allow all the time" (ACCESS_BACKGROUND_LOCATION).
     * Selecting "Allow only while using the app" or "Approximate location" returns false.
     */
    val isAllTimePreciseLocationEnabled: Boolean
        get() = hasFineLocation && hasBackgroundLocation

    val isUnrestrictedBatteryEnabled: Boolean
        get() = isIgnoringBatteryOptimizations

    val isActivityRecognitionEnabled: Boolean
        get() = hasActivityRecognition

    val isNotificationsEnabled: Boolean
        get() = hasNotifications

    val matchedRequirementsCount: Int
        get() = listOf(
            isAllTimePreciseLocationEnabled,
            isUnrestrictedBatteryEnabled,
            isActivityRecognitionEnabled,
            isNotificationsEnabled
        ).count { it }

    val totalRequirementsCount: Int = 4

    /**
     * True ONLY when all 4 requirements for smooth continuous background tracking are matched.
     */
    val areAllRequirementsMatched: Boolean
        get() = isAllTimePreciseLocationEnabled &&
            isUnrestrictedBatteryEnabled &&
            isActivityRecognitionEnabled &&
            isNotificationsEnabled

    /**
     * Forces any dependent system setting to show as NOT Enabled until all requirements are matched.
     */
    fun isSystemSettingShownAsEnabled(settingFlag: Boolean): Boolean {
        return settingFlag && areAllRequirementsMatched
    }

    fun locationDiagnosticStatus(): String {
        return when {
            isAllTimePreciseLocationEnabled ->
                "Enabled: 'Allow all the time' + 'Use precise location' verified"
            hasFineLocation && !hasBackgroundLocation ->
                "Not Enabled: Set to 'While using the app' only — Must select 'Allow all the time'"
            !hasFineLocation && hasCoarseLocation ->
                "Not Enabled: Approximate location only — Must turn ON 'Use precise location' & 'Allow all the time'"
            else ->
                "Not Enabled: Must grant 'Allow all the time' & 'Use precise location'"
        }
    }

    fun locationActionButtonText(): String {
        return when {
            !hasFineLocation && !hasCoarseLocation -> "Step 1: Enable Precise Location"
            !hasFineLocation && hasCoarseLocation -> "Turn ON Precise Location"
            hasFineLocation && !hasBackgroundLocation -> "Step 2: Set 'Allow All the Time'"
            else -> "Enabled"
        }
    }

    fun batteryDiagnosticStatus(): String {
        return if (isUnrestrictedBatteryEnabled) {
            "Enabled: Unrestricted background battery usage verified"
        } else {
            "Not Enabled: Battery optimization is active — Must select 'Unrestricted'"
        }
    }

    fun activityDiagnosticStatus(): String {
        return if (isActivityRecognitionEnabled) {
            "Enabled: Vehicle & physical motion recognition verified"
        } else {
            "Not Enabled: Physical activity permission required — Must select 'Allow'"
        }
    }

    fun notificationDiagnosticStatus(): String {
        return if (isNotificationsEnabled) {
            "Enabled: Priority lock-screen & foreground notifications verified"
        } else {
            "Not Enabled: Notification permission required — Must select 'Allow'"
        }
    }

    companion object {
        fun evaluate(context: Context): PermissionReadinessState {
            val fineGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val coarseGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val backgroundGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                fineGranted
            }

            val unrestrictedBattery = checkBatteryOptimizationStatus(context)

            val activityGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED &&
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }

            return PermissionReadinessState(
                hasFineLocation = fineGranted,
                hasCoarseLocation = coarseGranted,
                hasBackgroundLocation = backgroundGranted,
                isIgnoringBatteryOptimizations = unrestrictedBattery,
                hasActivityRecognition = activityGranted,
                hasNotifications = notificationsGranted
            )
        }
    }
}

fun checkBatteryOptimizationStatus(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else {
        true
    }
}

@SuppressLint("BatteryLife")
fun launchUnrestrictedBatteryRequest(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppDetailsSystemSettings(context)
        }
    }
}

fun openAppDetailsSystemSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

/**
 * Lifecycle-aware permission readiness state that automatically refreshes on ON_RESUME
 * whenever the user returns from Android System Settings or permission dialogs.
 */
@Composable
fun rememberPermissionReadinessState(context: Context): Pair<PermissionReadinessState, () -> Unit> {
    var readiness by remember { mutableStateOf(PermissionReadinessState.evaluate(context)) }
    val refresh = {
        readiness = PermissionReadinessState.evaluate(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                readiness = PermissionReadinessState.evaluate(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return readiness to refresh
}

/**
 * Reusable detailed card for each permission in the Permissions & Battery Hub.
 * Renders:
 *  1. Permission Name + Strict Status Badge ("Enabled" ONLY when 100% matched vs "Not Enabled")
 *  2. Current Live Diagnostic Sub-Status
 *  3. "WHY THIS IS REQUIRED" explanation box directly underneath the permission name
 *  4. "HOW TO GIVE THIS PERMISSION" step-by-step guide directly underneath
 *  5. Action button to grant/configure the setting
 */
@Composable
fun DetailedPermissionGuideCard(
    title: String,
    diagnosticStatus: String,
    isEnabled: Boolean,
    icon: ImageVector,
    whyRequiredText: String,
    howToGrantSteps: List<String>,
    actionButtonText: String,
    onActionClick: () -> Unit,
    onOpenSystemSettingsClick: (() -> Unit)? = null,
    testTagPrefix: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    val borderColor = if (isEnabled) EmeraldGreen.copy(alpha = 0.45f) else AmberPersonal.copy(alpha = 0.65f)
    val cardBgColor = if (isEnabled) EmeraldGreen.copy(alpha = 0.04f) else Color(0xFFFFFBEB)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { isExpanded = !isExpanded }
            .testTag("${testTagPrefix}_card"),
        shape = RoundedCornerShape(14.dp),
        color = cardBgColor,
        border = BorderStroke(1.2.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row (Always Visible in Collapsed State): Icon + Title + Status Badge + Expand Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isEnabled) EmeraldGreen.copy(alpha = 0.15f)
                                else RedExpense.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isEnabled) EmeraldGreen else RedExpense,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NavyDark
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = diagnosticStatus,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isEnabled) EmeraldGreen else RedExpense
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Strict Status Pill: Never shows "Enabled" until all requirements for this setting match
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isEnabled) EmeraldGreen.copy(alpha = 0.15f) else RedExpense.copy(alpha = 0.14f),
                        border = BorderStroke(
                            1.dp,
                            if (isEnabled) EmeraldGreen.copy(alpha = 0.4f) else RedExpense.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.testTag("${testTagPrefix}_status_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isEnabled) Icons.Default.Check else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isEnabled) EmeraldGreen else RedExpense,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEnabled) "Enabled" else "Not Enabled",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isEnabled) EmeraldGreen else RedExpense
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Expand / Collapse Arrow Indicator
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.85f))
                            .testTag("${testTagPrefix}_expand_arrow"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse permission guide" else "Expand permission guide",
                            tint = NavyDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Expanded Details: "WHY THIS IS REQUIRED", "HOW TO GIVE THIS PERMISSION", and Action Buttons
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Underneath Permission Name: 1. WHY THIS IS REQUIRED
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.7f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "WHY THIS IS REQUIRED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BlueCobalt,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = whyRequiredText,
                                fontSize = 11.sp,
                                color = NavyDark,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Underneath Permission Name: 2. HOW TO GIVE THIS PERMISSION (STEP-BY-STEP GUIDE)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.8f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "HOW TO GIVE THIS PERMISSION (STEP-BY-STEP GUIDE)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SlateMedium,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            howToGrantSteps.forEachIndexed { index, stepText ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(BlueCobalt.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BlueCobalt
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stepText,
                                        fontSize = 11.sp,
                                        color = NavyDark,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    if (!isEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onActionClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("${testTagPrefix}_action_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BlueCobalt)
                            ) {
                                Text(
                                    text = actionButtonText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            if (onOpenSystemSettingsClick != null) {
                                OutlinedButton(
                                    onClick = onOpenSystemSettingsClick,
                                    modifier = Modifier.height(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, BlueCobalt.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "App Settings",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BlueCobalt
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Mandatory modal dialog shown when required permissions ("Allow all the time" + Precise location,
 * Unrestricted battery usage, Physical Activity, Notifications) are not yet matched.
 * Forces the user to configure the missing settings before the system shows as Enabled.
 */
@Composable
fun MandatoryPermissionsEnforcementDialog(
    readiness: PermissionReadinessState,
    onRequestLocation: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestActivity: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenPermissionsHub: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(RedExpense.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = RedExpense,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Action Required: Setup Permissions",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NavyDark
                        )
                        Text(
                            text = "System Status: Not Enabled (${readiness.matchedRequirementsCount}/${readiness.totalRequirementsCount} Matched)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = RedExpense
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "For smooth 24/7 background auto-tracking when the app is closed, Android requires Location set to \"Allow all the time\" with \"Use precise location\" ON, and Battery set to \"Unrestricted\". Settings will remain Not Enabled until all requirements below are matched:",
                    fontSize = 12.sp,
                    color = SlateMedium,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 1. All-the-Time Precise Location
                EnforcementQuickRow(
                    title = "1. Location: \"Allow all the time\" & Precise",
                    statusText = readiness.locationDiagnosticStatus(),
                    howToText = "Permissions → Location → Select 'Allow all the time' & turn ON 'Use precise location'",
                    isEnabled = readiness.isAllTimePreciseLocationEnabled,
                    buttonText = readiness.locationActionButtonText(),
                    onClick = onRequestLocation
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Unrestricted Battery Usage
                EnforcementQuickRow(
                    title = "2. Battery: \"Unrestricted\" Usage",
                    statusText = readiness.batteryDiagnosticStatus(),
                    howToText = "App Info → Battery → Select 'Unrestricted' (no background restrictions)",
                    isEnabled = readiness.isUnrestrictedBatteryEnabled,
                    buttonText = "Set Unrestricted",
                    onClick = onRequestBattery
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Physical Activity Recognition
                EnforcementQuickRow(
                    title = "3. Physical Activity Recognition",
                    statusText = readiness.activityDiagnosticStatus(),
                    howToText = "Permissions → Physical activity → Select 'Allow'",
                    isEnabled = readiness.isActivityRecognitionEnabled,
                    buttonText = "Allow Activity",
                    onClick = onRequestActivity
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 4. Foreground & Lock-Screen Notifications
                EnforcementQuickRow(
                    title = "4. Priority Lock-Screen Notifications",
                    statusText = readiness.notificationDiagnosticStatus(),
                    howToText = "Permissions → Notifications → Turn ON 'Show notifications'",
                    isEnabled = readiness.isNotificationsEnabled,
                    buttonText = "Allow Notifications",
                    onClick = onRequestNotifications
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onOpenPermissionsHub,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("open_permissions_hub_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueCobalt)
                ) {
                    Text(
                        text = "Open Permissions & Battery Hub Guide",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Text(
                        text = "I Will Configure in Settings",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun EnforcementQuickRow(
    title: String,
    statusText: String,
    howToText: String,
    isEnabled: Boolean,
    buttonText: String,
    onClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(12.dp),
        color = if (isEnabled) EmeraldGreen.copy(alpha = 0.06f) else Color(0xFFFFFBEB),
        border = BorderStroke(
            1.dp,
            if (isEnabled) EmeraldGreen.copy(alpha = 0.4f) else AmberPersonal.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyDark,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isEnabled) "Enabled" else "Not Enabled",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isEnabled) EmeraldGreen else RedExpense
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = NavyDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isEnabled) EmeraldGreen else RedExpense
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Guide: $howToText",
                        fontSize = 10.sp,
                        color = SlateLight
                    )
                    if (!isEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BlueCobalt)
                        ) {
                            Text(buttonText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
