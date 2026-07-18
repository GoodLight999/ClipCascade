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
    fun extractsCodeWhenDestinationEmailIsPresent() {
        assertEquals(
            "462881",
            OtpCodeExtractor.extract(
                "Use 462881 to verify user1234@example.com.",
            ),
        )
    }

    @Test
    fun extractsBeeperStandaloneEmailCode() {
        assertEquals(
            "774464",
            OtpCodeExtractor.extract(
                """
                Beeper logo
                774464

                Your login code for Beeper

                Login to Beeper
                Either click the login button, or manually enter the login code above to verify your account.
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun extractsCodeWhenEmailNotificationTitlePrecedesBody() {
        assertEquals(
            "774464",
            OtpCodeExtractor.extract(
                """
                Your login code for Beeper
                Login to Beeper
                Either click the login button, or manually enter the login code above to verify your account.
                If you didn't request this email, you can ignore it.
                Beeper logo
                774464
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun extractsStandaloneCodeLineWithLogoAltText() {
        assertEquals(
            "774464",
            OtpCodeExtractor.extract(
                """
                Beeper logo 774464
                Your login code for Beeper
                """.trimIndent(),
            ),
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
    fun extractsCodeFromJapaneseTransactionNotification() {
        assertEquals(
            "246810",
            OtpCodeExtractor.extract(
                "決済金額は1,000円です。ワンタイムパスワードは246810です。",
            ),
        )
    }

    @Test
    fun extractsCodeFromEnglishTransactionNotification() {
        assertEquals(
            "884211",
            OtpCodeExtractor.extract(
                "A charge of $10.00 was requested. Your security code is 884211.",
            ),
        )
    }

    @Test
    fun requiresVerificationContext() {
        assertNull(OtpCodeExtractor.extract("荷物番号は 123456 です"))
    }

    @Test
    fun rejectsGenericPostalCode() {
        assertNull(OtpCodeExtractor.extract("Your postal code is 12345"))
    }

    @Test
    fun rejectsGenericPromotionCode() {
        assertNull(OtpCodeExtractor.extract("Promotion code is SAVE20"))
    }

    @Test
    fun rejectsDiscountCodeNearLoginButton() {
        assertNull(
            OtpCodeExtractor.extract(
                "Login to the shop and use this coupon code SAVE20 for a discount.",
            ),
        )
    }

    @Test
    fun rejectsGenericErrorCode() {
        assertNull(OtpCodeExtractor.extract("Error code is E12345"))
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
