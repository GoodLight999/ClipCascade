# Current Implementation Status

Branch: `stability-mobile-otp`  
Canonical Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade`

## Current target truth

`.21-alpha.1` is a failed HONOR/MagicOS build despite green CI.

The user confirmed:

- the true notification-listener-path self-test failed;
- Android-to-Windows background clipboard sending failed;
- `.21-alpha.1` therefore did not recover either decisive Priority 1 path.

Do not describe `.21-alpha.1` as fixed, partially fixed, or target-proven.

`.22-alpha.1` is Android/Windows CI-green but has not yet been tested on the target. Do not claim it fixes either path until the HONOR rows pass.

## Current implementation/release candidate

- implementation/release SHA: `29febc3e7a83575564145d470c4143b5b92e42f4`
- versionName: `3.2.1-extended.22-alpha.1-standalone`
- versionCode: `320127`
- intended tag: `v3.2.1-extended.22-alpha.1`
- Android CI: `29917141620`, success
- Windows CI: `29917141540`, success
- artifact ID: `8528455362`
- artifact ZIP SHA-256: `61cba5012ebc412d0075c165b29fb6a5d4ded79f1ad8a28a993218c722717539`
- main APK SHA-256: `ac6fe987eb3e4a469abcdc53bc552313f752c8780a27498a8abf7aa89c8a681a`
- main APK size: `147968683` bytes
- helper APK SHA-256: `61e9183d4eb93fedb80e1ea0b624663516f61fc2a2e5752df985aee9066b6e2b`
- helper APK size: `831357` bytes
- signer diagnostics artifact: `8528452879`
- signer SHA-256 for both APKs: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- artifact expiry: `2026-10-20T11:48:52Z`

The downloaded ZIP digest matched GitHub. Both APK hashes/sizes matched the packaged checksum file, and both signer records matched the stable certificate.

## Why `.21` failed despite the Go reference

The known-working Go Android implementation is not merely an overlay helper. Its successful path is:

1. Accessibility starts and binds a native foreground service.
2. The service returns `START_STICKY` and survives independently of the Activity.
3. The service owns overlay creation and clipboard reading.
4. Accessibility accepts broad candidate events, including selection changes and generic clicks.
5. Weak candidates wait about 1.2 seconds before requesting a native read.
6. The service adds a transparent 1×1 application overlay, reads `ClipboardManager`, and immediately removes it.

`.21` copied only the final overlay/read primitive. It kept the read inside Accessibility and reached it only after a semantic or exact framework-localized Copy cue. MagicOS could suppress that cue, so the overlay could compile without ever executing.

Calling `.21` Go-equivalent was incorrect.

## `.22` clipboard architecture

`ClipboardAcquisitionService` is now the native acquisition owner.

- native Android foreground service;
- returns `START_STICKY`;
- started and bound by `ClipboardAccessibilityService`;
- owns the 1×1 transparent overlay;
- owns `ClipboardManager` reads;
- owns clipboard fingerprint comparison;
- inserts only confirmed mutations into the existing `ClipboardRelayStore`;
- does not own or replace Extended transport.

Accessibility submits broad probes:

- selection change: weak mutation probe;
- generic click/context-click: weak mutation probe;
- announcement/notification-state change: weak mutation probe;
- semantic `ACTION_COPY` or exact system-localized Copy label: strong probe;
- Ctrl+C: strong probe.

A selection probe records the old clipboard SHA-256 fingerprint immediately and compares it with the delayed native read. Selection without Copy therefore remains unchanged and must not queue or send.

Only the one-way fingerprint is persisted. Clipboard content is not stored in diagnostics.

Reliable mode uses the Go-style overlay. When reliable mode is disabled, a normal `ClipboardManager` read remains available for foreground/control behavior but is not described as reliable background acquisition.

## Android 15+ notification constraint

The target runs Android 16. Android 15+ redacts detected OTP contents from notifications delivered to an untrusted `NotificationListenerService`.

`.21` had ordinary notification access but no CompanionDeviceManager association. Extractor improvements cannot recover text already redacted by Android.

`.22` adds:

- self-managed CompanionDeviceManager association;
- user confirmation and display name;
- association status in English/Japanese settings;
- notification-access request through `CompanionDeviceManager.requestNotificationAccess`;
- non-exported NotificationListenerService.

This is the platform-supported trust route, but HONOR behavior remains target-unproven.

## Genuine listener-path self-test

The old same-package notification is no longer treated as the true listener-path test.

`.22` includes a second APK:

- package: `com.clipcascade.extended.testnotifier`;
- signed with the same stable public test certificate;
- protected by a signature permission;
- posts a normal external notification containing a newly generated fake code.

The main APK launches the helper. Success requires Android NotificationListenerService delivery, text collection, extraction, durable queue insertion, transport, Windows application, peer ACK, and native deletion. The listener test performs no direct queue insertion.

## ACK path — preserve exactly

`NATIVE_QUEUE -> NATIVE_IN_FLIGHT -> FOREGROUND_RUNNER_CLAIM -> LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_VALIDATE -> WINDOWS_APPLY -> PEER_ACK -> NATIVE_ACK -> DELETE`

`.22` changes acquisition and notification trust only. It does not change this transport order.

Also preserved:

- ordinary clipboard queue has no TTL;
- no accepted-item eviction;
- capacity 16 and explicit `queue_full`;
- internal-write echo suppression;
- opaque relay IDs and bounded claims;
- generation-scoped old-peer compatibility fallback;
- debug notification default OFF and outside ACK logic.

## Validation evidence

Temporary Draft PR #2 established buildability before final integration. Final exact-SHA CI then passed on PR #1's branch.

Final Android CI verified:

- all production transforms;
- native service ownership and bind path;
- broad probes and fingerprint policy unit tests;
- queue and ACK invariants;
- CompanionDeviceManager and external helper sources;
- JavaScript bundle;
- Kotlin unit tests;
- main and helper APK assembly;
- matching signer verification;
- artifact upload.

Windows CI reverified the existing P2P peer-ACK, validation-before-ACK, shutdown/tray, tests, and EXE package.

## Not proven

CI still does not prove:

- in-place `.22` main-APK upgrade and settings retention;
- helper-APK installation and launch on HONOR;
- CompanionDeviceManager association flow on MagicOS;
- unredacted Gmail/DAWN/Perceptron notification extras;
- external listener-path self-test on target;
- native acquisition service survival after UI closure;
- background clipboard fingerprint change and queue insertion;
- selection-only negative behavior in target apps;
- removed-from-recents, locked, or screen-off outbound;
- exactly-once reconnect behavior;
- queue-full and long-disconnect durability;
- battery and Windows tray behavior.

Canonical handoff: `docs/NEXT_CHATGPT_HANDOFF.md`.  
Artifact record: `docs/LATEST_GREEN_ARTIFACTS.md`.  
Detailed failure/redesign record: `docs/LATEST_ALPHA21_FAILURE_ALPHA22_NATIVE_RECOVERY_HANDOFF.md`.  
PR #1 must remain Open and Draft. Temporary PR #2 must be closed without merge after the final handoff is green.
