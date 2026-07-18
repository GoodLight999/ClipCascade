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
        private const val MAX_NESTED_EXTRA_DEPTH = 2
        private val AUTH_HINT = Regex(
            "(?i)(otp|one[\\s-]?time|verification|security|authentication|auth|" +
                "login|log[\\s-]?in|sign[\\s-]?in|signin|passcode|pin|" +
                "認証|確認コード|ログイン|サインイン|ワンタイム|本人確認|" +
                "验证码|驗證碼|인증)",
        )
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
            } else if (joinedText.isBlank() || AUTH_HINT.containsMatchIn(joinedText)) {
                // Store only the failure class. Never persist notification text,
                // account identifiers, package names, or the candidate value.
                RelayHealthStore.record(
                    applicationContext,
                    category = "verification",
                    trigger = "notification_received",
                    path = "notification_extras",
                    result = if (joinedText.isBlank()) "empty" else "no_match",
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
        val parts = linkedSetOf<String>()
        notification.tickerText?.let { addPart(parts, it) }
        val extras = notification.extras ?: return parts.joinToString("\n")
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
            extras.getCharSequence(key)?.let { addPart(parts, it) }
        }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.forEach { addPart(parts, it) }

        addMessageTexts(extras, Notification.EXTRA_MESSAGES, parts)
        addMessageTexts(extras, Notification.EXTRA_HISTORIC_MESSAGES, parts)
        addLooseTextExtras(extras, parts, depth = 0)
        notification.publicVersion?.tickerText?.let { addPart(parts, it) }
        notification.publicVersion?.extras?.let {
            addLooseTextExtras(it, parts, depth = 0)
        }
        return parts.joinToString("\n")
    }

    private fun addLooseTextExtras(
        extras: Bundle,
        parts: MutableSet<String>,
        depth: Int,
    ) {
        if (depth > MAX_NESTED_EXTRA_DEPTH) return
        extras.keySet().sorted().forEach { key ->
            val value = try {
                extras.get(key)
            } catch (error: Exception) {
                null
            } ?: return@forEach
            when (value) {
                is CharSequence -> addPart(parts, value)
                is Bundle -> addLooseTextExtras(value, parts, depth + 1)
                is Array<*> -> value.forEach { item -> addLooseExtraValue(item, parts, depth) }
                is ArrayList<*> -> value.forEach { item -> addLooseExtraValue(item, parts, depth) }
            }
        }
    }

    private fun addLooseExtraValue(
        value: Any?,
        parts: MutableSet<String>,
        depth: Int,
    ) {
        when (value) {
            is CharSequence -> addPart(parts, value)
            is Bundle -> addLooseTextExtras(value, parts, depth + 1)
        }
    }

    private fun addPart(parts: MutableSet<String>, value: CharSequence) {
        value.toString()
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.take(4096)
            ?.let(parts::add)
    }

    private fun addMessageTexts(
        extras: Bundle,
        key: String,
        parts: MutableSet<String>,
    ) {
        extras.getParcelableArray(key)?.forEach { parcelable ->
            val message = parcelable as? Bundle ?: return@forEach
            message.getCharSequence("text")?.let { addPart(parts, it) }
        }
    }
}
