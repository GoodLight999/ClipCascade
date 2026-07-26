package com.clipcascade

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.clipcascade.acquisition.AcquisitionBackendId
import com.clipcascade.acquisition.AndroidClipboardChangeRegistrar
import com.clipcascade.acquisition.BackendReasonCode
import com.clipcascade.acquisition.BackendStartCode
import com.clipcascade.acquisition.BackendStopCode
import com.clipcascade.acquisition.ClipboardReadResultCode
import com.clipcascade.acquisition.ClipboardReadRuntime
import com.clipcascade.acquisition.MonotonicClock
import com.clipcascade.acquisition.OrdinaryClipboardBackend
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipboardListenerModule(
    reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {
    private val clipboardManager = reactContext.getSystemService(
        Context.CLIPBOARD_SERVICE,
    ) as android.content.ClipboardManager

    private val ordinaryBackend = OrdinaryClipboardBackend(
        registrar = AndroidClipboardChangeRegistrar(clipboardManager),
        clock = MonotonicClock { SystemClock.elapsedRealtime() },
    )

    @Volatile
    private var isListening = false

    @Volatile
    private var lastActivityStartTime = 0L

    private val activityDebounceTime = 1_000L

    @Volatile
    private var logcatThread: Thread? = null

    @Volatile
    private var logcatProcess: Process? = null

    @Volatile
    private var logcatGeneration = 0L

    @Volatile
    private var lastLogcatMatchAtMonotonicMs = -1L

    @Volatile
    private var lastOverlayLaunchAtMonotonicMs = -1L

    @Volatile
    private var lastLogcatErrorCode: BackendReasonCode? = null

    override fun getName(): String = "ClipboardListener"

    @ReactMethod
    @Synchronized
    fun startListening() {
        val ordinaryResult = ordinaryBackend.start { trigger ->
            ClipboardReadRuntime.readAndEmit(
                context = reactApplicationContext,
                backendId = trigger.backendId,
                reactContext = reactApplicationContext,
            )
        }
        val ordinaryStarted = ordinaryResult.code == BackendStartCode.STARTED ||
            ordinaryResult.code == BackendStartCode.ALREADY_RUNNING
        if (!ordinaryStarted) {
            Log.e(
                TAG,
                "Ordinary clipboard backend failed: ${ordinaryResult.reasonCode}",
            )
        }

        // Re-inspect on every request so granting READ_LOGS or overlay permission
        // does not require reconstructing the React Native module.
        val logcatStarted = startLegacyLogcatMonitoringIfAvailable()
        isListening = ordinaryStarted || logcatStarted
    }

    @ReactMethod
    @Synchronized
    fun stopListening() {
        val ordinaryResult = ordinaryBackend.stop()
        if (ordinaryResult.code == BackendStopCode.FAILED) {
            Log.e(
                TAG,
                "Ordinary clipboard backend stop failed: ${ordinaryResult.reasonCode}",
            )
        }

        logcatGeneration += 1
        val process = logcatProcess
        val thread = logcatThread
        logcatProcess = null
        logcatThread = null

        try {
            process?.destroy()
        } catch (_: Exception) {
        }
        try {
            thread?.interrupt()
        } catch (_: Exception) {
        }
        isListening = false
    }

    @ReactMethod
    fun getAcquisitionSnapshot(promise: Promise) {
        try {
            val ordinary = ordinaryBackend.snapshot()
            val ordinaryMap = Arguments.createMap().apply {
                putString("backendId", ordinary.backendId.name)
                putBoolean("running", ordinary.running)
                putDouble("startCount", ordinary.startCount.toDouble())
                putDouble("triggerCount", ordinary.triggerCount.toDouble())
                putLongOrNull(
                    "lastStartedAtMonotonicMs",
                    ordinary.lastStartedAtMonotonicMs,
                )
                putLongOrNull(
                    "lastStoppedAtMonotonicMs",
                    ordinary.lastStoppedAtMonotonicMs,
                )
                putLongOrNull(
                    "lastTriggerAtMonotonicMs",
                    ordinary.lastTriggerAtMonotonicMs,
                )
                putStringOrNull("lastErrorCode", ordinary.lastErrorCode?.name)
            }

            val logcatMap = Arguments.createMap().apply {
                putString("backendId", AcquisitionBackendId.LOGCAT_OVERLAY.name)
                putBoolean("sdkEligible", Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                putBoolean("readLogsGranted", hasReadLogsPermission())
                putBoolean("overlayGranted", hasOverlayPermission())
                putBoolean("threadAlive", logcatThread?.isAlive == true)
                putBoolean("processAlive", logcatProcess?.isAlive == true)
                putDouble("generation", logcatGeneration.toDouble())
                putLongOrNull(
                    "lastLogcatMatchAtMonotonicMs",
                    lastLogcatMatchAtMonotonicMs.takeIf { it >= 0 },
                )
                putLongOrNull(
                    "lastOverlayLaunchAtMonotonicMs",
                    lastOverlayLaunchAtMonotonicMs.takeIf { it >= 0 },
                )
                putStringOrNull("lastErrorCode", lastLogcatErrorCode?.name)
            }

            val read = ClipboardReadRuntime.snapshot()
            val readMap = Arguments.createMap().apply {
                putLongOrNull(
                    "lastReadAttemptAtMonotonicMs",
                    read.lastReadAttemptAtMonotonicMs,
                )
                putLongOrNull(
                    "lastSuccessfulReadAtMonotonicMs",
                    read.lastSuccessfulReadAtMonotonicMs,
                )
                putStringOrNull("lastReadBackend", read.lastReadBackend?.name)
                putStringOrNull("lastReadResult", read.lastReadResult?.name)
                putDouble("readAttemptCount", read.readAttemptCount.toDouble())
                putDouble("successfulReadCount", read.successfulReadCount.toDouble())
                putDouble("failedReadCount", read.failedReadCount.toDouble())
            }

            promise.resolve(
                Arguments.createMap().apply {
                    putBoolean("requested", isListening)
                    putMap("ordinaryListener", ordinaryMap)
                    putMap("legacyLogcatOverlay", logcatMap)
                    putMap("clipboardRead", readMap)
                },
            )
        } catch (exception: Exception) {
            promise.reject(
                "ACQUISITION_SNAPSHOT_ERROR",
                "Failed to obtain acquisition snapshot",
                exception,
            )
        }
    }

    private fun startLegacyLogcatMonitoringIfAvailable(): Boolean {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            lastLogcatErrorCode = null
            return false
        }
        if (!hasReadLogsPermission()) {
            lastLogcatErrorCode = BackendReasonCode.READ_LOGS_PERMISSION_REQUIRED
            return false
        }
        if (!hasOverlayPermission()) {
            lastLogcatErrorCode = BackendReasonCode.OVERLAY_PERMISSION_REQUIRED
            return false
        }
        if (logcatThread?.isAlive == true) {
            return true
        }

        logcatGeneration += 1
        val generation = logcatGeneration
        lastLogcatErrorCode = null
        val thread = Thread {
            runLegacyLogcatMonitor(generation)
        }.apply {
            name = "ClipCascade-ClipboardLogcat-$generation"
            isDaemon = true
        }
        logcatThread = thread
        thread.start()
        return true
    }

    private fun runLegacyLogcatMonitor(generation: Long) {
        var process: Process? = null
        try {
            val timeStamp = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.getDefault(),
            ).format(Date())
            process = Runtime.getRuntime().exec(
                arrayOf(
                    "logcat",
                    "-T",
                    timeStamp,
                    "ClipboardService:E",
                    "*:S",
                ),
            )

            synchronized(this) {
                if (generation != logcatGeneration) {
                    process.destroy()
                    return
                }
                logcatProcess = process
            }

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                while (
                    generation == logcatGeneration &&
                    !Thread.currentThread().isInterrupted
                ) {
                    val line = reader.readLine() ?: break
                    if (!line.contains(BuildConfig.APPLICATION_ID)) {
                        continue
                    }

                    val now = SystemClock.elapsedRealtime()
                    lastLogcatMatchAtMonotonicMs = now
                    if (now - lastActivityStartTime <= activityDebounceTime) {
                        continue
                    }
                    lastActivityStartTime = now

                    if (!hasOverlayPermission()) {
                        lastLogcatErrorCode = BackendReasonCode.OVERLAY_PERMISSION_REQUIRED
                        ClipboardReadRuntime.recordExternalResult(
                            AcquisitionBackendId.LOGCAT_OVERLAY,
                            ClipboardReadResultCode.FOCUS_REQUIRED,
                        )
                        continue
                    }

                    try {
                        reactApplicationContext.startActivity(
                            ClipboardFloatingActivity.getIntent(
                                reactApplicationContext,
                            ),
                        )
                        lastOverlayLaunchAtMonotonicMs = now
                        lastLogcatErrorCode = null
                    } catch (_: SecurityException) {
                        lastLogcatErrorCode = BackendReasonCode.OVERLAY_PERMISSION_REQUIRED
                        ClipboardReadRuntime.recordExternalResult(
                            AcquisitionBackendId.LOGCAT_OVERLAY,
                            ClipboardReadResultCode.FOCUS_REQUIRED,
                        )
                    } catch (exception: Exception) {
                        lastLogcatErrorCode = BackendReasonCode.BACKEND_START_FAILED
                        Log.e(TAG, "Failed to launch clipboard overlay", exception)
                    }
                }
            }
        } catch (exception: Exception) {
            if (generation == logcatGeneration) {
                lastLogcatErrorCode = BackendReasonCode.BACKEND_START_FAILED
                Log.e(TAG, "Legacy logcat monitor failed", exception)
            }
        } finally {
            try {
                process?.destroy()
            } catch (_: Exception) {
            }
            synchronized(this) {
                if (generation == logcatGeneration) {
                    if (logcatProcess === process) {
                        logcatProcess = null
                    }
                    if (logcatThread === Thread.currentThread()) {
                        logcatThread = null
                    }
                }
            }
        }
    }

    private fun hasReadLogsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            reactApplicationContext,
            Manifest.permission.READ_LOGS,
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasOverlayPermission(): Boolean =
        Settings.canDrawOverlays(reactApplicationContext)

    @ReactMethod
    fun addListener(type: String?) {
        // Required for React Native NativeEventEmitter.
    }

    @ReactMethod
    fun removeListeners(type: Int?) {
        // Required for React Native NativeEventEmitter.
    }

    private fun WritableMap.putLongOrNull(key: String, value: Long?) {
        if (value == null) {
            putNull(key)
        } else {
            putDouble(key, value.toDouble())
        }
    }

    private fun WritableMap.putStringOrNull(key: String, value: String?) {
        if (value == null) {
            putNull(key)
        } else {
            putString(key, value)
        }
    }

    private companion object {
        const val TAG = "ClipCascadeCapture"
    }
}
