package com.clipcascade

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardCopySignalPolicyTest {
    @Test
    fun selectionAloneNeverCaptures() {
        assertFalse(ClipboardCopySignalPolicy.shouldCaptureSelectionEvent())
    }

    @Test
    fun externalClipboardChangeWithRecentSelectionCaptures() {
        assertTrue(
            ClipboardCopySignalPolicy.shouldCaptureClipboardChange(
                internalWrite = false,
                hasSelectedText = true,
                selectionAgeMs = 1_000L,
            ),
        )
    }

    @Test
    fun internalClipboardWriteNeverCaptures() {
        assertFalse(
            ClipboardCopySignalPolicy.shouldCaptureClipboardChange(
                internalWrite = true,
                hasSelectedText = true,
                selectionAgeMs = 1_000L,
            ),
        )
    }

    @Test
    fun staleSelectionNeverCaptures() {
        assertFalse(
            ClipboardCopySignalPolicy.shouldCaptureClipboardChange(
                internalWrite = false,
                hasSelectedText = true,
                selectionAgeMs = ClipboardCopySignalPolicy.SELECTION_TTL_MS + 1L,
            ),
        )
    }

    @Test
    fun semanticActionFallbackRunsOnlyWithoutClipboardCallback() {
        assertTrue(
            ClipboardCopySignalPolicy.shouldRunActionFallback(
                hasSelectedText = true,
                selectionAgeMs = 500L,
                clipboardSerialAtAction = 7L,
                currentClipboardSerial = 7L,
            ),
        )
        assertFalse(
            ClipboardCopySignalPolicy.shouldRunActionFallback(
                hasSelectedText = true,
                selectionAgeMs = 500L,
                clipboardSerialAtAction = 7L,
                currentClipboardSerial = 8L,
            ),
        )
    }
}
