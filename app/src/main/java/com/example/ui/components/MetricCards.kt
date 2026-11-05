package com.example.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppSettingsEntity
import com.example.data.model.VehicleEntity
import com.example.data.model.VehicleType
import com.example.ui.theme.AmberPersonal
import com.example.ui.theme.BlueCobalt
import com.example.ui.theme.CyanCategory
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyDark
import com.example.ui.theme.PurpleCategory
import com.example.ui.theme.RedExpense
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateDark
import com.example.ui.theme.SlateLight
import com.example.ui.theme.SlateMedium
import com.example.ui.viewmodel.MonthlyMetrics
import com.example.ui.viewmodel.CategoryStat
import com.example.ui.viewmodel.MonthFilter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun MonthlySummaryHeader(
    metrics: MonthlyMetrics,
    settings: AppSettingsEntity,
    availableMonths: List<MonthFilter> = emptyList(),
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onSelectMonthFilter: (MonthFilter) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMonthDialog by remember { mutableStateOf(false) }

    if (showMonthDialog) {
        MonthSelectionDialog(
            availableMonths = availableMonths,
            currentFilter = metrics.currentFilter,
            onSelectMonthFilter = onSelectMonthFilter,
            onDismiss = { showMonthDialog = false }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row with Month Navigation: [◀] [Month Year ▾] [▶] & Trips Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onPreviousMonth,
                        modifier = Modifier.size(32.dp),
                        enabled = metrics.hasPreviousPeriod
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Month",
                            tint = if (metrics.hasPreviousPeriod) NavyDark else SlateBorder,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showMonthDialog = true },
                        color = Color(0xFFF1F5F9),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = BlueCobalt,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = metrics.periodLabel.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Month",
                                tint = SlateLight,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onNextMonth,
                        modifier = Modifier.size(32.dp),
                        enabled = metrics.hasNextPeriod
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Month",
                            tint = if (metrics.hasNextPeriod) NavyDark else SlateBorder,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                ) {
                    Text(
                        text = "${metrics.tripCount} Trips",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlueCobalt,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val fuelPrice = if (settings.currentFuelPrice > 0.0) settings.currentFuelPrice else 104.21
            val displayFuelCost = if (metrics.totalFuelCost > 0.0) metrics.totalFuelCost else metrics.totalPersonalCost
            val totalFuelConsumed = metrics.totalFuelConsumed
            val businessStat = metrics.tagBreakdown.find { it.categoryName.uppercase() == "BUSINESS" }
            val businessFuelConsumed = if (fuelPrice > 0.0) (businessStat?.fuelCost ?: 0.0) / fuelPrice else 0.0
            val fuelUnit = settings.fuelRateUnit.trim().removePrefix("/").trim().ifBlank { "L" }

            // Row 1: Tax Deductions & Fuel Cost
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Business Deductions Card
                SummaryMetricItem(
                    label = "Tax Deductions",
                    value = "${settings.currencySymbol}${String.format(Locale.US, "%.0f", metrics.totalBusinessDeduction)}",
                    accentColor = EmeraldGreen,
                    icon = Icons.Default.ReceiptLong,
                    subLabel = "Fuel: ${String.format(Locale.US, "%.1f", businessFuelConsumed)} $fuelUnit",
                    modifier = Modifier.weight(1f)
                )

                // Fuel Cost Card
                SummaryMetricItem(
                    label = "Fuel Cost",
                    value = "${settings.currencySymbol}${String.format(Locale.US, "%.0f", displayFuelCost)}",
                    accentColor = AmberPersonal,
                    icon = Icons.Default.LocalGasStation,
                    subLabel = "Fuel: ${String.format(Locale.US, "%.1f", totalFuelConsumed)} $fuelUnit",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Total Distance & Total Fuel Consumed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Total Distance Card
                SummaryMetricItem(
                    label = "Total Distance",
                    value = "${String.format(Locale.US, "%.1f", metrics.totalDistanceKm)} ${settings.unitDistance}",
                    accentColor = BlueCobalt,
                    icon = Icons.Default.DirectionsCar,
                    subLabel = "Consumed: ${String.format(Locale.US, "%.1f", totalFuelConsumed)} $fuelUnit",
                    modifier = Modifier.weight(1f)
                )

                // Fuel Consumption Live Metric Card
                SummaryMetricItem(
                    label = "Fuel Consumed",
                    value = "${String.format(Locale.US, "%.1f", totalFuelConsumed)} $fuelUnit",
                    accentColor = Color(0xFFD97706),
                    icon = Icons.Default.LocalGasStation,
                    subLabel = if (metrics.avgEfficiency != null) {
                        "Avg: ${String.format(Locale.US, "%.1f", metrics.avgEfficiency)} ${settings.unitDistance}/$fuelUnit"
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Tag-wise Fuel Cost & Total KM Section
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Label,
                        contentDescription = null,
                        tint = SlateLight,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "TAG-WISE FUEL COST & DISTANCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateDark,
                        letterSpacing = 0.5.sp
                    )
                }
                if (metrics.tagBreakdown.isNotEmpty()) {
                    Text(
                        text = "${metrics.tagBreakdown.size} Tags",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (metrics.tagBreakdown.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No categorized drives recorded yet this month",
                        fontSize = 11.sp,
                        color = SlateLight,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    metrics.tagBreakdown.forEach { stat ->
                        val (catColor, catLabel) = when (stat.categoryName.uppercase(Locale.ROOT)) {
                            "BUSINESS" -> EmeraldGreen to "Business"
                            "PERSONAL" -> AmberPersonal to "Personal"
                            "MEDICAL" -> CyanCategory to "Medical"
                            "CHARITY" -> PurpleCategory to "Charity"
                            else -> BlueCobalt to (stat.categoryName.replaceFirstChar { it.uppercase() })
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(catColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = catLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• ${stat.tripCount} ${if (stat.tripCount == 1) "trip" else "trips"}",
                                        fontSize = 11.sp,
                                        color = SlateLight
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Total Distance
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", stat.distanceKm)} ${settings.unitDistance}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BlueCobalt
                                    )

                                    // Fuel Cost & Consumption badges
                                    val fuelPrice = if (settings.currentFuelPrice > 0.0) settings.currentFuelPrice else 104.21
                                    val tagFuelConsumed = stat.fuelCost / fuelPrice
                                    val fuelUnit = "L"

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = AmberPersonal.copy(alpha = 0.12f),
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, AmberPersonal.copy(alpha = 0.3f))
                                        ) {
                                            Text(
                                                text = "Fuel: ${settings.currencySymbol}${String.format(Locale.US, "%.0f", stat.fuelCost)}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AmberPersonal,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = BlueCobalt.copy(alpha = 0.12f),
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, BlueCobalt.copy(alpha = 0.3f))
                                        ) {
                                            Text(
                                                text = "${String.format(Locale.US, "%.1f", tagFuelConsumed)} $fuelUnit",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BlueCobalt,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
    }
}

@Composable
fun MonthSelectionDialog(
    availableMonths: List<MonthFilter>,
    currentFilter: MonthFilter,
    onSelectMonthFilter: (MonthFilter) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, SlateBorder, RoundedCornerShape(20.dp)),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with Calendar Icon and Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(BlueCobalt.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = BlueCobalt,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SELECT TIME PERIOD",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "View summary & fuel stats",
                                fontSize = 11.sp,
                                color = SlateLight
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SlateLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable List of Months
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableMonths.forEach { filter ->
                        val label = when (filter) {
                            is MonthFilter.AllTime -> "All Time (Cumulative)"
                            is MonthFilter.Month -> {
                                val mCal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, filter.year)
                                    set(Calendar.MONTH, filter.month)
                                    set(Calendar.DAY_OF_MONTH, 1)
                                }
                                SimpleDateFormat("MMMM yyyy", Locale.US).format(mCal.time)
                            }
                        }
                        val isSelected = (filter is MonthFilter.AllTime && currentFilter is MonthFilter.AllTime) ||
                                (filter is MonthFilter.Month && currentFilter is MonthFilter.Month &&
                                        filter.year == currentFilter.year && filter.month == currentFilter.month)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onDismiss()
                                    onSelectMonthFilter(filter)
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) BlueCobalt.copy(alpha = 0.1f) else Color(0xFFF8FAFC),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) BlueCobalt.copy(alpha = 0.5f) else SlateBorder.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) BlueCobalt else NavyDark
                                )

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(BlueCobalt),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
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
}

@Composable
private fun SummaryMetricItem(
    label: String,
    value: String,
    accentColor: Color,
    icon: ImageVector,
    subLabel: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = SlateLight,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = NavyDark,
                maxLines = 1
            )
            if (subLabel != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = SlateLight,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ActiveVehicleSelectorBar(
    vehicles: List<VehicleEntity>,
    selectedVehicleId: String?,
    onVehicleSelect: (String) -> Unit,
    currentCity: String,
    currentFuelPrice: Double,
    selectedFuelType: String,
    fuelRateUnit: String,
    currencySymbol: String,
    onRefreshRate: () -> Unit,
    onAddVehicleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentVehicle = vehicles.find { it.id == selectedVehicleId }
        ?: vehicles.find { it.isDefault }
        ?: vehicles.firstOrNull()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vehicle Dropdown Selector or Add Vehicle prompt
        if (vehicles.isEmpty()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onAddVehicleClick() }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(BlueCobalt.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = BlueCobalt,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "No Vehicle Added",
                        fontSize = 10.sp,
                        color = SlateLight
                    )
                    Text(
                        text = "+ Add Vehicle",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlueCobalt
                    )
                }
            }
        } else {
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expanded = true }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (currentVehicle?.getParsedVehicleType()) {
                        VehicleType.MOTORCYCLE, VehicleType.SCOOTER -> Icons.Default.TwoWheeler
                        else -> Icons.Default.DirectionsCar
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(NavyDark.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = BlueCobalt,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "${currentVehicle?.vehicleType} • ${currentVehicle?.fuelType}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = SlateLight
                        )
                        Text(
                            text = currentVehicle?.name ?: "Select Vehicle",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Change vehicle",
                        tint = SlateMedium,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    vehicles.forEach { vehicle ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = vehicle.name,
                                        fontWeight = if (vehicle.id == currentVehicle?.id) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = "${vehicle.vehicleType} • ${vehicle.fuelType} • ${vehicle.efficiencyVal} ${vehicle.getParsedVehicleType().efficiencyUnit}",
                                        fontSize = 11.sp,
                                        color = SlateLight
                                    )
                                }
                            },
                            onClick = {
                                onVehicleSelect(vehicle.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // Live fuel price badge matching active fuel type
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFF1F5F9),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
        ) {
            Row(
                modifier = Modifier
                    .clickable { onRefreshRate() }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocalGasStation,
                    contentDescription = null,
                    tint = AmberPersonal,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Column {
                    Text(
                        text = "$currentCity • $selectedFuelType",
                        fontSize = 9.sp,
                        color = SlateLight,
                        fontWeight = FontWeight.SemiBold
                    )
                    val cleanUnit = if (fuelRateUnit.startsWith("/")) fuelRateUnit else "/$fuelRateUnit"
                    Text(
                        text = "$currencySymbol${String.format(Locale.US, "%.2f", currentFuelPrice)}$cleanUnit",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh rate",
                    tint = SlateMedium,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
fun StartTrackingBanner(
    isTracking: Boolean,
    onStartDrive: () -> Unit,
    onStopDrive: () -> Unit,
    onOpenLiveHUD: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isTracking) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clickable { onOpenLiveHUD() },
            shape = RoundedCornerShape(16.dp),
            color = EmeraldGreen,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "LIVE DRIVE IN PROGRESS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Tap to view telemetry & add tolls",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
                Button(
                    onClick = onStopDrive,
                    colors = ButtonDefaults.buttonColors(containerColor = RedExpense),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("stop_drive_banner_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        Button(
            onClick = onStartDrive,
            modifier = modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("start_drive_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BlueCobalt),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "START AUTO GPS DRIVE",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
