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

`main` was compared directly against the verified upstream SHA and was identical:

- ahead: 0
- behind: 0
- changed files: 0

The old patchwork draft PR was closed. Do not use its branch, files, assumptions, or architecture as a baseline.

## Read-first documents

1. `docs/CLEAN_REBUILD_HANDOFF.md`
2. `docs/EXPERIMENT_LOG.md`
3. `docs/BUILD_REPRODUCTION.md`
4. `docs/BASELINE_ARCHITECTURE_INVENTORY.md`
5. `docs/DESKTOP_CONNECTION_STATE_MACHINE.md`
6. Draft PR #3 description and latest comments
7. Latest commit diff

## Non-negotiable operating rules

1. Keep `main` as an upstream mirror. Product changes belong on focused branches and enter through reviewed pull requests.
2. Update this handoff in every meaningful work session.
3. Append every experiment, failure, build result, and device observation to `docs/EXPERIMENT_LOG.md`.
4. Do not stack speculative fixes. Every behavior change must have:
   - a stated hypothesis;
   - a bounded implementation;
   - a reproducible verification method;
   - an explicit outcome.
5. Preserve the upstream server protocol and compatibility with the public ClipCascade server.
6. Do not require root access.
7. Produce installable artifacts as release gates:
   - Android APK;
   - Windows executable/package;
   - Linux executable/package.
8. Never report a feature as working merely because compilation or CI passed. Background Android outbound sync requires a real-device acceptance test.
9. Do not inspect or copy the archived patchwork implementation unless a narrowly scoped experiment explicitly justifies one isolated comparison.
10. Separate pure models, transport integration, and UI migration into independently testable commits.

## Product requirements

### Android outbound reliability

Make Android-to-server clipboard transmission reliable while the application is backgrounded. Permitted mechanisms include:

- ordinary Android APIs;
- foreground services;
- AccessibilityService;
- Shizuku-mediated privileged operations;
- ADB-assisted setup or operation;
- combinations of the above.

Root-only designs are prohibited.

The implementation should prefer the least intrusive available mechanism, automatically detect capabilities, expose clear health state, and fall back deliberately rather than silently.

### Power consumption

Reduce unnecessary polling, wakeups, reconnect loops, and duplicated clipboard processing without sacrificing reliability. Power work is subordinate to correct delivery and must be measured rather than guessed.

### Setup experience

Provide a guided setup that a non-technical user can complete. Shizuku integration, when present, must include capability detection, step-by-step instructions, deep links where possible, and post-setup verification.

### Automatic diagnostics

Provide a one-action diagnostic mode that:

- verifies authentication and server reachability;
- verifies WebSocket/session state;
- verifies Android clipboard acquisition paths;
- verifies background execution capability;
- verifies outbound queueing and acknowledgement;
- records reconnect behavior;
- exports a sanitized support bundle.

### Desktop reliability and visibility

Windows and Linux clients must provide a persistent status UI after login, including:

- connected/disconnected/reconnecting state;
- last successful send and receive;
- last error with actionable explanation;
- manual reconnect;
- automatic reconnect with bounded exponential backoff and jitter;
- diagnostics export;
- tray/status integration where supported.

## Architecture guardrails

- One authoritative connection state machine per client.
- One authoritative outbound queue per client.
- Idempotent message handling and duplicate suppression.
- Explicit capability model for Android acquisition methods.
- No hidden infinite retry loops.
- No feature-specific reconnect implementations.
- No UI-only state that disagrees with transport state.
- Structured logs with stable event names.
- Sensitive clipboard contents must not appear in normal logs or exported diagnostics.

## Verified upstream architecture findings

Detailed evidence is recorded in `docs/BASELINE_ARCHITECTURE_INVENTORY.md`.

### Android

- React Native `0.80.2` with Kotlin native modules.
- The large `StartForegroundService.js` module currently owns transport, encryption, clipboard event listeners, duplicate suppression, foreground-service behavior, and media/file handling.
- `ClipboardListenerModule.kt` uses both a normal clipboard listener and a protected `READ_LOGS` logcat reader.
- The logcat path watches `ClipboardService:E`; when it sees a matching denial/event line, it launches `ClipboardFloatingActivity`.
- `ClipboardFloatingActivity` creates an overlay, temporarily takes focus, reads the clipboard, emits an event to React Native, and closes.
- This path is timing-sensitive and does not separate capture health from transport health.

### Desktop

- Python, Tkinter/pystray, STOMP/WebSocket, and PyInstaller.
- `STOMPManager.get_stats()` returns `None`.
- reconnect uses fixed-delay sleep and recursive `connect()` from the close callback.
- tray state initializes as connected independently of transport truth.
- the tray polls stats every second, but the STOMP implementation supplies none.

### External behavioral references

- Octoclip documents no-root Accessibility and Shizuku setup flows.
- Accessibility is acknowledged to miss copy operations that produce no recognizable accessibility event.
- Shizuku modes demonstrate capability detection, guided authorization, verification, and reboot-recovery UX patterns.
- No Octoclip source code is used.

## Reproducible baseline artifacts

Authoritative workflow: `.github/workflows/baseline-artifacts.yml`

The first Android attempt exposed an upstream build-tree omission: `android/app/build.gradle` referenced `hermesEnabled`, but the repository lacked `android/gradle.properties`. The clean branch restored the standard React Native 0.80 Android properties in commit `e725f479c5075baa6d32ed465322d6c0ee06979f`. This is a build-configuration repair, not a runtime reliability fix.

First fully green workflow:

- run ID: `30192411087`
- build-config head: `e725f479c5075baa6d32ed465322d6c0ee06979f`
- Android: success
- Windows: success
- Linux: success

Verified artifacts:

| Platform | Artifact ID | Inner file | Size | SHA-256 |
|---|---:|---|---:|---|
| Android | `8629067655` | `ClipCascade-Android-baseline-debug.apk` | 146,796,102 bytes | `0bac8825d51fe90bb1c07dca8fc9a39c28236b034896f23e33b87e5b29a850ba` |
| Windows | `8629026745` | `ClipCascade-Windows-baseline.exe` | 57,432,247 bytes | `7d7c16ca582ffc6881d7a268934e4b13c56f87bd04868689d367f6cf8a381b04` |
| Linux | `8629014836` | `ClipCascade-Linux-baseline.tar.gz` | 60,070 bytes | `6ae8433d278bfdc1e530171cdf8a7414a57d8d94927fdab607ce452d6dd99a5f` |

Independent checks passed for artifact checksum files, APK archive integrity, Windows PE format, and Linux archive enumeration.

The APK is debug-signed. A reproducible release-signing process remains a later release gate.

## Desktop state-machine contract

`docs/DESKTOP_CONNECTION_STATE_MACHINE.md` is now the authoritative implementation contract.

Key decisions:

- transport owns connection truth;
- initial state is `DISCONNECTED`, not connected;
- states are `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `RECONNECT_WAIT`, `AUTH_REQUIRED`, `STOPPING`, and `FATAL_ERROR`;
- retry uses cancellable capped exponential backoff with jitter;
- socket callbacks never sleep;
- snapshots provide state, retry timing, last send/receive, and normalized error fields;
- pure model/tests, STOMP integration, and tray migration must be separate commits.

No desktop product code has yet been migrated to this contract.

## Planned delivery sequence

1. Baseline inventory and reproducible upstream builds. **Complete.**
2. Protocol and transport characterization without server changes. **In progress.**
3. Desktop connection-state refactor and visible status UI.
4. Android acquisition capability matrix and instrumentation.
5. Android foreground/background delivery state machine.
6. Accessibility path prototype and real-device evaluation.
7. Shizuku path prototype and guided setup.
8. Adaptive fallback policy and power measurements.
9. One-action diagnostic mode and support bundle.
10. Release workflows producing APK, Windows, and Linux artifacts.

## Current state

Completed:

- verified pristine upstream mirror;
- archived the previous patchwork development line;
- created `clean-rebuild` and draft PR #3;
- established canonical handoff and append-only experiment records;
- inventoried Android capture and desktop reconnect failure surfaces;
- added repeatable Android, Windows, and Linux artifact CI;
- preserved and diagnosed the initial Android Gradle failure;
- restored the omitted React Native 0.80 Gradle properties;
- generated and independently verified APK, EXE, and Linux package artifacts;
- specified the desktop connection state machine and its test gates.

Runtime behavior has not yet been changed or claimed fixed. In particular, Android background outbound reliability remains untested on a real device.

## Exact next actions

1. Add the pure Python connection model, normalized errors, retry calculator, injected scheduler/clock interfaces, and unit tests described in `docs/DESKTOP_CONNECTION_STATE_MACHINE.md`.
2. Add Windows and Linux CI test jobs without changing `STOMPManager` yet.
3. Characterize STOMP close/auth/error callbacks and map them to normalized events.
4. Integrate the tested controller into `STOMPManager` in a separate commit.
5. Migrate tray rendering to immutable snapshots in another separate commit.
6. Build Windows and Linux artifacts and perform forced-network-loss smoke tests.
7. Characterize server send semantics before designing durable queue deletion.
8. Begin Android capability-backend implementation only after desktop diagnostics patterns are stable.

## Definition of a valid handoff

A new thread must be able to continue by reading only the read-first documents listed above.

If those sources disagree, this file and the experiment log must be corrected before further implementation.
