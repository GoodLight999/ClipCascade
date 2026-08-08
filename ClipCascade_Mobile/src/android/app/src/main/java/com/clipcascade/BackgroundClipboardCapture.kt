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
 * Concurrent signals are coalesced by actual read completion, without an
 * arbitrary time window. A pending trigger whose monotonic timestamp is at or
 * before the completed clipboard read was already represented by that read and
 * is discarded. Only a trigger that arrived after the read is executed as a
 * subsequent request.
 */
object BackgroundClipboardCapture {
    private const val TAG = "ClipboardCapture"

    private data class Trigger(
        val source: String,
        val requestedAtElapsedNanos: Long
    )

    private val stateLock = Any()
    private var active = false
    private var pendingTrigger: Trigger? = null

    fun request(context: Context, source: String) {
        val appContext = context.applicationContext
        val trigger = Trigger(source, SystemClock.elapsedRealtimeNanos())
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
                    trigger.requestedAtElapsedNanos >= current.requestedAtElapsedNanos
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
            var coveredThroughElapsedNanos: Long? = null
            try {
                if (result.success && result.content != null && result.type != null) {
                    coveredThroughElapsedNanos = result.readCompletedAtElapsedNanos
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
                    finishRequest(context, coveredThroughElapsedNanos)
                }
            }
        }
    }

    fun completeOverlay(
        context: Context,
        readCompletedAtElapsedNanos: Long?
    ) {
        finishRequest(context.applicationContext, readCompletedAtElapsedNanos)
    }

    private fun finishRequest(
        context: Context,
        coveredThroughElapsedNanos: Long?
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
                coveredThroughElapsedNanos != null &&
                    queued.requestedAtElapsedNanos <= coveredThroughElapsedNanos -> {
                    active = false
                    coveredTrigger = queued
                    null
                }
                else -> queued
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
