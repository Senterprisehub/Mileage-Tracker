package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object AutoResumeManager {

    const val ACTION_AUTO_RESUME_TRACKING = "com.example.ACTION_AUTO_RESUME_TRACKING"
    private const val REQUEST_CODE_AUTO_RESUME = 9021

    fun scheduleResume(context: Context, resumeMillis: Long) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(appContext, AutoResumeReceiver::class.java).apply {
            action = ACTION_AUTO_RESUME_TRACKING
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_AUTO_RESUME,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = System.currentTimeMillis()
        if (resumeMillis <= now) {
            // Target time already elapsed, trigger immediately
            Log.d("AutoResumeManager", "Target time already elapsed ($resumeMillis <= $now). Sending intent directly.")
            appContext.sendBroadcast(intent)
            return
        }

        val showIntent = Intent(appContext, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // setAlarmClock grants an explicit foreground service start exemption on Android 12+
            val alarmClockInfo = AlarmManager.AlarmClockInfo(resumeMillis, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d("AutoResumeManager", "Scheduled AlarmClock alarm for $resumeMillis")
        } catch (e: Exception) {
            Log.w("AutoResumeManager", "setAlarmClock failed, falling back to setExactAndAllowWhileIdle", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, resumeMillis, pendingIntent)
                        Log.d("AutoResumeManager", "Scheduled exact alarm for $resumeMillis")
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, resumeMillis, pendingIntent)
                        Log.d("AutoResumeManager", "Scheduled inexact alarm for $resumeMillis")
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, resumeMillis, pendingIntent)
                    Log.d("AutoResumeManager", "Scheduled exact alarm for $resumeMillis")
                }
            } catch (e2: Exception) {
                Log.e("AutoResumeManager", "Fallback alarm also failed", e2)
                try {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, resumeMillis, pendingIntent)
                } catch (_: Exception) {}
            }
        }
    }

    fun cancelPendingResume(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(appContext, AutoResumeReceiver::class.java).apply {
            action = ACTION_AUTO_RESUME_TRACKING
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_AUTO_RESUME,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("AutoResumeManager", "Canceled pending auto-resume alarm.")
        }
    }
}
