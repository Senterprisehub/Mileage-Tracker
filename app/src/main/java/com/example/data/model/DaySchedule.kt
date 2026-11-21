package com.example.data.model

import java.util.Locale

data class DaySchedule(
    val dayIndex: Int,
    val dayName: String,
    val dayShort: String = dayName.take(3),
    val enabled: Boolean,
    val startHour12: Int,
    val startMinute: Int,
    val startAmPm: String,
    val endHour12: Int,
    val endMinute: Int,
    val endAmPm: String,
    val category: String
) {
    fun formatStartTime(): String {
        return String.format(Locale.US, "%02d:%02d %s", startHour12, startMinute, startAmPm)
    }

    fun formatEndTime(): String {
        return String.format(Locale.US, "%02d:%02d %s", endHour12, endMinute, endAmPm)
    }

    fun toStartMinutes24(): Int {
        val h = if (startAmPm.equals("AM", ignoreCase = true)) {
            if (startHour12 == 12) 0 else startHour12
        } else {
            if (startHour12 == 12) 12 else startHour12 + 12
        }
        return h * 60 + startMinute
    }

    fun toEndMinutes24(): Int {
        val h = if (endAmPm.equals("AM", ignoreCase = true)) {
            if (endHour12 == 12) 0 else endHour12
        } else {
            if (endHour12 == 12) 12 else endHour12 + 12
        }
        return h * 60 + endMinute
    }
}
