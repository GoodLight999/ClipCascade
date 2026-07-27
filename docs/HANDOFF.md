# ClipCascade Stability Recovery Handoff

Last updated: 2026-07-27 (Asia/Tokyo)

## Read this first

This is the canonical continuation document for `GoodLight999/Trial-and-Error-ClipCascade`.
A new thread must read, in order:

1. `docs/HANDOFF.md`
2. `docs/EXPERIMENT_LOG.md`
3. `docs/EXPERIMENT_LOG_2026-07-27_CAPTURE_PIPELINE.md`
4. `docs/EXPERIMENT_LOG_2026-07-27_SHIZUKU.md`
5. `docs/EXPERIMENT_LOG_2026-07-27_SHIZUKU_BUILD.md`
6. `docs/EXPERIMENT_LOG_2026-07-27_P2S_OUTBOX.md`
7. `docs/EXPERIMENT_LOG_2026-07-27_DESKTOP_RECOVERY.md`
8. Draft PR `#4`

## Canonical repository state

- Repository: `GoodLight999/Trial-and-Error-ClipCascade`
- Upstream: `Sathvik-Rao/ClipCascade`
- Upstream-aligned baseline SHA: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Mirror branch: `main`
- Active development branch: `stability-recovery`
- Active draft PR: `#4`
- Latest green branch head: `f35ebffba8b99f783b20b0bec2e4bc16a0421f1b`
- Latest green Android workflow: `30248168083`
- Latest green desktop workflow on the same head: `30248168084`

The repository reset is already complete. **Do not reset, reconstruct, re-baseline, or start another clean-rebuild project.**

## Permanent operating rules

1. **No wheel reinvention.** Inspect upstream and the named references before designing an equivalent mechanism.
2. **Never return to completed setup work.** Read this handoff and the logs before starting.
3. Keep `main` aligned with upstream. Product work belongs on `stability-recovery` or a later focused branch created from its accepted state.
4. Extend existing runtime paths instead of creating parallel transports, duplicate state owners, or speculative frameworks.
5. Do not stack unverified fixes. Build and test each focused unit before expansion.
6. Compilation and unit tests are not runtime proof. Android and desktop reliability require real-device/runtime evidence.
7. Record every meaningful source, hypothesis, implementation, failure, correction, result, artifact, and decision.
8. Update this handoff whenever completed work, artifacts, known failures, or exact next actions change.
9. Preserve the Sathvik-Rao server protocol and public-server compatibility.
10. Do not require root.
11. Do not inspect archived patchwork except for narrowly named, evidence-driven recovery.
12. Never restore failed Android diagnostics/acquisition scaffolding from archived PR `#3`.
13. APK, EXE, and Linux artifact generation are product requirements, not CI decoration.
14. Never call an artifact runtime-proven merely because CI is green.
15. Do not introduce a named backend, model, or status unless a real runtime path uses it.

## Product requirements that must not be forgotten

### Android

- Make Android-to-server clipboard sharing reliable while ClipCascade is in the background.
- Ordinary listener, Accessibility, Shizuku, guided ADB, or a safe combination may be used; root must not be required.
- Reduce unnecessary wakeups, polling, overlays, and battery drain without sacrificing reliability.
- Provide setup usable by a non-technical user.
- Shizuku setup must be automated or semi-automated and explain restart recovery.
- Provide an automatic diagnostic flow that can be run by opening the app and tapping clear steps.
- Do not interfere with launcher drawers, Amazon/browser search fields, text selection, or ordinary touch/focus behavior.
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

1. `https://github.com/Sathvik-Rao/ClipCascade`
2. `https://github.com/wuxinkami/ClipCascade_go_fork`
3. `https://docs.octoclip.app/features/source/background-monitoring/android/shizuku`
4. `https://docs.octoclip.app/features/source/background-monitoring/android/accessibility`
5. Official Android clipboard, Accessibility, foreground-service, and background-start documentation.
6. Official Shizuku API source, README, demo, UserService, AIDL, lifecycle, and setup documentation.

## Archived development lines

- Prior patchwork PR `#1`: closed and unmerged; never use as a baseline.
- Failed clean-rebuild PR `#3`: closed and unmerged; never restore wholesale.

A narrow recovery exception was used for independently reviewable Windows code from product-code commit `a94b830fb954d09fc742b39833cebd5915988566`. Only connection state, retry, STOMP readiness, tray projection, and tests were recovered after comparison with upstream. Archived Android diagnostics and speculative acquisition abstractions remain excluded.

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

The active branch extends this path. It does not create a second Android transport.

## Android milestone A11Y-001

Implemented and build-verified:

- conservative Accessibility copy trigger;
- high-confidence copy actions, labels, and copied confirmations only;
- no generic-click or generic-selection trigger;
- no Accessibility window-content retrieval;
- own-package and inactive-runtime events ignored;
- debounce and clipboard-write settling delay;
- reuse of the existing React Native event, send, dedup, and overlay paths;
- `CLEAR_TASK` removed from the overlay path;
- fail-closed overlay behavior when permission or React Native runtime is unavailable;
- native setup/status activity and launcher long-press shortcut;
- platform theme colors instead of the failed custom diagnostic panel;
- Metro-free `standalone` build with release runtime semantics and debug signing.

The deleted Go/Android reference used broad generic-click and text-selection triggers. Those triggers were intentionally rejected because they could recreate the reported Amazon/search-field interference and unnecessary wakeups.

## Android milestone SHIZUKU-001

Implemented and build-verified:

- official Shizuku API/provider `13.1.5`;
- official UserService and AIDL pattern;
- application-level Shizuku binder, permission, binder-death, UserService binding, UID, and error status;
- shell-side read-only clipboard UserService;
- adaptive reflection over Android `IClipboard.getPrimaryClip` signatures;
- `com.android.shell` identity supplied to the clipboard service;
- one shared `BackgroundClipboardCapture` decision point;
- Accessibility and READ_LOGS triggers try Shizuku first;
- successful Shizuku text reads return to the existing React Native `onClipboardChange -> sendClipBoard` path;
- Shizuku absence, stop, denial, binding state, error, or non-text clip falls back to the existing overlay path;
- no new transport, network client, or polling loop was introduced by SHIZUKU-001; the later OUTBOX-001 milestone extends the existing P2S send path separately;
- guided setup shows installation, running state, permission, UserService binding, service UID, Accessibility, overlay, READ_LOGS, battery exemption, foreground runtime, and last non-payload error;
- setup can open/install Shizuku, request permission, bind, and run a privacy-safe read test;
- the read test reports only type and length, never clipboard content.

### Intentional first-version limitation

Direct Shizuku output is text-only. Image/file content URIs may be readable by the shell UserService but not by the ClipCascade app process because URI grants are process/identity-sensitive. Non-text clips therefore use the existing app-process overlay path until a separate permission/stream-transfer experiment proves a safe implementation.

### What SHIZUKU-001 does not yet do

- It does not monitor clipboard changes by itself.
- It still needs an event trigger: ordinary listener in foreground, conservative Accessibility, or READ_LOGS/ADB.
- A Shizuku-side clipboard-change listener will be considered only if real-device evidence shows it is needed and safe.

## Android milestone OUTBOX-001

Implemented and build-verified for P2S text:

- persistent bounded `P2STextOutbox` using the existing AsyncStorage adapter;
- exact existing `/app/cliptext` publish and `/user/queue/cliptext` subscription destinations retained;
- no second network client and no server change;
- queue scoped by server URL, username, cipher mode, and hashed-password fingerprint;
- text prepared by the existing validation and encryption code before persistence;
- when encryption is enabled, persisted wire payload is ciphertext;
- FIFO, maximum 20 items, bounded bytes, 24-hour default expiry, and serialized storage mutations;
- one in-flight item at a time;
- STOMP connect plus successful subscription starts draining;
- matching plaintext server echo acknowledges and removes only the in-flight head;
- 30-second missing-echo timeout releases the head for retry;
- disconnect, STOMP error, WebSocket error/close, and service shutdown release in-flight text back to queued state;
- restart recovery converts persisted in-flight state back to queued;
- acknowledged own echoes are not written back to the Android clipboard, preventing A/B/C rollback during offline replay;
- queue count, bytes, oldest time, head state, attempts, and last-attempt time are stored as non-payload status;
- image/file sending remains unchanged and is not claimed durable.

Verification:

- JavaScript reliability gate: 3 suites, 19 tests, all passed;
- Android JVM tests, AIDL/Shizuku compilation, Kotlin/resources, standalone build, JS bundle, ZIP integrity, checksum, and upload passed;
- bundle inspection found outbox storage and echo-acknowledgement markers.

Runtime semantics are at-least-once relative to the unchanged server echo, not a new application-level delivery acknowledgement. Real-device disconnect/reconnect ordering still requires acceptance testing.

## Latest verified Android artifact

- Workflow: `30248168083`
- Branch head: `f35ebffba8b99f783b20b0bec2e4bc16a0421f1b`
- APK artifact ID: `8645964331`
- Build-log artifact ID: `8645962581`
- Artifact file: `ClipCascade-Android-stability-standalone.apk`
- User-facing file: `ClipCascade-Android-stability-outbox.apk`
- Size: `93,613,211` bytes
- SHA-256: `9810be35788fbcad32cf34986f0b19bcb324db1f40a9766024c298aec8e32b2d`
- Exact `assets/index.android.bundle`: present
- APK ZIP integrity: passed
- APK entry count: `538`
- JavaScript tests: `19/19` passed
- Status: debug-signed engineering artifact, not a production release

## Android build failures retained as evidence

### Run `30209073143`

The upstream-aligned tree lacked required React Native Gradle properties. Standard React Native 0.80 properties were restored, including `newArchEnabled=true` and `hermesEnabled=true`.

### Run `30209210999`

Direct `assembleStandalone` reached React Native CMake autolinking before generated JNI directories existed. The previously proven order was reused: `:app:testDebugUnitTest` then `:app:assembleStandalone`. Dependencies were not patched.

### Replaced Shizuku run `30211379415`

The run was replaced by a later PR update, but its retained build log reached Kotlin compilation and showed that `IShizukuClipboardService` had not been generated. Root cause: AIDL generation was disabled. Correction: add only `buildFeatures { aidl true }` and retain the official AIDL/UserService design.

### Run `30211592338`

AIDL, Shizuku dependencies, Kotlin, Android resources, app tests, standalone APK, JS-bundle check, ZIP integrity, checksum, and artifact upload passed.

## Desktop milestone DESKTOP-001

Selectively recovered and verified on Ubuntu and Windows:

- authoritative `ConnectionController`;
- states `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `RECONNECT_WAIT`, `AUTH_REQUIRED`, `STOPPING`, and `FATAL_ERROR`;
- immutable snapshots and sanitized errors;
- capped exponential retry with jitter;
- cancellable retry timers and stale-generation invalidation;
- no socket-callback sleeps or recursive reconnect;
- fresh STOMP client per attempt;
- connection reported only after STOMP `CONNECTED` and successful subscription callback;
- timeout, socket error, close-before-CONNECTED, STOMP ERROR, and subscription-failure unblocking;
- explicit disconnect suppresses the remote-close callback;
- automatic and manual reconnect;
- login-required and fatal states;
- lost/restored notifications;
- last accepted send and valid receive observations;
- GUI and CLI tray state projection;
- legacy P2P boolean fallback retained;
- missing `logging` imports in the archived GUI/CLI tray code found and corrected.

The STOMP destinations, cookie handling, encryption payload format, and server protocol were not changed.

## Verified desktop artifacts

Latest same-head desktop workflow: `30248168084` at `f35ebffba8b99f783b20b0bec2e4bc16a0421f1b`.

The workflow passed Ubuntu and Windows tests, Windows EXE generation, Linux packaging, checksum creation, and artifact upload.

### Windows

- Artifact ID: `8645900812`
- File: `ClipCascade-Windows-stability.exe`
- Size: `57,456,040` bytes
- SHA-256: `e83390cffca570224ae47d8144313062564e7886eace44dc04a28701c7855128`
- PE32+ GUI x86-64
- Artifact ZIP integrity: passed
- Embedded checksum matched independent recalculation

### Linux

- Artifact ID: `8645865478`
- File: `ClipCascade-Linux-stability.tar.gz`
- Size: `60,415` bytes
- SHA-256: `6bde0b175cf12ef3e26224efd04ee8449720d34f46735f39e1520fd7599cfc57`
- gzip-compressed Unix tar
- Archive integrity: passed
- Entry count: `59`
- Embedded checksum matched independent recalculation

## Explicitly not yet proven

### Android

- launch and setup-screen rendering on the user's device;
- Shizuku installation/start/permission/UserService binding on that device;
- expected shell UID in the UserService;
- hidden clipboard Binder read on the user's Android/vendor build;
- foreground/background clipboard delivery to the server and remote Windows device;
- automatic overlay fallback after Shizuku stop or denial;
- Amazon, launcher drawer, browser, selection toolbar, and search-field safety;
- duplicate-send behavior on the real device;
- battery/wakeup behavior;
- Shizuku-only clipboard-change monitoring;
- offline queue survival, ordering, retry, and self-echo suppression on the real device/public server;
- image/file durability;
- true server or remote-application acknowledgement beyond the unchanged server echo;
- full end-to-end automatic diagnostic flow.

### Desktop

- generated EXE against the public server;
- forced real network-loss and recovery;
- actual tray rendering/manual controls on Windows;
- seamless in-process reauthentication after `AUTH_REQUIRED`;
- P2P migration to the snapshot contract;
- durable outbound queue and application-level delivery acknowledgement;
- persistent full status window or diagnostics export bundle.

## Exact next actions

1. Install `ClipCascade-Android-stability-outbox.apk`.
2. Start Shizuku using its normal wireless-debugging or computer-assisted setup.
3. Long-press ClipCascade and open `バックグラウンド設定`.
4. Confirm readable light/dark rendering and Shizuku installation/running/permission/UserService state.
5. Confirm the service UID is a shell UID and run the privacy-safe Shizuku read test.
6. Start the existing ClipCascade foreground service and verify one normal Android-to-Windows text copy.
7. Disable Android networking without stopping the foreground service; copy A, B, and C; reconnect; verify ordered A/B/C delivery without duplicates.
8. Confirm Android's clipboard does not roll back to A or B when queued self-echoes return.
9. Repeat with process termination/relaunch between enqueue and reconnect to test persisted recovery.
10. Stop Shizuku and repeat a copy to verify overlay fallback.
11. Test launcher drawer, Amazon, browser, selection toolbar, and search fields for focus/input regressions.
12. Record missed sends, duplicate sends, queue state, UI changes, wakeups, and battery behavior.
13. Run `ClipCascade-Windows-stability.exe` against the existing server and force network loss/restoration.
14. Connect `p2sTextOutboxStatus` to the existing mobile status page, then expand diagnostics only around stages proven useful by acceptance evidence.

## Continuation checklist

A new thread must recover without archived patchwork:

- baseline, active branch, PR, and latest green product head;
- original requirements;
- no-wheel-reinvention rule;
- required references;
- A11Y, Shizuku, P2S outbox, and desktop implementation boundaries;
- build-verified versus runtime/device-verified claims;
- latest workflows, artifacts, sizes, and hashes;
- retained failures and corrections;
- exact next actions.
