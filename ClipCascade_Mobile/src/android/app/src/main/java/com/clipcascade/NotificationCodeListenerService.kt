package com.clipcascade

import android.app.Notification
import android.content.ComponentName
import android.os.Bundle
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

        val notification = posted.notification ?: return
        val syntheticTest = OtpTestNotificationManager.isSyntheticTest(notification)
        if (posted.packageName == packageName && !syntheticTest) return

        val selectedApps = RelaySettingsStore.selectedApps(this)
        if (
            !syntheticTest &&
            selectedApps.isNotEmpty() &&
            posted.packageName !in selectedApps
        ) {
            return
        }

        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if ((notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return

        val expected = if (syntheticTest) {
            OtpTestNotificationManager.expectedValue(notification)
        } else {
            ""
        }
        val joinedText = collectNotificationText(notification)
        val value = OtpCodeExtractor.extract(joinedText)
        if (value == null || (syntheticTest && expected.isNotBlank() && value != expected)) {
            if (syntheticTest) {
                OtpTestStatusStore.extractionFailed(applicationContext, expected)
                RelayHealthStore.record(
                    applicationContext,
                    category = "verification",
                    trigger = "test_notification",
                    path = "local_extractor",
                    result = "interrupted",
                )
            }
            return
        }

        if (syntheticTest) {
            OtpTestStatusStore.detected(applicationContext, value)
        }

        val relayId = if (syntheticTest) {
            OtpTestStatusStore.RELAY_ID_PREFIX + UUID.randomUUID().toString()
        } else {
            UUID.randomUUID().toString()
        }
        val item = OtpRelayStore.Item(
            id = relayId,
            code = value,
            createdAt = System.currentTimeMillis(),
        )

        val queued = OtpRelayStore.enqueue(applicationContext, item)
        if (queued) {
            if (syntheticTest) {
                OtpTestStatusStore.queued(applicationContext, value, relayId)
            }
            OtpRelayDispatcher.schedule(applicationContext)
        } else if (syntheticTest) {
            OtpTestStatusStore.deduplicated(applicationContext, value)
        }
        RelayHealthStore.record(
            applicationContext,
            category = "verification",
            trigger = if (syntheticTest) "test_notification" else "context_match",
            path = "local_extractor",
            result = if (queued) "queued" else "deduplicated",
        )
    }

    private fun collectNotificationText(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val parts = linkedSetOf<String>()
        listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_INFO_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_CONVERSATION_TITLE,
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

        addMessageTexts(extras, Notification.EXTRA_MESSAGES, parts)
        addMessageTexts(extras, Notification.EXTRA_HISTORIC_MESSAGES, parts)
        return parts.joinToString("\n")
    }

    private fun addMessageTexts(
        extras: Bundle,
        key: String,
        parts: MutableSet<String>,
    ) {
        extras.getParcelableArray(key)?.forEach { parcelable ->
            val message = parcelable as? Bundle ?: return@forEach
            message.getCharSequence("text")
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(parts::add)
        }
    }
}
