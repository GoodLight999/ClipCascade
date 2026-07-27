package com.clipcascade

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardEmissionGateTest {
    @Test
    fun sameContentFromTwoSourcesIsSuppressedInsideWindow() {
        var now = 1_000L
        val gate = ClipboardEmissionGate(duplicateWindowMs = 1_500L) { now }

        assertTrue(gate.shouldEmit("hello", "text"))
        now += 200L
        assertFalse(gate.shouldEmit("hello", "text"))
    }

    @Test
    fun sameContentCanBeCopiedAgainAfterWindow() {
        var now = 1_000L
        val gate = ClipboardEmissionGate(duplicateWindowMs = 1_500L) { now }

        assertTrue(gate.shouldEmit("hello", "text"))
        now += 1_500L
        assertTrue(gate.shouldEmit("hello", "text"))
    }

    @Test
    fun typeParticipatesInFingerprint() {
        val gate = ClipboardEmissionGate(duplicateWindowMs = 1_500L) { 1_000L }

        assertTrue(gate.shouldEmit("content://example", "image"))
        assertTrue(gate.shouldEmit("content://example", "files"))
    }

    @Test
    fun clockRollbackDoesNotSuppressLegitimateCopy() {
        var now = 2_000L
        val gate = ClipboardEmissionGate(duplicateWindowMs = 1_500L) { now }

        assertTrue(gate.shouldEmit("hello", "text"))
        now = 1_000L
        assertTrue(gate.shouldEmit("hello", "text"))
    }

    @Test
    fun fingerprintSeparatesTypeAndContentBoundaries() {
        assertNotEquals(
            ClipboardEmissionGate.fingerprint("bc", "a"),
            ClipboardEmissionGate.fingerprint("c", "ab")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeWindowIsRejected() {
        ClipboardEmissionGate(duplicateWindowMs = -1L)
    }
}
