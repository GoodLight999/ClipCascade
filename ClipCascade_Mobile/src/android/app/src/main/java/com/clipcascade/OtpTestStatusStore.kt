package com.clipcascade

import android.content.Context

object OtpTestStatusStore {
    const val RELAY_ID_PREFIX = "synthetic-test:"
    const val POSTED = "posted"
    const val DETECTED = "detected"
    const val QUEUED = "queued"
    const val ACKNOWLEDGED = "acknowledged"
    const val EXTRACT_FAILED = "extract_failed"
    const val DEDUPLICATED = "deduplicated"
    const val POST_FAILED = "post_failed"

    private const val FILE_NAME = "synthetic_verification_test"
    private const val STATUS_TTL_MS = 5 * 60_000L

    private val PROGRESSED_STATES = setOf(
        QUEUED,
        ACKNOWLEDGED,
    )

    data class Snapshot(
        val state: String,
        val value: String,
        val relayId: String,
        val timestamp: Long,
    )

    fun start(context: Context, value: String) = save(context, POSTED, value, "")

    fun detected(context: Context, value: String) = saveUnlessProgressed(context, DETECTED, value, "")

    fun queued(context: Context, value: String, relayId: String) =
        saveUnlessAcknowledged(context, QUEUED, value, relayId)

    fun extractionFailed(context: Context, value: String) =
        saveUnlessProgressed(context, EXTRACT_FAILED, value, "")

    fun deduplicated(context: Context, value: String) =
        saveUnlessProgressed(context, DEDUPLICATED, value, "")

    fun postFailed(context: Context, detail: String) =
        save(context, POST_FAILED, detail.take(80), "")

    @Synchronized
    fun acknowledged(context: Context, relayId: String) {
        val current = read(context) ?: return
        if (current.relayId == relayId) {
            save(context, ACKNOWLEDGED, current.value, relayId)
        }
    }

    @Synchronized
    fun read(context: Context): Snapshot? {
        val preferences = context.applicationContext.getSharedPreferences(
            FILE_NAME,
            Context.MODE_PRIVATE,
        )
        val state = preferences.getString("state", null).orEmpty()
        if (state.isBlank()) return null
        val timestamp = preferences.getLong("timestamp", 0L)
        val age = System.currentTimeMillis() - timestamp
        if (timestamp <= 0L || age !in 0..STATUS_TTL_MS) {
            preferences.edit().clear().commit()
            return null
        }
        return Snapshot(
            state = state,
            value = preferences.getString("value", "").orEmpty(),
            relayId = preferences.getString("relay_id", "").orEmpty(),
            timestamp = timestamp,
        )
    }

    @Synchronized
    private fun saveUnlessProgressed(
        context: Context,
        state: String,
        value: String,
        relayId: String,
    ) {
        val current = read(context)
        if (current != null && current.value == value && current.state in PROGRESSED_STATES) return
        save(context, state, value, relayId)
    }

    @Synchronized
    private fun saveUnlessAcknowledged(
        context: Context,
        state: String,
        value: String,
        relayId: String,
    ) {
        val current = read(context)
        if (current != null && current.value == value && current.state == ACKNOWLEDGED) return
        save(context, state, value, relayId)
    }

    @Synchronized
    private fun save(
        context: Context,
        state: String,
        value: String,
        relayId: String,
    ) {
        context.applicationContext.getSharedPreferences(
            FILE_NAME,
            Context.MODE_PRIVATE,
        ).edit()
            .putString("state", state)
            .putString("value", value)
            .putString("relay_id", relayId)
            .putLong("timestamp", System.currentTimeMillis())
            .commit()
    }
}
