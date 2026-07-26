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
- Observed result: all five jobs succeeded. Android reported 38 passing app tests, generated the debug APK, and uploaded it. Independent checksum and format checks passed.
- Evidence/artifacts:
  - workflow run `30201613966`;
  - Android artifact `8631864819`, inner file `ClipCascade-Android-clean-rebuild-debug.apk`, 146,845,313 bytes, SHA-256 `e7097304688544dd0251e3ba19f56fa3da18a51c7994937ea0877d9accf7a103`;
  - Android build-log artifact `8631864069`;
  - Windows artifact `8631829156`, inner file `ClipCascade-Windows-clean-rebuild.exe`, 57,456,724 bytes, SHA-256 `a78e8e26d486a3fff014c860149a4b88fd2eec6799b6c1ade01c2e90a84bcda0`;
  - Linux artifact `8631812042`;
  - GitHub artifact digests are retained by Actions;
  - APK ZIP integrity: passed;
  - Windows format: PE32+ x86-64 GUI executable.
- Decision: treat `9b7ca34ab9ffa165848e1d813b7caff04a546c0e` as the latest fully green product-code head. This proves compilation, unit behavior, packaging, and artifact integrity only.
- Follow-up:
  1. install the APK on a real Android device;
  2. call and display `getAcquisitionSnapshot()` in the app diagnostics UI;
  3. verify ordinary foreground capture and legacy logcat-overlay capture separately;
  4. test background copies from search fields, browser pages, Amazon, selection toolbars, and apps that emit no accessibility copy event;
  5. verify no overlay focus regression or duplicate send;
  6. only then implement the Accessibility backend behind the existing contract;
  7. add Shizuku after the capability probe and guided setup interfaces are stable.

## Claims explicitly not made

- Android background outbound reliability is not yet proven.
- The current APK does not yet contain an AccessibilityService backend, Shizuku backend, or durable outbound queue.
- A successful native read and React Native event emission are not server delivery acknowledgements.
- Power consumption has not yet been measured.
- The debug APK is not a release-signed production artifact.
