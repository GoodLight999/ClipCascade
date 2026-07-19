package com.clipcascade

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object ClipboardRelayStore {
    private const val TAG = "ClipboardRelayStore"
    private const val PREFS = "clipboard_relay_queue"
    private const val KEY_QUEUE = "items"
    private const val MAX_ITEMS = 16
    private const val MAX_TEXT_LENGTH = 500_000
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

        val pending = read(context).toMutableList()
        if (pending.any {
                it.text == normalized &&
                    item.createdAt - it.createdAt in 0..DEDUP_MS
            }
        ) {
            return false
        }

        pending += item.copy(text = normalized)
        while (pending.size > MAX_ITEMS) pending.removeAt(0)
        write(context, pending)
        return true
    }

    @Synchronized
    fun pending(context: Context): Item? = read(context).firstOrNull()

    @Synchronized
    fun remove(context: Context, id: String) {
        write(context, read(context).filterNot { it.id == id })
    }

    @Synchronized
    fun clear(context: Context) {
        write(context, emptyList())
    }

    @Synchronized
    fun count(context: Context): Int = read(context).size

    /**
     * Ordinary clipboard items are retained until acknowledgement or an explicit
     * clear. The queue is already bounded by [MAX_ITEMS], so time-based expiry
     * would only discard an unacknowledged Copy during a long screen-off or peer
     * disconnect interval.
     */
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
        } catch (error: Exception) {
            Log.w(TAG, "Discarding an unreadable clipboard queue", error)
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
        val committed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUEUE, array.toString())
            .commit()
        if (!committed) {
            Log.w(TAG, "Unable to persist clipboard queue state")
        }
    }
}
