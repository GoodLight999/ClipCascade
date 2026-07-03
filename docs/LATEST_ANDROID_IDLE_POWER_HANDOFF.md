# Android Idle Power Handoff — 2026-07-03

## User report

The repaired Android build is currently functioning very well, but battery consumption appears too high.

## Confirmed continuous work

Two avoidable idle costs were present:

1. The foreground JavaScript service synchronously read four AsyncStorage/SQLite flags once every second for its entire lifetime.
2. The Accessibility service accepted high-volume window-content events with a 25 ms notification timeout and fetched the source AccessibilityNodeInfo for every possible copy cue.

The P2P WebRTC connection and its existing keepalive remain unchanged in this pass because connection stability is currently good and must not be traded away without measurement.

## Repair

Build transform `prepare_idle_power.js` applies these changes:

- foreground-service flag polling: 1 second -> 3 seconds;
- visible-UI status polling: 300 ms -> 1 second;
- Accessibility notification timeout: 25 ms -> 100 ms;
- high-frequency Accessibility events first inspect lightweight event text;
- source-node binder inspection is retained only for direct click, context-click, and window-state events;
- the 15-minute WorkManager heartbeat waits up to 4 seconds so it remains compatible with the slower service poll.

## Preserved behavior

- explicit Copy detection and selected-text fallback remain enabled;
- persistent clipboard and verification-code queues remain unchanged;
- native relay claim and exactly-once protection remain unchanged;
- Extended P2P peer-applied acknowledgement remains unchanged;
- P2P keepalive cadence and WebRTC recovery remain unchanged;
- startup recovery and runtime Start/Stop state repair remain unchanged.

## Build identity

- versionName: `3.2.1-extended.8-standalone`
- versionCode: decimal `320112`
- package: `com.clipcascade.extended`
- deterministic test signer unchanged

## Required validation

1. Install over the existing stable-signed build without uninstalling.
2. Confirm ordinary Copy still relays while visible, backgrounded, locked, and screen-off.
3. Confirm notification-code relay still works.
4. Confirm Stop completes within about three seconds and Start reconnects normally.
5. Compare Android battery usage over a similar idle interval with the same network and peer connected.
6. Record foreground active time, background active time, and percentage consumed.

If drain remains high after this build, the next measurement target is the P2P/WebRTC transport and application-level 20-second data-channel keepalive. Do not lengthen or remove that keepalive without an isolated overnight connection-stability test.

PR #1 remains Draft.
