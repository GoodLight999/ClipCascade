package com.clipcascade.acquisition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerDeduplicatorTest {
    private fun trigger(
        timeMs: Long,
        backendId: AcquisitionBackendId = AcquisitionBackendId.ORDINARY_LISTENER,
        type: TriggerType = TriggerType.PRIMARY_CLIP_CHANGED,
        sourcePackage: String? = "example.app",
    ) = AcquisitionTrigger(
        backendId = backendId,
        triggerType = type,
        monotonicTimestampMs = timeMs,
        sourcePackage = sourcePackage,
        confidence = TriggerConfidence.DIRECT,
    )

    @Test
    fun `first trigger is accepted`() {
        val deduplicator = TriggerDeduplicator(250)

        assertTrue(deduplicator.shouldAttemptRead(trigger(1_000)))
    }

    @Test
    fun `near simultaneous triggers from different backends are suppressed`() {
        val deduplicator = TriggerDeduplicator(250)

        assertTrue(
            deduplicator.shouldAttemptRead(
                trigger(
                    1_000,
                    AcquisitionBackendId.ORDINARY_LISTENER,
                ),
            ),
        )
        assertFalse(
            deduplicator.shouldAttemptRead(
                trigger(
                    1_100,
                    AcquisitionBackendId.ACCESSIBILITY,
                    TriggerType.ACCESSIBILITY_COPY_ACTION,
                ),
            ),
        )
    }

    @Test
    fun `trigger after duplicate window is accepted`() {
        val deduplicator = TriggerDeduplicator(250)

        assertTrue(deduplicator.shouldAttemptRead(trigger(1_000)))
        assertTrue(deduplicator.shouldAttemptRead(trigger(1_251)))
    }

    @Test
    fun `different known source package is not suppressed`() {
        val deduplicator = TriggerDeduplicator(250)

        assertTrue(
            deduplicator.shouldAttemptRead(
                trigger(1_000, sourcePackage = "first.app"),
            ),
        )
        assertTrue(
            deduplicator.shouldAttemptRead(
                trigger(1_100, sourcePackage = "second.app"),
            ),
        )
    }

    @Test
    fun `unknown source may still represent duplicate`() {
        val deduplicator = TriggerDeduplicator(250)

        assertTrue(deduplicator.shouldAttemptRead(trigger(1_000)))
        assertFalse(
            deduplicator.shouldAttemptRead(
                trigger(1_100, sourcePackage = null),
            ),
        )
    }

    @Test
    fun `self test trigger is never suppressed`() {
        val deduplicator = TriggerDeduplicator(250)

        assertTrue(deduplicator.shouldAttemptRead(trigger(1_000)))
        assertTrue(
            deduplicator.shouldAttemptRead(
                trigger(
                    1_100,
                    AcquisitionBackendId.MANUAL_SHARE,
                    TriggerType.EXPLICIT_SELF_TEST,
                ),
            ),
        )
    }

    @Test
    fun `clock rollback does not suppress a trigger`() {
        val deduplicator = TriggerDeduplicator(250)

        assertTrue(deduplicator.shouldAttemptRead(trigger(1_000)))
        assertTrue(deduplicator.shouldAttemptRead(trigger(900)))
    }

    @Test
    fun `reset clears prior trigger`() {
        val deduplicator = TriggerDeduplicator(250)

        assertTrue(deduplicator.shouldAttemptRead(trigger(1_000)))
        deduplicator.reset()
        assertTrue(deduplicator.shouldAttemptRead(trigger(1_100)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative duplicate window is rejected`() {
        TriggerDeduplicator(-1)
    }
}
