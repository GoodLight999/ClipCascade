# Clean Rebuild Handoff

Last updated: 2026-07-26 (Asia/Tokyo)

## Canonical repository and refs

- Repository: `GoodLight999/Trial-and-Error-ClipCascade`
- Upstream: `Sathvik-Rao/ClipCascade`
- Pristine upstream/mirror baseline: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Mirror branch: `main`
- Active branch: `clean-rebuild`
- Active draft PR: `#3`
- Latest fully green **product-code/pipeline** head: `a94b830fb954d09fc742b39833cebd5915988566`
- Latest fully green workflow: `30203690726`

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
14. Never distribute a React Native debug APK as a standalone test artifact unless Metro is deliberately part of the test.
15. A user-installable Android artifact must pass an automated APK check for `assets/index.android.bundle`.

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
3. Android app JVM tests plus **standalone bundled APK**;
4. Windows standalone EXE;
5. Linux source package.

Android command:

```text
:app:testDebugUnitTest :app:assembleStandalone
```

The `standalone` Android build type:

- inherits release runtime semantics;
- is signed with the debug key and remains an engineering artifact;
- is not listed in React Native `debuggableVariants`;
- packages the Hermes JavaScript bundle;
- does not require Metro at runtime.

CI rejects the Android artifact unless all of these pass:

```text
assets/index.android.bundle exists in the APK
APK ZIP integrity passes
embedded SHA-256 is generated
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
- healthy `AVAILABLE` backends selected before any `DEGRADED` backend;
- configured priority used only within the same capability quality.

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

- `PASSED`: native event reached the active React Native root runtime and the matching ACK returned.
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

Production Kotlin, Manifest, shortcut resources, React Native/Hermes bundle, diagnostics Activity, and standalone APK assembly all passed.

## Latest verified artifacts

Workflow run: `30203690726`

Product-code/pipeline head: `a94b830fb954d09fc742b39833cebd5915988566`

All five jobs succeeded.

| Platform | Artifact ID | Inner file | Size | SHA-256 |
|---|---:|---|---:|---|
| Android standalone APK | `8632482778` | `ClipCascade-Android-clean-rebuild-standalone.apk` | 93,574,655 bytes | `ca7ee41f95f729879a5298bdc2b6e413f0f2086cbc922205e06468d7878f471b` |
| Android build log | `8632482104` | Gradle log | see artifact | GitHub digest retained |
| Windows EXE | `8632434975` | `ClipCascade-Windows-clean-rebuild.exe` | 57,456,724 bytes | `36da95c51e473b8bf33677f81142ea70bdfcd81a968e24e8a0be4878e11ce103` |
| Linux package | `8632420551` | `ClipCascade-Linux-clean-rebuild.tar.gz` | 68,791 bytes | `6fadbb46751fe0e714f85d9118c69c2ecc7ea14c0d8a76b2d85c2eed0114ba6b` |

Independent post-download checks passed:

- Android embedded SHA-256;
- Android package identification;
- `assets/index.android.bundle` present;
- APK ZIP integrity;
- Windows embedded SHA-256;
- PE32+ x86-64 GUI identification;
- Linux embedded SHA-256;
- gzip identification and 53-entry archive enumeration.

The Android APK uses release runtime behavior but debug signing. It is a standalone engineering artifact, not a production release.

## Failed attempts that must remain understood

### Run `30201114997`

Unqualified `testDebugUnitTest` compiled defective sample tests in `@react-native-module/pbkdf2`. The product did not require a dependency patch. CI was scoped to `:app:testDebugUnitTest`.

### Run `30201352946`

Five JUnit expectations used `Int` literals against intentionally `Long` counters/timestamps. Production types were preserved and test expectations corrected.

### Distributed debug APK required Metro

The artifact from run `30202889590` was built with `assembleDebug`. React Native deliberately skips JS bundling for the debug variant, so installation on a disconnected device produced `Unable to load script` and requested Metro/ADB reverse. The APK was structurally valid but unsuitable as a standalone user-test artifact.

Decision:

- the old debug APK is superseded and must not be redistributed for ordinary installation;
- retain `debug` for Metro development;
- distribute only the `standalone` variant for device testing;
- require automated bundle-presence verification in CI.

Detailed evidence: `docs/EXPERIMENT_LOG_2026-07-26_ANDROID_ACQUISITION.md`.

## Not implemented or not proven

- AccessibilityService backend;
- Shizuku backend;
- ADB-assisted backend;
- adaptive coordinator that starts/stops the selected background backend;
- durable outbound queue and server acknowledgement;
- real-device foreground/background capture acceptance;
- on-device success of the new standalone artifact;
- overlay focus-regression testing;
- duplicate-send testing under multiple acquisition triggers;
- power measurements;
- production release signing.

## Exact next actions

1. Uninstall or overwrite the old debug APK with the latest **standalone** APK.
2. Launch it without Metro, USB, or `adb reverse`; confirm the main React Native screen loads.
3. Start the ClipCascade service so the React Native context exists.
4. Long-press the app icon and select `Capture status`.
5. Run the native → React Native self-test and record `PASSED`, `TIMED_OUT`, or `EMIT_FAILED`.
6. Separately test ordinary foreground clipboard capture.
7. Separately test legacy logcat/overlay background capture from launcher/search fields, browser pages, Amazon, text-selection toolbars, and apps that emit no recognizable accessibility copy event.
8. Record whether overlay focus dismisses search, changes selection, opens unwanted UI, or causes duplicates.
9. Classify the existing path per device as healthy, degraded, or blocked.
10. Implement Accessibility only behind the existing backend contract and capability probe.
11. Add Shizuku only after detection, permission, reboot recovery, guided setup, and post-setup self-test are specified.
12. Design durable outbound queueing separately from acquisition.
13. Perform Windows public-server and forced-network-loss smoke tests.

## Valid continuation criterion

A new thread must be able to identify, without reading archived patchwork code:

- exact upstream baseline;
- active branch and PR;
- latest green product-code/pipeline head and workflow;
- completed desktop and Android work;
- failed attempts and decisions;
- current standalone artifacts and hashes;
- unverified claims;
- exact next action.
