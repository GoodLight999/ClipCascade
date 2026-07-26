# Clean Rebuild Handoff

Last updated: 2026-07-26 (Asia/Tokyo)

## Canonical repository and refs

- Repository: `GoodLight999/Trial-and-Error-ClipCascade`
- Upstream: `Sathvik-Rao/ClipCascade`
- Pristine upstream/mirror baseline: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Mirror branch: `main`
- Active branch: `clean-rebuild`
- Active draft PR: `#3`
- Latest fully green product-code/pipeline head: `a94b830fb954d09fc742b39833cebd5915988566`
- Latest fully green workflow: `30203690726`

`main` was verified identical to the upstream baseline: ahead 0, behind 0, changed files 0.

The prior 529-commit patchwork PR #1 is closed and unmerged. Do not use its branch, implementation, assumptions, or documentation as a baseline.

## Read first

1. `docs/CLEAN_REBUILD_HANDOFF.md`
2. `docs/EXPERIMENT_LOG.md`
3. `docs/EXPERIMENT_LOG_2026-07-26_ANDROID_ACQUISITION.md`
4. `docs/EXPERIMENT_LOG_2026-07-26_ANDROID_STANDALONE.md`
5. `docs/ANDROID_ACQUISITION_ARCHITECTURE.md`
6. `docs/BUILD_REPRODUCTION.md`
7. `docs/BASELINE_ARCHITECTURE_INVENTORY.md`
8. `docs/DESKTOP_CONNECTION_STATE_MACHINE.md`
9. Draft PR #3 description and recent comments
10. Latest product-code diff

Where older documents describe pre-migration status, this handoff and the dated experiment logs are authoritative.

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
11. Never retain clipboard payloads, credentials, or secret-bearing URLs in normal diagnostics.
12. Acquisition success is not transport acknowledgement; transport acceptance is not server delivery acknowledgement.
13. Do not inspect archived patchwork except for narrowly named evidence-driven comparison.
14. Never distribute a React Native debug APK as a standalone test artifact unless Metro is deliberately part of the test.
15. A user-installable Android artifact must pass an APK-internal check for `assets/index.android.bundle`.

## Current CI gate

Workflow: `.github/workflows/baseline-artifacts.yml`

Triggers:

- one pull-request run for product-code updates targeting `main`;
- `workflow_dispatch` for explicit rebuilds;
- docs-only changes ignored;
- no simultaneous push trigger.

Jobs:

1. desktop unit tests on Ubuntu;
2. desktop unit tests on Windows;
3. Android app JVM tests plus standalone bundled APK;
4. Windows standalone EXE;
5. Linux source package.

Android command:

```text
:app:testDebugUnitTest :app:assembleStandalone
```

The Android `standalone` build type:

- inherits release runtime semantics;
- uses debug signing and remains an engineering artifact;
- is excluded from React Native `debuggableVariants`;
- packages the Hermes JS bundle;
- does not require Metro.

CI requires:

- exact APK entry `assets/index.android.bundle`;
- APK ZIP integrity;
- embedded SHA-256.

Unqualified `testDebugUnitTest` is prohibited because it compiles defective sample tests in third-party React Native subprojects.

## Desktop P2S status

Implemented and CI-green:

- authoritative `ConnectionController`;
- states `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `RECONNECT_WAIT`, `AUTH_REQUIRED`, `STOPPING`, `FATAL_ERROR`;
- immutable snapshots and normalized errors;
- capped exponential backoff with jitter;
- cancellable timers and stale-generation protection;
- no socket-callback sleeps or recursive reconnect;
- actual STOMP `CONNECTED` plus subscription completion before connected state;
- fresh client per attempt;
- manual reconnect and automatic recovery;
- lost/restored notifications;
- last accepted send and valid receive observations;
- GUI/CLI controls derived from snapshots;
- explicit P2P legacy fallback;
- Windows and Ubuntu tests;
- Windows EXE and Linux package gates.

Not yet proven or implemented:

- public-server runtime test of generated Windows EXE;
- forced network-loss recovery on Windows;
- in-process reauthentication after `AUTH_REQUIRED`;
- P2P migration to the same snapshot contract;
- persistent status window;
- diagnostics bundle export;
- application-level delivery acknowledgement;
- durable outbound queue.

## Android acquisition status

Implemented and CI-green:

- `ClipboardAcquisitionBackend` contract;
- backend IDs `ORDINARY_LISTENER`, `ACCESSIBILITY`, `LOGCAT_OVERLAY`, `SHIZUKU`, `ADB_ASSISTED`, `MANUAL_SHARE`;
- capability, reason, required action, coverage, trigger, read-result, and coordinator vocabularies;
- pure backend selection policy and coordinator reducer;
- trigger deduplication model;
- healthy `AVAILABLE` backend selected before any `DEGRADED` backend;
- `OrdinaryClipboardBackend` with idempotent and concurrency-safe start/stop;
- one shared `ClipboardReadRuntime` for ordinary and overlay reads;
- legacy logcat process/thread generation ownership;
- stale callback invalidation;
- separate permission, match, overlay, focus, read, and process diagnostics;
- no clipboard content retained in diagnostics.

Exactly 46 Android JVM tests pass:

| Surface | Count |
|---|---:|
| Acquisition state reducer | 12 |
| Backend selection | 9 |
| Ordinary listener | 8 |
| Trigger deduplication | 9 |
| Diagnostics formatter | 2 |
| Self-test tracker | 6 |
| **Total** | **46** |

## Acquisition diagnostics and self-test

Implemented and CI-green:

- immutable `AcquisitionDiagnosticsSnapshot`;
- native and React Native diagnostics use one snapshot builder;
- pure formatter with tests;
- independent native `AcquisitionDiagnosticsActivity`;
- launcher long-press shortcut `Capture status`;
- Refresh, self-test, overlay settings, open app, and close actions;
- payload-free native → React Native self-test;
- states `IDLE`, `PENDING`, `PASSED`, `TIMED_OUT`, `EMIT_FAILED`;
- five-second deterministic timeout;
- test-ID matching and stale ACK rejection;
- independent `AcquisitionSelfTestBridge.js` initialized from `index.js`.

Interpretation on device:

- `PASSED`: native test event reached the active React Native root runtime and matching ACK returned.
- `TIMED_OUT`: no matching ACK returned within five seconds.
- `EMIT_FAILED`: native event emission failed.

`PASSED` does not prove real clipboard capture, transport, server receipt, or remote clipboard application.

## Latest verified artifacts

Workflow: `30203690726`

Head: `a94b830fb954d09fc742b39833cebd5915988566`

All five jobs succeeded.

| Platform | Artifact ID | Inner file | Size | SHA-256 |
|---|---:|---|---:|---|
| Android standalone APK | `8632482778` | `ClipCascade-Android-clean-rebuild-standalone.apk` | 93,574,655 bytes | `ca7ee41f95f729879a5298bdc2b6e413f0f2086cbc922205e06468d7878f471b` |
| Android build log | `8632482104` | Gradle log | see artifact | GitHub digest retained |
| Windows EXE | `8632434975` | `ClipCascade-Windows-clean-rebuild.exe` | 57,456,724 bytes | `36da95c51e473b8bf33677f81142ea70bdfcd81a968e24e8a0be4878e11ce103` |
| Linux package | `8632420551` | `ClipCascade-Linux-clean-rebuild.tar.gz` | 68,791 bytes | `6fadbb46751fe0e714f85d9118c69c2ecc7ea14c0d8a76b2d85c2eed0114ba6b` |

Independent checks passed:

- Android embedded SHA-256;
- APK identification;
- exact `assets/index.android.bundle` entry;
- APK ZIP integrity;
- Windows embedded SHA-256 and PE32+ x86-64 GUI identification;
- Linux embedded SHA-256, gzip identification, and 53-entry enumeration.

The APK has release runtime behavior but debug signing. It is a standalone engineering artifact, not a production release.

## Failures that must remain understood

### Run `30201114997`

Unqualified `testDebugUnitTest` compiled defective `@react-native-module/pbkdf2` sample tests. CI was scoped to `:app:testDebugUnitTest`; generated dependencies were not patched.

### Run `30201352946`

Five JUnit expectations used `Int` literals against intentionally `Long` counters/timestamps. Production types were preserved; tests were corrected.

### Debug APK required Metro

Workflow `30202889590` used `assembleDebug`. React Native deliberately skipped JS bundling, so the installed APK displayed `Unable to load script` and requested Metro/`adb reverse`.

This was a packaging decision error, not a device setup error. The debug APK is superseded for ordinary installation.

Correction:

- retain debug for deliberate Metro development;
- distribute only the `standalone` variant;
- fail CI if the packaged bundle is missing.

Detailed evidence:

- `docs/EXPERIMENT_LOG_2026-07-26_ANDROID_ACQUISITION.md`
- `docs/EXPERIMENT_LOG_2026-07-26_ANDROID_STANDALONE.md`

## Not implemented or not proven

- AccessibilityService backend;
- Shizuku backend;
- ADB-assisted backend;
- adaptive coordinator that starts/stops selected background backends;
- durable outbound queue and server acknowledgement;
- successful launch of the new standalone APK on the user's device;
- real-device foreground/background acquisition acceptance;
- overlay focus-regression and duplicate-send tests;
- power measurements;
- production release signing.

## Exact next actions

1. Replace the old debug APK with the latest standalone APK.
2. Launch without Metro, USB, or `adb reverse`; verify the React Native main screen loads.
3. Start the ClipCascade service.
4. Long-press the app icon and select `Capture status`.
5. Run the native → React Native self-test and record its state.
6. Test ordinary foreground capture separately.
7. Test legacy logcat/overlay background capture from launcher/search, browser, Amazon, selection toolbars, and apps with no accessibility copy event.
8. Record focus changes, dismissed UI, unwanted windows, and duplicate sends.
9. Classify the existing path per device as healthy, degraded, or blocked.
10. Implement Accessibility only behind the existing backend contract and capability probe.
11. Add Shizuku only after detection, permission, reboot recovery, guided setup, and post-setup self-test are specified.
12. Design durable outbound queueing separately from acquisition.
13. Perform Windows public-server and forced-network-loss smoke tests.

## Valid continuation criterion

A new thread must recover, without archived patchwork code:

- exact upstream baseline;
- active branch and PR;
- latest green head and workflow;
- completed desktop and Android work;
- all failed attempts and decisions;
- current standalone artifacts and hashes;
- unverified claims;
- exact next action.
