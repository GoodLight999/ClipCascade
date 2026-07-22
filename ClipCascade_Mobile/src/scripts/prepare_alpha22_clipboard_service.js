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

const observationStoreSource = String.raw`package com.clipcascade

import android.content.Context
import java.security.MessageDigest

/** Persists only a one-way fingerprint of the last observed clipboard text. */
object ClipboardObservationStore {
    private const val PREFS = "clipboard_observation"
    private const val KEY_FINGERPRINT = "fingerprint"

    fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    fun currentFingerprint(context: Context): String = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_FINGERPRINT, "")
        .orEmpty()

    @Synchronized
    fun rememberText(context: Context, text: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FINGERPRINT, fingerprint(text))
            .commit()
    }

    @Synchronized
    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
`;
fs.writeFileSync(path.join(javaDir, 'ClipboardObservationStore.kt'), observationStoreSource, 'utf8');

const acquisitionPolicySource = String.raw`package com.clipcascade

object ClipboardAcquisitionPolicy {
    enum class Decision {
        IGNORE_EMPTY,
        PRIME_BASELINE,
        UNCHANGED,
        SEND,
    }

    fun decide(
        strongCopySignal: Boolean,
        previousFingerprint: String,
        currentFingerprint: String,
    ): Decision = when {
        currentFingerprint.isBlank() -> Decision.IGNORE_EMPTY
        previousFingerprint.isBlank() && strongCopySignal -> Decision.SEND
        previousFingerprint.isBlank() -> Decision.PRIME_BASELINE
        previousFingerprint == currentFingerprint -> Decision.UNCHANGED
        else -> Decision.SEND
    }
}
`;
fs.writeFileSync(path.join(javaDir, 'ClipboardAcquisitionPolicy.kt'), acquisitionPolicySource, 'utf8');

const acquisitionPolicyTestSource = String.raw`package com.clipcascade

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardAcquisitionPolicyTest {
    @Test
    fun selectionProbeNeverSendsWithoutClipboardMutation() {
        assertEquals(
            ClipboardAcquisitionPolicy.Decision.UNCHANGED,
            ClipboardAcquisitionPolicy.decide(
                strongCopySignal = false,
                previousFingerprint = "same",
                currentFingerprint = "same",
            ),
        )
    }

    @Test
    fun firstWeakProbePrimesInsteadOfSendingStaleClipboard() {
        assertEquals(
            ClipboardAcquisitionPolicy.Decision.PRIME_BASELINE,
            ClipboardAcquisitionPolicy.decide(false, "", "first"),
        )
    }

    @Test
    fun explicitCopyMaySendFirstObservation() {
        assertEquals(
            ClipboardAcquisitionPolicy.Decision.SEND,
            ClipboardAcquisitionPolicy.decide(true, "", "first"),
        )
    }

    @Test
    fun realClipboardMutationSendsAfterBaseline() {
        assertEquals(
            ClipboardAcquisitionPolicy.Decision.SEND,
            ClipboardAcquisitionPolicy.decide(false, "before", "after"),
        )
    }
}
`;
fs.writeFileSync(path.join(testDir, 'ClipboardAcquisitionPolicyTest.kt'), acquisitionPolicyTestSource, 'utf8');

const acquisitionServiceSource = String.raw`package com.clipcascade

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * Go-reference-equivalent native owner for background clipboard acquisition.
 *
 * Accessibility only supplies candidate events. This sticky foreground service
 * owns the overlay, ClipboardManager read, mutation proof, and durable queue
 * insertion. Transport and peer ACK remain in the existing Extended path.
 */
class ClipboardAcquisitionService : Service() {
    companion object {
        private const val TAG = "ClipboardAcquisition"
        private const val CHANNEL_ID = "clipcascade_clipboard_acquisition"
        private const val NOTIFICATION_ID = 4112
        private const val ACTION_PRIME = "com.clipcascade.action.PRIME_CLIPBOARD"
        private const val ACTION_CAPTURE = "com.clipcascade.action.CAPTURE_CLIPBOARD"
        private const val EXTRA_TRIGGER = "trigger"
        private const val EXTRA_SOURCE_PACKAGE = "source_package"
        private const val EXTRA_STRONG = "strong"
        private const val WEAK_DELAY_MS = 1_200L
        private const val STRONG_DELAY_MS = 300L

        fun start(context: Context, primeOnly: Boolean = false) {
            val intent = Intent(context.applicationContext, ClipboardAcquisitionService::class.java)
                .setAction(if (primeOnly) ACTION_PRIME else null)
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        fun request(
            context: Context,
            trigger: String,
            sourcePackage: String,
            strongCopySignal: Boolean,
        ) {
            val intent = Intent(context.applicationContext, ClipboardAcquisitionService::class.java)
                .setAction(ACTION_CAPTURE)
                .putExtra(EXTRA_TRIGGER, trigger.take(64))
                .putExtra(EXTRA_SOURCE_PACKAGE, sourcePackage.take(256))
                .putExtra(EXTRA_STRONG, strongCopySignal)
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): ClipboardAcquisitionService = this@ClipboardAcquisitionService
    }

    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private var pendingCapture: Runnable? = null
    private var overlayLayout: LinearLayout? = null

    private val clipboardChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!RelaySettingsStore.clipboardEnabled(this)) return@OnPrimaryClipChangedListener
        if (ClipboardWriteGuard.isMarked()) {
            handler.postDelayed({ primeCurrentClipboard("internal_write") }, 150L)
            return@OnPrimaryClipChangedListener
        }
        requestClipboardObservation(
            trigger = "clipboard_change",
            sourcePackage = "",
            strongCopySignal = true,
        )
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        startForegroundNotification()
        clipboardManager.addPrimaryClipChangedListener(clipboardChangedListener)
        handler.postDelayed({ primeCurrentClipboard("service_start") }, 700L)
        RelayHealthStore.record(
            applicationContext,
            category = "copy_runtime",
            trigger = "native_service_started",
            path = "sticky_foreground_service",
            result = "ready",
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PRIME -> handler.post { primeCurrentClipboard("explicit_prime") }
            ACTION_CAPTURE -> requestClipboardObservation(
                trigger = intent.getStringExtra(EXTRA_TRIGGER).orEmpty().ifBlank { "accessibility_probe" },
                sourcePackage = intent.getStringExtra(EXTRA_SOURCE_PACKAGE).orEmpty(),
                strongCopySignal = intent.getBooleanExtra(EXTRA_STRONG, false),
            )
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun requestClipboardObservation(
        trigger: String,
        sourcePackage: String,
        strongCopySignal: Boolean,
    ) {
        if (!RelaySettingsStore.clipboardEnabled(this)) return
        // A selection event normally precedes Copy. Capture the old clipboard
        // fingerprint immediately, then compare after the Go-reference delay.
        // Selection alone therefore observes an unchanged fingerprint and cannot send.
        if (trigger == "selection_probe" && !strongCopySignal) {
            primeCurrentClipboard("pre_selection_baseline")
        }
        pendingCapture?.let(handler::removeCallbacks)
        val runnable = Runnable {
            pendingCapture = null
            observeAndQueue(trigger, sourcePackage, strongCopySignal)
        }
        pendingCapture = runnable
        handler.postDelayed(
            runnable,
            if (strongCopySignal) STRONG_DELAY_MS else WEAK_DELAY_MS,
        )
        RelayHealthStore.record(
            applicationContext,
            category = "copy_detection",
            trigger = trigger,
            path = if (strongCopySignal) "native_strong_probe" else "native_mutation_probe",
            result = "requested",
        )
    }

    private fun primeCurrentClipboard(trigger: String) {
        val read = readClipboardWithOverlay()
        val value = read.text?.trim()?.takeIf { it.isNotEmpty() } ?: run {
            RelayHealthStore.record(
                applicationContext,
                category = "copy_runtime",
                trigger = trigger,
                path = read.path,
                result = "prime_empty",
            )
            return
        }
        ClipboardObservationStore.rememberText(applicationContext, value)
        RelayHealthStore.record(
            applicationContext,
            category = "copy_runtime",
            trigger = trigger,
            path = read.path,
            result = "baseline",
        )
    }

    private fun observeAndQueue(
        trigger: String,
        sourcePackage: String,
        strongCopySignal: Boolean,
    ) {
        val read = readClipboardWithOverlay()
        val value = read.text
            ?.trim()
            ?.take(500_000)
            ?.takeIf { it.isNotEmpty() }
        if (value == null) {
            RelayHealthStore.record(
                applicationContext,
                category = "clipboard",
                trigger = trigger,
                path = read.path,
                result = "retrying",
            )
            return
        }

        if (ClipboardWriteGuard.isMarked()) {
            ClipboardObservationStore.rememberText(applicationContext, value)
            RelayHealthStore.record(
                applicationContext,
                category = "clipboard",
                trigger = trigger,
                path = "internal_write_baseline",
                result = "suppressed",
            )
            return
        }

        val fingerprint = ClipboardObservationStore.fingerprint(value)
        val previous = ClipboardObservationStore.currentFingerprint(applicationContext)
        when (
            ClipboardAcquisitionPolicy.decide(
                strongCopySignal = strongCopySignal,
                previousFingerprint = previous,
                currentFingerprint = fingerprint,
            )
        ) {
            ClipboardAcquisitionPolicy.Decision.IGNORE_EMPTY -> return
            ClipboardAcquisitionPolicy.Decision.PRIME_BASELINE -> {
                ClipboardObservationStore.rememberText(applicationContext, value)
                RelayHealthStore.record(
                    applicationContext,
                    category = "clipboard",
                    trigger = trigger,
                    path = read.path,
                    result = "baseline",
                )
                return
            }
            ClipboardAcquisitionPolicy.Decision.UNCHANGED -> {
                RelayHealthStore.record(
                    applicationContext,
                    category = "clipboard",
                    trigger = trigger,
                    path = read.path,
                    result = "unchanged",
                )
                return
            }
            ClipboardAcquisitionPolicy.Decision.SEND -> Unit
        }

        val item = ClipboardRelayStore.Item(
            id = UUID.randomUUID().toString(),
            text = value,
            sourcePackage = sourcePackage,
            createdAt = System.currentTimeMillis(),
        )
        val enqueueResult = ClipboardRelayStore.enqueue(applicationContext, item)
        if (enqueueResult == ClipboardRelayStore.EnqueueResult.QUEUED) {
            ClipboardRelayDispatcher.schedule(applicationContext)
        }
        if (enqueueResult != ClipboardRelayStore.EnqueueResult.QUEUE_FULL) {
            ClipboardObservationStore.rememberText(applicationContext, value)
        }
        RelayHealthStore.record(
            applicationContext,
            category = "clipboard",
            trigger = trigger,
            path = read.path,
            result = when (enqueueResult) {
                ClipboardRelayStore.EnqueueResult.QUEUED -> "queued"
                ClipboardRelayStore.EnqueueResult.DEDUPLICATED -> "deduplicated"
                ClipboardRelayStore.EnqueueResult.QUEUE_FULL -> "queue_full"
            },
        )
    }

    private data class ClipboardReadResult(
        val text: String?,
        val path: String,
    )

    private fun inspectClipboard(successPath: String, emptyPath: String): ClipboardReadResult = try {
        val clip = clipboardManager.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(this)?.toString()
        } else {
            null
        }
        ClipboardReadResult(text, if (text.isNullOrBlank()) emptyPath else successPath)
    } catch (error: SecurityException) {
        Log.d(TAG, "Clipboard access denied", error)
        ClipboardReadResult(null, "clipboard_denied")
    } catch (error: Exception) {
        Log.w(TAG, "Clipboard inspection failed", error)
        ClipboardReadResult(null, "clipboard_error")
    }

    private fun readClipboardWithOverlay(): ClipboardReadResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return inspectClipboard("clipboard_manager", "clipboard_empty")
        }
        if (!RelaySettingsStore.overlayClipboardEnabled(this)) {
            return ClipboardReadResult(null, "overlay_disabled")
        }
        if (!SetupPermissionHelper.overlayPermissionGranted(this)) {
            return ClipboardReadResult(null, "overlay_permission_missing")
        }

        showInvisibleOverlay()
        return try {
            inspectClipboard("overlay_clipboard_manager", "overlay_clipboard_empty")
        } finally {
            removeInvisibleOverlay()
        }
    }

    private fun showInvisibleOverlay() {
        if (overlayLayout != null) return
        val view = LinearLayout(this).apply {
            alpha = 0f
            setBackgroundColor(
                ContextCompat.getColor(
                    this@ClipboardAcquisitionService,
                    android.R.color.transparent,
                ),
            )
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSPARENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        try {
            windowManager.addView(view, params)
            overlayLayout = view
        } catch (error: Exception) {
            Log.w(TAG, "Unable to add clipboard overlay", error)
            overlayLayout = null
            throw error
        }
    }

    private fun removeInvisibleOverlay() {
        val view = overlayLayout ?: return
        overlayLayout = null
        try {
            windowManager.removeView(view)
        } catch (error: Exception) {
            Log.w(TAG, "Unable to remove clipboard overlay", error)
        }
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.clipboard_acquisition_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.clipboard_acquisition_channel_description)
                    setShowBadge(false)
                },
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, RelaySettingsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(getString(R.string.clipboard_acquisition_notification_title))
            .setContentText(getString(R.string.clipboard_acquisition_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        pendingCapture?.let(handler::removeCallbacks)
        handler.removeCallbacksAndMessages(null)
        clipboardManager.removePrimaryClipChangedListener(clipboardChangedListener)
        removeInvisibleOverlay()
        super.onDestroy()
    }
}
`;
fs.writeFileSync(path.join(javaDir, 'ClipboardAcquisitionService.kt'), acquisitionServiceSource, 'utf8');


console.log('Prepared alpha.22 clipboard observation store, mutation policy, and sticky native acquisition service.');
