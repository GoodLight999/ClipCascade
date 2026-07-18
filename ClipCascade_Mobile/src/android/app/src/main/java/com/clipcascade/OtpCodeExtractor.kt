package com.clipcascade

import java.text.Normalizer

/**
 * Conservative multilingual verification-value extractor for notification text.
 *
 * The extractor follows the same broad shape as WebOTP / SMS Retriever /
 * one-time-code autofill systems: a short 4-10 character code is accepted only
 * when it is structurally bound to authentication, verification, login,
 * recovery, or a domain-bound OTP line. Dates, times, amounts, phone numbers,
 * tracking/order IDs, coupon codes, URLs, email local-parts, and SMS Retriever
 * app-hash lines are rejected before anything enters the queue.
 */
object OtpCodeExtractor {
    private val keywordRegex = Regex(
        pattern = "(?i)(" +
            "otp" +
            "|one[\\s-]?time(?:\\s+(?:password|passcode|pin|code))?" +
            "|(?:verification|security|authentication|auth|login|log[\\s-]?in|sign[\\s-]?in|" +
            "signin|confirmation|access|two[\\s-]?(?:factor|step)|2fa|mfa|" +
            "authorization|authorisation|approval|temporary|recovery|password[\\s-]?reset|" +
            "reset[\\s-]?password|device|email|phone)" +
            "(?:\\s+(?:code|number|passcode|password|pin|token))" +
            "|(?:login|log[\\s-]?in|sign[\\s-]?in|signin|password[\\s-]?reset|" +
            "account[\\s-]?recovery|device)\\s+(?:code|token)" +
            "|(?:manually\\s+)?(?:use|enter|input|type|paste|submit)\\s+(?:the\\s+)?" +
            "(?:login\\s+|log[\\s-]?in\\s+|sign[\\s-]?in\\s+|signin\\s+|verification\\s+|" +
            "security\\s+|authentication\\s+|auth\\s+|one[\\s-]?time\\s+|temporary\\s+)?" +
            "(?:code|passcode|pin|token)(?:\\s+above)?" +
            "|verify\\s+(?:your\\s+)?(?:account|email|e-mail|phone|number|identity|login|sign[\\s-]?in)" +
            "|verify\\s+[A-Z0-9._%+-]+@[A-Z0-9.-]+" +
            "|confirm\\s+(?:your\\s+)?(?:email|e-mail|phone|identity|account|login|sign[\\s-]?in)" +
            "|authenticate\\s+(?:your\\s+)?(?:account|login|sign[\\s-]?in|identity)" +
            "|(?:your|this)\\s+(?:verification\\s+|security\\s+|login\\s+|log[\\s-]?in\\s+|" +
            "sign[\\s-]?in\\s+|signin\\s+|confirmation\\s+|access\\s+|temporary\\s+|" +
            "authorization\\s+|approval\\s+|recovery\\s+)?(?:code|passcode|pin|token)" +
            "|use\\s+(?:this\\s+)?(?:code|passcode|pin|token)" +
            "|(?:code|passcode|pin|token)\\s+(?:is|for|to)" +
            "|verify\\s+(?:it'?s|this\\s+is)\\s+you" +
            "|認証(?:コード|番号|キー|トークン)?" +
            "|確認(?:コード|番号)" +
            "|検証(?:コード|番号)" +
            "|承認(?:コード|番号)" +
            "|認可(?:コード|番号)" +
            "|ログイン(?:コード|認証番号|認証コード|番号)?" +
            "|サインイン(?:コード|認証番号|認証コード|番号)?" +
            "|ワンタイム(?:パスワード|パスコード|コード|暗証番号)?" +
            "|本人確認(?:コード|番号)?" +
            "|(?:二|2)段階認証(?:コード|番号)?" +
            "|セキュリティ(?:コード|番号)" +
            "|暗証番号" +
            "|パスコード" +
            "|パスワード(?:リセット|再設定)(?:コード|番号)?" +
            "|アカウント(?:復旧|回復)(?:コード|番号)?" +
            "|验证码|驗證碼|登录码|登入碼|登錄碼|一次性(?:密码|密碼)|動態碼|动态码" +
            "|인증(?:번호|코드)|로그인(?:번호|코드)|보안(?:번호|코드)" +
            "|c[oó]digo\\s+de\\s+(?:verificaci[oó]n|seguridad|acceso)" +
            "|code\\s+de\\s+(?:v[ée]rification|s[ée]curit[ée]|connexion)" +
            "|(?:best[aä]tigungs|sicherheits|anmelde)code" +
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

    private val domainBoundCodeRegex = Regex(
        pattern = "(?i)(?:^|\\s)@[A-Z0-9.-]+\\s+#([A-Z0-9][A-Z0-9\\s\\-–—]{2,16}[A-Z0-9])(?![A-Z0-9])",
    )
    private val actionCodeRegex = Regex(
        pattern = "(?i)(?:use|enter|input|type|paste|submit)\\s+(?:this\\s+)?(?:code\\s+|passcode\\s+|pin\\s+|token\\s+)?" +
            "([A-Z0-9][A-Z0-9\\s\\-–—]{2,16}[A-Z0-9])\\s+" +
            "(?:to|for)\\s+(?:verify|confirm|authenticate|login|log\\s*in|sign\\s*in|continue|access|complete)",
    )
    private val labelThenCodeRegex = Regex(
        pattern = "(?i)(?:verification|security|authentication|auth|login|log[\\s-]?in|sign[\\s-]?in|" +
            "signin|one[\\s-]?time|temporary|authorization|approval|recovery|device|" +
            "認証|確認|ログイン|サインイン|ワンタイム|本人確認|验证码|驗證碼|인증)" +
            "[^\\nA-Z0-9]{0,24}(?:code|number|passcode|pin|token|コード|番号)?" +
            "[^\\nA-Z0-9]{0,12}([A-Z0-9][A-Z0-9\\s\\-–—]{2,16}[A-Z0-9])",
    )
    private val japaneseActionCodeRegex = Regex(
        pattern = "(?i)([A-Z0-9][A-Z0-9\\s\\-–—]{2,16}[A-Z0-9])\\s*(?:を|が|は)?\\s*" +
            "(?:入力|使用|確認|認証|承認|ログイン|サインイン|送信|貼り付け)",
    )

    private val datePatterns = listOf(
        Regex("^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}$"),
        Regex("^\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}$"),
    )
    private val timePattern = Regex("^\\d{1,2}:\\d{2}(?::\\d{2})?$")
    private val amountContext = Regex(
        "(?i)([¥￥$€£]|円|jpy|usd|eur|gbp|dollars?|yen|amount|price|fee|" +
            "料金|金額|残高|支払|決済|購入|請求|invoice|receipt)",
    )
    private val phoneContext = Regex(
        "(?i)(phone|tel|telephone|mobile|call|fax|電話|携帯|連絡先|電話番号)",
    )
    private val trackingContext = Regex(
        "(?i)(tracking|shipment|delivery|parcel|order(?:\\s+(?:id|number|no))?|" +
            "reference(?:\\s+(?:id|number|no))?|booking|reservation|ticket|case\\s+(?:id|number|no)|" +
            "追跡|配送|荷物|注文番号|受付番号|予約番号|整理番号|チケット)",
    )
    private val calendarContext = Regex(
        "(?i)(date|year|scheduled|schedule|appointment|expires?\\s+on|" +
            "日付|年|予定|予約|有効期限)",
    )
    private val expiryContext = Regex(
        "(?i)(expires?|valid\\s+for|valid\\s+until|within|minutes?|mins?|seconds?|secs?|" +
            "do\\s+not\\s+share|never\\s+share|don't\\s+share|didn'?t\\s+request|" +
            "有効|期限|分以内|秒以内|共有しない|教えない|リクエストしていない)",
    )
    private val urlOrEmail = Regex(
        "(?i)(https?://\\S+|www\\.\\S+|[A-Z0-9._%+-]+@[A-Z0-9.-]+)",
    )
    private val explicitSeparator = Regex(
        "(?i)^[\\s:：=\\-–—]*(?:is|is\\s+your|は|が|です|になります| lautet| est)?" +
            "[\\s:：=\\-–—]*$",
    )
    private val standaloneCodeLineResidue = Regex(
        "(?i)^(?:" +
            "code|otp|pin|passcode|password|login|log[\\s-]?in|sign[\\s-]?in|signin|verification|security|" +
            "one[\\s-]?time|temporary|auth|authentication|認証|確認|ログイン|サインイン|ワンタイム|" +
            "コード|番号|暗証番号|[:：=\\-–—#\\s]|" +
            "[A-Z0-9._%+-]{1,32}\\s+(?:logo|ロゴ)|ロゴ" +
            ")*$",
    )
    private val nonAuthCodeContext = Regex(
        "(?i)(promotion|promo|coupon|discount|voucher|gift\\s*card|offer|sale|referral|invite|" +
            "postal|zip|error|status|tracking|shipment|delivery|parcel|order|booking|reservation|" +
            "ticket|case|invoice|receipt|キャンペーン|クーポン|割引|紹介|招待|郵便番号|" +
            "エラー|ステータス|追跡|配送|注文|予約|チケット|請求|領収)",
    )
    private val strongAuthContext = Regex(
        "(?i)(otp|one[\\s-]?time|verification|security|authentication|auth|login\\s+code|" +
            "log[\\s-]?in\\s+code|sign[\\s-]?in\\s+code|signin\\s+code|2fa|mfa|" +
            "password[\\s-]?reset|account[\\s-]?recovery|verify\\s+(?:your\\s+)?(?:account|email|phone|identity)|" +
            "confirm\\s+(?:your\\s+)?(?:email|phone|identity|account)|" +
            "認証|確認コード|ログイン(?:コード|認証)|サインイン|ワンタイム|本人確認|" +
            "验证码|驗證碼|인증)",
    )
    private val weakEphemeralContext = Regex(
        "(?i)(valid\\s+for|expires?|within\\s+\\d+|minutes?|seconds?|do\\s+not\\s+share|" +
            "有効|期限|分以内|秒以内|共有しない|教えない)",
    )
    private val smsRetrieverHashLine = Regex("^[A-Za-z0-9+/]{11}$")

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
        val candidatesByPosition = linkedMapOf<String, ScoredCandidate>()

        collectDomainBoundCandidates(normalizedText, candidatesByPosition)

        val keywordMatches = keywordRegex.findAll(normalizedText).toList()
        if (keywordMatches.isNotEmpty()) {
            collectKeywordWindowCandidates(normalizedText, keywordMatches, candidatesByPosition)
            collectStandaloneLineCandidates(normalizedText, candidatesByPosition)
        }
        collectActionPhraseCandidates(normalizedText, candidatesByPosition)
        collectLabelThenCodeCandidates(normalizedText, candidatesByPosition)

        return candidatesByPosition.values
            .filter { it.score >= 80 }
            .sortedWith(compareByDescending<ScoredCandidate> { it.score }.thenBy { it.start })
            .firstOrNull()
            ?.value
    }

    private fun collectDomainBoundCandidates(
        normalizedText: String,
        candidatesByPosition: MutableMap<String, ScoredCandidate>,
    ) {
        domainBoundCodeRegex.findAll(normalizedText).forEach { match ->
            val raw = match.groupValues[1].trim()
            val value = normalizeCandidate(raw)
            val absoluteStart = match.range.first + match.value.indexOf(raw)
            val absoluteEnd = absoluteStart + raw.length - 1
            if (!isValidCandidate(value)) return@forEach
            if (looksLikeStructuredNonCode(raw, value)) return@forEach
            if (lineLooksLikeSmsRetrieverHash(normalizedText, absoluteStart)) return@forEach
            putBest(
                candidatesByPosition,
                ScoredCandidate(value.uppercase(), 340 + codeShapeBonus(value), absoluteStart),
            )
        }
    }

    private fun collectKeywordWindowCandidates(
        normalizedText: String,
        keywordMatches: List<MatchResult>,
        candidatesByPosition: MutableMap<String, ScoredCandidate>,
    ) {
        for (keyword in keywordMatches) {
            val from = (keyword.range.first - 96).coerceAtLeast(0)
            val to = (keyword.range.last + 160).coerceAtMost(normalizedText.lastIndex)
            if (to < from) continue

            val window = normalizedText.substring(from, to + 1)
            candidateRegex.findAll(window).forEach { match ->
                val raw = match.groupValues[1].trim()
                val value = normalizeCandidate(raw)
                val absoluteStart = from + match.range.first
                val absoluteEnd = from + match.range.last

                scoreCandidateNearKeyword(
                    text = normalizedText,
                    raw = raw,
                    value = value,
                    absoluteStart = absoluteStart,
                    absoluteEnd = absoluteEnd,
                    keyword = keyword,
                )?.let { candidate ->
                    putBest(candidatesByPosition, candidate)
                }
            }
        }
    }

    private fun collectStandaloneLineCandidates(
        normalizedText: String,
        candidatesByPosition: MutableMap<String, ScoredCandidate>,
    ) {
        var offset = 0
        val lines = normalizedText.split('\n')
        for ((index, line) in lines.withIndex()) {
            val lineStart = offset
            val lineEnd = lineStart + line.length
            val matches = candidateRegex.findAll(line).toList()
            for (match in matches) {
                val raw = match.groupValues[1].trim()
                val value = normalizeCandidate(raw)
                val absoluteStart = lineStart + match.range.first
                val absoluteEnd = lineStart + match.range.last
                val residue = line.removeRange(match.range).trim()

                if (!isValidCandidate(value)) continue
                if (!lineResidueAllowsStandaloneCode(residue)) continue
                if (!surroundingLinesHaveAuthenticationContext(lines, index)) continue
                if (looksLikeStructuredNonCode(raw, value)) continue
                if (lineLooksLikeSmsRetrieverHash(normalizedText, absoluteStart)) continue
                if (
                    contextRejectsCandidate(
                        normalizedText,
                        absoluteStart,
                        absoluteEnd,
                        raw,
                        value,
                    )
                ) {
                    continue
                }

                val surrounding = surroundingLines(lines, index, before = 4, after = 5)
                var score = 235 + codeShapeBonus(value)
                if (residue.isBlank()) score += 35
                if (residue.contains("logo", ignoreCase = true) || residue.contains("ロゴ")) score += 20
                if (expiryContext.containsMatchIn(surrounding)) score += 8
                if (strongAuthContext.containsMatchIn(surrounding)) score += 35
                if (nonAuthCodeContext.containsMatchIn(surrounding) && !strongAuthContext.containsMatchIn(surrounding)) {
                    score -= 110
                }

                putBest(
                    candidatesByPosition,
                    ScoredCandidate(value.uppercase(), score, absoluteStart),
                )
            }
            offset = lineEnd + 1
        }
    }

    private fun collectActionPhraseCandidates(
        normalizedText: String,
        candidatesByPosition: MutableMap<String, ScoredCandidate>,
    ) {
        collectRegexGroupCandidates(
            normalizedText = normalizedText,
            regex = actionCodeRegex,
            candidatesByPosition = candidatesByPosition,
            baseScore = 255,
            requireAuthOrEphemeralContext = false,
        )
        collectRegexGroupCandidates(
            normalizedText = normalizedText,
            regex = japaneseActionCodeRegex,
            candidatesByPosition = candidatesByPosition,
            baseScore = 230,
            requireAuthOrEphemeralContext = true,
        )
    }

    private fun collectLabelThenCodeCandidates(
        normalizedText: String,
        candidatesByPosition: MutableMap<String, ScoredCandidate>,
    ) {
        collectRegexGroupCandidates(
            normalizedText = normalizedText,
            regex = labelThenCodeRegex,
            candidatesByPosition = candidatesByPosition,
            baseScore = 250,
            requireAuthOrEphemeralContext = true,
        )
    }

    private fun collectRegexGroupCandidates(
        normalizedText: String,
        regex: Regex,
        candidatesByPosition: MutableMap<String, ScoredCandidate>,
        baseScore: Int,
        requireAuthOrEphemeralContext: Boolean,
    ) {
        regex.findAll(normalizedText).forEach { match ->
            val raw = match.groupValues[1].trim()
            val value = normalizeCandidate(raw)
            val rawIndex = match.value.indexOf(raw)
            if (rawIndex < 0) return@forEach
            val absoluteStart = match.range.first + rawIndex
            val absoluteEnd = absoluteStart + raw.length - 1
            if (!isValidCandidate(value)) return@forEach
            if (looksLikeStructuredNonCode(raw, value)) return@forEach
            if (lineLooksLikeSmsRetrieverHash(normalizedText, absoluteStart)) return@forEach
            if (requireAuthOrEphemeralContext && !hasAuthOrEphemeralContext(normalizedText, absoluteStart, absoluteEnd)) {
                return@forEach
            }
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
            val surrounding = contextAround(normalizedText, absoluteStart, absoluteEnd, 96)
            var score = baseScore + codeShapeBonus(value)
            if (strongAuthContext.containsMatchIn(surrounding)) score += 35
            if (weakEphemeralContext.containsMatchIn(surrounding)) score += 8
            putBest(candidatesByPosition, ScoredCandidate(value.uppercase(), score, absoluteStart))
        }
    }

    private fun putBest(
        candidatesByPosition: MutableMap<String, ScoredCandidate>,
        candidate: ScoredCandidate,
    ) {
        val key = "${candidate.start}:${candidate.value}"
        val previous = candidatesByPosition[key]
        if (previous == null || candidate.score > previous.score) {
            candidatesByPosition[key] = candidate
        }
    }

    private fun scoreCandidateNearKeyword(
        text: String,
        raw: String,
        value: String,
        absoluteStart: Int,
        absoluteEnd: Int,
        keyword: MatchResult,
    ): ScoredCandidate? {
        if (!isValidCandidate(value)) return null
        if (looksLikeStructuredNonCode(raw, value)) return null
        if (lineLooksLikeSmsRetrieverHash(text, absoluteStart)) return null
        if (
            contextRejectsCandidate(
                text,
                absoluteStart,
                absoluteEnd,
                raw,
                value,
            )
        ) {
            return null
        }

        val distance = distanceBetween(
            absoluteStart,
            absoluteEnd,
            keyword.range.first,
            keyword.range.last,
        )
        var score = 170 - distance.coerceAtMost(170)
        if (absoluteStart > keyword.range.last) score += 15
        if (sameLine(text, absoluteStart, keyword.range.first)) score += 10
        if (hasExplicitRelation(text, keyword, absoluteStart, absoluteEnd)) {
            score += 35
        }
        score += codeShapeBonus(value)
        if (expiryNear(text, absoluteStart, absoluteEnd)) score += 8
        if (strongKeyword(keyword.value)) score += 15

        return ScoredCandidate(value.uppercase(), score, absoluteStart)
    }

    private fun normalizeCandidate(raw: String): String =
        raw.replace(Regex("[\\s\\-–—#]"), "")

    private fun isValidCandidate(value: String): Boolean {
        if (!value.all { it in 'A'..'Z' || it in 'a'..'z' || it.isDigit() }) return false
        if (!value.any(Char::isDigit)) return false

        val digitsOnly = value.all(Char::isDigit)
        if (digitsOnly && value.length !in 4..10) return false
        if (!digitsOnly && value.length !in 4..10) return false
        if (!digitsOnly && !value.any(Char::isLetter)) return false

        val blockedWords = setOf(
            "code",
            "codes",
            "otpcode",
            "passcode",
            "security",
            "verify",
            "login",
            "signin",
            "token",
        )
        return value.lowercase() !in blockedWords
    }

    private fun codeShapeBonus(value: String): Int {
        var bonus = 0
        val digitsOnly = value.all(Char::isDigit)
        if (digitsOnly) bonus += 20
        if (value.length == 6) bonus += 40
        if (value.length == 4 || value.length == 8) bonus += 15
        if (value.length in 9..10) bonus -= 10
        if (value.any(Char::isLetter) && value.any(Char::isDigit)) bonus += 15
        return bonus
    }

    private fun lineResidueAllowsStandaloneCode(residue: String): Boolean {
        if (residue.isBlank()) return true
        return standaloneCodeLineResidue.matches(residue)
    }

    private fun surroundingLinesHaveAuthenticationContext(lines: List<String>, index: Int): Boolean {
        val surrounding = surroundingLines(lines, index, before = 4, after = 5)
        if (!keywordRegex.containsMatchIn(surrounding) && !strongAuthContext.containsMatchIn(surrounding)) return false
        if (nonAuthCodeContext.containsMatchIn(surrounding) && !strongAuthContext.containsMatchIn(surrounding)) {
            return false
        }
        return true
    }

    private fun surroundingLines(
        lines: List<String>,
        index: Int,
        before: Int,
        after: Int,
    ): String {
        val from = (index - before).coerceAtLeast(0)
        val to = (index + after).coerceAtMost(lines.lastIndex)
        return lines.subList(from, to + 1).joinToString("\n")
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
        val context = contextAround(text, start, end, 72)
        val closeContext = contextAround(text, start, end, 10)

        if (overlapsStructuredToken(text, start, end, urlOrEmail)) return true
        if (value.all(Char::isDigit) && amountContext.containsMatchIn(closeContext)) return true
        if (value.length >= 7 && phoneContext.containsMatchIn(context)) return true
        if (value.length >= 8 && trackingContext.containsMatchIn(context)) return true
        if (nonAuthCodeContext.containsMatchIn(context) && !strongAuthContext.containsMatchIn(context)) {
            return true
        }

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

    private fun hasAuthOrEphemeralContext(text: String, start: Int, end: Int): Boolean {
        val context = contextAround(text, start, end, 96)
        return keywordRegex.containsMatchIn(context) ||
            strongAuthContext.containsMatchIn(context) ||
            weakEphemeralContext.containsMatchIn(context)
    }

    private fun contextAround(text: String, start: Int, end: Int, radius: Int): String {
        val contextStart = (start - radius).coerceAtLeast(0)
        val contextEnd = (end + radius).coerceAtMost(text.lastIndex)
        return text.substring(contextStart, contextEnd + 1)
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

    private fun lineLooksLikeSmsRetrieverHash(text: String, candidateStart: Int): Boolean {
        val lineStart = text.lastIndexOf('\n', candidateStart).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', candidateStart).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd).trim()
        return smsRetrieverHashLine.matches(line) && !keywordRegex.containsMatchIn(line)
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
        return between.length <= 32 && explicitSeparator.matches(between)
    }

    private fun expiryNear(text: String, start: Int, end: Int): Boolean {
        val from = (start - 32).coerceAtLeast(0)
        val to = (end + 80).coerceAtMost(text.lastIndex)
        return expiryContext.containsMatchIn(text.substring(from, to + 1))
    }

    private fun strongKeyword(value: String): Boolean {
        val normalized = value.lowercase()
        return normalized.contains("otp") ||
            normalized.contains("verification") ||
            normalized.contains("security") ||
            normalized.contains("authentication") ||
            normalized.contains("auth") ||
            normalized.contains("login") ||
            normalized.contains("log-in") ||
            normalized.contains("sign-in") ||
            normalized.contains("signin") ||
            normalized.contains("one-time") ||
            normalized.contains("one time") ||
            normalized.contains("password reset") ||
            value.contains("認証") ||
            value.contains("確認") ||
            value.contains("ワンタイム") ||
            value.contains("本人確認") ||
            value.contains("验证码") ||
            value.contains("驗證碼") ||
            value.contains("인증")
    }
}
