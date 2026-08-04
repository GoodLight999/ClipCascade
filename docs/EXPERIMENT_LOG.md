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

Static verification does not prove target-device Shizuku, HONOR/MagicOS clipboard access, ACTION_COPY coverage, overlay focus behavior, live transport, tray/UI behavior, or battery performance.