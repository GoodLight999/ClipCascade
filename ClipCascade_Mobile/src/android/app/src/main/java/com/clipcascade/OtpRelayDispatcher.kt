package com.clipcascade

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule

/**
 * Delivers queued OTPs through the existing SHARED_TEXT path.
 *
 * StartForegroundService.js already consumes SHARED_TEXT and applies the same
 * encryption, duplicate suppression, P2S/P2P routing, and size checks used for
 * manually shared text.
 */
object OtpRelayDispatcher {
    private const val TAG = "OtpRelayDispatcher"
    private const val EVENT_NAME = "SHARED_TEXT"

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        listOf(0L, 2_000L, 7_000L).forEach { delay ->
            handler.postDelayed({ tryDispatch(appContext) }, delay)
        }
    }

    @Synchronized
    fun tryDispatch(context: Context): Int {
        val appContext = context.applicationContext
        val asyncStorage = AsyncStorageBridge(appContext)
        try {
            // A live sync service owns the SHARED_TEXT listener. Keep the queue
            // intact when syncing is disabled or the JS runtime is unavailable.
            if (asyncStorage.getValue("wsIsRunning") != "true") return 0

            val application = appContext as? MainApplication ?: return 0
            val reactContext = application.reactNativeHost
                .reactInstanceManager
                .currentReactContext
                ?: return 0

            val pending = OtpRelayStore.pending(appContext, limit = 8)
            if (pending.isEmpty()) return 0

            val delivered = mutableListOf<String>()
            for (item in pending) {
                try {
                    val params = Arguments.createMap().apply {
                        putString("text", item.code)
                        putString("source", "otp_notification")
                        putString("sourcePackage", item.sourcePackage)
                    }
                    reactContext
                        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                        .emit(EVENT_NAME, params)
                    delivered += item.id
                } catch (error: Exception) {
                    Log.w(TAG, "React event delivery failed; leaving OTP queued", error)
                    break
                }
            }

            OtpRelayStore.markDelivered(appContext, delivered)
            return delivered.size
        } finally {
            asyncStorage.disconnect()
        }
    }
}
