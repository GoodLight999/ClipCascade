# Current Implementation Status

Branch: `stability-mobile-otp`  
Canonical Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade`

## Current target truth

`.21-alpha.1` is a failed HONOR/MagicOS build despite green CI.

The user confirmed:

- the true notification-listener-path self-test failed;
- Android-to-Windows background clipboard sending failed;
- `.21-alpha.1` did not recover either decisive Priority 1 path.

Do not describe `.21-alpha.1` as fixed, partially fixed, or target-proven.

`.22-alpha.1` is Android/Windows CI-green but has not yet been tested on the target. Do not claim it fixes either path until the HONOR rows pass.

## Current implementation/release candidate

- implementation/release SHA: `29febc3e7a83575564145d470c4143b5b92e42f4`
- versionName: `3.2.1-extended.22-alpha.1-standalone`
- versionCode: `320127`
- intended tag: `v3.2.1-extended.22-alpha.1`
- implementation Android CI: `29917141620`, success
- implementation Windows CI: `29917141540`, success
- first finalized handoff HEAD: `a12621942d2a22b51fb94b9042509ab3845b1c3f`
- handoff Android CI: `29917915931`, success
- handoff Windows CI: `29917915917`, success
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

The known-working Go implementation is a complete event and ownership structure:

1. Accessibility starts and binds a native foreground service.
2. The service returns `START_STICKY` and survives independently of the Activity.
3. The service owns overlay creation and clipboard reading.
4. Accessibility accepts broad candidate events, including selection changes and generic clicks.
5. Weak candidates wait about 1.2 seconds before requesting a native read.
6. The service adds a transparent 1×1 application overlay, reads `ClipboardManager`, and immediately removes it.

`.21` copied only the final overlay/read primitive. The read stayed inside Accessibility and required a semantic or exact framework-localized Copy cue. MagicOS could suppress that cue, so the overlay could compile without ever executing.

Calling `.21` Go-equivalent was incorrect.

## `.22` clipboard architecture

`ClipboardAcquisitionService` is the native acquisition owner.

- native Android foreground service;
- returns `START_STICKY`;
- started and bound by `ClipboardAccessibilityService`;
- owns overlay, `ClipboardManager` reads, fingerprint comparison, and durable queue insertion;
- does not own or replace Extended transport.

Accessibility submits broad weak/strong probes. A selection probe records the old clipboard SHA-256 fingerprint immediately and compares it with the delayed native read. Selection without Copy remains unchanged and must not queue or send.

Reliable mode uses the Go-style overlay. Overlay-disabled mode retains direct `ClipboardManager` control behavior without claiming reliable background access.

## Android 15+ notification constraint and `.22` response

The target runs Android 16. Android 15+ redacts detected OTP content from notifications delivered to an untrusted `NotificationListenerService`.

`.21` had ordinary notification access but no CompanionDeviceManager association. Extractor improvements cannot recover text already redacted by Android.

`.22` adds self-managed CompanionDeviceManager association, notification access through that trust path, a non-exported listener, and a separately installed same-signed helper APK that posts a real external fake-code notification.

Success requires listener delivery, text collection, extraction, durable queue insertion, Windows application, peer ACK, and native deletion. The true listener test performs no direct queue insertion.

## ACK path — preserve exactly

`NATIVE_QUEUE -> NATIVE_IN_FLIGHT -> FOREGROUND_RUNNER_CLAIM -> LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_VALIDATE -> WINDOWS_APPLY -> PEER_ACK -> NATIVE_ACK -> DELETE`

Preserved:

- ordinary queue no TTL;
- no accepted-item eviction;
- capacity 16 and `queue_full`;
- internal-write echo suppression;
- opaque relay IDs and bounded claims;
- generation-scoped old-peer fallback;
- debug notification default OFF and outside ACK logic.

## PR #2 process state

Temporary Draft PR #2 was intended to be closed without merge. Its commits were fast-forwarded into `stability-mobile-otp` before closure, so GitHub automatically marked it `merged=true` when it was closed.

No merge button/API, merge commit, force push, or auto-merge was used. Nevertheless, the required close-before-fast-forward order was violated. This state cannot be undone without forbidden history rewriting and is retained as a process error.

PR #1 was not merged or marked Ready.

## Not proven

CI still does not prove:

- in-place main-APK upgrade/settings retention;
- helper APK installation/launch on HONOR;
- CompanionDeviceManager association on MagicOS;
- unredacted Gmail/DAWN/Perceptron extras;
- external listener-path self-test on target;
- native acquisition service survival after UI closure;
- background clipboard fingerprint change and queue insertion;
- selection-only negative behavior in target apps;
- removed-from-recents, locked, or screen-off outbound;
- exactly-once reconnect, queue-full, long-disconnect, battery, or tray behavior.

Canonical handoff: `docs/NEXT_CHATGPT_HANDOFF.md`.  
Artifact record: `docs/LATEST_GREEN_ARTIFACTS.md`.  
Detailed failure/redesign record: `docs/LATEST_ALPHA21_FAILURE_ALPHA22_NATIVE_RECOVERY_HANDOFF.md`.  
PR #1 must remain Open, Draft, and unmerged.
