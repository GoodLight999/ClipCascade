package com.clipcascade

/**
 * Builds a user-shareable, payload-free diagnostic report.
 *
 * Inputs intentionally exclude clipboard content, content hashes, server URLs,
 * usernames, passwords, cookies, and encryption keys. Free-form status/error
 * strings are normalized and bounded before inclusion.
 */
object DiagnosticReportBuilder {
    data class CapabilityState(
        val shizukuInstalled: Boolean,
        val shizukuRunning: Boolean,
        val shizukuPermission: Boolean,
        val shizukuServiceBound: Boolean,
        val shizukuServiceUid: Int?,
        val shizukuError: String?,
        val accessibilityEnabled: Boolean,
        val overlayEnabled: Boolean,
        val readLogsEnabled: Boolean,
        val batteryExempt: Boolean,
        val runtimeActive: Boolean
    )

    data class ConnectionState(
        val serverMode: String?,
        val websocketRunning: String?,
        val websocketStatus: String?
    )

    data class OutboxState(
        val loaded: Boolean,
        val count: Int,
        val totalBytes: Long,
        val dropped: Int,
        val oldestCreatedAt: Long?,
        val headState: String?,
        val headAttempts: Int,
        val headLastAttemptAt: Long?
    )

    data class CaptureState(
        val triggerCount: Long,
        val coalescedTriggerCount: Long,
        val shizukuAttemptCount: Long,
        val shizukuSuccessCount: Long,
        val overlayFallbackCount: Long,
        val emittedCount: Long,
        val duplicateSuppressedCount: Long,
        val ignoredCount: Long,
        val lastSource: String?,
        val lastStage: String?,
        val lastError: String?,
        val lastEventAt: Long?
    )

    data class Input(
        val generatedAt: String,
        val appVersion: String,
        val buildType: String,
        val manufacturer: String,
        val model: String,
        val androidRelease: String,
        val apiLevel: Int,
        val capabilities: CapabilityState,
        val connection: ConnectionState,
        val outbox: OutboxState?,
        val capture: CaptureState
    )

    fun build(input: Input): String = buildString {
        appendLine("ClipCascade diagnostic report")
        appendLine("Generated: ${safe(input.generatedAt)}")
        appendLine("App: ${safe(input.appVersion)} (${safe(input.buildType)})")
        appendLine(
            "Device: ${safe(input.manufacturer)} ${safe(input.model)} | " +
                "Android ${safe(input.androidRelease)} | API ${input.apiLevel}"
        )
        appendLine()

        appendLine("[Capabilities]")
        appendLine("Shizuku installed: ${flag(input.capabilities.shizukuInstalled)}")
        appendLine("Shizuku running: ${flag(input.capabilities.shizukuRunning)}")
        appendLine("Shizuku permission: ${flag(input.capabilities.shizukuPermission)}")
        appendLine("Shizuku UserService: ${flag(input.capabilities.shizukuServiceBound)}")
        appendLine("Shizuku service UID: ${input.capabilities.shizukuServiceUid ?: "none"}")
        appendLine("Shizuku last error: ${safeOrNone(input.capabilities.shizukuError)}")
        appendLine("Accessibility: ${flag(input.capabilities.accessibilityEnabled)}")
        appendLine("Overlay fallback: ${flag(input.capabilities.overlayEnabled)}")
        appendLine("READ_LOGS trigger: ${flag(input.capabilities.readLogsEnabled)}")
        appendLine("Battery optimization exempt: ${flag(input.capabilities.batteryExempt)}")
        appendLine("ClipCascade runtime active: ${flag(input.capabilities.runtimeActive)}")
        appendLine()

        appendLine("[Capture pipeline]")
        appendLine("Triggers: ${input.capture.triggerCount}")
        appendLine("Coalesced triggers: ${input.capture.coalescedTriggerCount}")
        appendLine("Shizuku attempts: ${input.capture.shizukuAttemptCount}")
        appendLine("Shizuku successes: ${input.capture.shizukuSuccessCount}")
        appendLine("Overlay fallbacks: ${input.capture.overlayFallbackCount}")
        appendLine("Emitted to JavaScript: ${input.capture.emittedCount}")
        appendLine("Duplicates suppressed: ${input.capture.duplicateSuppressedCount}")
        appendLine("Ignored/unavailable: ${input.capture.ignoredCount}")
        appendLine("Last source: ${safeOrNone(input.capture.lastSource)}")
        appendLine("Last stage: ${safeOrNone(input.capture.lastStage)}")
        appendLine("Last error: ${safeOrNone(input.capture.lastError)}")
        appendLine("Last event epoch ms: ${input.capture.lastEventAt ?: "none"}")
        appendLine()

        appendLine("[Connection]")
        appendLine("Server mode: ${safeOrNone(input.connection.serverMode)}")
        appendLine("Foreground/WebSocket requested: ${safeOrNone(input.connection.websocketRunning)}")
        appendLine("Connection status: ${safeOrNone(input.connection.websocketStatus)}")
        appendLine()

        appendLine("[P2S text outbox]")
        val outbox = input.outbox
        if (outbox == null) {
            appendLine("Status: unavailable")
        } else {
            appendLine("Loaded: ${flag(outbox.loaded)}")
            appendLine("Count: ${outbox.count.coerceAtLeast(0)}")
            appendLine("Total wire bytes: ${outbox.totalBytes.coerceAtLeast(0L)}")
            appendLine("Dropped: ${outbox.dropped.coerceAtLeast(0)}")
            appendLine("Oldest created epoch ms: ${outbox.oldestCreatedAt ?: "none"}")
            appendLine("Head state: ${safeOrNone(outbox.headState)}")
            appendLine("Head attempts: ${outbox.headAttempts.coerceAtLeast(0)}")
            appendLine("Head last attempt epoch ms: ${outbox.headLastAttemptAt ?: "none"}")
        }
        appendLine()

        appendLine("Privacy: clipboard payloads, hashes, credentials, cookies, server URLs, and keys are not included.")
    }.trimEnd()

    private fun flag(value: Boolean): String = if (value) "enabled" else "disabled"

    private fun safeOrNone(value: String?): String =
        value?.takeIf { it.isNotBlank() }?.let(::safe) ?: "none"

    internal fun safe(value: String, maxLength: Int = 300): String {
        val normalized = value
            .replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isEmpty()) return "none"
        return if (normalized.length <= maxLength) {
            normalized
        } else {
            normalized.take(maxLength) + "…"
        }
    }
}
