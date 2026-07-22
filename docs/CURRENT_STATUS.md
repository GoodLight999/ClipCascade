# Current Implementation Status

Branch: `stability-mobile-otp`  
Canonical Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade`

## Current target truth

`.21-alpha.1` is a failed HONOR/MagicOS build despite green CI.

The user confirmed:

- the true notification-listener-path self-test failed;
- Android-to-Windows background clipboard sending failed;
- therefore `.21-alpha.1` did not recover either decisive Priority 1 path.

Do not describe `.21-alpha.1` as fixed, partially fixed, or target-proven.

## Current development candidate

- versionName: `3.2.1-extended.22-alpha.1-standalone`
- versionCode: `320127`
- intended tag: `v3.2.1-extended.22-alpha.1`
- staging branch: `agent/alpha22-diagnosis`
- pre-final staging SHA: `f8ec5245f9bd2eeaac6400a4f0f57d85ad6d429e`
- temporary validation PR: `#2`, Draft, never merge
- staging Android CI: `29915789910`, success
- final `stability-mobile-otp` SHA/CI/artifact: pending integration

PR #1 has not yet received `.22` at the time of this status snapshot.

## Why `.21` failed despite the Go reference

The known-working Go Android implementation is not merely an overlay helper. Its successful path is:

1. Accessibility starts and binds a native foreground service.
2. The service returns `START_STICKY` and survives independently of the Activity.
3. The service owns overlay creation and clipboard reading.
4. Accessibility accepts broad candidate events, including selection changes and generic clicks.
5. Weak candidates wait about 1.2 seconds, then request a native clipboard read.
6. The service adds a transparent 1×1 application overlay, reads `ClipboardManager`, and immediately removes the overlay.

`.21` copied only the final overlay/read primitive. It kept the read inside Accessibility and reached it only after a semantic or exact framework-localized Copy cue. MagicOS could suppress that cue, so the overlay implementation could compile without ever executing.

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

Accessibility now submits broad probes:

- selection change: weak mutation probe;
- generic click/context-click: weak mutation probe;
- announcement/notification-state change: weak mutation probe;
- semantic `ACTION_COPY` or exact system-localized Copy label: strong probe;
- Ctrl+C: strong probe.

A selection probe records the old clipboard SHA-256 fingerprint immediately and compares it with the delayed native read. Selection without Copy therefore produces an unchanged fingerprint and must not queue or send.

Only the fingerprint is persisted. Clipboard content is not stored in diagnostics.

## Android 15+ notification constraint

The target runs Android 16. Android 15+ redacts detected OTP contents from notifications delivered to an untrusted `NotificationListenerService`.

`.21` had ordinary notification access but no CompanionDeviceManager association. Extractor improvements cannot recover text already redacted by Android.

`.22` adds:

- a self-managed CompanionDeviceManager association flow;
- user confirmation and display name;
- association status in English/Japanese settings;
- notification-access request through `CompanionDeviceManager.requestNotificationAccess`;
- a non-exported NotificationListenerService.

This is the platform-supported trust route, but HONOR behavior remains target-unproven.

## Genuine listener-path self-test

The old same-package notification is no longer treated as the true listener-path test.

`.22` includes a second APK:

- package: `com.clipcascade.extended.testnotifier`;
- signed with the same stable public test certificate;
- protected by a signature permission;
- posts a normal external notification containing a newly generated fake code.

The main APK launches that helper. Success requires Android NotificationListenerService delivery, text collection, extraction, durable queue insertion, transport, Windows application, peer ACK, and native deletion. The listener test performs no direct queue insertion.

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

## Pre-final validation evidence

Draft PR #2 Android CI `29915789910` succeeded through:

- all production transforms;
- final generated-source invariants;
- JavaScript bundle;
- Kotlin unit tests;
- main APK compilation and assembly;
- external test-sender APK compilation and assembly;
- matching stable-signer verification for both APKs;
- artifact upload.

Staging artifact:

- artifact ID: `8527934136`;
- artifact ZIP SHA-256: `8632fb589b7e4870eea32a8c70f94362216a5c30b49172eb8c3cc0905a596ce8`;
- staging main APK SHA-256: `bf83d9c127ea714932ec429b7a662760f04e125821eb4fe01dcdc2dd0a6f6dd1`;
- staging main APK size: `147968683` bytes;
- staging helper APK SHA-256: `61e9183d4eb93fedb80e1ea0b624663516f61fc2a2e5752df985aee9066b6e2b`;
- staging helper APK size: `831357` bytes.

These are staging artifacts, not the final PR #1 deliverables.

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
Detailed failure/redesign record: `docs/LATEST_ALPHA21_FAILURE_ALPHA22_NATIVE_RECOVERY_HANDOFF.md`.  
PR #1 must remain Open and Draft. PR #2 must be closed without merge after final integration validation.
