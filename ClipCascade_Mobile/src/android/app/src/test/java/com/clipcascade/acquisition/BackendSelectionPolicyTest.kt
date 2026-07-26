package com.clipcascade.acquisition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendSelectionPolicyTest {
    private val policy = BackendSelectionPolicy()

    private fun capability(
        id: AcquisitionBackendId,
        state: BackendCapabilityState,
        reason: BackendReasonCode = BackendReasonCode.SUPPORTED,
        action: RequiredUserAction? = null,
        coverage: AcquisitionCoverageClass = AcquisitionCoverageClass.EVENT_DEPENDENT,
    ) = BackendCapability(
        backendId = id,
        state = state,
        reasonCode = reason,
        coverageClass = coverage,
        requiredUserAction = action,
    )

    @Test
    fun `verified Shizuku wins over other background backends`() {
        val selection = policy.select(
            listOf(
                capability(
                    AcquisitionBackendId.ORDINARY_LISTENER,
                    BackendCapabilityState.AVAILABLE,
                    coverage = AcquisitionCoverageClass.FOREGROUND_ONLY,
                ),
                capability(
                    AcquisitionBackendId.ACCESSIBILITY,
                    BackendCapabilityState.AVAILABLE,
                ),
                capability(
                    AcquisitionBackendId.SHIZUKU,
                    BackendCapabilityState.AVAILABLE,
                    coverage = AcquisitionCoverageClass.PRIVILEGED,
                ),
                capability(
                    AcquisitionBackendId.MANUAL_SHARE,
                    BackendCapabilityState.AVAILABLE,
                    coverage = AcquisitionCoverageClass.MANUAL_ONLY,
                ),
            ),
        )

        assertEquals(
            AcquisitionBackendId.SHIZUKU,
            selection.selectedBackgroundBackend,
        )
        assertEquals(
            setOf(
                AcquisitionBackendId.ORDINARY_LISTENER,
                AcquisitionBackendId.MANUAL_SHARE,
            ),
            selection.alwaysEnabledBackends,
        )
        assertNull(selection.pendingUserAction)
    }

    @Test
    fun `Accessibility wins when Shizuku is unavailable`() {
        val selection = policy.select(
            listOf(
                capability(
                    AcquisitionBackendId.SHIZUKU,
                    BackendCapabilityState.UNAVAILABLE,
                    BackendReasonCode.SHIZUKU_NOT_INSTALLED,
                ),
                capability(
                    AcquisitionBackendId.ACCESSIBILITY,
                    BackendCapabilityState.AVAILABLE,
                ),
                capability(
                    AcquisitionBackendId.LOGCAT_OVERLAY,
                    BackendCapabilityState.AVAILABLE,
                    coverage = AcquisitionCoverageClass.FOCUS_WORKAROUND,
                ),
            ),
        )

        assertEquals(
            AcquisitionBackendId.ACCESSIBILITY,
            selection.selectedBackgroundBackend,
        )
    }

    @Test
    fun `ADB assisted path wins over legacy logcat fallback`() {
        val selection = policy.select(
            listOf(
                capability(
                    AcquisitionBackendId.ADB_ASSISTED,
                    BackendCapabilityState.AVAILABLE,
                    coverage = AcquisitionCoverageClass.PRIVILEGED,
                ),
                capability(
                    AcquisitionBackendId.LOGCAT_OVERLAY,
                    BackendCapabilityState.DEGRADED,
                    coverage = AcquisitionCoverageClass.FOCUS_WORKAROUND,
                ),
            ),
        )

        assertEquals(
            AcquisitionBackendId.ADB_ASSISTED,
            selection.selectedBackgroundBackend,
        )
    }

    @Test
    fun `degraded legacy backend remains selectable until replacement is proven`() {
        val selection = policy.select(
            listOf(
                capability(
                    AcquisitionBackendId.LOGCAT_OVERLAY,
                    BackendCapabilityState.DEGRADED,
                    BackendReasonCode.OEM_BACKGROUND_RESTRICTION,
                    coverage = AcquisitionCoverageClass.FOCUS_WORKAROUND,
                ),
            ),
        )

        assertEquals(
            AcquisitionBackendId.LOGCAT_OVERLAY,
            selection.selectedBackgroundBackend,
        )
        assertEquals(
            BackendReasonCode.OEM_BACKGROUND_RESTRICTION,
            selection.selectionReason,
        )
    }

    @Test
    fun `missing background permission returns highest-priority user action`() {
        val selection = policy.select(
            listOf(
                capability(
                    AcquisitionBackendId.SHIZUKU,
                    BackendCapabilityState.NEEDS_USER_ACTION,
                    BackendReasonCode.SHIZUKU_PERMISSION_REQUIRED,
                    RequiredUserAction.AUTHORIZE_SHIZUKU,
                    AcquisitionCoverageClass.PRIVILEGED,
                ),
                capability(
                    AcquisitionBackendId.ACCESSIBILITY,
                    BackendCapabilityState.NEEDS_USER_ACTION,
                    BackendReasonCode.ACCESSIBILITY_DISABLED,
                    RequiredUserAction.ENABLE_ACCESSIBILITY,
                ),
            ),
        )

        assertNull(selection.selectedBackgroundBackend)
        assertEquals(
            RequiredUserAction.AUTHORIZE_SHIZUKU,
            selection.pendingUserAction,
        )
        assertEquals(
            BackendReasonCode.SHIZUKU_PERMISSION_REQUIRED,
            selection.selectionReason,
        )
    }

    @Test
    fun `blocked backend is never selected`() {
        val selection = policy.select(
            listOf(
                capability(
                    AcquisitionBackendId.SHIZUKU,
                    BackendCapabilityState.BLOCKED,
                    BackendReasonCode.BACKEND_START_FAILED,
                    coverage = AcquisitionCoverageClass.PRIVILEGED,
                ),
                capability(
                    AcquisitionBackendId.MANUAL_SHARE,
                    BackendCapabilityState.AVAILABLE,
                    coverage = AcquisitionCoverageClass.MANUAL_ONLY,
                ),
            ),
        )

        assertNull(selection.selectedBackgroundBackend)
        assertTrue(
            selection.alwaysEnabledBackends.contains(
                AcquisitionBackendId.MANUAL_SHARE,
            ),
        )
    }

    @Test
    fun `empty capability set yields no fabricated success`() {
        val selection = policy.select(emptyList())

        assertNull(selection.selectedBackgroundBackend)
        assertTrue(selection.alwaysEnabledBackends.isEmpty())
        assertNull(selection.pendingUserAction)
        assertNull(selection.selectionReason)
    }
}
