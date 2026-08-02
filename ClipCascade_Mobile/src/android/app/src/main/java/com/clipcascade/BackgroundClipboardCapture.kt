package com.clipcascade

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Single automatic clipboard-acquisition decision point.
 *
 * Foreground ClipboardManager notifications, documented Accessibility
 * ACTION_COPY events, and the optional READ_LOGS signal all enter here.
 * Preferred reader: Shizuku UserService. Fallback reader: the existing overlay
 * activity. The existing React Native sender remains the only transport owner.
 *
 * Concurrent signals are coalesced. One latest pending signal is retained so a
 * second real copy that arrives during an in-flight read is not silently lost.
 */
object BackgroundClipboardCapture {
    private const val TAG = "ClipboardCapture"
    private val inFlight = AtomicBoolean(false)
    private val pendingSource = AtomicReference<String?>(null)
    private val ignoredClipboardListenerEvents = AtomicInteger(0)

    /**
     * Marks an application-owned clipboard write that must not be synchronized.
     * This is used only when ClipCascade itself copies setup commands.
     */
    fun ignoreNextClipboardListenerEvent() {
        ignoredClipboardListenerEvents.incrementAndGet()
    }

    fun request(context: Context, source: String) {
        val appContext = context.applicationContext
        CaptureDiagnostics.recordTrigger(source)

        if (
            source == "clipboard_listener" &&
            consumeIgnoredClipboardListenerEvent()
        ) {
            CaptureDiagnostics.recordIgnored(source, "application_owned_clipboard_write")
            return
        }

        if (!ClipboardListenerModule.isRuntimeActive()) {
            Log.d(TAG, "Ignoring $source trigger because the ClipCascade runtime is inactive")
            CaptureDiagnostics.recordIgnored(source, "runtime_inactive")
            return
        }

        if (!inFlight.compareAndSet(false, true)) {
            pendingSource.set(source)
            CaptureDiagnostics.recordCoalesced(source)
            return
        }

        CaptureDiagnostics.recordShizukuAttempt(source)
        ShizukuClipboardBridge.readClipboard { result ->
            try {
                if (result.success && result.content != null && result.type != null) {
                    CaptureDiagnostics.recordShizukuSuccess(source)
                    if (
                        !ClipboardListenerModule.emitExternalClipboard(
                            result.content,
                            result.type,
                            "shizuku:$source"
                        )
                    ) {
                        Log.w(TAG, "Shizuku read succeeded but React Native runtime was unavailable")
                        CaptureDiagnostics.recordIgnored(source, "react_runtime_unavailable")
                    }
                    return@readClipboard
                }

                // Shizuku may be absent, stopped, denied, binding, unsupported,
                // or may intentionally request fallback for non-text content.
                CaptureDiagnostics.recordOverlayFallback(
                    source,
                    result.error ?: result.status
                )
                if (
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                    Settings.canDrawOverlays(appContext)
                ) {
                    try {
                        appContext.startActivity(ClipboardFloatingActivity.getIntent(appContext))
                    } catch (error: Throwable) {
                        Log.e(TAG, "Overlay fallback failed for $source", error)
                        CaptureDiagnostics.recordFailure(source, "overlay_launch_failed", error)
                    }
                } else {
                    Log.w(TAG, "No usable clipboard read path for $source: ${result.status}")
                    CaptureDiagnostics.recordIgnored(source, "overlay_permission_missing")
                }
            } finally {
                finishRequest(appContext)
            }
        }
    }

    private fun consumeIgnoredClipboardListenerEvent(): Boolean {
        while (true) {
            val current = ignoredClipboardListenerEvents.get()
            if (current <= 0) return false
            if (ignoredClipboardListenerEvents.compareAndSet(current, current - 1)) {
                return true
            }
        }
    }

    private fun finishRequest(context: Context) {
        inFlight.set(false)
        pendingSource.getAndSet(null)?.let { pending ->
            request(context, "$pending:coalesced")
        }
    }
}
