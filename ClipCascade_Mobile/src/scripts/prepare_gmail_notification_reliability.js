const fs = require('fs');
const path = require('path');

function replaceRequired(source, before, after, label) {
  if (!source.includes(before)) {
    throw new Error(`Expected ${label} text was not found`);
  }
  return source.replace(before, after);
}

const javaDir = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'main',
  'java',
  'com',
  'clipcascade',
);

const listenerSource = String.raw`package com.clipcascade

import android.app.Notification
import android.content.ComponentName
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
            NotificationListenerRuntimeStore.markBindingRequested(
                context.applicationContext,
                origin = reason,
            )
            return try {
                NotificationListenerService.requestRebind(
                    ComponentName(
                        context.applicationContext,
                        NotificationCodeListenerService::class.java,
                    ),
                )
                false
            } catch (error: Exception) {
                Log.w(TAG, "Unable to request notification-listener rebind", error)
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
        RelayHealthStore.record(
            applicationContext,
            category = "verification",
            trigger = "listener_disconnected",
            path = "notification_access",
            result = "rebind_requested",
        )
        ensureBound(applicationContext, "listener_disconnected")
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        processNotification(sbn ?: return, origin = "live")
    }

    private fun scanActiveNotifications(origin: String) {
        mainHandler.post {
            val now = System.currentTimeMillis()
            val candidates = try {
                activeNotifications
                    ?.asSequence()
                    ?.filter { posted ->
                        posted.postTime > 0L && now - posted.postTime in 0..MAX_ACTIVE_SCAN_AGE_MS
                    }
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
                origin = origin,
                candidateCount = candidates.size,
            )
            candidates.forEach { processNotification(it, origin = "active_scan") }
        }
    }

    private fun processNotification(posted: StatusBarNotification, origin: String) {
        NotificationListenerRuntimeStore.recordSeen(applicationContext, origin)

        if (!RelaySettingsStore.codeRelayEnabled(this)) {
            NotificationListenerRuntimeStore.recordStage(applicationContext, "relay_disabled", origin)
            return
        }

        val notification = posted.notification ?: run {
            NotificationListenerRuntimeStore.recordStage(applicationContext, "notification_missing", origin)
            return
        }
        val syntheticTest = OtpTestNotificationManager.isSyntheticTest(notification)
        if (posted.packageName == packageName && !syntheticTest) {
            NotificationListenerRuntimeStore.recordStage(applicationContext, "self_filtered", origin)
            return
        }

        val selectedApps = RelaySettingsStore.selectedApps(this)
        if (
            !syntheticTest &&
            selectedApps.isNotEmpty() &&
            posted.packageName !in selectedApps
        ) {
            NotificationListenerRuntimeStore.recordFiltered(applicationContext, origin)
            return
        }

        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            NotificationListenerRuntimeStore.recordStage(applicationContext, "ongoing_filtered", origin)
            return
        }
        if ((notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) {
            NotificationListenerRuntimeStore.recordStage(applicationContext, "foreground_filtered", origin)
            return
        }

        val expected = if (syntheticTest) {
            OtpTestNotificationManager.expectedValue(notification)
        } else {
            ""
        }
        val collected = collectNotificationText(notification)
        NotificationListenerRuntimeStore.recordTextCollection(
            applicationContext,
            origin = origin,
            partCount = collected.partCount,
            totalChars = collected.text.length,
        )
        val value = OtpCodeExtractor.extract(collected.text)
        if (value == null || (syntheticTest && expected.isNotBlank() && value != expected)) {
            val hasAuthHint = AUTH_HINT.containsMatchIn(collected.text)
            NotificationListenerRuntimeStore.recordNoMatch(
                applicationContext,
                origin = origin,
                empty = collected.text.isBlank(),
                authHint = hasAuthHint,
            )
            if (syntheticTest) {
                OtpTestStatusStore.extractionFailed(applicationContext, expected)
                RelayHealthStore.record(
                    applicationContext,
                    category = "verification",
                    trigger = "test_notification",
                    path = "local_extractor",
                    result = "interrupted",
                )
            } else if (collected.text.isBlank() || hasAuthHint) {
                RelayHealthStore.record(
                    applicationContext,
                    category = "verification",
                    trigger = "notification_received",
                    path = "notification_extras",
                    result = if (collected.text.isBlank()) "empty" else "no_match",
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
            NotificationListenerRuntimeStore.recordQueued(applicationContext, origin)
        } else {
            if (syntheticTest) {
                OtpTestStatusStore.deduplicated(applicationContext, value)
            }
            NotificationListenerRuntimeStore.recordDeduplicated(applicationContext, origin)
        }
        RelayHealthStore.record(
            applicationContext,
            category = "verification",
            trigger = if (syntheticTest) "test_notification" else "context_match",
            path = "local_extractor",
            result = if (queued) "queued" else "deduplicated",
        )
    }

    private data class CollectedNotificationText(
        val text: String,
        val partCount: Int,
    )

    private fun collectNotificationText(notification: Notification): CollectedNotificationText {
        val parts = linkedSetOf<String>()
        notification.tickerText?.let { addPart(parts, it) }
        notification.extras?.let { addNotificationExtras(it, parts) }
        notification.publicVersion?.let { publicNotification ->
            publicNotification.tickerText?.let { addPart(parts, it) }
            publicNotification.extras?.let { addNotificationExtras(it, parts) }
        }
        return CollectedNotificationText(
            text = parts.joinToString("\n").take(MAX_TOTAL_TEXT_CHARS),
            partCount = parts.size,
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
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.forEach { addPart(parts, it) }
        addMessageTexts(extras, Notification.EXTRA_MESSAGES, parts)
        addMessageTexts(extras, Notification.EXTRA_HISTORIC_MESSAGES, parts)
        addLooseTextExtras(extras, parts, depth = 0)
    }

    private fun addLooseTextExtras(
        extras: Bundle,
        parts: MutableSet<String>,
        depth: Int,
    ) {
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

    private fun addLooseExtraValue(
        value: Any?,
        parts: MutableSet<String>,
        depth: Int,
    ) {
        if (parts.size >= MAX_TEXT_PARTS) return
        when (value) {
            is CharSequence -> addPart(parts, value)
            is Bundle -> addLooseTextExtras(value, parts, depth + 1)
            is Array<*> -> value.forEach { item -> addLooseExtraValue(item, parts, depth + 1) }
            is ArrayList<*> -> value.forEach { item -> addLooseExtraValue(item, parts, depth + 1) }
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

    private fun addMessageTexts(
        extras: Bundle,
        key: String,
        parts: MutableSet<String>,
    ) {
        extras.getParcelableArray(key)?.forEach { parcelable ->
            val message = parcelable as? Bundle ?: return@forEach
            message.getCharSequence("text")?.let { addPart(parts, it) }
            message.getCharSequence("sender")?.let { addPart(parts, it) }
            val senderPerson = if (android.os.Build.VERSION.SDK_INT >= 33) {
                message.getParcelable("sender_person", android.app.Person::class.java)
            } else {
                @Suppress("DEPRECATION")
                message.getParcelable<android.app.Person>("sender_person")
            }
            senderPerson?.name?.let { addPart(parts, it) }
        }
    }
}
`;
fs.writeFileSync(
  path.join(javaDir, 'NotificationCodeListenerService.kt'),
  listenerSource,
  'utf8',
);

const runtimeStoreSource = String.raw`package com.clipcascade

import android.content.Context

object NotificationListenerRuntimeStore {
    private const val PREFS = "notification_listener_runtime"

    data class Snapshot(
        val connected: Boolean,
        val lastConnectedAt: Long,
        val lastNotificationAt: Long,
        val lastStage: String,
        val lastOrigin: String,
        val seenCount: Int,
        val textAvailableCount: Int,
        val authHintCount: Int,
        val queuedCount: Int,
        val deduplicatedCount: Int,
        val noMatchCount: Int,
        val emptyCount: Int,
        val filteredCount: Int,
        val activeScanCount: Int,
        val lastPartCount: Int,
        val lastTextChars: Int,
    )

    @Synchronized
    fun markConnected(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("connected", true)
            .putLong("last_connected_at", System.currentTimeMillis())
            .putString("last_stage", "connected")
            .putString("last_origin", "listener")
            .commit()
    }

    @Synchronized
    fun markDisconnected(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("connected", false)
            .putString("last_stage", "disconnected")
            .putString("last_origin", "listener")
            .commit()
    }

    @Synchronized
    fun markBindingRequested(context: Context, origin: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("connected", false)
            .putString("last_stage", "rebind_requested")
            .putString("last_origin", origin)
            .commit()
    }

    @Synchronized
    fun recordSeen(context: Context, origin: String) {
        increment(context, "seen_count", "seen", origin, markNotification = true)
    }

    @Synchronized
    fun recordFiltered(context: Context, origin: String) {
        increment(context, "filtered_count", "source_filtered", origin)
    }

    @Synchronized
    fun recordTextCollection(
        context: Context,
        origin: String,
        partCount: Int,
        totalChars: Int,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putInt("last_part_count", partCount)
            .putInt("last_text_chars", totalChars)
            .putString("last_stage", if (totalChars > 0) "text_collected" else "text_empty")
            .putString("last_origin", origin)
        if (totalChars > 0) {
            editor.putInt("text_available_count", prefs.getInt("text_available_count", 0) + 1)
        }
        editor.commit()
    }

    @Synchronized
    fun recordNoMatch(
        context: Context,
        origin: String,
        empty: Boolean,
        authHint: Boolean,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString("last_stage", if (empty) "empty" else if (authHint) "auth_no_match" else "no_auth_context")
            .putString("last_origin", origin)
        if (empty) {
            editor.putInt("empty_count", prefs.getInt("empty_count", 0) + 1)
        } else if (authHint) {
            editor.putInt("auth_hint_count", prefs.getInt("auth_hint_count", 0) + 1)
            editor.putInt("no_match_count", prefs.getInt("no_match_count", 0) + 1)
        }
        editor.commit()
    }

    @Synchronized
    fun recordQueued(context: Context, origin: String) {
        increment(context, "queued_count", "queued", origin)
    }

    @Synchronized
    fun recordDeduplicated(context: Context, origin: String) {
        increment(context, "deduplicated_count", "deduplicated", origin)
    }

    @Synchronized
    fun recordActiveScan(context: Context, origin: String, candidateCount: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("active_scan_count", prefs.getInt("active_scan_count", 0) + 1)
            .putInt("last_active_scan_candidates", candidateCount)
            .putString("last_stage", "active_scan")
            .putString("last_origin", origin)
            .commit()
    }

    @Synchronized
    fun recordStage(context: Context, stage: String, origin: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("last_stage", stage)
            .putString("last_origin", origin)
            .commit()
    }

    @Synchronized
    fun read(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            connected = prefs.getBoolean("connected", false),
            lastConnectedAt = prefs.getLong("last_connected_at", 0L),
            lastNotificationAt = prefs.getLong("last_notification_at", 0L),
            lastStage = prefs.getString("last_stage", "never_connected").orEmpty(),
            lastOrigin = prefs.getString("last_origin", "none").orEmpty(),
            seenCount = prefs.getInt("seen_count", 0),
            textAvailableCount = prefs.getInt("text_available_count", 0),
            authHintCount = prefs.getInt("auth_hint_count", 0),
            queuedCount = prefs.getInt("queued_count", 0),
            deduplicatedCount = prefs.getInt("deduplicated_count", 0),
            noMatchCount = prefs.getInt("no_match_count", 0),
            emptyCount = prefs.getInt("empty_count", 0),
            filteredCount = prefs.getInt("filtered_count", 0),
            activeScanCount = prefs.getInt("active_scan_count", 0),
            lastPartCount = prefs.getInt("last_part_count", 0),
            lastTextChars = prefs.getInt("last_text_chars", 0),
        )
    }

    @Synchronized
    fun clear(context: Context) {
        val connected = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("connected", false)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("connected", connected)
            .commit()
    }

    private fun increment(
        context: Context,
        counter: String,
        stage: String,
        origin: String,
        markNotification: Boolean = false,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putInt(counter, prefs.getInt(counter, 0) + 1)
            .putString("last_stage", stage)
            .putString("last_origin", origin)
        if (markNotification) {
            editor.putLong("last_notification_at", System.currentTimeMillis())
        }
        editor.commit()
    }
}
`;
fs.writeFileSync(
  path.join(javaDir, 'NotificationListenerRuntimeStore.kt'),
  runtimeStoreSource,
  'utf8',
);

const debugNotifierSource = String.raw`package com.clipcascade

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object RelayDebugNotifier {
    private const val CHANNEL_ID = "clipcascade_outbound_debug"
    private const val NOTIFICATION_ID = 4108

    fun showIfEnabled(context: Context, source: String, mode: String) {
        val applicationContext = context.applicationContext
        if (!RelaySettingsStore.outboundDebugNotificationEnabled(applicationContext)) return
        if (!SetupPermissionHelper.notificationPermissionGranted(applicationContext)) return

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.outbound_debug_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = applicationContext.getString(R.string.outbound_debug_channel_description)
                },
            )
        }
        val sourceLabel = when (source) {
            "notification_code" -> applicationContext.getString(R.string.outbound_debug_source_otp)
            "accessibility_clipboard" -> applicationContext.getString(R.string.outbound_debug_source_clipboard)
            else -> applicationContext.getString(R.string.outbound_debug_source_manual)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_small_icon)
            .setContentTitle(applicationContext.getString(R.string.outbound_debug_notification_title))
            .setContentText(
                applicationContext.getString(
                    R.string.outbound_debug_notification_body,
                    sourceLabel,
                    mode.ifBlank { "unknown" },
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setTimeoutAfter(8_000L)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
`;
fs.writeFileSync(
  path.join(javaDir, 'RelayDebugNotifier.kt'),
  debugNotifierSource,
  'utf8',
);

const settingsStorePath = path.join(javaDir, 'RelaySettingsStore.kt');
let settingsStore = fs.readFileSync(settingsStorePath, 'utf8');
settingsStore = replaceRequired(
  settingsStore,
  `    fun codeRelayEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getBoolean("code_relay_enabled", true)
`,
  `    fun codeRelayEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getBoolean("code_relay_enabled", true)

    fun outboundDebugNotificationEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getBoolean("outbound_debug_notification_enabled", false)

    fun setOutboundDebugNotificationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("outbound_debug_notification_enabled", enabled)
            .commit()
    }
`,
  'outbound debug notification settings',
);
fs.writeFileSync(settingsStorePath, settingsStore, 'utf8');

const nativePath = path.join(javaDir, 'NativeBridgeModule.kt');
let nativeSource = fs.readFileSync(nativePath, 'utf8');
nativeSource = replaceRequired(
  nativeSource,
  `    @ReactMethod
    fun clearCookies(promise: Promise) {
`,
  `    @ReactMethod
    fun showOutboundDebugNotification(source: String, mode: String, promise: Promise) {
        try {
            RelayDebugNotifier.showIfEnabled(reactApplicationContext, source, mode)
            promise.resolve(true)
        } catch (_: Exception) {
            promise.resolve(false)
        }
    }

    @ReactMethod
    fun clearCookies(promise: Promise) {
`,
  'native outbound debug notification method',
);
fs.writeFileSync(nativePath, nativeSource, 'utf8');

const servicePath = path.resolve(__dirname, '..', 'StartForegroundService.js');
let serviceSource = fs.readFileSync(servicePath, 'utf8');
const finalSendBefore = `        const sendClipBoard = async (
          clipContent,
          type_ = 'text',
          forceSend = false,
          relayId = null,
        ) => {
          if (server_mode === 'P2S') {
            return await sendClipBoardP2S(clipContent, type_, forceSend);
          } else if (server_mode === 'P2P') {
            return await sendClipBoardP2P(
              clipContent,
              type_,
              forceSend,
              relayId,
            );
          }
          return false;
        };`;
const finalSendAfter = `        const sendClipBoard = async (
          clipContent,
          type_ = 'text',
          forceSend = false,
          relayId = null,
          relaySource = 'manual',
        ) => {
          let accepted = false;
          if (server_mode === 'P2S') {
            accepted = await sendClipBoardP2S(clipContent, type_, forceSend);
          } else if (server_mode === 'P2P') {
            accepted = await sendClipBoardP2P(
              clipContent,
              type_,
              forceSend,
              relayId,
            );
          }
          if (
            accepted === true &&
            NativeBridgeModule?.showOutboundDebugNotification
          ) {
            try {
              await NativeBridgeModule.showOutboundDebugNotification(
                relaySource || 'manual',
                server_mode || 'unknown',
              );
            } catch (_) {
              // Debug UI must never change transport acceptance or ACK behavior.
            }
          }
          return accepted === true;
        };`;
serviceSource = replaceRequired(
  serviceSource,
  finalSendBefore,
  finalSendAfter,
  'final generic outbound send wrapper',
);
serviceSource = replaceRequired(
  serviceSource,
  `                Boolean(relayId),
                relayId || null,
              );`,
  `                Boolean(relayId),
                relayId || null,
                relaySource || 'manual',
              );`,
  'background relay debug source propagation',
);
fs.writeFileSync(servicePath, serviceSource, 'utf8');

const schedulePath = path.join(javaDir, 'ScheduleService.kt');
let scheduleSource = fs.readFileSync(schedulePath, 'utf8');
scheduleSource = replaceRequired(
  scheduleSource,
  `    override suspend fun doWork(): Result {
        val bridge = AsyncStorageBridge(applicationContext)
`,
  `    override suspend fun doWork(): Result {
        if (SetupPermissionHelper.notificationAccessEnabled(applicationContext)) {
            NotificationCodeListenerService.ensureBound(applicationContext, "work_manager")
        }
        val bridge = AsyncStorageBridge(applicationContext)
`,
  'notification-listener heartbeat recovery',
);
fs.writeFileSync(schedulePath, scheduleSource, 'utf8');

const activityPath = path.join(javaDir, 'RelaySettingsActivity.kt');
let activity = fs.readFileSync(activityPath, 'utf8');
activity = replaceRequired(
  activity,
  `    private lateinit var notificationStatus: TextView
    private lateinit var selectedAppsSummary: TextView
`,
  `    private lateinit var notificationStatus: TextView
    private lateinit var notificationRuntimeStatus: TextView
    private lateinit var selectedAppsSummary: TextView
`,
  'notification runtime status field',
);
activity = replaceRequired(
  activity,
  `        content.addView(bodyText(getString(R.string.notification_explanation)))

        content.addView(sectionTitle(getString(R.string.otp_apps_section)))
`,
  `        content.addView(bodyText(getString(R.string.notification_explanation)))
        notificationRuntimeStatus = bodyText("")
        content.addView(notificationRuntimeStatus)
        content.addView(Button(this).apply {
            text = getString(R.string.notification_rescan)
            setOnClickListener {
                val alreadyConnected = NotificationCodeListenerService.requestImmediateScan(
                    applicationContext,
                    "settings_manual",
                )
                Toast.makeText(
                    this@RelaySettingsActivity,
                    getString(
                        if (alreadyConnected) R.string.notification_rescan_started
                        else R.string.notification_rebind_requested,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
                updateStatus()
            }
        })

        content.addView(sectionTitle(getString(R.string.otp_apps_section)))
`,
  'notification rebind and active scan controls',
);
activity = replaceRequired(
  activity,
  `        content.addView(sectionTitle(getString(R.string.health_section)))
        healthStatus = bodyText("")
`,
  `        content.addView(sectionTitle(getString(R.string.outbound_debug_section)))
        content.addView(Switch(this).apply {
            text = getString(R.string.outbound_debug_enable)
            isChecked = RelaySettingsStore.outboundDebugNotificationEnabled(
                this@RelaySettingsActivity,
            )
            setOnCheckedChangeListener { _, enabled ->
                RelaySettingsStore.setOutboundDebugNotificationEnabled(
                    this@RelaySettingsActivity,
                    enabled,
                )
            }
        })
        content.addView(bodyText(getString(R.string.outbound_debug_body)))

        content.addView(sectionTitle(getString(R.string.health_section)))
        healthStatus = bodyText("")
`,
  'outbound debug notification switch',
);
activity = replaceRequired(
  activity,
  `                RelayHealthStore.clear(applicationContext)
                updateStatus()
`,
  `                RelayHealthStore.clear(applicationContext)
                NotificationListenerRuntimeStore.clear(applicationContext)
                updateStatus()
`,
  'notification runtime diagnostic clear',
);
activity = replaceRequired(
  activity,
  `    override fun onResume() {
        super.onResume()
        statusHandler.removeCallbacks(statusRefresh)
`,
  `    override fun onResume() {
        super.onResume()
        if (SetupPermissionHelper.notificationAccessEnabled(this)) {
            NotificationCodeListenerService.ensureBound(applicationContext, "settings_resume")
        }
        statusHandler.removeCallbacks(statusRefresh)
`,
  'settings notification listener recovery',
);
activity = replaceRequired(
  activity,
  `        notificationStatus.text = getString(
            if (SetupPermissionHelper.notificationAccessEnabled(this)) {
                R.string.notification_status_enabled
            } else {
                R.string.notification_status_disabled
            },
        )

        val selected = RelaySettingsStore.selectedApps(this)
`,
  `        notificationStatus.text = getString(
            if (SetupPermissionHelper.notificationAccessEnabled(this)) {
                R.string.notification_status_enabled
            } else {
                R.string.notification_status_disabled
            },
        )
        val listenerSnapshot = NotificationListenerRuntimeStore.read(this)
        notificationRuntimeStatus.text = getString(
            R.string.notification_runtime_format,
            if (listenerSnapshot.connected) {
                getString(R.string.notification_runtime_connected)
            } else {
                getString(R.string.notification_runtime_disconnected)
            },
            listenerSnapshot.lastStage,
            listenerSnapshot.lastOrigin,
            listenerSnapshot.seenCount,
            listenerSnapshot.textAvailableCount,
            listenerSnapshot.authHintCount,
            listenerSnapshot.queuedCount,
            listenerSnapshot.noMatchCount,
            listenerSnapshot.emptyCount,
            listenerSnapshot.filteredCount,
            listenerSnapshot.lastPartCount,
            listenerSnapshot.lastTextChars,
        )

        val selected = RelaySettingsStore.selectedApps(this)
`,
  'notification runtime diagnostic display',
);
fs.writeFileSync(activityPath, activity, 'utf8');

const stringFiles = [
  {
    file: path.resolve(__dirname, '..', 'android', 'app', 'src', 'main', 'res', 'values', 'strings.xml'),
    notificationAnchor: '    <string name="notification_explanation">Notification text is analyzed only on this phone. Only the short extracted verification value enters the relay queue.</string>',
    notificationReplacement:
      '    <string name="notification_explanation">Notification text is analyzed only on this phone. Only the short extracted verification value enters the relay queue.</string>\n' +
      '    <string name="notification_rescan">Reconnect listener and scan current notifications</string>\n' +
      '    <string name="notification_rescan_started">Notification listener is connected. Active-notification scan started.</string>\n' +
      '    <string name="notification_rebind_requested">Notification-listener rebind requested. Return here after a few seconds.</string>\n' +
      '    <string name="notification_runtime_connected">connected</string>\n' +
      '    <string name="notification_runtime_disconnected">not connected in this process</string>\n' +
      '    <string name="notification_runtime_format">Listener: %1$s\\nLast stage: %2$s / %3$s\\nSeen: %4$d / text available: %5$d / auth hint: %6$d / queued: %7$d\\nNo match: %8$d / empty: %9$d / filtered: %10$d\\nLast collected parts: %11$d / characters: %12$d</string>',
    healthAnchor: '    <string name="health_section">Recent diagnostics</string>',
    healthReplacement:
      '    <string name="outbound_debug_section">Outbound debug notification</string>\n' +
      '    <string name="outbound_debug_enable">Notify when an outbound item is accepted by the transport</string>\n' +
      '    <string name="outbound_debug_body">Default is OFF. The notification contains only the source class and P2P/P2S mode; it never contains clipboard text or a verification code.</string>\n' +
      '    <string name="outbound_debug_channel_name">ClipCascade outbound debug</string>\n' +
      '    <string name="outbound_debug_channel_description">Temporary notifications for accepted outbound transport sends.</string>\n' +
      '    <string name="outbound_debug_notification_title">ClipCascade outbound accepted</string>\n' +
      '    <string name="outbound_debug_notification_body">%1$s was accepted by %2$s transport.</string>\n' +
      '    <string name="outbound_debug_source_otp">Verification code</string>\n' +
      '    <string name="outbound_debug_source_clipboard">Copied text</string>\n' +
      '    <string name="outbound_debug_source_manual">Manual shared data</string>\n\n' +
      '    <string name="health_section">Recent diagnostics</string>',
  },
  {
    file: path.resolve(__dirname, '..', 'android', 'app', 'src', 'main', 'res', 'values-ja', 'strings.xml'),
    notificationAnchor: '    <string name="notification_explanation">通知本文は端末内だけで解析し、短い認証値だけを送信キューへ入れます。</string>',
    notificationReplacement:
      '    <string name="notification_explanation">通知本文は端末内だけで解析し、短い認証値だけを送信キューへ入れます。</string>\n' +
      '    <string name="notification_rescan">通知リスナーを再接続して現在の通知を再走査</string>\n' +
      '    <string name="notification_rescan_started">通知リスナーは接続済みです。現在の通知を再走査しました。</string>\n' +
      '    <string name="notification_rebind_requested">通知リスナーの再接続を要求しました。数秒後にこの画面へ戻ってください。</string>\n' +
      '    <string name="notification_runtime_connected">接続中</string>\n' +
      '    <string name="notification_runtime_disconnected">このプロセスでは未接続</string>\n' +
      '    <string name="notification_runtime_format">リスナー: %1$s\\n最終段階: %2$s / %3$s\\n受信: %4$d / 本文あり: %5$d / 認証文脈: %6$d / キュー追加: %7$d\\n抽出失敗: %8$d / 本文空: %9$d / 対象外: %10$d\\n直近の収集断片: %11$d / 文字数: %12$d</string>',
    healthAnchor: '    <string name="health_section">最近の診断</string>',
    healthReplacement:
      '    <string name="outbound_debug_section">送信デバッグ通知</string>\n' +
      '    <string name="outbound_debug_enable">データが送信経路に受理されたら通知する</string>\n' +
      '    <string name="outbound_debug_body">初期値はOFFです。通知には通常コピー・認証コードなどの種別とP2P/P2Sだけを表示し、本文やコード自体は表示しません。</string>\n' +
      '    <string name="outbound_debug_channel_name">ClipCascade送信デバッグ</string>\n' +
      '    <string name="outbound_debug_channel_description">送信経路に受理された際の一時的なデバッグ通知です。</string>\n' +
      '    <string name="outbound_debug_notification_title">ClipCascade送信受理</string>\n' +
      '    <string name="outbound_debug_notification_body">%1$sを%2$s送信経路が受理しました。</string>\n' +
      '    <string name="outbound_debug_source_otp">認証コード</string>\n' +
      '    <string name="outbound_debug_source_clipboard">通常コピー</string>\n' +
      '    <string name="outbound_debug_source_manual">手動共有データ</string>\n\n' +
      '    <string name="health_section">最近の診断</string>',
  },
];

for (const entry of stringFiles) {
  let strings = fs.readFileSync(entry.file, 'utf8');
  strings = replaceRequired(
    strings,
    entry.notificationAnchor,
    entry.notificationReplacement,
    `${entry.file} notification listener diagnostics strings`,
  );
  strings = replaceRequired(
    strings,
    entry.healthAnchor,
    entry.healthReplacement,
    `${entry.file} outbound debug strings`,
  );
  fs.writeFileSync(entry.file, strings, 'utf8');
}

const testPath = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'test',
  'java',
  'com',
  'clipcascade',
  'OtpCodeExtractorTest.kt',
);
let tests = fs.readFileSync(testPath, 'utf8');
const finalBrace = tests.lastIndexOf('\n}');
if (finalBrace < 0) {
  throw new Error('Expected OTP extractor test class closing brace was not found');
}
const gmailTests = `

    @Test
    fun extractsCodeFromGmailTitlePreviewAndExpandedBody() {
        assertEquals(
            "8F92FE",
            OtpCodeExtractor.extract(
                """
                Perceptron Network
                Use this code to login
                Perceptron Network received a request to login with an account.
                8F92FE
                This code will expire in 5 minutes.
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun gmailSubjectWithoutExposedCodeDoesNotInventValue() {
        assertNull(
            OtpCodeExtractor.extract(
                "Perceptron Network\\nUse this code to login\\nOpen Gmail to view the message",
            ),
        )
    }
`;
if (!tests.includes('extractsCodeFromGmailTitlePreviewAndExpandedBody')) {
  tests = tests.slice(0, finalBrace) + gmailTests + tests.slice(finalBrace);
}
fs.writeFileSync(testPath, tests, 'utf8');

console.log(
  'Prepared Gmail/notification-listener recovery, active rescan, stage diagnostics, and outbound debug notifications.',
);
