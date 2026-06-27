package com.clipcascade

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationCodeListenerService : NotificationListenerService() {
    companion object {
        private const val TAG = "NotificationCodeListener"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        OtpRelayDispatcher.schedule(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
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
        val title = notification.extras
            ?.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            .orEmpty()

        val item = OtpRelayStore.Item(
            id = "${posted.packageName}:${posted.key}:${posted.postTime}:$value",
            code = value,
            sourcePackage = posted.packageName,
            sourceTitle = title,
            createdAt = System.currentTimeMillis(),
        )

        if (OtpRelayStore.enqueue(applicationContext, item)) {
            OtpRelayDispatcher.schedule(applicationContext)
        }
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
