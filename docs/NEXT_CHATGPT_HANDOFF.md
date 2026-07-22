# Next ChatGPT Handoff

This is the canonical handoff.

## Hard constraints

- repository: `GoodLight999/ClipCascade`
- final branch: `stability-mobile-otp`
- canonical PR: `#1`, Open and Draft
- never mark PR #1 Ready, merge it, enable auto-merge, or force-push
- preserve Extended P2P Windows-applied ACK before native deletion
- run Android and Windows CI after every final-branch change
- never claim HONOR/MagicOS success from CI
- never commit real clipboard text, notification bodies, codes, accounts, or private URLs
- no ADB, root, Shizuku, or `READ_LOGS`
- the permitted overlay is the explicit user-authorized, transparent 1×1, non-touchable acquisition view described below

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
6. `docs/LATEST_GREEN_ARTIFACTS.md`
7. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
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

`.22-alpha.1` is CI-green but has not yet been tested on HONOR. Do not claim either decisive path is fixed until target rows pass.

## Current implementation/release candidate

- implementation/release SHA: `29febc3e7a83575564145d470c4143b5b92e42f4`
- version: `3.2.1-extended.22-alpha.1-standalone`
- versionCode: `320127`
- intended tag: `v3.2.1-extended.22-alpha.1`
- implementation Android CI: `29917141620`, success
- implementation Windows CI: `29917141540`, success
- first finalized handoff HEAD: `a12621942d2a22b51fb94b9042509ab3845b1c3f`
- handoff Android CI: `29917915931`, success
- handoff Windows CI: `29917915917`, success
- Actions artifact ID: `8528455362`
- Actions ZIP SHA-256: `61cba5012ebc412d0075c165b29fb6a5d4ded79f1ad8a28a993218c722717539`
- main APK SHA-256: `ac6fe987eb3e4a469abcdc53bc552313f752c8780a27498a8abf7aa89c8a681a`
- main APK size: `147968683` bytes
- helper APK SHA-256: `61e9183d4eb93fedb80e1ea0b624663516f61fc2a2e5752df985aee9066b6e2b`
- helper APK size: `831357` bytes
- signer diagnostics artifact ID: `8528452879`
- signer SHA-256 for both: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- artifact expiry: `2026-10-20T11:48:52Z`

The downloaded ZIP digest matched GitHub. Both APK hashes and sizes matched the packaged checksum file. Both signer records matched the stable certificate.

## Why `.21` failed despite using the Go reference

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

`ClipboardAcquisitionService`:

- is a native Android foreground service;
- returns `START_STICKY`;
- is started and bound by `ClipboardAccessibilityService`;
- owns the transparent overlay, `ClipboardManager` reads, fingerprint comparison, and durable queue insertion;
- never sends directly and never acknowledges or deletes queue items.

Accessibility submits probes instead of waiting exclusively for an exact Copy label:

- selection change: weak probe;
- generic click/context-click: weak probe;
- announcement/notification-state change: weak probe;
- semantic `ACTION_COPY` or exact active-locale Copy/Copy URL: strong probe;
- Ctrl+C: strong probe.

A selection probe records the old clipboard SHA-256 fingerprint immediately, then compares it with the delayed native read. Selection without Copy therefore remains unchanged and must not queue or send.

Reliable mode uses the Go-style overlay. Overlay-disabled mode retains a direct `ClipboardManager` read for foreground/control behavior without claiming reliable background access.

The overlay is explicitly authorized, 1×1, alpha 0, transparent, non-touchable, non-touch-modal, intentionally focusable, and removed after every read attempt.

## Android 15+ notification constraint and `.22` response

The target runs Android 16. Android 15+ redacts detected OTP content from notifications delivered to an untrusted `NotificationListenerService`.

`.21` requested ordinary notification access but created no CompanionDeviceManager association. Extractor changes cannot recover text already redacted by Android.

`.22` requires a user-confirmed self-managed CompanionDeviceManager association on Android 15+, then requests notification access through `CompanionDeviceManager.requestNotificationAccess`.

The true listener test now requires a second APK:

- filename: `ClipCascade-Notification-Test-Sender-22-alpha.1.apk`;
- package: `com.clipcascade.extended.testnotifier`;
- same stable test signature as the main APK;
- Activity protected by signature permission;
- posts a normal external notification containing a generated fake code.

The value must pass through NotificationListenerService, extraction, persistent queue, foreground claim, Windows application, peer ACK, and native deletion. No direct queue insertion is allowed.

## ACK boundary — preserve exactly

`NATIVE_QUEUE -> NATIVE_IN_FLIGHT -> FOREGROUND_RUNNER_CLAIM -> LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_VALIDATE -> WINDOWS_APPLY -> PEER_ACK -> NATIVE_ACK -> DELETE`

Also preserve:

- ordinary queue has no TTL;
- no accepted-item eviction;
- capacity 16 and explicit `queue_full`;
- internal-write echo suppression;
- opaque relay IDs and bounded native claims;
- validation before peer ACK;
- generation-scoped old-peer fallback;
- notification receipt guard;
- debug notification default OFF and outside ACK logic.

## PR #2 process error — retain permanently

Temporary Draft PR #2 was intended to be closed without merge after validation. Its commits were instead fast-forwarded into `stability-mobile-otp` before the PR was closed. GitHub therefore classified PR #2 as `merged=true` automatically when it was closed.

No merge button, merge API, merge commit, force push, or auto-merge was used. However, the required close-before-fast-forward order was violated. GitHub's merged classification cannot be undone without forbidden history rewriting. Do not conceal or reinterpret this record.

This process error did not alter PR #1's Draft state and did not change the implementation tree beyond the already-reviewed fast-forward.

## Required target test order

1. Install the `.22` main APK over the existing signed build; do not uninstall.
2. Install the external notification test-sender APK.
3. Disable Phone Link and all competing clipboard synchronizers.
4. Allow Display over other apps and enable ClipCascade Accessibility.
5. Complete the trusted companion association shown in settings.
6. Grant notification access through the companion flow.
7. Confirm the transport runner is active and the native acquisition service has started/bound.
8. Run the deterministic component/transport test and require queue -> claim -> Windows apply -> peer ACK -> native deletion.
9. Run the external listener test and require helper notification -> seen -> eligible -> text -> extraction -> queue -> claim -> Windows -> ACK/delete.
10. Leave the main UI without force-stop, select a unique value, explicitly Copy, and require baseline -> changed fingerprint -> overlay read -> queue -> claim -> Windows -> ACK/delete.
11. Select text without Copy and require unchanged fingerprint, no queue, and no Windows change.
12. Only after simple success test recents removal, lock, screen off, long disconnect, queue full, and exactly-once behavior.

## Failure map

- native service not started/bound: Accessibility/native-service lifecycle failure;
- probe but no delayed observation: debounce/service failure;
- overlay permission missing: setup failure;
- overlay empty/denied: MagicOS clipboard-access failure;
- unchanged fingerprint after unique Copy: event/read timing or acquisition failure;
- changed fingerprint but no queue: dedup/queue-full/storage boundary;
- queued but no claim: transport-runner drain failure;
- Windows apply but queue remains: peer ACK/native deletion failure;
- helper cannot launch: install/package/signature-permission failure;
- external notification unseen: HONOR listener delivery failure;
- seen/eligible but text empty/redacted: companion association/trust failure;
- text available but no extraction: extractor boundary.

## Finalization status

The implementation and first handoff HEAD are green. This correction documents PR #2's actual GitHub state. After it is fast-forwarded to the final branch, run Android and Windows CI once more, update PR #1 body, and verify PR #1 remains Open, Draft, and unmerged.

CI is not HONOR proof.
