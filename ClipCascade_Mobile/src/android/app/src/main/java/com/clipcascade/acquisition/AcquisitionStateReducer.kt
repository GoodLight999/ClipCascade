package com.clipcascade.acquisition

sealed interface AcquisitionCoordinatorEvent {
    val monotonicTimestampMs: Long

    data class InspectionStarted(
        override val monotonicTimestampMs: Long,
    ) : AcquisitionCoordinatorEvent

    data class CapabilitiesInspected(
        override val monotonicTimestampMs: Long,
        val selection: BackendSelection,
        val availableBackends: Set<AcquisitionBackendId>,
    ) : AcquisitionCoordinatorEvent

    data class StartRequested(
        override val monotonicTimestampMs: Long,
    ) : AcquisitionCoordinatorEvent

    data class Started(
        override val monotonicTimestampMs: Long,
        val activeBackends: Set<AcquisitionBackendId>,
    ) : AcquisitionCoordinatorEvent

    data class TriggerObserved(
        override val monotonicTimestampMs: Long,
        val backendId: AcquisitionBackendId,
        val suppressedAsDuplicate: Boolean,
    ) : AcquisitionCoordinatorEvent

    data class ReadCompleted(
        override val monotonicTimestampMs: Long,
        val backendId: AcquisitionBackendId,
        val result: ClipboardReadResultCode,
    ) : AcquisitionCoordinatorEvent

    data class StopRequested(
        override val monotonicTimestampMs: Long,
    ) : AcquisitionCoordinatorEvent

    data class Stopped(
        override val monotonicTimestampMs: Long,
    ) : AcquisitionCoordinatorEvent

    data class Failed(
        override val monotonicTimestampMs: Long,
        val reasonCode: BackendReasonCode,
    ) : AcquisitionCoordinatorEvent
}

/**
 * Deterministic state reducer used by the future Android coordinator and by
 * diagnostics. It contains no Android framework calls and no clipboard data.
 */
class AcquisitionStateReducer {
    fun initialSnapshot(monotonicTimestampMs: Long): AcquisitionSnapshot =
        AcquisitionSnapshot(
            state = AcquisitionCoordinatorState.STOPPED,
            stateSinceMonotonicMs = monotonicTimestampMs,
        )

    fun reduce(
        current: AcquisitionSnapshot,
        event: AcquisitionCoordinatorEvent,
    ): AcquisitionSnapshot = when (event) {
        is AcquisitionCoordinatorEvent.InspectionStarted -> current.transitionTo(
            AcquisitionCoordinatorState.INSPECTING,
            event.monotonicTimestampMs,
            lastErrorCode = null,
            pendingUserAction = null,
        )

        is AcquisitionCoordinatorEvent.CapabilitiesInspected -> {
            val selected = event.selection.selectedBackgroundBackend
            val activeState = when {
                selected != null -> AcquisitionCoordinatorState.STARTING
                event.selection.pendingUserAction != null ->
                    AcquisitionCoordinatorState.WAITING_FOR_USER_ACTION
                event.selection.alwaysEnabledBackends.isNotEmpty() ->
                    AcquisitionCoordinatorState.DEGRADED
                else -> AcquisitionCoordinatorState.ERROR
            }
            current.copy(
                state = activeState,
                stateSinceMonotonicMs = event.monotonicTimestampMs,
                selectedBackgroundBackend = selected,
                activeBackends = emptySet(),
                availableBackends = event.availableBackends,
                lastErrorCode = if (activeState == AcquisitionCoordinatorState.ERROR) {
                    event.selection.selectionReason
                        ?: BackendReasonCode.UNKNOWN_CAPABILITY_ERROR
                } else {
                    event.selection.selectionReason
                        ?.takeIf { activeState == AcquisitionCoordinatorState.DEGRADED }
                },
                pendingUserAction = event.selection.pendingUserAction,
            )
        }

        is AcquisitionCoordinatorEvent.StartRequested -> current.transitionTo(
            AcquisitionCoordinatorState.STARTING,
            event.monotonicTimestampMs,
            lastErrorCode = null,
        )

        is AcquisitionCoordinatorEvent.Started -> current.copy(
            state = if (current.selectedBackgroundBackend == null) {
                AcquisitionCoordinatorState.DEGRADED
            } else {
                AcquisitionCoordinatorState.ACTIVE
            },
            stateSinceMonotonicMs = event.monotonicTimestampMs,
            activeBackends = event.activeBackends,
            lastErrorCode = null,
            pendingUserAction = null,
        )

        is AcquisitionCoordinatorEvent.TriggerObserved -> {
            if (event.suppressedAsDuplicate) {
                current.copy(
                    suppressedDuplicateTriggerCount =
                        current.suppressedDuplicateTriggerCount + 1,
                )
            } else {
                current.copy(
                    lastTriggerAtMonotonicMs = event.monotonicTimestampMs,
                    lastTriggerBackend = event.backendId,
                    triggerCount = current.triggerCount + 1,
                )
            }
        }

        is AcquisitionCoordinatorEvent.ReadCompleted -> {
            val succeeded = event.result == ClipboardReadResultCode.SUCCESS
            val failed = event.result in failedReadResults
            current.copy(
                lastReadAttemptAtMonotonicMs = event.monotonicTimestampMs,
                lastSuccessfulReadAtMonotonicMs = if (succeeded) {
                    event.monotonicTimestampMs
                } else {
                    current.lastSuccessfulReadAtMonotonicMs
                },
                lastReadResult = event.result,
                successfulReadCount = current.successfulReadCount +
                    if (succeeded) 1 else 0,
                failedReadCount = current.failedReadCount +
                    if (failed) 1 else 0,
                state = if (failed && current.state == AcquisitionCoordinatorState.ACTIVE) {
                    AcquisitionCoordinatorState.DEGRADED
                } else {
                    current.state
                },
                stateSinceMonotonicMs = if (
                    failed && current.state == AcquisitionCoordinatorState.ACTIVE
                ) {
                    event.monotonicTimestampMs
                } else {
                    current.stateSinceMonotonicMs
                },
            )
        }

        is AcquisitionCoordinatorEvent.StopRequested -> current.transitionTo(
            AcquisitionCoordinatorState.STOPPING,
            event.monotonicTimestampMs,
        )

        is AcquisitionCoordinatorEvent.Stopped -> AcquisitionSnapshot(
            state = AcquisitionCoordinatorState.STOPPED,
            stateSinceMonotonicMs = event.monotonicTimestampMs,
        )

        is AcquisitionCoordinatorEvent.Failed -> current.copy(
            state = AcquisitionCoordinatorState.ERROR,
            stateSinceMonotonicMs = event.monotonicTimestampMs,
            lastErrorCode = event.reasonCode,
        )
    }

    private fun AcquisitionSnapshot.transitionTo(
        state: AcquisitionCoordinatorState,
        timestampMs: Long,
        lastErrorCode: BackendReasonCode? = this.lastErrorCode,
        pendingUserAction: RequiredUserAction? = this.pendingUserAction,
    ): AcquisitionSnapshot = copy(
        state = state,
        stateSinceMonotonicMs = timestampMs,
        lastErrorCode = lastErrorCode,
        pendingUserAction = pendingUserAction,
    )

    private companion object {
        val failedReadResults = setOf(
            ClipboardReadResultCode.ACCESS_DENIED,
            ClipboardReadResultCode.FOCUS_REQUIRED,
            ClipboardReadResultCode.REACT_CONTEXT_UNAVAILABLE,
            ClipboardReadResultCode.UNSUPPORTED_CONTENT,
            ClipboardReadResultCode.READ_FAILED,
        )
    }
}
