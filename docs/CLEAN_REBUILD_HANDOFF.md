# Clean Rebuild Handoff

Last updated: 2026-07-26 (Asia/Tokyo)

## Canonical repository and refs

- Repository: `GoodLight999/Trial-and-Error-ClipCascade`
- Upstream: `Sathvik-Rao/ClipCascade`
- Pristine upstream/mirror baseline: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Mirror branch: `main`
- Active branch: `clean-rebuild`
- Active draft PR: `#3`
- Latest fully green **product-code** head: `01394199be3e40160dcd592e8d0e5ee6a85722d1`
- Latest fully green workflow: `30202889590`

`main` was verified identical to the upstream baseline: ahead 0, behind 0, changed files 0.

The prior 529-commit patchwork PR #1 is closed and unmerged. Do not use its branch, implementation, assumptions, or documentation as a baseline.

## Read first

1. `docs/CLEAN_REBUILD_HANDOFF.md`
2. `docs/EXPERIMENT_LOG.md`
3. `docs/EXPERIMENT_LOG_2026-07-26_ANDROID_ACQUISITION.md`
4. `docs/ANDROID_ACQUISITION_ARCHITECTURE.md`
5. `docs/BUILD_REPRODUCTION.md`
6. `docs/BASELINE_ARCHITECTURE_INVENTORY.md`
7. `docs/DESKTOP_CONNECTION_STATE_MACHINE.md`
8. Draft PR #3 description and recent comments
9. Latest product-code diff

Where older documents describe pre-migration status, this handoff and the dated Android experiment log are authoritative.

## Permanent operating rules

1. Keep `main` as an upstream mirror.
2. Use focused, attributable commits on `clean-rebuild`.
3. Record every meaningful hypothesis, attempt, failure, result, artifact, and decision.
4. Do not stack speculative fixes.
5. Preserve the public server protocol.
6. Do not require root.
7. Never equate compilation or unit tests with runtime correctness.
8. Android background reliability requires real-device evidence.
9. Do not patch generated `node_modules` merely to make CI green.
10. GUI, transport, acquisition, and diagnostics must not own conflicting state.
11. Normal logs, diagnostics, and support exports must not contain clipboard payloads, credentials, or secret-bearing URLs.
12. Acquisition success is not transport acknowledgement; transport acceptance is not server delivery acknowledgement.
13. Do not inspect the archived patchwork branch except for a narrowly named evidence-driven comparison.

## CI gate

Workflow: `.github/workflows/baseline-artifacts.yml`

Triggers:

- one `pull_request` run for product-code updates targeting `main`;
- `workflow_dispatch` for an explicit rebuild;
- documentation-only changes under `docs/**` or `**/*.md` are ignored;
- no simultaneous push trigger, preventing duplicate matrices.

Jobs:

1. desktop unit tests on Ubuntu;
2. desktop unit tests on Windows;
3. Android app JVM tests plus debug APK;
4. Windows standalone EXE;
5. Linux source package.

Android command:

```text
:app:testDebugUnitTest :app:assembleDebug
```

Unqualified `testDebugUnitTest` is prohibited because it compiles defective sample tests in third-party React Native subprojects.

The clean branch also restores the omitted standard React Native 0.80 `android/gradle.properties`. That is a build repair, not a runtime fix.

## Desktop P2S status

Implemented and CI-green:

- one authoritative `ConnectionController`;
- states `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `RECONNECT_WAIT`, `AUTH_REQUIRED`, `STOPPING`, `FATAL_ERROR`;
- immutable snapshots and normalized errors;
- capped exponential backoff with jitter;
- cancellable timers and stale-generation protection;
- no socket-callback sleeps or recursive reconnect;
- actual STOMP `CONNECTED` plus subscription completion before connected state;
- fresh client per attempt;
- manual reconnect, automatic recovery, lost/restored notification;
- last accepted send and valid receive observations;
- GUI and CLI controls derived from snapshots;
- explicit legacy fallback for P2P;
- Windows and Ubuntu automated tests;
- Windows EXE and Linux package compatibility.

Not yet proven or implemented:

- real public-server smoke test of the generated Windows EXE;
- forced network-loss recovery on Windows;
- in-process reauthentication after `AUTH_REQUIRED`;
- P2P migration to the same snapshot contract;
- persistent status window;
- diagnostics bundle export;
- application-level delivery acknowledgement;
- durable outbound queue.

## Android acquisition architecture status

### Pure contracts and policy

Implemented and CI-green:

- `ClipboardAcquisitionBackend` interface;
- backend IDs `ORDINARY_LISTENER`, `ACCESSIBILITY`, `LOGCAT_OVERLAY`, `SHIZUKU`, `ADB_ASSISTED`, `MANUAL_SHARE`;
- capability state, reason code, required action, coverage, trigger, read-result, and coordinator vocabularies;
- pure backend selection policy;
- pure coordinator reducer;
- trigger deduplication model;
- backend runtime snapshots;
- healthy `AVAILABLE` backends are selected before any `DEGRADED` backend;
- configured priority breaks ties only within the same capability quality.

### Ordinary listener

- `OrdinaryClipboardBackend` owns idempotent start/stop and trigger metadata.
- `AndroidClipboardChangeRegistrar` is the thin Android framework adapter.
- Tests cover start/stop, concurrent start, stale callback, registration failure, unregistration failure, trigger delivery failure, counters, and monotonic timestamps.

### Shared native read runtime

- `ClipboardReadRuntime` is the single extraction and React Native emission path used by the ordinary listener and legacy overlay.
- Existing text, image URI, and file URI event shape is preserved.
- Diagnostics retain only counts, timestamps, backend ID, and stable result.
- Clipboard content is never retained by diagnostics.

### Legacy logcat/overlay hardening

- `READ_LOGS` and overlay capability are checked on every start request.
- logcat process/thread ownership uses a generation token.
- stop invalidates stale callbacks.
- log match, overlay launch, focus failure, read failure, process state, and permission state are distinct observations.
- `ClipboardFloatingActivity` uses the shared read runtime and idempotent teardown.

## Android acquisition diagnostics

Implemented and CI-green:

- immutable `AcquisitionDiagnosticsSnapshot`;
- native and React Native diagnostics use the same snapshot builder;
- pure `AcquisitionDiagnosticsFormatter` with tests;
- independent native `AcquisitionDiagnosticsActivity`;
- static launcher long-press shortcut: `Capture status`;
- actions: Refresh, run self-test, overlay settings, open ClipCascade, close;
- selectable monospaced status text;
- no clipboard content, credentials, or server URLs.

Native API:

```text
ClipboardListener.getAcquisitionSnapshot()
```

Current fields include:

- acquisition requested;
- ordinary listener running/start/trigger/error state and lifecycle timestamps;
- `READ_LOGS` and overlay permission;
- logcat thread/process/generation;
- latest log match and overlay launch;
- latest read backend/result;
- read attempt/success/failure counts;
- native → React Native self-test state and counts.

## Payload-free native → React Native self-test

Implemented and CI-green:

- `AcquisitionSelfTestTracker` states: `IDLE`, `PENDING`, `PASSED`, `TIMED_OUT`, `EMIT_FAILED`;
- deterministic 5-second timeout;
- matching test ID only; stale/mismatched ACK ignored;
- new test supersedes the previous ID;
- native emits only `{testId}` on `onAcquisitionSelfTest`;
- independent `AcquisitionSelfTestBridge.js` is initialized from `index.js`;
- JS validates the ID and calls `acknowledgeAcquisitionSelfTest(testId)`;
- the self-test does not read the clipboard and does not enter encryption, WebSocket, P2P, P2S, or outbound clipboard handlers;
- the diagnostic Activity refreshes immediately, after 350 ms, and after timeout.

Interpretation on a real device:

- `PASSED`: the native event reached the active React Native root runtime and the matching ACK returned.
- `TIMED_OUT`: no matching ACK returned within five seconds.
- `EMIT_FAILED`: native event emission itself failed.

A `PASSED` result does **not** prove clipboard capture, transport, server receipt, or remote clipboard application.

## Automated Android verification

Exactly 46 app JVM tests are present and passed:

- acquisition state reducer: 12;
- backend selection: 9;
- ordinary listener backend: 8;
- trigger deduplication: 9;
- diagnostics formatter: 2;
- self-test tracker: 6.

Production Kotlin, Manifest, shortcut resources, React Native bundle, diagnostics Activity, and APK assembly all passed.

## Latest verified artifacts

Workflow run: `30202889590`

Product-code head: `01394199be3e40160dcd592e8d0e5ee6a85722d1`

All five jobs succeeded.

| Platform | Artifact ID | Inner file | Size | SHA-256 |
|---|---:|---|---:|---|
| Android debug APK | `8632223256` | `ClipCascade-Android-clean-rebuild-debug.apk` | 146,868,392 bytes | `a9c23f3d54c529f501ee92fe233cc434264edf862de5b1be82b6b86b2d434808` |
| Android build log | `8632222540` | Gradle log | see artifact | GitHub digest retained |
| Windows EXE | `8632211465` | `ClipCascade-Windows-clean-rebuild.exe` | 57,456,724 bytes | `ad09902ba6e6ccc22fce7b42d9aad7f6f6755647e435be05d97d413dfc910491` |
| Linux package | `8632190948` | `ClipCascade-Linux-clean-rebuild.tar.gz` | 68,793 bytes | `9156f49b406e2d4acbd00007fc2194e5a3b3e66fd676541413b8b533576fff50` |

Independent post-download checks passed:

- Android embedded SHA-256;
- APK identification;
- APK ZIP integrity;
- Windows embedded SHA-256;
- PE32+ x86-64 GUI identification;
- Linux embedded SHA-256;
- gzip identification and 53-entry archive enumeration.

The APK is debug-signed and is an engineering artifact, not a production release.

## Failed attempts that must remain understood

### Run `30201114997`

Unqualified `testDebugUnitTest` compiled defective sample tests in `@react-native-module/pbkdf2`. The product did not require a dependency patch. CI was scoped to `:app:testDebugUnitTest`.

### Run `30201352946`

Five JUnit expectations used `Int` literals against intentionally `Long` counters/timestamps. Production types were preserved and test expectations corrected.

Detailed evidence: `docs/EXPERIMENT_LOG_2026-07-26_ANDROID_ACQUISITION.md`.

## Not implemented or not proven

- AccessibilityService backend;
- Shizuku backend;
- ADB-assisted backend;
- adaptive coordinator that starts/stops the selected background backend;
- durable outbound queue and server acknowledgement;
- real-device foreground/background capture acceptance;
- overlay focus-regression testing;
- duplicate-send testing under multiple acquisition triggers;
- power measurements;
- release signing.

## Exact next actions

1. Install the latest APK on a real Android device.
2. Open ClipCascade and start the service so the React Native context exists.
3. Long-press the app icon and select `Capture status`.
4. Run the native → React Native self-test and record `PASSED`, `TIMED_OUT`, or `EMIT_FAILED`.
5. Separately test ordinary foreground clipboard capture.
6. Separately test legacy logcat/overlay background capture from:
   - launcher/search fields;
   - browser pages;
   - Amazon;
   - text-selection toolbars;
   - apps that emit no recognizable accessibility copy event.
7. Record whether overlay focus dismisses search, changes selection, opens unwanted UI, or causes duplicates.
8. Classify the existing path per device as healthy, degraded, or blocked.
9. Implement Accessibility only behind the existing backend contract and capability probe.
10. Add Shizuku only after detection, permission, reboot recovery, guided setup, and post-setup self-test are specified.
11. Design durable outbound queueing separately from acquisition.
12. Perform Windows public-server and forced-network-loss smoke tests.

## Valid continuation criterion

A new thread must be able to identify, without reading archived patchwork code:

- exact upstream baseline;
- active branch and PR;
- latest green product-code head and workflow;
- completed desktop and Android work;
- failed attempts and decisions;
- current artifacts and hashes;
- unverified claims;
- exact next action.
