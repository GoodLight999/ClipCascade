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

No Android foreground/background/locked/screen-off outbound success is currently proven. Phone Link and every competing clipboard synchronizer must be disabled for future validation. PR #1 remains Draft.

## Current Android build

Prepared identity:

- app: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.6-standalone`
- versionCode: `320110`
- deterministic test signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

It is intended to update stable-signed versionCode `320107`, `320108`, or `320109` without uninstalling.

## Android background capture architecture

Android 10+ does not provide ordinary background clipboard reads unless the application is focused or is the default IME. Therefore Accessibility must recover the explicitly selected text around an explicit Copy action instead of merely triggering `ClipboardManager.primaryClip`.

Current implementation:

- observes selection and copy-related Accessibility events;
- retains recent selected text for 60 seconds;
- scans interactive Accessibility windows for a live selected range when necessary;
- queues only the selected substring;
- requires a Copy cue rather than sending on selection alone;
- uses a persistent native queue with TTL and text deduplication;
- requests bounded transport recovery when transport or React state is unavailable.

Target-device proof remains pending.

## Latest Android initialization repair

Reported error:

`Cannot perform this operation because the connection pool has been closed.`

Confirmed cause:

- `AsyncStorageBridge` received the singleton database owned by React Native AsyncStorage;
- its `disconnect()` method closed that shared database;
- React initialization could then access an invalid connection pool;
- reopening the application recreated/reopened the supplier connection, explaining the temporary recovery.

Repair:

- the transformed bridge no longer closes the shared database;
- it only releases its local reference;
- Android CI rejects the transformed source if the unsafe close remains.

## Latest exactly-once relay repair

A single native queue item could be observed by more than one JavaScript service listener after failed initialization and reopen. Text hashing does not fully protect this case because native queue delivery intentionally forces sending and each service generation has separate JavaScript transport state.

Current implementation:

- native `RelaySettingsModule` owns a process-wide synchronized relay-claim map;
- each JavaScript listener must claim the opaque relay ID before sending;
- only the first listener succeeds;
- an unaccepted send releases the claim;
- native acknowledgement releases the claim;
- stale claims expire after five seconds so retry remains possible.

This protects one native queue item from duplicate JavaScript listeners while retaining the existing Accessibility-level text deduplication for separately created items.

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

The supplied log showed failed link-local candidate binds followed by successful candidates and `ICE completed`. The watchdog now waits ten seconds before reporting a persistent unhealthy state; full restart remains delayed until 25 seconds.

Real Task Manager proof for Quit and immediate relaunch remains pending.

## Latest green code artifacts

Code/release-workflow commit:

- `fb31ebca8fc99a7ff9504163cf584f35e52127ba`
- Android CI `28317382119`: success
- Windows CI `28317382112`: success
- Android artifact ID `7933086597`
- Windows artifact ID `7933081818`
- APK SHA-256: `572f39b7e524a83b3d6e2819295b5aba111eed16d76ac3a9e3c10fc512fe3135`
- EXE SHA-256: `0953676d8eec3a69084b7cd17fd4e273906be8fece1d2e764a93ceaae573dd5d`

Exact ZIP hashes and test instructions are in `docs/LATEST_ANDROID_INIT_DUPLICATE_HANDOFF.md`.

## Mandatory next proof

With Phone Link and all competing synchronizers disabled:

1. update the Android app to versionCode `320110` without uninstalling;
2. cold-launch at least five times and confirm no connection-pool initialization error;
3. copy one unique synthetic value once and confirm exactly one peer clipboard application;
4. repeat after closing/reopening the UI while synchronization remains active;
5. repeat backgrounded for 30 seconds, removed from recents, locked, and screen-off;
6. confirm queue deletion only after the defined acknowledgement;
7. run synthetic OTP, then real SMS and email without storing their contents;
8. test Windows Quit process disappearance and immediate relaunch.

Failure classification:

- two native queue items: Accessibility/capture duplicate;
- one queue item and two peer applications: listener/transport duplicate;
- one peer application and two UI/history observations: receiver UI or another clipboard observer.

## Do not claim

Do not describe Android outbound synchronization as working, beta-ready, exactly-once, screen-off capable, or real-SMS validated until these isolated target-device tests pass.
