package com.clipcascade

import android.content.Context

object RelayHealthStore {
    private const val PREFS = "relay_health"

    data class Snapshot(
        val timestamp: Long,
        val trigger: String,
        val path: String,
        val result: String,
    )

    fun record(
        context: Context,
        category: String,
        trigger: String,
        path: String,
        result: String,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong("${category}_timestamp", System.currentTimeMillis())
            .putString("${category}_trigger", trigger)
            .putString("${category}_path", path)
            .putString("${category}_result", result)
            .apply()
    }

    fun read(context: Context, category: String): Snapshot? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong("${category}_timestamp", 0L)
        if (timestamp <= 0L) return null
        return Snapshot(
            timestamp = timestamp,
            trigger = prefs.getString("${category}_trigger", "").orEmpty(),
            path = prefs.getString("${category}_path", "").orEmpty(),
            result = prefs.getString("${category}_result", "").orEmpty(),
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
