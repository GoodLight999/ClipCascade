# System-Localized Copy and Foreground Queue Drain Handoff — 2026-07-21

## Corrected target-device truth

The earlier statement that ordinary synchronization had recovered was too broad and must not be repeated.

The user established:

- Windows-to-Android receive works while ClipCascade is not open;
- Android-to-Windows outbound Copy works while ClipCascade is open;
- Android-to-Windows outbound Copy fails while ClipCascade is not open;
- a real Gmail notification with a normal standalone six-digit login-code layout was not relayed.

This proves the receive/live-connection side is alive. It does not prove Android background outbound capture or dispatch.

## What original upstream Android actually did

Original React Native ClipCascade registered `ClipboardManager.OnPrimaryClipChangedListener`, but modern Android does not reliably expose background clipboard changes or contents to an unfocused app.

Its background workaround used:

1. ADB-granted `android.permission.READ_LOGS`;
2. logcat monitoring for `ClipboardService` denial lines containing ClipCascade's application ID;
3. a temporary focusable `TYPE_APPLICATION_OVERLAY` activity;
4. clipboard read while the temporary window held focus;
5. an event to the React Native transport.

This was never a clean public-API-only background clipboard detector. Extended intentionally removed it because the canonical product goal is ADB-free and the distributed setup must not require READ_LOGS or overlay permission.

## What the Go fork contributes

`wuxinkami/ClipCascade_go_fork` does contain an Android client under `mobile/android`.

Its AccessibilityService:

- starts and binds a native Android foreground service;
- treats selection/click/notification/announcement events as possible Copy cues;
- asks that service to read and synchronize the clipboard.

Its background service:

- owns the live Go synchronization engine;
- is `START_STICKY`;
- reads/sends clipboard contents natively;
- uses a transparent 1x1 `TYPE_APPLICATION_OVERLAY` to obtain clipboard access in the background;
- requires `SYSTEM_ALERT_WINDOW`.

Extended does **not** adopt the transparent-overlay workaround. It adopts only the robust ownership principle: the runtime that owns the live background transport should pull durable native queue items directly instead of waiting for a UI React context.

## `.19-alpha.1`: explicit Copy cue recovery

Implementation anchor: `dfff235dc293d75e28ca56787d1926a9cac33192`.

The alpha added `SystemCopyCuePolicy` and a final transform that:

- obtains Android's own active-locale framework strings via `android.R.string.copy` and `android.R.string.copyUrl`;
- matches only exact NFKC/whitespace/case-normalized labels;
- accepts only Accessibility click/context-click candidates from event/node text or content description;
- keeps selection events as memory only;
- keeps OS clipboard mutation as the strongest primary Copy proof;
- waits 700 ms after an explicit Copy cue;
- cancels the fallback when the OS clipboard callback serial changed;
- otherwise queues only the recent remembered Accessibility selection.

Negative tests reject `Copied`, `Copy all`, `Paste`, blank labels, and arbitrary selected text.

It also added a separate content-free `copy_detection` health category so detection and payload capture can be diagnosed independently.

## `.19-alpha.2`: live foreground transport drains native queues

Current implementation anchor: `ed9c009af0fcfc238cfc6264dd4c7b85a8fe82a3`.

The second alpha adds `prepare_foreground_queue_drain.js` after all Copy/notification transforms.

It adds:

- `ClipboardRelayDispatcher.claimForForegroundService()`;
- `OtpRelayDispatcher.claimForForegroundService()`;
- `RelaySettingsModule.claimPendingForegroundRelay()`;
- `drainNativeRelayQueue()` inside the existing Notifee foreground-service runtime;
- a poll hook in the existing 3-second service flag loop.

Behavior:

- verification values are claimed before ordinary clipboard text because their queue has a short expiry;
- the native queue's existing in-flight ID and 15-second acknowledgement timeout remain authoritative;
- the existing `sendClipBoard` implementation is reused for validation, encryption, fragmentation, P2S/P2P transport, and debug notification;
- Extended P2P stages the existing peer ACK before send;
- failed send cancels the staged ACK and retains the native item;
- successful Extended P2P arms the existing fallback/peer-ACK machinery;
- successful P2S retains the existing local-transport acknowledgement limitation;
- exceptions leave the item queued for bounded retry.

This addresses the second background boundary: native capture may have queued an item, but dispatch previously depended on a React context/event path associated with the app UI. The foreground service now owns both the live transport and queue drain.

## Current alpha metadata

- tag: `v3.2.1-extended.19-alpha.2`
- versionName: `3.2.1-extended.19-alpha.2-standalone`
- versionCode: `320124`
- Android CI: `29837847117`, success
- Windows CI: `29837846863`, success
- Actions artifact ID: `8498121042`
- Actions ZIP SHA-256: `8c20eadce450c57fadb9eab39e465332fe34f8f25f7e28d44a072162dc480db3`
- APK SHA-256: `f9f7b5fe6653beb8d0b08436657ddf719fd155e3ac9b1216b0307b7ea1cf63a7`
- APK size: `147933819` bytes
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- artifact expiry: `2026-10-19T14:10:20Z`

The tag resolves exactly to `ed9c009a...`.

## Gmail / DAWN-shaped result

A repository regression test uses a different synthetic six-digit value in the same structure:

- service name;
- login instruction;
- `Your code is`;
- standalone code line;
- expiration/do-not-share sentence.

That extractor test passes. This proves only that `OtpCodeExtractor` accepts the layout when full text reaches it.

The real Gmail failure therefore likely occurred at one of these earlier boundaries:

- NotificationListener not connected or not receiving the Gmail notification;
- selected-app filtering;
- Gmail/Android exposing no code/body in notification extras;
- OEM process/lifecycle suppression.

This is an inference until content-free counters are captured. Never loosen the parser first without stage evidence, and never commit the user's real code or email body.

## Preserved invariants

- no ADB, root, Shizuku, READ_LOGS, or overlay permission;
- selection alone never sends;
- internal ClipCascade writes remain suppressed;
- accepted ordinary items have no age TTL;
- accepted items are never evicted to admit newer items;
- queue capacity remains 16 with explicit `queue_full` rejection;
- relay IDs and native in-flight claims remain;
- failed sends retain queue data and release through bounded timeout;
- Windows validates and applies text before peer ACK;
- peer ACK precedes native deletion;
- old-peer fallback remains bounded and generation-scoped;
- notification receipt guard and true NotificationListener self-test remain;
- outbound debug notification defaults OFF and cannot alter ACK or deletion;
- Windows implementation did not change in `.19`.

## Mandatory target proof

1. Install `.19-alpha.2` over the existing Extended build without uninstalling.
2. Confirm settings, Accessibility, notification access, and battery configuration survive.
3. Disable Phone Link and every competing clipboard synchronizer.
4. Enable outbound transport-accepted debug notification temporarily.
5. Clear content-free diagnostics.
6. Foreground Copy a unique synthetic value and record:
   - Copy detection;
   - Clipboard capture;
   - pending queue count;
   - debug notice;
   - Windows application count;
   - peer ACK/native deletion.
7. Background the app without force-stop and repeat with a new unique value.
8. Interpret the first failed boundary:
   - no Copy-detection record: Accessibility did not expose the Copy cue;
   - cue/fallback recorded but no capture: selected text/capture failed;
   - capture queued but no `foreground_poll / claimed`: foreground drain did not claim;
   - claimed but no debug notice: send/transport acceptance failed;
   - debug notice but no Windows application: peer/transport failure;
   - Windows applied but native queue remains: peer ACK/deletion failure.
9. Test Chrome and Firefox-family before removed-from-recents, lock, and screen-off rows.
10. For the next real Gmail OTP, clear notification diagnostics immediately before triggering it and record connected/seen/eligible/text/auth/queued/foreground-poll/debug/Windows/ACK stages without recording the code.

## Honest limitation

`.19-alpha.2` combines the best available ADB-free trigger and dispatch architecture, but CI does not prove that HONOR/MagicOS or every app exposes a usable Copy toolbar event to Accessibility. If no explicit Copy event is exposed, public generic Android APIs cannot reproduce upstream's privileged logcat/overlay trigger.

PR #1 remains open and Draft.
