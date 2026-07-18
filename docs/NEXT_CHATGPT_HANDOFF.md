# Next ChatGPT Handoff

Read these first, in this order:

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. `docs/LATEST_BROAD_OTP_EXTRACTION_HANDOFF.md`
5. `docs/LATEST_OTP_EMAIL_EXTRACTION_HANDOFF.md`
6. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`
7. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
8. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
9. `docs/LATEST_ANDROID_INIT_DUPLICATE_HANDOFF.md`
10. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
11. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`

## Current branch and PR

- Branch: `stability-mobile-otp`
- PR: `#1`
- PR status: Draft; keep it Draft.

## Latest work

Broad OTP extraction was added after the user rejected a Beeper-only fix. The extractor now models WebOTP/domain-bound SMS, SMS Retriever-style messages, SMS User Consent-style code lengths, standalone email code lines, action phrases, and multilingual verification wording.

Key changes:

- `@domain #code` extraction;
- SMS Retriever sample extraction while rejecting the 11-character app hash itself;
- action phrases such as `Enter 552244 to sign in` and `Use code A8K4-P2 to authenticate`;
- standalone code lines and logo-alt-text code lines near authentication text;
- English/Japanese/Chinese/Korean/Spanish/French/German verification samples;
- negative contexts for coupon, discount, order, tracking, status, error, phone, amount, date/year, email-local-part, and app-hash false positives;
- build transform prevents relation words such as `is` from being swallowed into the code and prevents candidates crossing newline into SMS Retriever hash lines.

Android build identity:

- `3.2.1-extended.10-standalone`
- versionCode `320114`

Validated code head before documentation-only commits:

- `26c719655491afb838e082c882bd2253ae2746ac`
- Android CI `29627309385`: success
- Windows CI `29627309390`: success

## Android status

The user reports the latest Android relay build is very stable, but real-device isolated notification-code proof remains pending. Disable Phone Link and every competing clipboard synchronizer before Android outbound tests.

## Validation reminders

Notification-code validation must include:

1. synthetic OTP notification;
2. Beeper-style email notification from the selected mail app;
3. WebOTP/SMS Retriever-style SMS examples where possible;
4. a real SMS code without storing its contents;
5. a real email code without storing its contents;
6. if Beeper/Gmail still fails, record whether the expanded Android notification visibly contains the code and whether a queue item was created.

Windows tray validation must include normal startup, forced signaling failure, at least 10 reconnect/restart cycles, tray Quit, and no ghost icon without restarting Explorer.

Preserve Extended P2P peer-applied ACK and Android persistent queue semantics.
