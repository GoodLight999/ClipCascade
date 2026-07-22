# `.21-alpha.1` Target Failure and `.22-alpha.1` Native Recovery Handoff

## Target evidence — do not soften

The HONOR 400 Pro / MagicOS target failed both of the decisive `.21-alpha.1` checks:

- the true notification-listener-path self-test failed;
- background Android-to-Windows clipboard sending failed.

Therefore `.21-alpha.1` is a failed target build. Android and Windows CI success did not establish either target behavior.

## Why the Go reference still did not work in `.21`

The previous implementation copied only the Go overlay geometry and immediate `ClipboardManager` read. It did not reproduce the complete successful event and ownership structure.

### Known-working Go path

1. Accessibility starts and binds `ClipCascadeBackgroundService`.
2. That service is native and returns `START_STICKY`.
3. The service owns the overlay and transport engine independently of the Activity.
4. Accessibility accepts broad candidate events: selection change, any click, notification-state change, and announcement.
5. Weak candidates wait about 1.2 seconds and then ask the service to read the clipboard.
6. The service adds a transparent 1×1 focusable overlay, reads `ClipboardManager`, and immediately removes the view.

### `.21` mismatch

- The overlay read lived inside `ClipboardAccessibilityService`, not a bound sticky native owner.
- The read was entered only after a semantic or exact framework-localized Copy cue.
- MagicOS could suppress that Copy cue, so the overlay code could exist and compile without ever running.
- `.21` therefore reproduced the final read primitive but not the event path that reaches it.

Calling `.21` Go-equivalent was incorrect.

## Android 15+ notification root cause

Android 15 and later redact detected OTP content from notifications delivered to an untrusted `NotificationListenerService`. Apps with a CompanionDeviceManager association are exempt from that specific restriction.

The target is Android 16. `.21` requested ordinary notification access but did not establish a CompanionDeviceManager association. Extractor improvements cannot recover text that Android has already redacted.

The previous listener-path self-test also posted the notification from the same package. That was not a faithful external-notification test and could be filtered or treated specially by the OEM.

## `.22-alpha.1` design

### Native clipboard acquisition owner

`ClipboardAcquisitionService` is a dedicated native foreground service that:

- returns `START_STICKY`;
- is started and bound by Accessibility;
- owns the overlay, clipboard read, content fingerprint, and durable queue insertion;
- uses the same 1×1 transparent `TYPE_APPLICATION_OVERLAY` flags as the Go reference;
- does not own or replace Extended transport.

### Broad event entry without selection-only sending

Accessibility now sends broad probes rather than waiting exclusively for an exact Copy label:

- selection change: weak probe;
- generic click/context click: weak probe;
- semantic or exact localized Copy: strong probe;
- notification/announcement: weak probe;
- Ctrl+C: strong probe.

A selection probe reads and stores the old clipboard fingerprint immediately, then reads again after the Go-reference delay. The native service queues only when the fingerprint actually changes. Selection without Copy therefore produces `unchanged`, not a send.

Only a one-way SHA-256 fingerprint is persisted; clipboard content is not stored in diagnostics.

### ACK boundary retained

The native acquisition service inserts into the existing `ClipboardRelayStore`. It does not send directly and does not acknowledge or delete items.

Required order remains:

`NATIVE_QUEUE -> NATIVE_IN_FLIGHT -> FOREGROUND_RUNNER_CLAIM -> LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_VALIDATE -> WINDOWS_APPLY -> PEER_ACK -> NATIVE_ACK -> DELETE`

Ordinary queue TTL remains absent. Capacity remains 16 with explicit `queue_full`. Accepted items are not evicted.

### Companion notification trust

On Android 15+ the settings flow now requires a user-confirmed self-managed CompanionDeviceManager association before notification access is considered ready. After association, ClipCascade requests notification access through `CompanionDeviceManager.requestNotificationAccess`.

This is the platform-supported route for avoiding Android 15 OTP redaction, but it remains target-unproven until the HONOR device exposes the expected unredacted fields.

### Genuine external listener self-test

The Android artifact now includes a second APK:

- package: `com.clipcascade.extended.testnotifier`;
- signed with the same stable public test certificate;
- protected by a signature permission;
- posts a normal external message notification containing a newly generated fake code.

The main APK launches this helper for the listener-path test. The main app must observe the notification through `NotificationListenerService`, collect text, extract the expected value, queue it, send it, receive peer ACK, and delete it. No direct queue insertion is allowed for this test.

## Pre-final validation

Temporary Draft PR #2 was created solely to validate the staging branch and must never be merged.

- staging SHA: `f8ec5245f9bd2eeaac6400a4f0f57d85ad6d429e`;
- Android CI: `29915789910`, success;
- production transforms: success;
- static native-ownership and ACK assertions: success;
- JavaScript bundle: success;
- Kotlin unit tests: success;
- main APK assembly: success;
- external test-sender APK assembly: success;
- both APK signer checks: success.

Staging artifact:

- artifact ID: `8527934136`;
- artifact ZIP SHA-256: `8632fb589b7e4870eea32a8c70f94362216a5c30b49172eb8c3cc0905a596ce8`;
- staging main APK SHA-256: `bf83d9c127ea714932ec429b7a662760f04e125821eb4fe01dcdc2dd0a6f6dd1`;
- staging main APK size: `147968683` bytes;
- staging helper APK SHA-256: `61e9183d4eb93fedb80e1ea0b624663516f61fc2a2e5752df985aee9066b6e2b`;
- staging helper APK size: `831357` bytes.

These staging artifacts prove buildability only. Final branch artifacts and hashes must be recorded separately after exact-SHA Android and Windows CI.

## Target test order for `.22`

1. Install the main APK over the existing signed Extended build; do not uninstall.
2. Install the external notification test-sender APK.
3. Disable Phone Link and all competing clipboard synchronizers.
4. Allow Display over other apps and enable Accessibility.
5. Complete the trusted companion association shown in settings.
6. Grant notification access through the companion flow.
7. Verify both the existing transport runner and the native clipboard acquisition service are healthy.
8. Run the component/transport test and require Windows apply -> peer ACK -> native deletion.
9. Run the external listener-path test and require helper notification -> seen -> text -> extracted -> queue -> Windows -> ACK/delete.
10. Leave the main UI without force-stop, select a unique value, explicitly Copy it, and require fingerprint change -> overlay read -> queue -> Windows -> ACK/delete.
11. Select text without Copy and require unchanged fingerprint, no queue, and no Windows change.
12. Only after these pass, test recents removal, screen lock, screen off, reconnect durability, queue full, and exactly-once behavior.

## Status

`.22-alpha.1` is CI-buildable but not yet target-proven. Do not claim either background Copy or real Gmail/DAWN/Perceptron delivery is fixed until the target rows pass.

PR #1 must remain Open and Draft. Temporary PR #2 must be closed without merge after final integration validation.
