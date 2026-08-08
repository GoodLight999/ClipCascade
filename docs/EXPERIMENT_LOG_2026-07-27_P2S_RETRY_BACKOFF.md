# Experiment Log — 2026-07-27 — Bounded P2S Echo Retry Backoff

## Goal

Reduce repeated Android wakeups and network traffic during a server or network outage without changing the existing ClipCascade server protocol, STOMP destinations, queue acknowledgement semantics, or foreground-service ownership.

This experiment extends the already implemented persistent P2S text outbox. It does not add another transport, polling loop, server endpoint, or synthetic diagnostic path.

## Existing path inspected

The implementation continues to use:

- the existing React Native/Notifee foreground service;
- the existing STOMP client;
- publish destination `/app/cliptext`;
- subscription destination `/user/queue/cliptext`;
- matching server echo as the current acknowledgement boundary;
- `P2STextOutbox` as the sole durable P2S text queue;
- the existing 300 ms status poller in `App.js`;
- the existing native setup screen and passive diagnostic report.

The prior outbox used a fixed 30-second missing-echo timeout. Repeating that interval indefinitely during a long outage would create avoidable wakeups and retry traffic.

## Retry policy

Added `ClipCascade_Mobile/src/P2SRetryPolicy.js`.

Default policy:

- attempt 1: 30 seconds before the next retry deadline;
- subsequent attempts: exponential doubling;
- maximum unjittered delay: 10 minutes;
- symmetric jitter: plus or minus 20 percent;
- final result is clamped to at least 1 ms and at most 10 minutes;
- invalid attempt numbers, bounds, jitter ratios, and random values fail explicitly.

The deterministic no-jitter sequence is:

`30s → 60s → 120s → 240s → 480s → 600s → 600s ...`

## Runtime integration

`StartForegroundService.js` continues to own the real P2S send path.

The integration:

- computes the retry delay immediately before an attempt;
- records the attempt and its `nextAttemptAt` deadline in the existing outbox item;
- schedules the existing one-shot drain timer for the computed delay;
- preserves `nextAttemptAt` when an in-flight item is released after disconnect, STOMP failure, WebSocket failure/close, publish failure, service shutdown, or missing echo;
- reloads persisted `nextAttemptAt` after process restart;
- on reconnect, waits until the persisted deadline instead of retrying immediately;
- clamps restored waits to the policy maximum;
- updates only payload-free outbox status metadata.

There is still one in-flight P2S text item and one retry timer. No periodic retry poller was introduced.

## Queue status UI

Commit `946dc76504be97937a3cd87b4d67e125bc00da65` added a payload-free countdown to the existing P2S queue line:

`Retry in: <seconds>s`

Commit `0bdaaf20a4a9cb56e58386334b25bb5e8d11902c` added deterministic tests for:

- future retry deadlines;
- expired deadlines;
- invalid deadlines;
- invalid `now` values;
- preservation of the existing count/state/bytes/attempts/drop display.

The countdown is a projection of persisted `headNextAttemptAt`; it is not a second retry state owner.

## Diagnostic report projection

The queue snapshot already contained `headNextAttemptAt`, but the native shareable diagnostic report did not expose it.

The following focused commits corrected that omission:

- `50596dc525bbba274473c07cb5d44a83c49398de` — add the retry deadline to `DiagnosticReportBuilder.OutboxState` and report output;
- `73b618842ce288641f8f934a76132c1ca88342c2` — extend JVM tests;
- `829f872c684c59f16a78bbe283b9e1260e20fce9` — parse `headNextAttemptAt` from the existing AsyncStorage outbox status JSON.

The report now includes:

`Head next attempt epoch ms: <value-or-none>`

This field is payload-free and contains no clipboard content, hash, server URL, account, credential, cookie, or key.

A comparison from `0bdaaf20...` to `829f872c...` confirmed exactly three modified files and no transport or protocol changes:

- `BackgroundSetupActivity.kt`;
- `DiagnosticReportBuilder.kt`;
- `DiagnosticReportBuilderTest.kt`.

## Temporary workflow cleanup

A one-use write-enabled workflow was used earlier to apply the focused retry integration because direct repository editing was constrained. Commit `6e61d35073a49bff6f4970a664b50c159a1cafc3` removed that workflow after use.

No write-enabled or one-shot patch workflow remains. The normal Android and desktop workflows are read-only verification/build workflows.

## Verification

### JavaScript

Latest Android workflow executed:

- 5 suites passed;
- 36 tests passed;
- 0 snapshots;
- suites: outbox state, outbox status, outbox integration, product contract, and retry policy.

Retry-policy tests cover:

- 30-second start and doubling;
- 10-minute cap;
- plus/minus 20 percent jitter bounds;
- deterministic custom bounds;
- invalid inputs.

Outbox and integration tests cover persisted `nextAttemptAt`, restart recovery, deadline-respecting reconnect behavior, FIFO, single-flight, server destinations, own-echo suppression, and release paths.

### Android JVM and packaging

Product-code head: `829f872c684c59f16a78bbe283b9e1260e20fce9`

Android workflow: `30257268532`

Passed:

- JavaScript reliability gate;
- Android JVM tests, including the diagnostic retry-deadline assertion;
- AIDL and Shizuku compilation;
- Kotlin and Android resources;
- standalone APK build;
- exact `assets/index.android.bundle` presence;
- APK ZIP integrity;
- checksum staging and artifact upload.

Gradle result:

- `BUILD SUCCESSFUL in 3m 41s`;
- 505 actionable tasks executed.

### Desktop

Same product-code head desktop workflow: `30257268751`

Passed:

- Ubuntu tests;
- Windows tests;
- Windows EXE generation;
- Linux source-package generation;
- checksums and artifact uploads.

## Independently inspected artifacts

All payload hashes below were recalculated after downloading and extracting the GitHub Actions artifact ZIPs. Embedded checksum files matched the independent calculations.

### Android

- artifact ID: `8649504009`
- build-log artifact ID: `8649502019`
- file: `ClipCascade-Android-stability-standalone.apk`
- user-facing copy: `ClipCascade-Android-stability-retry-diagnostics.apk`
- size: `93,617,791` bytes
- SHA-256: `9f8fefd4d32892e891e763590a443d4dbcc20534b7cba4260fabe8489589f106`
- artifact ZIP SHA-256: `6c48dc5619b11b031fcd970bb244a3bce90f15e283a9dacbb76b1920313d3937`
- APK entries: `538`
- exact `assets/index.android.bundle`: present
- APK ZIP integrity: passed
- bundle markers present: `Retry in:`, `headNextAttemptAt`, `/app/cliptext`, `/user/queue/cliptext`, and scoped outbox storage
- DEX markers present: `Head next attempt epoch ms`, `DiagnosticReportBuilder`, diagnostic report title, and URL-redaction marker
- status: debug-signed engineering artifact; build-verified, not runtime-proven

### Windows

- artifact ID: `8649445837`
- file: `ClipCascade-Windows-stability.exe`
- size: `57,456,040` bytes
- SHA-256: `871fac5fe6c4bade7fc58b65deb7301805c91078f3699d6d8a987e5a6c8b5c0f`
- artifact ZIP SHA-256: `acf9be28ea181de4c32f28adf56f4b745715a4898754be1070f09f707b62c633`
- format: PE32+ GUI executable, x86-64

### Linux

- artifact ID: `8649406597`
- file: `ClipCascade-Linux-stability.tar.gz`
- size: `60,405` bytes
- SHA-256: `7c6f780d170b4058430dd474b61514e69328b17ae2ee44c25745d5a41ddcaef7`
- artifact ZIP SHA-256: `fc0bdbcc2f10fd9ee5516038806fd15238efbc19311cf0a7b51deeea1823e07f`
- format: gzip-compressed Unix tar
- archive entries: `59`
- tar integrity: passed

## What this proves

- The retry policy is deterministic under test and bounded under all tested inputs.
- The real existing P2S outbox persists and projects the retry deadline.
- The service respects a persisted future deadline in source-contract and integration tests.
- The existing UI can display the remaining retry time without exposing payload data.
- The native diagnostic report compiles and includes the same deadline.
- APK, EXE, and Linux artifacts were generated and independently validated.

## What remains unproven

Real-device/runtime evidence is still required for:

- actual missing-echo timing against the public server;
- retry delay growth across repeated failures on the user's Android device;
- process death/relaunch preserving the real remaining delay;
- reconnect before the deadline not sending prematurely;
- countdown rendering and refresh behavior on the HONOR device;
- battery and wakeup improvement during a long outage;
- ordinary/background capture, Shizuku, overlay fallback, and A/B/C replay acceptance;
- Windows public-server reconnect behavior.

The unchanged server echo remains the acknowledgement boundary. This work does not claim remote application acknowledgement or exactly-once delivery.

## Exact acceptance test

1. Install the latest engineering APK and start the existing foreground service.
2. Confirm one ordinary Android-to-Windows text copy succeeds.
3. Create a controlled P2S failure in which the queued head does not receive its server echo.
4. Confirm the queue line shows attempts and a decreasing `Retry in` value.
5. Confirm the diagnostic report shows `headNextAttemptAt` while the item is waiting.
6. Reopen or restart the Android app before the deadline and confirm it does not retry immediately.
7. Observe successive failures and confirm the interval grows toward, but never beyond, 10 minutes.
8. Restore the connection/echo path and confirm the head is acknowledged, removed, and the next FIFO item drains.
9. Repeat the offline A/B/C and process-restart acceptance matrix from the persistent-outbox experiment.
10. Record timings, duplicates, clipboard rollback, wakeups, and battery observations before changing retry semantics again.
