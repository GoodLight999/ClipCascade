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

        // The platform listener is a trigger only. Foreground and background
        // automatic copies use the same Shizuku-first coordinator and the same
        // overlay fallback. This prevents foreground success from hiding a
        // broken background acquisition path.
        listener = ClipboardManager.OnPrimaryClipChangedListener {
            val appOwned = clipboardManager.primaryClipDescription
                ?.extras
                ?.getBoolean(APP_OWNED_CLIP_MARKER)
                ?: false
            if (appOwned) {
                CaptureDiagnostics.recordIgnored(
                    "clipboard_listener",
                    "application_owned_clipboard_write"
                )
            } else {
                BackgroundClipboardCapture.request(
                    reactApplicationContext,
                    "clipboard_listener"
                )
            }
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
            startReadLogsTrigger()
        }
    }

    private fun startReadLogsTrigger() {
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
                BufferedReader(InputStreamReader(logcatProcess!!.inputStream)).use { reader ->
                    while (!stopLogcat) {
                        val line = reader.readLine() ?: break
                        if (line.contains(BuildConfig.APPLICATION_ID)) {
                            BackgroundClipboardCapture.request(
                                reactApplicationContext,
                                "read_logs"
                            )
                        }
                    }
                }
            } catch (error: Exception) {
                if (!stopLogcat) {
                    CaptureDiagnostics.recordFailure("read_logs", "logcat_failed", error)
                    error.printStackTrace()
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
            isDaemon = true
            name = "clipcascade-logcat"
            start()
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
            logcatProcess?.destroy()
        } catch (_: Exception) {
        }
        try {
            logcatThread?.interrupt()
        } catch (_: Exception) {
        }
        logcatThread = null
        logcatProcess = null
    }

    private fun emitExternalContent(content: String, type: String, source: String) {
        val params = Arguments.createMap().apply {
            putString("content", content)
            putString("type", type)
        }
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("onClipboardChange", params)
        CaptureDiagnostics.recordEmission(source)
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
        internal const val APP_OWNED_CLIP_MARKER =
            "com.clipcascade.extra.APPLICATION_OWNED_CLIP"

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
