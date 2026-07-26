package com.clipcascade.acquisition

enum class BackendStartCode {
    STARTED,
    ALREADY_RUNNING,
    NEEDS_USER_ACTION,
    FAILED,
}

enum class BackendStopCode {
    STOPPED,
    ALREADY_STOPPED,
    FAILED,
}

data class BackendStartResult(
    val code: BackendStartCode,
    val reasonCode: BackendReasonCode? = null,
)

data class BackendStopResult(
    val code: BackendStopCode,
    val reasonCode: BackendReasonCode? = null,
)

data class BackendRuntimeSnapshot(
    val backendId: AcquisitionBackendId,
    val running: Boolean,
    val startCount: Long,
    val triggerCount: Long,
    val lastStartedAtMonotonicMs: Long? = null,
    val lastStoppedAtMonotonicMs: Long? = null,
    val lastTriggerAtMonotonicMs: Long? = null,
    val lastErrorCode: BackendReasonCode? = null,
)

fun interface MonotonicClock {
    fun nowMs(): Long
}

interface ClipboardAcquisitionBackend {
    val id: AcquisitionBackendId

    fun inspectCapability(): BackendCapability

    fun start(triggerSink: (AcquisitionTrigger) -> Unit): BackendStartResult

    fun stop(): BackendStopResult

    fun snapshot(): BackendRuntimeSnapshot
}
