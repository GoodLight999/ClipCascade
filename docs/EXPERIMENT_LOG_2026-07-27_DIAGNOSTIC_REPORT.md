# Experiment Log — 2026-07-27 — Payload-Free Diagnostic Report

## Goal

Move toward the original “open the app and tap through debugging” requirement without repeating the failed synthetic diagnostics design from archived PR #3.

This milestone does not create a second React Native runtime, Activity-owned transport, synthetic clipboard event, or parallel diagnostic state machine. It aggregates states already owned by the real setup, capture, connection, and outbox paths.

## Implementation

Added `DiagnosticReportBuilder.kt` and a `診断レポートを共有` button to the existing native `BackgroundSetupActivity`.

The report combines:

- generation time, package version/build type, device model, Android release/API;
- Shizuku installed/running/permission/UserService/UID/error state;
- Accessibility, overlay, READ_LOGS, battery-exemption, and foreground-runtime state;
- real `CaptureDiagnostics` counters and last source/stage/error/time;
- existing AsyncStorage `server_mode`, `wsIsRunning`, and `wsStatusMessage` state;
- bounded P2S text-outbox count/bytes/drop/head-state/attempt timestamps.

The setup Activity remains a viewer/launcher only. It does not own network transport or clipboard acquisition.

## Why Android Sharesheet is used

A “copy diagnostic report” button would write the report to Android’s clipboard. With ClipCascade active, that report could itself be captured and synchronized to other devices.

The implementation therefore uses `Intent.ACTION_SEND` and the Android Sharesheet. The user explicitly chooses ChatGPT, a notes app, email, or another destination. The report is never placed on the clipboard by this feature.

## Privacy boundary

The report input types do not contain:

- clipboard payloads;
- content hashes;
- server URLs;
- usernames;
- passwords or derived keys;
- cookies;
- encryption material.

Free-form connection/error strings are additionally:

- stripped of control/format characters;
- collapsed to one line;
- bounded to 300 characters;
- scanned for `http`, `https`, `ws`, and `wss` URLs and replaced with `[redacted-url]`;
- scanned for email addresses and replaced with `[redacted-email]`.

Malformed/missing outbox JSON fails closed to `Status: unavailable` rather than exposing raw storage.

## Tests

Added four `DiagnosticReportBuilderTest` cases covering:

1. operational stage/capability inclusion without sensitive fields;
2. control-character normalization, length bounding, URL redaction, and email redaction;
3. missing outbox handling without invented zero state;
4. negative counter clamping.

Existing JavaScript reliability gate also remained green:

- 4 suites passed;
- 26 tests passed.

Android Gradle verification passed, including the new JVM tests, Shizuku/AIDL compilation, Kotlin/resources, standalone APK, exact JS bundle, ZIP integrity, checksum, and upload.

## Independent artifact inspection

Branch product head: `9277b67d8c0797014e17489a59d3c4aca64e97eb`

Android:

- workflow: `30250829837`
- APK artifact ID: `8647003886`
- build-log artifact ID: `8647002070`
- file: `ClipCascade-Android-stability-standalone.apk`
- user-facing file: `ClipCascade-Android-stability-diagnostics.apk`
- size: `93,615,651` bytes
- SHA-256: `2af9f94dff4f8447001378da780591b970d8adfe0698357f42ef6eb826fbd785`
- APK entries: `538`
- exact `assets/index.android.bundle`: present
- DEX/resource inspection found `DiagnosticReportBuilder`, `ClipCascade diagnostic report`, `[redacted-url]`, `Shizuku UserService`, and `share_diagnostic_report`
- JavaScript: 4 suites / 26 tests passed
- Gradle: `BUILD SUCCESSFUL in 3m 55s`

Desktop on the same head:

- workflow: `30250829830`
- Windows artifact ID: `8646940303`
- Windows size: `57,456,040` bytes
- Windows SHA-256: `45eedcd94b3d6f953639716002e63218d2b2f5d6e304f1c0f9f4b72ca502b39f`
- Linux artifact ID: `8646901511`
- Linux size: `60,409` bytes
- Linux SHA-256: `7f61bcbfa4073ed7af20391fe2a3b0d3ff9ba65c52a3d578553c5f15efd39908`
- Linux entries: `59`

## What this proves

- The report builder and setup-screen integration compile.
- The privacy/redaction rules are JVM-tested.
- A standalone APK containing the report code was produced.
- The diagnostic report uses real state owners rather than a synthetic React Native roundtrip.

## What remains unproven

- the Sharesheet opens correctly on the user’s HONOR device;
- report formatting/readability in light and dark modes;
- actual values returned by the user’s AsyncStorage/database state;
- whether vendor WebSocket error text includes forms not covered by URL/email redaction;
- whether the report alone is sufficient to identify every missed clipboard stage;
- a guided active end-to-end test that deliberately performs capture → queue → server echo → remote apply.

## Exact acceptance action

After installing the latest APK:

1. long-press ClipCascade and open `バックグラウンド設定`;
2. start the foreground service and perform at least one copy attempt;
3. return to the setup screen and tap `診断レポートを共有`;
4. share the report into the ChatGPT conversation;
5. verify no clipboard payload, server URL, account name, password, cookie, or key appears;
6. use the stage counts and connection/outbox state to choose the next focused fix.

---

## 2026-07-27 follow-up — persisted retry deadline

The bounded P2S retry implementation added `headNextAttemptAt` to the real outbox snapshot. The initial diagnostic report still stopped at `headLastAttemptAt`, so it could not distinguish “queued and immediately eligible” from “queued but deliberately waiting for backoff.”

Focused correction:

- `DiagnosticReportBuilder.OutboxState` now includes `headNextAttemptAt`;
- `BackgroundSetupActivity` parses that field from the existing `p2sTextOutboxStatus` JSON;
- the report prints `Head next attempt epoch ms: <value-or-none>`;
- JVM tests assert both a real value and the `none` case;
- no payload, hash, server/account identifier, credential, cookie, or key field was added.

Commits:

- `50596dc525bbba274473c07cb5d44a83c49398de`
- `73b618842ce288641f8f934a76132c1ca88342c2`
- `829f872c684c59f16a78bbe283b9e1260e20fce9`

Latest superseding verification:

- product-code head: `829f872c684c59f16a78bbe283b9e1260e20fce9`
- Android workflow: `30257268532` — success
- desktop workflow: `30257268751` — success
- JavaScript: 5 suites / 36 tests passed
- Gradle: `BUILD SUCCESSFUL in 3m 41s`
- APK artifact ID: `8649504009`
- APK size: `93,617,791` bytes
- APK SHA-256: `9f8fefd4d32892e891e763590a443d4dbcc20534b7cba4260fabe8489589f106`
- independent DEX scan found the new `Head next attempt epoch ms` marker

Detailed retry/backoff evidence is recorded in `docs/EXPERIMENT_LOG_2026-07-27_P2S_RETRY_BACKOFF.md`.
