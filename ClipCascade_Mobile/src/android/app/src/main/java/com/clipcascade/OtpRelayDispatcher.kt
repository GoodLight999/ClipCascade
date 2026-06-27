package com.clipcascade

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule

/**
 * Delivers queued verification values through the existing encrypted text path.
 *
 * Values remain queued while the JavaScript runtime or network transport is not
 * ready. A single retry loop wakes every five seconds until the queue is empty
 * or its items expire in OtpRelayStore.
 */
object OtpRelayDispatcher {
    private const val TAG = "OtpRelayDispatcher"
    private const val EVENT_NAME = "SHARED_TEXT"
    private const val RETRY_DELAY_MS = 5_000L
    private const val MAX_BATCH_SIZE = 1

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
            if (OtpRelayStore.pendingCount(context) > 0) {
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

    @Synchronized
    fun tryDispatch(context: Context): Int {
        val applicationContext = context.applicationContext
        val asyncStorage = AsyncStorageBridge(applicationContext)
        try {
            if (!transportIsReady(asyncStorage)) return 0

            val application = applicationContext as? MainApplication ?: return 0
            val reactContext = application.reactNativeHost
                .reactInstanceManager
                .currentReactContext
                ?: return 0
            if (!reactContext.hasActiveCatalystInstance()) return 0

            val pending = OtpRelayStore.pending(applicationContext, MAX_BATCH_SIZE)
            if (pending.isEmpty()) return 0

            val accepted = mutableListOf<String>()
            for (item in pending) {
                try {
                    val params = Arguments.createMap().apply {
                        putString("text", item.code)
                        putString("relayId", item.id)
                        putString("source", "notification_code")
                        putString("sourcePackage", item.sourcePackage)
                    }
                    reactContext
                        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                        .emit(EVENT_NAME, params)
                    accepted += item.id
                } catch (error: Exception) {
                    Log.w(TAG, "React event delivery failed; leaving value queued", error)
                    break
                }
            }

            // The active foreground service has accepted the event while its
            // transport reports ready. The existing protocol has no server ACK.
            OtpRelayStore.markDelivered(applicationContext, accepted)
            return accepted.size
        } finally {
            asyncStorage.disconnect()
        }
    }

    private fun transportIsReady(storage: AsyncStorageBridge): Boolean {
        if (storage.getValue("wsIsRunning") != "true") return false

        val status = storage.getValue("wsStatusMessage").orEmpty()
        if (!status.contains("Connected", ignoreCase = true)) return false

        val mode = storage.getValue("server_mode").orEmpty()
        if (!mode.equals("P2P", ignoreCase = true)) return true

        val p2pStatus = storage.getValue("p2pStatusMessage").orEmpty()
        val peers = Regex("Peers:\\s*(\\d+)")
            .find(p2pStatus)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
        return peers > 0
    }
}
