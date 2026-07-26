package com.clipcascade.acquisition

interface ClipboardChangeRegistrar {
    fun register(listener: () -> Unit)
    fun unregister(listener: () -> Unit)
}

/**
 * Event-driven ordinary Android clipboard listener backend.
 *
 * Android framework registration is supplied through [ClipboardChangeRegistrar]
 * so lifecycle and diagnostics can be tested on the local JVM.
 */
class OrdinaryClipboardBackend(
    private val registrar: ClipboardChangeRegistrar,
    private val clock: MonotonicClock,
) : ClipboardAcquisitionBackend {
    override val id: AcquisitionBackendId = AcquisitionBackendId.ORDINARY_LISTENER

    private val lock = Any()
    private var running = false
    private var triggerSink: ((AcquisitionTrigger) -> Unit)? = null
    private var startCount = 0L
    private var triggerCount = 0L
    private var lastStartedAtMonotonicMs: Long? = null
    private var lastStoppedAtMonotonicMs: Long? = null
    private var lastTriggerAtMonotonicMs: Long? = null
    private var lastErrorCode: BackendReasonCode? = null

    private val listener: () -> Unit = { handleClipboardChanged() }

    override fun inspectCapability(): BackendCapability = BackendCapability(
        backendId = id,
        state = BackendCapabilityState.AVAILABLE,
        reasonCode = BackendReasonCode.SUPPORTED,
        coverageClass = AcquisitionCoverageClass.FOREGROUND_ONLY,
    )

    override fun start(
        triggerSink: (AcquisitionTrigger) -> Unit,
    ): BackendStartResult = synchronized(lock) {
        if (running) {
            return@synchronized BackendStartResult(
                BackendStartCode.ALREADY_RUNNING,
            )
        }

        this.triggerSink = triggerSink
        try {
            // Registration is serialized with start/stop. The Android registrar
            // operation is bounded and does not perform clipboard reads.
            registrar.register(listener)
            running = true
            startCount += 1
            lastStartedAtMonotonicMs = clock.nowMs()
            lastErrorCode = null
            BackendStartResult(BackendStartCode.STARTED)
        } catch (_: Exception) {
            this.triggerSink = null
            running = false
            lastErrorCode = BackendReasonCode.BACKEND_START_FAILED
            BackendStartResult(
                BackendStartCode.FAILED,
                BackendReasonCode.BACKEND_START_FAILED,
            )
        }
    }

    override fun stop(): BackendStopResult = synchronized(lock) {
        if (!running) {
            triggerSink = null
            return@synchronized BackendStopResult(
                BackendStopCode.ALREADY_STOPPED,
            )
        }

        // Mark stopped before unregistering so a re-entrant callback cannot
        // emit after shutdown has begun.
        running = false
        triggerSink = null
        try {
            registrar.unregister(listener)
            lastStoppedAtMonotonicMs = clock.nowMs()
            lastErrorCode = null
            BackendStopResult(BackendStopCode.STOPPED)
        } catch (_: Exception) {
            lastStoppedAtMonotonicMs = clock.nowMs()
            lastErrorCode = BackendReasonCode.BACKEND_STOP_FAILED
            BackendStopResult(
                BackendStopCode.FAILED,
                BackendReasonCode.BACKEND_STOP_FAILED,
            )
        }
    }

    override fun snapshot(): BackendRuntimeSnapshot = synchronized(lock) {
        BackendRuntimeSnapshot(
            backendId = id,
            running = running,
            startCount = startCount,
            triggerCount = triggerCount,
            lastStartedAtMonotonicMs = lastStartedAtMonotonicMs,
            lastStoppedAtMonotonicMs = lastStoppedAtMonotonicMs,
            lastTriggerAtMonotonicMs = lastTriggerAtMonotonicMs,
            lastErrorCode = lastErrorCode,
        )
    }

    private fun handleClipboardChanged() {
        val eventAndSink = synchronized(lock) {
            if (!running) {
                return
            }
            val now = clock.nowMs()
            triggerCount += 1
            lastTriggerAtMonotonicMs = now
            val event = AcquisitionTrigger(
                backendId = id,
                triggerType = TriggerType.PRIMARY_CLIP_CHANGED,
                monotonicTimestampMs = now,
                sourcePackage = null,
                confidence = TriggerConfidence.DIRECT,
            )
            event to triggerSink
        }

        val sink = eventAndSink.second ?: return
        try {
            sink(eventAndSink.first)
        } catch (_: Exception) {
            synchronized(lock) {
                lastErrorCode = BackendReasonCode.TRIGGER_DELIVERY_FAILED
            }
        }
    }
}
