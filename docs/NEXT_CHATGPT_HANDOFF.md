# Next ChatGPT Handoff

This is the canonical handoff.

## Hard constraints

- repository: `GoodLight999/ClipCascade`
- final branch: `stability-mobile-otp`
- canonical PR: `#1`, Open and Draft
- never mark Ready, merge, enable auto-merge, or force-push
- temporary PR `#2` is validation-only and must be closed without merge
- preserve Extended P2P Windows-applied ACK before native deletion
- run Android and Windows CI after every final-branch change
- never claim HONOR/MagicOS success from CI
- never commit real clipboard text, notification bodies, codes, accounts, or private URLs
- no ADB, root, Shizuku, or `READ_LOGS`
- the permitted overlay is the explicit user-authorized, transparent 1×1, non-touchable clipboard-acquisition view described below

## Repository roles

1. `Sathvik-Rao/ClipCascade` is upstream and the formal primary source.
2. `GoodLight999/ClipCascade` is the Extended repair repository.
3. `wuxinkami/ClipCascade_go_fork` is the surviving fork of a vanished Go improvement project. Its Android background outbound implementation was known to work and is the successful reference implementation, not a speculative example.

Go reference revision inspected: `0ff3ba4b28daccc1a51e7c09907792bc0f8e53a8`.

## Read first

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. `docs/NEXT_CHATGPT_HANDOFF.md`
5. `docs/LATEST_ALPHA21_FAILURE_ALPHA22_NATIVE_RECOVERY_HANDOFF.md`
6. `docs/LATEST_GO_OVERLAY_CLIPBOARD_RECOVERY_HANDOFF.md`
7. `docs/LATEST_FOREGROUND_RUNNER_LIFECYCLE_HANDOFF.md`
8. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
9. `docs/LATEST_GREEN_ARTIFACTS.md`
10. `docs/LATEST_NOTIFICATION_LISTENER_ALPHA_HANDOFF.md`
11. `docs/LATEST_GMAIL_NOTIFICATION_RELIABILITY_HANDOFF.md`

## Target evidence — do not soften

`.21-alpha.1` failed both decisive HONOR tests:

- the true notification-listener-path self-test failed;
- Android-to-Windows background clipboard sending failed.

`.21-alpha.1` is a failed target build even though Android and Windows CI were green.

Earlier facts remain:

- `.19-alpha.2` also failed listener-path and UI-closed outbound;
- Windows-to-Android receive continued to work without the main UI;
- Android-to-Windows worked when the main UI was open on a prior build;
- authentication, encryption, basic transport, and Windows clipboard application were therefore not globally broken.

## Why `.21` failed despite using the Go link

The known-working Go path is a complete event and ownership structure:

1. Accessibility starts and binds a native foreground service.
2. The service returns `START_STICKY`.
3. The service owns overlay creation, clipboard reading, and its Go connection independently of the Activity.
4. Accessibility accepts broad candidate events: selection change, generic clicks, notification-state changes, and announcements.
5. Weak events wait about 1.2 seconds before requesting the native read; strong copy indications wait about 300 ms.
6. The service creates a transparent 1×1 `TYPE_APPLICATION_OVERLAY`, reads `ClipboardManager`, and immediately removes the view.

`.21` copied only step 6. The read still lived in Accessibility and was reached only after a semantic or exact framework-localized Copy cue. MagicOS could suppress that cue, so the overlay could compile and never execute.

Calling `.21` Go-equivalent was incorrect.

## Android 15+ notification constraint

The target runs Android 16. Android 15+ redacts detected OTP content from notifications delivered to an untrusted `NotificationListenerService`.

`.21` requested ordinary notification access but created no CompanionDeviceManager association. Extractor improvements cannot recover text already redacted by Android.

The old same-package notification also was not a faithful external listener-path test.

## Current `.22-alpha.1` candidate

- version: `3.2.1-extended.22-alpha.1-standalone`
- versionCode: `320127`
- intended tag: `v3.2.1-extended.22-alpha.1`
- staging branch: `agent/alpha22-diagnosis`
- validated staging SHA: `f8ec5245f9bd2eeaac6400a4f0f57d85ad6d429e`
- temporary Draft PR: `#2`, do not merge
- pre-final Android CI: `29915789910`, success
- final PR #1 SHA/CI/artifacts: pending exact final-branch integration

## `.22` clipboard architecture

### Native owner

`ClipboardAcquisitionService`:

- is a native Android foreground service;
- returns `START_STICKY`;
- is started and bound by `ClipboardAccessibilityService`;
- owns the transparent overlay;
- owns `ClipboardManager` reads;
- owns clipboard fingerprint comparison;
- inserts only proven clipboard mutations into the existing durable `ClipboardRelayStore`;
- never sends directly and never acknowledges or deletes queue items.

### Broad event entry

Accessibility submits probes instead of waiting exclusively for an exact Copy label:

- selection change: weak probe;
- generic click/context-click: weak probe;
- announcement/notification-state change: weak probe;
- semantic `ACTION_COPY` or exact active-locale Copy/Copy URL: strong probe;
- Ctrl+C: strong probe.

A selection probe records the old clipboard SHA-256 fingerprint immediately, then compares it with the delayed native overlay read. Selection without Copy therefore remains unchanged and must not queue or send.

Only a one-way fingerprint is persisted. Clipboard contents do not enter diagnostics.

### Overlay

The native service uses:

- explicit `SYSTEM_ALERT_WINDOW` authorization;
- `TYPE_APPLICATION_OVERLAY`;
- 1×1 size;
- alpha 0 and transparent background;
- `FLAG_NOT_TOUCHABLE | FLAG_NOT_TOUCH_MODAL`;
- no `FLAG_NOT_FOCUSABLE`;
- immediate removal after the read, including failure paths.

## `.22` notification trust and self-test

### Companion association

On Android 15+ settings require a user-confirmed self-managed CompanionDeviceManager association before notification setup is considered complete.

After association, ClipCascade requests notification access through `CompanionDeviceManager.requestNotificationAccess`.

This is the platform-supported route for the Android 15 OTP-redaction exception, but it remains HONOR-target-unproven.

### External listener test

The true listener test now requires a second APK:

- package: `com.clipcascade.extended.testnotifier`;
- same stable public test signature as the main APK;
- Activity protected by signature permission;
- posts a normal external message notification containing a newly generated fake code.

The main app launches the helper. The fake value must pass through Android NotificationListenerService, extraction, persistent queue, foreground claim, transport, Windows application, peer ACK, and native deletion. No direct queue insertion is allowed.

The deterministic component/transport test remains separate and is explicitly not NotificationListener proof.

## ACK boundary — preserve exactly

`NATIVE_QUEUE -> NATIVE_IN_FLIGHT -> FOREGROUND_RUNNER_CLAIM -> LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_VALIDATE -> WINDOWS_APPLY -> PEER_ACK -> NATIVE_ACK -> DELETE`

Also preserve:

- ordinary queue has no TTL;
- no accepted-item eviction;
- capacity 16 and explicit `queue_full`;
- internal-write echo suppression;
- opaque relay IDs and bounded native claims;
- validation before peer ACK;
- generation-scoped old-peer compatibility fallback;
- notification receipt guard;
- debug notification default OFF and outside ACK logic.

## Pre-final validation obtained

Draft PR #2 staging SHA `f8ec5245...` passed Android CI `29915789910` through:

- all production transforms;
- final generated-source ownership and ACK assertions;
- JavaScript bundle;
- Kotlin unit tests;
- main APK compilation/assembly;
- external test-sender APK compilation/assembly;
- matching signer verification for both APKs;
- artifact upload.

Staging artifact metadata:

- artifact ID: `8527934136`;
- ZIP SHA-256: `8632fb589b7e4870eea32a8c70f94362216a5c30b49172eb8c3cc0905a596ce8`;
- main APK SHA-256: `bf83d9c127ea714932ec429b7a662760f04e125821eb4fe01dcdc2dd0a6f6dd1`;
- main size: `147968683` bytes;
- helper APK SHA-256: `61e9183d4eb93fedb80e1ea0b624663516f61fc2a2e5752df985aee9066b6e2b`;
- helper size: `831357` bytes;
- signer SHA-256 for both: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`.

These are staging artifacts only. Do not distribute them as final PR #1 artifacts.

## Required final integration procedure

1. Finish requirements, status, test matrix, release, and handoff documents on `agent/alpha22-diagnosis`.
2. Verify PR #1 remains Open and Draft.
3. Fast-forward `stability-mobile-otp` without force.
4. Create a normal contents commit if a ref-only update does not trigger Actions.
5. Require exact-SHA Android and Windows CI success.
6. Download the final Android artifact and independently record both APK hashes, sizes, signatures, artifact ID, and expiry.
7. Update final artifact/handoff documents.
8. Require Android and Windows CI on that final handoff HEAD as well.
9. Close PR #2 without merge.
10. Update PR #1 body and verify it remains Open, Draft, and unmerged.

## Required target test order

1. Install the `.22` main APK over the existing signed build; do not uninstall.
2. Install the external notification test-sender APK.
3. Disable Phone Link and all competing clipboard synchronizers.
4. Allow Display over other apps and enable ClipCascade Accessibility.
5. Complete the trusted companion association shown in settings.
6. Grant notification access through the companion flow.
7. Confirm the existing transport runner is active and the native clipboard acquisition service has started/bound.
8. Run the deterministic component/transport test and require queue -> claim -> one Windows apply -> peer ACK -> native deletion.
9. Run the external listener-path test and require helper notification -> seen -> eligible -> text -> extraction -> queue -> claim -> Windows -> ACK/delete.
10. Leave the main UI without force-stop, select a unique value, explicitly Copy, and require pre-selection baseline -> changed fingerprint -> overlay read -> queue -> claim -> Windows -> ACK/delete.
11. Select text without Copy and require unchanged fingerprint, no queue, and no Windows change.
12. Only after simple success test recents removal, lock, screen off, long disconnect, queue full, and exactly-once behavior.

## Failure map

- native acquisition service not started/bound: Accessibility/native-service lifecycle failure;
- probe recorded but no delayed observation: native debounce/service failure;
- `overlay_permission_missing`: setup failure;
- overlay empty/denied: MagicOS did not grant clipboard access;
- unchanged fingerprint after explicit Copy: app/OEM copied the same old value or event/read timing failed;
- changed fingerprint but no queue: dedup/queue-full/storage boundary;
- queued but no foreground claim: existing transport runner drain failure;
- claim but no debug/local acceptance: transport failure;
- Windows apply but queue remains: peer ACK/native deletion failure;
- helper APK cannot launch: install/signature permission failure;
- external notification unseen: HONOR notification-listener delivery failure;
- seen/eligible but text empty or redacted: companion association/trust failure;
- text available but no extraction: extractor boundary.

## Trial and error

Retain all earlier `.20`/`.21` CI runs and their target failures.

Current `.22` history:

- a non-default-branch diagnostic workflow did not report a run and is not counted;
- temporary Draft PR #2 was created to trigger a real pull-request CI without touching PR #1;
- Android `29915789910` passed full staging validation;
- no force push, merge, Ready conversion, or auto-merge occurred.

PR #1 must remain Open and Draft. CI is not HONOR proof.
