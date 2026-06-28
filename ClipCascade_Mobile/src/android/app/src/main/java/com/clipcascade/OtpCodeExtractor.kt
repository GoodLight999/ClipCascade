package com.clipcascade

import java.text.Normalizer

/**
 * Conservative bilingual verification-value extractor for notification text.
 *
 * Candidates are ranked only near Japanese or English authentication language.
 * Dates, times, amounts, phone numbers, delivery references, URLs, and other
 * structured values are rejected locally before anything enters the queue.
 */
object OtpCodeExtractor {
    private val keywordRegex = Regex(
        pattern = "(?i)(" +
            "otp" +
            "|one[\\s-]?time(?:\\s+(?:password|passcode|pin|code))?" +
            "|(?:verification|security|authentication|auth|login|sign[\\s-]?in|" +
            "confirmation|access|two[\\s-]?(?:factor|step)|2fa)" +
            "(?:\\s+(?:code|number|passcode|password|pin))" +
            "|verify\\s+(?:your\\s+)?(?:account|email|e-mail|phone|number|identity)" +
            "|verify\\s+[A-Z0-9._%+-]+@[A-Z0-9.-]+" +
            "|(?:your|this)\\s+(?:verification\\s+|security\\s+|login\\s+|" +
            "sign[\\s-]?in\\s+|confirmation\\s+|access\\s+)?(?:code|passcode)" +
            "|use\\s+(?:this\\s+)?(?:code|passcode)" +
            "|enter\\s+(?:the\\s+)?(?:code|passcode)" +
            "|passcode\\s+(?:is|for|to)" +
            "|認証(?:コード|番号|キー)?" +
            "|確認(?:コード|番号)" +
            "|ログイン(?:コード|認証番号|認証コード)?" +
            "|サインイン(?:コード|認証番号|認証コード)?" +
            "|ワンタイム(?:パスワード|パスコード|コード|暗証番号)?" +
            "|本人確認(?:コード|番号)?" +
            "|(?:二|2)段階認証(?:コード|番号)?" +
            "|セキュリティ(?:コード|番号)" +
            "|暗証番号" +
            ")",
    )

    private val candidateRegex = Regex(
        pattern = "(?<![A-Z0-9])(" +
            "(?:[A-Z]-\\d{4,8})" +
            "|(?:\\d{2,4}(?:[\\s\\-–—]\\d{2,4}){1,2})" +
            "|(?:[A-Z0-9]{2,5}(?:[\\-–—][A-Z0-9]{2,5}){1,2})" +
            "|(?:[A-Z0-9]{4,10})" +
            ")(?![A-Z0-9])",
        option = RegexOption.IGNORE_CASE,
    )

    private val datePatterns = listOf(
        Regex("^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}$"),
        Regex("^\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}$"),
    )
    private val timePattern = Regex("^\\d{1,2}:\\d{2}(?::\\d{2})?$")
    private val amountContext = Regex(
        "(?i)([¥￥$€£]|円|jpy|usd|eur|gbp|dollars?|yen|amount|price|fee|" +
            "料金|金額|残高|支払|決済|購入)",
    )
    private val phoneContext = Regex(
        "(?i)(phone|tel|telephone|mobile|call|電話|携帯|連絡先)",
    )
    private val trackingContext = Regex(
        "(?i)(tracking|shipment|delivery|parcel|order(?:\\s+(?:id|number|no))?|" +
            "reference(?:\\s+(?:id|number|no))?|追跡|配送|荷物|注文番号|受付番号)",
    )
    private val calendarContext = Regex(
        "(?i)(date|year|scheduled|schedule|appointment|expires?\\s+on|" +
            "日付|年|予定|予約|有効期限)",
    )
    private val expiryContext = Regex(
        "(?i)(expires?|valid\\s+for|minutes?|seconds?|do\\s+not\\s+share|" +
            "有効|期限|分以内|秒以内|共有しない|教えない)",
    )
    private val urlOrEmail = Regex(
        "(?i)(https?://\\S+|www\\.\\S+|[A-Z0-9._%+-]+@[A-Z0-9.-]+)",
    )
    private val explicitSeparator = Regex(
        "(?i)^[\\s:：=\\-–—]*(?:is|is\\s+your|は|が|です|になります)?" +
            "[\\s:：=\\-–—]*$",
    )

    private data class ScoredCandidate(
        val value: String,
        val score: Int,
        val start: Int,
    )

    fun extract(text: String): String? {
        if (text.isBlank()) return null

        val normalizedText = Normalizer.normalize(
            text.replace('\u00A0', ' '),
            Normalizer.Form.NFKC,
        )
        val keywordMatches = keywordRegex.findAll(normalizedText).toList()
        if (keywordMatches.isEmpty()) return null

        val candidatesByPosition = linkedMapOf<String, ScoredCandidate>()
        for (keyword in keywordMatches) {
            val from = (keyword.range.first - 48).coerceAtLeast(0)
            val to = (keyword.range.last + 96).coerceAtMost(normalizedText.lastIndex)
            if (to < from) continue

            val window = normalizedText.substring(from, to + 1)
            candidateRegex.findAll(window).forEach { match ->
                val raw = match.groupValues[1].trim()
                val value = normalizeCandidate(raw)
                val absoluteStart = from + match.range.first
                val absoluteEnd = from + match.range.last

                if (!isValidCandidate(value)) return@forEach
                if (looksLikeStructuredNonCode(raw, value)) return@forEach
                if (
                    contextRejectsCandidate(
                        normalizedText,
                        absoluteStart,
                        absoluteEnd,
                        raw,
                        value,
                    )
                ) {
                    return@forEach
                }

                val distance = distanceBetween(
                    absoluteStart,
                    absoluteEnd,
                    keyword.range.first,
                    keyword.range.last,
                )
                var score = 170 - distance.coerceAtMost(130)
                if (absoluteStart > keyword.range.last) score += 15
                if (sameLine(normalizedText, absoluteStart, keyword.range.first)) score += 10
                if (hasExplicitRelation(normalizedText, keyword, absoluteStart, absoluteEnd)) {
                    score += 35
                }
                if (value.all(Char::isDigit)) score += 20
                if (value.length == 6) score += 35
                if (value.length == 4 || value.length == 8) score += 15
                if (value.any(Char::isLetter) && value.any(Char::isDigit)) score += 15
                if (expiryNear(normalizedText, absoluteStart, absoluteEnd)) score += 8
                if (strongKeyword(keyword.value)) score += 10

                val candidate = ScoredCandidate(value.uppercase(), score, absoluteStart)
                val key = "$absoluteStart:${candidate.value}"
                val previous = candidatesByPosition[key]
                if (previous == null || candidate.score > previous.score) {
                    candidatesByPosition[key] = candidate
                }
            }
        }

        return candidatesByPosition.values
            .filter { it.score >= 80 }
            .sortedWith(compareByDescending<ScoredCandidate> { it.score }.thenBy { it.start })
            .firstOrNull()
            ?.value
    }

    private fun normalizeCandidate(raw: String): String =
        raw.replace(Regex("[\\s\\-–—]"), "")

    private fun isValidCandidate(value: String): Boolean {
        if (!value.all { it in 'A'..'Z' || it in 'a'..'z' || it.isDigit() }) return false
        if (!value.any(Char::isDigit)) return false

        val digitsOnly = value.all(Char::isDigit)
        if (digitsOnly && value.length !in 4..8) return false
        if (!digitsOnly && value.length !in 5..10) return false
        if (!digitsOnly && !value.any(Char::isLetter)) return false

        val blockedWords = setOf(
            "code",
            "codes",
            "otpcode",
            "passcode",
            "security",
            "verify",
            "login",
        )
        return value.lowercase() !in blockedWords
    }

    private fun looksLikeStructuredNonCode(raw: String, value: String): Boolean {
        if (datePatterns.any { it.matches(raw) }) return true
        if (timePattern.matches(raw)) return true

        val separators = raw.count { it == '-' || it == ' ' || it == '–' || it == '—' }
        if (value.all(Char::isDigit) && separators >= 2 && value.length >= 8) {
            return true
        }
        return false
    }

    private fun contextRejectsCandidate(
        text: String,
        start: Int,
        end: Int,
        raw: String,
        value: String,
    ): Boolean {
        val contextStart = (start - 40).coerceAtLeast(0)
        val contextEnd = (end + 40).coerceAtMost(text.lastIndex)
        val context = text.substring(contextStart, contextEnd + 1)
        val closeContextStart = (start - 6).coerceAtLeast(0)
        val closeContextEnd = (end + 6).coerceAtMost(text.lastIndex)
        val closeContext = text.substring(closeContextStart, closeContextEnd + 1)

        if (overlapsStructuredToken(text, start, end, urlOrEmail)) return true
        if (value.all(Char::isDigit) && amountContext.containsMatchIn(closeContext)) return true
        if (value.length >= 7 && phoneContext.containsMatchIn(context)) return true
        if (value.length >= 8 && trackingContext.containsMatchIn(context)) return true

        val numeric = value.toIntOrNull()
        if (
            raw.length == 4 &&
            numeric != null &&
            numeric in 1900..2099 &&
            calendarContext.containsMatchIn(context)
        ) {
            return true
        }
        return false
    }

    private fun overlapsStructuredToken(
        text: String,
        candidateStart: Int,
        candidateEnd: Int,
        pattern: Regex,
    ): Boolean {
        val from = (candidateStart - 64).coerceAtLeast(0)
        val to = (candidateEnd + 64).coerceAtMost(text.lastIndex)
        return pattern.findAll(text.substring(from, to + 1)).any { match ->
            val matchStart = from + match.range.first
            val matchEnd = from + match.range.last
            candidateStart <= matchEnd && candidateEnd >= matchStart
        }
    }

    private fun distanceBetween(
        firstStart: Int,
        firstEnd: Int,
        secondStart: Int,
        secondEnd: Int,
    ): Int = when {
        firstStart > secondEnd -> firstStart - secondEnd
        firstEnd < secondStart -> secondStart - firstEnd
        else -> 0
    }

    private fun sameLine(text: String, first: Int, second: Int): Boolean {
        val from = minOf(first, second)
        val to = maxOf(first, second)
        return !text.substring(from, to).contains('\n')
    }

    private fun hasExplicitRelation(
        text: String,
        keyword: MatchResult,
        candidateStart: Int,
        candidateEnd: Int,
    ): Boolean {
        val between = when {
            candidateStart > keyword.range.last ->
                text.substring(keyword.range.last + 1, candidateStart)
            candidateEnd < keyword.range.first ->
                text.substring(candidateEnd + 1, keyword.range.first)
            else -> return true
        }
        return between.length <= 24 && explicitSeparator.matches(between)
    }

    private fun expiryNear(text: String, start: Int, end: Int): Boolean {
        val from = (start - 24).coerceAtLeast(0)
        val to = (end + 64).coerceAtMost(text.lastIndex)
        return expiryContext.containsMatchIn(text.substring(from, to + 1))
    }

    private fun strongKeyword(value: String): Boolean {
        val normalized = value.lowercase()
        return normalized.contains("otp") ||
            normalized.contains("verification") ||
            normalized.contains("one-time") ||
            normalized.contains("one time") ||
            value.contains("認証") ||
            value.contains("ワンタイム") ||
            value.contains("本人確認")
    }
}
