# ClipCascade Stability Recovery Handoff

Last updated: 2026-07-27 (Asia/Tokyo)

## Read this first

This is the canonical continuation document for `GoodLight999/Trial-and-Error-ClipCascade`.
A new thread must read this file and `docs/EXPERIMENT_LOG.md` before inspecting or changing code.

## Canonical repository state

- Repository: `GoodLight999/Trial-and-Error-ClipCascade`
- Upstream: `Sathvik-Rao/ClipCascade`
- Upstream-aligned baseline SHA: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Mirror branch: `main`
- Active development branch: `stability-recovery`
- Active draft PR: `#4`
- Latest green Android product head: `613006702d22444449ce69500934c08e8a953ce0`
- Latest green Android workflow: `30209366320`
- Latest green desktop product head: `4174f87e5c4c4d5ffaf6ed07b1862fcfb77623d6`
- Latest green desktop workflow: `30210415035`

The repository reset is already complete. **Do not reset, reconstruct, re-baseline, or start another clean-rebuild project.**

## Permanent operating rules

1. **No wheel reinvention.** Inspect upstream and the named references before designing an equivalent mechanism.
2. **Never return to completed setup work.** Check this handoff and the experiment log first.
3. Keep `main` aligned with upstream. Product work belongs on a focused development branch.
4. Extend existing runtime paths instead of creating parallel transports, state owners, or speculative frameworks.
5. Do not stack unverified fixes. Build and test each focused unit before expanding it.
6. Compilation and unit tests are not runtime proof. Android and desktop reliability require real-device/runtime evidence.
7. Record every meaningful hypothesis, source, implementation, failure, result, artifact, and decision in `docs/EXPERIMENT_LOG.md`.
8. Update this handoff whenever completed work, known failures, artifacts, or exact next actions change.
9. Preserve the Sathvik-Rao server protocol and public-server compatibility.
10. Do not require root.
11. Do not inspect archived patchwork except for narrowly named, evidence-driven recovery.
12. Do not restore the failed Android diagnostics/acquisition scaffolding from archived PR #3.
13. APK, EXE, and Linux artifact generation are product requirements, not CI decoration.
14. Never label an artifact as runtime-proven merely because CI is green.

## Product requirements that must not be forgotten

### Android

- Make Android-to-server clipboard sharing reliable while ClipCascade is in the background.
- Ordinary listener, Accessibility, Shizuku, guided ADB, or a safe combination may be used; root must not be required.
- Reduce unnecessary wakeups, polling, overlays, and battery drain without sacrificing reliability.
- Provide a setup guide usable by a non-technical user.
- Shizuku setup must be automated or semi-automated and explain restart recovery.
- Provide an automatic diagnostic flow that can be run by opening the app and tapping through clear steps.
- Do not interfere with drawers, Amazon/browser search fields, text selection, or ordinary touch/focus behavior.
- Prevent duplicate sends and distinguish capture, local transport acceptance, server delivery, and remote application.

### Windows and Linux

- Add meaningful runtime status after login.
- Show connection, reconnect, last send/receive, and actionable errors.
- Implement reliable automatic and manual reconnect without sleeping or recursively reconnecting inside socket callbacks.
- Generate Windows EXE and Linux artifacts.

### Compatibility and delivery

- Do not change the server protocol.
- Continue to work with Sathvik-Rao's existing server, including the public server.
- Generate installable APK and EXE artifacts.

## Required references

Inspect these before implementing the corresponding feature:

1. Upstream source: `https://github.com/Sathvik-Rao/ClipCascade`
2. Removed Go/Android history: `https://github.com/wuxinkami/ClipCascade_go_fork`
3. OctoClip Shizuku documentation: `https://docs.octoclip.app/features/source/background-monitoring/android/shizuku`
4. OctoClip Accessibility documentation: `https://docs.octoclip.app/features/source/background-monitoring/android/accessibility`
5. Official Android clipboard, Accessibility, foreground-service, and background-start documentation.
6. Official Shizuku source, API guide, demo, and user setup documentation before Shizuku implementation.

## Archived development lines

- Prior patchwork PR `#1`: closed and unmerged; never use as a baseline.
- Failed clean-rebuild PR `#3`: closed and unmerged; never restore it wholesale.

A narrow recovery exception was applied to independently reviewable Windows code from product-code commit `a94b830fb954d09fc742b39833cebd5915988566`. Only the connection controller, retry policy, STOMP readiness, tray projection, and tests were selectively recovered after comparison with upstream. The old Android diagnostics and speculative acquisition framework remain excluded.

## Existing upstream Android path being extended

Upstream already contains:

- React Native/Notifee foreground service owning transport;
- ordinary `ClipboardManager` listener;
- `onClipboardChange` feeding existing `sendClipBoard`;
- JavaScript content hashing/deduplication;
- READ_LOGS trigger;
- `ClipboardFloatingActivity` overlay read;
- share-sheet/PROCESS_TEXT fallback;
- boot receiver, battery guidance, and ADB guidance.

The recovery branch extends this path. It does not create a second Android transport.

## Android reference archaeology

The deleted Android implementation in `wuxinkami/ClipCascade_go_fork` had a foreground service, Accessibility trigger, invisible overlay clipboard read, debounce, self-loop suppression, and permission guidance.

Its Accessibility service also reacted to generic clicks and text-selection changes. That broad policy was intentionally rejected because it could create false triggers, unnecessary wakeups, and the reported Amazon/search-field interference.

## Android milestone A11Y-001

Implemented and Android-build verified:

- process-wide visibility of whether the existing React Native clipboard runtime is active;
- conservative `ClipCascadeAccessibilityService`;
- triggers limited to high-confidence copy actions, copy-labelled clicks, and copied-confirmation notifications/announcements;
- no generic-click or generic-selection trigger;
- no Accessibility window-content retrieval;
- own-package events ignored;
- debounce and clipboard-write settling delay;
- no trigger while the existing foreground runtime is inactive;
- no trigger while overlay permission is absent;
- reuse of existing `ClipboardFloatingActivity` and JavaScript send/dedup path;
- safer overlay intent flags with `CLEAR_TASK` removed;
- fail-closed overlay behavior when permission or React Native runtime is unavailable;
- native `BackgroundSetupActivity` showing Accessibility, overlay, READ_LOGS, battery exemption, and foreground-runtime state;
- system-settings buttons, ADB fallback command copy, refresh, and return-to-app controls;
- launcher long-press setup shortcut;
- platform theme colors instead of the failed custom diagnostic panel;
- Metro-free `standalone` build using release runtime semantics and debug signing;
- CI checks for app tests, APK build, JS bundle presence, ZIP integrity, artifact upload, and SHA-256.

Build verification does **not** prove real-device background capture or UI safety.

## Latest verified Android artifact

- Workflow: `30209366320`
- Head: `613006702d22444449ce69500934c08e8a953ce0`
- APK artifact ID: `8634074763`
- Build-log artifact ID: `8634074053`
- File: `ClipCascade-Android-stability-standalone.apk`
- Size: `93,544,663` bytes
- SHA-256: `b2bca637638a829c8c594df567ca6951f56972ffc70b8c7d03fef326bed4e857`
- Exact `assets/index.android.bundle`: present
- APK ZIP integrity: passed
- Status: debug-signed engineering artifact, not a production release

## Android build failures retained as evidence

### Run `30209073143`

`app/build.gradle` referenced `hermesEnabled`, but the upstream-aligned tree lacked `android/gradle.properties`. Standard React Native 0.80 properties were restored, including `newArchEnabled=true` and `hermesEnabled=true`.

### Run `30209210999`

Direct `assembleStandalone` reached React Native CMake autolinking before generated JNI directories existed. The previously proven order was reused: `:app:testDebugUnitTest` then `:app:assembleStandalone`. Dependencies were not patched.

### Run `30209366320`

Android tests, build, bundle-presence check, ZIP integrity, checksum, and artifact upload passed.

## Desktop milestone DESKTOP-001

Selectively recovered and reverified on both Ubuntu and Windows:

- authoritative `ConnectionController`;
- states `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `RECONNECT_WAIT`, `AUTH_REQUIRED`, `STOPPING`, and `FATAL_ERROR`;
- immutable snapshots and sanitized errors;
- capped exponential retry with jitter;
- cancellable retry timers and stale-generation invalidation;
- no socket-callback sleeps or recursive reconnect;
- fresh STOMP client per attempt;
- connection is not reported until STOMP `CONNECTED` and subscription callback complete;
- timeout, socket error, close-before-CONNECTED, STOMP ERROR, and subscription-failure unblocking;
- explicit disconnect suppresses the remote-close callback;
- automatic reconnect after runtime loss;
- manual immediate reconnect during retry wait;
- login-required and fatal-error states;
- lost/restored notifications;
- last accepted send and valid receive observations;
- GUI and CLI tray projections for connect, reconnect, disconnect, login-required, stopping, and fatal error;
- legacy P2P boolean fallback retained until P2P is migrated;
- missing `logging` imports in the archived GUI/CLI tray code were found during review and corrected rather than blindly restored.

The existing STOMP destinations, request/cookie handling, encryption payload format, and server protocol were not changed.

## Latest verified desktop artifacts

Workflow `30210415035` at head `4174f87e5c4c4d5ffaf6ed07b1862fcfb77623d6` passed:

- desktop syntax checks and unit tests on Ubuntu;
- desktop syntax checks and unit tests on Windows;
- Windows PyInstaller EXE build;
- Linux source-package build;
- artifact checksums and uploads.

### Windows

- Artifact ID: `8634309346`
- File: `ClipCascade-Windows-stability.exe`
- Size: `57,456,012` bytes
- SHA-256: `4dc7aa89917ae3f0779428327013745dc2c64e33809ee4633fcb2308598e0b69`
- Independent identification: PE32+ GUI executable, x86-64
- Artifact ZIP integrity: passed
- Embedded checksum: matched independent recalculation

### Linux

- Artifact ID: `8634293452`
- File: `ClipCascade-Linux-stability.tar.gz`
- Size: `60,389` bytes
- SHA-256: `8e3421abf268cd9a6488ec9a8353ae92c5bd88207529324ae83eabdd390640d6`
- Independent identification: gzip-compressed Unix tar archive
- Archive integrity: passed
- Entry count: `59`
- Embedded checksum: matched independent recalculation

## Explicitly not yet implemented or proven

### Android

- successful launch of the latest APK on the user's device;
- real-device foreground/background capture;
- Amazon, launcher drawer, browser, selection toolbar, and search-field regression tests;
- duplicate-send acceptance tests;
- battery/wakeup measurement;
- Shizuku integration and guided restart recovery;
- durable outbound queue or server-level delivery acknowledgement;
- true end-to-end automatic diagnostic mode.

### Desktop

- public-server runtime test of the generated EXE;
- forced network-loss and recovery test on a real Windows installation;
- real tray rendering and manual control acceptance on Windows;
- seamless in-process reauthentication after `AUTH_REQUIRED`;
- P2P migration to the authoritative snapshot contract;
- application-level delivery acknowledgement and durable outbound queue;
- persistent full status window or diagnostics export bundle.

## Exact next actions

1. Install and launch the latest Android APK on the user's device.
2. Verify the launcher long-press `バックグラウンド設定` screen in light and dark modes.
3. Start the existing service and enable overlay plus ClipCascade Accessibility.
4. Test ordinary foreground, Accessibility background, and READ_LOGS/ADB capture separately.
5. Test launcher drawer, Amazon, browser, selection toolbar, and search fields for regressions.
6. Record capture, duplicate-send, focus, dismissed-UI, and battery observations.
7. Run the generated Windows EXE against the existing server and force network loss/restoration.
8. Record tray/status/reconnect behavior and any public-server incompatibility.
9. Implement Shizuku as the preferred stable Android path only after inspecting official Shizuku APIs/demo and OctoClip's documented flow.
10. Expand diagnostics only after real capture and transport stages are available to test.

## Continuation checklist

A new thread must recover without archived patchwork:

- canonical baseline, active branch, and PR;
- original product requirements;
- permanent no-wheel-reinvention rule;
- required reference links;
- Android and desktop implementation status;
- build-verified versus runtime/device-verified claims;
- latest workflows, artifacts, sizes, and hashes;
- retained failures and corrections;
- exact next action.
