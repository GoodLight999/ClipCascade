package com.clipcascade

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule

class ClipboardListenerModule(
    reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {
    companion object {
        private const val TAG = "ClipboardListener"
        private const val DEBOUNCE_MS = 100L
    }

    private val clipboardManager =
        reactContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var isListening = false
    private var lastEmittedTime = 0L

    override fun getName(): String = "ClipboardListener"

    @ReactMethod
    fun startListening() {
        if (isListening) return

        listener = ClipboardManager.OnPrimaryClipChangedListener {
            try {
                val clip = clipboardManager.primaryClip
                    ?: return@OnPrimaryClipChangedListener
                if (clip.itemCount <= 0) return@OnPrimaryClipChangedListener

                val description = clip.description
                    ?: return@OnPrimaryClipChangedListener
                val mimeType = description.getMimeType(0)
                    ?: return@OnPrimaryClipChangedListener
                val item = clip.getItemAt(0)
                val params: WritableMap = Arguments.createMap()

                when {
                    mimeType.startsWith("text/") && item.text != null -> {
                        // Extended text relay is owned by AccessibilityService so it
                        // receives persistent queueing and transport acknowledgement.
                        // Preserve the upstream listener only when that relay is off.
                        if (RelaySettingsStore.clipboardEnabled(reactApplicationContext)) {
                            return@OnPrimaryClipChangedListener
                        }
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

                    else -> return@OnPrimaryClipChangedListener
                }

                sendEventToJs(params)
            } catch (error: SecurityException) {
                Log.d(TAG, "Android denied direct clipboard access")
            } catch (error: Exception) {
                Log.w(TAG, "Unable to process clipboard change", error)
            }
        }

        clipboardManager.addPrimaryClipChangedListener(listener)
        isListening = true
    }

    @ReactMethod
    fun stopListening() {
        listener?.let(clipboardManager::removePrimaryClipChangedListener)
        listener = null
        isListening = false
    }

    private fun sendEventToJs(params: WritableMap) {
        val now = System.currentTimeMillis()
        if (now - lastEmittedTime < DEBOUNCE_MS) return
        lastEmittedTime = now

        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("onClipboardChange", params)
    }

    @ReactMethod
    fun addListener(type: String?) {
        // Required by React Native event emitter integration.
    }

    @ReactMethod
    fun removeListeners(count: Int?) {
        // Required by React Native event emitter integration.
    }
}
