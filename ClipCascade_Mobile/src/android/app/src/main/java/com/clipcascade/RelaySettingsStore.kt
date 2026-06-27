package com.clipcascade

import android.content.Context

object RelaySettingsStore {
    private const val FILE_NAME = "relay_settings"

    fun clipboardEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getBoolean("clipboard_enabled", true)

    fun setClipboardEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("clipboard_enabled", enabled).apply()
    }

    fun codeRelayEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getBoolean("code_relay_enabled", true)

    fun setCodeRelayEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("code_relay_enabled", enabled).apply()
    }

    fun selectedApps(context: Context): Set<String> =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getStringSet("selected_apps", emptySet())?.toSet() ?: emptySet()

    fun setSelectedApps(context: Context, values: Set<String>) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet("selected_apps", values.toSet()).apply()
    }
}
