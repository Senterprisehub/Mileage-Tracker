package com.example.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.ui.theme.BlueCobalt
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateLight
import com.example.ui.theme.SlateMedium
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddSavedPlaceDialog(
    onDismiss: () -> Unit,
    onSavePlace: (name: String, latitude: Double, longitude: Double, radiusMeters: Float) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var placeName by remember { mutableStateOf("") }
    var addressQuery by remember { mutableStateOf("") }
    var latitudeText by remember { mutableStateOf("") }
    var longitudeText by remember { mutableStateOf("") }
    var selectedRadius by remember { mutableFloatStateOf(150f) }
    var isLocating by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val parsedLat = latitudeText.toDoubleOrNull()
    val parsedLng = longitudeText.toDoubleOrNull()
    val isValid = placeName.trim().isNotEmpty() &&
        parsedLat != null && parsedLat in -90.0..90.0 &&
        parsedLng != null && parsedLng in -180.0..180.0

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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Add Saved Place",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Text(
                            text = "Trips starting or ending inside this geofence auto-label with this name",
                            fontSize = 11.sp,
                            color = SlateLight
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateMedium)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Preset Chips for Place Label
                Text(
                    text = "PLACE LABEL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateLight
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Home", "Office", "Client HQ", "Warehouse", "Site A").forEach { preset ->
                        val isSelected = placeName.equals(preset, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { placeName = preset },
                            label = {
                                Text(
                                    text = preset,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BlueCobalt.copy(alpha = 0.12f),
                                selectedLabelColor = BlueCobalt
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = placeName,
                    onValueChange = { placeName = it },
                    label = { Text("Place Name (e.g., Home, Office, Client)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("saved_place_name_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = SlateBorder)
                Spacer(modifier = Modifier.height(14.dp))

                // Location Acquisition: 1-Tap Current GPS or Typed Address Lookup
                Text(
                    text = "SET LOCATION COORDINATES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateLight
                )
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        isLocating = true
                        statusMessage = "Acquiring current GPS coordinates..."
                        fetchDeviceCurrentLocation(context) { lat, lng, resolvedAddr ->
                            isLocating = false
                            if (lat != null && lng != null) {
                                latitudeText = String.format(Locale.US, "%.6f", lat)
                                longitudeText = String.format(Locale.US, "%.6f", lng)
                                if (!resolvedAddr.isNullOrBlank()) {
                                    addressQuery = resolvedAddr
                                }
                                statusMessage = "GPS locked: ${resolvedAddr ?: "$latitudeText, $longitudeText"}"
                            } else {
                                statusMessage = "Could not acquire GPS fix. Try typing an address below."
                            }
                        }
                    },
                    enabled = !isLocating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("saved_place_use_current_gps_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    if (isLocating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "USE CURRENT GPS LOCATION",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Typed Address Lookup Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = addressQuery,
                        onValueChange = { addressQuery = it },
                        label = { Text("Or search street / landmark address", fontSize = 11.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("saved_place_address_lookup_input"),
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = {
                            val query = addressQuery.trim()
                            if (query.isEmpty()) {
                                Toast.makeText(context, "Enter an address or city to lookup", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            isLocating = true
                            statusMessage = "Looking up address coordinates..."
                            scope.launch {
                                val result = geocodeAddressQuery(context, query)
                                isLocating = false
                                if (result != null) {
                                    latitudeText = String.format(Locale.US, "%.6f", result.first)
                                    longitudeText = String.format(Locale.US, "%.6f", result.second)
                                    statusMessage = "Found coordinates for \"$query\""
                                } else {
                                    statusMessage = "Address not found. You can enter Latitude & Longitude directly below."
                                }
                            }
                        },
                        enabled = !isLocating,
                        modifier = Modifier
                            .height(54.dp)
                            .padding(top = 6.dp)
                            .testTag("saved_place_lookup_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BlueCobalt)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Lookup Address", tint = BlueCobalt, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Find", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BlueCobalt)
                    }
                }

                if (!statusMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = statusMessage!!,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BlueCobalt
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Latitude & Longitude Fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = latitudeText,
                        onValueChange = { latitudeText = it },
                        label = { Text("Latitude", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("saved_place_lat_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = longitudeText,
                        onValueChange = { longitudeText = it },
                        label = { Text("Longitude", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("saved_place_lng_input"),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Geofence Radius Selector
                Text(
                    text = "GEOFENCE MATCH RADIUS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateLight
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(100f, 150f, 250f, 500f).forEach { rad ->
                        val isSel = selectedRadius == rad
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedRadius = rad },
                            label = {
                                Text(
                                    text = "${rad.toInt()}m",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BlueCobalt,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = SlateMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            if (isValid && parsedLat != null && parsedLng != null) {
                                onSavePlace(
                                    placeName.trim(),
                                    parsedLat,
                                    parsedLng,
                                    selectedRadius
                                )
                            }
                        },
                        enabled = isValid,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("save_place_confirm_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueCobalt)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Place", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun fetchDeviceCurrentLocation(
    context: Context,
    onResult: (Double?, Double?, String?) -> Unit
) {
    try {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                var actualLoc = loc
                if (actualLoc == null) {
                    val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    actualLoc = locManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: locManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }
                if (actualLoc != null) {
                    val addr = reverseGeocodeShort(context, actualLoc.latitude, actualLoc.longitude)
                    onResult(actualLoc.latitude, actualLoc.longitude, addr)
                } else {
                    fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            val addr = reverseGeocodeShort(context, lastLoc.latitude, lastLoc.longitude)
                            onResult(lastLoc.latitude, lastLoc.longitude, addr)
                        } else {
                            onResult(null, null, null)
                        }
                    }.addOnFailureListener {
                        onResult(null, null, null)
                    }
                }
            }
            .addOnFailureListener {
                onResult(null, null, null)
            }
    } catch (_: Exception) {
        onResult(null, null, null)
    }
}

@Suppress("DEPRECATION")
private fun reverseGeocodeShort(context: Context, lat: Double, lng: Double): String? {
    return try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val list = geocoder.getFromLocation(lat, lng, 1)
        list?.firstOrNull()?.getAddressLine(0)
    } catch (_: Exception) {
        null
    }
}

@Suppress("DEPRECATION")
private suspend fun geocodeAddressQuery(context: Context, query: String): Pair<Double, Double>? {
    return withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val results = geocoder.getFromLocationName(query, 1)
            val match = results?.firstOrNull()
            if (match != null) {
                Pair(match.latitude, match.longitude)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
