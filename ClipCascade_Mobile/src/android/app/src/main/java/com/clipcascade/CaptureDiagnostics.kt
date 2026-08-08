package com.clipcascade

/**
 * Process-local, payload-free diagnostics for Android capture paths.
 * Only counters, source names, stages, timestamps, and exception class names
 * are retained. Clipboard content is never stored here.
 */
object CaptureDiagnostics {
    data class Snapshot(
        val triggerCount: Long,
        val coalescedTriggerCount: Long,
        val shizukuAttemptCount: Long,
        val shizukuSuccessCount: Long,
        val overlayFallbackCount: Long,
        val emittedCount: Long,
        val ignoredCount: Long,
        val lastSource: String?,
        val lastStage: String?,
        val lastError: String?,
        val lastEventAt: Long?
    )

    private val lock = Any()
    private var triggerCount = 0L
    private var coalescedTriggerCount = 0L
    private var shizukuAttemptCount = 0L
    private var shizukuSuccessCount = 0L
    private var overlayFallbackCount = 0L
    private var emittedCount = 0L
    private var ignoredCount = 0L
    private var lastSource: String? = null
    private var lastStage: String? = null
    private var lastError: String? = null
    private var lastEventAt: Long? = null

    fun recordTrigger(source: String) = mutate(source, "trigger") { triggerCount += 1 }

    fun recordCoalesced(source: String) = mutate(source, "coalesced") {
        coalescedTriggerCount += 1
    }

    fun recordShizukuAttempt(source: String) = mutate(source, "shizuku_attempt") {
        shizukuAttemptCount += 1
    }

    fun recordShizukuSuccess(source: String) = mutate(source, "shizuku_success") {
        shizukuSuccessCount += 1
    }

    fun recordOverlayFallback(source: String, reason: String?) =
        mutate(source, "overlay_fallback", reason) { overlayFallbackCount += 1 }

    fun recordEmission(source: String) = mutate(source, "emitted") { emittedCount += 1 }

    fun recordIgnored(source: String, reason: String) = mutate(source, "ignored", reason) {
        ignoredCount += 1
    }

    fun recordFailure(source: String, stage: String, error: Throwable) =
        mutate(source, stage, error.javaClass.simpleName)

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            triggerCount = triggerCount,
            coalescedTriggerCount = coalescedTriggerCount,
            shizukuAttemptCount = shizukuAttemptCount,
            shizukuSuccessCount = shizukuSuccessCount,
            overlayFallbackCount = overlayFallbackCount,
            emittedCount = emittedCount,
            ignoredCount = ignoredCount,
            lastSource = lastSource,
            lastStage = lastStage,
            lastError = lastError,
            lastEventAt = lastEventAt
        )
    }

    fun reset() {
        synchronized(lock) {
            triggerCount = 0L
            coalescedTriggerCount = 0L
            shizukuAttemptCount = 0L
            shizukuSuccessCount = 0L
            overlayFallbackCount = 0L
            emittedCount = 0L
            ignoredCount = 0L
            lastSource = null
            lastStage = null
            lastError = null
            lastEventAt = null
        }
    }

    private fun mutate(
        source: String,
        stage: String,
        error: String? = null,
        update: () -> Unit = {}
    ) {
        synchronized(lock) {
            update()
            lastSource = source
            lastStage = stage
            lastError = error
            lastEventAt = System.currentTimeMillis()
        }
    }
}
