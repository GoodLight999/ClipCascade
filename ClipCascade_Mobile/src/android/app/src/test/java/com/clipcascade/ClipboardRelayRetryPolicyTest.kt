package com.clipcascade

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardRelayRetryPolicyTest {
    @Test
    fun idleRetryBacksOffWithoutExceedingCap() {
        var delay = ClipboardRelayRetryPolicy.INITIAL_DELAY_MS
        delay = ClipboardRelayRetryPolicy.nextDelayMs(delay, waitingForAck = false)
        assertEquals(6_000L, delay)
        delay = ClipboardRelayRetryPolicy.nextDelayMs(delay, waitingForAck = false)
        assertEquals(12_000L, delay)
        delay = ClipboardRelayRetryPolicy.nextDelayMs(delay, waitingForAck = false)
        assertEquals(ClipboardRelayRetryPolicy.MAX_IDLE_DELAY_MS, delay)
        delay = ClipboardRelayRetryPolicy.nextDelayMs(delay, waitingForAck = false)
        assertEquals(ClipboardRelayRetryPolicy.MAX_IDLE_DELAY_MS, delay)
    }

    @Test
    fun acknowledgementWaitKeepsPromptCadence() {
        assertEquals(
            ClipboardRelayRetryPolicy.INITIAL_DELAY_MS,
            ClipboardRelayRetryPolicy.nextDelayMs(
                currentDelayMs = ClipboardRelayRetryPolicy.MAX_IDLE_DELAY_MS,
                waitingForAck = true,
            ),
        )
    }
}
