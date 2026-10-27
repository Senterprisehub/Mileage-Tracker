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
        if (intent?.action != AutoResumeManager.ACTION_AUTO_RESUME_TRACKING) return

        Log.d("AutoResumeReceiver", "Auto-resume alarm triggered! Clearing pause and restarting motion monitoring.")
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = MileageDatabase.getInstance(appContext)
                val repo = MileageRepository(db)
                val current = repo.getSettings()
                repo.updateSettings(current.copy(autoTrackingPausedUntil = null))
                if (current.autoTrackingEnabled) {
                    MileageTrackingService.resumeMonitoring(appContext)
                }
            } catch (e: Exception) {
                Log.e("AutoResumeReceiver", "Failed to auto-resume tracking", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
