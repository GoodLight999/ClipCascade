package com.clipcascade

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule

object RecoveryCoordinator {
    private const val TAG = "RecoveryCoordinator"
    private const val EVENT_NAME = "CLIPCASCADE_RECOVERY_REQUEST"
    private const val MIN_REQUEST_INTERVAL_MS = 60_000L

    @Volatile
    private var lastRequestElapsedMs = 0L

    /**
     * Requests one bounded recovery attempt without assuming Android will allow a
     * background foreground-service launch. Returns true when a React event or
     * Headless JS service start request was accepted locally.
     */
    @Synchronized
    fun request(context: Context, reason: String): Boolean {
        val applicationContext = context.applicationContext

        ClipboardRelayDispatcher.schedule(applicationContext)
        OtpRelayDispatcher.schedule(applicationContext)

        val storage = AsyncStorageBridge(applicationContext)
        try {
            if (storage.getValue("wsIsRunning") != "true") {
                RelayHealthStore.record(
                    applicationContext,
                    category = "recovery",
                    trigger = reason,
                    path = "coordinator",
                    result = "sync_disabled",
                )
                return false
            }
        } finally {
            storage.disconnect()
        }

        val now = SystemClock.elapsedRealtime()
        if (
            lastRequestElapsedMs > 0L &&
            now - lastRequestElapsedMs < MIN_REQUEST_INTERVAL_MS
        ) {
            Log.i(TAG, "Recovery request suppressed by cooldown")
            RelayHealthStore.record(
                applicationContext,
                category = "recovery",
                trigger = reason,
                path = "coordinator",
                result = "cooldown",
            )
            return true
        }

        // Bound all attempts, including OS-rejected starts. Without this assignment,
        // a dead background transport could cause every queue retry to start another
        // recovery request and flood the process with failures.
        lastRequestElapsedMs = now

        if (emitToActiveReactContext(applicationContext, reason)) {
            RelayHealthStore.record(
                applicationContext,
                category = "recovery",
                trigger = reason,
                path = "active_react_context",
                result = "requested",
            )
            return true
        }

        return try {
            val intent = Intent(applicationContext, HeadlessTaskService::class.java).apply {
                putExtra("event", "HEALTH_CHECK_FAILED")
                putExtra("reason", reason)
            }
            val component = applicationContext.startService(intent)
            if (component == null) {
                Log.w(TAG, "Android declined the Headless JS recovery start")
                RelayHealthStore.record(
                    applicationContext,
                    category = "recovery",
                    trigger = reason,
                    path = "headless_js",
                    result = "declined",
                )
                false
            } else {
                HeadlessJsTaskService.acquireWakeLockNow(applicationContext)
                RelayHealthStore.record(
                    applicationContext,
                    category = "recovery",
                    trigger = reason,
                    path = "headless_js",
                    result = "requested",
                )
                true
            }
        } catch (error: Exception) {
            Log.w(TAG, "Background recovery start was not allowed", error)
            RelayHealthStore.record(
                applicationContext,
                category = "recovery",
                trigger = reason,
                path = "headless_js",
                result = "blocked",
            )
            false
        }
    }

    private fun emitToActiveReactContext(context: Context, reason: String): Boolean {
        return try {
            val application = context as? MainApplication ?: return false
            val reactContext = application.reactNativeHost
                .reactInstanceManager
                .currentReactContext
                ?: return false
            if (!reactContext.hasActiveCatalystInstance()) return false

            val params = Arguments.createMap().apply {
                putString("reason", reason)
            }
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(EVENT_NAME, params)
            true
        } catch (error: Exception) {
            Log.w(TAG, "Unable to emit recovery request to React Native", error)
            false
        }
    }
}
