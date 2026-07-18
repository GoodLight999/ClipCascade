# Broad OTP Extraction Handoff — 2026-07-18

## User report

The user supplied a Beeper email layout where the code did not trigger:

- code line: `774464`
- nearby text: `Your login code for Beeper`
- nearby text: `Either click the login button, or manually enter the login code above to verify your account.`

The user explicitly rejected a Beeper-only fix and asked for broader OTP extraction based on common samples and likely variants, with false positives minimized.

## Sources and format assumptions

The extractor now models common OTP families rather than a single service:

1. WebOTP/origin-bound SMS:
   - `@domain #code` line shape.
2. Android SMS Retriever:
   - app message contains a one-time code plus an 11-character app hash line.
3. SMS User Consent-style messages:
   - 4 to 10 character alphanumeric code containing at least one digit.
4. Email/push notification layouts:
   - code before or after the label;
   - standalone code line near authentication text;
   - logo alt text plus code;
   - action phrases such as `Enter 123456 to sign in` or `Use code A8K4-P2 to authenticate`.

## Implementation

Files changed:

- `ClipCascade_Mobile/src/android/app/src/main/java/com/clipcascade/OtpCodeExtractor.kt`
- `ClipCascade_Mobile/src/android/app/src/test/java/com/clipcascade/OtpCodeExtractorTest.kt`
- `ClipCascade_Mobile/src/scripts/prepare_broad_otp_extractor.js`
- `ClipCascade_Mobile/src/scripts/prepare_extended_version.js`
- `ClipCascade_Mobile/src/scripts/prepare_background_recovery_version.js`
- `.github/workflows/android-mobile-ci.yml`
- `.github/workflows/fork-release.yml`

Build identity:

- versionName: `3.2.1-extended.10-standalone`
- versionCode: `320114`

Extraction expansion:

- English: OTP, one-time password/passcode/code, verification/security/authentication/login/sign-in/2FA/MFA/authorization/approval/recovery/password-reset/device code/token.
- Japanese: 認証, 確認, 検証, 承認, 認可, ログイン, サインイン, ワンタイム, 本人確認, パスコード, パスワード再設定, アカウント復旧.
- Chinese: 验证码, 驗證碼, 登录码, 登入碼, 登錄碼, 一次性密码/密碼, 動態碼/动态码.
- Korean: 인증번호/인증코드, 로그인번호/로그인코드, 보안번호/보안코드.
- European samples: Spanish `código de verificación`, French `code de vérification`, German `Sicherheitscode` / `Anmeldecode`.

Accepted structural forms:

- code near explicit keyword;
- keyword before code: `verification code is 123456`;
- code before keyword: `123456 is your sign-in code`;
- action phrase: `Enter 123456 to sign in`, `Use code A8K4-P2 to authenticate`;
- standalone code line near auth context;
- logo-alt-text code line near auth context;
- WebOTP/domain-bound `@example.com #123456`;
- SMS Retriever sample with an app hash line, while rejecting the app hash itself.

False-positive controls:

- rejects URL/email local-parts;
- rejects dates and times;
- rejects amounts/currency context;
- rejects phone/tel/fax context;
- rejects tracking/shipment/delivery/order/booking/ticket/case/invoice/receipt context;
- rejects promo/coupon/discount/voucher/gift-card/offer/referral/invite contexts unless strong authentication language is present;
- rejects SMS Retriever 11-character app hash when it appears alone;
- rejects code candidates that span a newline so a code line cannot absorb the following app hash.

## Failed attempts recorded

1. First broad implementation failed unit tests because `labelThenCodeRegex` swallowed relation words such as `is`, producing values like `ISA1B2C3`.
2. The initial repair transform failed because JavaScript string escaping did not match Kotlin source backslashes.
3. The next repair compiled but failed the SMS Retriever test because a candidate captured across the newline into the 11-character app hash. The transform now preserves Kotlin regex escapes and rejects raw candidates containing newline or carriage return.

## Validation

Head with the broad extractor repair: `26c719655491afb838e082c882bd2253ae2746ac`.

CI at that head:

- Android standalone CI run `29627309385`: success.
- Desktop Windows CI run `29627309390`: success.

Unit tests include:

- Beeper standalone email code;
- Beeper title/body order variant;
- logo-alt-text plus code;
- WebOTP/domain-bound SMS;
- SMS Retriever message plus app hash;
- angle-prefixed SMS Retriever text;
- English/Japanese/Chinese/Korean/Spanish/French/German samples;
- password reset, temporary security code, device code;
- coupon, discount, order, tracking, error, status, phone, amount, date, year, email-local-part, and hash-alone false-positive rejection.

## Remaining limitation

This only improves extraction when the notification listener actually receives the code text in notification extras. If Gmail or another mail app does not expose the code line in any notification extra, NotificationListenerService cannot extract it. In that case the next diagnostic target is per-app notification extras coverage, not regex extraction.

PR #1 remains Draft.
