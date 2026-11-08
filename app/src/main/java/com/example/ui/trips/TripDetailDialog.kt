package com.example.ui.trips

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppSettingsEntity
import com.example.data.model.TripCategory
import com.example.data.model.TripEntity
import com.example.data.model.VehicleEntity
import com.example.data.model.VehicleType
import com.example.ui.theme.AmberPersonal
import com.example.ui.theme.BlueCobalt
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyDark
import com.example.ui.theme.RedExpense
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateLight
import com.example.ui.theme.SlateMedium
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripDetailDialog(
    trip: TripEntity,
    vehicle: VehicleEntity?,
    settings: AppSettingsEntity,
    vehicles: List<VehicleEntity> = emptyList(),
    gpsPointsCount: Int = 0,
    onChangeVehicle: (String) -> Unit = {},
    onEditTripDetails: (
        startAddress: String,
        endAddress: String,
        tolls: Double,
        parking: Double,
        startOdometer: Double?,
        endOdometer: Double?,
        notes: String
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onDismiss: () -> Unit
) {
    var vehicleMenuExpanded by remember { mutableStateOf(false) }
    var showEditTripModal by remember { mutableStateOf(false) }
    val dateTimeFormat = SimpleDateFormat("EEE, d MMM yyyy • h:mm a", Locale.getDefault())
    val startFormatted = dateTimeFormat.format(Date(trip.startTime))
    val endFormatted = trip.endTime?.let { dateTimeFormat.format(Date(it)) } ?: "In Progress"

    val durationMillis = ((trip.endTime ?: System.currentTimeMillis()) - trip.startTime).coerceAtLeast(60_000L)
    val durationMinutes = (durationMillis / 60_000L).coerceAtLeast(1L)
    val durationFormatted = if (durationMinutes >= 60L) {
        "${durationMinutes / 60L}h ${durationMinutes % 60L}m"
    } else {
        "${durationMinutes}m"
    }
    val durationHours = durationMillis / 3_600_000.0
    val avgSpeed = if (durationHours > 0.0 && trip.distanceKm > 0.0) {
        (trip.distanceKm / durationHours).coerceIn(0.0, 220.0)
    } else 0.0

    if (showEditTripModal) {
        EditTripDialog(
            trip = trip,
            settings = settings,
            onDismiss = { showEditTripModal = false },
            onSaveChanges = { startAddr, endAddr, tollsVal, parkingVal, startOdo, endOdo, notesVal ->
                onEditTripDetails(startAddr, endAddr, tollsVal, parkingVal, startOdo, endOdo, notesVal)
                showEditTripModal = false
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp)),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Trip Details",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Text(
                            text = trip.category,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (trip.category == TripCategory.BUSINESS.name) EmeraldGreen else AmberPersonal
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showEditTripModal = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BlueCobalt.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BlueCobalt),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("open_edit_trip_modal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Trip",
                                tint = BlueCobalt,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Edit",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueCobalt
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Route details
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(EmeraldGreen))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Departure", fontSize = 10.sp, color = SlateLight)
                                Text(trip.startAddress, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NavyDark)
                                Text(startFormatted, fontSize = 11.sp, color = SlateLight)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(RedExpense))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Arrival", fontSize = 10.sp, color = SlateLight)
                                Text(trip.endAddress ?: "In Progress", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NavyDark)
                                Text(endFormatted, fontSize = 11.sp, color = SlateLight)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Compact Static Route Summary Strip (Duration, Average Speed, GPS Route Telemetry)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compact_route_summary_strip"),
                    shape = RoundedCornerShape(12.dp),
                    color = BlueCobalt.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, BlueCobalt.copy(alpha = 0.22f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = BlueCobalt,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Column {
                                Text("Duration", fontSize = 9.sp, color = SlateLight)
                                Text(durationFormatted, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyDark)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Column {
                                Text("Avg Speed", fontSize = 9.sp, color = SlateLight)
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", avgSpeed)} ${settings.unitDistance}/h",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = null,
                                tint = if (trip.isManual) AmberPersonal else EmeraldGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Column {
                                Text("Route Log", fontSize = 9.sp, color = SlateLight)
                                Text(
                                    text = if (trip.isManual) "Manual Entry" else if (gpsPointsCount > 0) "$gpsPointsCount GPS pts" else "GPS Verified",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Metric Cards: Distance & Changeable Vehicle Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total Distance", fontSize = 11.sp, color = SlateLight)
                            Text(
                                text = "${String.format(Locale.US, "%.2f", trip.distanceKm)} ${settings.unitDistance}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueCobalt
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = vehicles.isNotEmpty()) {
                                    vehicleMenuExpanded = true
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                            border = if (vehicles.size > 1) BorderStroke(1.dp, BlueCobalt.copy(alpha = 0.35f)) else null,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (vehicles.size > 1) "Vehicle (Tap to change)" else "Vehicle Used",
                                        fontSize = 10.sp,
                                        color = if (vehicles.size > 1) BlueCobalt else SlateLight,
                                        fontWeight = if (vehicles.size > 1) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    if (vehicles.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Change vehicle",
                                            tint = BlueCobalt,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = vehicle?.name ?: "Select Vehicle",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark,
                                    maxLines = 1
                                )
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
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Detailed Cost Breakdown Table
                Text(
                    text = "Personal Expense Breakdown",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyDark
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val vehEff = vehicle?.efficiencyVal ?: 14.0
                        val fuelConsumed = if (vehEff > 0) trip.distanceKm / vehEff else 0.0
                        val fuelUnit = settings.fuelRateUnit.trim().removePrefix("/").trim().ifBlank { "L" }
                        CostBreakdownRow("Vehicle Efficiency (preset)", "${String.format(Locale.US, "%.1f", vehEff)} ${settings.unitDistance}/$fuelUnit")
                        CostBreakdownRow("Fuel Consumed", "${String.format(Locale.US, "%.2f", fuelConsumed)} $fuelUnit")
                        CostBreakdownRow("Fuel Cost (est.)", "${settings.currencySymbol}${String.format(Locale.US, "%.2f", trip.fuelCost)}")
                        CostBreakdownRow("Wear & Tear Maintenance", "${settings.currencySymbol}${String.format(Locale.US, "%.2f", trip.maintenanceCost)}")
                        if (trip.tolls > 0) {
                            CostBreakdownRow("Tolls", "${settings.currencySymbol}${String.format(Locale.US, "%.2f", trip.tolls)}")
                        }
                        if (trip.parking > 0) {
                            CostBreakdownRow("Parking", "${settings.currencySymbol}${String.format(Locale.US, "%.2f", trip.parking)}")
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = SlateBorder)
                        CostBreakdownRow(
                            label = "Total Personal Out-of-Pocket",
                            value = "${settings.currencySymbol}${String.format(Locale.US, "%.2f", trip.totalPersonalCost)}",
                            isBold = true,
                            valueColor = AmberPersonal
                        )
                    }
                }

                if (trip.category == TripCategory.BUSINESS.name) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Business Tax Deduction", fontSize = 11.sp, color = EmeraldGreen, fontWeight = FontWeight.SemiBold)
                                Text("Standard rate: ${settings.currencySymbol}${settings.standardTaxRatePerKm}/${settings.unitDistance}", fontSize = 10.sp, color = SlateLight)
                            }
                            Text(
                                text = "${settings.currencySymbol}${String.format(Locale.US, "%.2f", trip.reimbursementAmount)}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = EmeraldGreen
                            )
                        }
                    }
                }

                if (trip.startOdometer != null || trip.endOdometer != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Start Odometer", fontSize = 11.sp, color = SlateLight)
                                Text("${trip.startOdometer ?: "--"}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NavyDark)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("End Odometer", fontSize = 11.sp, color = SlateLight)
                                Text("${trip.endOdometer ?: "--"}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NavyDark)
                            }
                        }
                    }
                }

                if (trip.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Notes", fontSize = 11.sp, color = SlateLight, fontWeight = FontWeight.SemiBold)
                    Text(trip.notes, fontSize = 13.sp, color = NavyDark, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun CostBreakdownRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: Color = NavyDark
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isBold) NavyDark else SlateMedium
        )
        Text(
            text = value,
            fontSize = if (isBold) 14.sp else 12.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor
        )
    }
}
