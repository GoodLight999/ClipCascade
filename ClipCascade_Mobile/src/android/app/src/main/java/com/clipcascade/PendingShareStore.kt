package com.clipcascade

import java.util.ArrayDeque

/**
 * Process-local, bounded handoff from Android share intents to the JavaScript
 * foreground-service listener. React Native events are wake signals only; the
 * payload remains here until JavaScript atomically drains it.
 */
object PendingShareStore {
    const val EVENT_AVAILABLE = "SHARED_EVENT_AVAILABLE"
    internal const val MAX_PENDING_EVENTS = 64

    data class PendingShareEvent(
        val eventName: String,
        val key: String,
        val value: String
    )

    private val lock = Any()
    private val events = ArrayDeque<PendingShareEvent>()

    /** Returns true when the oldest event had to be discarded at the bound. */
    fun enqueue(eventName: String, key: String, value: String): Boolean =
        synchronized(lock) {
            val droppedOldest = events.size >= MAX_PENDING_EVENTS
            if (droppedOldest) {
                events.removeFirst()
            }
            events.addLast(PendingShareEvent(eventName, key, value))
            droppedOldest
        }

    fun drain(): List<PendingShareEvent> = synchronized(lock) {
        if (events.isEmpty()) return@synchronized emptyList()
        val drained = events.toList()
        events.clear()
        drained
    }
}
