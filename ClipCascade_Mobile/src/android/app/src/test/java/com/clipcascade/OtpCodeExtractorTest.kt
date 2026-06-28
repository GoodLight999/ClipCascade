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
    fun extractsJapaneseIdentityVerificationNumber() {
        assertEquals(
            "482901",
            OtpCodeExtractor.extract("本人確認番号：482901。有効時間は5分です。"),
        )
    }

    @Test
    fun extractsJapaneseTwoStepCode() {
        assertEquals(
            "7314",
            OtpCodeExtractor.extract("2段階認証コード 7314 を入力してください"),
        )
    }

    @Test
    fun extractsJapaneseFullWidthOneTimePassword() {
        assertEquals(
            "908172",
            OtpCodeExtractor.extract("ワンタイムパスワードは９０８－１７２です"),
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
    fun extractsEnglishCodeBeforeKeyword() {
        assertEquals(
            "845921",
            OtpCodeExtractor.extract("845921 is your sign-in code. Do not share it."),
        )
    }

    @Test
    fun extractsEnglishEmailVerificationCode() {
        assertEquals(
            "462881",
            OtpCodeExtractor.extract("Use 462881 to verify your email address."),
        )
    }

    @Test
    fun extractsPrefixedCode() {
        assertEquals(
            "G123456",
            OtpCodeExtractor.extract("G-123456 is your verification code"),
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
    fun prefersActualCodeOverExpiryDate() {
        assertEquals(
            "654321",
            OtpCodeExtractor.extract(
                "Your verification code is 654321. Expires on 2026-06-27.",
            ),
        )
    }

    @Test
    fun prefersActualCodeOverExpiryDuration() {
        assertEquals(
            "573920",
            OtpCodeExtractor.extract(
                "認証コードは573920です。10分以内に入力してください。",
            ),
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
    fun rejectsYearNearVerificationWords() {
        assertNull(
            OtpCodeExtractor.extract(
                "The verification service maintenance year is 2026",
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
    fun rejectsEmailAddressLocalPart() {
        assertNull(
            OtpCodeExtractor.extract(
                "Verification notices are sent to user1234@example.com",
            ),
        )
    }

    @Test
    fun rejectsOrdinarySixDigitMessage() {
        assertNull(OtpCodeExtractor.extract("本日の売上は 123456 円でした"))
    }
}
