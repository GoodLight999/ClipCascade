package com.clipcascade.acquisition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcquisitionDiagnosticsFormatterTest {
    @Test
    fun `formatter shows stable health fields and relative ages`() {
        val snapshot = AcquisitionDiagnosticsSnapshot(
            requested = true,
            ordinaryRunning = true,
            ordinaryStartCount = 2,
            ordinaryTriggerCount = 4,
            ordinaryLastStartedAtMonotonicMs = 9_000,
            ordinaryLastStoppedAtMonotonicMs = null,
            ordinaryLastTriggerAtMonotonicMs = 9_500,
            ordinaryLastErrorCode = null,
            logcatSdkEligible = true,
            readLogsGranted = true,
            overlayGranted = false,
            logcatThreadAlive = false,
            logcatProcessAlive = false,
            logcatGeneration = 3,
            lastLogcatMatchAtMonotonicMs = 8_000,
            lastOverlayLaunchAtMonotonicMs = null,
            logcatLastErrorCode = BackendReasonCode.OVERLAY_PERMISSION_REQUIRED,
            lastReadAttemptAtMonotonicMs = 9_800,
            lastSuccessfulReadAtMonotonicMs = 9_000,
            lastReadBackend = AcquisitionBackendId.ORDINARY_LISTENER,
            lastReadResult = ClipboardReadResultCode.SUCCESS,
            readAttemptCount = 5,
            successfulReadCount = 3,
            failedReadCount = 1,
        )

        val text = AcquisitionDiagnosticsFormatter.format(snapshot, 10_000)

        assertTrue(text.contains("Acquisition request: yes"))
        assertTrue(text.contains("triggers: 4"))
        assertTrue(text.contains("last trigger: 500 ms ago"))
        assertTrue(text.contains("overlay granted: no"))
        assertTrue(text.contains("OVERLAY_PERMISSION_REQUIRED"))
        assertTrue(text.contains("last result: SUCCESS"))
        assertFalse(text.contains("clipboard content", ignoreCase = true))
        assertFalse(text.contains("payload", ignoreCase = true))
    }

    @Test
    fun `formatter renders missing observations explicitly`() {
        val snapshot = AcquisitionDiagnosticsSnapshot(
            requested = false,
            ordinaryRunning = false,
            ordinaryStartCount = 0,
            ordinaryTriggerCount = 0,
            ordinaryLastStartedAtMonotonicMs = null,
            ordinaryLastStoppedAtMonotonicMs = null,
            ordinaryLastTriggerAtMonotonicMs = null,
            ordinaryLastErrorCode = null,
            logcatSdkEligible = true,
            readLogsGranted = false,
            overlayGranted = false,
            logcatThreadAlive = false,
            logcatProcessAlive = false,
            logcatGeneration = 0,
            lastLogcatMatchAtMonotonicMs = null,
            lastOverlayLaunchAtMonotonicMs = null,
            logcatLastErrorCode = null,
            lastReadAttemptAtMonotonicMs = null,
            lastSuccessfulReadAtMonotonicMs = null,
            lastReadBackend = null,
            lastReadResult = null,
            readAttemptCount = 0,
            successfulReadCount = 0,
            failedReadCount = 0,
        )

        val text = AcquisitionDiagnosticsFormatter.format(snapshot, 10_000)

        assertTrue(text.contains("Acquisition request: no"))
        assertTrue(text.contains("last trigger: never"))
        assertTrue(text.contains("last backend: none"))
        assertTrue(text.contains("last result: none"))
    }
}
