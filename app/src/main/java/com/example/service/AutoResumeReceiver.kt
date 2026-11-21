package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.data.local.MileageDatabase
import com.example.data.local.MileageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AutoResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val isSupportedAction = action == AutoResumeManager.ACTION_AUTO_RESUME_TRACKING ||
            action == AutoResumeManager.ACTION_RESTART_SERVICE ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (!isSupportedAction) return

        val appContext = context.applicationContext
        Log.d("AutoResumeReceiver", "Received background wakeup action=$action")

        // Acquire a temporary 10-second CPU WakeLock so the device does not re-enter Doze
        // before MileageTrackingService promotes itself to foreground and starts standby GPS.
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MileageTracker::AutoResumeWakeLock"
        )?.apply {
            setReferenceCounted(false)
            try {
                acquire(10_000L)
            } catch (_: Exception) {}
        }

        // CRITICAL: For ACTION_AUTO_RESUME_TRACKING, promote/start the Foreground Service synchronously
        // on the main thread inside onReceive() while the AlarmClock FGS exemption window is active.
        if (action == AutoResumeManager.ACTION_AUTO_RESUME_TRACKING &&
            MileageTrackingService.canStartLocationForegroundService(appContext)
        ) {
            MileageTrackingService.resumeMonitoring(appContext)
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = MileageDatabase.getInstance(appContext)
                val repo = MileageRepository(db)
                val current = repo.getSettings()
                val activeTrip = repo.getActiveTrip()

                if (action == AutoResumeManager.ACTION_AUTO_RESUME_TRACKING) {
                    if (current.autoTrackingPausedUntil != null) {
                        repo.updateSettings(current.copy(autoTrackingPausedUntil = null))
                    }
                    AutoResumeManager.cancelPendingResume(appContext)
                    if (current.autoTrackingEnabled && MileageTrackingService.canStartLocationForegroundService(appContext)) {
                        MileageTrackingService.resumeMonitoring(appContext)
                    }
                } else {
                    val pausedUntil = current.autoTrackingPausedUntil
                    val resolvedSettings = if (pausedUntil != null && pausedUntil > 0L && System.currentTimeMillis() >= pausedUntil) {
                        val unpaused = current.copy(autoTrackingPausedUntil = null)
                        repo.updateSettings(unpaused)
                        AutoResumeManager.cancelPendingResume(appContext)
                        unpaused
                    } else {
                        current
                    }

                    if (activeTrip != null || (resolvedSettings.autoTrackingEnabled && !resolvedSettings.isAutoTrackingPaused())) {
                        if (MileageTrackingService.canStartLocationForegroundService(appContext)) {
                            MileageTrackingService.ensureRunning(appContext)
                        }
                    } else if (resolvedSettings.autoTrackingEnabled &&
                        resolvedSettings.autoTrackingPausedUntil != null &&
                        resolvedSettings.autoTrackingPausedUntil > System.currentTimeMillis()
                    ) {
                        // Ensure the pause wakeup alarm remains scheduled if app/service was restarted during pause
                        AutoResumeManager.scheduleResume(appContext, resolvedSettings.autoTrackingPausedUntil)
                    }
                }
            } catch (e: Exception) {
                Log.w("AutoResumeReceiver", "Deferred background wakeup ($action): ${e.message}")
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }
    }
}
