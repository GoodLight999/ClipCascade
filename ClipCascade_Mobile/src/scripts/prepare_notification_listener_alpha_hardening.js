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
const testDir = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'test',
  'java',
  'com',
  'clipcascade',
);

const receiptPolicySource = String.raw`package com.clipcascade

object NotificationReceiptPolicy {
    const val RECEIPT_TTL_MS = 30 * 60_000L
    const val MAX_RECEIPTS = 128

    fun isActive(now: Long, recordedAt: Long): Boolean =
        recordedAt > 0L && now - recordedAt in 0..RECEIPT_TTL_MS

    fun newest(
        entries: List<Pair<String, Long>>,
        maxItems: Int = MAX_RECEIPTS,
    ): List<Pair<String, Long>> = entries
        .sortedBy { it.second }
        .takeLast(maxItems.coerceAtLeast(0))
}
`;
fs.writeFileSync(
  path.join(javaDir, 'NotificationReceiptPolicy.kt'),
  receiptPolicySource,
  'utf8',
);

const receiptStoreSource = String.raw`package com.clipcascade

import android.content.Context
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Prevents one active notification from being re-queued after the OTP queue's
 * short content-deduplication window expires. Only a one-way fingerprint and
 * timestamp are persisted; notification text, code, package, key, and account
 * identifiers are never stored in recoverable form.
 */
object NotificationDeliveryReceiptStore {
    private const val PREFS = "notification_delivery_receipts"
    private const val KEY_ENTRIES = "entries"

    private data class Entry(
        val fingerprint: String,
        val recordedAt: Long,
    )

    @Synchronized
    fun claimIfNew(
        context: Context,
        posted: StatusBarNotification,
        extractedValue: String,
    ): Boolean {
        val now = System.currentTimeMillis()
        val fingerprint = fingerprint(posted, extractedValue)
        val entries = activeEntries(context, now).toMutableList()
        if (entries.any { it.fingerprint == fingerprint }) return false

        entries += Entry(fingerprint, now)
        writeEntries(
            context,
            NotificationReceiptPolicy.newest(
                entries.map { it.fingerprint to it.recordedAt },
            ).map { Entry(it.first, it.second) },
        )
        return true
    }

    @Synchronized
    fun releaseClaim(
        context: Context,
        posted: StatusBarNotification,
        extractedValue: String,
    ) {
        val fingerprint = fingerprint(posted, extractedValue)
        writeEntries(
            context,
            activeEntries(context, System.currentTimeMillis())
                .filterNot { it.fingerprint == fingerprint },
        )
    }

    @Synchronized
    fun clear(context: Context) {
        writeEntries(context, emptyList())
    }

    private fun activeEntries(context: Context, now: Long): List<Entry> {
        val all = readEntries(context)
        val active = all.filter {
            NotificationReceiptPolicy.isActive(now, it.recordedAt)
        }
        if (active.size != all.size) writeEntries(context, active)
        return active
    }

    private fun fingerprint(
        posted: StatusBarNotification,
        extractedValue: String,
    ): String {
        val material = buildString {
            append(posted.packageName)
            append('\u0000')
            append(posted.key.orEmpty())
            append('\u0000')
            append(posted.id)
            append('\u0000')
            append(posted.tag.orEmpty())
            append('\u0000')
            append(posted.postTime)
            append('\u0000')
            append(extractedValue)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun readEntries(context: Context): List<Entry> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null)
            ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optJSONObject(index) ?: continue
                    val fingerprint = value.optString("fingerprint")
                    val recordedAt = value.optLong("recordedAt")
                    if (fingerprint.isNotBlank() && recordedAt > 0L) {
                        add(Entry(fingerprint, recordedAt))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeEntries(context: Context, entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("fingerprint", entry.fingerprint)
                    put("recordedAt", entry.recordedAt)
                },
            )
        }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, array.toString())
            .commit()
    }
}
`;
fs.writeFileSync(
  path.join(javaDir, 'NotificationDeliveryReceiptStore.kt'),
  receiptStoreSource,
  'utf8',
);

const receiptPolicyTestSource = String.raw`package com.clipcascade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationReceiptPolicyTest {
    @Test
    fun receiptRemainsActiveThroughTtlBoundary() {
        val now = 10_000_000L
        assertTrue(NotificationReceiptPolicy.isActive(now, now))
        assertTrue(
            NotificationReceiptPolicy.isActive(
                now,
                now - NotificationReceiptPolicy.RECEIPT_TTL_MS,
            ),
        )
    }

    @Test
    fun staleAndFutureReceiptsAreRejected() {
        val now = 10_000_000L
        assertFalse(
            NotificationReceiptPolicy.isActive(
                now,
                now - NotificationReceiptPolicy.RECEIPT_TTL_MS - 1L,
            ),
        )
        assertFalse(NotificationReceiptPolicy.isActive(now, now + 1L))
        assertFalse(NotificationReceiptPolicy.isActive(now, 0L))
    }

    @Test
    fun newestReceiptSetRemainsBounded() {
        val entries = (1L..140L).map { "fp-$it" to it }
        val newest = NotificationReceiptPolicy.newest(entries)
        assertEquals(NotificationReceiptPolicy.MAX_RECEIPTS, newest.size)
        assertEquals("fp-13", newest.first().first)
        assertEquals("fp-140", newest.last().first)
    }
}
`;
fs.mkdirSync(testDir, {recursive: true});
fs.writeFileSync(
  path.join(testDir, 'NotificationReceiptPolicyTest.kt'),
  receiptPolicyTestSource,
  'utf8',
);

const listenerPath = path.join(javaDir, 'NotificationCodeListenerService.kt');
let listener = fs.readFileSync(listenerPath, 'utf8');
listener = replaceRequired(
  listener,
  `        val syntheticTest = OtpTestNotificationManager.isSyntheticTest(notification)
        if (posted.packageName == packageName && !syntheticTest) {
            NotificationListenerRuntimeStore.recordStage(applicationContext, "self_filtered", origin)
            return
        }
`,
  `        val syntheticTest = posted.packageName == packageName &&
            OtpTestNotificationManager.isSyntheticTest(notification)
        val listenerPathTest = syntheticTest &&
            OtpTestNotificationManager.isListenerPathTest(notification)
        if (posted.packageName == packageName && !syntheticTest) {
            NotificationListenerRuntimeStore.recordStage(applicationContext, "self_filtered", origin)
            return
        }
        if (syntheticTest && !listenerPathTest) {
            NotificationListenerRuntimeStore.recordStage(
                applicationContext,
                "component_test_direct_queue",
                origin,
            )
            return
        }
`,
  'synthetic notification trust boundary',
);
listener = replaceRequired(
  listener,
  `        if ((notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) {
            NotificationListenerRuntimeStore.recordStage(applicationContext, "foreground_filtered", origin)
            return
        }

        val expected = if (syntheticTest) {
`,
  `        if ((notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) {
            NotificationListenerRuntimeStore.recordStage(applicationContext, "foreground_filtered", origin)
            return
        }

        NotificationListenerRuntimeStore.recordEligible(
            applicationContext,
            origin = origin,
            callbackDelayMs = (System.currentTimeMillis() - posted.postTime)
                .coerceAtLeast(0L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt(),
            listenerPathTest = listenerPathTest,
        )

        val expected = if (syntheticTest) {
`,
  'eligible notification stage',
);
listener = replaceRequired(
  listener,
  `        val relayId = if (syntheticTest) {
            OtpTestStatusStore.RELAY_ID_PREFIX + UUID.randomUUID().toString()
        } else {
            UUID.randomUUID().toString()
        }
`,
  `        if (
            !NotificationDeliveryReceiptStore.claimIfNew(
                applicationContext,
                posted,
                value,
            )
        ) {
            NotificationListenerRuntimeStore.recordAlreadyProcessed(
                applicationContext,
                origin,
            )
            if (listenerPathTest) {
                OtpTestStatusStore.deduplicated(applicationContext, value)
            }
            return
        }

        val relayId = if (syntheticTest) {
            OtpTestStatusStore.RELAY_ID_PREFIX + UUID.randomUUID().toString()
        } else {
            UUID.randomUUID().toString()
        }
`,
  'persistent notification receipt claim',
);
listener = replaceRequired(
  listener,
  `        val queued = OtpRelayStore.enqueue(applicationContext, item)
        if (queued) {
`,
  `        val queued = try {
            OtpRelayStore.enqueue(applicationContext, item)
        } catch (error: Exception) {
            NotificationDeliveryReceiptStore.releaseClaim(
                applicationContext,
                posted,
                value,
            )
            NotificationListenerRuntimeStore.recordStage(
                applicationContext,
                "queue_error",
                origin,
            )
            Log.w(TAG, "Unable to enqueue extracted verification value", error)
            return
        }
        if (queued) {
`,
  'receipt rollback after queue failure',
);
fs.writeFileSync(listenerPath, listener, 'utf8');

const runtimePath = path.join(javaDir, 'NotificationListenerRuntimeStore.kt');
let runtime = fs.readFileSync(runtimePath, 'utf8');
runtime = replaceRequired(
  runtime,
  `        val seenCount: Int,
        val textAvailableCount: Int,
`,
  `        val seenCount: Int,
        val eligibleCount: Int,
        val alreadyProcessedCount: Int,
        val listenerTestCount: Int,
        val textAvailableCount: Int,
`,
  'runtime snapshot alpha counters',
);
runtime = replaceRequired(
  runtime,
  `        val activeScanCount: Int,
        val lastPartCount: Int,
        val lastTextChars: Int,
`,
  `        val activeScanCount: Int,
        val lastActiveScanCandidates: Int,
        val lastCallbackDelayMs: Int,
        val lastPartCount: Int,
        val lastTextChars: Int,
`,
  'runtime active scan details',
);
runtime = replaceRequired(
  runtime,
  `    @Synchronized
    fun recordFiltered(context: Context, origin: String) {
        increment(context, "filtered_count", "source_filtered", origin)
    }
`,
  `    @Synchronized
    fun recordFiltered(context: Context, origin: String) {
        increment(context, "filtered_count", "source_filtered", origin)
    }

    @Synchronized
    fun recordEligible(
        context: Context,
        origin: String,
        callbackDelayMs: Int,
        listenerPathTest: Boolean,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putInt("eligible_count", prefs.getInt("eligible_count", 0) + 1)
            .putInt("last_callback_delay_ms", callbackDelayMs)
            .putString("last_stage", "eligible")
            .putString("last_origin", origin)
        if (listenerPathTest) {
            editor.putInt(
                "listener_test_count",
                prefs.getInt("listener_test_count", 0) + 1,
            )
        }
        editor.commit()
    }

    @Synchronized
    fun recordAlreadyProcessed(context: Context, origin: String) {
        increment(
            context,
            "already_processed_count",
            "already_processed",
            origin,
        )
    }
`,
  'eligible and duplicate notification counters',
);
runtime = replaceRequired(
  runtime,
  `            seenCount = prefs.getInt("seen_count", 0),
            textAvailableCount = prefs.getInt("text_available_count", 0),
`,
  `            seenCount = prefs.getInt("seen_count", 0),
            eligibleCount = prefs.getInt("eligible_count", 0),
            alreadyProcessedCount = prefs.getInt("already_processed_count", 0),
            listenerTestCount = prefs.getInt("listener_test_count", 0),
            textAvailableCount = prefs.getInt("text_available_count", 0),
`,
  'runtime snapshot counter reads',
);
runtime = replaceRequired(
  runtime,
  `            activeScanCount = prefs.getInt("active_scan_count", 0),
            lastPartCount = prefs.getInt("last_part_count", 0),
            lastTextChars = prefs.getInt("last_text_chars", 0),
`,
  `            activeScanCount = prefs.getInt("active_scan_count", 0),
            lastActiveScanCandidates = prefs.getInt("last_active_scan_candidates", 0),
            lastCallbackDelayMs = prefs.getInt("last_callback_delay_ms", 0),
            lastPartCount = prefs.getInt("last_part_count", 0),
            lastTextChars = prefs.getInt("last_text_chars", 0),
`,
  'runtime active scan snapshot reads',
);
fs.writeFileSync(runtimePath, runtime, 'utf8');

const testManagerPath = path.join(javaDir, 'OtpTestNotificationManager.kt');
let testManager = fs.readFileSync(testManagerPath, 'utf8');
testManager = replaceRequired(
  testManager,
  `    const val EXTRA_SYNTHETIC_TEST = "com.clipcascade.extra.SYNTHETIC_VERIFICATION_TEST"
    const val EXTRA_EXPECTED_VALUE = "com.clipcascade.extra.SYNTHETIC_EXPECTED_VALUE"

    private const val CHANNEL_ID = "clipcascade_verification_test"
    private const val NOTIFICATION_TAG = "clipcascade-verification-test"
    private const val NOTIFICATION_ID = 4107
`,
  `    const val EXTRA_SYNTHETIC_TEST = "com.clipcascade.extra.SYNTHETIC_VERIFICATION_TEST"
    const val EXTRA_EXPECTED_VALUE = "com.clipcascade.extra.SYNTHETIC_EXPECTED_VALUE"
    const val EXTRA_SYNTHETIC_MODE = "com.clipcascade.extra.SYNTHETIC_MODE"

    private const val MODE_COMPONENT = "component"
    private const val MODE_LISTENER_PATH = "listener_path"
    private const val CHANNEL_ID = "clipcascade_verification_test"
    private const val NOTIFICATION_TAG = "clipcascade-verification-test"
    private const val LISTENER_NOTIFICATION_TAG = "clipcascade-listener-verification-test"
    private const val NOTIFICATION_ID = 4107
    private const val LISTENER_NOTIFICATION_ID = 4109
`,
  'listener-path synthetic test constants',
);
testManager = replaceRequired(
  testManager,
  `    fun expectedValue(notification: Notification): String =
        notification.extras?.getString(EXTRA_EXPECTED_VALUE).orEmpty()

    fun post(context: Context): String {
`,
  `    fun expectedValue(notification: Notification): String =
        notification.extras?.getString(EXTRA_EXPECTED_VALUE).orEmpty()

    fun isListenerPathTest(notification: Notification): Boolean =
        notification.extras?.getString(EXTRA_SYNTHETIC_MODE) == MODE_LISTENER_PATH

    fun post(context: Context): String {
`,
  'listener-path synthetic test predicate',
);
testManager = replaceRequired(
  testManager,
  `        val extras = Bundle().apply {
            putBoolean(EXTRA_SYNTHETIC_TEST, true)
            putString(EXTRA_EXPECTED_VALUE, value)
        }
`,
  `        val extras = Bundle().apply {
            putBoolean(EXTRA_SYNTHETIC_TEST, true)
            putString(EXTRA_EXPECTED_VALUE, value)
            putString(EXTRA_SYNTHETIC_MODE, MODE_COMPONENT)
        }
`,
  'component test mode marker',
);
testManager = replaceRequired(
  testManager,
  `        manager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification)
        queueSyntheticValue(applicationContext, text, value)
        return value
    }

    private fun queueSyntheticValue(context: Context, notificationText: String, expected: String) {
`,
  `        manager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification)
        queueSyntheticValue(applicationContext, text, value)
        return value
    }

    fun postListenerPath(context: Context): String {
        val applicationContext = context.applicationContext
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        ensureChannel(applicationContext, manager)

        val value = random.nextInt(1_000_000).toString().padStart(6, '0')
        OtpTestStatusStore.start(applicationContext, value)
        val text = applicationContext.getString(
            R.string.otp_test_notification_text,
            value,
        )
        val extras = Bundle().apply {
            putBoolean(EXTRA_SYNTHETIC_TEST, true)
            putString(EXTRA_EXPECTED_VALUE, value)
            putString(EXTRA_SYNTHETIC_MODE, MODE_LISTENER_PATH)
        }
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(applicationContext.getString(R.string.otp_listener_test_title))
            .setContentText(text)
            .setSubText(applicationContext.getString(R.string.otp_test_notification_subtext))
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000L)
            .addExtras(extras)
            .build()

        manager.notify(
            LISTENER_NOTIFICATION_TAG,
            LISTENER_NOTIFICATION_ID,
            notification,
        )
        return value
    }

    private fun queueSyntheticValue(context: Context, notificationText: String, expected: String) {
`,
  'listener-path synthetic notification posting',
);
fs.writeFileSync(testManagerPath, testManager, 'utf8');

const activityPath = path.join(javaDir, 'RelaySettingsActivity.kt');
let activity = fs.readFileSync(activityPath, 'utf8');
activity = replaceRequired(
  activity,
  `        content.addView(Button(this).apply {
            text = getString(R.string.otp_test_send)
            setOnClickListener { postOtpTestNotification() }
        })

        content.addView(sectionTitle(getString(R.string.clipboard_test_section)))
`,
  `        content.addView(Button(this).apply {
            text = getString(R.string.otp_test_send)
            setOnClickListener { postOtpTestNotification() }
        })
        content.addView(bodyText(getString(R.string.otp_listener_test_body)))
        content.addView(Button(this).apply {
            text = getString(R.string.otp_listener_test_send)
            setOnClickListener { postOtpListenerPathTestNotification() }
        })

        content.addView(sectionTitle(getString(R.string.clipboard_test_section)))
`,
  'listener-path self-test controls',
);
activity = replaceRequired(
  activity,
  `    private fun enqueueClipboardTest() {
`,
  `    private fun postOtpListenerPathTestNotification() {
        val setupReady = RelaySettingsStore.codeRelayEnabled(this) &&
            SetupPermissionHelper.notificationPermissionGranted(this) &&
            SetupPermissionHelper.notificationAccessEnabled(this)
        if (!setupReady) {
            AlertDialog.Builder(this)
                .setTitle(R.string.otp_test_missing_setup_title)
                .setMessage(R.string.otp_test_missing_setup_message)
                .setPositiveButton(R.string.setup_continue) { _, _ -> continueSetup() }
                .setNegativeButton(R.string.generic_cancel, null)
                .show()
            return
        }

        try {
            NotificationCodeListenerService.ensureBound(
                applicationContext,
                "listener_path_self_test",
            )
            val value = OtpTestNotificationManager.postListenerPath(applicationContext)
            Toast.makeText(
                this,
                getString(R.string.otp_listener_test_posted_toast, value),
                Toast.LENGTH_LONG,
            ).show()
        } catch (error: Exception) {
            val detail = error.javaClass.simpleName.ifBlank { "error" }
            OtpTestStatusStore.postFailed(applicationContext, detail)
            Toast.makeText(
                this,
                getString(R.string.otp_test_status_post_failed, detail),
                Toast.LENGTH_LONG,
            ).show()
        }
        updateStatus()
    }

    private fun enqueueClipboardTest() {
`,
  'listener-path self-test action',
);
activity = replaceRequired(
  activity,
  `            listenerSnapshot.seenCount,
            listenerSnapshot.textAvailableCount,
`,
  `            listenerSnapshot.seenCount,
            listenerSnapshot.eligibleCount,
            listenerSnapshot.alreadyProcessedCount,
            listenerSnapshot.listenerTestCount,
            listenerSnapshot.textAvailableCount,
`,
  'runtime alpha counters display',
);
activity = replaceRequired(
  activity,
  `            listenerSnapshot.filteredCount,
            listenerSnapshot.lastPartCount,
            listenerSnapshot.lastTextChars,
`,
  `            listenerSnapshot.filteredCount,
            listenerSnapshot.activeScanCount,
            listenerSnapshot.lastActiveScanCandidates,
            listenerSnapshot.lastCallbackDelayMs,
            listenerSnapshot.lastPartCount,
            listenerSnapshot.lastTextChars,
`,
  'runtime alpha scan details display',
);
activity = replaceRequired(
  activity,
  `                NotificationListenerRuntimeStore.clear(applicationContext)
                updateStatus()
`,
  `                NotificationListenerRuntimeStore.clear(applicationContext)
                NotificationDeliveryReceiptStore.clear(applicationContext)
                updateStatus()
`,
  'receipt diagnostic clear',
);
fs.writeFileSync(activityPath, activity, 'utf8');

const stringFiles = [
  {
    file: path.resolve(__dirname, '..', 'android', 'app', 'src', 'main', 'res', 'values', 'strings.xml'),
    runtimeBefore:
      '    <string name="notification_runtime_format">Listener: %1$s\\nLast stage: %2$s / %3$s\\nSeen: %4$d / text available: %5$d / auth hint: %6$d / queued: %7$d\\nNo match: %8$d / empty: %9$d / filtered: %10$d\\nLast collected parts: %11$d / characters: %12$d</string>',
    runtimeAfter:
      '    <string name="notification_runtime_format">Listener: %1$s\\nLast stage: %2$s / %3$s\\nSeen: %4$d / eligible: %5$d / already processed: %6$d / listener tests: %7$d\\nText available: %8$d / auth hint: %9$d / queued: %10$d\\nNo match: %11$d / empty: %12$d / filtered: %13$d\\nActive scans: %14$d / last candidates: %15$d / callback delay: %16$d ms\\nLast collected parts: %17$d / characters: %18$d</string>',
    testAnchor:
      '    <string name="otp_test_send">Post test verification-code notification</string>',
    testReplacement:
      '    <string name="otp_test_send">Send deterministic component/transport test</string>\n' +
      '    <string name="otp_listener_test_body">This separate test does not queue the value directly. The local notification must pass through the actual NotificationListenerService, extractor, persistent queue, transport, and ACK path.</string>\n' +
      '    <string name="otp_listener_test_send">Run real notification-listener path self-test</string>\n' +
      '    <string name="otp_listener_test_title">ClipCascade listener-path test</string>\n' +
      '    <string name="otp_listener_test_posted_toast">Listener-path notification posted. Expected code: %1$s</string>',
  },
  {
    file: path.resolve(__dirname, '..', 'android', 'app', 'src', 'main', 'res', 'values-ja', 'strings.xml'),
    runtimeBefore:
      '    <string name="notification_runtime_format">リスナー: %1$s\\n最終段階: %2$s / %3$s\\n受信: %4$d / 本文あり: %5$d / 認証文脈: %6$d / キュー追加: %7$d\\n抽出失敗: %8$d / 本文空: %9$d / 対象外: %10$d\\n直近の収集断片: %11$d / 文字数: %12$d</string>',
    runtimeAfter:
      '    <string name="notification_runtime_format">リスナー: %1$s\\n最終段階: %2$s / %3$s\\n受信: %4$d / 対象通過: %5$d / 処理済み抑止: %6$d / リスナーテスト: %7$d\\n本文あり: %8$d / 認証文脈あり: %9$d / キュー追加: %10$d\\n抽出失敗: %11$d / 本文空: %12$d / 対象外: %13$d\\n再走査: %14$d回 / 直近候補: %15$d件 / callback遅延: %16$dms\\n直近の収集: %17$d項目 / %18$d文字</string>',
    testAnchor:
      '    <string name="otp_test_send">認証コードのテスト通知を送る</string>',
    testReplacement:
      '    <string name="otp_test_send">決定的な部品・通信テストを実行</string>\n' +
      '    <string name="otp_listener_test_body">この別テストは値を直接キューへ入れません。ローカル通知が実際のNotificationListenerService、抽出器、永続キュー、通信、ACKを通る必要があります。</string>\n' +
      '    <string name="otp_listener_test_send">実通知リスナー経路のセルフテスト</string>\n' +
      '    <string name="otp_listener_test_title">ClipCascade リスナー経路テスト</string>\n' +
      '    <string name="otp_listener_test_posted_toast">リスナー経路テスト通知を送信しました。期待するコード: %1$s</string>',
  },
];
for (const entry of stringFiles) {
  let strings = fs.readFileSync(entry.file, 'utf8');
  strings = replaceRequired(
    strings,
    entry.runtimeBefore,
    entry.runtimeAfter,
    `${entry.file} runtime alpha diagnostics`,
  );
  strings = replaceRequired(
    strings,
    entry.testAnchor,
    entry.testReplacement,
    `${entry.file} listener-path self-test strings`,
  );
  fs.writeFileSync(entry.file, strings, 'utf8');
}

console.log(
  'Prepared alpha notification-listener self-test, persistent receipt guard, and expanded diagnostics.',
);
