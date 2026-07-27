package com.clipcascade

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Conservative background trigger for Android 10+ clipboard restrictions.
 *
 * This service does not inspect arbitrary screen contents and does not react to
 * generic clicks or text-selection changes. It only requests the shared
 * Shizuku-first capture path after a high-confidence copy signal. Existing
 * React Native transport and duplicate suppression remain authoritative.
 */
class ClipCascadeAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastTriggerAt: Long = 0L

    private val triggerClipboardRead = Runnable {
        if (!ClipboardListenerModule.isRuntimeActive()) {
            Log.d(TAG, "Ignoring copy trigger because the ClipCascade runtime is inactive")
            CaptureDiagnostics.recordIgnored("accessibility", "runtime_inactive")
            return@Runnable
        }

        BackgroundClipboardCapture.request(this, "accessibility")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (!ClipboardListenerModule.isRuntimeActive()) return
        if (event.packageName?.toString() == packageName) return

        val labels = buildList {
            event.text.forEach { value -> value?.toString()?.let(::add) }
            event.contentDescription?.toString()?.let(::add)
        }
        if (
            !CopySignalClassifier.isHighConfidenceCopySignal(
                action = event.action,
                eventType = event.eventType,
                labels = labels
            )
        ) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastTriggerAt < TRIGGER_DEBOUNCE_MS) {
            CaptureDiagnostics.recordCoalesced("accessibility_debounce")
            return
        }
        lastTriggerAt = now

        handler.removeCallbacks(triggerClipboardRead)
        handler.postDelayed(triggerClipboardRead, CLIPBOARD_WRITE_SETTLE_MS)
    }

    override fun onInterrupt() {
        handler.removeCallbacks(triggerClipboardRead)
    }

    override fun onDestroy() {
        handler.removeCallbacks(triggerClipboardRead)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ClipCascadeA11y"
        private const val TRIGGER_DEBOUNCE_MS = 600L
        private const val CLIPBOARD_WRITE_SETTLE_MS = 250L
    }
}
