// android\app\src\main\java\com\clipcascade\ClipboardListenerModule.kt
package com.clipcascade

import android.Manifest
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
    private val emissionGate = ClipboardEmissionGate()
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

        listener = ClipboardManager.OnPrimaryClipChangedListener {
            emitClip(clipboardManager.primaryClip, "ordinary_listener")
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

    private fun emitClip(clip: android.content.ClipData?, source: String) {
        if (clip == null || clip.itemCount == 0) {
            CaptureDiagnostics.recordIgnored(source, "clipboard_empty")
            return
        }
        val description = clip.description
        val mimeType = if (description.mimeTypeCount > 0) description.getMimeType(0) else ""
        val item = clip.getItemAt(0)

        when {
            item.text != null && mimeType.startsWith("text/") ->
                emitContent(item.text.toString(), "text", source)
            item.uri != null && mimeType.startsWith("image/") ->
                emitContent(item.uri.toString(), "image", source)
            item.uri != null ->
                emitContent(item.uri.toString(), "files", source)
            item.text != null ->
                emitContent(item.text.toString(), "text", source)
            else -> CaptureDiagnostics.recordIgnored(source, "clipboard_type_unsupported")
        }
    }

    private fun emitContent(content: String, type: String, source: String): Boolean {
        if (!emissionGate.shouldEmit(content, type)) {
            CaptureDiagnostics.recordDuplicate(source)
            return false
        }

        val params: WritableMap = Arguments.createMap().apply {
            putString("content", content)
            putString("type", type)
        }
        sendEventToJS(params)
        CaptureDiagnostics.recordEmission(source)
        return true
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
        // Required for RN built-in Event Emitter Calls.
    }

    @ReactMethod
    fun removeListeners(type: Int?) {
        // Required for RN built-in Event Emitter Calls.
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
                        module.emitContent(content, type, source)
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
