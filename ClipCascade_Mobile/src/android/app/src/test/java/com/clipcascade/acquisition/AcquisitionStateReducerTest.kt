package com.clipcascade.acquisition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcquisitionStateReducerTest {
    private val reducer = AcquisitionStateReducer()

    @Test
    fun `initial snapshot is stopped and empty`() {
        val snapshot = reducer.initialSnapshot(100)

        assertEquals(AcquisitionCoordinatorState.STOPPED, snapshot.state)
        assertEquals(100, snapshot.stateSinceMonotonicMs)
        assertTrue(snapshot.activeBackends.isEmpty())
        assertEquals(0, snapshot.triggerCount)
        assertNull(snapshot.lastReadResult)
    }

    @Test
    fun `inspection with selected backend enters starting`() {
        val initial = reducer.initialSnapshot(100)
        val inspecting = reducer.reduce(
            initial,
            AcquisitionCoordinatorEvent.InspectionStarted(110),
        )
        val selection = BackendSelection(
            selectedBackgroundBackend = AcquisitionBackendId.SHIZUKU,
            alwaysEnabledBackends = setOf(AcquisitionBackendId.ORDINARY_LISTENER),
            pendingUserAction = null,
            selectionReason = BackendReasonCode.SUPPORTED,
        )

        val snapshot = reducer.reduce(
            inspecting,
            AcquisitionCoordinatorEvent.CapabilitiesInspected(
                monotonicTimestampMs = 120,
                selection = selection,
                availableBackends = setOf(
                    AcquisitionBackendId.SHIZUKU,
                    AcquisitionBackendId.ORDINARY_LISTENER,
                ),
            ),
        )

        assertEquals(AcquisitionCoordinatorState.STARTING, snapshot.state)
        assertEquals(
            AcquisitionBackendId.SHIZUKU,
            snapshot.selectedBackgroundBackend,
        )
        assertNull(snapshot.pendingUserAction)
    }

    @Test
    fun `missing permission enters explicit user action state`() {
        val selection = BackendSelection(
            selectedBackgroundBackend = null,
            alwaysEnabledBackends = setOf(AcquisitionBackendId.ORDINARY_LISTENER),
            pendingUserAction = RequiredUserAction.ENABLE_ACCESSIBILITY,
            selectionReason = BackendReasonCode.ACCESSIBILITY_DISABLED,
        )

        val snapshot = reducer.reduce(
            reducer.initialSnapshot(100),
            AcquisitionCoordinatorEvent.CapabilitiesInspected(
                monotonicTimestampMs = 120,
                selection = selection,
                availableBackends = setOf(AcquisitionBackendId.ORDINARY_LISTENER),
            ),
        )

        assertEquals(
            AcquisitionCoordinatorState.WAITING_FOR_USER_ACTION,
            snapshot.state,
        )
        assertEquals(
            RequiredUserAction.ENABLE_ACCESSIBILITY,
            snapshot.pendingUserAction,
        )
    }

    @Test
    fun `ordinary listener only is represented as degraded not fully active`() {
        val selection = BackendSelection(
            selectedBackgroundBackend = null,
            alwaysEnabledBackends = setOf(AcquisitionBackendId.ORDINARY_LISTENER),
            pendingUserAction = null,
            selectionReason = BackendReasonCode.ANDROID_BACKGROUND_RESTRICTION,
        )

        val snapshot = reducer.reduce(
            reducer.initialSnapshot(100),
            AcquisitionCoordinatorEvent.CapabilitiesInspected(
                monotonicTimestampMs = 120,
                selection = selection,
                availableBackends = setOf(AcquisitionBackendId.ORDINARY_LISTENER),
            ),
        )

        assertEquals(AcquisitionCoordinatorState.DEGRADED, snapshot.state)
        assertEquals(
            BackendReasonCode.ANDROID_BACKGROUND_RESTRICTION,
            snapshot.lastErrorCode,
        )
    }

    @Test
    fun `no backend and no action is an explicit error`() {
        val selection = BackendSelection(
            selectedBackgroundBackend = null,
            alwaysEnabledBackends = emptySet(),
            pendingUserAction = null,
            selectionReason = null,
        )

        val snapshot = reducer.reduce(
            reducer.initialSnapshot(100),
            AcquisitionCoordinatorEvent.CapabilitiesInspected(
                monotonicTimestampMs = 120,
                selection = selection,
                availableBackends = emptySet(),
            ),
        )

        assertEquals(AcquisitionCoordinatorState.ERROR, snapshot.state)
        assertEquals(
            BackendReasonCode.UNKNOWN_CAPABILITY_ERROR,
            snapshot.lastErrorCode,
        )
    }

    @Test
    fun `started with background backend enters active`() {
        val before = AcquisitionSnapshot(
            state = AcquisitionCoordinatorState.STARTING,
            stateSinceMonotonicMs = 100,
            selectedBackgroundBackend = AcquisitionBackendId.ACCESSIBILITY,
        )

        val snapshot = reducer.reduce(
            before,
            AcquisitionCoordinatorEvent.Started(
                monotonicTimestampMs = 130,
                activeBackends = setOf(
                    AcquisitionBackendId.ACCESSIBILITY,
                    AcquisitionBackendId.ORDINARY_LISTENER,
                ),
            ),
        )

        assertEquals(AcquisitionCoordinatorState.ACTIVE, snapshot.state)
        assertEquals(2, snapshot.activeBackends.size)
    }

    @Test
    fun `accepted and duplicate triggers update separate counters`() {
        val initial = AcquisitionSnapshot(
            state = AcquisitionCoordinatorState.ACTIVE,
            stateSinceMonotonicMs = 100,
        )
        val accepted = reducer.reduce(
            initial,
            AcquisitionCoordinatorEvent.TriggerObserved(
                monotonicTimestampMs = 200,
                backendId = AcquisitionBackendId.ORDINARY_LISTENER,
                suppressedAsDuplicate = false,
            ),
        )
        val duplicate = reducer.reduce(
            accepted,
            AcquisitionCoordinatorEvent.TriggerObserved(
                monotonicTimestampMs = 210,
                backendId = AcquisitionBackendId.ACCESSIBILITY,
                suppressedAsDuplicate = true,
            ),
        )

        assertEquals(1, duplicate.triggerCount)
        assertEquals(1, duplicate.suppressedDuplicateTriggerCount)
        assertEquals(200, duplicate.lastTriggerAtMonotonicMs)
        assertEquals(
            AcquisitionBackendId.ORDINARY_LISTENER,
            duplicate.lastTriggerBackend,
        )
    }

    @Test
    fun `successful read records success without payload`() {
        val initial = AcquisitionSnapshot(
            state = AcquisitionCoordinatorState.ACTIVE,
            stateSinceMonotonicMs = 100,
        )

        val snapshot = reducer.reduce(
            initial,
            AcquisitionCoordinatorEvent.ReadCompleted(
                monotonicTimestampMs = 300,
                backendId = AcquisitionBackendId.SHIZUKU,
                result = ClipboardReadResultCode.SUCCESS,
            ),
        )

        assertEquals(1, snapshot.successfulReadCount)
        assertEquals(0, snapshot.failedReadCount)
        assertEquals(300, snapshot.lastSuccessfulReadAtMonotonicMs)
        assertEquals(ClipboardReadResultCode.SUCCESS, snapshot.lastReadResult)
    }

    @Test
    fun `unchanged and empty reads are not failures`() {
        var snapshot = AcquisitionSnapshot(
            state = AcquisitionCoordinatorState.ACTIVE,
            stateSinceMonotonicMs = 100,
        )
        snapshot = reducer.reduce(
            snapshot,
            AcquisitionCoordinatorEvent.ReadCompleted(
                200,
                AcquisitionBackendId.ORDINARY_LISTENER,
                ClipboardReadResultCode.UNCHANGED,
            ),
        )
        snapshot = reducer.reduce(
            snapshot,
            AcquisitionCoordinatorEvent.ReadCompleted(
                210,
                AcquisitionBackendId.ORDINARY_LISTENER,
                ClipboardReadResultCode.EMPTY,
            ),
        )

        assertEquals(0, snapshot.successfulReadCount)
        assertEquals(0, snapshot.failedReadCount)
        assertEquals(AcquisitionCoordinatorState.ACTIVE, snapshot.state)
    }

    @Test
    fun `failed read degrades active coordinator and preserves prior success time`() {
        val initial = AcquisitionSnapshot(
            state = AcquisitionCoordinatorState.ACTIVE,
            stateSinceMonotonicMs = 100,
            lastSuccessfulReadAtMonotonicMs = 150,
            successfulReadCount = 1,
        )

        val snapshot = reducer.reduce(
            initial,
            AcquisitionCoordinatorEvent.ReadCompleted(
                monotonicTimestampMs = 300,
                backendId = AcquisitionBackendId.LOGCAT_OVERLAY,
                result = ClipboardReadResultCode.FOCUS_REQUIRED,
            ),
        )

        assertEquals(AcquisitionCoordinatorState.DEGRADED, snapshot.state)
        assertEquals(1, snapshot.failedReadCount)
        assertEquals(150, snapshot.lastSuccessfulReadAtMonotonicMs)
        assertEquals(300, snapshot.stateSinceMonotonicMs)
    }

    @Test
    fun `stop discards runtime counters and backend state`() {
        val active = AcquisitionSnapshot(
            state = AcquisitionCoordinatorState.ACTIVE,
            stateSinceMonotonicMs = 100,
            selectedBackgroundBackend = AcquisitionBackendId.SHIZUKU,
            activeBackends = setOf(AcquisitionBackendId.SHIZUKU),
            triggerCount = 12,
            successfulReadCount = 8,
        )
        val stopping = reducer.reduce(
            active,
            AcquisitionCoordinatorEvent.StopRequested(400),
        )
        val stopped = reducer.reduce(
            stopping,
            AcquisitionCoordinatorEvent.Stopped(410),
        )

        assertEquals(AcquisitionCoordinatorState.STOPPING, stopping.state)
        assertEquals(AcquisitionCoordinatorState.STOPPED, stopped.state)
        assertNull(stopped.selectedBackgroundBackend)
        assertEquals(0, stopped.triggerCount)
        assertTrue(stopped.activeBackends.isEmpty())
    }

    @Test
    fun `fatal backend event records stable reason`() {
        val snapshot = reducer.reduce(
            reducer.initialSnapshot(100),
            AcquisitionCoordinatorEvent.Failed(
                monotonicTimestampMs = 500,
                reasonCode = BackendReasonCode.BACKEND_START_FAILED,
            ),
        )

        assertEquals(AcquisitionCoordinatorState.ERROR, snapshot.state)
        assertEquals(
            BackendReasonCode.BACKEND_START_FAILED,
            snapshot.lastErrorCode,
        )
    }
}
