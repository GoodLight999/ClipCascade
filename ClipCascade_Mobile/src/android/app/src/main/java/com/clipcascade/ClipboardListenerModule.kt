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
import com.clipcascade.acquisition.AcquisitionDiagnosticsSnapshot
import com.clipcascade.acquisition.AcquisitionSelfTestTracker
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
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipboardListenerModule(
    reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {
    private val monotonicClock = MonotonicClock { SystemClock.elapsedRealtime() }

    private val clipboardManager = reactContext.getSystemService(
        Context.CLIPBOARD_SERVICE,
    ) as android.content.ClipboardManager

    private val ordinaryBackend = OrdinaryClipboardBackend(
        registrar = AndroidClipboardChangeRegistrar(clipboardManager),
        clock = monotonicClock,
    )

    private val selfTestTracker = AcquisitionSelfTestTracker(
        clock = monotonicClock,
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

    /**
     * Starts a synthetic native -> React Native -> native acknowledgement.
     * The event contains only a diagnostic test ID and never reads clipboard
     * content or enters the outbound send path.
     */
    @Synchronized
    fun startAcquisitionSelfTest(): String {
        val testId = selfTestTracker.start()
        val event = Arguments.createMap().apply {
            putString("testId", testId)
        }
        try {
            reactApplicationContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(SELF_TEST_EVENT_NAME, event)
        } catch (exception: Exception) {
            selfTestTracker.markEmitFailed(testId)
            throw exception
        }
        return testId
    }

    @ReactMethod
    fun runAcquisitionSelfTest(promise: Promise) {
        try {
            promise.resolve(startAcquisitionSelfTest())
        } catch (exception: Exception) {
            promise.reject(
                "ACQUISITION_SELF_TEST_EMIT_FAILED",
                "Failed to emit acquisition self-test event",
                exception,
            )
        }
    }

    @ReactMethod
    fun acknowledgeAcquisitionSelfTest(testId: String) {
        selfTestTracker.acknowledge(testId)
    }

    /**
     * Native callers and React Native use the same immutable, payload-free
     * snapshot. Keeping one builder prevents UI and support diagnostics from
     * disagreeing about capture health.
     */
    @Synchronized
    fun snapshotForDiagnostics(): AcquisitionDiagnosticsSnapshot {
        val ordinary = ordinaryBackend.snapshot()
        val read = ClipboardReadRuntime.snapshot()
        val selfTest = selfTestTracker.snapshot()
        return AcquisitionDiagnosticsSnapshot(
            requested = isListening,
            ordinaryRunning = ordinary.running,
            ordinaryStartCount = ordinary.startCount,
            ordinaryTriggerCount = ordinary.triggerCount,
            ordinaryLastStartedAtMonotonicMs = ordinary.lastStartedAtMonotonicMs,
            ordinaryLastStoppedAtMonotonicMs = ordinary.lastStoppedAtMonotonicMs,
            ordinaryLastTriggerAtMonotonicMs = ordinary.lastTriggerAtMonotonicMs,
            ordinaryLastErrorCode = ordinary.lastErrorCode,
            logcatSdkEligible = Build.VERSION.SDK_INT > Build.VERSION_CODES.P,
            readLogsGranted = hasReadLogsPermission(),
            overlayGranted = hasOverlayPermission(),
            logcatThreadAlive = logcatThread?.isAlive == true,
            logcatProcessAlive = logcatProcess?.isAlive == true,
            logcatGeneration = logcatGeneration,
            lastLogcatMatchAtMonotonicMs =
                lastLogcatMatchAtMonotonicMs.takeIf { it >= 0 },
            lastOverlayLaunchAtMonotonicMs =
                lastOverlayLaunchAtMonotonicMs.takeIf { it >= 0 },
            logcatLastErrorCode = lastLogcatErrorCode,
            lastReadAttemptAtMonotonicMs = read.lastReadAttemptAtMonotonicMs,
            lastSuccessfulReadAtMonotonicMs = read.lastSuccessfulReadAtMonotonicMs,
            lastReadBackend = read.lastReadBackend,
            lastReadResult = read.lastReadResult,
            readAttemptCount = read.readAttemptCount,
            successfulReadCount = read.successfulReadCount,
            failedReadCount = read.failedReadCount,
            selfTestStatus = selfTest.status,
            selfTestRequestedAtMonotonicMs = selfTest.requestedAtMonotonicMs,
            selfTestAcknowledgedAtMonotonicMs = selfTest.acknowledgedAtMonotonicMs,
            selfTestAttemptCount = selfTest.attemptCount,
            selfTestPassCount = selfTest.passCount,
        )
    }

    @ReactMethod
    fun getAcquisitionSnapshot(promise: Promise) {
        try {
            val snapshot = snapshotForDiagnostics()
            val selfTestMap = Arguments.createMap().apply {
                putString("status", snapshot.selfTestStatus.name)
                putDouble("attemptCount", snapshot.selfTestAttemptCount.toDouble())
                putDouble("passCount", snapshot.selfTestPassCount.toDouble())
                putLongOrNull(
                    "requestedAtMonotonicMs",
                    snapshot.selfTestRequestedAtMonotonicMs,
                )
                putLongOrNull(
                    "acknowledgedAtMonotonicMs",
                    snapshot.selfTestAcknowledgedAtMonotonicMs,
                )
            }

            val ordinaryMap = Arguments.createMap().apply {
                putString("backendId", AcquisitionBackendId.ORDINARY_LISTENER.name)
                putBoolean("running", snapshot.ordinaryRunning)
                putDouble("startCount", snapshot.ordinaryStartCount.toDouble())
                putDouble("triggerCount", snapshot.ordinaryTriggerCount.toDouble())
                putLongOrNull(
                    "lastStartedAtMonotonicMs",
                    snapshot.ordinaryLastStartedAtMonotonicMs,
                )
                putLongOrNull(
                    "lastStoppedAtMonotonicMs",
                    snapshot.ordinaryLastStoppedAtMonotonicMs,
                )
                putLongOrNull(
                    "lastTriggerAtMonotonicMs",
                    snapshot.ordinaryLastTriggerAtMonotonicMs,
                )
                putStringOrNull(
                    "lastErrorCode",
                    snapshot.ordinaryLastErrorCode?.name,
                )
            }

            val logcatMap = Arguments.createMap().apply {
                putString("backendId", AcquisitionBackendId.LOGCAT_OVERLAY.name)
                putBoolean("sdkEligible", snapshot.logcatSdkEligible)
                putBoolean("readLogsGranted", snapshot.readLogsGranted)
                putBoolean("overlayGranted", snapshot.overlayGranted)
                putBoolean("threadAlive", snapshot.logcatThreadAlive)
                putBoolean("processAlive", snapshot.logcatProcessAlive)
                putDouble("generation", snapshot.logcatGeneration.toDouble())
                putLongOrNull(
                    "lastLogcatMatchAtMonotonicMs",
                    snapshot.lastLogcatMatchAtMonotonicMs,
                )
                putLongOrNull(
                    "lastOverlayLaunchAtMonotonicMs",
                    snapshot.lastOverlayLaunchAtMonotonicMs,
                )
                putStringOrNull(
                    "lastErrorCode",
                    snapshot.logcatLastErrorCode?.name,
                )
            }

            val readMap = Arguments.createMap().apply {
                putLongOrNull(
                    "lastReadAttemptAtMonotonicMs",
                    snapshot.lastReadAttemptAtMonotonicMs,
                )
                putLongOrNull(
                    "lastSuccessfulReadAtMonotonicMs",
                    snapshot.lastSuccessfulReadAtMonotonicMs,
                )
                putStringOrNull("lastReadBackend", snapshot.lastReadBackend?.name)
                putStringOrNull("lastReadResult", snapshot.lastReadResult?.name)
                putDouble("readAttemptCount", snapshot.readAttemptCount.toDouble())
                putDouble(
                    "successfulReadCount",
                    snapshot.successfulReadCount.toDouble(),
                )
                putDouble("failedReadCount", snapshot.failedReadCount.toDouble())
            }

            promise.resolve(
                Arguments.createMap().apply {
                    putBoolean("requested", snapshot.requested)
                    putMap("selfTest", selfTestMap)
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
            val startedProcess = Runtime.getRuntime().exec(
                arrayOf(
                    "logcat",
                    "-T",
                    timeStamp,
                    "ClipboardService:E",
                    "*:S",
                ),
            )
            process = startedProcess

            synchronized(this) {
                if (generation != logcatGeneration) {
                    startedProcess.destroy()
                    return
                }
                logcatProcess = startedProcess
            }

            BufferedReader(InputStreamReader(startedProcess.inputStream)).use { reader ->
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
        const val SELF_TEST_EVENT_NAME = "onAcquisitionSelfTest"
    }
}
