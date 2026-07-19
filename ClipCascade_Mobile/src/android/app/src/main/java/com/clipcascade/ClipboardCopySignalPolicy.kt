package com.clipcascade

/**
 * Language-neutral policy for confirming an actual user Copy operation.
 *
 * Selection events and translated UI labels are never sufficient. A copy is
 * confirmed by an OS clipboard-change callback, or by a semantic ACTION_COPY /
 * keyboard fallback when that callback was not observed.
 */
object ClipboardCopySignalPolicy {
    const val SELECTION_TTL_MS = 60_000L

    fun shouldCaptureSelectionEvent(): Boolean = false

    fun shouldCaptureClipboardChange(
        internalWrite: Boolean,
        hasSelectedText: Boolean,
        selectionAgeMs: Long,
    ): Boolean =
        !internalWrite &&
            hasSelectedText &&
            selectionAgeMs in 0..SELECTION_TTL_MS

    fun shouldRunActionFallback(
        hasSelectedText: Boolean,
        selectionAgeMs: Long,
        clipboardSerialAtAction: Long,
        currentClipboardSerial: Long,
    ): Boolean =
        hasSelectedText &&
            selectionAgeMs in 0..SELECTION_TTL_MS &&
            clipboardSerialAtAction == currentClipboardSerial
}
