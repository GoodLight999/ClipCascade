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
    fun extractsJapanesePasswordResetCode() {
        assertEquals(
            "135790",
            OtpCodeExtractor.extract("パスワード再設定コード：135790"),
        )
    }

    @Test
    fun extractsChineseVerificationCode() {
        assertEquals(
            "246810",
            OtpCodeExtractor.extract("您的验证码是 246810，5分钟内有效。"),
        )
    }

    @Test
    fun extractsKoreanVerificationCode() {
        assertEquals(
            "864209",
            OtpCodeExtractor.extract("인증번호는 864209 입니다. 타인에게 공유하지 마세요."),
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
    fun extractsEnterCodeToSignInPattern() {
        assertEquals(
            "552244",
            OtpCodeExtractor.extract("Enter 552244 to sign in to your account."),
        )
    }

    @Test
    fun extractsUseCodeToAuthenticatePattern() {
        assertEquals(
            "A8K4P2",
            OtpCodeExtractor.extract("Use code A8K4-P2 to authenticate this login."),
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
    fun extractsWebOtpDomainBoundSms() {
        assertEquals(
            "123456",
            OtpCodeExtractor.extract(
                """
                Your verification code is 123456.

                @www.example.com #123456
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun extractsDomainBoundLastLineEvenWhenPreviewDropsBody() {
        assertEquals(
            "789012",
            OtpCodeExtractor.extract("@accounts.example.com #789012"),
        )
    }

    @Test
    fun extractsSmsRetrieverMessageAndIgnoresAppHash() {
        assertEquals(
            "123ABC78",
            OtpCodeExtractor.extract(
                """
                Your ExampleApp code is: 123ABC78

                FA+9qCX9VSu
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun extractsAnglePrefixedSmsRetrieverText() {
        assertEquals(
            "493827",
            OtpCodeExtractor.extract("<#> Your ExampleApp verification code is 493827\nFA+9qCX9VSu"),
        )
    }

    @Test
    fun extractsTemporarySecurityCode() {
        assertEquals(
            "314159",
            OtpCodeExtractor.extract("Your temporary security code: 314159. Valid for 5 minutes."),
        )
    }

    @Test
    fun extractsPasswordResetCode() {
        assertEquals(
            "778899",
            OtpCodeExtractor.extract("Your password reset code is 778899. Don't share this code."),
        )
    }

    @Test
    fun extractsDeviceLoginCode() {
        assertEquals(
            "A1B2C3D4",
            OtpCodeExtractor.extract("Device code: A1B2-C3D4. Enter this code to continue."),
        )
    }

    @Test
    fun extractsSpanishVerificationCode() {
        assertEquals(
            "431256",
            OtpCodeExtractor.extract("Su código de verificación es 431256."),
        )
    }

    @Test
    fun extractsFrenchVerificationCode() {
        assertEquals(
            "653210",
            OtpCodeExtractor.extract("Votre code de vérification est 653210."),
        )
    }

    @Test
    fun extractsGermanSecurityCode() {
        assertEquals(
            "908877",
            OtpCodeExtractor.extract("Ihr Sicherheitscode lautet 908877."),
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
    fun rejectsStatusCodeNearAccountText() {
        assertNull(OtpCodeExtractor.extract("Account status code A1B2C3 means the request is pending."))
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
    fun rejectsOrderNumberNearCodeWord() {
        assertNull(
            OtpCodeExtractor.extract("Your order code is AB12CD34EF for delivery tracking."),
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
    fun rejectsSmsRetrieverHashAlone() {
        assertNull(OtpCodeExtractor.extract("FA+9qCX9VSu"))
    }

    @Test
    fun rejectsOrdinarySixDigitMessage() {
        assertNull(OtpCodeExtractor.extract("本日の売上は 123456 円でした"))
    }
}
