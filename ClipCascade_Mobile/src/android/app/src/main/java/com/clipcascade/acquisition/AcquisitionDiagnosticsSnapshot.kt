package com.clipcascade.acquisition

/**
 * Sanitized process diagnostics. Clipboard payloads, server credentials, and
 * URLs are deliberately absent.
 */
data class AcquisitionDiagnosticsSnapshot(
    val requested: Boolean,
    val ordinaryRunning: Boolean,
    val ordinaryStartCount: Long,
    val ordinaryTriggerCount: Long,
    val ordinaryLastTriggerAtMonotonicMs: Long?,
    val ordinaryLastErrorCode: BackendReasonCode?,
    val logcatSdkEligible: Boolean,
    val readLogsGranted: Boolean,
    val overlayGranted: Boolean,
    val logcatThreadAlive: Boolean,
    val logcatProcessAlive: Boolean,
    val logcatGeneration: Long,
    val lastLogcatMatchAtMonotonicMs: Long?,
    val lastOverlayLaunchAtMonotonicMs: Long?,
    val logcatLastErrorCode: BackendReasonCode?,
    val lastReadAttemptAtMonotonicMs: Long?,
    val lastSuccessfulReadAtMonotonicMs: Long?,
    val lastReadBackend: AcquisitionBackendId?,
    val lastReadResult: ClipboardReadResultCode?,
    val readAttemptCount: Long,
    val successfulReadCount: Long,
    val failedReadCount: Long,
)
