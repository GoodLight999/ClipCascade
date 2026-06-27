package com.clipcascade

import java.text.Normalizer

/**
 * Conservative verification-value extractor for notification text.
 *
 * A candidate must be close to an authentication-related phrase. Date, time,
 * amount, phone-number, and tracking/order patterns are rejected locally so
 * unrelated notification data never enters the relay queue.
 */
object OtpCodeExtractor {
    private val keywordRegex = Regex(
        pattern = "(?i)(otp|one[\\s-]?time(?:\\s+(?:password|passcode|code))?|verification(?:\\s+code)?|security\\s+code|authentication\\s+code|auth\\s+code|login\\s+code|confirmation\\s+code|your\\s+code|use\\s+(?:this\\s+)?code|enter\\s+(?:the\\s+)?code|passcode|認証(?:コード|番号)?|確認コード|ログインコード|ワンタイム(?:パスワード|コード)?|暗証番号|セキュリティコード)",
    )

    private val candidateRegex = Regex(
        pattern = "(?<![\\p{L}\\p{N}])(" +
            "(?:[A-Z0-9]{2,5}(?:-[A-Z0-9]{2,5}){1,2})" +
            "|(?:\\d{2,4}(?:[\\s-]\\d{2,4}){1,2})" +
            "|(?:[A-Z0-9]{4,10})" +
            ")(?![\\p{L}\\p{N}])",
        option = RegexOption.IGNORE_CASE,
    )

    private val datePatterns = listOf(
        Regex("^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}$"),
        Regex("^\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}$"),
    )
    private val timePattern = Regex("^\\d{1,2}:\\d{2}(?::\\d{2})?$")
    private val amountContext = Regex(
        "(?i)([¥￥$€£]|円|jpy|usd|eur|gbp|dollars?|yen|amount|price|料金|金額|残高)",
    )
    private val phoneContext = Regex("(?i)(phone|tel|mobile|電話|携帯|連絡先)")
    private val trackingContext = Regex(
        "(?i)(tracking|shipment|delivery|order(?:\\s+(?:id|number))?|追跡|配送|注文番号)",
    )

    fun extract(text: String): String? {
        if (text.isBlank()) return null

        val normalizedText = Normalizer.normalize(
            text.replace('\u00A0', ' '),
            Normalizer.Form.NFKC,
        )
        val keywordMatches = keywordRegex.findAll(normalizedText).toList()
        if (keywordMatches.isEmpty()) return null

        data class ScoredCandidate(val value: String, val score: Int)
        val candidates = mutableListOf<ScoredCandidate>()

        for (keyword in keywordMatches) {
            val from = (keyword.range.first - 32).coerceAtLeast(0)
            val to = (keyword.range.last + 64).coerceAtMost(normalizedText.lastIndex)
            if (to < from) continue

            val window = normalizedText.substring(from, to + 1)
            candidateRegex.findAll(window).forEach { match ->
                val raw = match.groupValues[1].trim()
                val value = raw.replace(" ", "").replace("-", "")
                val absoluteStart = from + match.range.first
                val absoluteEnd = from + match.range.last

                if (!isValidCandidate(value)) return@forEach
                if (looksLikeStructuredNonCode(raw, value)) return@forEach
                if (
                    contextRejectsCandidate(
                        normalizedText,
                        absoluteStart,
                        absoluteEnd,
                        value,
                    )
                ) {
                    return@forEach
                }

                val distance = when {
                    absoluteStart > keyword.range.last ->
                        absoluteStart - keyword.range.last
                    absoluteEnd < keyword.range.first ->
                        keyword.range.first - absoluteEnd
                    else -> 0
                }

                var score = 120 - distance.coerceAtMost(100)
                if (absoluteStart > keyword.range.last) score += 20
                if (value.all(Char::isDigit)) score += 20
                if (value.length == 6) score += 30
                if (value.length in 4..8) score += 10
                candidates += ScoredCandidate(value.uppercase(), score)
            }
        }

        return candidates.maxByOrNull { it.score }?.value
    }

    private fun isValidCandidate(value: String): Boolean {
        if (!value.all { it.isLetterOrDigit() }) return false
        if (!value.any(Char::isDigit)) return false

        val digitsOnly = value.all(Char::isDigit)
        if (digitsOnly && value.length !in 4..8) return false
        if (!digitsOnly && value.length !in 5..10) return false
        if (!digitsOnly && !value.any(Char::isLetter)) return false

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

    private fun looksLikeStructuredNonCode(raw: String, value: String): Boolean {
        if (datePatterns.any { it.matches(raw) }) return true
        if (timePattern.matches(raw)) return true

        val separators = raw.count { it == '-' || it == ' ' }
        if (value.all(Char::isDigit) && separators >= 2 && value.length >= 8) {
            return true
        }
        return false
    }

    private fun contextRejectsCandidate(
        text: String,
        start: Int,
        end: Int,
        value: String,
    ): Boolean {
        val contextStart = (start - 24).coerceAtLeast(0)
        val contextEnd = (end + 24).coerceAtMost(text.lastIndex)
        val context = text.substring(contextStart, contextEnd + 1)

        if (value.all(Char::isDigit) && amountContext.containsMatchIn(context)) {
            return true
        }
        if (value.length >= 7 && phoneContext.containsMatchIn(context)) {
            return true
        }
        if (value.length >= 8 && trackingContext.containsMatchIn(context)) {
            return true
        }
        return false
    }
}
