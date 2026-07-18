# Next ChatGPT Handoff

Read these first, in this order:

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. `docs/LATEST_OTP_SELF_TEST_HANDOFF.md`
5. `docs/LATEST_BROAD_OTP_EXTRACTION_HANDOFF.md`
6. `docs/LATEST_OTP_EMAIL_EXTRACTION_HANDOFF.md`
7. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`
8. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
9. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
10. `docs/LATEST_ANDROID_INIT_DUPLICATE_HANDOFF.md`
11. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
12. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`

## Current branch and PR

- Branch: `stability-mobile-otp`
- PR: `#1`
- PR status: Draft; keep it Draft.

## Latest work

The in-app synthetic OTP test was repaired after the user reported that the app displayed a test verification code but did not copy/relay it.

Confirmed issue:

- the old test only posted a local synthetic notification;
- it relied on Android delivering ClipCascade's own notification back to `NotificationListenerService`;
- if that listener path did not fire, no OTP queue item was created and nothing reached Windows.

Repair:

- `OtpTestNotificationManager.post()` still posts the local notification;
- the generated notification text is immediately passed through `OtpCodeExtractor.extract()`;
- when extraction matches the generated value, a `synthetic-test:` relay ID is inserted into `OtpRelayStore`;
- `OtpRelayDispatcher.schedule(context)` is called directly;
- notification-listener `detected`/`deduplicated` updates cannot downgrade already queued or acknowledged self-test state;
- UI wording now says the built-in test deterministically validates extractor + persistent queue + transport + acknowledgement, but real app notification coverage still needs separate testing.

Android build identity:

- `3.2.1-extended.11-standalone`
- versionCode `320115`

Validated code head before documentation-only commits:

- `885bac31c8d10d4a1f172624e6355248a867c411`
- Android CI `29627885148`: success
- Windows CI `29627885149`: success

## Previous latest work

Broad OTP extraction was added after the user rejected a Beeper-only fix. The extractor now models WebOTP/domain-bound SMS, SMS Retriever-style messages, SMS User Consent-style code lengths, standalone email code lines, action phrases, multilingual verification wording, and false-positive rejection for coupon/order/tracking/status/error/phone/amount/date/email-local-part/app-hash cases.

## Android status

The user reports the latest Android relay build is very stable, but real-device isolated notification-code proof remains pending. Disable Phone Link and every competing clipboard synchronizer before Android outbound tests.

## Validation reminders

Notification-code validation must include:

1. built-in synthetic OTP test, now expected to queue and relay deterministically;
2. Beeper-style email notification from the selected mail app;
3. WebOTP/SMS Retriever-style SMS examples where possible;
4. a real SMS code without storing its contents;
5. a real email code without storing its contents;
6. if Beeper/Gmail still fails, record whether the expanded Android notification visibly contains the code and whether a queue item was created.

Windows tray validation must include normal startup, forced signaling failure, at least 10 reconnect/restart cycles, tray Quit, and no ghost icon without restarting Explorer.

Preserve Extended P2P peer-applied ACK and Android persistent queue semantics.
