# Next ChatGPT Handoff

Read these first, in this order:

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. `docs/LATEST_OTP_EMAIL_EXTRACTION_HANDOFF.md`
5. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`
6. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
7. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
8. `docs/LATEST_ANDROID_INIT_DUPLICATE_HANDOFF.md`
9. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
10. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`

## Current branch and PR

- Branch: `stability-mobile-otp`
- PR: `#1`
- PR status: Draft; keep it Draft.

## Latest work

Beeper-style email OTP extraction was added after the user reported that a visible Beeper login code did not relay.

Key changes:

- `OtpCodeExtractor` now recognizes standalone code lines near authentication language;
- logo-alt-text residue such as `Beeper logo 774464` is allowed when login-code context is nearby;
- English authentication context includes `login code`, `sign-in code`, `signin code`, `enter the login code above`, `2FA`, and `MFA`;
- keyword windows and surrounding-line scoring are widened for mail-client title/body/reordered notification extras;
- promo/coupon/discount/postal/error/status/tracking/order-like false positives are rejected unless strong authentication context is present;
- `NotificationCodeListenerService` also folds safe loose string extras into extraction text, without logging notification contents;
- tests cover the Beeper sample layout, notification-order variation, logo-alt-text variation, and a coupon false-positive.

Android build identity for this pass:

- `3.2.1-extended.9-standalone`
- versionCode `320113`

## Previous latest work

Windows tray ghost icon containment was added after the user reported many `ClipCascade` default/ghost tray icons with owner PID `0`. The patch is staged through `ClipCascade_Desktop/src/scripts/prepare_windows_tray_lifecycle.py` and covered by `tests.test_windows_tray_lifecycle`.

## Important caveat

At the time this handoff was written, final GitHub Actions runs for the Beeper OTP pass still needed to be checked. Do not claim the OTP pass green until final Android and Windows CI complete successfully on the latest HEAD.

## Android status

The user reports the latest Android relay build is very stable, but battery use may be high. Android idle-power changes reduced foreground-service polling, UI polling, and Accessibility event wakeups. P2P/WebRTC keepalive remains unchanged because stability is currently good.

## Validation reminders

Disable Phone Link and every competing clipboard synchronizer before Android outbound tests.

Notification-code validation must include:

1. synthetic OTP notification;
2. Beeper-style email notification from the selected mail app;
3. a real SMS code without storing its contents;
4. a real email code without storing its contents;
5. if Beeper still fails, record whether the expanded Android notification visibly contains the code and whether a queue item was created.

Windows tray validation must include:

1. normal startup -> one live tray icon;
2. forced signaling failure -> at least 10 reconnect/restart cycles -> still one tray icon;
3. Quit -> no `ClipCascade` ghost without restarting Explorer;
4. if `scheme http is invalid - goodbye` reappears, preserve adjacent P2P diagnostic lines.

Preserve Extended P2P peer-applied ACK and Android persistent queue semantics.
