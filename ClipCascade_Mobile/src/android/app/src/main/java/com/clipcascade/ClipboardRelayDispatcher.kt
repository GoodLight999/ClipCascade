package com.clipcascade

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule

object ClipboardRelayDispatcher {
    private const val TAG = "ClipboardRelayDispatcher"
    private const val RETRY_DELAY_MS = 3_000L
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var scheduled = false

    @Volatile
    private var appContext: Context? = null

    private val retryTask = object : Runnable {
        override fun run() {
            val context = appContext
            if (context == null) {
                scheduled = false
                return
            }

            tryDispatch(context)
            if (ClipboardRelayStore.count(context) > 0) {
                handler.postDelayed(this, RETRY_DELAY_MS)
            } else {
                scheduled = false
                appContext = null
            }
        }
    }

    @Synchronized
    fun schedule(context: Context) {
        appContext = context.applicationContext
        if (scheduled) return
        scheduled = true
        handler.post(retryTask)
    }

    private fun tryDispatch(context: Context): Boolean {
        val storage = AsyncStorageBridge(context)
        try {
            if (storage.getValue("wsIsRunning") != "true") return false
            val status = storage.getValue("wsStatusMessage").orEmpty()
            if (!status.contains("Connected", ignoreCase = true)) return false

            val mode = storage.getValue("server_mode").orEmpty()
            if (mode.equals("P2P", ignoreCase = true)) {
                val p2pStatus = storage.getValue("p2pStatusMessage").orEmpty()
                val peers = Regex("Peers:\\s*(\\d+)")
                    .find(p2pStatus)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 0
                if (peers <= 0) return false
            }

            val application = context.applicationContext as? MainApplication ?: return false
            val reactContext = application.reactNativeHost
                .reactInstanceManager
                .currentReactContext
                ?: return false
            if (!reactContext.hasActiveCatalystInstance()) return false

            val item = ClipboardRelayStore.pending(context) ?: return true
            val params = Arguments.createMap().apply {
                putString("text", item.text)
                putString("source", "accessibility_clipboard")
                putString("sourcePackage", item.sourcePackage)
                putString("relayId", item.id)
            }
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("SHARED_TEXT", params)
            ClipboardRelayStore.remove(context, item.id)
            return true
        } catch (error: Exception) {
            Log.w(TAG, "Clipboard relay is not ready", error)
            return false
        } finally {
            storage.disconnect()
        }
    }
}
