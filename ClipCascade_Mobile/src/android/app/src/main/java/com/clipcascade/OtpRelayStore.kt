package com.clipcascade

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small persistent queue for OTPs extracted from notifications.
 *
 * The queue is intentionally independent of React Native/AsyncStorage so a
 * NotificationListenerService can safely enqueue while the JS runtime is
 * suspended or being recreated.
 */
object OtpRelayStore {
    private const val PREFS_NAME = "clipcascade_otp_relay"
    private const val KEY_QUEUE = "pending_items"
    private const val MAX_ITEMS = 32
    private const val DEDUP_WINDOW_MS = 90_000L

    data class Item(
        val id: String,
        val code: String,
        val sourcePackage: String,
        val sourceTitle: String,
        val createdAt: Long,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id)
            put("code", code)
            put("sourcePackage", sourcePackage)
            put("sourceTitle", sourceTitle)
            put("createdAt", createdAt)
        }

        companion object {
            fun fromJson(value: JSONObject): Item? {
                val id = value.optString("id")
                val code = value.optString("code")
                if (id.isBlank() || code.isBlank()) return null

                return Item(
                    id = id,
                    code = code,
                    sourcePackage = value.optString("sourcePackage"),
                    sourceTitle = value.optString("sourceTitle"),
                    createdAt = value.optLong("createdAt"),
                )
            }
        }
    }

    @Synchronized
    fun enqueue(context: Context, item: Item): Boolean {
        val items = readItems(context).toMutableList()
        val duplicate = items.any {
            it.code == item.code &&
                it.sourcePackage == item.sourcePackage &&
                item.createdAt - it.createdAt in 0..DEDUP_WINDOW_MS
        }
        if (duplicate) return false

        items.add(item)
        while (items.size > MAX_ITEMS) {
            items.removeAt(0)
        }
        writeItems(context, items)
        return true
    }

    @Synchronized
    fun pending(context: Context, limit: Int = MAX_ITEMS): List<Item> =
        readItems(context).take(limit.coerceAtLeast(0))

    @Synchronized
    fun markDelivered(context: Context, deliveredIds: Collection<String>) {
        if (deliveredIds.isEmpty()) return
        val idSet = deliveredIds.toHashSet()
        writeItems(context, readItems(context).filterNot { idSet.contains(it.id) })
    }

    @Synchronized
    fun pendingCount(context: Context): Int = readItems(context).size

    private fun readItems(context: Context): List<Item> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_QUEUE, null)
            ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optJSONObject(index) ?: continue
                    Item.fromJson(value)?.let(::add)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeItems(context: Context, items: List<Item>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUEUE, array.toString())
            .apply()
    }
}
