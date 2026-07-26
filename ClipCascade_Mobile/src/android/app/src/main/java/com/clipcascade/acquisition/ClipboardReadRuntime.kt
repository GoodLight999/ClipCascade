package com.clipcascade.acquisition

import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.modules.core.DeviceEventManagerModule

/**
 * Process-wide clipboard read and React Native delivery path.
 *
 * Acquisition backends only report that a read should be attempted. This
 * runtime owns content extraction and health counters so ordinary-listener and
 * focus-overlay reads cannot drift into different behavior or diagnostics.
 * Clipboard contents are never retained in the snapshot.
 */
data class ClipboardReadHealthSnapshot(
    val lastReadAttemptAtMonotonicMs: Long? = null,
    val lastSuccessfulReadAtMonotonicMs: Long? = null,
    val lastReadBackend: AcquisitionBackendId? = null,
    val lastReadResult: ClipboardReadResultCode? = null,
    val readAttemptCount: Long = 0,
    val successfulReadCount: Long = 0,
    val failedReadCount: Long = 0,
)

object ClipboardReadRuntime {
    private const val TAG = "ClipCascadeCapture"

    private val readLock = Any()
    private val snapshotLock = Any()

    private var lastReadAttemptAtMonotonicMs: Long? = null
    private var lastSuccessfulReadAtMonotonicMs: Long? = null
    private var lastReadBackend: AcquisitionBackendId? = null
    private var lastReadResult: ClipboardReadResultCode? = null
    private var readAttemptCount = 0L
    private var successfulReadCount = 0L
    private var failedReadCount = 0L

    fun readAndEmit(
        context: Context,
        backendId: AcquisitionBackendId,
        reactContext: ReactContext?,
    ): ClipboardReadResultCode = synchronized(readLock) {
        val attemptAt = SystemClock.elapsedRealtime()
        val result = try {
            val clipboardManager = context.getSystemService(
                Context.CLIPBOARD_SERVICE,
            ) as ClipboardManager
            val clip = clipboardManager.primaryClip
            if (clip == null || clip.itemCount <= 0) {
                ClipboardReadResultCode.EMPTY
            } else {
                val mimeType = clip.description?.getMimeType(0)
                val item = clip.getItemAt(0)
                val payload = Arguments.createMap()

                when {
                    mimeType?.startsWith("text/") == true && item.text != null -> {
                        payload.putString("content", item.text.toString())
                        payload.putString("type", "text")
                    }

                    mimeType?.startsWith("image/") == true && item.uri != null -> {
                        payload.putString("content", item.uri.toString())
                        payload.putString("type", "image")
                    }

                    item.uri != null -> {
                        payload.putString("content", item.uri.toString())
                        payload.putString("type", "files")
                    }

                    else -> return@synchronized recordResultAt(
                        backendId,
                        ClipboardReadResultCode.UNSUPPORTED_CONTENT,
                        attemptAt,
                    )
                }

                if (reactContext == null) {
                    ClipboardReadResultCode.REACT_CONTEXT_UNAVAILABLE
                } else {
                    try {
                        reactContext
                            .getJSModule(
                                DeviceEventManagerModule.RCTDeviceEventEmitter::class.java,
                            )
                            .emit("onClipboardChange", payload)
                        ClipboardReadResultCode.SUCCESS
                    } catch (exception: Exception) {
                        Log.w(
                            TAG,
                            "React Native clipboard event delivery failed",
                            exception,
                        )
                        ClipboardReadResultCode.REACT_CONTEXT_UNAVAILABLE
                    }
                }
            }
        } catch (_: SecurityException) {
            ClipboardReadResultCode.ACCESS_DENIED
        } catch (exception: Exception) {
            Log.e(TAG, "Clipboard read failed", exception)
            ClipboardReadResultCode.READ_FAILED
        }

        recordResultAt(backendId, result, attemptAt)
    }

    /** Records a failure that occurred before ClipboardManager could be read. */
    fun recordExternalResult(
        backendId: AcquisitionBackendId,
        result: ClipboardReadResultCode,
    ): ClipboardReadResultCode = synchronized(readLock) {
        recordResultAt(backendId, result, SystemClock.elapsedRealtime())
    }

    fun snapshot(): ClipboardReadHealthSnapshot = synchronized(snapshotLock) {
        ClipboardReadHealthSnapshot(
            lastReadAttemptAtMonotonicMs = lastReadAttemptAtMonotonicMs,
            lastSuccessfulReadAtMonotonicMs = lastSuccessfulReadAtMonotonicMs,
            lastReadBackend = lastReadBackend,
            lastReadResult = lastReadResult,
            readAttemptCount = readAttemptCount,
            successfulReadCount = successfulReadCount,
            failedReadCount = failedReadCount,
        )
    }

    private fun recordResultAt(
        backendId: AcquisitionBackendId,
        result: ClipboardReadResultCode,
        attemptAt: Long,
    ): ClipboardReadResultCode {
        synchronized(snapshotLock) {
            lastReadAttemptAtMonotonicMs = attemptAt
            lastReadBackend = backendId
            lastReadResult = result
            readAttemptCount += 1

            if (result == ClipboardReadResultCode.SUCCESS) {
                lastSuccessfulReadAtMonotonicMs = SystemClock.elapsedRealtime()
                successfulReadCount += 1
            } else if (result in failedResults) {
                failedReadCount += 1
            }
        }
        return result
    }

    private val failedResults = setOf(
        ClipboardReadResultCode.ACCESS_DENIED,
        ClipboardReadResultCode.FOCUS_REQUIRED,
        ClipboardReadResultCode.REACT_CONTEXT_UNAVAILABLE,
        ClipboardReadResultCode.UNSUPPORTED_CONTENT,
        ClipboardReadResultCode.READ_FAILED,
    )
}
