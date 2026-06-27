package com.clipcascade

import android.content.Context
import org.json.JSONObject

/** A one-slot durable buffer for the newest clipboard event. */
object ClipboardEventBuffer {
    private const val PREFS = "clipcascade_clipboard_buffer"
    private const val KEY_EVENT = "pending_event"

    data class Event(val content: String, val type: String)

    @Synchronized
    fun put(context: Context, content: String, type: String) {
        if (content.isBlank() || type.isBlank()) return
        val value = JSONObject().apply {
            put("content", content)
            put("type", type)
        }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EVENT, value.toString())
            .apply()
    }

    @Synchronized
    fun take(context: Context): Event? {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_EVENT, null) ?: return null
        return try {
            val value = JSONObject(raw)
            val content = value.optString("content")
            val type = value.optString("type")
            if (content.isBlank() || type.isBlank()) {
                null
            } else {
                prefs.edit().remove(KEY_EVENT).apply()
                Event(content, type)
            }
        } catch (_: Exception) {
            prefs.edit().remove(KEY_EVENT).apply()
            null
        }
    }
}
