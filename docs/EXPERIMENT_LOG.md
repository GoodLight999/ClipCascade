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

### Current result

Source implementation is committed on `stability-recovery`.

### Verification status

Not yet compiled. Not yet installed. No runtime claim is valid yet.

### Required next evidence

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

## Next experiment queue

1. `BUILD-001`: compile A11Y-001 and produce a bundled installable APK.
2. `DEVICE-001`: real-device acceptance matrix for foreground, Accessibility, and READ_LOGS paths.
3. `SHIZUKU-001`: verify current official Shizuku API and design a preferred stable capture path without changing transport.
4. `DESKTOP-001`: review and selectively recover the independent Windows connection-controller subset from `a94b830fb954d09fc742b39833cebd5915988566`.
