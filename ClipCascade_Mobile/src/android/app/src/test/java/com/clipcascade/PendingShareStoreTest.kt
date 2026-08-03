package com.clipcascade

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingShareStoreTest {
    @After
    fun clearStore() {
        PendingShareStore.drain()
    }

    @Test
    fun drainIsAtomicAndEmptyAfterward() {
        PendingShareStore.enqueue("SHARED_TEXT", "text", "one")
        PendingShareStore.enqueue("SHARED_IMAGE", "image", "two")

        val drained = PendingShareStore.drain()

        assertEquals(listOf("one", "two"), drained.map { it.value })
        assertTrue(PendingShareStore.drain().isEmpty())
    }

    @Test
    fun queueIsBoundedAndDropsOnlyTheOldestEvent() {
        for (index in 0..PendingShareStore.MAX_PENDING_EVENTS) {
            PendingShareStore.enqueue("SHARED_TEXT", "text", index.toString())
        }

        val drained = PendingShareStore.drain()

        assertEquals(PendingShareStore.MAX_PENDING_EVENTS, drained.size)
        assertEquals("1", drained.first().value)
        assertEquals(PendingShareStore.MAX_PENDING_EVENTS.toString(), drained.last().value)
    }
}
