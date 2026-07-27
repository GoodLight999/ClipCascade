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

The earlier monolithic entries were consolidated into the detailed append-only files and canonical Handoff. The records above preserve the implementation/failure evidence and are the source of truth for continuation.

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

Source inspection found that the previous implementation waited only for passive Binder delivery and never sent the standard `rikka.shizuku.intent.action.REQUEST_BINDER` action, although both official and inspected fork managers expose a compatible receiver.

Accepted correction:

- protocol-based official/fork manager discovery;
- explicit targeted Binder reprobe at initialization and on permission/binding attempts;
- manager label/version/package in payload-free errors;
- detected manager opened dynamically;
- official Shizuku download URL removed from runtime;
- runtime metadata upstream product links removed;
- dedicated explicit light/dark setup-screen palette;
- source-contract tests for reprobe, links, and theme.

Verified product-code head:

`3ea7072231a7a3bea0a7ae4eab0c94090fe31103`

Android:

- workflow `30276002653` — success;
- artifact ID `8656949852`;
- build-log artifact ID `8656947464`;
- 5 suites / 38 JavaScript tests passed;
- Gradle `BUILD SUCCESSFUL in 3m 51s`, 505 tasks;
- APK size `93,619,111` bytes;
- APK SHA-256 `d235ab7c8de285c672cd7975ec08387ec535b2cbe03f9e68cdacf9535eff2efd`;
- 538 entries, exact JS bundle, ZIP integrity and embedded checksum passed.

Desktop same-head workflow:

- workflow `30276012794` — success;
- Windows artifact ID `8656885168`;
- Windows size `57,456,040` bytes;
- Windows SHA-256 `97f567ccc59ec98b3bc148f026201d3ec1887853ea133c34370070888157ba5b`;
- Linux artifact ID `8656828720`;
- Linux size `60,409` bytes;
- Linux SHA-256 `b717489dba07894d69a31615b5f86dada7612c0e51d25dbb3f25872f1b235c3f`;
- PE32+ x86-64 and 59-entry gzip tar independently confirmed;
- embedded checksums matched.

Packaged APK inspection found Binder request/reprobe and explicit theme resources, while the official Shizuku download URL and Sathvik-Rao product URLs were absent.

Still unproven:

- Binder delivery from the user's exact fork and its current stealth settings;
- permission/UserService/UID/read success on HONOR Android 16;
- corrected palette on that device;
- background delivery and full acceptance matrix.

Detailed evidence and exact device test: `docs/EXPERIMENT_LOG_2026-07-27_DEVICE_SHIZUKU_REPROBE.md`.
