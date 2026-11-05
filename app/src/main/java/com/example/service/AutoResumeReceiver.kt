package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

        Log.d("AutoResumeReceiver", "Received background wakeup action=$action")
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = MileageDatabase.getInstance(appContext)
                val repo = MileageRepository(db)
                val current = repo.getSettings()
                val activeTrip = repo.getActiveTrip()

                if (action == AutoResumeManager.ACTION_AUTO_RESUME_TRACKING) {
                    repo.updateSettings(current.copy(autoTrackingPausedUntil = null))
                    if (current.autoTrackingEnabled && MileageTrackingService.canStartLocationForegroundService(appContext)) {
                        MileageTrackingService.resumeMonitoring(appContext)
                    }
                } else {
                    // If pause timer expired while app was closed, clear it automatically
                    val pausedUntil = current.autoTrackingPausedUntil
                    val resolvedSettings = if (pausedUntil != null && pausedUntil > 0L && System.currentTimeMillis() >= pausedUntil) {
                        val unpaused = current.copy(autoTrackingPausedUntil = null)
                        repo.updateSettings(unpaused)
                        unpaused
                    } else {
                        current
                    }

                    if ((activeTrip != null || (resolvedSettings.autoTrackingEnabled && !resolvedSettings.isAutoTrackingPaused())) &&
                        MileageTrackingService.canStartLocationForegroundService(appContext)
                    ) {
                        MileageTrackingService.ensureRunning(appContext)
                    }
                }
            } catch (e: Exception) {
                Log.w("AutoResumeReceiver", "Deferred background wakeup ($action): ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
