package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppSettingsEntity
import com.example.data.model.TripCategory
import com.example.data.model.TripEntity
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
import com.example.ui.theme.SlateLight
import com.example.ui.theme.SlateMedium
import com.example.ui.viewmodel.DayTripsGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DayTripsSection(
    dayGroup: DayTripsGroup,
    vehicles: List<VehicleEntity>,
    settings: AppSettingsEntity,
    onTripClick: (TripEntity) -> Unit,
    onToggleCategory: (TripEntity) -> Unit,
    onDeleteTrip: (TripEntity) -> Unit,
    onChangeTripVehicle: (TripEntity, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable(dayGroup.dateLabel) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Day Header Card (Light theme with category breakdown)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { isExpanded = !isExpanded },
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = dayGroup.dateLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${dayGroup.trips.size} ${if (dayGroup.trips.size == 1) "drive" else "drives"}",
                                fontSize = 11.sp,
                                color = SlateLight
                            )
                            Text(text = "•", fontSize = 11.sp, color = SlateLight)
                            Text(
                                text = "${String.format(Locale.US, "%.1f", dayGroup.totalDistanceKm)} ${settings.unitDistance}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueCobalt
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.End) {
                            // Day total personal cost
                            Text(
                                text = "Cost: ${settings.currencySymbol}${String.format(Locale.US, "%.2f", dayGroup.totalPersonalCost)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberPersonal
                            )
                            // Day total tax deduction
                            if (dayGroup.totalReimbursement > 0) {
                                Text(
                                    text = "Tax Ded: ${settings.currencySymbol}${String.format(Locale.US, "%.2f", dayGroup.totalReimbursement)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EmeraldGreen
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand/Collapse",
                            tint = SlateMedium,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Category-wise Breakdown (Requirement 5: distance & fuel cost by trip type)
                if (dayGroup.categoryBreakdown.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dayGroup.categoryBreakdown.forEach { catStat ->
                            val isBiz = catStat.categoryName.equals("BUSINESS", ignoreCase = true)
                            val isPersonal = catStat.categoryName.equals("PERSONAL", ignoreCase = true)
                            val badgeColor = when {
                                isBiz -> EmeraldGreen
                                isPersonal -> AmberPersonal
                                else -> BlueCobalt
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF8FAFC),
                                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.25f))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(badgeColor)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = catStat.categoryName.lowercase().replaceFirstChar { it.uppercase() },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${String.format(Locale.US, "%.1f", catStat.distanceKm)} ${settings.unitDistance}",
                                            fontSize = 10.sp,
                                            color = NavyDark,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Fuel: ${settings.currencySymbol}${String.format(Locale.US, "%.1f", catStat.fuelCost)}",
                                            fontSize = 10.sp,
                                            color = AmberPersonal,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Trips list within the day
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dayGroup.trips.forEach { trip ->
                    val vehicle = vehicles.find { it.id == trip.vehicleId }
                    TripCardItem(
                        trip = trip,
                        vehicle = vehicle,
                        vehicles = vehicles,
                        settings = settings,
                        onTripClick = { onTripClick(trip) },
                        onToggleCategory = { onToggleCategory(trip) },
                        onDeleteTrip = { onDeleteTrip(trip) },
                        onChangeVehicle = { newVehId -> onChangeTripVehicle(trip, newVehId) }
                    )
                }
            }
        }
    }
}

@Composable
fun TripCardItem(
    trip: TripEntity,
    vehicle: VehicleEntity?,
    settings: AppSettingsEntity,
    onTripClick: () -> Unit,
    onToggleCategory: () -> Unit,
    onDeleteTrip: () -> Unit,
    vehicles: List<VehicleEntity> = emptyList(),
    onChangeVehicle: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var vehicleMenuExpanded by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val startTimeStr = timeFormat.format(Date(trip.startTime))
    val endTimeStr = trip.endTime?.let { timeFormat.format(Date(it)) } ?: "Ongoing"

    val category = trip.getParsedCategory()
    val categoryColor = when (category) {
        TripCategory.BUSINESS -> EmeraldGreen
        TripCategory.PERSONAL -> AmberPersonal
        TripCategory.MEDICAL -> CyanCategory
        TripCategory.CHARITY -> PurpleCategory
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onTripClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Time range, Category badge, and more menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$startTimeStr – $endTimeStr",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                    if (trip.isManual) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = "Manual",
                                fontSize = 9.sp,
                                color = SlateLight,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Tap to quick-toggle category
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggleCategory() },
                        shape = RoundedCornerShape(10.dp),
                        color = categoryColor.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(categoryColor)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = category.displayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = categoryColor
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.SyncAlt,
                                contentDescription = "Toggle category",
                                tint = categoryColor,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }

                    // Context Menu
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Trip actions",
                                tint = SlateMedium,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View Full Details") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onTripClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Toggle Category") },
                                leadingIcon = { Icon(Icons.Default.SyncAlt, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onToggleCategory()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Trip", color = RedExpense) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = RedExpense) },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteTrip()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Route Visualization (Origin -> Destination)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreen)
                    )
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(16.dp)
                            .background(SlateBorder)
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RedExpense)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trip.startAddress,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = NavyDark,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = trip.endAddress ?: "In progress...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (trip.endAddress != null) NavyDark else SlateLight,
                        maxLines = 1
                    )
                }

                // Distance Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BlueCobalt.copy(alpha = 0.1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f", trip.distanceKm),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlueCobalt
                        )
                        Text(
                            text = settings.unitDistance,
                            fontSize = 10.sp,
                            color = BlueCobalt
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(8.dp))

            // Footer: Changeable Vehicle Dropdown and Financial breakdowns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Vehicle used (Interactive Dropdown when vehicles exist)
                Box {
                    val vehIcon = when (vehicle?.getParsedVehicleType()) {
                        VehicleType.MOTORCYCLE, VehicleType.SCOOTER -> Icons.Default.TwoWheeler
                        else -> Icons.Default.DirectionsCar
                    }
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = vehicles.isNotEmpty()) {
                                vehicleMenuExpanded = true
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (vehicles.size > 1) BlueCobalt.copy(alpha = 0.08f) else Color(0xFFF8FAFC),
                        border = BorderStroke(
                            1.dp,
                            if (vehicles.size > 1) BlueCobalt.copy(alpha = 0.25f) else SlateBorder.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = vehIcon,
                                contentDescription = null,
                                tint = if (vehicles.size > 1) BlueCobalt else SlateLight,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = vehicle?.name ?: "Select Vehicle",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (vehicles.size > 1) NavyDark else SlateLight,
                                maxLines = 1
                            )
                            if (vehicles.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Change trip vehicle",
                                    tint = if (vehicles.size > 1) BlueCobalt else SlateLight,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = vehicleMenuExpanded,
                        onDismissRequest = { vehicleMenuExpanded = false }
                    ) {
                        val unitClean = settings.fuelRateUnit.trim().removePrefix("/").trim().ifBlank { "L" }
                        vehicles.forEach { itemVeh ->
                            val isSelected = itemVeh.id == vehicle?.id
                            val itemIcon = when (itemVeh.getParsedVehicleType()) {
                                VehicleType.MOTORCYCLE, VehicleType.SCOOTER -> Icons.Default.TwoWheeler
                                else -> Icons.Default.DirectionsCar
                            }
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = itemVeh.name,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) BlueCobalt else NavyDark
                                        )
                                        Text(
                                            text = "${itemVeh.vehicleType} • ${itemVeh.fuelType} • ${itemVeh.efficiencyVal} ${settings.unitDistance}/$unitClean",
                                            fontSize = 10.sp,
                                            color = SlateLight
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = itemIcon,
                                        contentDescription = null,
                                        tint = if (isSelected) BlueCobalt else SlateMedium,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = BlueCobalt,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    vehicleMenuExpanded = false
                                    if (itemVeh.id != trip.vehicleId) {
                                        onChangeVehicle(itemVeh.id)
                                    }
                                }
                            )
                        }
                    }
                }

                // Cost & Fuel figures
                val eff = if ((vehicle?.efficiencyVal ?: 0.0) > 0.0) vehicle!!.efficiencyVal else 14.0
                val tripFuelConsumed = if (eff > 0.0) trip.distanceKm / eff else 0.0
                val unit = settings.fuelRateUnit.trim().removePrefix("/").trim().ifBlank { "L" }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Fuel consumed
                    Text(
                        text = "Fuel: ${String.format(Locale.US, "%.2f", tripFuelConsumed)} $unit",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BlueCobalt
                    )
                    // Personal trip cost
                    Text(
                        text = "Cost: ${settings.currencySymbol}${String.format(Locale.US, "%.2f", trip.totalPersonalCost)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberPersonal
                    )
                    // Business Tax deduction
                    if (trip.category == TripCategory.BUSINESS.name && trip.reimbursementAmount > 0) {
                        Text(
                            text = "Ded: ${settings.currencySymbol}${String.format(Locale.US, "%.2f", trip.reimbursementAmount)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                    }
                }
            }
        }
    }
}
