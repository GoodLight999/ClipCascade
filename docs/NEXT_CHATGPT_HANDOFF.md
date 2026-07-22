# Next ChatGPT Handoff

This is the canonical handoff.

## Hard constraints

- repository: `GoodLight999/ClipCascade`
- final branch: `stability-mobile-otp`
- canonical PR: `#1`, Open and Draft
- never mark Ready, merge, enable auto-merge, or force-push
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
6. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
7. `docs/LATEST_GREEN_ARTIFACTS.md`
8. `docs/LATEST_GO_OVERLAY_CLIPBOARD_RECOVERY_HANDOFF.md`
9. `docs/LATEST_FOREGROUND_RUNNER_LIFECYCLE_HANDOFF.md`
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
- authentication, encryption, basic transport, and Windows clipboard application were not globally broken.

`.22-alpha.1` is CI-green and artifact-verified but has not yet been tested on HONOR. Do not claim either decisive path is fixed until the target rows pass.

## Current implementation/release candidate

- implementation/release SHA: `29febc3e7a83575564145d470c4143b5b92e42f4`
- version: `3.2.1-extended.22-alpha.1-standalone`
- versionCode: `320127`
- intended tag: `v3.2.1-extended.22-alpha.1`
- Android CI: `29917141620`, success
- Windows CI: `29917141540`, success
- Actions artifact ID: `8528455362`
- Actions ZIP SHA-256: `61cba5012ebc412d0075c165b29fb6a5d4ded79f1ad8a28a993218c722717539`
- main APK SHA-256: `ac6fe987eb3e4a469abcdc53bc552313f752c8780a27498a8abf7aa89c8a681a`
- main APK size: `147968683` bytes
- helper APK SHA-256: `61e9183d4eb93fedb80e1ea0b624663516f61fc2a2e5752df985aee9066b6e2b`
- helper APK size: `831357` bytes
- signer diagnostics artifact ID: `8528452879`
- signer diagnostics ZIP SHA-256: `b60b8d425ef486a87a7905de06b429415f50bb9e874b96ec29e73f840b6bc114`
- signer SHA-256 for both: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- artifact expiry: `2026-10-20T11:48:52Z`

The downloaded ZIP digest matched GitHub. Both APK hashes and sizes matched the packaged checksum file. Signer diagnostics listed the expected certificate twice, once for each APK.

A finalized documentation head `a12621942d2a22b51fb94b9042509ab3845b1c3f` also passed Android `29917915931` and Windows `29917915917`. This factual PR #2 correction changes documentation only; the exact current handoff-head CI is recorded in PR #1's body after it completes.

## Why `.21` failed despite using the Go link

The known-working Go path is a complete event and ownership structure:

1. Accessibility starts and binds a native foreground service.
2. The service returns `START_STICKY`.
3. The service owns overlay creation, clipboard reading, and its Go connection independently of the Activity.
4. Accessibility accepts broad candidate events: selection changes, generic clicks, notification-state changes, and announcements.
5. Weak events wait about 1.2 seconds before the native read; strong copy indications use a shorter delay.
6. The service creates a transparent 1×1 `TYPE_APPLICATION_OVERLAY`, reads `ClipboardManager`, and immediately removes it.

`.21` copied only step 6. The read remained in Accessibility and was reached only after a semantic or exact framework-localized Copy cue. MagicOS could suppress that cue, so the overlay could compile and never execute.

Calling `.21` Go-equivalent was incorrect.

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

Only a one-way fingerprint is persisted. Clipboard content does not enter diagnostics.

Reliable mode uses the Go-style overlay. When reliable mode is disabled, the native owner retains a normal `ClipboardManager` read for foreground/control behavior, without claiming reliable background access.

### Overlay

The native service uses:

- explicit `SYSTEM_ALERT_WINDOW` authorization;
- `TYPE_APPLICATION_OVERLAY`;
- 1×1 size;
- alpha 0 and transparent background;
- `FLAG_NOT_TOUCHABLE | FLAG_NOT_TOUCH_MODAL`;
- no `FLAG_NOT_FOCUSABLE`;
- removal after every read attempt, including failures.

## Android 15+ notification constraint

The target runs Android 16. Android 15+ redacts detected OTP content from notifications delivered to an untrusted `NotificationListenerService`.

`.21` requested ordinary notification access but created no CompanionDeviceManager association. Extractor improvements cannot recover text already redacted by Android.

## `.22` notification trust and true listener test

### Companion association

On Android 15+ settings require a user-confirmed self-managed CompanionDeviceManager association before notification setup is considered complete.

After association, ClipCascade requests notification access through `CompanionDeviceManager.requestNotificationAccess`.

This is the platform-supported trust route, but it remains HONOR-target-unproven.

### External listener test

The true listener test requires the second APK:

- filename: `ClipCascade-Notification-Test-Sender-22-alpha.1.apk`;
- package: `com.clipcascade.extended.testnotifier`;
- same stable public test signature as the main APK;
- Activity protected by signature permission;
- posts a normal external message notification containing a newly generated fake code.

The main app launches the helper. The fake value must pass through Android NotificationListenerService, text collection, extraction, persistent queue, foreground claim, transport, Windows application, peer ACK, and native deletion. No direct queue insertion is allowed.

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

## Temporary PR #2 record

Temporary Draft PR #2 was used only to trigger staging Android CI. The validated tree was integrated through a no-force fast-forward. GitHub reports PR #2 as `closed` and `merged` because its exact head commit became an ancestor of `stability-mobile-otp`; its `merge_commit_sha` is the PR head itself. There was no separate merge commit, merge-button action, Ready conversion, auto-merge, or force push.

Canonical PR #1 remains Open, Draft, and unmerged.

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
10. Leave the main UI without force-stop, select a fresh unique value, explicitly Copy, and require pre-selection baseline -> changed fingerprint -> overlay read -> queue -> claim -> Windows -> ACK/delete.
11. Select text without Copy and require unchanged fingerprint, no queue, and no Windows change.
12. Only after simple success test recents removal, lock, screen off, reboot, long disconnect, queue full, and exactly-once behavior.

## Failure map

- native service not started/bound: Accessibility/native-service lifecycle failure;
- probe recorded but no delayed observation: debounce/service failure;
- `overlay_permission_missing`: setup failure;
- overlay empty/denied: MagicOS did not grant clipboard access;
- unchanged fingerprint after unique explicit Copy: event/read timing or acquisition failure;
- changed fingerprint but no queue: dedup/queue-full/storage boundary;
- queued but no foreground claim: transport-runner drain failure;
- claim but no local acceptance/debug: transport failure;
- Windows apply but queue remains: peer ACK/native deletion failure;
- helper cannot launch: install/package/signature-permission failure;
- external notification unseen: HONOR listener delivery failure;
- seen/eligible but text empty/redacted: companion association/trust failure;
- text available but no extraction: extractor boundary.

## Trial and error

Retain all earlier `.20`/`.21` CI runs and target failures.

`.22` history:

- non-default-branch diagnostic workflow did not report a run and is not counted;
- staging Android `29915789910` succeeded;
- later staging Android `29916941772` succeeded after the final direct-read control fixup;
- exact implementation Android `29917141620` succeeded;
- exact implementation Windows `29917141540` succeeded;
- documentation head `a1262194...` passed Android `29917915931` and Windows `29917915917`;
- final artifact ZIP and both APKs were independently verified;
- no force push, Ready conversion, auto-merge, or separate merge commit occurred.

## Current status

`.22-alpha.1` is exact-SHA CI-green and independently hashed, but not HONOR/MagicOS target-proven. Do not claim either background Copy or Gmail/DAWN/Perceptron delivery is fixed until the corresponding target rows pass.

PR #1 must remain Open and Draft.