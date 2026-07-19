package com.clipcascade

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule

object ClipboardRelayDispatcher {
    private const val TAG = "ClipboardRelayDispatcher"
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

    @Volatile
    private var retryDelayMs = ClipboardRelayRetryPolicy.INITIAL_DELAY_MS

    private val retryTask = object : Runnable {
        override fun run() {
            val context = appContext
            if (context == null) {
                scheduled = false
                return
            }

            tryDispatch(context)
            if (ClipboardRelayStore.count(context) > 0) {
                val waitingForAck = inFlightId != null
                val delay = if (waitingForAck) {
                    ClipboardRelayRetryPolicy.INITIAL_DELAY_MS
                } else {
                    retryDelayMs
                }
                retryDelayMs = ClipboardRelayRetryPolicy.nextDelayMs(
                    currentDelayMs = delay,
                    waitingForAck = waitingForAck,
                )
                handler.postDelayed(this, delay)
            } else {
                scheduled = false
                appContext = null
                inFlightId = null
                inFlightSince = 0L
                retryDelayMs = ClipboardRelayRetryPolicy.INITIAL_DELAY_MS
            }
        }
    }

    @Synchronized
    fun schedule(context: Context) {
        appContext = context.applicationContext
        retryDelayMs = ClipboardRelayRetryPolicy.INITIAL_DELAY_MS
        if (scheduled) {
            // A new item, reconnect, or recovery event should not wait behind an
            // old idle-backoff timer.
            handler.removeCallbacks(retryTask)
            handler.post(retryTask)
            return
        }
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
        record(context, "peer_ack", "acknowledged")
        // Drain the next durable item immediately rather than waiting for the
        // previous retry timer.
        schedule(context.applicationContext)
        return true
    }

    @Synchronized
    private fun tryDispatch(context: Context): Boolean {
        if (!RelaySettingsStore.clipboardEnabled(context)) {
            ClipboardRelayStore.clear(context)
            inFlightId = null
            inFlightSince = 0L
            record(context, "settings", "sync_disabled")
            return false
        }

        val now = System.currentTimeMillis()
        val currentInFlight = inFlightId
        if (currentInFlight != null) {
            if (now - inFlightSince < ACK_TIMEOUT_MS) return false
            Log.w(TAG, "Transport acknowledgement timed out; retrying relay item")
            record(context, "peer_ack", "retrying")
            inFlightId = null
            inFlightSince = 0L
        }

        val storage = AsyncStorageBridge(context)
        try {
            if (storage.getValue("wsIsRunning") != "true") {
                record(context, "transport_enabled", "sync_disabled")
                return false
            }

            val status = storage.getValue("wsStatusMessage").orEmpty()
            if (!isConnectedStatus(status)) {
                record(context, "transport_status", "retrying")
                RecoveryCoordinator.request(context, "clipboard_transport_not_ready")
                return false
            }

            val mode = storage.getValue("server_mode").orEmpty()
            if (mode.equals("P2P", ignoreCase = true)) {
                val p2pStatus = storage.getValue("p2pStatusMessage").orEmpty()
                val peers = Regex("Peers:\\s*(\\d+)")
                    .find(p2pStatus)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 0
                // Signaling can be healthy before another peer is online. Keep the
                // item queued without restarting a healthy transport in this case.
                if (peers <= 0) {
                    record(context, "p2p_peer", "retrying")
                    return false
                }
            }

            val application = context.applicationContext as? MainApplication
            if (application == null) {
                record(context, "react_context", "rebind_requested")
                RecoveryCoordinator.request(context, "clipboard_application_context_missing")
                return false
            }
            val reactContext = application.reactNativeHost
                .reactInstanceManager
                .currentReactContext
            if (reactContext == null || !reactContext.hasActiveCatalystInstance()) {
                record(context, "react_context", "rebind_requested")
                RecoveryCoordinator.request(context, "clipboard_react_context_missing")
                return false
            }

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
                record(context, "react_event", "emitted")
                true
            } catch (error: Exception) {
                inFlightId = null
                inFlightSince = 0L
                record(context, "react_event", "interrupted")
                RecoveryCoordinator.request(context, "clipboard_react_event_failed")
                throw error
            }
        } catch (error: Exception) {
            Log.w(TAG, "Clipboard relay is not ready", error)
            record(context, "dispatcher", "interrupted")
            return false
        } finally {
            storage.disconnect()
        }
    }

    private fun record(context: Context, path: String, result: String) {
        RelayHealthStore.record(
            context.applicationContext,
            category = "clipboard",
            trigger = "delivery",
            path = path,
            result = result,
        )
    }

    private fun isConnectedStatus(status: String): Boolean {
        val normalized = status.trim().removePrefix("✅").trimStart()
        return normalized.equals("Connected", ignoreCase = true) ||
            normalized.startsWith("Connected -", ignoreCase = true)
    }
}
