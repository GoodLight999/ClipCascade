# Android Acquisition Experiment Log — 2026-07-26

This file is a chronological continuation of `docs/EXPERIMENT_LOG.md` for the Android acquisition work performed after the desktop P2S status implementation. It is append-only except for factual corrections.

## Consolidate clipboard reads and acquisition diagnostics

- Baseline commit: `cc9ffaf1d1ce66607963626b280b19922e5b421c`
- Environment/device: source inspection through the authenticated GitHub connector; no Android device attached
- Hypothesis: ordinary-listener and logcat-overlay reads cannot be diagnosed reliably while each path extracts clipboard content and emits to React Native independently.
- Change or command:
  - added `acquisition/ClipboardReadRuntime.kt`;
  - routed ordinary-listener reads through the shared runtime;
  - routed `ClipboardFloatingActivity` reads through the same runtime;
  - exposed read attempt, success, failure, backend, and result counters without retaining clipboard payloads;
  - added generation-token ownership for the legacy logcat process/thread;
  - rechecked `READ_LOGS` and overlay permission on every start request;
  - recorded explicit focus/overlay/read failure codes.
- Expected result: both existing capture paths use one extraction and diagnostic vocabulary; stale logcat threads cannot overwrite current runtime state.
- Observed result: source changes committed successfully. Runtime behavior was not claimed before Gradle/JUnit and APK verification.
- Evidence/artifacts: commits from `62f9e10e2e43cc4818f4199fbe2468aea79c6860` through `3ac366cab2df56bc77d39c461644dfd152f9f263`.
- Decision: keep transport behavior and React Native event name unchanged while acquisition truth moves into the native shared runtime.
- Follow-up: run app-scoped JVM tests and produce an APK.

## Prefer healthy lower-priority backends over degraded higher-priority backends

- Baseline commit: Android pure acquisition model on `clean-rebuild`
- Environment/device: pure Kotlin/JUnit model
- Hypothesis: selecting a degraded Shizuku capability ahead of a healthy Accessibility capability is an incorrect interpretation of backend priority.
- Change or command: changed `BackendSelectionPolicy` to select `AVAILABLE` backends in priority order first, then `DEGRADED` backends; added `BackendSelectionQualityTest.kt`.
- Expected result: capability quality dominates backend preference; priority only breaks ties within the same quality.
- Observed result: the new policy and tests compiled; final CI success is recorded below.
- Evidence/artifacts: commits `5bf4d26a341c14910f0e99bb93c3b6cf69c2ab4a` and `67b5e3db4f41f8672ebd5ee8cd2f1e122b55f804`.
- Decision: never choose a known degraded path merely because it has a nominally higher preference.
- Follow-up: apply the same rule when real Accessibility and Shizuku capability probes are implemented.

## Restore one trackable CI run per PR update

- Baseline commit: workflow using only `clean-rebuild` push events
- Environment/device: GitHub Actions and connector API behavior
- Hypothesis: the push-only workflow prevents the available connector from resolving runs by commit, while simultaneous push and PR triggers would duplicate the matrix.
- Change or command: changed the workflow to one `pull_request` trigger targeting `main`, retained `workflow_dispatch`, and ignored `docs/**` and Markdown-only changes.
- Expected result: one heavyweight verification run per product-code PR update, trackable by commit; documentation-only updates do not rebuild artifacts.
- Observed result: PR runs became discoverable through `fetch_commit_workflow_runs`; no duplicate push run was configured.
- Evidence/artifacts: workflow commit `075ec42c63b17f1c3d0ed74ef064c1476c43a82a`.
- Decision: use PR-triggered verification as the canonical product gate while PR #3 remains active.
- Follow-up: preserve manual dispatch for release or diagnostic rebuilds.

## First Android test attempt incorrectly included dependency subprojects

- Baseline commit: `3ac366cab2df56bc77d39c461644dfd152f9f263`
- Environment/device: GitHub Actions run `30201114997`, Ubuntu runner, Temurin 17, Gradle 8.14.1
- Hypothesis: `testDebugUnitTest assembleDebug` would run the app acquisition tests and then build the APK.
- Change or command: invoked unqualified Gradle tasks from the Android root project.
- Expected result: app tests and APK build complete.
- Observed result: Android failed before app test completion because Gradle also compiled the third-party `@react-native-module/pbkdf2` sample tests. Its `UnitTest.java` referenced `Base64.DEFAULT` on the wrong `Base64` class. The app production Kotlin source itself compiled.
- Evidence/artifacts:
  - workflow run `30201114997`;
  - Android job `89791123217`;
  - build-log artifact `8631703004`;
  - failing task `:react-native-module_pbkdf2:compileDebugUnitTestJavaWithJavac`.
- Decision: do not patch `node_modules` or suppress a dependency defect. Scope verification to `:app:testDebugUnitTest :app:assembleDebug`.
- Follow-up: rerun app-only tests.

## App tests exposed Long-versus-Int expectation errors

- Baseline commit: `8c49bbf1e6186aa4d60992c2a588b459f451e142`
- Environment/device: GitHub Actions run `30201352946`
- Hypothesis: scoping Gradle tasks to the app would validate the acquisition model and build the APK.
- Change or command: changed the workflow to `:app:testDebugUnitTest :app:assembleDebug`.
- Expected result: dependency sample tests are excluded and all app tests pass.
- Observed result: production Kotlin compilation succeeded. Five JUnit assertions failed because expected values used `Int` literals while counters and monotonic timestamps are intentionally `Long` values. No behavioral mismatch was found.
- Evidence/artifacts:
  - workflow run `30201352946`;
  - build-log artifact `8631777048`;
  - failing tests in `AcquisitionStateReducerTest` and `OrdinaryClipboardBackendTest`;
  - `38 tests completed, 5 failed`.
- Decision: preserve `Long` production fields and correct test expectations to `0L`, `1L`, and Long timestamp literals.
- Follow-up: rerun the complete five-job gate.

## Android acquisition runtime and all artifacts pass CI

- Baseline commit: `9b7ca34ab9ffa165848e1d813b7caff04a546c0e`
- Environment/device: GitHub Actions run `30201613966` plus independent post-download inspection in the execution container
- Hypothesis: after correcting test value types, all acquisition tests and platform artifact jobs will pass from one head.
- Change or command:
  - ran `:app:testDebugUnitTest :app:assembleDebug`;
  - ran desktop tests on Windows and Ubuntu;
  - built Windows EXE and Linux source package;
  - downloaded Android and Windows artifacts;
  - checked embedded SHA-256 files;
  - tested APK archive integrity;
  - identified APK and PE formats.
- Expected result: all five jobs succeed and uploaded artifacts are internally consistent.
- Observed result: all five jobs succeeded. Android had 38 passing app tests, generated the debug APK, and uploaded it. Independent checksum and format checks passed.
- Evidence/artifacts:
  - workflow run `30201613966`;
  - Android artifact `8631864819`, inner file `ClipCascade-Android-clean-rebuild-debug.apk`, 146,845,313 bytes, SHA-256 `e7097304688544dd0251e3ba19f56fa3da18a51c7994937ea0877d9accf7a103`;
  - Android build-log artifact `8631864069`;
  - Windows artifact `8631829156`, inner file `ClipCascade-Windows-clean-rebuild.exe`, 57,456,724 bytes, SHA-256 `a78e8e26d486a3fff014c860149a4b88fd2eec6799b6c1ade01c2e90a84bcda0`;
  - Linux artifact `8631812042`;
  - APK ZIP integrity: passed;
  - Windows format: PE32+ x86-64 GUI executable.
- Decision: this head proves compilation, unit behavior, packaging, and artifact integrity only.
- Follow-up: expose sanitized acquisition diagnostics without adding more responsibility to the large React Native screen.

## Add an independent payload-free acquisition diagnostics screen

- Baseline commit: `9b7ca34ab9ffa165848e1d813b7caff04a546c0e`
- Environment/device: Kotlin/JVM tests and GitHub Actions; no Android device attached
- Hypothesis: adding diagnostics directly to the roughly 1,500-line React Native application screen would recreate responsibility mixing and make capture health dependent on the main UI lifecycle.
- Change or command:
  - added immutable `AcquisitionDiagnosticsSnapshot`;
  - made native and React Native diagnostics consume the same snapshot builder;
  - added pure `AcquisitionDiagnosticsFormatter` and two tests;
  - added native `AcquisitionDiagnosticsActivity`;
  - registered the activity in `AndroidManifest.xml`;
  - added static app-icon long-press shortcut `Capture status` through `res/xml/shortcuts.xml`;
  - added actions to refresh, open overlay settings, open ClipCascade, and close.
- Expected result: capture state remains visible through a small native surface without storing clipboard contents, credentials, or server URLs.
- Observed result: workflow run `30202437538` completed all five jobs successfully; Activity, manifest, shortcut resources, formatter tests, and APK assembly all passed.
- Evidence/artifacts: product head `b3ff8f72b3a09fda586aebcdadfba1f541446be1`; workflow run `30202437538`.
- Decision: keep diagnostics rendering native and independent from `App.js`; keep the actual snapshot authoritative in `ClipboardListenerModule`.
- Follow-up: add a payload-free native-to-React-Native bridge self-test.

## Add native-to-React-Native acquisition self-test

- Baseline commit: diagnostics screen milestone
- Environment/device: pure Kotlin/JUnit, React Native bundle build, GitHub Actions; no Android device attached
- Hypothesis: a synthetic test ID can prove that a native module event reached the React Native root runtime and that the matching acknowledgement returned, without reading the clipboard or entering transport code.
- Change or command:
  - added `AcquisitionSelfTestTracker` with `IDLE`, `PENDING`, `PASSED`, `TIMED_OUT`, and `EMIT_FAILED`;
  - added six deterministic tracker tests;
  - native `ClipboardListenerModule.startAcquisitionSelfTest()` emits only `{testId}` on `onAcquisitionSelfTest`;
  - added independent `AcquisitionSelfTestBridge.js` at the React Native entry point;
  - the bridge validates the ID and calls `acknowledgeAcquisitionSelfTest(testId)`;
  - the diagnostic Activity gained `Run native → React Native self-test` and timed refreshes;
  - diagnostics formatter displays status, counts, and monotonic ages;
  - neither `StartForegroundService.js` nor the clipboard outbound handler was modified for the self-test.
- Expected result: JVM tests, React Native bundle, native compilation, resources, and APK assembly pass; on-device execution may then distinguish `PASSED`, `TIMED_OUT`, and `EMIT_FAILED`.
- Observed result: workflow run `30202889590` completed all five jobs successfully. The Android suite contains exactly 46 `@Test` methods: state reducer 12, backend selection 9, ordinary backend 8, trigger deduplication 9, diagnostics formatter 2, self-test tracker 6.
- Evidence/artifacts:
  - product-code head `01394199be3e40160dcd592e8d0e5ee6a85722d1`;
  - workflow run `30202889590`;
  - Android artifact `8632223256`, inner APK 146,868,392 bytes, SHA-256 `a9c23f3d54c529f501ee92fe233cc434264edf862de5b1be82b6b86b2d434808`;
  - Android build-log artifact `8632222540`;
  - Windows artifact `8632211465`, inner EXE 57,456,724 bytes, SHA-256 `ad09902ba6e6ccc22fce7b42d9aad7f6f6755647e435be05d97d413dfc910491`;
  - Linux artifact `8632190948`, inner tarball 68,793 bytes, SHA-256 `9156f49b406e2d4acbd00007fc2194e5a3b3e66fd676541413b8b533576fff50`;
  - Android embedded checksum: passed;
  - APK ZIP integrity: passed;
  - Windows embedded checksum and PE32+ x86-64 GUI identification: passed;
  - Linux embedded checksum, gzip identification, and 53-entry enumeration: passed.
- Decision: treat `01394199be3e40160dcd592e8d0e5ee6a85722d1` as the latest fully green product-code head. The self-test proves only the event/ACK bridge when it reports `PASSED` on a real device.
- Follow-up:
  1. install this APK on a real Android device;
  2. open ClipCascade and start the service so the React Native context exists;
  3. long-press the app icon and select `Capture status`;
  4. run the native → React Native self-test and record the result;
  5. separately test real ordinary-listener and legacy logcat-overlay clipboard capture;
  6. record UI focus regressions and duplicate sends;
  7. only then implement Accessibility behind the existing backend contract.

## Claims explicitly not made

- Android background outbound reliability is not yet proven.
- A `PASSED` synthetic self-test does not prove clipboard access, WebSocket transmission, server receipt, or another device applying the clipboard.
- The current APK does not yet contain an AccessibilityService backend, Shizuku backend, ADB-assisted backend, or durable outbound queue.
- A successful native read and React Native event emission are not server delivery acknowledgements.
- Power consumption has not yet been measured.
- The debug APK is not a release-signed production artifact.
