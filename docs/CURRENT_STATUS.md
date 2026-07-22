# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade`

## Current alpha candidate

- application: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.21-alpha.1-standalone`
- versionCode: `320126`
- implementation/release candidate anchor: `2ede4caf7b59b0cb9d56f06aa976e4d61c63be18`
- intended tag: `v3.2.1-extended.21-alpha.1`
- Android CI: pending exact final-branch validation
- Windows CI: pending exact final-branch validation
- artifact hashes/ID: pending successful Android validation
- signer must remain: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

## Target-device truth

`.19-alpha.2` was tested and failed:

- true NotificationListener-path self-test did not complete;
- outbound failed whenever the main app UI was not open;
- inbound continued to work.

`.20-alpha.1` is not target-proven. It repaired a real foreground-runner lifecycle defect but did not recreate the overlay-based clipboard acquisition used by the known-working Go implementation.

`.21-alpha.1` restores that acquisition condition, but background outbound and real Gmail remain unproven until HONOR/MagicOS tests pass.

## Repository and reference relationship

- `Sathvik-Rao/ClipCascade` is upstream.
- `GoodLight999/ClipCascade` is the Extended repair fork.
- `wuxinkami/ClipCascade_go_fork` is a surviving fork of a vanished Go improvement project. Its Android background outbound path was known to work and is the success reference for lifecycle and clipboard acquisition.

The Go implementation:

- binds Accessibility to a sticky native foreground service;
- lets that service own the gomobile connection;
- uses a fully transparent 1×1 application overlay before reading `ClipboardManager`;
- removes the overlay immediately;
- disables mobile P2P and has no durable outbound queue or Windows-applied peer ACK.

Extended imports the successful overlay acquisition condition but does not import the Go transport.

## Foreground runner retained from `.20`

- Notifee runner registration occurs from `index.js` before `AppRegistry.registerComponent`;
- registration is process-generation scoped;
- heartbeat every 15 seconds;
- stale threshold 45 seconds;
- settings show active/stale;
- native clipboard/OTP queues are drained from the foreground runner;
- listener-path tests still use the real NotificationListener path.

## Clipboard acquisition in `.21`

- exact framework-localized Copy/Copy URL cues and semantic `ACTION_COPY` remain;
- selection alone remains inert;
- direct `ClipboardManager` read is attempted first;
- if the value is unavailable and the user enabled/authorized the reliable fallback, Accessibility briefly creates a transparent 1×1 `TYPE_APPLICATION_OVERLAY` view;
- the view is non-touchable, intentionally focusable, and removed with `removeViewImmediate` in `finally`;
- content-free diagnostics distinguish direct read, overlay success, permission missing, empty read, denial, and add failure;
- overlay-free mode remains available but is not claimed to be Go-equivalent on Android 10+ or HONOR/MagicOS.

## ACK path — preserve exactly

`NATIVE_QUEUE -> NATIVE_IN_FLIGHT -> FOREGROUND_RUNNER_CLAIM -> sendClipBoard -> LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_VALIDATE -> WINDOWS_APPLY -> PEER_ACK -> NATIVE_ACK -> DELETE`

The `.21` acquisition transform does not modify this path.

Also preserve:

- ordinary queue has no TTL;
- no accepted-item eviction;
- capacity 16 and `queue_full`;
- internal-write echo suppression;
- debug notification default OFF and outside ACK logic;
- generation-scoped old-peer compatibility fallback.

## Not proven

CI is not target proof. The following remain unproven:

- in-place `.21` install and settings retention;
- overlay permission flow on HONOR/MagicOS;
- foreground runner heartbeat after UI closure;
- deterministic component/transport test;
- true NotificationListener-path test;
- background explicit Copy producing `overlay_clipboard_manager`;
- selection-only negative behavior on target apps;
- removed-from-recents, locked, and screen-off outbound;
- real Gmail/DAWN/Perceptron notification delivery and extras;
- exactly-once behavior under reconnect;
- queue-full and long-disconnect durability;
- battery and Windows tray behavior.

Canonical handoff: `docs/NEXT_CHATGPT_HANDOFF.md`. Detailed Go analysis: `docs/LATEST_GO_OVERLAY_CLIPBOARD_RECOVERY_HANDOFF.md`. PR #1 remains Open and Draft.
