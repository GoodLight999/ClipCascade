# ClipCascade Extended 3.2.1-extended.21-alpha.1

This alpha integrates the Android clipboard-acquisition condition used by the known-working Go implementation while preserving Extended's durable queue and Windows-applied peer acknowledgement.

## What changed

- Adds an explicit, user-authorized `SYSTEM_ALERT_WINDOW` clipboard fallback.
- After an explicit Copy cue only, ClipCascade first tries the normal clipboard read.
- When Android denies or hides that read, ClipCascade can briefly add a fully transparent 1×1 non-touchable overlay, read the actual clipboard, and remove the overlay immediately.
- Adds an English/Japanese setup switch, permission status, permission shortcut, and content-free acquisition diagnostics.
- Keeps selection-only events inert. Selecting text without Copy does not queue or send anything.
- Keeps the existing native queue, capacity 16, `queue_full`, retry, deduplication, internal-write suppression, and Extended P2P peer-ACK path unchanged.

## Why this alpha exists

`wuxinkami/ClipCascade_go_fork` is a surviving fork of a Go-based ClipCascade improvement project whose Android background outbound path was known to work. Its successful Android path did not rely on Accessibility selection text alone: Accessibility triggered a sticky foreground service, and that service used a temporary transparent overlay to obtain the real clipboard contents.

`.20-alpha.1` adopted the UI-independent foreground-runner lifecycle principle but did not reproduce that clipboard-acquisition condition. Therefore `.20-alpha.1` had a sound transport-runner repair but insufficient evidence for background clipboard capture.

## Preserved acknowledgement order

`NATIVE_QUEUE -> NATIVE_IN_FLIGHT -> FOREGROUND_RUNNER_CLAIM -> LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_VALIDATE -> WINDOWS_APPLY -> PEER_ACK -> NATIVE_ACK -> DELETE`

The Go mobile transport itself is not imported. In particular, mobile P2P is not disabled and a WebSocket write is not treated as Windows clipboard application.

## Required target validation

CI is not HONOR/MagicOS proof. Validate in this order:

1. Install over the existing Extended build without uninstalling.
2. Enable the overlay fallback and allow Display over other apps.
3. Verify the foreground runner remains active for at least 60 seconds.
4. Run the deterministic component/transport test and confirm Windows apply, peer ACK, and native deletion.
5. Background the main UI without force-stop, explicitly press Copy, and confirm `overlay_clipboard_manager`, queueing, foreground claim, Windows apply, peer ACK, and deletion.
6. Confirm selection without Copy never sends.
7. Only after that, test removal from recents, lock, screen-off, disconnect durability, and queue-full behavior.

No target-device success is claimed by these release notes.
