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
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ScheduleService"
        private const val NOTIFICATION_CHANNEL_ID =
            "clipcascade_foreground_service_stopped_running"
        private const val NOTIFICATION_ID = 1
        private const val LIVENESS_POLL_INTERVAL_MS = 100L
        private const val LIVENESS_POLL_ATTEMPTS = 35

        fun removeNotificationIfPresent(context: Context) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        }

        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }
    }

    override suspend fun doWork(): Result {
        return try {
            if (hasNotificationPermission(applicationContext)) {
                val bridgeData = AsyncStorageBridge(applicationContext)
                if (enableForegroundService(bridgeData)) {
                    if (!foregroundServiceIsActive(bridgeData)) {
                        showNotificationIfNotPresent()
                    } else {
                        removeNotificationIfPresent(applicationContext)
                    }
                }
            }
            Result.success()
        } catch (error: Exception) {
            Log.e(TAG, "Error running worker", error)
            Result.failure()
        }
    }

    fun enableForegroundService(bridgeData: AsyncStorageBridge): Boolean {
        return bridgeData.getValue("wsIsRunning")?.toBoolean() ?: false
    }

    suspend fun foregroundServiceIsActive(bridgeData: AsyncStorageBridge): Boolean {
        // Existing AsyncStorage ping/pong protocol. The 3.5-second budget spans
        // multiple one-second JavaScript status-poller iterations.
        bridgeData.setValue("echo", "ping")
        repeat(LIVENESS_POLL_ATTEMPTS) {
            delay(LIVENESS_POLL_INTERVAL_MS)
            if (bridgeData.getValue("echo") == "pong") {
                return true
            }
        }
        return false
    }

    private fun showNotificationIfNotPresent() {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "ClipCascade Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        if (!isNotificationActive(notificationManager)) {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                action = "com.clipcascade.NOTIFICATION_ACTION"
                putExtra("action", "foreground_service_stopped_running")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(
                applicationContext,
                NOTIFICATION_CHANNEL_ID
            )
                .setSmallIcon(R.drawable.ic_notification_failure)
                .setContentTitle("ClipCascade Service Inactive")
                .setContentText("ClipCascade monitoring is inactive. Tap to restart.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun isNotificationActive(notificationManager: NotificationManager): Boolean =
        notificationManager.activeNotifications.any { it.id == NOTIFICATION_ID }
}
