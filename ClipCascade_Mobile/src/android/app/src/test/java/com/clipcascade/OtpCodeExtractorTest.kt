package com.clipcascade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpCodeExtractorTest {
    @Test
    fun extractsJapaneseNumericCode() {
        assertEquals(
            "123456",
            OtpCodeExtractor.extract("ログイン認証コードは 123456 です"),
        )
    }

    @Test
    fun extractsEnglishAlphanumericCode() {
        assertEquals(
            "A1B2C3",
            OtpCodeExtractor.extract("Your verification code is A1B2C3"),
        )
    }

    @Test
    fun normalizesGroupedAndFullWidthCodes() {
        assertEquals(
            "123456",
            OtpCodeExtractor.extract("確認コード: １２３-４５６"),
        )
    }

    @Test
    fun requiresVerificationContext() {
        assertNull(OtpCodeExtractor.extract("荷物番号は 123456 です"))
    }

    @Test
    fun rejectsDateNearVerificationWords() {
        assertNull(
            OtpCodeExtractor.extract(
                "Verification code service maintenance is scheduled for 2026-06-27",
            ),
        )
    }

    @Test
    fun prefersActualCodeOverExpiryDate() {
        assertEquals(
            "654321",
            OtpCodeExtractor.extract(
                "Your verification code is 654321. Expires on 2026-06-27.",
            ),
        )
    }

    @Test
    fun rejectsAmountNearVerificationWords() {
        assertNull(
            OtpCodeExtractor.extract("認証コードキャンペーンの金額は12000円です"),
        )
    }

    @Test
    fun rejectsPhoneNumberNearVerificationWords() {
        assertNull(
            OtpCodeExtractor.extract("認証コードのお問い合わせ電話 090-1234-5678"),
        )
    }

    @Test
    fun rejectsTrackingIdentifierNearVerificationWords() {
        assertNull(
            OtpCodeExtractor.extract(
                "Verification code support: tracking AB12CD34EF",
            ),
        )
    }

    @Test
    fun rejectsOrdinarySixDigitMessage() {
        assertNull(OtpCodeExtractor.extract("本日の売上は 123456 円でした"))
    }
}
