# ClipCascade Extended 3.2.1-extended.22-alpha.1

This alpha replaces the failed `.21-alpha.1` background-acquisition architecture.

## What changed

- Accessibility now starts and binds a dedicated sticky native foreground service.
- The native service owns the transparent 1×1 overlay, `ClipboardManager` read, clipboard-mutation proof, and durable queue insertion.
- Selection and generic click events are treated as probes, matching the known-working Go implementation's broad event entry rather than requiring MagicOS to expose an exact Copy label.
- A selection immediately records the old clipboard fingerprint; only a later fingerprint change may enter the queue. Selecting text without copying therefore remains inert.
- The existing Extended P2P path remains unchanged: Windows validation and clipboard application precede peer ACK, and Android deletes the native item only after that ACK.
- Android 15+ notification setup now creates a user-confirmed CompanionDeviceManager association before requesting notification access, because ordinary untrusted listeners receive OTP-redacted notification content.
- The listener-path self-test now uses a separately installed, same-signed helper APK. The helper posts an external notification; the main app must receive it through Android NotificationListenerService before extraction and relay.

## Included APKs

1. `ClipCascade-Extended-3.2.1-extended.22-alpha.1.apk`
2. `ClipCascade-Notification-Test-Sender-22-alpha.1.apk`

Install the main APK over the existing signed Extended build. Install the test-sender APK separately before running the external listener-path self-test.

## Required target setup

- Allow Display over other apps for the reliable background clipboard acquisition path.
- Enable the ClipCascade Accessibility service.
- Complete the trusted companion association shown in Extended settings on Android 15 or later.
- Allow notification access.
- Disable Phone Link and every other clipboard synchronizer during validation.

## Not yet proven

CI proves source invariants, unit tests, Kotlin compilation, APK assembly, embedded JavaScript, and matching signatures. It does not prove HONOR/MagicOS background Copy acquisition, companion exemption behavior, Gmail/DAWN notification visibility, or exactly-once delivery on the target device.
