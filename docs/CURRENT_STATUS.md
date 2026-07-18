# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade`  
Canonical requirements: `docs/REQUIREMENTS.md`

## Current focus

Current focus is an intermittent Android ordinary-copy regression: copying often works only while the UI is open, but sometimes works briefly in the background. The exact first bad build is unknown. PR #1 remains Draft.

## Current Android build

- app: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.12-standalone`
- versionCode: `320116`
- deterministic signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

Validated code head before documentation commits:

- `7dda7214ed2f9cf35926dd5faedbd583b6d21341`
- Android standalone CI `29629825353`: success
- Desktop Windows CI `29629825352`: success

## Intermittent ordinary-copy repair

`.11` did not modify the ordinary-copy implementation, so do not claim the OTP self-test change directly caused the regression.

Strongest concrete regression candidate:

- `.8` reduced Accessibility source-node inspection to click, context-click, and window-state events;
- window-content, announcement, and notification-state events then used only lightweight event text;
- some OEM/custom floating toolbars expose their Copy marker only through the source node, producing app/event-order-dependent misses.

Current `.12` repair:

- retains lightweight event-text checks;
- restores inspection of the single event source node for every copy-relevant event type;
- adds a three-second fallback using a recent Accessibility selection plus `OnPrimaryClipChangedListener`;
- uses the selected text when Android hides background clipboard data;
- clears the remembered selection after a completed queue/dedup result;
- marks ClipCascade's own text/image clipboard writes with `ClipboardWriteGuard` so inbound/local writes do not echo back;
- preserves the existing persistent queue and ACK semantics.

## Background delivery recovery

A persistent native queue item may outlive the React/Notifee foreground-service generation. This matches the symptom where reopening the app suddenly restores delivery.

Current `.12` repair:

- `RecoveryCoordinator` can bootstrap `ReactInstanceManager` in-process when no active Catalyst instance exists;
- it polls for up to four seconds and emits the existing recovery event when React becomes active;
- Headless JS remains as a compatibility fallback;
- the 60-second Android Service-start cooldown remains, but does not suppress the lighter React bootstrap;
- queue retries therefore no longer have to wait a minute or require opening the UI before attempting React recovery.

Content-free ordinary-copy delivery diagnostics now distinguish:

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

The broad extractor still covers WebOTP/domain-bound SMS, SMS Retriever, standalone email code lines, action phrases, multilingual labels, and false-positive rejection.

Latest reported sample:

- Perceptron Network email;
- `Use this code to login:`;
- standalone six-character alphanumeric code `8F92FE`;
- destination email elsewhere in the message;
- five-minute expiry text after the code.

The complete message is now a unit-test fixture and must extract `8F92FE`.

Notification collection now also includes:

- ticker text;
- known notification text extras;
- message and historic-message Bundles;
- safe nested Bundle CharSequences to depth two;
- public-version ticker/extras.

For an authentication-looking notification that still fails, only `notification_extras / empty` or `notification_extras / no_match` is recorded. Notification text, app/package name, email address, and code are not stored.

Do not claim real Gmail/Beeper/Perceptron extraction until the target device proves that the mail app exposes the code through notification surfaces.

## OTP self-test

The built-in synthetic OTP test is deterministic for:

`extractor -> persistent OTP queue -> dispatcher -> transport -> ACK`

It still posts a real local notification, but it does not depend on Android delivering that self-owned notification back through NotificationListener. It is not a pure test of third-party notification access.

## Android idle power

Retained:

- foreground-service flag polling: 3 seconds;
- visible UI polling: 1 second;
- Accessibility notification timeout: 100 ms;
- 15-minute worker heartbeat bounded wait: up to 4 seconds;
- P2P/WebRTC 20-second keepalive unchanged.

The `.12` repair partially revises the `.8` source-node restriction for reliability, but inspects only the event source node rather than traversing every active window on every event.

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

1. update to `.12 / 320116` without uninstalling;
2. verify Accessibility and synchronization remain enabled;
3. copy unique values with the UI visible and backgrounded;
4. test immediate Copy after selection and Copy after waiting more than three seconds;
5. repeat removed from recents, locked, and screen-off;
6. after a miss, record only ordinary-copy queue count, latest ordinary-copy diagnostic, and latest recovery diagnostic;
7. confirm exactly one peer application and queue deletion only after ACK;
8. rerun synthetic OTP;
9. retry Perceptron/Gmail-style real notification and record `empty`, `no_match`, or `queued` classification;
10. continue battery and Windows tray tests.

Interpretation:

- queue `0` and no recent copy diagnostic: capture missed;
- queue `>0` plus `react_context / rebind_requested`: React generation absent;
- queue `>0` plus `transport_status / retrying`: transport unavailable;
- queue `>0` plus `p2p_peer / retrying`: no peer channel;
- `react_event / emitted` then `peer_ack / acknowledged`: peer-applied path completed.

## Do not claim

Do not describe Android background, locked, or screen-off outbound copy as reliable or exactly-once proven until the isolated target-device matrix passes. Do not describe real third-party OTP extraction, battery efficiency, or Windows tray ghost prevention as target-device proven yet.
