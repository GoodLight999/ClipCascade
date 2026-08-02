# ClipCascade Experiment Log

Append-only index and recovery boundary for `GoodLight999/Trial-and-Error-ClipCascade`.

Detailed experiment files retain the full hypotheses, source inspection, failed attempts, corrections, CI runs, artifacts, and runtime limitations. Do not rewrite a failure as if it never occurred.

## Permanent recovery boundary

- Baseline and `main`: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Active branch: `stability-recovery`
- Draft PR: `#4`
- Repository reset/re-baseline is complete and must not be repeated.
- Extend existing Android capture/transport and desktop paths; do not create parallel replacements.
- Preserve `/app/cliptext` and `/user/queue/cliptext`.
- Root must not be required.
- CI success is not runtime proof.
- Temporary write workflows and patch helpers must be removed after use.

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

The detailed records and canonical Handoff are the source of truth for continuation.

---

## 2026-07-27 — Repository recovery and conservative capture

Result:

- upstream-aligned baseline retained;
- existing React Native/Notifee foreground-service transport reused;
- broad generic-click and text-selection Accessibility triggers rejected;
- high-confidence copy signals only;
- no Accessibility window-content retrieval;
- existing overlay, READ_LOGS, share sheet, send path, and duplicate hashing retained.

Detailed evidence:

- `docs/EXPERIMENT_LOG_2026-07-27_CAPTURE_PIPELINE.md`
- `docs/EXPERIMENT_LOG_2026-07-27_SHIZUKU_BUILD.md`

---

## 2026-07-27 — SHIZUKU-001

Result:

- Shizuku API/provider `13.1.5`, AIDL, and UserService integrated;
- shell-side text clipboard read returns through the existing emission/send path;
- Accessibility and READ_LOGS try Shizuku first;
- existing overlay remains fallback;
- privacy-safe read test and capability status added;
- direct output intentionally text-only.

Build success did not prove real-device Binder delivery or hidden clipboard access.

Detailed evidence: `docs/EXPERIMENT_LOG_2026-07-27_SHIZUKU.md`.

---

## 2026-07-27 — OUTBOX-001

Result:

- persistent bounded FIFO for existing P2S text transport;
- offline enqueue and post-subscription drain;
- matching server echo acknowledges the in-flight head;
- disconnect/error/shutdown release;
- restart recovery;
- own queued echoes do not roll the Android clipboard backward;
- no server or STOMP destination change.

Detailed evidence: `docs/EXPERIMENT_LOG_2026-07-27_P2S_OUTBOX.md`.

---

## 2026-07-27 — OUTBOX-UI-001

Result:

- existing 300 ms status poller reused;
- queue count/state/bytes/attempt/drop display added;
- native serialized AsyncStorage JSON shape detected and handled;
- malformed/unloaded status fails closed;
- no payload data exposed.

Detailed evidence: `docs/EXPERIMENT_LOG_2026-07-27_OUTBOX_STATUS_UI.md`.

---

## 2026-07-27 — DIAGNOSTIC-REPORT-001

Result:

- native setup screen shares real state through Android Sharesheet;
- capability, capture, connection, and bounded P2S outbox metadata included;
- clipboard payloads, hashes, server URLs, accounts, credentials, cookies, and keys excluded;
- URLs and email addresses redacted from free-form errors;
- `headNextAttemptAt` included.

Detailed evidence: `docs/EXPERIMENT_LOG_2026-07-27_DIAGNOSTIC_REPORT.md`.

---

## 2026-07-27 — P2S-RETRY-001

Result:

- fixed repeated 30-second missing-echo retry replaced by bounded exponential policy;
- no-jitter sequence `30s → 60s → 120s → 240s → 480s → 600s`;
- 10-minute cap and default ±20% jitter;
- persisted `nextAttemptAt` respected across release/restart/reconnect;
- existing UI displays retry countdown;
- no retry poller, transport, server endpoint, or acknowledgement protocol added;
- one-use write workflow removed.

Detailed evidence: `docs/EXPERIMENT_LOG_2026-07-27_P2S_RETRY_BACKOFF.md`.

---

## 2026-07-27 — DESKTOP-001

Result:

- authoritative connection state controller;
- fresh STOMP client per attempt;
- connected only after STOMP CONNECTED and subscription;
- capped reconnect with jitter and stale-timer invalidation;
- no callback sleep or recursive reconnect;
- automatic/manual reconnect, lost/restored notifications, send/receive observations, tray projection;
- Ubuntu/Windows tests and Windows/Linux artifact generation.

Detailed evidence: `docs/EXPERIMENT_LOG_2026-07-27_DESKTOP_RECOVERY.md`.

---

## 2026-07-27 — DEVICE-SHIZUKU-REPROBE-001

Triggering real-device evidence:

- HONOR DNP-NX9, Android 16 / API 36;
- previous engineering APK setup Activity rendered but had gray-background/black-text contrast failure;
- Shizuku manager package visible;
- Shizuku Binder, permission, UserService, and UID unavailable;
- 7 Shizuku attempts and 0 successes;
- manager was a fork;
- P2P connection was active;
- P2S outbox unavailable was expected in P2P mode.

Source inspection incorrectly concluded that manually broadcasting `rikka.shizuku.intent.action.REQUEST_BINDER` was the required client correction.

Attempted correction:

- protocol-based manager discovery;
- targeted Binder reprobe;
- manager label/version/package diagnostics;
- manager launch selection;
- setup-screen palette and product-link changes.

Verified head `3ea7072231a7a3bea0a7ae4eab0c94090fe31103` built, but the real device still failed to obtain the Binder, foreground copies stopped sending, UI/link problems remained, and Windows had no visible window.

This experiment is retained as a failed design, not the current architecture.

Detailed evidence: `docs/EXPERIMENT_LOG_2026-07-27_DEVICE_SHIZUKU_REPROBE.md`.

---

## 2026-08-02 — OFFICIAL-SHIZUKU-AND-GUI-001

Triggering evidence:

- forked Shizuku still not recognized;
- `Binder requested from ...` was displayed without progress;
- copy while ClipCascade was visible did not send;
- upstream-linked product UI remained;
- gray/black poor-contrast UI remained;
- Windows EXE exposed no visible GUI.

Official-source conclusion:

- Accessibility is a copy-event trigger, not a supported cross-app clipboard-content API;
- Android 10+ ordinary background clipboard access is restricted;
- Shizuku client Binder acquisition requires the official `rikka.shizuku.ShizukuProvider` lifecycle;
- content read belongs in a Shizuku UserService using explicit AOSP clipboard Binder signatures;
- the ordinary foreground listener must remain independent and feed the existing React Native sender directly.

Rejected implementation:

- manual `REQUEST_BINDER` broadcast;
- manager package/label scanning;
- fork-name heuristics;
- missing Provider registration;
- generic “largest overload” hidden-API argument construction;
- shared native duplicate gate in front of the ordinary foreground listener;
- server-supplied product `DONATE` link;
- tray-only Windows result.

Accepted correction:

- explicit `rikka.shizuku.ShizukuProvider` registration;
- official Binder listener, permission, and `bindUserService` lifecycle;
- explicit AOSP `getPrimaryClip` signatures and AOSP user-ID formula;
- restored upstream ordinary `OnPrimaryClipChangedListener -> onClipboardChange` path;
- Accessibility remains trigger-only;
- Shizuku/overlay use a separate external-emission entrypoint;
- pure-white/high-contrast light palette and explicit dark palette across app/setup themes;
- product footers limited to `PROJECT / SETUP / SERVER`;
- visible Windows Tk status GUI integrated with the existing tray/controller.

Retained build failure:

- Android run `30749600073` failed because `Process.myUserHandle().identifier` was unavailable to the compile surface;
- corrected to AOSP `Process.myUid() / 100000` without changing Binder semantics.

Final product-code head:

`97ccb853f618ba7051c793fb35a488be9b405c68`

Android:

- workflow `30749894749` — success;
- artifact ID `8834136426`;
- build-log artifact ID `8834135678`;
- 5 suites / 40 JavaScript tests passed;
- Gradle `BUILD SUCCESSFUL in 3m 57s`;
- 505 Gradle tasks executed;
- APK size `93,619,299` bytes;
- APK SHA-256 `b85e40021c0a3a88bfc93768a2a05d96e397e107ff6961347ec149d9554ded55`;
- 538 entries, exact JS bundle, ZIP integrity, and embedded checksum passed;
- binary Manifest independently confirmed the official Provider, authority, and permission;
- packaged runtime contained official Binder/UserService, explicit AOSP, ordinary-listener, theme, recovery-link, and existing STOMP markers;
- rejected Binder action/status, upstream product URLs, and donation link were absent from the relevant packaged runtime sections.

Desktop:

- workflow `30749894743` — success;
- Windows and Ubuntu tests passed;
- Windows artifact ID `8834105537`;
- Windows size `57,490,610` bytes;
- Windows SHA-256 `49d6ab44198f0d7309658881d58751801f21275f2716bb2c2f9f34024aa56f94`;
- PE32+ Windows GUI x86-64;
- Linux artifact ID `8834089832`;
- Linux size `61,663` bytes;
- Linux SHA-256 `5a702d369658af4a1a05e14a06a98e659f5f0642c9a301f09349b1f780942356`;
- 60-entry gzip tar; checksums and archive integrity passed;
- packaged source contained the visible GUI entrypoints and GUI contract test.

Exact device/runtime test and source links:

- `docs/EXPERIMENT_LOG_2026-08-02_OFFICIAL_SHIZUKU_AND_GUI.md`.
