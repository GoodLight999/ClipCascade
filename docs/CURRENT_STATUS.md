# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade`  
Canonical requirements: `docs/REQUIREMENTS.md`

## Current focus

Current focus is proving that Android relays an actual Copy action but not text selection or floating-toolbar appearance. Background delivery intermittency and real notification-code extraction also remain under target-device validation. PR #1 remains Draft.

## Current Android build

- app: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.13-standalone`
- versionCode: `320117`
- deterministic signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

Validated code head before documentation commits:

- `594c37224bdcfbd96c54b41bd0509cf97f22ef5b`
- Android standalone CI `29632041698`: success
- Desktop Windows CI `29632041722`: success

Final CI for the documentation HEAD must be checked before distribution.

## Selection-only false-positive repair

Latest user report: an older build can relay a selected value even when Copy was never pressed.

Audit result:

- selection events themselves only remember the selected range;
- selecting text opens the floating toolbar;
- toolbar appearance can emit passive window-content/window-state events;
- the `.12` generic matcher could see the toolbar's `Copy` / `コピー` command and treat its appearance as copy completion.

Current `.13` repair:

- `AccessibilityNodeInfo.ACTION_COPY` remains authoritative;
- direct `TYPE_VIEW_CLICKED` and `TYPE_VIEW_CONTEXT_CLICKED` events may accept a generic `Copy` / `コピー` command;
- passive announcement, notification-state, window-state, and window-content events require completion wording such as `Copied`, `Copied to clipboard`, `コピーしました`, `クリップボードにコピーしました`, or `コピー済み`;
- a passive event containing only `Copy` / `コピー` is rejected;
- the existing three-second selected-text plus real clipboard-change fallback remains;
- ClipCascade's own clipboard writes remain excluded through `ClipboardWriteGuard`.

Unit tests reject selection-toolbar labels and ordinary explanatory text while accepting direct copy controls and completion messages.

## Intermittent ordinary-copy repair

`.11` did not modify the ordinary-copy implementation, so do not claim the OTP self-test change directly caused the regression.

Strongest concrete regression candidate:

- `.8` reduced Accessibility source-node inspection to click, context-click, and window-state events;
- window-content, announcement, and notification-state events then used only lightweight event text;
- some OEM/custom interfaces expose the useful copy signal only through the source node, producing app/event-order-dependent misses.

Retained from `.12`:

- lightweight event-text fast path;
- source-node inspection where needed;
- three-second selected-text plus `OnPrimaryClipChangedListener` fallback;
- selected text used when Android hides background clipboard data;
- remembered selection consumed after completed queue/deduplication;
- internal clipboard-write echo suppression;
- persistent queue and ACK semantics.

The `.13` classifier narrows passive source-node evidence so reliability restoration does not reintroduce selection-toolbar false positives.

## Background delivery recovery

A persistent native queue item may outlive the React/Notifee foreground-service generation. This matches the symptom where reopening the app suddenly restores delivery.

Retained repair:

- `RecoveryCoordinator` can bootstrap `ReactInstanceManager` in-process when no active Catalyst instance exists;
- it polls for up to four seconds and emits the existing recovery event when React becomes active;
- Headless JS remains as a compatibility fallback;
- the 60-second Android Service-start cooldown does not suppress the lighter React bootstrap.

Content-free diagnostics distinguish:

- `transport_enabled / sync_disabled`;
- `transport_status / retrying`;
- `p2p_peer / retrying`;
- `react_context / rebind_requested`;
- `react_event / emitted`;
- `peer_ack / retrying`;
- `peer_ack / acknowledged`;
- `dispatcher / interrupted`.

No clipboard text or source-app name is stored.

## Notification-code extraction

The broad extractor covers WebOTP/domain-bound SMS, SMS Retriever, standalone email code lines, action phrases, multilingual labels, and false-positive rejection.

Latest reported sample:

- Perceptron Network email;
- `Use this code to login:`;
- standalone six-character alphanumeric code `8F92FE`;
- destination email elsewhere in the message;
- five-minute expiry text after the code.

The complete message is a unit-test fixture and extracts `8F92FE`.

Notification collection also includes ticker text, known text extras, message/historic-message Bundles, safe nested Bundle CharSequences to depth two, and public-version ticker/extras.

For an authentication-looking notification that still fails, only `notification_extras / empty` or `notification_extras / no_match` is recorded. Notification text, app/package name, email address, and code are not stored.

Do not claim real Gmail/Beeper/Perceptron extraction until the target device proves that the mail app exposes the code through notification surfaces.

## OTP self-test

The built-in synthetic OTP test is deterministic for:

`extractor -> persistent OTP queue -> dispatcher -> transport -> ACK`

It still posts a real local notification, but does not depend on Android delivering that self-owned notification back through NotificationListener. It is not a pure test of third-party notification access.

## Android idle power

Retained:

- foreground-service flag polling: 3 seconds;
- visible UI polling: 1 second;
- Accessibility notification timeout: 100 ms;
- 15-minute worker heartbeat bounded wait: up to 4 seconds;
- P2P/WebRTC 20-second keepalive unchanged.

Reliability code inspects only the event source node, not every active window on every event.

## Windows tray repair

The branch retains centralized idempotent pystray disposal, `visible = False -> stop()`, single panel ownership, P2P URL/scheme diagnostics, fatal scheme classification, and bounded watchdog full restarts. Real Explorer ghost-icon validation remains pending.

## Delivery acknowledgement — preserve

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

P2S:

`LOCAL_TRANSPORT_ACCEPTED -> NATIVE_ACK -> DELETE`

Extended P2P:

`LOCAL_TRANSPORT_ACCEPTED -> PEER_RECEIVED -> PEER_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

Old/non-Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

Preserve `relayId`, `ackRequested`, validation-before-ACK, generation-scoped timers, relay claims, and native acknowledgement-based deletion.

## Mandatory next proof

With Microsoft Phone Link and every competing synchronizer disabled:

1. update to `.13 / 320117` without uninstalling;
2. verify Accessibility and synchronization remain enabled;
3. select a unique value and leave the floating toolbar visible without pressing Copy;
4. confirm Windows receives nothing and the native queue does not increase;
5. dismiss the selection and confirm no delayed relay;
6. repeat in Chrome, Firefox-family browser, Gmail, notes/editor, and a WebView app;
7. then actually press Copy and confirm exactly one peer application and queue deletion only after ACK;
8. repeat visible, backgrounded, removed from recents, locked, and screen-off;
9. after a miss, record only queue count, latest ordinary-copy diagnostic, and latest recovery diagnostic;
10. rerun synthetic OTP and a controlled real notification sample;
11. continue battery and Windows tray tests.

Interpretation:

- queue `0` and no recent copy diagnostic: capture missed;
- queue `>0` plus `react_context / rebind_requested`: React generation absent;
- queue `>0` plus `transport_status / retrying`: transport unavailable;
- queue `>0` plus `p2p_peer / retrying`: no peer channel;
- `react_event / emitted` then `peer_ack / acknowledged`: peer-applied path completed.

## Do not claim

Do not describe selection-only suppression, Android background/locked/screen-off outbound copy, exactly-once behavior, real third-party OTP extraction, battery efficiency, or Windows tray ghost prevention as target-device proven until the isolated device tests pass.
