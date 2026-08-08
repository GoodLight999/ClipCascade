# ClipCascade Experiment Log

Append-only recovery index for `GoodLight999/Trial-and-Error-ClipCascade`.

Detailed files retain hypotheses, source inspection, failed attempts, corrections, CI evidence, artifacts, and runtime limitations. Never rewrite a failed experiment as if it did not occur.

## Permanent boundary

- Baseline and `main`: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Active branch: `stability-recovery`
- Draft PR: `#4`
- Do not reset, reconstruct, re-baseline, or replace the architecture.
- Extend existing Android and Desktop paths.
- Preserve `/app/cliptext` and `/user/queue/cliptext`.
- Root must not be required.
- CI success is not runtime proof.
- Delete temporary write workflows/helpers after use.

## Detailed records

- `docs/EXPERIMENT_LOG_2026-07-27_CAPTURE_PIPELINE.md`
- `docs/EXPERIMENT_LOG_2026-07-27_SHIZUKU.md`
- `docs/EXPERIMENT_LOG_2026-07-27_SHIZUKU_BUILD.md`
- `docs/EXPERIMENT_LOG_2026-07-27_P2S_OUTBOX.md`
- `docs/EXPERIMENT_LOG_2026-07-27_OUTBOX_STATUS_UI.md`
- `docs/EXPERIMENT_LOG_2026-07-27_DIAGNOSTIC_REPORT.md`
- `docs/EXPERIMENT_LOG_2026-07-27_P2S_RETRY_BACKOFF.md`
- `docs/EXPERIMENT_LOG_2026-07-27_DESKTOP_RECOVERY.md`
- `docs/EXPERIMENT_LOG_2026-07-27_DEVICE_SHIZUKU_REPROBE.md`
- `docs/EXPERIMENT_LOG_2026-08-02_OFFICIAL_SHIZUKU_AND_GUI.md`
- `docs/EXPERIMENT_LOG_2026-08-03_STATIC_AUDIT_AND_UNIFIED_CAPTURE.md`
- `docs/EXPERIMENT_LOG_2026-08-04_FINAL_STATIC_VERIFICATION.md`
- `docs/EXPERIMENT_LOG_2026-08-08_HONOR_ANDROID16_VENDOR_CLIPBOARD.md`
- `docs/EXPERIMENT_LOG_2026-08-08_HONOR_FIX_VERIFICATION.md`
- `docs/EXPERIMENT_LOG_2026-08-08_SHIZUKU_OFFICIAL_SOURCE_REAUDIT.md`

## Chronology

### 2026-07-27 — Conservative capture recovery

- upstream-aligned baseline retained;
- existing React Native/Notifee transport reused;
- broad Accessibility click/selection triggers rejected;
- overlay and READ_LOGS retained;
- build success explicitly separated from runtime proof.

### 2026-07-27 — Initial Shizuku integration

- API/provider 13.1.5, AIDL, and UserService added;
- shell-side text read returned through the existing sender;
- Accessibility/READ_LOGS attempted Shizuku first;
- overlay fallback retained;
- real-device Binder acquisition remained unproved.

### 2026-07-27 — P2S durable text outbox

- persistent bounded FIFO;
- one in-flight head;
- matching echo acknowledgement;
- restart recovery;
- bounded exponential retry with persisted deadline;
- queue/status UI and payload-free diagnostics.

### 2026-07-27 — Desktop recovery

- authoritative reconnect controller;
- fresh STOMP client per attempt;
- stale-timer invalidation;
- visible status projection and tray integration;
- Ubuntu/Windows tests and Windows/Linux packages.

### 2026-07-27 — Failed manual Shizuku reprobe

Real-device evidence on HONOR/MagicOS showed no Binder, no Shizuku success, gray UI, and foreground-copy regression.

The attempted manager scan/manual `REQUEST_BINDER` broadcast design is retained as failed history and must not return.

### 2026-08-02 — Official Shizuku lifecycle and visible GUI

- official `ShizukuProvider` lifecycle adopted;
- explicit AOSP clipboard signatures and UID/user relation;
- manual manager discovery removed;
- high-contrast Android UI;
- visible Windows Tk status GUI;
- initial build/artifact evidence recorded.

This stage still incorrectly kept foreground automatic acquisition separate from background acquisition.

### 2026-08-03 — Unified capture and strict static audit

User correction: foreground and background automatic copies must exercise the same acquisition mechanism.

Accepted architecture:

```text
listener / exact ACTION_COPY / optional READ_LOGS
    -> BackgroundClipboardCapture
    -> Shizuku UserService first
    -> overlay fallback
    -> onClipboardChange
    -> existing sender
```

Major corrections:

- foreground direct payload read removed;
- Accessibility wording/toast/button heuristics removed;
- arbitrary debounce/delay gates removed;
- causal nanosecond trigger coalescing added;
- explicit app-owned clipboard marker added;
- obsolete duplicate gate and one-shot suppression removed;
- process-local native share queue added;
- foreground-service generation race fixed;
- strict ESLint exposed and fixed real JavaScript defects;
- dependency high/critical findings removed without forcing CLI 20;
- AsyncStorage shared-database ownership and JSON escaping fixed;
- Accessibility export/binding declaration corrected;
- APK version corrected to `3.2.0 / 30200`;
- permanent full/prod audit and packaging gates added;
- every one-shot workflow/helper and accidental scratch file removed.

Full failure/correction history is in `EXPERIMENT_LOG_2026-08-03_STATIC_AUDIT_AND_UNIFIED_CAPTURE.md`.

### 2026-08-04 — Final static verification and packaged artifacts

Verified source head: `4a169516e2c5f87f3a4175a683ea7aff50b464c9`.

Android run `30819520542` succeeded:

- full/prod high-severity dependency gates;
- syntax and ESLint zero warnings;
- 11 Jest suites / 61 tests;
- Android lint, JVM tests, standalone APK;
- embedded JS bundle, ZIP integrity, checksum, aggregate gate.

Android artifact:

- artifact `8858357801`;
- APK `93,543,179` bytes;
- SHA-256 `f0e6bee697d3304e6804ae3bab77369868144dd3ae869005b5fa806b6d720c4b`;
- 538 entries;
- binary Manifest confirmed `3.2.0 / 30200`, Shizuku provider, Accessibility export/system permission, and absence of battery allowlist-request permission.

Desktop run `30819520353` succeeded:

- Ubuntu and Windows tests;
- Windows artifact `8858276449`, SHA-256 `bf12b82fff45447d6a8c4d01d65b7bcb9f144176a711143a4529a3a0c673ce09`;
- Linux artifact `8858226619`, SHA-256 `60187d87a11ce399fec366419264a808b9994f74bfe5631f807e6929693cc925`;
- extracted Linux compile/tests and archive checks passed.

Android lint is not warning-free: 0 errors / 18 reviewed warnings. Seven moderate CLI dependency findings remain; no high or critical findings remain.

Static verification did not prove target-device Shizuku, HONOR/MagicOS clipboard access, ACTION_COPY coverage, overlay focus behavior, live transport, tray/UI behavior, or battery performance.

### 2026-08-08 — HONOR Android 16 vendor clipboard ABI failure

First target-device test after the static campaign reached Shizuku Binder, permission, and UserService UID 2000 successfully, but every clipboard read failed before emission.

Observed HONOR runtime hidden signature:

```text
IClipboard#getPrimaryClip(String, String, int, int, String)
```

Current AOSP uses four parameters. The previous implementation's exact AOSP hidden-signature enumeration therefore failed on the HONOR vendor extension.

Correction:

- do not guess the fifth vendor argument;
- remove application-side direct hidden `IClipboard` reflection from the UserService;
- use the Shizuku v13 supplied Context and a same-user `com.android.shell` package Context;
- acquire the device framework `ClipboardManager` and call `primaryClip`;
- let the device's own framework adapt to its vendor Binder ABI;
- add a regression contract forbidding direct hidden Binder reflection/overload synthesis in this path;
- separate Shizuku UserService implementation version from the unchanged app versionCode so a new engineering APK cannot silently reconnect to the old privileged implementation.

Code commits:

- `744f0efff09d245f0dd5b0a0ec4073e4d1c28e85` — framework ClipboardManager delegation;
- `7af159555a61653953ed26817500d53b94c0a23e` — OEM-boundary regression contract;
- `2fc18d16269287ce3c54c72c3e5a20ca28ebcdfa` — independent UserService implementation version;
- `5d2f99c3ff591195f71b6179d5f19528645e5c4d` — stale-service regression contract.

Full source/research detail is in `EXPERIMENT_LOG_2026-08-08_HONOR_ANDROID16_VENDOR_CLIPBOARD.md`.

### 2026-08-08 — HONOR correction build verification

The correction exposed additional issues before a new APK was accepted:

- run `31227720486`: new `image-size` high advisories plus one stale Jest assertion made the aggregate red, while Android compilation/package generation itself succeeded;
- stale test was replaced with the new framework-delegation contract;
- because the two `image-size` high advisories had no patched release, an exact temporary CI exception was introduced instead of forcing the breaking React Native downgrade suggested by npm;
- run `31228471408`: the first exception script mishandled npm-audit cycles among Metro packages, so the aggregate correctly remained red;
- audit policy was corrected to validate the exact two GHSA records, exact Metro/image-size dependency shape, an `effects` transitive closure, and an expiry date; all other high/critical findings still fail.

Final product-code head `30c179d9099f67afaa2b4e93a5164fd5ceae5808` passed Android run `31228888126`:

- 11/11 Jest suites, 61/61 tests;
- Gradle `BUILD SUCCESSFUL in 2m 57s`, 480 tasks;
- lint 0 errors / 18 reviewed warnings;
- full/prod exact advisory policy: success;
- APK/package/integrity/aggregate gates: success.

Final APK artifact `9013102582`:

- size `93,543,179` bytes;
- SHA-256 `1dfe70fa14be54ddb8f319c12b820e297dca8743ecbdc501f7a97c48d922c604`;
- 538 entries;
- independent DEX inspection confirmed the new `com.android.shell`/framework ClipboardManager implementation and absence of the old unsupported-signature error/helper markers.

Desktop run `31228888150` also passed all jobs.

This is build/package proof only. The same HONOR DNP-NX9 must still pass the manual Shizuku read before the runtime bug is called fixed. Full verification detail is in `EXPERIMENT_LOG_2026-08-08_HONOR_FIX_VERIFICATION.md`.

### 2026-08-08 — Official Shizuku/AOSP source re-audit after the 08:21 report

The repeated HONOR diagnostic was timestamped `08:21:04.855 JST`; the framework-delegation fix was committed at `08:27:43 JST`. Therefore that report came from the old hidden-Binder implementation and does not constitute a failed run of the accepted correction.

Before touching product code again, the current correction was re-audited against official Shizuku-API source and AOSP Android 16 source. The audit confirmed the v13 Context constructor behavior, UserService tag/version replacement semantics, Android 16's UID/package verification, `com.android.shell`'s background-clipboard permission, and the framework `ClipboardManager` as the correct owner of device-specific hidden Binder arguments.

No new product patch was made. The next experiment is the first real-device execution of the already-built corrected APK. Full findings are in `EXPERIMENT_LOG_2026-08-08_SHIZUKU_OFFICIAL_SOURCE_REAUDIT.md`.
