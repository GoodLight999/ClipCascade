# Clean Rebuild Handoff

Last updated: 2026-07-26 (Asia/Tokyo)

## Canonical repository and branches

- Working repository: `GoodLight999/Trial-and-Error-ClipCascade`
- Upstream source of truth: `Sathvik-Rao/ClipCascade`
- Upstream/default branch: `main`
- Verified upstream baseline SHA: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Mirror branch: `main`
- Active development branch: `clean-rebuild`
- Active draft PR: `#3`
- Latest fully green product head: `bb5f047fb448b59591b9590b8762a5ba907ecc50`

`main` was directly compared with the verified upstream SHA and was identical:

- ahead: 0
- behind: 0
- changed files: 0

The previous 529-commit patchwork PR #1 is closed and unmerged. Do not use its branch, implementation, assumptions, or documentation as a baseline.

## Read first

1. `docs/CLEAN_REBUILD_HANDOFF.md`
2. `docs/EXPERIMENT_LOG.md`
3. `docs/BUILD_REPRODUCTION.md`
4. `docs/BASELINE_ARCHITECTURE_INVENTORY.md`
5. `docs/DESKTOP_CONNECTION_STATE_MACHINE.md`
6. Draft PR #3 description and latest comments
7. Latest commit diff

If these sources disagree, correct the handoff and experiment log before implementing more code.

## Non-negotiable rules

1. Keep `main` as an upstream mirror.
2. Work only on focused branches/commits and preserve attributable changes.
3. Update this handoff in every meaningful session.
4. Append hypotheses, exact changes, evidence, failures, results, and decisions to `docs/EXPERIMENT_LOG.md`.
5. Do not stack speculative fixes.
6. Preserve the upstream server protocol and public-server compatibility.
7. Do not require root.
8. Produce APK, Windows, and Linux artifacts as build gates.
9. Never equate compilation with runtime correctness.
10. Android background outbound reliability requires real-device tests.
11. Do not inspect the archived patchwork branch except for a narrowly named, evidence-driven comparison.
12. Do not let GUI, transport, and diagnostics maintain conflicting state.
13. Never log or export clipboard payload contents in normal diagnostics.

## Product requirements

### Android

Reliable Android-to-server clipboard sharing while backgrounded. Permitted mechanisms:

- ordinary Android APIs;
- foreground services;
- AccessibilityService;
- Shizuku;
- ADB-assisted setup or operation;
- deliberate combinations and fallback.

Root-only designs are prohibited.

The app must detect capabilities, explain setup, verify health after setup, expose the active capture path, and fail visibly rather than silently.

### Power

Correct delivery comes first. After correctness, reduce polling, wakeups, duplicate processing, and reconnect churn using measurements rather than guesses.

### Automatic diagnostics

One-action diagnostics must eventually verify:

- authentication and server reachability;
- WebSocket/STOMP state;
- Android clipboard acquisition path;
- background execution capability;
- outbound queue and acknowledgement state;
- reconnect behavior;
- sanitized support-bundle export.

### Desktop

Windows and Linux must expose:

- connected/connecting/reconnecting/disconnected/auth-required/error states;
- last accepted send and last receive;
- actionable last error;
- manual reconnect;
- bounded automatic reconnect;
- logs and later diagnostics export;
- tray/CLI integration.

## Verified upstream architecture

### Android

- React Native `0.80.2` with Kotlin native modules.
- `StartForegroundService.js` currently owns transport, encryption, capture event listeners, duplicate suppression, service behavior, and media/file handling.
- `ClipboardListenerModule.kt` combines a normal clipboard listener with protected `READ_LOGS` logcat monitoring.
- The logcat path watches `ClipboardService:E` and launches `ClipboardFloatingActivity`.
- `ClipboardFloatingActivity` creates an overlay, temporarily takes focus, reads the clipboard, emits to React Native, and closes.
- This capture path is timing-sensitive and does not separately expose capture health and transport health.

### Desktop before this clean work

- Python, Tkinter/pystray, STOMP/WebSocket, PyInstaller.
- P2S `get_stats()` returned `None`.
- Close callbacks slept for a fixed interval and recursively called `connect()`.
- The tray initialized itself as connected independently of transport truth.
- The low-level client could return after WebSocket open before STOMP readiness.

### References

- Octoclip Accessibility and Shizuku documentation is used only as behavioral/setup reference.
- The surviving Go fork may be inspected at explicitly named revisions for lifecycle and no-root acquisition evidence.
- No external implementation is copied wholesale.

## Build reproduction

Workflow: `.github/workflows/baseline-artifacts.yml`

Current workflow behavior:

- branch-push verification on `clean-rebuild`;
- manual dispatch;
- documentation/Markdown-only branch pushes ignored;
- no duplicate pull-request trigger;
- Windows and Linux unit-test matrix;
- Android debug APK;
- Windows standalone EXE;
- Linux source package;
- embedded SHA-256 files;
- Android Gradle log uploaded even on failure.

The upstream Android tree omitted `android/gradle.properties` while `app/build.gradle` referenced `hermesEnabled`. The clean branch restored the standard React Native 0.80 properties in commit `e725f479c5075baa6d32ed465322d6c0ee06979f`. This is a build repair, not an Android reliability fix.

## Desktop P2S implementation status

Detailed contract and evidence: `docs/DESKTOP_CONNECTION_STATE_MACHINE.md`

Implemented:

- authoritative `ConnectionController`;
- states: `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `RECONNECT_WAIT`, `AUTH_REQUIRED`, `STOPPING`, `FATAL_ERROR`;
- immutable snapshots;
- normalized errors;
- capped exponential backoff with jitter;
- cancellable timers and stale-generation protection;
- no `sleep()` inside socket-close callbacks;
- actual STOMP `CONNECTED` plus subscription completion before connected state;
- fresh client per connection attempt;
- manual reconnect and automatic recovery paths;
- runtime lost/restored notifications;
- last accepted send and last valid receive observations;
- P2S GUI and CLI primary controls derived from snapshots;
- explicit P2P legacy fallback rather than a mixed partial migration;
- Windows/Linux automated tests;
- Windows EXE and Linux package build compatibility.

Not yet proven:

- real public-server runtime login/connect on the generated EXE;
- forced network-loss countdown and recovery on Windows;
- in-process reauthentication after `AUTH_REQUIRED`;
- P2P migration;
- full persistent status window;
- diagnostics bundle export;
- application-level delivery acknowledgement or durable outbound queue.

## Latest fully green verification

Workflow run: `30193922241`

All five jobs succeeded:

- Android debug APK;
- Windows standalone EXE;
- Linux source package;
- desktop unit tests on Ubuntu;
- desktop unit tests on Windows.

Artifacts:

| Platform | Artifact ID | Size inside artifact | SHA-256 |
|---|---:|---:|---|
| Android debug APK | `8629529566` | 146,796,102 bytes | `2aae59fc24b7d9e50b58f5be89b1f54267197f08624ba08aff161d901e6408bc` |
| Windows EXE | `8629510789` | 57,456,724 bytes | `64190743de0c19a581675db255db7736d74602c5098ebf7c632c03bf66f1552a` |
| Linux package | `8629490534` | See workflow artifact | Embedded checksum generated by workflow |
| Android build log | `8629528730` | See workflow artifact | GitHub artifact digest available |

Independent checks passed:

- embedded Windows checksum;
- Windows PE32+ x86-64 GUI format;
- embedded Android checksum;
- Android APK identification;
- APK ZIP integrity.

The Android APK remains a debug-signed upstream-behavior build. It does **not** contain the requested Android background reliability redesign yet.

## Planned delivery sequence

1. Baseline inventory and reproducible upstream builds — **complete**.
2. Desktop P2S state model, STOMP readiness, reconnect, and status controls — **implemented and CI-green; live smoke test pending**.
3. Desktop diagnostics/status panel and live network-loss verification.
4. Android acquisition capability contract and instrumentation.
5. Android capture-event normalization and durable outbound queue.
6. Accessibility prototype and real-device evaluation.
7. Shizuku prototype and guided setup.
8. Optional ADB-assisted path and setup automation.
9. Adaptive fallback and power measurement.
10. One-action diagnostics/support bundle.
11. Release signing and final APK/EXE/Linux artifacts.

## Exact next actions

1. Run the current Windows EXE against the unchanged public server.
2. Verify login, connect, copy send/receive, manual disconnect, and quit.
3. Force network loss and verify reconnect countdown, manual retry, automatic recovery, and notification behavior.
4. Record sanitized logs and exact observations in `docs/EXPERIMENT_LOG.md`.
5. Design and add the Android `ClipboardAcquisitionBackend` capability contract before touching existing capture triggers.
6. Add capture-health events and an acquisition-only diagnostic model independent from transport.
7. Recover narrowly relevant Android lifecycle facts from `wuxinkami/ClipCascade_go_fork` history without importing its architecture wholesale.
8. Prototype ordinary-listener and Accessibility backends behind the same contract.
9. Add Shizuku only after the capability and diagnostic interfaces are stable.

## Definition of a valid continuation

A new thread must be able to identify:

- exact upstream baseline;
- active branch and PR;
- latest green product head;
- completed implementation;
- unverified claims;
- current artifacts;
- exact next action;
- prior failed attempts and their evidence.

Those facts must be obtainable from this file, `docs/EXPERIMENT_LOG.md`, the state-machine document, PR #3, and the latest diff without reading the archived patchwork branch.
