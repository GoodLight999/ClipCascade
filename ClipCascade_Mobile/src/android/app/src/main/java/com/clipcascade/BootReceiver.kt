package com.clipcascade

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.facebook.react.HeadlessJsTaskService
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        private const val BOOT_RECOVERY_WORK = "clipcascade_boot_recovery"
        private const val FALLBACK_DELAY_SECONDS = 30L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val applicationContext = context.applicationContext
        if (!shouldRecoverAfterBoot(applicationContext)) {
            RelayHealthStore.record(
                applicationContext,
                category = "recovery",
                trigger = "boot_completed",
                path = "boot_receiver",
                result = "disabled",
            )
            return
        }

        // Persist a second recovery path before trying Headless JS. If Android
        // declines the direct background service start, WorkManager will run a
        // delayed heartbeat and request recovery only when sync is still down.
        enqueueFallbackHealthCheck(applicationContext)

        try {
            val headlessTaskIntent = Intent(
                applicationContext,
                HeadlessTaskService::class.java,
            ).apply {
                putExtra("event", "BOOT_COMPLETED")
            }
            val component = applicationContext.startService(headlessTaskIntent)
            if (component == null) {
                RelayHealthStore.record(
                    applicationContext,
                    category = "recovery",
                    trigger = "boot_completed",
                    path = "headless_js",
                    result = "declined_fallback_scheduled",
                )
                return
            }

            HeadlessJsTaskService.acquireWakeLockNow(applicationContext)
            RelayHealthStore.record(
                applicationContext,
                category = "recovery",
                trigger = "boot_completed",
                path = "headless_js",
                result = "requested_with_fallback",
            )
        } catch (error: Exception) {
            Log.w(TAG, "Immediate boot recovery was blocked; fallback remains scheduled", error)
            RelayHealthStore.record(
                applicationContext,
                category = "recovery",
                trigger = "boot_completed",
                path = "headless_js",
                result = "blocked_fallback_scheduled",
            )
        }
    }

    private fun shouldRecoverAfterBoot(context: Context): Boolean {
        val storage = AsyncStorageBridge(context)
        return try {
            storage.getValue("relaunch_on_boot") == "true" &&
                storage.getValue("wsIsRunning") == "true"
        } catch (error: Exception) {
            Log.w(TAG, "Unable to read boot recovery settings", error)
            false
        } finally {
            storage.disconnect()
        }
    }

    private fun enqueueFallbackHealthCheck(context: Context) {
        try {
            val request = OneTimeWorkRequestBuilder<ScheduleService>()
                .setInitialDelay(FALLBACK_DELAY_SECONDS, TimeUnit.SECONDS)
                .addTag(BOOT_RECOVERY_WORK)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                BOOT_RECOVERY_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
            RelayHealthStore.record(
                context,
                category = "recovery",
                trigger = "boot_completed",
                path = "work_manager_fallback",
                result = "scheduled",
            )
        } catch (error: Exception) {
            Log.e(TAG, "Unable to schedule boot recovery fallback", error)
            RelayHealthStore.record(
                context,
                category = "recovery",
                trigger = "boot_completed",
                path = "work_manager_fallback",
                result = "schedule_failed",
            )
        }
    }
}
