package com.clipcascade

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportBuilderTest {
    @Test
    fun reportContainsOperationalStagesWithoutSensitiveFields() {
        val report = DiagnosticReportBuilder.build(sampleInput())

        assertTrue(report.contains("Shizuku running: enabled"))
        assertTrue(report.contains("Duplicates suppressed: 7"))
        assertTrue(report.contains("Server mode: P2S"))
        assertTrue(report.contains("Count: 3"))
        assertTrue(report.contains("Head state: inflight"))
        assertTrue(report.contains("Head next attempt epoch ms: 6789"))
        assertTrue(report.contains("Privacy: clipboard payloads"))

        assertFalse(report.contains("secret clipboard payload"))
        assertFalse(report.contains("https://private.example"))
        assertFalse(report.contains("alice@example.com"))
        assertFalse(report.contains("contentHash"))
    }

    @Test
    fun freeFormValuesAreSingleLineBoundedAndRedacted() {
        val unsafe =
            "first\nsecond\tthird https://private.example/socket alice@example.com " +
                "x".repeat(500)
        val report = DiagnosticReportBuilder.build(
            sampleInput().copy(
                connection = sampleInput().connection.copy(websocketStatus = unsafe),
                capabilities = sampleInput().capabilities.copy(shizukuError = unsafe)
            )
        )

        assertFalse(report.contains("first\nsecond"))
        assertTrue(report.contains("first second third"))
        assertTrue(report.contains("[redacted-url]"))
        assertTrue(report.contains("[redacted-email]"))
        assertFalse(report.contains("https://private.example"))
        assertFalse(report.contains("alice@example.com"))
        assertTrue(report.contains("…"))
        report.lineSequence().forEach { line ->
            assertTrue("unexpectedly long line: ${line.length}", line.length <= 340)
        }
    }

    @Test
    fun missingOutboxIsReportedWithoutInventingState() {
        val report = DiagnosticReportBuilder.build(sampleInput().copy(outbox = null))

        assertTrue(report.contains("[P2S text outbox]\nStatus: unavailable"))
        assertFalse(report.contains("Count: 0"))
    }

    @Test
    fun negativeOutboxCountersAreClamped() {
        val report = DiagnosticReportBuilder.build(
            sampleInput().copy(
                outbox = DiagnosticReportBuilder.OutboxState(
                    loaded = true,
                    count = -3,
                    totalBytes = -10,
                    dropped = -2,
                    oldestCreatedAt = null,
                    headState = null,
                    headAttempts = -8,
                    headLastAttemptAt = null,
                    headNextAttemptAt = null
                )
            )
        )

        assertTrue(report.contains("Count: 0"))
        assertTrue(report.contains("Total wire bytes: 0"))
        assertTrue(report.contains("Dropped: 0"))
        assertTrue(report.contains("Head attempts: 0"))
        assertTrue(report.contains("Head next attempt epoch ms: none"))
    }

    private fun sampleInput(): DiagnosticReportBuilder.Input = DiagnosticReportBuilder.Input(
        generatedAt = "2026-07-27T17:30:00.000+09:00",
        appVersion = "3.2.0",
        buildType = "standalone",
        manufacturer = "HONOR",
        model = "ELP-AN00",
        androidRelease = "16",
        apiLevel = 36,
        capabilities = DiagnosticReportBuilder.CapabilityState(
            shizukuInstalled = true,
            shizukuRunning = true,
            shizukuPermission = true,
            shizukuServiceBound = true,
            shizukuServiceUid = 2000,
            shizukuError = null,
            accessibilityEnabled = true,
            overlayEnabled = true,
            readLogsEnabled = false,
            batteryExempt = true,
            runtimeActive = true
        ),
        connection = DiagnosticReportBuilder.ConnectionState(
            serverMode = "P2S",
            websocketRunning = "true",
            websocketStatus = "Connected - Subscribed"
        ),
        outbox = DiagnosticReportBuilder.OutboxState(
            loaded = true,
            count = 3,
            totalBytes = 2048,
            dropped = 1,
            oldestCreatedAt = 1234,
            headState = "inflight",
            headAttempts = 2,
            headLastAttemptAt = 5678,
            headNextAttemptAt = 6789
        ),
        capture = DiagnosticReportBuilder.CaptureState(
            triggerCount = 10,
            coalescedTriggerCount = 2,
            shizukuAttemptCount = 8,
            shizukuSuccessCount = 6,
            overlayFallbackCount = 2,
            emittedCount = 6,
            duplicateSuppressedCount = 7,
            ignoredCount = 1,
            lastSource = "accessibility",
            lastStage = "emitted",
            lastError = null,
            lastEventAt = 9999
        )
    )
}
