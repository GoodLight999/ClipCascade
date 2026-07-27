# ClipCascade Experiment Log

Append-only record for `GoodLight999/Trial-and-Error-ClipCascade`.

Do not rewrite failures as if they never happened. Correct inaccurate conclusions explicitly and preserve the evidence needed to avoid repeating work.

---

## 2026-07-27 — Repository recovery boundary

### User requirement restated

- Do not reinvent the wheel.
- Never forget the original Android, desktop, setup, diagnostic, server-compatibility, power, and artifact requirements.
- Keep handoff documentation current enough for immediate continuation after any context-window limit.
- Inspect the named upstream, Go-fork history, and OctoClip documentation before implementing equivalent functionality.

### Canonical state

- Baseline and `main`: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Active branch created from that exact SHA: `stability-recovery`
- Repository reset was already complete and must not be repeated.
- Failed PR `#3` remains closed and unmerged.

### Prior-work classification

- The failed PR produced independently reviewable Windows connection-controller and reconnect work, but it did not deliver the required Android product functionality.
- Its Android diagnostics and speculative acquisition abstractions are not an active baseline and must not be restored.
- The Windows subset may be recovered only file-by-file after review from product-code commit `a94b830fb954d09fc742b39833cebd5915988566`.

### Decision

Begin from upstream code and extend existing runtime paths. Do not create a parallel transport engine or another clean-rebuild framework.

---

## 2026-07-27 — Reference inspection before Android implementation

### Sources inspected

1. Current upstream Android source at baseline `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`.
2. Deleted Android implementation history in `wuxinkami/ClipCascade_go_fork`, especially commit `084616111aa993c77c9f293811534253b7d3d3f9`.
3. OctoClip Accessibility and Shizuku documentation.
4. Official Android clipboard, Accessibility, and background-execution documentation.

### Existing upstream mechanisms confirmed

- React Native/Notifee foreground service already owns transport.
- `ClipboardListenerModule` already emits `onClipboardChange`.
- `StartForegroundService.js` already feeds that event into the existing `sendClipBoard` path.
- JavaScript already hashes content for duplicate suppression.
- Upstream already has ordinary listener, READ_LOGS trigger, overlay clipboard-read activity, share sheet, boot receiver, and battery/ADB setup text.

### Go-fork mechanisms confirmed

The removed native Android implementation used:

- a foreground service;
- Accessibility as a trigger;
- an invisible overlay to gain clipboard-read eligibility;
- debounce;
- a last-written value to suppress self-loops;
- permission/setup guidance.

### Rejected mechanism

The Go-fork Accessibility service reacted to generic `TYPE_VIEW_CLICKED` and `TYPE_VIEW_TEXT_SELECTION_CHANGED` events. That policy would create frequent false triggers, unnecessary wakeups, and a credible risk of reproducing the reported Amazon/search-field interference.

### Accepted mechanism

Reuse the existing upstream overlay and send path, but add a conservative Accessibility trigger limited to high-confidence copy signals.

---

## 2026-07-27 — Experiment A11Y-001: conservative Accessibility trigger

### Hypothesis

Android background clipboard capture can be improved without creating a second transport implementation by using Accessibility only to detect a likely copy action, then invoking upstream's existing overlay read and React Native send path.

### Safety constraints

- No generic-click trigger.
- No generic text-selection trigger.
- No Accessibility window-content retrieval.
- Ignore events from ClipCascade itself.
- Do nothing unless the existing ClipCascade foreground runtime is active.
- Do nothing unless overlay permission is granted.
- Do not log clipboard payloads.
- Preserve upstream JavaScript duplicate suppression and transport protocol.

### Source changes

- `ClipboardListenerModule.kt`
  - added process-wide runtime-active state;
  - retained ordinary listener and READ_LOGS behavior.
- `ClipCascadeAccessibilityService.kt`
  - added high-confidence copy trigger;
  - accepts `ACTION_COPY`, copy-labelled clicks, and copied-confirmation notification/announcement text;
  - applies 600 ms debounce and 250 ms clipboard-write settling delay.
- `ClipboardFloatingActivity.kt`
  - removed `FLAG_ACTIVITY_CLEAR_TASK`;
  - added no-history/no-animation flags;
  - added overlay-permission and active-runtime checks;
  - made overlay cleanup fail safely.
- `BackgroundSetupActivity.kt`
  - reports Accessibility, overlay, READ_LOGS, battery-exemption, and foreground-runtime state;
  - opens relevant Android settings;
  - copies the existing ADB fallback commands;
  - uses platform theme colors.
- `accessibility_service_config.xml`
  - listens only for clicked, notification-state, and announcement events;
  - does not retrieve window content.
- `shortcuts.xml`
  - adds a launcher long-press setup shortcut.
- `AndroidManifest.xml` and `strings.xml`
  - register the service/activity and user-facing text.

### Current result at source-implementation checkpoint

Source implementation was committed on `stability-recovery`.

### Verification status at source-implementation checkpoint

Not yet compiled. Not yet installed. No runtime claim was valid at this checkpoint.

### Required next evidence at source-implementation checkpoint

1. Android resource/Kotlin compilation.
2. Standalone APK containing the JavaScript bundle.
3. Foreground and background copy tests.
4. Amazon, launcher, browser, search-field, and selection-toolbar regression tests.
5. Duplicate-send observations.
6. Battery/wakeup observations.

### Failure conditions

A11Y-001 must be revised or rejected if it:

- interferes with input focus or dismisses UI;
- fires on ordinary clicks;
- causes repeated duplicate sends;
- cannot access the clipboard reliably enough to improve the upstream path;
- materially increases battery drain;
- prevents the existing ADB/READ_LOGS path from operating.

---

## Next experiment queue at A11Y-001 source checkpoint

1. `BUILD-001`: compile A11Y-001 and produce a bundled installable APK.
2. `DEVICE-001`: real-device acceptance matrix for foreground, Accessibility, and READ_LOGS paths.
3. `SHIZUKU-001`: verify current official Shizuku API and design a preferred stable capture path without changing transport.
4. `DESKTOP-001`: review and selectively recover the independent Windows connection-controller subset from `a94b830fb954d09fc742b39833cebd5915988566`.

---

## 2026-07-27 — Experiment BUILD-001: bundled Android APK

### Goal

Compile A11Y-001, run app-scoped Android tests, package a Metro-independent APK, and verify that the JavaScript bundle is actually inside the APK.

### Packaging reused instead of reinvented

Recovered only the previously successful packaging rule from the archived development line:

- `standalone` build type inherits release runtime semantics;
- `standalone` uses debug signing for engineering installation;
- only `debug` is a React Native debuggable variant;
- CI requires the exact APK entry `assets/index.android.bundle`;
- CI verifies APK ZIP integrity and records SHA-256.

No archived Android feature code or diagnostics UI was restored.

### Attempt 1 — workflow run `30209073143`

Result: failed before Android source compilation.

Observed error:

- `app/build.gradle` referenced `hermesEnabled`;
- the upstream-aligned repository did not contain `ClipCascade_Mobile/src/android/gradle.properties`;
- Gradle raised `MissingPropertyException` for `hermesEnabled`.

Correction:

- added the standard React Native 0.80 project properties;
- `newArchEnabled=true`;
- `hermesEnabled=true`;
- retained AndroidX and the existing supported ABI set.

This was a concrete restoration of required template configuration, not a feature redesign.

### Attempt 2 — workflow run `30209210999`

Result: failed during React Native CMake autolinking.

Observed error:

- direct `:app:assembleStandalone` ran before generated JNI/codegen directories existed for async-storage, clipboard, and document picker;
- CMake failed on missing `build/generated/source/codegen/jni` directories.

Rejected correction:

- do not disable the new architecture merely to bypass the error;
- do not patch generated `node_modules` or CMake files.

Accepted correction:

- reuse the exact build ordering previously proven in CI;
- run `:app:testDebugUnitTest` before `:app:assembleStandalone`;
- the app-scoped test task generates the required code before the standalone native build.

### Attempt 3 — workflow run `30209366320`

Head SHA: `613006702d22444449ce69500934c08e8a953ce0`

Result: passed.

Verified steps:

- repository checkout;
- Node and Java setup;
- `npm ci`;
- app-scoped debug unit-test task and code generation;
- standalone APK build;
- exact `assets/index.android.bundle` entry present;
- APK ZIP integrity;
- APK artifact upload;
- build-log artifact upload.

### Artifact evidence

- APK artifact ID: `8634074763`
- Build-log artifact ID: `8634074053`
- File: `ClipCascade-Android-stability-standalone.apk`
- Size: `93,544,663` bytes
- SHA-256: `b2bca637638a829c8c594df567ca6951f56972ffc70b8c7d03fef326bed4e857`
- Signing/status: debug-signed engineering artifact, not production release

The artifact was downloaded independently after Actions completion. Its SHA-256, APK identification, exact JavaScript-bundle entry, and ZIP integrity were rechecked outside the workflow.

### BUILD-001 conclusion

BUILD-001 passed. A11Y-001 is build-verified but remains device-unverified.

No claim is made yet about:

- actual background clipboard capture;
- Amazon/search-field safety;
- duplicate-send behavior;
- battery impact;
- server or remote-device delivery.

---

## Current experiment queue

1. `DEVICE-001`: install the latest APK and test foreground, Accessibility, and READ_LOGS paths separately.
2. `DEVICE-002`: test launcher drawer, Amazon, browser, search fields, and selection toolbars for focus/input regressions.
3. `DEVICE-003`: record duplicate-send and battery/wakeup behavior.
4. `SHIZUKU-001`: use official Shizuku source/API/demo and OctoClip's documented flow to design the preferred stable path without changing transport.
5. `DESKTOP-001`: selectively recover and retest the Windows connection controller; do not restore archived PR #3 wholesale.

---

## 2026-07-27 — OUTBOX-001: persistent P2S text outbox

Detailed record: `docs/EXPERIMENT_LOG_2026-07-27_P2S_OUTBOX.md`.

Result:

- existing server protocol and STOMP destinations retained;
- offline P2S text is now persisted, bounded, replayed after subscription, and removed after matching server echo;
- connection loss and shutdown release in-flight text for retry;
- own queued echoes do not roll the Android clipboard backward;
- 3 JavaScript suites / 19 tests passed;
- Android workflow `30248168083` and desktop workflow `30248168084` passed on head `f35ebffba8b99f783b20b0bec2e4bc16a0421f1b`;
- latest APK SHA-256: `9810be35788fbcad32cf34986f0b19bcb324db1f40a9766024c298aec8e32b2d`.

Runtime/device acceptance remains required. Image/file durability is not implemented.

---

## 2026-07-27 — OUTBOX-UI-001: queue status on the existing connection page

Detailed record: `docs/EXPERIMENT_LOG_2026-07-27_OUTBOX_STATUS_UI.md`.

Result:

- existing 300 ms UI poller reused;
- payload-free count/state/bytes/attempt/drop display added for P2S;
- native bridge JSON-string shape identified and handled safely;
- 4 JavaScript suites / 26 tests passed;
- Android workflow `30249557589` and desktop workflow `30249557577` passed on head `60c71a7d77e2980d2f2e35c8f325c4c22d37c4cf`;
- latest APK SHA-256: `614f6fd7d2bcecc96ceba331601ae9d84f6c475047d4301fa5f099286ad0893b`.

Real-device rendering and live state changes remain unproven.

---

## 2026-07-27 — DIAGNOSTIC-REPORT-001: shareable real-state report

Detailed record: `docs/EXPERIMENT_LOG_2026-07-27_DIAGNOSTIC_REPORT.md`.

Result:

- existing setup screen now shares a real-state diagnostic report through Android Sharesheet;
- report combines capability, capture, connection, and P2S outbox metadata without payload fields;
- URLs and email addresses in free-form errors are redacted;
- JVM diagnostic tests, existing Android tests, standalone APK, bundle, ZIP integrity, and artifact upload passed;
- Android workflow `30250829837` and desktop workflow `30250829830` passed on product head `9277b67d8c0797014e17489a59d3c4aca64e97eb`;
- latest APK SHA-256: `2af9f94dff4f8447001378da780591b970d8adfe0698357f42ef6eb826fbd785`.

Sharesheet/runtime readability and an active guided end-to-end test remain unproven.

---

## 2026-07-27 — P2S-RETRY-001: bounded missing-echo retry and observability

Detailed record: `docs/EXPERIMENT_LOG_2026-07-27_P2S_RETRY_BACKOFF.md`.

Result:

- replaced the fixed repeated 30-second missing-echo retry with a bounded exponential policy;
- deterministic no-jitter sequence is `30s → 60s → 120s → 240s → 480s → 600s`, capped thereafter;
- default symmetric jitter is plus or minus 20 percent;
- the existing outbox persists `nextAttemptAt` and preserves it through disconnect, release, process restart, and reconnect;
- reconnect waits for a persisted future deadline rather than immediately retrying;
- no second transport, server endpoint, retry poller, queue owner, or acknowledgement protocol was introduced;
- the existing P2S queue line now shows `Retry in: <seconds>s` for a valid future deadline;
- the native diagnostic report now includes the same payload-free `headNextAttemptAt` state;
- the one-use write workflow was removed in commit `6e61d35073a49bff6f4970a664b50c159a1cafc3` and no temporary write workflow remains.

Latest product-code verification:

- product-code head: `829f872c684c59f16a78bbe283b9e1260e20fce9`;
- Android workflow: `30257268532` — success;
- desktop workflow: `30257268751` — success;
- JavaScript: 5 suites / 36 tests passed;
- Gradle: `BUILD SUCCESSFUL in 3m 41s`, 505 tasks executed;
- APK artifact ID: `8649504009`;
- APK size: `93,617,791` bytes;
- APK SHA-256: `9f8fefd4d32892e891e763590a443d4dbcc20534b7cba4260fabe8489589f106`;
- Windows artifact ID: `8649445837`;
- Windows size: `57,456,040` bytes;
- Windows SHA-256: `871fac5fe6c4bade7fc58b65deb7301805c91078f3699d6d8a987e5a6c8b5c0f`;
- Linux artifact ID: `8649406597`;
- Linux size: `60,405` bytes;
- Linux SHA-256: `7c6f780d170b4058430dd474b61514e69328b17ae2ee44c25745d5a41ddcaef7`.

Independent inspection confirmed APK ZIP integrity, 538 APK entries, exact bundled JavaScript, retry/protocol markers, PE32+ x86-64 EXE format, 59-entry Linux tar integrity, and embedded checksum agreement.

Real-device/public-server timing, restart preservation, countdown rendering, battery impact, and actual missing-echo behavior remain unproven.
