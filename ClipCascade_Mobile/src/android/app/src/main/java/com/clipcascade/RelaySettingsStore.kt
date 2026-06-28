package com.clipcascade

import android.content.Context

object RelaySettingsStore {
    private const val FILE_NAME = "relay_settings"
    private const val KEY_SCHEMA_VERSION = "queue_schema_version"
    private const val KEY_BACKGROUND_CONFIRMED = "background_operation_confirmed"
    private const val KEY_RELAUNCH_ON_BOOT = "relaunch_on_boot"
    private const val CURRENT_SCHEMA_VERSION = 2

    @Synchronized
    fun ensureCurrentSchema(context: Context) {
        val applicationContext = context.applicationContext
        val preferences = applicationContext.getSharedPreferences(
            FILE_NAME,
            Context.MODE_PRIVATE,
        )
        val storedVersion = preferences.getInt(KEY_SCHEMA_VERSION, 0)
        if (storedVersion >= CURRENT_SCHEMA_VERSION) return

        // Earlier development builds used meaningful relay IDs containing source
        // metadata. Clear only pending queues before enabling opaque UUID IDs.
        ClipboardRelayStore.clear(applicationContext)
        OtpRelayStore.clear(applicationContext)
        preferences.edit()
            .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .commit()
    }

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

    fun backgroundOperationConfirmed(context: Context): Boolean =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BACKGROUND_CONFIRMED, false)

    fun setBackgroundOperationConfirmed(context: Context, confirmed: Boolean) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BACKGROUND_CONFIRMED, confirmed)
            .commit()
    }

    fun relaunchOnBootEnabled(context: Context): Boolean {
        val storage = AsyncStorageBridge(context.applicationContext)
        return try {
            storage.getValue(KEY_RELAUNCH_ON_BOOT) == "true"
        } finally {
            storage.disconnect()
        }
    }

    fun setRelaunchOnBootEnabled(context: Context, enabled: Boolean): Boolean {
        val storage = AsyncStorageBridge(context.applicationContext)
        return try {
            storage.setValue(KEY_RELAUNCH_ON_BOOT, enabled.toString())
        } finally {
            storage.disconnect()
        }
    }
}
