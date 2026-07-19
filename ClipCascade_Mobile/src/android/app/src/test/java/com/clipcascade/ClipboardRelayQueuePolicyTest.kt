package com.clipcascade

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardRelayQueuePolicyTest {
    @Test
    fun boundedQueueAcceptsOnlyBelowCapacity() {
        assertTrue(ClipboardRelayQueuePolicy.hasCapacity(0))
        assertTrue(
            ClipboardRelayQueuePolicy.hasCapacity(
                ClipboardRelayQueuePolicy.MAX_ITEMS - 1,
            ),
        )
        assertFalse(
            ClipboardRelayQueuePolicy.hasCapacity(
                ClipboardRelayQueuePolicy.MAX_ITEMS,
            ),
        )
        assertFalse(
            ClipboardRelayQueuePolicy.hasCapacity(
                ClipboardRelayQueuePolicy.MAX_ITEMS + 1,
            ),
        )
    }

    @Test
    fun invalidNegativeCountIsRejected() {
        assertFalse(ClipboardRelayQueuePolicy.hasCapacity(-1))
    }
}
