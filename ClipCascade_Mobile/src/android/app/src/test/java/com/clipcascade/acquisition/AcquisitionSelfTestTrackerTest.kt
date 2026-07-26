package com.clipcascade.acquisition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcquisitionSelfTestTrackerTest {
    private class FakeClock(var value: Long = 100) : MonotonicClock {
        override fun nowMs(): Long = value
    }

    @Test
    fun `new tracker is idle`() {
        val tracker = AcquisitionSelfTestTracker(FakeClock())

        assertEquals(AcquisitionSelfTestStatus.IDLE, tracker.snapshot().status)
        assertEquals(0L, tracker.snapshot().attemptCount)
    }

    @Test
    fun `matching acknowledgement passes pending test`() {
        val clock = FakeClock(1_000)
        val tracker = AcquisitionSelfTestTracker(clock)
        val testId = tracker.start()
        clock.value = 1_250

        assertTrue(tracker.acknowledge(testId))

        val snapshot = tracker.snapshot()
        assertEquals(AcquisitionSelfTestStatus.PASSED, snapshot.status)
        assertEquals(1_250L, snapshot.acknowledgedAtMonotonicMs)
        assertEquals(1L, snapshot.attemptCount)
        assertEquals(1L, snapshot.passCount)
    }

    @Test
    fun `stale or mismatched acknowledgement is ignored`() {
        val tracker = AcquisitionSelfTestTracker(FakeClock())
        tracker.start()

        assertFalse(tracker.acknowledge("wrong-id"))
        assertEquals(AcquisitionSelfTestStatus.PENDING, tracker.snapshot().status)
        assertEquals(0L, tracker.snapshot().passCount)
    }

    @Test
    fun `pending test times out deterministically`() {
        val clock = FakeClock(1_000)
        val tracker = AcquisitionSelfTestTracker(clock, timeoutMs = 5_000)
        val testId = tracker.start()
        clock.value = 6_000

        assertEquals(AcquisitionSelfTestStatus.TIMED_OUT, tracker.snapshot().status)
        assertFalse(tracker.acknowledge(testId))
    }

    @Test
    fun `new test supersedes old id and preserves counts`() {
        val clock = FakeClock(100)
        val tracker = AcquisitionSelfTestTracker(clock)
        val first = tracker.start()
        clock.value = 200
        val second = tracker.start()

        assertNotEquals(first, second)
        assertFalse(tracker.acknowledge(first))
        assertTrue(tracker.acknowledge(second))
        assertEquals(2L, tracker.snapshot().attemptCount)
        assertEquals(1L, tracker.snapshot().passCount)
    }

    @Test
    fun `emit failure is terminal for matching test`() {
        val tracker = AcquisitionSelfTestTracker(FakeClock())
        val testId = tracker.start()

        assertTrue(tracker.markEmitFailed(testId))
        assertEquals(AcquisitionSelfTestStatus.EMIT_FAILED, tracker.snapshot().status)
        assertFalse(tracker.acknowledge(testId))
    }
}
