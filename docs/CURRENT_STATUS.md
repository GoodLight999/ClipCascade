# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade`

## Current alpha target

- application: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.19-alpha.2-standalone`
- versionCode: `320124`
- implementation anchor: `ed9c009af0fcfc238cfc6264dd4c7b85a8fe82a3`
- tag: `v3.2.1-extended.19-alpha.2`
- Android CI: `29837847117`, success
- Windows CI: `29837846863`, success
- Android Actions artifact ID: `8498121042`

Hashes and signer are recorded in `docs/LATEST_GREEN_ARTIFACTS.md`.

## Corrected target-device status

- Windows-to-Android receive works while ClipCascade is not open.
- Android outbound Copy works while ClipCascade is open.
- Android outbound Copy fails while ClipCascade is not open.
- A real Gmail standalone six-digit login-code notification was not relayed.

The live connection and inbound path are alive. Android background outbound capture and dispatch remain the broken/unproven boundaries.

## Original upstream clipboard mechanism

Original upstream combined normal `OnPrimaryClipChangedListener` behavior with an ADB-granted `READ_LOGS` logcat monitor and a temporary focusable overlay to read clipboard contents after Android denied background access.

Extended does not restore that mechanism because its canonical requirements prohibit ADB/READ_LOGS/overlay setup.

## Go fork reference

The supplied Go fork has an Android AccessibilityService and native foreground service.

- Accessibility asks the service to perform clipboard synchronization.
- The service owns the sticky Go network engine.
- Its actual background clipboard read still depends on `SYSTEM_ALERT_WINDOW` and a transparent overlay.

Extended borrows only the foreground-service ownership principle, not the overlay.

## Copy detection in `.19`

`SystemCopyCuePolicy` compares Accessibility click candidates only against Android's active-locale framework strings `android.R.string.copy` and `android.R.string.copyUrl`.

Properties:

- no hard-coded Japanese/English list;
- exact normalized match only;
- click/context-click events only;
- selection events only remember text;
- OS clipboard callback remains primary;
- explicit Copy fallback waits 700 ms and cancels if the callback serial changed;
- otherwise it queues only the recent remembered selection;
- negative tests reject approximate labels and arbitrary text.

Settings exposes `Copy detection` separately from `Clipboard capture`.

## Background dispatch in `.19-alpha.2`

The existing Notifee foreground-service runtime now polls and drains native durable queues directly.

- OTP queue is considered before ordinary clipboard queue.
- Native in-flight IDs and the existing 15-second timeout remain authoritative.
- Existing `sendClipBoard` performs normal validation/encryption/fragmentation/transport.
- Extended P2P still waits for peer clipboard-application ACK before native deletion.
- Failed sends leave the item queued for bounded retry.
- Dispatch no longer depends exclusively on `MainApplication.currentReactContext` receiving a `SHARED_TEXT` event.

## Gmail status

A synthetic regression with the reported DAWN structure passes `OtpCodeExtractor`. Therefore the parser supports that structure if complete text reaches it.

The real Gmail failure likely occurred in NotificationListener binding/delivery, filtering, text extras exposure, or OEM lifecycle. That is an inference until stage counters are captured.

## ACK path — preserve exactly

`NATIVE_QUEUE -> NATIVE_IN_FLIGHT -> FOREGROUND_SERVICE_CLAIM -> sendClipBoard -> LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_VALIDATE -> WINDOWS_CLIPBOARD_APPLY -> PEER_ACK -> NATIVE_ACK -> DELETE`

Old/non-Extended peers retain the documented bounded compatibility fallback.

## Not proven

CI is not target proof. The following remain unproven:

- in-place alpha.2 installation and settings retention;
- background Copy cue exposure on HONOR and representative apps;
- native foreground queue drain on target;
- removed-from-recents, locked, and screen-off behavior;
- real Gmail listener delivery/extras;
- target exactly-once behavior;
- battery and tray behavior.

Canonical handoff: `docs/NEXT_CHATGPT_HANDOFF.md`. PR #1 remains open and Draft.
