package com.clipcascade.acquisition

enum class AcquisitionSelfTestStatus {
    IDLE,
    PENDING,
    PASSED,
    TIMED_OUT,
    EMIT_FAILED,
}

data class AcquisitionSelfTestSnapshot(
    val status: AcquisitionSelfTestStatus = AcquisitionSelfTestStatus.IDLE,
    val activeTestId: String? = null,
    val requestedAtMonotonicMs: Long? = null,
    val acknowledgedAtMonotonicMs: Long? = null,
    val attemptCount: Long = 0,
    val passCount: Long = 0,
)

/**
 * Tracks a payload-free native -> React Native -> native acknowledgement.
 *
 * A test ID is diagnostic metadata only. No clipboard data enters this path.
 */
class AcquisitionSelfTestTracker(
    private val clock: MonotonicClock,
    private val timeoutMs: Long = 5_000,
) {
    init {
        require(timeoutMs > 0) { "timeoutMs must be positive" }
    }

    private val lock = Any()
    private var sequence = 0L
    private var snapshot = AcquisitionSelfTestSnapshot()

    fun start(): String = synchronized(lock) {
        sequence += 1
        val now = clock.nowMs()
        val testId = "acquisition-$now-$sequence"
        snapshot = AcquisitionSelfTestSnapshot(
            status = AcquisitionSelfTestStatus.PENDING,
            activeTestId = testId,
            requestedAtMonotonicMs = now,
            acknowledgedAtMonotonicMs = null,
            attemptCount = snapshot.attemptCount + 1,
            passCount = snapshot.passCount,
        )
        testId
    }

    fun acknowledge(testId: String): Boolean = synchronized(lock) {
        expireIfNeeded()
        if (
            snapshot.status != AcquisitionSelfTestStatus.PENDING ||
            snapshot.activeTestId != testId
        ) {
            return false
        }
        snapshot = snapshot.copy(
            status = AcquisitionSelfTestStatus.PASSED,
            acknowledgedAtMonotonicMs = clock.nowMs(),
            passCount = snapshot.passCount + 1,
        )
        true
    }

    fun markEmitFailed(testId: String): Boolean = synchronized(lock) {
        if (
            snapshot.status != AcquisitionSelfTestStatus.PENDING ||
            snapshot.activeTestId != testId
        ) {
            return false
        }
        snapshot = snapshot.copy(
            status = AcquisitionSelfTestStatus.EMIT_FAILED,
        )
        true
    }

    fun snapshot(): AcquisitionSelfTestSnapshot = synchronized(lock) {
        expireIfNeeded()
        snapshot
    }

    private fun expireIfNeeded() {
        if (snapshot.status != AcquisitionSelfTestStatus.PENDING) {
            return
        }
        val requestedAt = snapshot.requestedAtMonotonicMs ?: return
        if (clock.nowMs() - requestedAt >= timeoutMs) {
            snapshot = snapshot.copy(
                status = AcquisitionSelfTestStatus.TIMED_OUT,
            )
        }
    }
}
