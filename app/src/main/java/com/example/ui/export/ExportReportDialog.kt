package com.example.ui.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppSettingsEntity
import com.example.data.model.TripEntity
import com.example.data.model.VehicleEntity
import com.example.ui.theme.AmberPersonal
import com.example.ui.theme.BlueCobalt
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateLight
import com.example.ui.theme.SlateMedium
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ExportPeriod(val label: String) {
    CURRENT_MONTH("Current Month"),
    DASHBOARD_PERIOD("Selected Period"),
    ALL_TIME("All Time (All Trips)")
}

enum class ExportFormat(val extension: String, val mimeType: String, val title: String) {
    PDF("pdf", "application/pdf", "PDF Document (Print & Audit)"),
    CSV("csv", "text/csv", "CSV Spreadsheet (Excel / Sheets)")
}

@Composable
fun ExportReportDialog(
    allTrips: List<TripEntity>,
    filteredTrips: List<TripEntity>,
    currentFilterLabel: String,
    vehicles: List<VehicleEntity>,
    settings: AppSettingsEntity,
    onDismiss: () -> Unit,
    onExportPdfToUri: (Context, Uri, List<TripEntity>, String) -> Boolean,
    onExportCsvToUri: (Context, Uri, List<TripEntity>) -> Boolean
) {
    val context = LocalContext.current
    var selectedPeriod by remember { mutableStateOf(ExportPeriod.CURRENT_MONTH) }
    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }

    // Calculate current month trips
    val currentMonthTrips = remember(allTrips) {
        val nowCal = Calendar.getInstance()
        val currentYear = nowCal.get(Calendar.YEAR)
        val currentMonth = nowCal.get(Calendar.MONTH)
        allTrips.filter {
            val tripCal = Calendar.getInstance().apply { timeInMillis = it.startTime }
            tripCal.get(Calendar.YEAR) == currentYear && tripCal.get(Calendar.MONTH) == currentMonth
        }
    }

    // Resolve active trip list based on selected period
    val (targetTrips, periodDisplayLabel) = when (selectedPeriod) {
        ExportPeriod.CURRENT_MONTH -> {
            val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
            currentMonthTrips to monthName
        }
        ExportPeriod.DASHBOARD_PERIOD -> {
            filteredTrips to currentFilterLabel
        }
        ExportPeriod.ALL_TIME -> {
            allTrips to "All Time (Complete History)"
        }
    }

    val totalDistance = targetTrips.sumOf { it.distanceKm }

    // Launcher for system file picker (user selects custom folder & file name)
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(selectedFormat.mimeType)
    ) { uri: Uri? ->
        if (uri != null) {
            val success = if (selectedFormat == ExportFormat.PDF) {
                onExportPdfToUri(context, uri, targetTrips, periodDisplayLabel)
            } else {
                onExportCsvToUri(context, uri, targetTrips)
            }

            if (success) {
                Toast.makeText(
                    context,
                    "Saved ${selectedFormat.name} report successfully! (${targetTrips.size} trips)",
                    Toast.LENGTH_LONG
                ).show()

                // Offer immediate open intent
                try {
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, selectedFormat.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(viewIntent, "Open Exported Report"))
                } catch (_: Exception) {
                    // Ignore if no viewer app installed
                }
                onDismiss()
            } else {
                Toast.makeText(context, "Failed to save file. Please check folder permissions.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(BlueCobalt.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = BlueCobalt,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Export Mileage Log",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                            Text(
                                text = "Audit-ready trips & expense report",
                                fontSize = 11.sp,
                                color = SlateLight
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateMedium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: Choose Time Period
                Text(
                    text = "SELECT TIME PERIOD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateLight,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PeriodOptionRow(
                        title = "Current Month (${SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date())})",
                        subtitle = "${currentMonthTrips.size} trips • ${String.format(Locale.US, "%.1f", currentMonthTrips.sumOf { it.distanceKm })} ${settings.unitDistance}",
                        isSelected = selectedPeriod == ExportPeriod.CURRENT_MONTH,
                        onClick = { selectedPeriod = ExportPeriod.CURRENT_MONTH }
                    )

                    PeriodOptionRow(
                        title = "Dashboard Period ($currentFilterLabel)",
                        subtitle = "${filteredTrips.size} trips • ${String.format(Locale.US, "%.1f", filteredTrips.sumOf { it.distanceKm })} ${settings.unitDistance}",
                        isSelected = selectedPeriod == ExportPeriod.DASHBOARD_PERIOD,
                        onClick = { selectedPeriod = ExportPeriod.DASHBOARD_PERIOD }
                    )

                    PeriodOptionRow(
                        title = "All Time (Complete History)",
                        subtitle = "${allTrips.size} total trips logged",
                        isSelected = selectedPeriod == ExportPeriod.ALL_TIME,
                        onClick = { selectedPeriod = ExportPeriod.ALL_TIME }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: Choose Format
                Text(
                    text = "CHOOSE FILE FORMAT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateLight,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FormatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.PictureAsPdf,
                        title = "PDF Report",
                        badge = "Recommended",
                        subtitle = "Ready to print or audit",
                        isSelected = selectedFormat == ExportFormat.PDF,
                        accentColor = Color(0xFFDC2626),
                        onClick = { selectedFormat = ExportFormat.PDF }
                    )

                    FormatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.TableChart,
                        title = "CSV File",
                        badge = "Excel / Sheets",
                        subtitle = "Raw data spreadsheet",
                        isSelected = selectedFormat == ExportFormat.CSV,
                        accentColor = EmeraldGreen,
                        onClick = { selectedFormat = ExportFormat.CSV }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Summary Pill Box
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "TRIPS IN REPORT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateLight
                            )
                            Text(
                                text = "${targetTrips.size} Drives • ${String.format(Locale.US, "%.1f", totalDistance)} ${settings.unitDistance}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = BlueCobalt, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Custom Folder",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BlueCobalt
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Text("CANCEL", color = SlateMedium, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val cleanPeriodName = periodDisplayLabel.replace(" ", "_").replace("(", "").replace(")", "").replace("/", "_")
                            val defaultFilename = if (selectedFormat == ExportFormat.PDF) {
                                "Mileage_Report_${cleanPeriodName}.pdf"
                            } else {
                                "Mileage_Log_${cleanPeriodName}.csv"
                            }
                            createDocLauncher.launch(defaultFilename)
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueCobalt)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SAVE ${selectedFormat.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) BlueCobalt.copy(alpha = 0.06f) else Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) BlueCobalt else Color(0xFFE2E8F0)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) BlueCobalt else NavyDark
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = SlateLight
                )
            }

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) BlueCobalt else Color.Transparent)
                    .border(1.5.dp, if (isSelected) BlueCobalt else SlateBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    badge: String,
    subtitle: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.07f) else Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) accentColor else Color(0xFFE2E8F0)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) accentColor else Color(0xFFE2E8F0)
                ) {
                    Text(
                        text = badge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else SlateMedium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) NavyDark else SlateMedium
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = SlateLight
            )
        }
    }
}
