package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

object ScheduleSerializer {
    fun defaultSchedules(): List<DaySchedule> {
        val days = listOf(
            Triple("Monday", "Mon", true),
            Triple("Tuesday", "Tue", true),
            Triple("Wednesday", "Wed", true),
            Triple("Thursday", "Thu", true),
            Triple("Friday", "Fri", true),
            Triple("Saturday", "Sat", false),
            Triple("Sunday", "Sun", false)
        )
        return days.mapIndexed { idx, (name, short, isEnabled) ->
            DaySchedule(
                dayIndex = idx,
                dayName = name,
                dayShort = short,
                enabled = isEnabled,
                startHour12 = 9,
                startMinute = 0,
                startAmPm = "AM",
                endHour12 = 6,
                endMinute = 0,
                endAmPm = "PM",
                category = "BUSINESS"
            )
        }
    }

    fun toJson(schedules: List<DaySchedule>): String {
        val arr = JSONArray()
        for (sched in schedules) {
            val obj = JSONObject()
            obj.put("dayIndex", sched.dayIndex)
            obj.put("dayName", sched.dayName)
            obj.put("dayShort", sched.dayShort)
            obj.put("enabled", sched.enabled)
            obj.put("startHour12", sched.startHour12)
            obj.put("startMinute", sched.startMinute)
            obj.put("startAmPm", sched.startAmPm)
            obj.put("endHour12", sched.endHour12)
            obj.put("endMinute", sched.endMinute)
            obj.put("endAmPm", sched.endAmPm)
            obj.put("category", sched.category)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun fromJson(jsonStr: String): List<DaySchedule> {
        if (jsonStr.isBlank()) return defaultSchedules()
        return try {
            val list = mutableListOf<DaySchedule>()
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val dayName = obj.optString("dayName", "Day")
                list.add(
                    DaySchedule(
                        dayIndex = obj.optInt("dayIndex", i),
                        dayName = dayName,
                        dayShort = obj.optString("dayShort", dayName.take(3)),
                        enabled = obj.optBoolean("enabled", true),
                        startHour12 = obj.optInt("startHour12", 9),
                        startMinute = obj.optInt("startMinute", 0),
                        startAmPm = obj.optString("startAmPm", "AM"),
                        endHour12 = obj.optInt("endHour12", 6),
                        endMinute = obj.optInt("endMinute", 0),
                        endAmPm = obj.optString("endAmPm", "PM"),
                        category = obj.optString("category", "BUSINESS")
                    )
                )
            }
            list
        } catch (_: Exception) {
            defaultSchedules()
        }
    }
}
