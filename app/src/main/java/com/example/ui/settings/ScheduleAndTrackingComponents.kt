package com.example.ui.settings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.DaySchedule
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

@Composable
fun DayWiseScheduleCard(
    schedules: List<DaySchedule>,
    workHoursEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdateSchedule: (DaySchedule) -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(BlueCobalt.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = BlueCobalt, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Work Hours Auto-Classification",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                    Text(
                        text = "Drives inside work hours are tagged Business; outside as Personal",
                        fontSize = 11.sp,
                        color = SlateLight
                    )
                }
            }
            Switch(
                checked = workHoursEnabled,
                onCheckedChange = onToggleEnabled,
                colors = SwitchDefaults.colors(checkedThumbColor = BlueCobalt)
            )
        }

        if (workHoursEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            schedules.forEach { day ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (day.enabled) Color(0xFFF8FAFC) else Color(0xFFF1F5F9).copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = day.enabled,
                                onCheckedChange = { onUpdateSchedule(day.copy(enabled = it)) },
                                modifier = Modifier.height(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = day.dayShort,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (day.enabled) NavyDark else SlateLight
                            )
                        }

                        if (day.enabled) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            val start24 = day.toStartMinutes24()
                                            TimePickerDialog(
                                                context,
                                                { _, h24, min ->
                                                    val amPm = if (h24 < 12) "AM" else "PM"
                                                    val h12 = when {
                                                        h24 == 0 -> 12
                                                        h24 > 12 -> h24 - 12
                                                        else -> h24
                                                    }
                                                    onUpdateSchedule(
                                                        day.copy(
                                                            startHour12 = h12,
                                                            startMinute = min,
                                                            startAmPm = amPm
                                                        )
                                                    )
                                                },
                                                start24 / 60,
                                                start24 % 60,
                                                false
                                            ).show()
                                        },
                                    color = Color.White,
                                    border = BorderStroke(1.dp, SlateBorder)
                                ) {
                                    Text(
                                        text = day.formatStartTime(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = BlueCobalt,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Text(" – ", fontSize = 12.sp, color = SlateMedium)

                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            val end24 = day.toEndMinutes24()
                                            TimePickerDialog(
                                                context,
                                                { _, h24, min ->
                                                    val amPm = if (h24 < 12) "AM" else "PM"
                                                    val h12 = when {
                                                        h24 == 0 -> 12
                                                        h24 > 12 -> h24 - 12
                                                        else -> h24
                                                    }
                                                    onUpdateSchedule(
                                                        day.copy(
                                                            endHour12 = h12,
                                                            endMinute = min,
                                                            endAmPm = amPm
                                                        )
                                                    )
                                                },
                                                end24 / 60,
                                                end24 % 60,
                                                false
                                            ).show()
                                        },
                                    color = Color.White,
                                    border = BorderStroke(1.dp, SlateBorder)
                                ) {
                                    Text(
                                        text = day.formatEndTime(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = BlueCobalt,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        } else {
                            Text("Off Duty (Personal)", fontSize = 11.sp, color = SlateLight)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PauseAutoTrackingDialog(
    onDismiss: () -> Unit,
    onConfirmPause: (Long) -> Unit
) {
    val context = LocalContext.current
    var targetTimestamp by remember {
        mutableLongStateOf(System.currentTimeMillis() + 2 * 3600 * 1000L)
    }
    var selectedPresetLabel by remember { mutableStateOf("2 Hours") }
    var isIndefinite by remember { mutableStateOf(false) }

    val presets = listOf(
        "30 Mins" to (30 * 60 * 1000L),
        "1 Hour" to (60 * 60 * 1000L),
        "2 Hours" to (2 * 60 * 60 * 1000L),
        "4 Hours" to (4 * 60 * 60 * 1000L),
        "8 Hours" to (8 * 60 * 60 * 1000L),
        "24 Hours" to (24 * 60 * 60 * 1000L)
    )

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
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AmberPersonal.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PauseCircle, contentDescription = null, tint = AmberPersonal)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Pause Auto-Tracking",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                            Text(
                                text = "Auto-resumes motion detection at preset time",
                                fontSize = 11.sp,
                                color = SlateLight
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateMedium)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Quick Duration Presets",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateLight
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (label, durationMs) ->
                        val selected = !isIndefinite && selectedPresetLabel == label
                        FilterChip(
                            selected = selected,
                            onClick = {
                                isIndefinite = false
                                selectedPresetLabel = label
                                targetTimestamp = System.currentTimeMillis() + durationMs
                            },
                            label = {
                                Text(
                                    text = label,
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

                    FilterChip(
                        selected = isIndefinite,
                        onClick = {
                            isIndefinite = true
                            selectedPresetLabel = "Until Resumed"
                        },
                        label = {
                            Text(
                                text = "Until I Resume",
                                fontSize = 12.sp,
                                fontWeight = if (isIndefinite) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberPersonal,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                if (!isIndefinite) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Custom Resume Date & Time",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateLight
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val cal = Calendar.getInstance().apply { timeInMillis = targetTimestamp }
                    val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
                    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val updatedCal = Calendar.getInstance().apply {
                                            timeInMillis = targetTimestamp
                                            set(Calendar.YEAR, year)
                                            set(Calendar.MONTH, month)
                                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                        }
                                        selectedPresetLabel = "Custom"
                                        targetTimestamp = updatedCal.timeInMillis
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = BlueCobalt, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(dateFormat.format(Date(targetTimestamp)), fontSize = 11.sp, color = NavyDark)
                        }

                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        val updatedCal = Calendar.getInstance().apply {
                                            timeInMillis = targetTimestamp
                                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                                            set(Calendar.MINUTE, minute)
                                            set(Calendar.SECOND, 0)
                                        }
                                        selectedPresetLabel = "Custom"
                                        targetTimestamp = updatedCal.timeInMillis
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    false
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = BlueCobalt, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(timeFormat.format(Date(targetTimestamp)), fontSize = 11.sp, color = NavyDark)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = EmeraldGreen.copy(alpha = 0.08f)
                    ) {
                        Text(
                            text = "Auto-tracking will automatically resume on ${SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(targetTimestamp))}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldGreen,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

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
                            val finalMillis = if (isIndefinite) -1L else targetTimestamp.coerceAtLeast(System.currentTimeMillis() + 60_000L)
                            onConfirmPause(finalMillis)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPersonal)
                    ) {
                        Text("Confirm Pause", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
