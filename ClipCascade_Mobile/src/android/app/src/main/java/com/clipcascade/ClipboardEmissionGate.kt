package com.clipcascade

import java.security.MessageDigest

/**
 * Short-window, process-local duplicate suppression shared by every Android
 * capture source before content reaches React Native.
 *
 * This is intentionally not durable history. Re-copying the same content after
 * the window remains a valid user action.
 */
internal class ClipboardEmissionGate(
    private val duplicateWindowMs: Long = 1_500L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    init {
        require(duplicateWindowMs >= 0L) { "duplicateWindowMs must be non-negative" }
    }

    private val lock = Any()
    private var lastFingerprint: String? = null
    private var lastAcceptedAt: Long = 0L

    fun shouldEmit(content: String, type: String): Boolean {
        val fingerprint = fingerprint(content, type)
        val now = clock()

        synchronized(lock) {
            val sameContent = lastFingerprint == fingerprint
            val insideWindow = now >= lastAcceptedAt && now - lastAcceptedAt < duplicateWindowMs
            if (sameContent && insideWindow) {
                return false
            }

            lastFingerprint = fingerprint
            lastAcceptedAt = now
            return true
        }
    }

    internal companion object {
        fun fingerprint(content: String, type: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(type.toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(content.toByteArray(Charsets.UTF_8))
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
