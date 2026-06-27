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
    private const val ACK_TIMEOUT_MS = 15_000L
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var scheduled = false

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var inFlightId: String? = null

    @Volatile
    private var inFlightSince = 0L

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
                inFlightId = null
                inFlightSince = 0L
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

    @Synchronized
    fun acknowledge(context: Context, relayId: String): Boolean {
        if (relayId.isBlank()) return false
        ClipboardRelayStore.remove(context.applicationContext, relayId)
        if (inFlightId == relayId) {
            inFlightId = null
            inFlightSince = 0L
        }
        return true
    }

    @Synchronized
    private fun tryDispatch(context: Context): Boolean {
        if (!RelaySettingsStore.clipboardEnabled(context)) {
            ClipboardRelayStore.clear(context)
            inFlightId = null
            inFlightSince = 0L
            return false
        }

        val now = System.currentTimeMillis()
        val currentInFlight = inFlightId
        if (currentInFlight != null) {
            if (now - inFlightSince < ACK_TIMEOUT_MS) return false
            Log.w(TAG, "Transport acknowledgement timed out; retrying relay item")
            inFlightId = null
            inFlightSince = 0L
        }

        val storage = AsyncStorageBridge(context)
        try {
            if (storage.getValue("wsIsRunning") != "true") return false
            val status = storage.getValue("wsStatusMessage").orEmpty()
            if (!isConnectedStatus(status)) return false

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
            inFlightId = item.id
            inFlightSince = now
            return try {
                val params = Arguments.createMap().apply {
                    putString("text", item.text)
                    putString("source", "accessibility_clipboard")
                    putString("sourcePackage", item.sourcePackage)
                    putString("relayId", item.id)
                }
                reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit("SHARED_TEXT", params)
                true
            } catch (error: Exception) {
                inFlightId = null
                inFlightSince = 0L
                throw error
            }
        } catch (error: Exception) {
            Log.w(TAG, "Clipboard relay is not ready", error)
            return false
        } finally {
            storage.disconnect()
        }
    }

    private fun isConnectedStatus(status: String): Boolean {
        val normalized = status.trim().removePrefix("✅").trimStart()
        return normalized.equals("Connected", ignoreCase = true) ||
            normalized.startsWith("Connected -", ignoreCase = true)
    }
}
