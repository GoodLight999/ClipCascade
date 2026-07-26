package com.clipcascade.acquisition

import org.junit.Assert.assertEquals
import org.junit.Test

class BackendSelectionQualityTest {
    @Test
    fun `available backend wins over higher priority degraded backend`() {
        val selection = BackendSelectionPolicy().select(
            listOf(
                BackendCapability(
                    backendId = AcquisitionBackendId.SHIZUKU,
                    state = BackendCapabilityState.DEGRADED,
                    reasonCode = BackendReasonCode.OEM_BACKGROUND_RESTRICTION,
                    coverageClass = AcquisitionCoverageClass.PRIVILEGED,
                ),
                BackendCapability(
                    backendId = AcquisitionBackendId.ACCESSIBILITY,
                    state = BackendCapabilityState.AVAILABLE,
                    reasonCode = BackendReasonCode.SUPPORTED,
                    coverageClass = AcquisitionCoverageClass.EVENT_DEPENDENT,
                ),
            ),
        )

        assertEquals(
            AcquisitionBackendId.ACCESSIBILITY,
            selection.selectedBackgroundBackend,
        )
    }

    @Test
    fun `priority still applies when all usable backends are degraded`() {
        val selection = BackendSelectionPolicy().select(
            listOf(
                BackendCapability(
                    backendId = AcquisitionBackendId.ACCESSIBILITY,
                    state = BackendCapabilityState.DEGRADED,
                    reasonCode = BackendReasonCode.OEM_BACKGROUND_RESTRICTION,
                    coverageClass = AcquisitionCoverageClass.EVENT_DEPENDENT,
                ),
                BackendCapability(
                    backendId = AcquisitionBackendId.LOGCAT_OVERLAY,
                    state = BackendCapabilityState.DEGRADED,
                    reasonCode = BackendReasonCode.ANDROID_BACKGROUND_RESTRICTION,
                    coverageClass = AcquisitionCoverageClass.FOCUS_WORKAROUND,
                ),
            ),
        )

        assertEquals(
            AcquisitionBackendId.ACCESSIBILITY,
            selection.selectedBackgroundBackend,
        )
    }
}
