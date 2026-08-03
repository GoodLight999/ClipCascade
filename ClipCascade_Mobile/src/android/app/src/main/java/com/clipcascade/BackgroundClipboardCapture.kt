package com.clipcascade

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

/**
 * Single automatic clipboard-acquisition decision point.
 *
 * Foreground ClipboardManager notifications, documented Accessibility
 * ACTION_COPY events, and the optional READ_LOGS signal all enter here.
 * Preferred reader: Shizuku UserService. Fallback reader: the existing overlay
 * activity. The existing React Native sender remains the only transport owner.
 *
 * Concurrent signals are coalesced without an arbitrary debounce window. A
 * pending trigger whose monotonic timestamp is at or before the completed
 * clipboard read was already represented by that read and is discarded. Only
 * a trigger that arrived after the read is executed as a subsequent request.
 */
object BackgroundClipboardCapture {
    private const val TAG = "ClipboardCapture"

    private data class Trigger(
        val source: String,
        val requestedAtElapsedMs: Long
    )

    private val stateLock = Any()
    private var active = false
    private var pendingTrigger: Trigger? = null

    fun request(context: Context, source: String) {
        val appContext = context.applicationContext
        val trigger = Trigger(source, SystemClock.elapsedRealtime())
        CaptureDiagnostics.recordTrigger(source)

        if (!ClipboardListenerModule.isRuntimeActive()) {
            Log.d(TAG, "Ignoring $source trigger because the ClipCascade runtime is inactive")
            CaptureDiagnostics.recordIgnored(source, "runtime_inactive")
            return
        }

        val startNow = synchronized(stateLock) {
            if (active) {
                val current = pendingTrigger
                if (
                    current == null ||
                    trigger.requestedAtElapsedMs >= current.requestedAtElapsedMs
                ) {
                    pendingTrigger = trigger
                }
                false
            } else {
                active = true
                true
            }
        }

        if (!startNow) {
            CaptureDiagnostics.recordCoalesced(source)
            return
        }
        beginCapture(appContext, trigger)
    }

    private fun beginCapture(context: Context, trigger: Trigger) {
        CaptureDiagnostics.recordShizukuAttempt(trigger.source)
        ShizukuClipboardBridge.readClipboard { result ->
            var overlayOwnsCompletion = false
            var coveredThroughElapsedMs: Long? = null
            try {
                if (result.success && result.content != null && result.type != null) {
                    coveredThroughElapsedMs = result.readCompletedAtElapsedMs
                    CaptureDiagnostics.recordShizukuSuccess(trigger.source)
                    if (
                        !ClipboardListenerModule.emitExternalClipboard(
                            result.content,
                            result.type,
                            "shizuku:${trigger.source}"
                        )
                    ) {
                        Log.w(TAG, "Shizuku read succeeded but React Native runtime was unavailable")
                        CaptureDiagnostics.recordIgnored(
                            trigger.source,
                            "react_runtime_unavailable"
                        )
                    }
                    return@readClipboard
                }

                // Shizuku may be absent, stopped, denied, binding, unsupported,
                // or may intentionally request fallback for non-text content.
                CaptureDiagnostics.recordOverlayFallback(
                    trigger.source,
                    result.error ?: result.status
                )
                if (Settings.canDrawOverlays(context)) {
                    try {
                        context.startActivity(
                            ClipboardFloatingActivity.getIntent(
                                context,
                                trigger.source
                            )
                        )
                        // ClipboardFloatingActivity calls completeOverlay once
                        // its actual read attempt and teardown have finished.
                        overlayOwnsCompletion = true
                    } catch (error: Throwable) {
                        Log.e(TAG, "Overlay fallback failed for ${trigger.source}", error)
                        CaptureDiagnostics.recordFailure(
                            trigger.source,
                            "overlay_launch_failed",
                            error
                        )
                    }
                } else {
                    Log.w(
                        TAG,
                        "No usable clipboard read path for ${trigger.source}: ${result.status}"
                    )
                    CaptureDiagnostics.recordIgnored(
                        trigger.source,
                        "overlay_permission_missing"
                    )
                }
            } finally {
                if (!overlayOwnsCompletion) {
                    finishRequest(context, coveredThroughElapsedMs)
                }
            }
        }
    }

    fun completeOverlay(
        context: Context,
        readCompletedAtElapsedMs: Long?
    ) {
        finishRequest(context.applicationContext, readCompletedAtElapsedMs)
    }

    private fun finishRequest(
        context: Context,
        coveredThroughElapsedMs: Long?
    ) {
        var coveredTrigger: Trigger? = null
        val nextTrigger = synchronized(stateLock) {
            val queued = pendingTrigger
            pendingTrigger = null

            when {
                queued == null -> {
                    active = false
                    null
                }
                coveredThroughElapsedMs != null &&
                    queued.requestedAtElapsedMs <= coveredThroughElapsedMs -> {
                    active = false
                    coveredTrigger = queued
                    null
                }
                else -> {
                    // Keep ownership while the next causal request starts.
                    queued
                }
            }
        }

        coveredTrigger?.let {
            CaptureDiagnostics.recordIgnored(
                it.source,
                "covered_by_completed_clipboard_read"
            )
        }
        nextTrigger?.let { beginCapture(context, it) }
    }
}
