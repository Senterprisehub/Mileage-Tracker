package com.example.ui.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppSettingsEntity
import com.example.data.model.VehicleEntity
import com.example.service.LiveTrackingData
import com.example.ui.theme.AmberPersonal
import com.example.ui.theme.BlueCobalt
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyDark
import com.example.ui.theme.RedExpense
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateLight
import com.example.ui.theme.SlateMedium
import java.util.Locale

@Composable
fun ActiveDriveDialog(
    trackingData: LiveTrackingData,
    settings: AppSettingsEntity,
    vehicles: List<VehicleEntity> = emptyList(),
    onDismiss: () -> Unit = {},
    onStopDrive: (tolls: Double, parking: Double, endOdometer: Double?, notes: String) -> Unit
) {
    var tollsText by remember { mutableStateOf("") }
    var parkingText by remember { mutableStateOf("") }
    var endOdometerText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }

    val minutes = trackingData.elapsedSeconds / 60
    val seconds = trackingData.elapsedSeconds % 60
    val timeFormatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    // Accurate vehicle-specific live fuel calculation
    val tolls = tollsText.toDoubleOrNull() ?: 0.0
    val parking = parkingText.toDoubleOrNull() ?: 0.0

    val vehicleMileage = if (trackingData.vehicleMileageKmpl > 0.0) trackingData.vehicleMileageKmpl else 14.0
    val fuelRate = if (trackingData.fuelPrice > 0.0) trackingData.fuelPrice else settings.currentFuelPrice
    val currency = if (trackingData.currencySymbol.isNotEmpty()) trackingData.currencySymbol else settings.currencySymbol

    // 1. Live fuel cost formula: (Distance / Mileage) * Fuel Price
    val liveFuelCost = if (vehicleMileage > 0.0) {
        (trackingData.distanceKm / vehicleMileage) * fuelRate
    } else 0.0

    // 2. Wear & Tear Maintenance cost: Distance * Wear Rate
    val activeVehicle = vehicles.find { it.id == trackingData.vehicleId }
    val wearRate = activeVehicle?.maintenanceCostPerKm ?: 0.50
    val liveWearCost = trackingData.distanceKm * wearRate

    // 3. Tolls cost: tolls
    // 4. Parking cost: parking

    // 5. Tax Write-off: Distance * standardTaxRatePerKm
    val liveTaxDeduction = trackingData.distanceKm * settings.standardTaxRatePerKm

    // Total Trip Cost = SUM of Live Fuel cost + Wear & Tear Maintenance cost + Tolls cost + Parking cost + Tax Write-off
    val totalTripCost = liveFuelCost + liveWearCost + tolls + parking + liveTaxDeduction
    val livePersonalCost = liveFuelCost + liveWearCost + tolls + parking

    // Persistent non-minimizable dialog per user requirement 2: cannot be dismissed until trip completed/stopped
    Dialog(
        onDismissRequest = { /* Cannot be minimized or dismissed by clicking outside or back press */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, SlateBorder, RoundedCornerShape(24.dp)),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with live pulsing status indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(EmeraldGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LIVE DRIVE IN PROGRESS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen,
                            letterSpacing = 1.sp
                        )
                    }

                    // Active vehicle pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = BlueCobalt,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = trackingData.vehicleName.ifBlank { "Vehicle" },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Telemetry Central Gauge: Distance + Speed + Elapsed Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Distance Gauge
                    Card(
                        modifier = Modifier.weight(1.2f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "DISTANCE", fontSize = 10.sp, color = SlateLight, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format(Locale.US, "%.2f", trackingData.distanceKm),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = NavyDark
                            )
                            Text(text = settings.unitDistance, fontSize = 11.sp, color = BlueCobalt, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Speed & Time Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Speed
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Speed, contentDescription = null, tint = BlueCobalt, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Speed", fontSize = 11.sp, color = SlateLight)
                                }
                                Text(
                                    text = "${trackingData.speedKmh.toInt()} km/h",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                            }
                        }

                        // Elapsed Time
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Timer, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Time", fontSize = 11.sp, color = SlateLight)
                                }
                                Text(
                                    text = timeFormatted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Physical Activity & Precision 3-Axis Accelerometer Status Strip
                val activityBadgeColor = if (trackingData.isIdle) AmberPersonal else EmeraldGreen
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_activity_sensor_card"),
                    shape = RoundedCornerShape(12.dp),
                    color = activityBadgeColor.copy(alpha = 0.10f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, activityBadgeColor.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(activityBadgeColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Status: ${if (trackingData.isIdle) "Stationary (Idle)" else "Driving"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                                Text(
                                    text = if (trackingData.isIdle) {
                                        "Requires ≥10 km/h for 5s to resume driving"
                                    } else {
                                        "3-Axis Linear Accel & Speed Gate (≥10 km/h)"
                                    },
                                    fontSize = 10.sp,
                                    color = SlateMedium
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format(Locale.US, "%.2f m/s²", trackingData.rmsAccelMps2),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = activityBadgeColor
                            )
                            Text(
                                text = String.format(Locale.US, "Peak %.2f", trackingData.linearAccelMps2),
                                fontSize = 9.sp,
                                color = SlateLight
                            )
                        }
                    }
                }

                // Idle Countdown Banner when vehicle is stationary
                if (trackingData.isIdle) {
                    val countdownMinutes = trackingData.idleSecondsRemaining / 60
                    val countdownSecs = trackingData.idleSecondsRemaining % 60
                    val countdownFormatted = String.format(Locale.US, "%02d:%02d", countdownMinutes, countdownSecs)
                    val idleProgress = if (trackingData.idleTimeoutTotalSeconds > 0) {
                        (trackingData.idleSecondsRemaining.toFloat() / trackingData.idleTimeoutTotalSeconds.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFEF3C7),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFF59E0B))
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF59E0B).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = Color(0xFFB45309),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "VEHICLE IDLE (NO DISTANCE)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF92400E),
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = "Auto-saving trip in $countdownFormatted",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFB45309)
                                ) {
                                    Text(
                                        text = countdownFormatted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { idleProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Color(0xFFB45309),
                                trackColor = Color(0xFFFDE68A)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Vehicle-Specific Live Fuel Cost & Consumption Meter Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = AmberPersonal.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberPersonal.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(AmberPersonal.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalGasStation,
                                        contentDescription = null,
                                        tint = AmberPersonal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Live Fuel Cost & Meter",
                                        fontSize = 12.sp,
                                        color = NavyDark,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${trackingData.vehicleName.ifBlank { "Vehicle" }} • ${String.format(Locale.US, "%.1f", vehicleMileage)} ${trackingData.vehicleEfficiencyUnit} preset",
                                        fontSize = 11.sp,
                                        color = SlateLight
                                    )
                                }
                            }

                            Text(
                                text = "$currency${String.format(Locale.US, "%.2f", liveFuelCost)}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AmberPersonal
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color.LightGray.copy(alpha = 0.4f)))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Live Consumption Meter Gauge
                        val fuelConsumedVal = if (vehicleMileage > 0.0) {
                            trackingData.distanceKm / vehicleMileage
                        } else 0.0
                        val fuelConsumedUnit = if (trackingData.vehicleEfficiencyUnit.lowercase().contains("mpg")) "Gal" else "L"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Fuel Consumed Live",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SlateMedium
                                )
                                Text(
                                    text = "Distance: ${String.format(Locale.US, "%.2f", trackingData.distanceKm)} ${settings.unitDistance}",
                                    fontSize = 10.sp,
                                    color = SlateLight
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${String.format(Locale.US, "%.3f", fuelConsumedVal)} $fuelConsumedUnit",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BlueCobalt
                                )
                                Text(
                                    text = "@ $currency${String.format(Locale.US, "%.2f", fuelRate)} / $fuelConsumedUnit",
                                    fontSize = 10.sp,
                                    color = SlateLight
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Total Running Cost & Tax Deductions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total Trip Cost", fontSize = 10.sp, color = SlateLight, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$currency${String.format(Locale.US, "%.2f", totalTripCost)}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                            Text(
                                text = "Fuel + Wear + Tolls + Parking + Tax",
                                fontSize = 8.sp,
                                color = SlateMedium
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Tax Write-off", fontSize = 10.sp, color = SlateLight, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$currency${String.format(Locale.US, "%.2f", liveTaxDeduction)}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                            Text(
                                text = "${settings.currencySymbol}${settings.standardTaxRatePerKm}/${settings.unitDistance}",
                                fontSize = 8.sp,
                                color = SlateMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Itemized Cost Breakdown Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Live Fuel Cost", fontSize = 10.sp, color = SlateMedium)
                            Text("$currency${String.format(Locale.US, "%.2f", liveFuelCost)}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = NavyDark)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Wear & Tear Maintenance", fontSize = 10.sp, color = SlateMedium)
                            Text("$currency${String.format(Locale.US, "%.2f", liveWearCost)}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = NavyDark)
                        }
                        if (tolls > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tolls", fontSize = 10.sp, color = SlateMedium)
                                Text("$currency${String.format(Locale.US, "%.2f", tolls)}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = NavyDark)
                            }
                        }
                        if (parking > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Parking", fontSize = 10.sp, color = SlateMedium)
                                Text("$currency${String.format(Locale.US, "%.2f", parking)}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = NavyDark)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tax Write-off", fontSize = 10.sp, color = EmeraldGreen)
                            Text("+$currency${String.format(Locale.US, "%.2f", liveTaxDeduction)}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = EmeraldGreen)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Locations Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Start: ${trackingData.startAddress}",
                                fontSize = 12.sp,
                                color = NavyDark,
                                maxLines = 1
                            )
                        }
                        if (trackingData.currentAddress.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = BlueCobalt, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Current: ${trackingData.currentAddress}",
                                    fontSize = 12.sp,
                                    color = SlateLight,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Optional Tolls, Parking & Odometer logging
                Text(
                    text = "Add Trip Expenses (Optional)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateLight,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tollsText,
                        onValueChange = { tollsText = it },
                        label = { Text("Tolls ($currency)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BlueCobalt,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = NavyDark,
                            unfocusedTextColor = NavyDark
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = parkingText,
                        onValueChange = { parkingText = it },
                        label = { Text("Parking ($currency)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BlueCobalt,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = NavyDark,
                            unfocusedTextColor = NavyDark
                        ),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = endOdometerText,
                        onValueChange = { endOdometerText = it },
                        label = { Text("End Odometer", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BlueCobalt,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = NavyDark,
                            unfocusedTextColor = NavyDark
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Notes", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BlueCobalt,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = NavyDark,
                            unfocusedTextColor = NavyDark
                        ),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Stop & Save Drive Button
                Button(
                    onClick = {
                        onStopDrive(
                            tolls,
                            parking,
                            endOdometerText.toDoubleOrNull(),
                            notesText
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("stop_and_save_drive_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedExpense)
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STOP & COMPLETE DRIVE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
