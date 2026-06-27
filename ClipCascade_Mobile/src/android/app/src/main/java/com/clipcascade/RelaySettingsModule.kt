package com.clipcascade

import android.content.Intent
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class RelaySettingsModule(
    reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {
    override fun getName(): String = "RelaySettingsModule"

    @ReactMethod
    fun openSettings(promise: Promise) {
        try {
            val intent = Intent(reactApplicationContext, RelaySettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            reactApplicationContext.startActivity(intent)
            promise.resolve(true)
        } catch (error: Exception) {
            promise.reject("RELAY_SETTINGS_ERROR", "Unable to open relay settings", error)
        }
    }

    @ReactMethod
    fun getBuildInfo(promise: Promise) {
        try {
            val map = Arguments.createMap().apply {
                putString("versionName", BuildConfig.VERSION_NAME)
                putString("sourceCommit", BuildConfig.SOURCE_COMMIT)
            }
            promise.resolve(map)
        } catch (error: Exception) {
            promise.reject("BUILD_INFO_ERROR", "Unable to read build information", error)
        }
    }

    @ReactMethod
    fun acknowledgeRelay(relayId: String, source: String, promise: Promise) {
        try {
            val acknowledged = when (source) {
                "accessibility_clipboard" ->
                    ClipboardRelayDispatcher.acknowledge(reactApplicationContext, relayId)
                "notification_code" ->
                    OtpRelayDispatcher.acknowledge(reactApplicationContext, relayId)
                else -> false
            }
            promise.resolve(acknowledged)
        } catch (error: Exception) {
            promise.reject("RELAY_ACK_ERROR", "Unable to acknowledge relay item", error)
        }
    }

    @ReactMethod
    fun resumeRelayQueues(promise: Promise) {
        try {
            ClipboardRelayDispatcher.schedule(reactApplicationContext)
            OtpRelayDispatcher.schedule(reactApplicationContext)
            promise.resolve(true)
        } catch (error: Exception) {
            promise.reject("RELAY_RESUME_ERROR", "Unable to resume relay queues", error)
        }
    }
}
