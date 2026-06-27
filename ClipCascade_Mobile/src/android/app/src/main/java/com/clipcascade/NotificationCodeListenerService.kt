package com.clipcascade

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.UUID

class NotificationCodeListenerService : NotificationListenerService() {
    companion object {
        private const val TAG = "NotificationCodeListener"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        RelayHealthStore.record(
            applicationContext,
            category = "verification",
            trigger = "listener_connected",
            path = "notification_access",
            result = "ready",
        )
        OtpRelayDispatcher.schedule(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        RelayHealthStore.record(
            applicationContext,
            category = "verification",
            trigger = "listener_disconnected",
            path = "notification_access",
            result = "rebind_requested",
        )
        try {
            requestRebind(
                ComponentName(applicationContext, NotificationCodeListenerService::class.java),
            )
        } catch (error: Exception) {
            Log.w(TAG, "Unable to request notification-listener rebind", error)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (!RelaySettingsStore.codeRelayEnabled(this)) return
        if (posted.packageName == packageName) return

        val selectedApps = RelaySettingsStore.selectedApps(this)
        if (selectedApps.isNotEmpty() && posted.packageName !in selectedApps) return

        val notification = posted.notification ?: return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if ((notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return

        val joinedText = collectNotificationText(notification)
        val value = OtpCodeExtractor.extract(joinedText) ?: return

        val item = OtpRelayStore.Item(
            id = UUID.randomUUID().toString(),
            code = value,
            createdAt = System.currentTimeMillis(),
        )

        val queued = OtpRelayStore.enqueue(applicationContext, item)
        if (queued) {
            OtpRelayDispatcher.schedule(applicationContext)
        }
        RelayHealthStore.record(
            applicationContext,
            category = "verification",
            trigger = "context_match",
            path = "local_extractor",
            result = if (queued) "queued" else "deduplicated",
        )
    }

    private fun collectNotificationText(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val parts = linkedSetOf<String>()
        listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_INFO_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
            Notification.EXTRA_BIG_TEXT,
        ).forEach { key ->
            extras.getCharSequence(key)
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(parts::add)
        }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map { it.toString().trim() }
            ?.filter { it.isNotEmpty() }
            ?.forEach(parts::add)
        return parts.joinToString("\n")
    }
}
