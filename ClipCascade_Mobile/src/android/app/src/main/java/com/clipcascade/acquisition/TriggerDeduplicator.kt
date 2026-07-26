package com.clipcascade.acquisition

/**
 * Suppresses near-simultaneous trigger attempts before clipboard reads.
 * Content-level duplicate suppression remains a separate transport concern.
 */
class TriggerDeduplicator(
    private val duplicateWindowMs: Long = 250,
) {
    init {
        require(duplicateWindowMs >= 0) {
            "duplicateWindowMs must not be negative"
        }
    }

    private var lastAcceptedTrigger: AcquisitionTrigger? = null

    @Synchronized
    fun shouldAttemptRead(trigger: AcquisitionTrigger): Boolean {
        val previous = lastAcceptedTrigger
        if (previous != null && isDuplicate(previous, trigger)) {
            return false
        }
        lastAcceptedTrigger = trigger
        return true
    }

    @Synchronized
    fun reset() {
        lastAcceptedTrigger = null
    }

    private fun isDuplicate(
        previous: AcquisitionTrigger,
        current: AcquisitionTrigger,
    ): Boolean {
        val elapsed = current.monotonicTimestampMs - previous.monotonicTimestampMs
        if (elapsed < 0 || elapsed > duplicateWindowMs) {
            return false
        }

        if (previous.triggerType == TriggerType.EXPLICIT_SELF_TEST ||
            current.triggerType == TriggerType.EXPLICIT_SELF_TEST
        ) {
            return false
        }

        val sameSource = previous.sourcePackage == null ||
            current.sourcePackage == null ||
            previous.sourcePackage == current.sourcePackage

        return sameSource
    }
}
