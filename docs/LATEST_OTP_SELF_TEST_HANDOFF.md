# OTP Self-Test Dispatch Handoff — 2026-07-18

## User report

The user reported that the in-app synthetic verification-code test did not copy/relay the test code. This exposed a bad validation path: the app posted a local synthetic notification and then depended on Android's NotificationListener delivery of that same app-owned notification before anything entered the OTP relay queue.

That is not robust enough for a built-in end-to-end test. A test button must prove the extractor, queue, transport, and acknowledgement path directly instead of merely posting a notification and hoping the listener observes it.

## Root cause

Before this repair:

1. `OtpTestNotificationManager.post()` generated a random six-digit code, saved status `posted`, and displayed a local notification.
2. `NotificationCodeListenerService.onNotificationPosted()` later had to observe that synthetic notification, extract the code, enqueue it, and schedule the dispatcher.
3. If Android did not deliver the app's own notification back through NotificationListener, or if listener startup/access was flaky, the test stayed posted but no code reached Windows.

## Repair

Changed files:

- `ClipCascade_Mobile/src/android/app/src/main/java/com/clipcascade/OtpTestNotificationManager.kt`
- `ClipCascade_Mobile/src/android/app/src/main/java/com/clipcascade/OtpTestStatusStore.kt`
- `ClipCascade_Mobile/src/android/app/src/main/res/values/strings.xml`
- `ClipCascade_Mobile/src/scripts/prepare_extended_version.js`
- `ClipCascade_Mobile/src/scripts/prepare_background_recovery_version.js`
- `.github/workflows/android-mobile-ci.yml`

New behavior:

1. The self-test still posts a real local notification containing a random synthetic code.
2. Immediately after posting, it runs the same generated text through `OtpCodeExtractor.extract()`.
3. If extraction returns the expected value, it creates a `synthetic-test:` relay ID, enqueues the value in `OtpRelayStore`, and calls `OtpRelayDispatcher.schedule(context)`.
4. If the NotificationListener also observes the same synthetic notification later, it may record detected/deduplicated, but it cannot downgrade an already queued or acknowledged self-test status.
5. The UI copy was updated to state the limitation honestly: the built-in test deterministically exercises extractor + persistent queue + transport + acknowledgement. Real SMS/email/app notification coverage still requires separate NotificationListener validation.

## ACK and queue preservation

Preserved:

- normal `notification_code` relay source;
- `OtpRelayStore` persistent queue and 90-second code dedupe window;
- relay IDs prefixed with `synthetic-test:` for test status acknowledgement;
- Extended P2P peer-applied ACK before native deletion;
- old-peer compatibility fallback;
- `OtpRelayDispatcher.acknowledge()` and `OtpTestStatusStore.acknowledged()` behavior.

## Build identity

- versionName: `3.2.1-extended.11-standalone`
- versionCode: `320115`

## Validation

Code head before follow-up docs: `885bac31c8d10d4a1f172624e6355248a867c411`.

CI at that head:

- Android standalone CI run `29627885148`: success.
- Desktop Windows CI run `29627885149`: success.

Android CI now verifies that:

- transformed app version is `3.2.1-extended.11`;
- transformed Gradle versionCode is `320115`;
- `OtpTestNotificationManager` calls `queueSyntheticValue(applicationContext, text, value)`;
- self-test dispatch schedules `OtpRelayDispatcher.schedule(context)`;
- `OtpTestStatusStore.detected()` cannot downgrade a progressed status;
- `OtpTestStatusStore.queued()` cannot downgrade an acknowledged status.

## Remaining limitation

This repair fixes the app's built-in synthetic test path. It does not prove that Gmail, Beeper, SMS apps, or other third-party notifications expose their OTP text to `NotificationListenerService`. Real notification-code validation still requires target-device tests with Phone Link and every competing sync tool disabled.

PR #1 remains Draft.
