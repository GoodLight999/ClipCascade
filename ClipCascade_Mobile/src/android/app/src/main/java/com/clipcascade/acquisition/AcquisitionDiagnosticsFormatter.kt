package com.clipcascade.acquisition

object AcquisitionDiagnosticsFormatter {
    fun format(
        snapshot: AcquisitionDiagnosticsSnapshot,
        nowMonotonicMs: Long,
    ): String = buildString {
        appendLine("Acquisition request: ${yesNo(snapshot.requested)}")
        appendLine()
        appendLine("Native → React Native self-test")
        appendLine("  status: ${snapshot.selfTestStatus.name}")
        appendLine("  attempts: ${snapshot.selfTestAttemptCount}")
        appendLine("  passed: ${snapshot.selfTestPassCount}")
        appendLine(
            "  requested: ${age(snapshot.selfTestRequestedAtMonotonicMs, nowMonotonicMs)}",
        )
        appendLine(
            "  acknowledged: ${age(snapshot.selfTestAcknowledgedAtMonotonicMs, nowMonotonicMs)}",
        )
        appendLine()
        appendLine("Ordinary listener")
        appendLine("  running: ${yesNo(snapshot.ordinaryRunning)}")
        appendLine("  starts: ${snapshot.ordinaryStartCount}")
        appendLine("  triggers: ${snapshot.ordinaryTriggerCount}")
        appendLine(
            "  last started: ${age(snapshot.ordinaryLastStartedAtMonotonicMs, nowMonotonicMs)}",
        )
        appendLine(
            "  last stopped: ${age(snapshot.ordinaryLastStoppedAtMonotonicMs, nowMonotonicMs)}",
        )
        appendLine(
            "  last trigger: ${age(snapshot.ordinaryLastTriggerAtMonotonicMs, nowMonotonicMs)}",
        )
        appendLine("  error: ${snapshot.ordinaryLastErrorCode?.name ?: "none"}")
        appendLine()
        appendLine("Legacy logcat + overlay")
        appendLine("  Android version eligible: ${yesNo(snapshot.logcatSdkEligible)}")
        appendLine("  READ_LOGS granted: ${yesNo(snapshot.readLogsGranted)}")
        appendLine("  overlay granted: ${yesNo(snapshot.overlayGranted)}")
        appendLine("  thread alive: ${yesNo(snapshot.logcatThreadAlive)}")
        appendLine("  process alive: ${yesNo(snapshot.logcatProcessAlive)}")
        appendLine("  generation: ${snapshot.logcatGeneration}")
        appendLine(
            "  last log match: ${age(snapshot.lastLogcatMatchAtMonotonicMs, nowMonotonicMs)}",
        )
        appendLine(
            "  last overlay launch: ${age(snapshot.lastOverlayLaunchAtMonotonicMs, nowMonotonicMs)}",
        )
        appendLine("  error: ${snapshot.logcatLastErrorCode?.name ?: "none"}")
        appendLine()
        appendLine("Shared clipboard read runtime")
        appendLine("  attempts: ${snapshot.readAttemptCount}")
        appendLine("  successful: ${snapshot.successfulReadCount}")
        appendLine("  failed: ${snapshot.failedReadCount}")
        appendLine("  last backend: ${snapshot.lastReadBackend?.name ?: "none"}")
        appendLine("  last result: ${snapshot.lastReadResult?.name ?: "none"}")
        appendLine(
            "  last attempt: ${age(snapshot.lastReadAttemptAtMonotonicMs, nowMonotonicMs)}",
        )
        append(
            "  last success: ${age(snapshot.lastSuccessfulReadAtMonotonicMs, nowMonotonicMs)}",
        )
    }

    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"

    private fun age(timestamp: Long?, now: Long): String {
        if (timestamp == null) {
            return "never"
        }
        val elapsed = (now - timestamp).coerceAtLeast(0)
        return when {
            elapsed < 1_000 -> "${elapsed} ms ago"
            elapsed < 60_000 -> "${elapsed / 1_000} s ago"
            else -> "${elapsed / 60_000} min ago"
        }
    }
}
