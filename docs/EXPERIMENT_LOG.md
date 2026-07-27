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

