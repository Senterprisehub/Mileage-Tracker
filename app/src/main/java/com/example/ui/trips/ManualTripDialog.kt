package com.example.ui.trips

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppSettingsEntity
import com.example.data.model.TripCategory
import com.example.data.model.VehicleEntity
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualTripDialog(
    vehicles: List<VehicleEntity>,
    selectedVehicleId: String?,
    settings: AppSettingsEntity,
    onDismiss: () -> Unit,
    onSaveTrip: (
        vehicleId: String?,
        category: TripCategory,
        distanceKm: Double,
        startAddress: String,
        endAddress: String,
        tolls: Double,
        parking: Double,
        startOdometer: Double?,
        endOdometer: Double?,
        notes: String,
        tripTimestampMillis: Long
    ) -> Unit
) {
    val context = LocalContext.current
    var chosenVehicleId by remember {
        mutableStateOf(
            selectedVehicleId
                ?: vehicles.find { it.isDefault }?.id
                ?: vehicles.firstOrNull()?.id
        )
    }
    var selectedCategory by remember { mutableStateOf(TripCategory.BUSINESS) }
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var datePresetKey by remember { mutableStateOf("TODAY") } // "TODAY", "YESTERDAY", "CUSTOM"

    var distanceText by remember { mutableStateOf("") }
    var startAddress by remember { mutableStateOf("") }
    var endAddress by remember { mutableStateOf("") }
    var tollsText by remember { mutableStateOf("") }
    var parkingText by remember { mutableStateOf("") }
    var startOdoText by remember { mutableStateOf("") }
    var endOdoText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var vehicleMenuOpen by remember { mutableStateOf(false) }

    val formattedSelectedDate = remember(selectedDateMillis) {
        SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(selectedDateMillis))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Log Manual Drive",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Text(
                            text = "Add a completed trip with automatic fuel & tax calculation",
                            fontSize = 11.sp,
                            color = SlateLight
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateMedium)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Trip Date Selector (Today, Yesterday, or Custom Past Date Picker)
                Text(
                    text = "Trip Date",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateLight
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = datePresetKey == "TODAY",
                        onClick = {
                            datePresetKey = "TODAY"
                            selectedDateMillis = System.currentTimeMillis()
                        },
                        label = {
                            Text(
                                text = "Today",
                                fontSize = 11.sp,
                                fontWeight = if (datePresetKey == "TODAY") FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("manual_trip_date_today"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BlueCobalt,
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = datePresetKey == "YESTERDAY",
                        onClick = {
                            datePresetKey = "YESTERDAY"
                            val cal = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, -1)
                            }
                            selectedDateMillis = cal.timeInMillis
                        },
                        label = {
                            Text(
                                text = "Yesterday",
                                fontSize = 11.sp,
                                fontWeight = if (datePresetKey == "YESTERDAY") FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("manual_trip_date_yesterday"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BlueCobalt,
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = datePresetKey == "CUSTOM",
                        onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val pickedCal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    }
                                    selectedDateMillis = pickedCal.timeInMillis
                                    datePresetKey = "CUSTOM"
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).apply {
                                datePicker.maxDate = System.currentTimeMillis()
                            }.show()
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (datePresetKey == "CUSTOM") Color.White else BlueCobalt
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (datePresetKey == "CUSTOM") formattedSelectedDate else "Pick Date...",
                                    fontSize = 11.sp,
                                    fontWeight = if (datePresetKey == "CUSTOM") FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        },
                        modifier = Modifier.testTag("manual_trip_date_picker_chip"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                Text(
                    text = "Logging for: $formattedSelectedDate",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BlueCobalt
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Vehicle Selector
                if (vehicles.isNotEmpty()) {
                    Text(
                        text = "Vehicle",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateLight
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val currentVeh = vehicles.find { it.id == chosenVehicleId } ?: vehicles.first()
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { vehicleMenuOpen = true },
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
                                    text = "${currentVeh.name} (${currentVeh.efficiencyVal} ${currentVeh.getParsedVehicleType().efficiencyUnit})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = SlateMedium)
                            }
                        }
                        DropdownMenu(
                            expanded = vehicleMenuOpen,
                            onDismissRequest = { vehicleMenuOpen = false }
                        ) {
                            vehicles.forEach { v ->
                                DropdownMenuItem(
                                    text = { Text("${v.name} • ${v.fuelType}") },
                                    onClick = {
                                        chosenVehicleId = v.id
                                        vehicleMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Category Chips
                Text(
                    text = "Trip Category",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateLight
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TripCategory.entries.forEach { cat ->
                        val selected = selectedCategory == cat
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategory = cat },
                            label = {
                                Text(
                                    text = cat.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BlueCobalt,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = distanceText,
                    onValueChange = { distanceText = it },
                    label = { Text("Distance (${settings.unitDistance})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_distance_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = startAddress,
                    onValueChange = { startAddress = it },
                    label = { Text("Start Location / Origin") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = endAddress,
                    onValueChange = { endAddress = it },
                    label = { Text("Destination") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tollsText,
                        onValueChange = { tollsText = it },
                        label = { Text("Tolls (${settings.currencySymbol})", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = parkingText,
                        onValueChange = { parkingText = it },
                        label = { Text("Parking (${settings.currencySymbol})", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startOdoText,
                        onValueChange = { startOdoText = it },
                        label = { Text("Start Odometer", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endOdoText,
                        onValueChange = { endOdoText = it },
                        label = { Text("End Odometer", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(18.dp))

                val startOdoParsed = startOdoText.toDoubleOrNull()
                val endOdoParsed = endOdoText.toDoubleOrNull()
                val odoDerivedDist = if (startOdoParsed != null && endOdoParsed != null && endOdoParsed > startOdoParsed) {
                    endOdoParsed - startOdoParsed
                } else null
                val parsedDist = distanceText.toDoubleOrNull() ?: odoDerivedDist ?: 0.0

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = SlateMedium)
                    }
                    Button(
                        onClick = {
                            if (parsedDist > 0.0) {
                                onSaveTrip(
                                    chosenVehicleId,
                                    selectedCategory,
                                    parsedDist,
                                    startAddress.ifBlank { "Origin" },
                                    endAddress.ifBlank { "Destination" },
                                    tollsText.toDoubleOrNull() ?: 0.0,
                                    parkingText.toDoubleOrNull() ?: 0.0,
                                    startOdoParsed,
                                    endOdoParsed,
                                    notes,
                                    selectedDateMillis
                                )
                            }
                        },
                        enabled = parsedDist > 0.0,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_manual_trip_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueCobalt)
                    ) {
                        Text("Save Drive", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
