package com.clipcascade

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.clipcascade.acquisition.AndroidClipboardChangeRegistrar
import com.clipcascade.acquisition.BackendReasonCode
import com.clipcascade.acquisition.BackendStartCode
import com.clipcascade.acquisition.BackendStopCode
import com.clipcascade.acquisition.ClipboardReadResultCode
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
    private val clipboardManager = reactContext.getSystemService(
        Context.CLIPBOARD_SERVICE,
    ) as ClipboardManager

    private val ordinaryBackend = OrdinaryClipboardBackend(
        registrar = AndroidClipboardChangeRegistrar(clipboardManager),
        clock = MonotonicClock { SystemClock.elapsedRealtime() },
    )

    @Volatile
    private var isListening = false

    @Volatile
    private var lastEmittedTime = 0L

    @Volatile
    private var lastActivityStartTime = 0L

    private val debounceTime = 0L
    private val activityDebounceTime = 1_000L

    @Volatile
    private var stopLogcat = false

    @Volatile
    private var logcatThread: Thread? = null

    @Volatile
    private var logcatProcess: Process? = null

    @Volatile
    private var lastLogcatMatchAtMonotonicMs = -1L

    @Volatile
    private var lastOverlayLaunchAtMonotonicMs = -1L

    @Volatile
    private var lastLogcatErrorCode: BackendReasonCode? = null

    @Volatile
    private var lastReadAttemptAtMonotonicMs = -1L

    @Volatile
    private var lastSuccessfulReadAtMonotonicMs = -1L

    @Volatile
    private var lastReadResult: ClipboardReadResultCode? = null

    override fun getName(): String = "ClipboardListener"

    @ReactMethod
    @Synchronized
    fun startListening() {
        if (isListening) {
            return
        }

        val ordinaryResult = ordinaryBackend.start {
            emitCurrentClipboard()
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

        stopLogcat = true
        try {
            logcatThread?.interrupt()
        } catch (_: Exception) {
        }
        try {
            logcatProcess?.destroy()
        } catch (_: Exception) {
        }
        logcatThread = null
        logcatProcess = null
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

            val hasReadLogs = hasReadLogsPermission()
            val logcatMap = Arguments.createMap().apply {
                putString("backendId", "LOGCAT_OVERLAY")
                putBoolean("sdkEligible", Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                putBoolean("readLogsGranted", hasReadLogs)
                putBoolean(
                    "overlayGranted",
                    Settings.canDrawOverlays(reactApplicationContext),
                )
                putBoolean("threadAlive", logcatThread?.isAlive == true)
                putBoolean("processAlive", logcatProcess?.isAlive == true)
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

            val readMap = Arguments.createMap().apply {
                putLongOrNull(
                    "lastReadAttemptAtMonotonicMs",
                    lastReadAttemptAtMonotonicMs.takeIf { it >= 0 },
                )
                putLongOrNull(
                    "lastSuccessfulReadAtMonotonicMs",
                    lastSuccessfulReadAtMonotonicMs.takeIf { it >= 0 },
                )
                putStringOrNull("lastReadResult", lastReadResult?.name)
            }

            val root = Arguments.createMap().apply {
                putBoolean("requested", isListening)
                putMap("ordinaryListener", ordinaryMap)
                putMap("legacyLogcatOverlay", logcatMap)
                putMap("clipboardRead", readMap)
            }
            promise.resolve(root)
        } catch (exception: Exception) {
            promise.reject(
                "ACQUISITION_SNAPSHOT_ERROR",
                "Failed to obtain acquisition snapshot",
                exception,
            )
        }
    }

    private fun startLegacyLogcatMonitoringIfAvailable(): Boolean {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ||
            !hasReadLogsPermission()
        ) {
            return false
        }
        if (logcatThread?.isAlive == true) {
            return true
        }

        stopLogcat = false
        lastLogcatErrorCode = null
        logcatThread = Thread {
            try {
                val timeStamp = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss.SSS",
                    Locale.getDefault(),
                ).format(Date())
                logcatProcess = Runtime.getRuntime().exec(
                    arrayOf(
                        "logcat",
                        "-T",
                        timeStamp,
                        "ClipboardService:E",
                        "*:S",
                    ),
                )
                BufferedReader(
                    InputStreamReader(logcatProcess!!.inputStream),
                ).use { reader ->
                    var line: String?
                    while (!stopLogcat && reader.readLine().also { line = it } != null) {
                        if (line?.contains(BuildConfig.APPLICATION_ID) == true) {
                            val now = SystemClock.elapsedRealtime()
                            lastLogcatMatchAtMonotonicMs = now
                            if (now - lastActivityStartTime > activityDebounceTime) {
                                lastActivityStartTime = now
                                lastOverlayLaunchAtMonotonicMs = now
                                reactApplicationContext.startActivity(
                                    ClipboardFloatingActivity.getIntent(
                                        reactApplicationContext,
                                    ),
                                )
                            }
                        }
                    }
                }
            } catch (exception: Exception) {
                if (!stopLogcat) {
                    lastLogcatErrorCode = BackendReasonCode.BACKEND_START_FAILED
                    Log.e(TAG, "Legacy logcat monitor failed", exception)
                }
            } finally {
                try {
                    logcatProcess?.destroy()
                } catch (_: Exception) {
                }
                logcatProcess = null
                stopLogcat = false
            }
        }.apply {
            name = "ClipCascade-ClipboardLogcat"
            isDaemon = true
            start()
        }
        return true
    }

    private fun hasReadLogsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            reactApplicationContext,
            Manifest.permission.READ_LOGS,
        ) == PackageManager.PERMISSION_GRANTED

    private fun emitCurrentClipboard() {
        lastReadAttemptAtMonotonicMs = SystemClock.elapsedRealtime()
        try {
            val clip = clipboardManager.primaryClip
            if (clip == null || clip.itemCount <= 0) {
                lastReadResult = ClipboardReadResultCode.EMPTY
                return
            }

            val description = clip.description
            val mimeType = description?.getMimeType(0)
            if (mimeType == null) {
                lastReadResult = ClipboardReadResultCode.UNSUPPORTED_CONTENT
                return
            }

            val item = clip.getItemAt(0)
            val params = Arguments.createMap()
            when {
                mimeType.startsWith("text/") && item.text != null -> {
                    params.putString("content", item.text.toString())
                    params.putString("type", "text")
                }

                mimeType.startsWith("image/") && item.uri != null -> {
                    params.putString("content", item.uri.toString())
                    params.putString("type", "image")
                }

                item.uri != null -> {
                    params.putString("content", item.uri.toString())
                    params.putString("type", "files")
                }

                else -> {
                    lastReadResult = ClipboardReadResultCode.UNSUPPORTED_CONTENT
                    return
                }
            }

            if (sendEventToJS(params)) {
                lastReadResult = ClipboardReadResultCode.SUCCESS
                lastSuccessfulReadAtMonotonicMs = SystemClock.elapsedRealtime()
            } else {
                lastReadResult = ClipboardReadResultCode.REACT_CONTEXT_UNAVAILABLE
            }
        } catch (exception: SecurityException) {
            lastReadResult = ClipboardReadResultCode.ACCESS_DENIED
            Log.w(TAG, "Clipboard read was denied")
        } catch (exception: Exception) {
            lastReadResult = ClipboardReadResultCode.READ_FAILED
            Log.e(TAG, "Clipboard read failed", exception)
        }
    }

    private fun sendEventToJS(params: WritableMap): Boolean {
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastEmittedTime <= debounceTime) {
            return true
        }

        return try {
            reactApplicationContext
                .getJSModule(
                    DeviceEventManagerModule.RCTDeviceEventEmitter::class.java,
                )
                .emit("onClipboardChange", params)
            lastEmittedTime = currentTime
            true
        } catch (exception: Exception) {
            Log.w(TAG, "React Native clipboard event delivery failed", exception)
            false
        }
    }

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
