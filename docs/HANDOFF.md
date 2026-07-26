# ClipCascade Stability Recovery Handoff

Last updated: 2026-07-27 (Asia/Tokyo)

## Read this first

This file is the canonical continuation document for `GoodLight999/Trial-and-Error-ClipCascade`.
A new thread must read this file before inspecting or changing code.

## Canonical repository state

- Repository: `GoodLight999/Trial-and-Error-ClipCascade`
- Upstream: `Sathvik-Rao/ClipCascade`
- Upstream-aligned baseline SHA: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Mirror branch: `main`
- Active development branch: `stability-recovery`
- Active draft PR: `#4`
- Latest green product head: `613006702d22444449ce69500934c08e8a953ce0`
- Latest green Android workflow: `30209366320`
- Active branch started from the exact baseline SHA above.

The repository reset is already complete. **Do not reset, reconstruct, or re-baseline the repository again.**

## Permanent operating rules

1. **No wheel reinvention.** Before designing a mechanism, inspect the upstream implementation and the named references below.
2. **Never return to completed setup work.** Check this handoff and the experiment log before starting work.
3. Keep `main` aligned with upstream. Perform product work only on a focused development branch.
4. Prefer minimal extensions to existing runtime paths over parallel frameworks, speculative abstractions, or duplicate state owners.
5. Do not stack unverified fixes. Build and test each focused change before expanding it.
6. Compilation and unit tests are not runtime proof. Android background reliability requires real-device evidence.
7. Every meaningful hypothesis, source, implementation, failure, result, artifact, and decision must be recorded in `docs/EXPERIMENT_LOG.md`.
8. Update this handoff whenever the branch head, completed work, known failures, artifacts, or exact next action changes.
9. Preserve the Sathvik-Rao server protocol and public-server compatibility.
10. Do not require root.
11. Do not inspect archived patchwork except for a narrowly named, evidence-driven recovery task.
12. Do not restore failed Android diagnostics scaffolding from archived PRs.
13. APK and EXE delivery is part of the product requirement, not optional CI decoration.

## Product requirements that must not be forgotten

### Android

- Make Android-to-server clipboard sharing reliable while ClipCascade is in the background.
- Any practical non-root mechanism may be used: ordinary listener, Accessibility, Shizuku, guided ADB, or a safe combination.
- Root must not be required.
- Preserve functionality while reducing unnecessary wakeups, polling, overlays, and battery drain.
- Provide a setup guide usable by a non-technical user.
- Shizuku setup, if used, must be automated or semi-automated and explain restart recovery.
- Provide an automatic diagnostic mode that can be run by opening the app and tapping through clear steps.
- Do not interfere with drawers, Amazon search fields, browser search fields, text selection, or ordinary touch/focus behavior.
- Prevent duplicate sends and distinguish capture, transport acceptance, server delivery, and remote application.

### Windows and Linux

- Add meaningful runtime GUI/status after login.
- Show connection, reconnect, last send/receive, and actionable errors.
- Implement reliable automatic and manual reconnect without blocking socket callbacks.
- Generate Windows EXE and Linux artifacts.

### Compatibility and delivery

- Do not change the server protocol.
- Continue to work with Sathvik-Rao's existing server, including the publicly available server.
- Generate installable APK and EXE artifacts.

## Required references

Inspect these before implementing the corresponding feature:

1. Upstream source: `https://github.com/Sathvik-Rao/ClipCascade`
2. Removed Go/Android implementation history: `https://github.com/wuxinkami/ClipCascade_go_fork`
3. OctoClip Shizuku documentation: `https://docs.octoclip.app/features/source/background-monitoring/android/shizuku`
4. OctoClip Accessibility documentation: `https://docs.octoclip.app/features/source/background-monitoring/android/accessibility`
5. Official Android documentation for clipboard, Accessibility, foreground services, and background activity restrictions.
6. Official Shizuku source, API guide, demo, and user setup documentation before Shizuku implementation.

## Archived development lines

- Prior patchwork PR `#1`: closed and unmerged. Do not use as a baseline.
- Failed clean-rebuild PR `#3`: closed and unmerged. Do not use its Android diagnostics UI, acquisition vocabulary, build artifacts, or handoff documents as a baseline.

A narrow exception exists for independently reviewable Windows reconnect code from product-code commit:

- `a94b830fb954d09fc742b39833cebd5915988566`

Only the desktop connection controller, retry policy, STOMP readiness changes, tray projection, and their tests may be considered for selective recovery after review. Do not restore the PR wholesale.

## Existing upstream Android path being extended

The current upstream application already contains:

- a React Native/Notifee foreground service that owns WebSocket/P2P transport;
- an ordinary `ClipboardManager` listener;
- an existing `onClipboardChange` event that feeds the existing `sendClipBoard` function;
- existing content hashing/deduplication in JavaScript;
- a READ_LOGS-based trigger path;
- `ClipboardFloatingActivity`, which temporarily uses an overlay to read the clipboard;
- a share-sheet/PROCESS_TEXT fallback;
- battery and ADB setup instructions;
- boot and headless service infrastructure.

The recovery branch extends this path rather than creating a second transport or connection engine.

## Reference archaeology result

The deleted Android implementation in `wuxinkami/ClipCascade_go_fork` included:

- a foreground background service;
- an Accessibility trigger;
- permission guidance;
- an invisible overlay clipboard-read mechanism;
- debounce and self-loop suppression.

Its Accessibility service also triggered on generic clicks and text-selection changes. That broad trigger policy is intentionally **not** reused because it can cause excess wakeups and UI/focus regressions.

## Active implementation unit: conservative Accessibility trigger

Branch: `stability-recovery`

Implemented and Android-build verified:

- process-wide visibility of whether the existing React Native clipboard listener runtime is active;
- a conservative `ClipCascadeAccessibilityService`;
- triggers limited to high-confidence copy actions, copy-labelled clicks, and copied-confirmation notifications/announcements;
- no trigger on generic clicks or generic text-selection changes;
- service ignores ClipCascade's own events;
- debounce and clipboard-write settling delay;
- trigger is disabled when the existing ClipCascade foreground runtime is inactive;
- trigger is disabled when overlay permission is missing;
- reuse of existing `ClipboardFloatingActivity` and existing JavaScript send/dedup path;
- safer overlay activity intent flags; removed `CLEAR_TASK` behavior;
- overlay activity now fails closed when permission or React Native runtime is unavailable;
- native `BackgroundSetupActivity` with Accessibility, overlay, READ_LOGS, battery-exemption, and foreground-runtime status;
- buttons to open system settings, copy the existing ADB fallback commands, refresh status, and return to ClipCascade;
- launcher long-press shortcut for the setup screen;
- default Android theme colors instead of a custom diagnostic panel, avoiding the previous dark-mode contrast failure;
- Metro-free `standalone` build variant using release runtime semantics and debug signing;
- CI gate for app tests, APK build, embedded JavaScript bundle, APK ZIP integrity, artifact upload, and SHA-256.

Build verification does **not** prove real-device background capture or UI safety.

## Latest verified Android artifact

- Workflow run: `30209366320`
- Head SHA: `613006702d22444449ce69500934c08e8a953ce0`
- APK artifact ID: `8634074763`
- Build-log artifact ID: `8634074053`
- File: `ClipCascade-Android-stability-standalone.apk`
- Size: `93,544,663` bytes
- SHA-256: `b2bca637638a829c8c594df567ca6951f56972ffc70b8c7d03fef326bed4e857`
- Exact APK entry `assets/index.android.bundle`: present
- APK ZIP integrity: passed
- Signing/status: debug-signed engineering artifact; not a production release

## Build failures retained as evidence

### Run `30209073143`

`app/build.gradle` referenced `hermesEnabled`, but the upstream-aligned tree did not contain `android/gradle.properties`. Added the standard React Native 0.80 properties, including `newArchEnabled=true` and `hermesEnabled=true`.

### Run `30209210999`

Direct `assembleStandalone` reached React Native CMake autolinking before generated JNI code existed. Reused the previously successful ordering: `:app:testDebugUnitTest` followed by `:app:assembleStandalone`. This generates app code before the standalone native build without patching dependencies.

### Run `30209366320`

All Android build, test, bundle-presence, ZIP-integrity, and artifact-upload steps passed.

## Explicitly not yet implemented or proven

- Successful launch of the latest APK on the user's Android device.
- Real-device capture in foreground or background.
- Amazon, launcher drawer, browser, selection toolbar, and search-field regression tests.
- Duplicate-send acceptance tests.
- Power measurement.
- Shizuku integration.
- Guided Shizuku activation and reboot recovery.
- Durable outbound queue or server-level delivery acknowledgement.
- Automatic end-to-end diagnostic mode.
- Selective Windows reconnect recovery on the active branch.
- Windows EXE or Linux artifact from the active branch.

## Exact next actions

1. Install `ClipCascade-Android-stability-standalone.apk` on a real Android device.
2. Open the launcher long-press `バックグラウンド設定` shortcut and verify readable light/dark rendering.
3. Start the existing ClipCascade service and enable overlay plus the ClipCascade Accessibility service.
4. Test ordinary foreground capture, Accessibility-triggered background capture, and existing READ_LOGS/ADB capture separately.
5. Test launcher drawer, Amazon, browser, selection toolbars, and search fields for focus/input regressions.
6. Record capture success, duplicate sends, focus changes, dismissed UI, and battery observations in `docs/EXPERIMENT_LOG.md`.
7. Research and implement Shizuku as the preferred stable path, using official Shizuku APIs and OctoClip's documented user flow as references.
8. Selectively recover the reviewed Windows reconnect implementation and tests; do not restore PR #3 wholesale.
9. Build Windows EXE and Linux artifacts after the selective desktop recovery is green.
10. Expand the current status screen into a true end-to-end diagnostic flow only after capture paths are real and testable.

## Continuation checklist

A new thread must be able to state, without consulting archived patchwork:

- canonical baseline, active branch, and active PR;
- original product requirements;
- permanent no-wheel-reinvention rule;
- required reference links;
- what is implemented in the active branch;
- what is build-verified versus device-verified;
- latest artifacts and hashes;
- retained build failures and corrections;
- exact next action.
