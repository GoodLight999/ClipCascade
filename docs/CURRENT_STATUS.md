# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade` (public)  
Canonical requirements: `docs/REQUIREMENTS.md`

## Critical evidence correction

Earlier Android outbound successes were misattributed to ClipCascade because Microsoft Phone Link clipboard synchronization was active.

Valid user evidence:

- Windows authentication, GUI, and synchronization work;
- peer -> Android reception works while ClipCascade is backgrounded and apparently while the screen is off;
- Android -> peer background sending was not working when isolated from Phone Link;
- the former ordinary-copy and Yahoo! JAPAN SMS success claims are invalid.

No isolated Android foreground/background/locked/screen-off outbound success is formally proven yet. Phone Link and every competing clipboard synchronizer must be disabled for validation. PR #1 remains Draft.

## Current Android build

Prepared identity:

- app: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.8-standalone`
- versionCode: `320112`
- deterministic test signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

It is intended to update the earlier stable-signed builds without uninstalling.

## Latest idle-power repair

The user reports that the current relay behavior is very stable, but battery consumption may be excessive.

Confirmed avoidable work:

- the foreground JavaScript service synchronously read four AsyncStorage/SQLite flags once per second for its entire lifetime;
- the visible UI polled status flags every 300 ms;
- Accessibility accepted high-volume window-content events with a 25 ms notification timeout and fetched a source node for every possible copy cue.

Current repair:

- foreground-service flag poll: 1 second -> 3 seconds;
- visible-UI poll: 300 ms -> 1 second;
- Accessibility notification timeout: 25 ms -> 100 ms;
- lightweight event text is checked before any source-node binder access;
- source-node inspection remains for click, context-click, and window-state events;
- the 15-minute WorkManager heartbeat waits up to 4 seconds for the slower service loop.

P2P/WebRTC and its 20-second application-level keepalive are deliberately unchanged in this pass because transport stability is currently good. If battery drain remains high, measure that layer separately before changing the keepalive cadence.

## Runtime Start/Stop state

A foreground synchronization service may already be active when the UI opens because a previous session survived, resumed at startup, or was recreated by recovery. In that case `Connected` / `接続済み` is valid before the user presses the upper control.

The upper control is a service Start/Stop toggle. Persisted `wsIsRunning` is now synchronized into React state, so:

- runtime `true` -> `Stop` / `停止`;
- runtime `false` -> `Start` / `開始`.

## Android background capture architecture

Android 10+ does not provide ordinary background clipboard reads unless the application is focused or is the default IME. Therefore Accessibility recovers explicitly selected text around an explicit Copy action instead of merely triggering `ClipboardManager.primaryClip`.

Current implementation:

- observes selection and copy-related Accessibility events;
- retains recent selected text for 60 seconds;
- scans interactive Accessibility windows for a live selected range when necessary;
- queues only the selected substring;
- requires a Copy cue rather than sending on selection alone;
- uses a persistent native queue with TTL and text deduplication;
- requests bounded transport recovery when transport or React state is unavailable.

Target-device matrix proof remains pending.

## Android initialization repair

Reported error:

`Cannot perform this operation because the connection pool has been closed.`

Confirmed cause and repair:

- `AsyncStorageBridge` received the singleton database owned by React Native AsyncStorage;
- its `disconnect()` method closed that shared database;
- the transformed bridge no longer closes the shared database and only releases its local reference;
- Android CI rejects the transformed source if the unsafe close remains.

## Exactly-once relay repair

A single native queue item could be observed by more than one JavaScript service listener after failed initialization and reopen.

Current implementation:

- native `RelaySettingsModule` owns a process-wide synchronized relay-claim map;
- each JavaScript listener must claim the opaque relay ID before sending;
- only the first listener succeeds;
- an unaccepted send releases the claim;
- native acknowledgement releases the claim;
- stale claims expire after five seconds so retry remains possible.

## Recovery and service startup

Implemented:

- clipboard and OTP queues request recovery for offline transport, missing React context, or failed event delivery;
- the first recovery request after boot is not incorrectly cooldown-suppressed;
- repeated attempts remain bounded;
- startup recreates a missing previous foreground-service generation instead of changing synchronization to OFF;
- zero P2P peers is treated as a normal queued condition rather than restart failure;
- boot, network, heartbeat, and delayed WorkManager recovery infrastructure remains present.

Android background-start restrictions and HONOR/MagicOS process management still require real-device testing.

## Verification-code relay

Implemented in code:

- local NotificationListenerService extraction;
- Japanese and English contextual matching and false-positive controls;
- standard, expanded, text-lines, conversation, and MessagingStyle fields;
- persistence of only extracted value, timestamp, and opaque relay ID;
- optional source-package filtering;
- persistent queue, TTL, deduplication, retry, and acknowledgement timeout;
- synthetic local notification test.

No real SMS/email result is currently valid because the previous test was contaminated by Phone Link. The synthetic and real screen-state matrix must be repeated in isolation.

## Delivery acknowledgement — preserve

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

P2S:

`LOCAL_TRANSPORT_ACCEPTED -> NATIVE_ACK -> DELETE`

Extended P2P:

`LOCAL_TRANSPORT_ACCEPTED -> PEER_RECEIVED -> PEER_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

Old/non-Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

Preserve:

- `relayId` and `ackRequested`;
- peer ACK only after validated clipboard application or duplicate-already-applied handling;
- ACK-envelope handling before clipboard parsing;
- receive-hash commit only after validation;
- generation-scoped timers;
- native acknowledgement-based deletion;
- queue wakeup after `SHARED_TEXT` listener registration.

P2S and old-peer fallback are not peer-applied acknowledgement.

## Windows status

User-validated:

- real authentication works;
- Windows GUI and synchronization work;
- Windows -> Android reception works while Android is backgrounded.

Implemented:

- authenticated persistent HTTP session and response validation;
- visible status/control window;
- restart/reconnect/disconnect/log/diagnostic controls;
- rotating logs;
- second-launch activation;
- complete P2P teardown and restart;
- Extended P2P Windows-applied ACK;
- explicit Quit cleanup and final exit guarantee.

Real Task Manager proof for Quit and immediate relaunch remains pending.

## Mandatory next proof

With Phone Link and all competing synchronizers disabled:

1. update the Android app to versionCode `320112` without uninstalling;
2. confirm no connection-pool initialization error across repeated cold launches;
3. confirm an active service opens with `停止`, not `開始`;
4. confirm Stop completes within about three seconds and Start reconnects;
5. copy one unique value once and confirm exactly one peer clipboard application;
6. repeat visible, backgrounded, reopened, removed from recents, locked, and screen-off;
7. confirm queue deletion only after the defined acknowledgement;
8. run synthetic OTP, then real SMS and email without storing their contents;
9. compare battery usage over matched idle intervals with the same network and peer connected;
10. test Windows Quit process disappearance and immediate relaunch.

Failure classification:

- two native queue items: Accessibility/capture duplicate;
- one queue item and two peer applications: listener/transport duplicate;
- one peer application and two UI/history observations: receiver UI or another clipboard observer.

## Do not claim

Do not describe Android outbound synchronization as beta-ready, exactly-once, screen-off capable, real-SMS validated, or battery-efficient until the isolated target-device tests pass.
