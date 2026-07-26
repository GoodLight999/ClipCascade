package com.clipcascade.acquisition

/**
 * Pure selection policy. It does not grant permissions, start services, or
 * silently mutate Android settings.
 */
class BackendSelectionPolicy {
    private val backgroundPriority = listOf(
        AcquisitionBackendId.SHIZUKU,
        AcquisitionBackendId.ACCESSIBILITY,
        AcquisitionBackendId.ADB_ASSISTED,
        AcquisitionBackendId.LOGCAT_OVERLAY,
    )

    private val alwaysEnabledCandidates = listOf(
        AcquisitionBackendId.ORDINARY_LISTENER,
        AcquisitionBackendId.MANUAL_SHARE,
    )

    fun select(capabilities: Collection<BackendCapability>): BackendSelection {
        val byId = capabilities.associateBy { it.backendId }

        val alwaysEnabled = alwaysEnabledCandidates
            .filter { backendId -> byId[backendId].isUsable() }
            .toSet()

        val selected = backgroundPriority.firstOrNull { backendId ->
            byId[backendId].isUsable()
        }
        if (selected != null) {
            return BackendSelection(
                selectedBackgroundBackend = selected,
                alwaysEnabledBackends = alwaysEnabled,
                pendingUserAction = null,
                selectionReason = byId.getValue(selected).reasonCode,
            )
        }

        val actionable = backgroundPriority
            .mapNotNull { backendId -> byId[backendId] }
            .firstOrNull { capability ->
                capability.state == BackendCapabilityState.NEEDS_USER_ACTION &&
                    capability.requiredUserAction != null
            }

        return BackendSelection(
            selectedBackgroundBackend = null,
            alwaysEnabledBackends = alwaysEnabled,
            pendingUserAction = actionable?.requiredUserAction,
            selectionReason = actionable?.reasonCode,
        )
    }

    private fun BackendCapability?.isUsable(): Boolean =
        this != null && (
            state == BackendCapabilityState.AVAILABLE ||
                state == BackendCapabilityState.DEGRADED
            )
}
