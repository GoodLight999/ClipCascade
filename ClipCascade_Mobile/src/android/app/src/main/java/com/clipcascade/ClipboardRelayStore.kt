package com.clipcascade

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ClipboardRelayStore {
    private const val PREFS = "clipboard_relay_queue"
    private const val KEY_QUEUE = "items"
    private const val MAX_ITEMS = 16
    private const val MAX_TEXT_LENGTH = 500_000
    private const val TTL_MS = 10 * 60_000L
    private const val DEDUP_MS = 2_000L

    data class Item(
        val id: String,
        val text: String,
        val sourcePackage: String,
        val createdAt: Long,
    )

    @Synchronized
    fun enqueue(context: Context, item: Item): Boolean {
        val normalized = item.text.take(MAX_TEXT_LENGTH)
        if (normalized.isBlank()) return false

        val active = activeItems(context).toMutableList()
        if (active.any {
                it.text == normalized &&
                    it.sourcePackage == item.sourcePackage &&
                    item.createdAt - it.createdAt in 0..DEDUP_MS
            }
        ) {
            return false
        }

        active += item.copy(text = normalized)
        while (active.size > MAX_ITEMS) active.removeAt(0)
        write(context, active)
        return true
    }

    @Synchronized
    fun pending(context: Context): Item? = activeItems(context).firstOrNull()

    @Synchronized
    fun remove(context: Context, id: String) {
        write(context, activeItems(context).filterNot { it.id == id })
    }

    @Synchronized
    fun count(context: Context): Int = activeItems(context).size

    private fun activeItems(context: Context): List<Item> {
        val now = System.currentTimeMillis()
        val all = read(context)
        val active = all.filter { now - it.createdAt in 0..TTL_MS }
        if (active.size != all.size) write(context, active)
        return active
    }

    private fun read(context: Context): List<Item> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_QUEUE, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optJSONObject(index) ?: continue
                    val id = value.optString("id")
                    val text = value.optString("text")
                    if (id.isBlank() || text.isBlank()) continue
                    add(
                        Item(
                            id = id,
                            text = text,
                            sourcePackage = value.optString("sourcePackage"),
                            createdAt = value.optLong("createdAt"),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun write(context: Context, items: List<Item>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("text", item.text)
                    put("sourcePackage", item.sourcePackage)
                    put("createdAt", item.createdAt)
                },
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_QUEUE, array.toString()).apply()
    }
}
