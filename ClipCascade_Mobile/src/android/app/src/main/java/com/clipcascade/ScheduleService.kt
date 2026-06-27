// android\app\src\main\java\com\clipcascade\ScheduleService.kt
package com.clipcascade

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

class ScheduleService(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ScheduleService"
        private const val NOTIFICATION_CHANNEL_ID =
            "clipcascade_foreground_service_stopped_running"
        private const val NOTIFICATION_ID = 1

        fun removeNotificationIfPresent(context: Context) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID)
        }

        fun hasNotificationPermission(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
    }

    override suspend fun doWork(): Result {
        val bridge = AsyncStorageBridge(applicationContext)
        return try {
            if (!enableForegroundService(bridge)) {
                removeNotificationIfPresent(applicationContext)
                Result.success()
            } else if (foregroundServiceIsActive(bridge)) {
                removeNotificationIfPresent(applicationContext)
                Result.success()
            } else {
                Log.w(TAG, "Foreground sync service did not answer its heartbeat")
                if (hasNotificationPermission(applicationContext)) {
                    showNotificationIfNotPresent()
                }
                Result.success()
            }
        } catch (error: Exception) {
            Log.e(TAG, "Error running worker", error)
            Result.retry()
        } finally {
            bridge.disconnect()
        }
    }

    private fun enableForegroundService(bridge: AsyncStorageBridge): Boolean =
        bridge.getValue("wsIsRunning")?.toBoolean() ?: false

    private suspend fun foregroundServiceIsActive(
        bridge: AsyncStorageBridge,
    ): Boolean {
        bridge.setValue("echo", "ping")
        repeat(35) {
            delay(100)
            if (bridge.getValue("echo") == "pong") return true
        }
        return false
    }

    private fun showNotificationIfNotPresent() {
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "ClipCascade Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }

        if (isNotificationActive(manager)) return

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = "com.clipcascade.NOTIFICATION_ACTION"
            putExtra("action", "foreground_service_stopped_running")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(
            applicationContext,
            NOTIFICATION_CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_notification_failure)
            .setContentTitle("ClipCascade Service Inactive")
            .setContentText("ClipCascade monitoring is inactive. Tap to restart.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun isNotificationActive(manager: NotificationManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return manager.activeNotifications.any { it.id == NOTIFICATION_ID }
    }
}
