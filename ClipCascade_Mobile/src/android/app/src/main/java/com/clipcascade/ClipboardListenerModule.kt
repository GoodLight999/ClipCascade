// android\app\src\main\java\com\clipcascade\ClipboardListenerModule.kt
package com.clipcascade

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipboardListenerModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val clipboardManager: ClipboardManager =
        reactContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var isListening = false
    private var lastEmittedTime: Long = 0
    private var lastActivityStartTime: Long = 0
    private val debounceTime: Long = 0
    private val activityDebounceTime: Long = 1000

    private var stopLogcat = false
    private var logcatThread: Thread? = null
    private var logcatProcess: Process? = null

    init {
        currentInstance = WeakReference(this)
    }

    override fun getName(): String = "ClipboardListener"

    @ReactMethod
    fun startListening() {
        if (isListening) {
            runtimeActive = true
            ShizukuClipboardBridge.ensureBound()
            return
        }

        // Preserve the upstream foreground listener path. It emits directly to
        // the existing React Native event and does not depend on Shizuku,
        // Accessibility, overlay state, or a second native duplicate gate.
        listener = ClipboardManager.OnPrimaryClipChangedListener {
            emitOrdinaryClipboard(clipboardManager.primaryClip)
        }
        clipboardManager.addPrimaryClipChangedListener(listener)
        isListening = true
        runtimeActive = true
        ShizukuClipboardBridge.ensureBound()

        if (
            Build.VERSION.SDK_INT > Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                reactApplicationContext,
                Manifest.permission.READ_LOGS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            stopLogcat = false
            logcatThread = Thread {
                try {
                    val timeStamp = SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss.SSS",
                        Locale.getDefault()
                    ).format(Date())
                    logcatProcess = Runtime.getRuntime().exec(
                        arrayOf("logcat", "-T", timeStamp, "ClipboardService:E", "*:S")
                    )
                    val reader = BufferedReader(InputStreamReader(logcatProcess!!.inputStream))
                    var line: String? = null
                    reader.use { br ->
                        while (!stopLogcat && br.readLine().also { line = it } != null) {
                            if (line!!.contains(BuildConfig.APPLICATION_ID)) {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastActivityStartTime > activityDebounceTime) {
                                    lastActivityStartTime = currentTime
                                    BackgroundClipboardCapture.request(
                                        reactApplicationContext,
                                        "read_logs"
                                    )
                                }
                            }
                        }
                    }
                } catch (error: Exception) {
                    CaptureDiagnostics.recordFailure("read_logs", "logcat_failed", error)
                    error.printStackTrace()
                } finally {
                    try {
                        logcatProcess?.destroy()
                    } catch (_: Exception) {
                    }
                    stopLogcat = false
                }
            }.apply {
                isDaemon = true
                name = "clipcascade-logcat"
                start()
            }
        }
    }

    @ReactMethod
    fun stopListening() {
        runtimeActive = false

        listener?.let {
            clipboardManager.removePrimaryClipChangedListener(it)
            listener = null
        }
        isListening = false

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
    }

    private fun emitOrdinaryClipboard(clip: ClipData?) {
        if (clip == null || clip.itemCount <= 0) return

        val description = clip.description ?: return
        if (description.mimeTypeCount <= 0) return
        val mimeType = description.getMimeType(0) ?: return
        val item = clip.getItemAt(0)
        val params: WritableMap = Arguments.createMap()

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
            item.text != null -> {
                params.putString("content", item.text.toString())
                params.putString("type", "text")
            }
            else -> return
        }

        sendEventToJS(params)
    }

    private fun emitExternalContent(content: String, type: String, source: String) {
        val params: WritableMap = Arguments.createMap().apply {
            putString("content", content)
            putString("type", type)
        }
        sendEventToJS(params)
        CaptureDiagnostics.recordEmission(source)
    }

    private fun sendEventToJS(params: WritableMap) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEmittedTime > debounceTime) {
            lastEmittedTime = currentTime
            reactApplicationContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("onClipboardChange", params)
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

    companion object {
        @Volatile
        private var runtimeActive: Boolean = false

        @Volatile
        private var currentInstance: WeakReference<ClipboardListenerModule>? = null

        @JvmStatic
        fun isRuntimeActive(): Boolean = runtimeActive

        @JvmStatic
        fun emitExternalClipboard(content: String, type: String): Boolean =
            emitExternalClipboard(content, type, "external")

        @JvmStatic
        fun emitExternalClipboard(content: String, type: String, source: String): Boolean {
            if (!runtimeActive) {
                CaptureDiagnostics.recordIgnored(source, "runtime_inactive")
                return false
            }
            val module = currentInstance?.get()
            if (module == null) {
                CaptureDiagnostics.recordIgnored(source, "module_unavailable")
                return false
            }

            return try {
                module.reactApplicationContext.runOnJSQueueThread {
                    if (runtimeActive) {
                        module.emitExternalContent(content, type, source)
                    } else {
                        CaptureDiagnostics.recordIgnored(source, "runtime_stopped_before_emit")
                    }
                }
                true
            } catch (error: Throwable) {
                CaptureDiagnostics.recordFailure(source, "react_emit_schedule_failed", error)
                false
            }
        }
    }
}
