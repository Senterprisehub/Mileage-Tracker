package com.example.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.data.model.TripEntity
import com.example.ui.theme.BlueCobalt
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SlateLight
import com.example.ui.theme.SlateMedium
import java.util.Locale

@Composable
fun EditTripDialog(
    trip: TripEntity,
    settings: AppSettingsEntity,
    onDismiss: () -> Unit,
    onSaveChanges: (
        startAddress: String,
        endAddress: String,
        tolls: Double,
        parking: Double,
        startOdometer: Double?,
        endOdometer: Double?,
        notes: String
    ) -> Unit
) {
    var startAddress by remember(trip.id) { mutableStateOf(trip.startAddress) }
    var endAddress by remember(trip.id) { mutableStateOf(trip.endAddress ?: "") }
    var tollsText by remember(trip.id) {
        mutableStateOf(if (trip.tolls > 0.0) String.format(Locale.US, "%.2f", trip.tolls) else "")
    }
    var parkingText by remember(trip.id) {
        mutableStateOf(if (trip.parking > 0.0) String.format(Locale.US, "%.2f", trip.parking) else "")
    }
    var startOdoText by remember(trip.id) {
        mutableStateOf(trip.startOdometer?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var endOdoText by remember(trip.id) {
        mutableStateOf(trip.endOdometer?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var notes by remember(trip.id) { mutableStateOf(trip.notes) }

    val parsedStartOdo = startOdoText.toDoubleOrNull()
    val parsedEndOdo = endOdoText.toDoubleOrNull()
    val odometerDistanceDelta = if (parsedStartOdo != null && parsedEndOdo != null && parsedEndOdo > parsedStartOdo) {
        parsedEndOdo - parsedStartOdo
    } else null

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Edit Trip Expenses & Notes",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Text(
                            text = "Costs & tax write-offs recalculate automatically",
                            fontSize = 11.sp,
                            color = SlateLight
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateMedium)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = startAddress,
                    onValueChange = { startAddress = it },
                    label = { Text("Departure Address") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_trip_start_address"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = endAddress,
                    onValueChange = { endAddress = it },
                    label = { Text("Arrival Address") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_trip_end_address"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = tollsText,
                        onValueChange = { tollsText = it },
                        label = { Text("Tolls (${settings.currencySymbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_trip_tolls"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = parkingText,
                        onValueChange = { parkingText = it },
                        label = { Text("Parking (${settings.currencySymbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_trip_parking"),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = startOdoText,
                        onValueChange = { startOdoText = it },
                        label = { Text("Start Odometer") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_trip_start_odo"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = endOdoText,
                        onValueChange = { endOdoText = it },
                        label = { Text("End Odometer") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_trip_end_odo"),
                        singleLine = true
                    )
                }

                if (odometerDistanceDelta != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Odometer Distance: ${String.format(Locale.US, "%.2f", odometerDistanceDelta)} ${settings.unitDistance} (will update trip distance)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldGreen
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Trip Notes / Purpose / Client") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_trip_notes"),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold, color = SlateMedium)
                    }

                    Button(
                        onClick = {
                            onSaveChanges(
                                startAddress,
                                endAddress,
                                tollsText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                parkingText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                startOdoText.toDoubleOrNull(),
                                endOdoText.toDoubleOrNull(),
                                notes
                            )
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("save_edited_trip_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueCobalt)
                    ) {
                        Text("Save Changes", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
