const fs = require('fs');
const path = require('path');

function replaceRequired(source, before, after, label) {
  if (!source.includes(before)) {
    throw new Error(`Expected ${label} text was not found`);
  }
  return source.replace(before, after);
}

const root = path.resolve(__dirname, '..');
const javaDir = path.join(root, 'android', 'app', 'src', 'main', 'java', 'com', 'clipcascade');
const testDir = path.join(root, 'android', 'app', 'src', 'test', 'java', 'com', 'clipcascade');
const manifestPath = path.join(root, 'android', 'app', 'src', 'main', 'AndroidManifest.xml');

const listenerSource = String.raw`package com.clipcascade

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.UUID

class NotificationCodeListenerService : NotificationListenerService() {
    companion object {
        private const val TAG = "NotificationCodeListener"
        private const val MAX_NESTED_EXTRA_DEPTH = 4
        private const val MAX_TEXT_PARTS = 128
        private const val MAX_TOTAL_TEXT_CHARS = 32_768
        private const val MAX_ACTIVE_SCAN_ITEMS = 64
        private const val MAX_ACTIVE_SCAN_AGE_MS = 15 * 60_000L
        private val AUTH_HINT = Regex(
            "(?i)(otp|one[\\s-]?time|verification|security|authentication|auth|" +
                "login|log[\\s-]?in|sign[\\s-]?in|signin|passcode|pin|code|" +
                "認証|確認コード|ログイン|サインイン|ワンタイム|本人確認|" +
                "验证码|驗證碼|인증)",
        )

        @Volatile
        private var activeInstance: NotificationCodeListenerService? = null

        fun ensureBound(context: android.content.Context, reason: String): Boolean {
            if (activeInstance != null) return true
            NotificationListenerRuntimeStore.markBindingRequested(context.applicationContext, reason)
            return try {
                requestRebind(ComponentName(context, NotificationCodeListenerService::class.java))
                false
            } catch (error: Exception) {
                Log.w(TAG, "Unable to request notification listener rebind", error)
                false
            }
        }

        fun requestImmediateScan(context: android.content.Context, reason: String): Boolean {
            val active = activeInstance
            if (active != null) {
                active.scanActiveNotifications(reason)
                return true
            }
            ensureBound(context, reason)
            return false
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeInstance = this
        NotificationListenerRuntimeStore.markConnected(applicationContext)
        RelayHealthStore.record(
            applicationContext,
            category = "verification",
            trigger = "listener_connected",
            path = "notification_access",
            result = "ready",
        )
        scanActiveNotifications("listener_connected")
        OtpRelayDispatcher.schedule(applicationContext)
    }

    override fun onListenerDisconnected() {
        activeInstance = null
        NotificationListenerRuntimeStore.markDisconnected(applicationContext)
        super.onListenerDisconnected()
        ensureBound(applicationContext, "listener_disconnected")
    }

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        processNotification(sbn ?: return, "live")
    }

    private fun scanActiveNotifications(origin: String) {
        mainHandler.post {
            val now = System.currentTimeMillis()
            val candidates = try {
                activeNotifications
                    ?.asSequence()
                    ?.filter { it.postTime > 0L && now - it.postTime in 0..MAX_ACTIVE_SCAN_AGE_MS }
                    ?.sortedByDescending { it.postTime }
                    ?.take(MAX_ACTIVE_SCAN_ITEMS)
                    ?.toList()
                    .orEmpty()
            } catch (error: Exception) {
                Log.w(TAG, "Unable to scan active notifications", error)
                emptyList()
            }
            NotificationListenerRuntimeStore.recordActiveScan(
                applicationContext,
                origin,
                candidates.size,
            )
            candidates.forEach { processNotification(it, "active_scan") }
        }
    }

    private fun processNotification(posted: StatusBarNotification, origin: String) {
        NotificationListenerRuntimeStore.recordSeen(applicationContext, origin)
        if (!RelaySettingsStore.codeRelayEnabled(this)) {
            NotificationListenerRuntimeStore.recordStage(applicationContext, "relay_disabled", origin)
            return
        }
        val notification = posted.notification ?: return
        val selfComponentTest = posted.packageName == packageName &&
            OtpTestNotificationManager.isSyntheticTest(notification)
        if (selfComponentTest) {
            NotificationListenerRuntimeStore.recordStage(
                applicationContext,
                "component_test_direct_queue",
                origin,
            )
            return
        }
        if (posted.packageName == packageName) {
            NotificationListenerRuntimeStore.recordStage(applicationContext, "self_filtered", origin)
            return
        }

        val expectedExternal = ExternalNotificationTestStore.expected(applicationContext)
        val externalListenerTest = expectedExternal.isNotBlank() &&
            ExternalNotificationTestStore.isTrustedHelper(applicationContext, posted.packageName)
        val selectedApps = RelaySettingsStore.selectedApps(this)
        if (!externalListenerTest && selectedApps.isNotEmpty() && posted.packageName !in selectedApps) {
            NotificationListenerRuntimeStore.recordFiltered(applicationContext, origin)
            return
        }
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if ((notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return

        NotificationListenerRuntimeStore.recordEligible(
            applicationContext,
            origin,
            (System.currentTimeMillis() - posted.postTime)
                .coerceAtLeast(0L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt(),
            externalListenerTest,
        )

        val collected = collectNotificationText(notification)
        NotificationListenerRuntimeStore.recordTextCollection(
            applicationContext,
            origin,
            collected.partCount,
            collected.text.length,
        )
        val value = OtpCodeExtractor.extract(collected.text)
        if (value == null || (externalListenerTest && value != expectedExternal)) {
            val hasAuthHint = AUTH_HINT.containsMatchIn(collected.text)
            NotificationListenerRuntimeStore.recordNoMatch(
                applicationContext,
                origin,
                collected.text.isBlank(),
                hasAuthHint,
            )
            if (externalListenerTest) {
                OtpTestStatusStore.extractionFailed(applicationContext, expectedExternal)
            }
            return
        }
        if (externalListenerTest) OtpTestStatusStore.detected(applicationContext, value)

        if (!NotificationDeliveryReceiptStore.claimIfNew(applicationContext, posted, value)) {
            NotificationListenerRuntimeStore.recordAlreadyProcessed(applicationContext, origin)
            return
        }
        val relayId = if (externalListenerTest) {
            OtpTestStatusStore.RELAY_ID_PREFIX + UUID.randomUUID().toString()
        } else {
            UUID.randomUUID().toString()
        }
        val item = OtpRelayStore.Item(relayId, value, System.currentTimeMillis())
        val queued = try {
            OtpRelayStore.enqueue(applicationContext, item)
        } catch (error: Exception) {
            NotificationDeliveryReceiptStore.releaseClaim(applicationContext, posted, value)
            Log.w(TAG, "Unable to enqueue verification value", error)
            return
        }
        if (queued) {
            if (externalListenerTest) {
                OtpTestStatusStore.queued(applicationContext, value, relayId)
                ExternalNotificationTestStore.clear(applicationContext)
            }
            OtpRelayDispatcher.schedule(applicationContext)
            NotificationListenerRuntimeStore.recordQueued(applicationContext, origin)
        } else {
            if (externalListenerTest) OtpTestStatusStore.deduplicated(applicationContext, value)
            NotificationListenerRuntimeStore.recordDeduplicated(applicationContext, origin)
        }
        RelayHealthStore.record(
            applicationContext,
            category = "verification",
            trigger = if (externalListenerTest) "external_listener_test" else "context_match",
            path = "notification_extras",
            result = if (queued) "queued" else "deduplicated",
        )
    }

    private data class CollectedNotificationText(val text: String, val partCount: Int)

    private fun collectNotificationText(notification: Notification): CollectedNotificationText {
        val parts = linkedSetOf<String>()
        notification.tickerText?.let { addPart(parts, it) }
        notification.extras?.let { addNotificationExtras(it, parts) }
        notification.publicVersion?.let { publicNotification ->
            publicNotification.tickerText?.let { addPart(parts, it) }
            publicNotification.extras?.let { addNotificationExtras(it, parts) }
        }
        return CollectedNotificationText(
            parts.joinToString("\n").take(MAX_TOTAL_TEXT_CHARS),
            parts.size,
        )
    }

    private fun addNotificationExtras(extras: Bundle, parts: MutableSet<String>) {
        listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_INFO_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_CONVERSATION_TITLE,
            Notification.EXTRA_REMOTE_INPUT_HISTORY,
        ).forEach { key ->
            extras.getCharSequence(key)?.let { addPart(parts, it) }
            extras.getCharSequenceArray(key)?.forEach { addPart(parts, it) }
        }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { addPart(parts, it) }
        addMessageTexts(extras, Notification.EXTRA_MESSAGES, parts)
        addMessageTexts(extras, Notification.EXTRA_HISTORIC_MESSAGES, parts)
        addLooseTextExtras(extras, parts, 0)
    }

    private fun addLooseTextExtras(extras: Bundle, parts: MutableSet<String>, depth: Int) {
        if (depth > MAX_NESTED_EXTRA_DEPTH || parts.size >= MAX_TEXT_PARTS) return
        extras.keySet().sorted().forEach { key ->
            if (parts.size >= MAX_TEXT_PARTS) return
            val value = try {
                @Suppress("DEPRECATION")
                extras.get(key)
            } catch (_: Exception) {
                null
            } ?: return@forEach
            addLooseExtraValue(value, parts, depth)
        }
    }

    private fun addLooseExtraValue(value: Any?, parts: MutableSet<String>, depth: Int) {
        if (parts.size >= MAX_TEXT_PARTS) return
        when (value) {
            is CharSequence -> addPart(parts, value)
            is Bundle -> addLooseTextExtras(value, parts, depth + 1)
            is Array<*> -> value.forEach { addLooseExtraValue(it, parts, depth + 1) }
            is ArrayList<*> -> value.forEach { addLooseExtraValue(it, parts, depth + 1) }
            is android.util.SparseArray<*> -> {
                for (index in 0 until value.size()) {
                    addLooseExtraValue(value.valueAt(index), parts, depth + 1)
                }
            }
        }
    }

    private fun addPart(parts: MutableSet<String>, value: CharSequence) {
        if (parts.size >= MAX_TEXT_PARTS) return
        value.toString()
            .replace('\u00A0', ' ')
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.take(4096)
            ?.let(parts::add)
    }

    private fun addMessageTexts(extras: Bundle, key: String, parts: MutableSet<String>) {
        extras.getParcelableArray(key)?.forEach { parcelable ->
            val message = parcelable as? Bundle ?: return@forEach
            message.getCharSequence("text")?.let { addPart(parts, it) }
            message.getCharSequence("sender")?.let { addPart(parts, it) }
            val person = if (Build.VERSION.SDK_INT >= 33) {
                message.getParcelable("sender_person", android.app.Person::class.java)
            } else {
                @Suppress("DEPRECATION")
                message.getParcelable<android.app.Person>("sender_person")
            }
            person?.name?.let { addPart(parts, it) }
        }
    }
}
`;
fs.writeFileSync(path.join(javaDir, 'NotificationCodeListenerService.kt'), listenerSource, 'utf8');


console.log('Prepared alpha.22 external-package NotificationListener path.');
