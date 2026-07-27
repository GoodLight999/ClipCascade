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
7. `docs/EXPERIMENT_LOG_2026-07-27_OUTBOX_STATUS_UI.md`
8. `docs/EXPERIMENT_LOG_2026-07-27_DIAGNOSTIC_REPORT.md`
9. `docs/EXPERIMENT_LOG_2026-07-27_P2S_RETRY_BACKOFF.md`
10. `docs/EXPERIMENT_LOG_2026-07-27_DESKTOP_RECOVERY.md`
11. Draft PR `#4` and its latest GitHub Actions checks

## Canonical repository state

- Repository: `GoodLight999/Trial-and-Error-ClipCascade`
- Upstream: `Sathvik-Rao/ClipCascade`
- Upstream-aligned baseline SHA: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Mirror branch: `main`
- Active development branch: `stability-recovery`
- Active draft PR: `#4`
- Latest green product-code head: `829f872c684c59f16a78bbe283b9e1260e20fce9`
- Latest green Android workflow on that product head: `30257268532`
- Latest green desktop workflow on that product head: `30257268751`

Documentation-only commits follow the product-code head. Use the PR head for the newest documents, but use `829f872c...` as the exact source/artifact identity until later product code is verified.

The repository reset is already complete. **Do not reset, reconstruct, re-baseline, clean rebuild, or start another replacement project.**

## Permanent operating rules

1. **No wheel reinvention.** Inspect upstream and the named references before designing an equivalent mechanism.
2. **Never return to completed setup work.** Read this handoff and the logs before starting.
3. Keep `main` aligned with upstream. Product work belongs on `stability-recovery` or a later focused branch created from its accepted state.
4. Extend existing runtime paths instead of creating parallel transports, duplicate state owners, speculative frameworks, or synthetic diagnostics.
5. Do not stack unverified product fixes. Build and test each focused unit before expansion.
6. Compilation and unit tests are not runtime proof. Android and desktop reliability require real-device/runtime evidence.
7. Record every meaningful source, hypothesis, implementation, failure, correction, result, artifact, and decision.
8. Update this handoff whenever completed work, artifacts, known failures, or exact next actions change.
9. Preserve the Sathvik-Rao server protocol and public-server compatibility.
10. Do not require root.
11. Do not inspect archived patchwork except for narrowly named, evidence-driven recovery.
12. Never restore failed Android diagnostics/acquisition scaffolding from archived PR `#3`.
13. APK, EXE, and Linux artifact generation are product requirements, not CI decoration.
14. Never call an artifact runtime-proven merely because CI is green.
15. Do not introduce a named backend, model, capture path, queue, or status unless a real runtime path uses it.
16. Temporary patch files or write-enabled workflows must be deleted immediately after use.

## Product requirements that must not be forgotten

### Android

- Make Android-to-server clipboard sharing reliable while ClipCascade is in the background.
- Reuse the existing foreground-service/STOMP path.
- Ordinary listener, Accessibility, Shizuku, guided ADB/READ_LOGS, overlay, and share sheet may be combined; root must not be required.
- Reduce unnecessary wakeups, polling, overlays, and battery drain without sacrificing reliability.
- Provide setup usable by a non-technical user.
- Shizuku setup must be automated or semi-automated and explain restart recovery.
- Provide diagnostics that identify capture, queue, connection, echo, and remote-application stages with a few taps.
- Do not interfere with launcher drawers, Amazon/browser/search fields, text selection, or ordinary touch/focus behavior.
- Prevent duplicate sends and distinguish capture, local queue acceptance, server echo, and remote application.
- Preserve offline text in FIFO order and retry it after reconnection/restart.

### Windows and Linux

- Show meaningful runtime status after login.
- Show connection, reconnect, last send/receive, and actionable errors.
- Implement reliable automatic and manual reconnect without sleeps or recursive reconnect inside socket callbacks.
- Generate Windows EXE and Linux artifacts.

### Compatibility and delivery

- Do not change the server protocol.
- Continue to work with Sathvik-Rao's existing server, including the public server.
- Keep publish destination `/app/cliptext`.
- Keep subscribe destination `/user/queue/cliptext`.
- Generate installable APK and EXE artifacts plus a Linux package.

## Required references

Inspect these before implementing the corresponding feature:

1. `https://github.com/Sathvik-Rao/ClipCascade`
2. `https://github.com/wuxinkami/ClipCascade_go_fork`
3. `https://docs.octoclip.app/features/source/background-monitoring/android/shizuku`
4. `https://docs.octoclip.app/features/source/background-monitoring/android/accessibility`
5. Official Android clipboard, Accessibility, foreground-service, background-start, battery, and package-visibility documentation.
6. Official Shizuku API source, README, demo, UserService, AIDL, lifecycle, and setup documentation.

## Archived development lines

- Prior patchwork PR `#1`: closed and unmerged; never use as a baseline.
- Failed clean-rebuild PR `#3`: closed and unmerged; never restore wholesale.

A narrow recovery exception was used for independently reviewable Windows code from product-code commit `a94b830fb954d09fc742b39833cebd5915988566`. Only connection state, retry, STOMP readiness, tray projection, and tests were recovered after comparison with upstream. Archived Android diagnostics and speculative acquisition abstractions remain excluded.

## Existing upstream Android path being extended

Upstream already contains:

- React Native/Notifee foreground service owning transport;
- ordinary `ClipboardManager` listener;
- `onClipboardChange` feeding the existing `sendClipBoard` path;
- JavaScript content hashing/deduplication;
- READ_LOGS trigger;
- `ClipboardFloatingActivity` overlay read;
- share-sheet/PROCESS_TEXT fallback;
- boot receiver, battery guidance, and ADB guidance.

The active branch extends this path. It does not create a second Android transport.

## Implemented Android milestones

### A11Y-001 — conservative Accessibility trigger

Build-verified:

- high-confidence copy actions, labels, and copied confirmations only;
- no generic-click or generic-selection trigger;
- no Accessibility window-content retrieval;
- own-package and inactive-runtime events ignored;
- debounce and clipboard-write settling delay;
- existing React Native event, send, dedup, and overlay paths reused;
- fail-closed overlay behavior;
- native setup/status activity and launcher long-press shortcut.

The broad generic-click/text-selection policy seen in the deleted Go implementation was intentionally rejected because it could reproduce Amazon/search-field interference and unnecessary wakeups.

### SHIZUKU-001 — preferred non-root clipboard read

Build-verified:

- official Shizuku API/provider `13.1.5`;
- official UserService and AIDL pattern;
- application-level binder, permission, death, binding, UID, and error state;
- shell-side read-only clipboard UserService;
- adaptive reflection over Android `IClipboard.getPrimaryClip` signatures;
- `com.android.shell` identity supplied to the clipboard service;
- one shared `BackgroundClipboardCapture` decision point;
- Accessibility and READ_LOGS triggers try Shizuku first;
- successful text reads return to the existing `onClipboardChange -> sendClipBoard` path;
- Shizuku absence, stop, denial, bind failure, error, or non-text clip falls back to the existing overlay path;
- setup screen can open/install Shizuku, request permission, bind, and run a privacy-safe read test.

Intentional limitation: direct Shizuku output is text-only. Image/file URIs continue through the existing app-process fallback.

Shizuku does not monitor changes by itself. It still needs ordinary listener, conservative Accessibility, or READ_LOGS as a trigger.

### OUTBOX-001 — persistent P2S text FIFO

Build-verified:

- persistent bounded `P2STextOutbox` using the existing AsyncStorage adapter;
- exact existing STOMP destinations retained;
- no second network client and no server change;
- queue scoped by server URL, username, cipher mode, and hashed-password fingerprint;
- existing validation and encryption reused before persistence;
- FIFO, maximum 20 items, bounded bytes, 24-hour default expiry, and serialized mutations;
- one in-flight item;
- connection plus successful subscription starts draining;
- matching plaintext server echo acknowledges/removes only the in-flight head;
- disconnect, STOMP/WebSocket failure, publish failure, missing echo, and shutdown release the head;
- restart converts persisted `inflight` state to `queued`;
- acknowledged own echoes do not overwrite the current Android clipboard;
- image/file behavior remains unchanged and is not claimed durable.

Runtime semantics are at-least-once relative to the unchanged server echo. There is no new application-level delivery acknowledgement.

### OUTBOX-UI-001 — payload-free queue status

Build-verified:

- existing `App.js` 300 ms poller reused; no additional poller;
- P2S connection page shows queue count, queued/sending state, bytes, attempts, drops, and retry countdown;
- native AsyncStorage JSON-string shape handled safely;
- P2P/unknown modes and service stop clear stale queue display;
- payloads, hashes, scope, server/account identifiers, and credentials are excluded;
- malformed/unloaded values fail closed.

### DIAGNOSTIC-REPORT-001 — shareable real-state report

Build-verified:

- existing native background setup screen exposes `診断レポートを共有`;
- report combines device/build, Shizuku, Accessibility/overlay/READ_LOGS/battery/runtime, real capture counters, connection state, and bounded P2S outbox metadata;
- Android Sharesheet is used instead of clipboard copy;
- report types exclude clipboard payloads, hashes, server URLs, usernames, passwords, cookies, and encryption keys;
- free-form values are one-line, bounded, and redact URLs and email addresses;
- malformed/missing outbox state fails closed;
- report now includes `headNextAttemptAt` as `Head next attempt epoch ms`.

This is a passive real-state report. A guided active capture → queue → server echo → remote-apply test is still not implemented.

### P2S-RETRY-001 — bounded missing-echo backoff

Build-verified:

- fixed repeated 30-second missing-echo retry replaced with exponential delay;
- no-jitter sequence: `30s → 60s → 120s → 240s → 480s → 600s`, capped thereafter;
- default symmetric jitter: plus or minus 20 percent;
- `nextAttemptAt` persisted in the existing outbox item;
- in-flight release preserves the deadline;
- process restart and reconnect respect a persisted future deadline;
- one existing one-shot timer schedules the next drain; no retry poller was added;
- queue status shows `Retry in: <seconds>s`;
- diagnostic report exposes the same payload-free deadline.

The temporary write-enabled retry workflow was removed in commit `6e61d35073a49bff6f4970a664b50c159a1cafc3`. No temporary write workflow remains.

## Implemented desktop milestone DESKTOP-001

Selectively recovered and build-verified on Ubuntu and Windows:

- authoritative `ConnectionController`;
- immutable snapshots;
- states `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `RECONNECT_WAIT`, `AUTH_REQUIRED`, `STOPPING`, and `FATAL_ERROR`;
- capped exponential retry with jitter;
- cancellable timers and stale-generation invalidation;
- no callback sleeps or recursive reconnect;
- fresh STOMP client per attempt;
- connected only after STOMP `CONNECTED` and successful subscription callback;
- timeout, socket error, close-before-CONNECTED, STOMP ERROR, and subscription failure handling;
- explicit disconnect suppression of remote-close callbacks;
- automatic and manual reconnect;
- lost/restored notifications;
- last accepted send and receive observations;
- GUI and CLI tray state projection;
- Windows EXE and Linux package generation.

The STOMP destinations, cookie handling, encryption payload format, and server protocol were not changed.

## Latest verified product evidence

Product-code head: `829f872c684c59f16a78bbe283b9e1260e20fce9`

### Android

- workflow: `30257268532`
- conclusion: success
- APK artifact ID: `8649504009`
- build-log artifact ID: `8649502019`
- file: `ClipCascade-Android-stability-standalone.apk`
- user-facing file: `ClipCascade-Android-stability-retry-diagnostics.apk`
- size: `93,617,791` bytes
- SHA-256: `9f8fefd4d32892e891e763590a443d4dbcc20534b7cba4260fabe8489589f106`
- artifact ZIP SHA-256: `6c48dc5619b11b031fcd970bb244a3bce90f15e283a9dacbb76b1920313d3937`
- APK entries: `538`
- exact `assets/index.android.bundle`: present
- APK ZIP integrity: passed
- JavaScript: 5 suites / 36 tests passed
- Gradle: `BUILD SUCCESSFUL in 3m 41s`
- Gradle tasks: 505 executed
- bundle markers present: retry countdown, persisted retry deadline, both unchanged STOMP destinations, and scoped outbox storage
- DEX markers present: diagnostic builder, diagnostic title, URL redaction, and next-attempt report field
- status: debug-signed engineering artifact; build-verified, not runtime-proven

### Windows

- workflow: `30257268751`
- conclusion: success
- artifact ID: `8649445837`
- file: `ClipCascade-Windows-stability.exe`
- size: `57,456,040` bytes
- SHA-256: `871fac5fe6c4bade7fc58b65deb7301805c91078f3699d6d8a987e5a6c8b5c0f`
- artifact ZIP SHA-256: `acf9be28ea181de4c32f28adf56f4b745715a4898754be1070f09f707b62c633`
- format: PE32+ GUI executable, x86-64
- embedded checksum: matched independent calculation

### Linux

- workflow: `30257268751`
- conclusion: success
- artifact ID: `8649406597`
- file: `ClipCascade-Linux-stability.tar.gz`
- size: `60,405` bytes
- SHA-256: `7c6f780d170b4058430dd474b61514e69328b17ae2ee44c25745d5a41ddcaef7`
- artifact ZIP SHA-256: `fc0bdbcc2f10fd9ee5516038806fd15238efbc19311cf0a7b51deeea1823e07f`
- format: gzip-compressed Unix tar
- entries: `59`
- tar integrity: passed
- embedded checksum: matched independent calculation

## Retained build/process failures

Do not repeat these:

1. Run `30209073143`: missing standard React Native Gradle properties caused `hermesEnabled` failure. Required properties were restored.
2. Run `30209210999`: direct standalone build reached CMake before generated JNI directories existed. Reuse the proven order: `:app:testDebugUnitTest` before `:app:assembleStandalone`.
3. Replaced Shizuku run `30211379415`: AIDL generation was disabled. Correction was only `buildFeatures { aidl true }`; do not abandon the official AIDL/UserService design.
4. The initial outbox-status UI assumed a direct object, but the native sync bridge returns AsyncStorage objects as serialized JSON strings. The formatter now handles both forms.
5. The first outbox integration could apply an acknowledged queued self-echo and roll Android's clipboard from B back to A. Acknowledged own echoes are now consumed without local clipboard application.
6. One-use write/patch workflows and helpers were removed after use. Do not recreate them unless repository tooling makes a focused write impossible, and remove them immediately afterward.

Detailed failure evidence remains in the experiment logs.

## Explicitly not yet proven

### Android

- launch and setup-screen rendering on the user's HONOR device;
- Shizuku installation/start/permission/UserService binding on that device;
- expected shell UID in the real UserService;
- hidden clipboard Binder read on that Android/vendor build;
- foreground/background clipboard delivery to the existing public server and remote Windows device;
- automatic overlay fallback after Shizuku stop or denial;
- Amazon, launcher drawer, browser, selection toolbar, and search-field safety;
- real-device duplicate suppression when multiple triggers overlap;
- battery and wakeup behavior;
- queue-status rendering and live countdown on the device;
- offline A/B/C survival, ordering, retry, and own-echo suppression against the public server;
- process death/relaunch preserving queue and retry deadline;
- actual missing-echo exponential timing and 10-minute cap;
- image/file durability;
- true remote-application acknowledgement beyond the unchanged server echo;
- Sharesheet behavior and report readability;
- active guided capture → queue → server echo → remote-apply diagnostics.

### Desktop

- generated EXE against the public server;
- forced real network loss and recovery;
- actual tray rendering and manual controls on Windows;
- seamless in-process reauthentication after `AUTH_REQUIRED`;
- P2P migration to the snapshot contract;
- durable desktop outbound queue and application-level delivery acknowledgement;
- persistent full status window or diagnostics export bundle.

## Exact next actions

1. Install `ClipCascade-Android-stability-retry-diagnostics.apk`.
2. Start Shizuku through its normal wireless-debugging or computer-assisted setup.
3. Long-press ClipCascade and open `バックグラウンド設定`.
4. Confirm readable light/dark rendering and Shizuku installation/running/permission/UserService state.
5. Confirm the service UID is a shell UID and run the privacy-safe Shizuku read test.
6. Start the existing ClipCascade foreground service and verify one normal Android-to-Windows text copy.
7. Tap `診断レポートを共有`, share it into the working conversation, and confirm payloads, URLs, accounts, credentials, cookies, and keys are absent.
8. Disable Android networking without stopping the foreground service; copy A, B, and C; reconnect; verify ordered A/B/C delivery without duplicates.
9. Confirm Android's clipboard does not roll back to A or B when queued self-echoes return.
10. Repeat with process termination/relaunch between enqueue and reconnect.
11. Create a controlled missing-echo condition and confirm attempts plus a decreasing `Retry in` countdown.
12. Reopen/restart before the deadline and confirm no premature retry; observe growth toward the 10-minute cap.
13. Confirm the shared diagnostic report contains `Head next attempt epoch ms` while waiting.
14. Stop Shizuku and repeat a copy to verify overlay fallback.
15. Test launcher drawer, Amazon, browser, selection toolbar, and search fields for focus/input regressions.
16. Record missed sends, duplicates, report stages, queue state, countdown timings, wakeups, and battery behavior.
17. Run `ClipCascade-Windows-stability.exe` against the existing server and force network loss/restoration.
18. Add an active guided end-to-end diagnostic only by invoking the real existing capture/queue/echo stages, never a synthetic parallel runtime.

## Continuation checklist

A new thread must recover without archived patchwork:

- baseline, active branch, PR, and latest green product-code head;
- original Android/desktop requirements;
- no-wheel-reinvention and no-reset rules;
- required references;
- A11Y, Shizuku, P2S outbox, queue UI, retry, passive diagnostic, and desktop implementation boundaries;
- build-verified versus runtime/device-verified claims;
- latest workflows, artifacts, sizes, formats, and hashes;
- retained failures and corrections;
- exact next acceptance actions.
