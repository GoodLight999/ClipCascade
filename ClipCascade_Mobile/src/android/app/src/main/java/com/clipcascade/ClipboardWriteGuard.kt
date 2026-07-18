package com.clipcascade

import android.os.SystemClock

/**
 * Process-local guard for clipboard writes performed by ClipCascade itself.
 *
 * The Accessibility clipboard-change fallback must not interpret an inbound
 * peer write, or the relay's own pre-send write, as a fresh user Copy action.
 * No clipboard contents are retained here.
 */
object ClipboardWriteGuard {
    private const val MARK_TTL_MS = 2_000L

    @Volatile
    private var markedUntilElapsedMs = 0L

    @Synchronized
    fun markInternalWrite() {
        markedUntilElapsedMs = SystemClock.elapsedRealtime() + MARK_TTL_MS
    }

    @Synchronized
    fun consumeIfMarked(): Boolean {
        val marked = SystemClock.elapsedRealtime() <= markedUntilElapsedMs
        if (marked) {
            markedUntilElapsedMs = 0L
        }
        return marked
    }
}
