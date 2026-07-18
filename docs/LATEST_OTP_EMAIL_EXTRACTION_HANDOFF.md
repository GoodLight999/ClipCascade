# OTP Email Extraction Handoff — Beeper-style login code

## User report

The user received a Beeper login email where the verification value did not relay:

- code layout: standalone six-digit line (`774464` in the user sample);
- nearby text: `Your login code for Beeper`;
- body text: `Either click the login button, or manually enter the login code above to verify your account.`

The user also reported that the extractor still produces false positives while missing important real codes.

Do not store real verification values in logs or docs. The user-supplied Beeper value is a stale sample used only to describe the layout and unit-test class of inputs.

## External references checked

- WebOTP and SMS Retriever conventions emphasize messages containing a one-time/verification code and an origin/app binding. This reinforces treating authentication context as mandatory rather than accepting arbitrary nearby numbers.
- Apple/Autofill-style behavior and common web forms rely on `one-time-code`/verification-code semantics. This supports adding login/sign-in/security/auth context, but not accepting generic coupon, postal, status, or error codes.

## Root cause hypothesis

`OtpCodeExtractor` already matched many inline forms such as `Your verification code is 123456`, but it lacked a strong structural rule for HTML/email layouts where:

1. the code is on its own line;
2. a logo alt-text line may appear adjacent to the code;
3. the authentication phrase appears on the previous/next title line or in nearby body text;
4. Android notification extras may reorder title, preview text, and expanded body text.

Additionally, `NotificationCodeListenerService` only collected known notification text fields. Some mail clients expose useful text in other `CharSequence` extras, so the collector now safely folds all string-like extras into the candidate text set without logging contents.

## Implemented patch

### Extractor

File: `ClipCascade_Mobile/src/android/app/src/main/java/com/clipcascade/OtpCodeExtractor.kt`

Changes:

- added Beeper-style line-structured extraction for standalone code lines;
- allows logo-alt-text residue such as `Beeper logo 774464` when authentication context is nearby;
- widened authentication context to include `login code`, `sign-in code`, `signin code`, `enter the login code above`, `2FA`, and `MFA`;
- extended keyword windows from 48/96 to 64/128 characters;
- added surrounding-line scoring so email body/title reorder is less fragile;
- added negative context for promo/coupon/discount/postal/error/status/tracking/order-like values unless strong authentication context is present;
- retained local rejections for dates, times, amounts, phone numbers, tracking IDs, URLs, and email addresses.

### Notification text collection

File: `ClipCascade_Mobile/src/android/app/src/main/java/com/clipcascade/NotificationCodeListenerService.kt`

Changes:

- keeps existing explicit `Notification.EXTRA_*` collection;
- additionally scans all `Bundle` keys for `CharSequence`, `Array<CharSequence>`, and `ArrayList<CharSequence>`;
- trims and caps each collected string to 4096 chars;
- does not log notification contents.

### Tests

File: `ClipCascade_Mobile/src/android/app/src/test/java/com/clipcascade/OtpCodeExtractorTest.kt`

New coverage:

- Beeper-style standalone six-digit code line followed by `Your login code for Beeper`;
- notification-order variant where title/preview precede the expanded body;
- logo-alt-text variant such as `Beeper logo 774464`;
- rejection of a coupon/discount code near login-button text.

## Build identity

This pass bumps Android to:

- versionName: `3.2.1-extended.9-standalone`
- versionCode: `320113`
- package: `com.clipcascade.extended`
- signer: unchanged deterministic public test signer

## Validation requirements

1. Run synthetic OTP notification tests.
2. Test a Beeper login email notification from the selected mail app.
3. If it still fails, capture content-free diagnostics: selected app/package, notification title visibility, whether expanded notification visibly contains the code, and whether any queue item was created.
4. If the code is visible in the notification shade but still not extracted, inspect Android notification extras around that app only, but do not persist or log full real email contents.
5. If the code is not exposed through notification extras, the app cannot relay it without a deeper mail-provider integration or screen/OCR route.

## CI status

Final CI must be checked on the latest HEAD after this handoff. Do not call this pass green until both Android and Windows CI complete successfully.

PR #1 remains Draft.
