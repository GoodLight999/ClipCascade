package com.clipcascade

import android.content.Context

object RelaySettingsStore {
    private const val FILE_NAME = "relay_settings"

    fun clipboardEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getBoolean("clipboard_enabled", true)

    fun setClipboardEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("clipboard_enabled", enabled)
            .commit()
        if (!enabled) {
            ClipboardRelayStore.clear(context.applicationContext)
        }
    }

    fun codeRelayEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getBoolean("code_relay_enabled", true)

    fun setCodeRelayEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("code_relay_enabled", enabled)
            .commit()
        if (!enabled) {
            OtpRelayStore.clear(context.applicationContext)
        }
    }

    fun selectedApps(context: Context): Set<String> =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getStringSet("selected_apps", emptySet())?.toSet() ?: emptySet()

    fun setSelectedApps(context: Context, values: Set<String>) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet("selected_apps", values.toSet())
            .commit()
        // Values already queued under the previous source policy must not leak
        // after the user narrows or resets the app filter.
        OtpRelayStore.clear(context.applicationContext)
    }
}
