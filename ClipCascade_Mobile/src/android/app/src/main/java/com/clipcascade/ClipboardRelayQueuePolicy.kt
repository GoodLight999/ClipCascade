package com.clipcascade

/**
 * Capacity policy for the durable ordinary-clipboard queue.
 *
 * Existing unacknowledged items always win. When the bounded queue is full, a
 * new Copy is rejected and diagnosed instead of silently evicting the oldest
 * relay before its defined acknowledgement.
 */
object ClipboardRelayQueuePolicy {
    const val MAX_ITEMS = 16

    fun hasCapacity(pendingCount: Int): Boolean = pendingCount in 0 until MAX_ITEMS
}
