# `.21-alpha.1` Target Failure and `.22-alpha.1` Native Recovery Handoff

## Target evidence — do not soften

The HONOR 400 Pro / MagicOS target failed both decisive `.21-alpha.1` checks:

- the true notification-listener-path self-test failed;
- background Android-to-Windows clipboard sending failed.

Therefore `.21-alpha.1` is a failed target build. Android and Windows CI success did not establish either target behavior.

## Why the Go reference still did not work in `.21`

The previous implementation copied only the Go overlay geometry and immediate `ClipboardManager` read. It did not reproduce the complete successful event and ownership structure.

### Known-working Go path

1. Accessibility starts and binds `ClipCascadeBackgroundService`.
2. That service is native and returns `START_STICKY`.
3. The service owns the overlay and transport engine independently of the Activity.
4. Accessibility accepts broad candidate events: selection changes, generic clicks, notification-state changes, and announcements.
5. Weak candidates wait about 1.2 seconds and then ask the service to read the clipboard.
6. The service adds a transparent 1×1 focusable overlay, reads `ClipboardManager`, and immediately removes the view.

### `.21` mismatch

- overlay reading lived inside `ClipboardAccessibilityService`, not a bound sticky native owner;
- reading began only after a semantic or exact framework-localized Copy cue;
- MagicOS could suppress that Copy cue, so the overlay could compile without ever running;
- `.21` reproduced the final read primitive but not the event path that reaches it.

Calling `.21` Go-equivalent was incorrect.

## Android 15+ notification root cause

Android 15+ redacts detected OTP content from notifications delivered to an untrusted `NotificationListenerService`. CompanionDeviceManager-associated apps are exempt from that specific restriction.

The target is Android 16. `.21` requested ordinary notification access but did not establish a CompanionDeviceManager association. Extractor improvements cannot recover text Android has already redacted.

The previous listener-path self-test also posted from the same package. That was not a faithful external-notification test.

## `.22-alpha.1` design

### Native clipboard acquisition owner

`ClipboardAcquisitionService`:

- returns `START_STICKY`;
- is started and bound by Accessibility;
- owns the overlay, clipboard read, content fingerprint, and durable queue insertion;
- uses the 1×1 transparent `TYPE_APPLICATION_OVERLAY` flags from the Go reference;
- does not own or replace Extended transport.

### Broad event entry without selection-only sending

Accessibility sends broad probes instead of waiting exclusively for an exact Copy label:

- selection change: weak probe;
- generic click/context click: weak probe;
- semantic or exact localized Copy: strong probe;
- notification/announcement: weak probe;
- Ctrl+C: strong probe.

A selection probe stores the old clipboard fingerprint immediately, then reads again after the Go-reference delay. The native service queues only when the fingerprint actually changes. Selection without Copy produces `unchanged`, not a send.

Only a one-way SHA-256 fingerprint is persisted; clipboard content is not stored in diagnostics.

### ACK boundary retained

The native acquisition service inserts into the existing `ClipboardRelayStore`. It does not send directly and does not acknowledge or delete items.

Required order remains:

`NATIVE_QUEUE -> NATIVE_IN_FLIGHT -> FOREGROUND_RUNNER_CLAIM -> LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_VALIDATE -> WINDOWS_APPLY -> PEER_ACK -> NATIVE_ACK -> DELETE`

Ordinary queue TTL remains absent. Capacity remains 16 with explicit `queue_full`. Accepted items are not evicted.

### Companion notification trust

On Android 15+ setup requires a user-confirmed self-managed CompanionDeviceManager association before notification access is considered ready. After association, ClipCascade requests notification access through `CompanionDeviceManager.requestNotificationAccess`.

This is the platform-supported route for avoiding Android 15 OTP redaction, but it remains target-unproven until HONOR exposes the expected unredacted fields.

### Genuine external listener self-test

The Android artifact includes a second APK:

- package: `com.clipcascade.extended.testnotifier`;
- signed with the same stable public test certificate;
- protected by a signature permission;
- posts a normal external notification containing a generated fake code.

The main app launches this helper. The main app must observe the notification through `NotificationListenerService`, collect text, extract the expected value, queue it, send it, receive peer ACK, and delete it. No direct queue insertion is allowed.

## Validation and artifacts

### Staging validation

Temporary Draft PR #2 was created solely to validate the staging branch.

- initial staging Android CI `29915789910`: success;
- latest staging Android CI `29916941772`: success;
- production transforms, static invariants, JS bundle, Kotlin unit tests, both APKs, and both signer checks succeeded.

### Exact final implementation

- implementation/release SHA: `29febc3e7a83575564145d470c4143b5b92e42f4`;
- Android CI `29917141620`: success;
- Windows CI `29917141540`: success;
- first finalized handoff HEAD `a12621942d2a22b51fb94b9042509ab3845b1c3f`;
- handoff Android CI `29917915931`: success;
- handoff Windows CI `29917915917`: success.

Final artifact:

- artifact ID `8528455362`;
- artifact ZIP SHA-256 `61cba5012ebc412d0075c165b29fb6a5d4ded79f1ad8a28a993218c722717539`;
- main APK SHA-256 `ac6fe987eb3e4a469abcdc53bc552313f752c8780a27498a8abf7aa89c8a681a`;
- main APK size `147968683` bytes;
- helper APK SHA-256 `61e9183d4eb93fedb80e1ea0b624663516f61fc2a2e5752df985aee9066b6e2b`;
- helper APK size `831357` bytes;
- signer SHA-256 for both `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`.

These prove buildability and invariant checks only, not HONOR functionality.

## PR #2 process error

PR #2 was intended to be closed without merge. Its commits were fast-forwarded into `stability-mobile-otp` before closure. GitHub therefore marked PR #2 `merged=true` automatically when it was closed.

No merge button/API, merge commit, force push, or auto-merge was used. The required close-before-fast-forward order was nevertheless violated. The state cannot be undone without forbidden history rewriting and is retained as a process error.

Canonical PR #1 remains the only development PR and must remain Open, Draft, and unmerged.

## Target test order for `.22`

1. Install the main APK over the existing signed Extended build; do not uninstall.
2. Install the external notification test-sender APK.
3. Disable Phone Link and all competing clipboard synchronizers.
4. Allow Display over other apps and enable Accessibility.
5. Complete the trusted companion association shown in settings.
6. Grant notification access through the companion flow.
7. Verify the existing transport runner and native acquisition service are healthy.
8. Run the component/transport test and require Windows apply -> peer ACK -> native deletion.
9. Run the external listener test and require helper notification -> seen -> text -> extracted -> queue -> Windows -> ACK/delete.
10. Leave the main UI without force-stop, select a unique value, explicitly Copy it, and require fingerprint change -> overlay read -> queue -> Windows -> ACK/delete.
11. Select text without Copy and require unchanged fingerprint, no queue, and no Windows change.
12. Only after these pass, test recents removal, screen lock/off, reconnect durability, queue full, and exactly-once behavior.

## Status

`.22-alpha.1` is CI-green but not target-proven. Do not claim either background Copy or real Gmail/DAWN/Perceptron delivery is fixed until target rows pass.
