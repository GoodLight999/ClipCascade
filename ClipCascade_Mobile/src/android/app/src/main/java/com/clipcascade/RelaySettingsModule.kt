package com.clipcascade

import android.content.Intent
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
}
