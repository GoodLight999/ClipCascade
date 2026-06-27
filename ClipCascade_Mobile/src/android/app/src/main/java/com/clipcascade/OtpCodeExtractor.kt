package com.clipcascade

/**
 * Conservative OTP extractor for notification text.
 *
 * It requires an OTP/code-related keyword near the candidate. This avoids
 * forwarding unrelated amounts, dates, phone numbers, and tracking IDs from
 * ordinary notifications.
 */
object OtpCodeExtractor {
    private val keywordRegex = Regex(
        pattern = "(?i)(otp|one[\\s-]?time(?:\\s+(?:password|passcode))?|verification(?:\\s+code)?|security\\s+code|authentication\\s+code|auth\\s+code|passcode|認証(?:コード|番号)?|確認コード|ワンタイム(?:パスワード)?|暗証番号|セキュリティコード)",
    )

    private val candidateRegex = Regex(
        pattern = "(?<![A-Z0-9])([A-Z0-9](?:[\\s-]?[A-Z0-9]){3,11})(?![A-Z0-9])",
        option = RegexOption.IGNORE_CASE,
    )

    fun extract(text: String): String? {
        if (text.isBlank()) return null

        val normalizedText = text.replace('\u00A0', ' ')
        val keywordMatches = keywordRegex.findAll(normalizedText).toList()
        if (keywordMatches.isEmpty()) return null

        data class ScoredCandidate(val value: String, val score: Int)
        val candidates = mutableListOf<ScoredCandidate>()

        for (keyword in keywordMatches) {
            val from = (keyword.range.first - 40).coerceAtLeast(0)
            val to = (keyword.range.last + 56).coerceAtMost(normalizedText.lastIndex)
            if (to < from) continue

            val window = normalizedText.substring(from, to + 1)
            candidateRegex.findAll(window).forEach { match ->
                val raw = match.groupValues[1]
                val value = raw.replace(" ", "").replace("-", "")
                if (!isValidCandidate(value)) return@forEach

                val absoluteStart = from + match.range.first
                val distance = when {
                    absoluteStart > keyword.range.last -> absoluteStart - keyword.range.last
                    absoluteStart + raw.length < keyword.range.first ->
                        keyword.range.first - (absoluteStart + raw.length)
                    else -> 0
                }

                var score = 100 - distance.coerceAtMost(80)
                if (value.all(Char::isDigit)) score += 30
                if (value.length == 6) score += 20
                if (value.length in 4..8) score += 10
                candidates += ScoredCandidate(value, score)
            }
        }

        return candidates.maxByOrNull { it.score }?.value
    }

    private fun isValidCandidate(value: String): Boolean {
        if (value.length !in 4..10) return false
        if (!value.any(Char::isDigit)) return false
        if (!value.all { it.isLetterOrDigit() }) return false

        val lower = value.lowercase()
        val blockedWords = setOf(
            "code",
            "codes",
            "otpcode",
            "passcode",
            "security",
            "verify",
        )
        return lower !in blockedWords
    }
}
