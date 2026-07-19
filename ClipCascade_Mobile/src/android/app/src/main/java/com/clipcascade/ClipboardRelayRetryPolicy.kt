package com.clipcascade

/**
 * Retry cadence for a durable clipboard item that has not yet been acknowledged.
 *
 * A missing transport or peer backs off to reduce idle wakeups. Once an item is
 * in flight, the short cadence is retained so the 15-second ACK timeout remains
 * responsive. Any explicit schedule request resets the dispatcher to the initial
 * delay and runs immediately.
 */
object ClipboardRelayRetryPolicy {
    const val INITIAL_DELAY_MS = 3_000L
    const val MAX_IDLE_DELAY_MS = 15_000L

    fun nextDelayMs(currentDelayMs: Long, waitingForAck: Boolean): Long {
        if (waitingForAck) return INITIAL_DELAY_MS
        val bounded = currentDelayMs.coerceIn(INITIAL_DELAY_MS, MAX_IDLE_DELAY_MS)
        return (bounded * 2L).coerceAtMost(MAX_IDLE_DELAY_MS)
    }
}
