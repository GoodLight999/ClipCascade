package com.clipcascade

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule

object OtpRelayDispatcher {
    private const val TAG = "OtpRelayDispatcher"
    private const val EVENT_NAME = "SHARED_TEXT"
    private const val RETRY_DELAY_MS = 5_000L
    private const val ACK_TIMEOUT_MS = 15_000L
    private const val MAX_BATCH_SIZE = 1

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
            if (OtpRelayStore.pendingCount(context) > 0) {
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
        val applicationContext = context.applicationContext
        OtpRelayStore.markDelivered(applicationContext, listOf(relayId))
        if (relayId.startsWith(OtpTestStatusStore.RELAY_ID_PREFIX)) {
            OtpTestStatusStore.acknowledged(applicationContext, relayId)
            RelayHealthStore.record(
                applicationContext,
                category = "verification",
                trigger = "test_notification",
                path = "local_extractor",
                result = "test_acknowledged",
            )
        }
        if (inFlightId == relayId) {
            inFlightId = null
            inFlightSince = 0L
        }
        return true
    }

    @Synchronized
    fun tryDispatch(context: Context): Int {
        val applicationContext = context.applicationContext
        if (!RelaySettingsStore.codeRelayEnabled(applicationContext)) {
            OtpRelayStore.clear(applicationContext)
            inFlightId = null
            inFlightSince = 0L
            return 0
        }

        val now = System.currentTimeMillis()
        val currentInFlight = inFlightId
        if (currentInFlight != null) {
            if (now - inFlightSince < ACK_TIMEOUT_MS) return 0
            Log.w(TAG, "Transport acknowledgement timed out; retrying relay item")
            inFlightId = null
            inFlightSince = 0L
        }

        val asyncStorage = AsyncStorageBridge(applicationContext)
        try {
            if (!transportIsEnabled(asyncStorage)) return 0

            val status = asyncStorage.getValue("wsStatusMessage").orEmpty()
            if (!isConnectedStatus(status)) {
                RecoveryCoordinator.request(applicationContext, "otp_transport_not_ready")
                return 0
            }

            val mode = asyncStorage.getValue("server_mode").orEmpty()
            if (mode.equals("P2P", ignoreCase = true)) {
                val p2pStatus = asyncStorage.getValue("p2pStatusMessage").orEmpty()
                val peers = Regex("Peers:\\s*(\\d+)")
                    .find(p2pStatus)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 0
                if (peers <= 0) return 0
            }

            val application = applicationContext as? MainApplication
            if (application == null) {
                RecoveryCoordinator.request(applicationContext, "otp_application_context_missing")
                return 0
            }
            val reactContext = application.reactNativeHost
                .reactInstanceManager
                .currentReactContext
            if (reactContext == null || !reactContext.hasActiveCatalystInstance()) {
                RecoveryCoordinator.request(applicationContext, "otp_react_context_missing")
                return 0
            }

            val pending = OtpRelayStore.pending(applicationContext, MAX_BATCH_SIZE)
            if (pending.isEmpty()) return 0

            val item = pending.first()
            inFlightId = item.id
            inFlightSince = now
            return try {
                val params = Arguments.createMap().apply {
                    putString("text", item.code)
                    putString("relayId", item.id)
                    putString("source", "notification_code")
                }
                reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit(EVENT_NAME, params)
                1
            } catch (error: Exception) {
                inFlightId = null
                inFlightSince = 0L
                RecoveryCoordinator.request(applicationContext, "otp_react_event_failed")
                throw error
            }
        } catch (error: Exception) {
            Log.w(TAG, "React event delivery failed; leaving value queued", error)
            return 0
        } finally {
            asyncStorage.disconnect()
        }
    }

    private fun transportIsEnabled(storage: AsyncStorageBridge): Boolean =
        storage.getValue("wsIsRunning") == "true"

    private fun isConnectedStatus(status: String): Boolean {
        val normalized = status.trim().removePrefix("✅").trimStart()
        return normalized.equals("Connected", ignoreCase = true) ||
            normalized.startsWith("Connected -", ignoreCase = true)
    }
}
