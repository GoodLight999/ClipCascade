# Clean Rebuild Handoff

Last updated: 2026-07-26 (Asia/Tokyo)

## Canonical repository and branches

- Working repository: `GoodLight999/Trial-and-Error-ClipCascade`
- Upstream source of truth: `Sathvik-Rao/ClipCascade`
- Upstream/default branch: `main`
- Verified pristine upstream baseline SHA: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Mirror branch: `main`
- Active development branch: `clean-rebuild`
- Active draft PR: `#3`
- Latest fully green product-code head: `9b7ca34ab9ffa165848e1d813b7caff04a546c0e`
- Latest fully green workflow: `30201613966`

`main` was directly compared with the verified upstream SHA and was identical: ahead 0, behind 0, changed files 0.

The previous 529-commit patchwork PR #1 is closed and unmerged. Do not use its branch, implementation, assumptions, or documentation as a baseline.

## Read first

1. `docs/CLEAN_REBUILD_HANDOFF.md`
2. `docs/EXPERIMENT_LOG.md`
3. `docs/EXPERIMENT_LOG_2026-07-26_ANDROID_ACQUISITION.md`
4. `docs/ANDROID_ACQUISITION_ARCHITECTURE.md`
5. `docs/BUILD_REPRODUCTION.md`
6. `docs/BASELINE_ARCHITECTURE_INVENTORY.md`
7. `docs/DESKTOP_CONNECTION_STATE_MACHINE.md`
8. Draft PR #3 description and latest comments
9. Latest commit diff

This handoff and the dated Android experiment continuation are the current authority where older documents still describe pre-migration status.

## Non-negotiable rules

1. Keep `main` as an upstream mirror.
2. Work on focused, attributable commits.
3. Update this handoff in every meaningful session.
4. Record hypotheses, exact changes, failures, evidence, results, and decisions.
5. Do not stack speculative fixes.
6. Preserve the upstream server protocol and public-server compatibility.
7. Do not require root.
8. Produce APK, Windows, and Linux artifacts as build gates.
9. Never equate compilation or unit tests with runtime correctness.
10. Android background outbound reliability requires real-device tests.
11. Do not inspect the archived patchwork branch except for a narrowly named evidence-driven comparison.
12. Do not let GUI, transport, acquisition, and diagnostics maintain conflicting state.
13. Never log or export clipboard payload contents in normal diagnostics.
14. Do not patch generated `node_modules` merely to make CI green.

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

Correct delivery comes first. After correctness, reduce polling, wakeups, duplicate processing, overlays, and reconnect churn using measurements rather than guesses.

### Automatic diagnostics

One-action diagnostics must eventually verify:

- authentication and server reachability;
- WebSocket/STOMP state;
- Android acquisition path;
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

### Android before clean refactoring

- React Native `0.80.2` with Kotlin native modules.
- `StartForegroundService.js` owns transport, encryption, capture listeners, duplicate suppression, service behavior, and media/file handling.
- `ClipboardListenerModule.kt` combined a normal clipboard listener with protected `READ_LOGS` logcat monitoring.
- The logcat path watches `ClipboardService:E` and launches `ClipboardFloatingActivity`.
- `ClipboardFloatingActivity` created an overlay, temporarily took focus, independently read the clipboard, emitted to React Native, and closed.
- Acquisition health and transport health were not separately observable.

### Desktop before clean refactoring

- P2S `get_stats()` returned `None`.
- close callbacks slept for a fixed interval and recursively called `connect()`.
- the tray initialized as connected independently of transport truth.
- the low-level client could return after WebSocket open but before STOMP readiness.

## Current CI and build reproduction

Workflow: `.github/workflows/baseline-artifacts.yml`

Current trigger model:

- one `pull_request` run for product-code updates targeting `main`;
- `workflow_dispatch` for explicit rebuilds;
- `docs/**` and Markdown-only PR updates ignored;
- no simultaneous push trigger, so the same commit is not built twice.

Current jobs:

1. desktop unit tests on Ubuntu;
2. desktop unit tests on Windows;
3. Android app unit tests plus debug APK;
4. Windows standalone EXE;
5. Linux source package.

Android invokes:

```text
:app:testDebugUnitTest :app:assembleDebug
```

The unqualified `testDebugUnitTest` task is prohibited because it also compiles defective sample tests inside third-party React Native subprojects.

The upstream Android tree omitted `android/gradle.properties` while `app/build.gradle` referenced `hermesEnabled`. The clean branch restored standard React Native 0.80 properties in commit `e725f479c5075baa6d32ed465322d6c0ee06979f`. This is a build repair, not an Android reliability fix.

## Desktop P2S implementation status

Implemented and CI-green:

- authoritative `ConnectionController`;
- states `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `RECONNECT_WAIT`, `AUTH_REQUIRED`, `STOPPING`, `FATAL_ERROR`;
- immutable snapshots;
- normalized errors;
- capped exponential backoff with jitter;
- cancellable timers and stale-generation protection;
- no `sleep()` inside socket-close callbacks;
- actual STOMP `CONNECTED` plus subscription completion before connected state;
- fresh client per attempt;
- manual reconnect and automatic recovery paths;
- lost/restored notifications;
- last accepted send and last valid receive observations;
- P2S GUI and CLI controls derived from snapshots;
- explicit P2P legacy fallback;
- Windows/Linux automated tests;
- Windows EXE and Linux package compatibility.

Not yet proven:

- public-server runtime login/connect on the generated Windows EXE;
- forced network-loss recovery on Windows;
- in-process reauthentication after `AUTH_REQUIRED`;
- P2P migration;
- persistent status window;
- diagnostics bundle export;
- application-level delivery acknowledgement;
- durable outbound queue.

## Android acquisition implementation status

### Implemented and CI-green

Architecture contract:

- `ClipboardAcquisitionBackend` interface;
- stable backend IDs: `ORDINARY_LISTENER`, `ACCESSIBILITY`, `LOGCAT_OVERLAY`, `SHIZUKU`, `ADB_ASSISTED`, `MANUAL_SHARE`;
- capability state, reason code, required-user-action, coverage-class, trigger, read-result, and coordinator vocabularies;
- pure selection policy;
- pure coordinator reducer;
- trigger deduplicator model;
- backend runtime snapshots.

Ordinary listener:

- `OrdinaryClipboardBackend` owns idempotent start/stop and trigger metadata;
- Android framework registration is isolated in `AndroidClipboardChangeRegistrar`;
- start, stop, concurrent-start, stale callback, registration failure, unregistration failure, and sink failure are unit-tested.

Shared read runtime:

- `ClipboardReadRuntime` is the single current native extraction and React Native emission path;
- ordinary-listener reads and logcat-overlay reads use the same implementation;
- snapshots retain counts, timestamps, backend ID, and stable result only;
- clipboard payload contents are not retained in diagnostics;
- text, image URI, and file URI behavior preserves the existing React Native event shape.

Legacy logcat/overlay path hardening:

- `READ_LOGS` and overlay capability are re-inspected on each start request;
- logcat process and thread use a generation token;
- stopping invalidates stale callbacks;
- overlay launch, focus failure, read failure, and process state are separately observable;
- `ClipboardFloatingActivity` uses the shared read runtime and safer idempotent teardown.

Selection policy:

- healthy `AVAILABLE` backends are considered before any `DEGRADED` backend;
- configured priority breaks ties within the same capability quality;
- a degraded Shizuku path cannot displace a healthy Accessibility path.

Native diagnostic method:

```text
ClipboardListener.getAcquisitionSnapshot()
```

It currently exposes:

- whether acquisition was requested;
- ordinary listener running/start/trigger/error state;
- `READ_LOGS` and overlay permission state;
- logcat thread/process/generation state;
- latest logcat match and overlay launch timestamps;
- latest read backend/result;
- read attempt, success, and failure counts.

### Automated verification

Latest Android test result:

- 38 app tests passed;
- production Kotlin compiled;
- debug APK assembled;
- APK uploaded;
- APK embedded checksum passed;
- APK ZIP integrity passed.

### Not implemented or not proven

- no AccessibilityService backend yet;
- no Shizuku backend yet;
- no ADB-assisted backend yet;
- no adaptive coordinator starts/stops the selected background backend yet;
- no acquisition diagnostics UI yet;
- no durable outbound queue or server acknowledgement;
- no real-device foreground/background acceptance tests;
- no overlay regression tests on search bars, browsers, Amazon, or selection toolbars;
- no power measurements;
- no release signing.

## Latest fully green verification and artifacts

Workflow run: `30201613966`

Product-code head: `9b7ca34ab9ffa165848e1d813b7caff04a546c0e`

All five jobs succeeded.

| Platform | Artifact ID | Inner file | Size | SHA-256 |
|---|---:|---|---:|---|
| Android debug APK | `8631864819` | `ClipCascade-Android-clean-rebuild-debug.apk` | 146,845,313 bytes | `e7097304688544dd0251e3ba19f56fa3da18a51c7994937ea0877d9accf7a103` |
| Android build log | `8631864069` | Gradle log | See artifact | GitHub digest retained |
| Windows EXE | `8631829156` | `ClipCascade-Windows-clean-rebuild.exe` | 57,456,724 bytes | `a78e8e26d486a3fff014c860149a4b88fd2eec6799b6c1ade01c2e90a84bcda0` |
| Linux package | `8631812042` | `ClipCascade-Linux-clean-rebuild.tar.gz` | See artifact | Embedded checksum generated |

Independent checks passed:

- Android embedded SHA-256;
- APK identification;
- APK ZIP integrity;
- Windows embedded SHA-256;
- Windows PE32+ x86-64 GUI identification.

The Android APK is debug-signed and is an engineering test artifact, not a production release.

## Failed attempts that must remain understood

### Run `30201114997`

Unqualified `testDebugUnitTest` compiled third-party sample tests and failed in `@react-native-module/pbkdf2` because its test referenced `Base64.DEFAULT` on the wrong class. Decision: scope to `:app:testDebugUnitTest`; do not patch `node_modules`.

### Run `30201352946`

App production code compiled, but five JUnit assertions compared `Int` literals with intentionally `Long` counters/timestamps. Decision: preserve production Long types and correct test expectations.

Detailed evidence: `docs/EXPERIMENT_LOG_2026-07-26_ANDROID_ACQUISITION.md`.

## Exact next actions

1. Add a minimal React Native diagnostics surface that calls `getAcquisitionSnapshot()` and renders stable fields without clipboard contents.
2. Add an explicit acquisition self-test trigger that records whether a native read reached React Native.
3. Install the latest APK on a real Android device.
4. Verify ordinary foreground capture independently from legacy logcat-overlay capture.
5. Test background copy from:
   - launcher/search fields;
   - browser pages;
   - Amazon;
   - text-selection toolbars;
   - apps that emit no recognizable accessibility copy event.
6. Record whether overlay focus changes UI state, dismisses search, opens unwanted windows, or causes duplicates.
7. Use the results to classify the existing path as healthy, degraded, or blocked per device.
8. Implement Accessibility only behind the existing backend contract and capability probe.
9. Add Shizuku only after capability detection, permission flow, reboot recovery, and post-setup self-test are specified.
10. Design the durable outbound queue separately from acquisition; never treat native read success as server delivery acknowledgement.
11. Perform Windows public-server and forced-network-loss smoke tests.

## Definition of a valid continuation

A new thread must be able to identify from the read-first documents:

- exact upstream baseline;
- active branch and PR;
- latest fully green product-code head;
- completed desktop and Android implementation;
- failed attempts and evidence;
- current artifacts and hashes;
- claims that remain unverified;
- exact next action.

No continuation should require reading the archived patchwork branch.
