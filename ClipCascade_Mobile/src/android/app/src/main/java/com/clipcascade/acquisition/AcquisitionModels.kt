package com.clipcascade.acquisition

enum class AcquisitionBackendId {
    ORDINARY_LISTENER,
    ACCESSIBILITY,
    LOGCAT_OVERLAY,
    SHIZUKU,
    ADB_ASSISTED,
    MANUAL_SHARE,
}

enum class BackendCapabilityState {
    UNAVAILABLE,
    NEEDS_USER_ACTION,
    AVAILABLE,
    DEGRADED,
    BLOCKED,
}

enum class BackendReasonCode {
    SUPPORTED,
    ANDROID_BACKGROUND_RESTRICTION,
    ACCESSIBILITY_DISABLED,
    ACCESSIBILITY_NOT_DECLARED,
    SHIZUKU_NOT_INSTALLED,
    SHIZUKU_NOT_RUNNING,
    SHIZUKU_PERMISSION_REQUIRED,
    READ_LOGS_PERMISSION_REQUIRED,
    OVERLAY_PERMISSION_REQUIRED,
    BATTERY_OPTIMIZATION_ACTIVE,
    OEM_BACKGROUND_RESTRICTION,
    REACT_CONTEXT_UNAVAILABLE,
    TRIGGER_DELIVERY_FAILED,
    BACKEND_START_FAILED,
    BACKEND_STOP_FAILED,
    UNKNOWN_CAPABILITY_ERROR,
}

enum class AcquisitionCoverageClass {
    FOREGROUND_ONLY,
    EVENT_DEPENDENT,
    FOCUS_WORKAROUND,
    PRIVILEGED,
    MANUAL_ONLY,
}

enum class RequiredUserAction {
    ENABLE_ACCESSIBILITY,
    INSTALL_SHIZUKU,
    START_SHIZUKU,
    AUTHORIZE_SHIZUKU,
    GRANT_READ_LOGS,
    GRANT_OVERLAY,
    DISABLE_BATTERY_OPTIMIZATION,
    REVIEW_OEM_BACKGROUND_SETTINGS,
}

data class BackendCapability(
    val backendId: AcquisitionBackendId,
    val state: BackendCapabilityState,
    val reasonCode: BackendReasonCode,
    val coverageClass: AcquisitionCoverageClass,
    val requiredUserAction: RequiredUserAction? = null,
    val canDeepLinkUserAction: Boolean = false,
    val mayBeInvalidatedByReboot: Boolean = false,
)

enum class TriggerType {
    PRIMARY_CLIP_CHANGED,
    ACCESSIBILITY_COPY_ACTION,
    ACCESSIBILITY_SELECTION_CHANGED,
    LOGCAT_CLIPBOARD_DENIAL,
    PRIVILEGED_CLIP_CHANGED,
    MANUAL_SHARE,
    EXPLICIT_SELF_TEST,
}

enum class TriggerConfidence {
    DIRECT,
    INFERRED,
    DIAGNOSTIC,
}

data class AcquisitionTrigger(
    val backendId: AcquisitionBackendId,
    val triggerType: TriggerType,
    val monotonicTimestampMs: Long,
    val sourcePackage: String? = null,
    val confidence: TriggerConfidence,
)

enum class ClipboardReadResultCode {
    SUCCESS,
    UNCHANGED,
    EMPTY,
    ACCESS_DENIED,
    FOCUS_REQUIRED,
    REACT_CONTEXT_UNAVAILABLE,
    UNSUPPORTED_CONTENT,
    READ_FAILED,
}

enum class AcquisitionCoordinatorState {
    STOPPED,
    INSPECTING,
    STARTING,
    ACTIVE,
    DEGRADED,
    WAITING_FOR_USER_ACTION,
    STOPPING,
    ERROR,
}

data class AcquisitionSnapshot(
    val state: AcquisitionCoordinatorState,
    val stateSinceMonotonicMs: Long,
    val selectedBackgroundBackend: AcquisitionBackendId? = null,
    val activeBackends: Set<AcquisitionBackendId> = emptySet(),
    val availableBackends: Set<AcquisitionBackendId> = emptySet(),
    val lastTriggerAtMonotonicMs: Long? = null,
    val lastTriggerBackend: AcquisitionBackendId? = null,
    val lastReadAttemptAtMonotonicMs: Long? = null,
    val lastSuccessfulReadAtMonotonicMs: Long? = null,
    val lastReadResult: ClipboardReadResultCode? = null,
    val lastErrorCode: BackendReasonCode? = null,
    val pendingUserAction: RequiredUserAction? = null,
    val triggerCount: Long = 0,
    val successfulReadCount: Long = 0,
    val failedReadCount: Long = 0,
    val suppressedDuplicateTriggerCount: Long = 0,
)

data class BackendSelection(
    val selectedBackgroundBackend: AcquisitionBackendId?,
    val alwaysEnabledBackends: Set<AcquisitionBackendId>,
    val pendingUserAction: RequiredUserAction?,
    val selectionReason: BackendReasonCode?,
)
