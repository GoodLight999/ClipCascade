# ClipCascade Stability Recovery Handoff

Last updated: 2026-07-27 (Asia/Tokyo)

## Read first

1. `docs/HANDOFF.md`
2. `docs/EXPERIMENT_LOG.md`
3. `docs/EXPERIMENT_LOG_2026-07-27_DEVICE_SHIZUKU_REPROBE.md`
4. `docs/EXPERIMENT_LOG_2026-07-27_SHIZUKU.md`
5. `docs/EXPERIMENT_LOG_2026-07-27_SHIZUKU_BUILD.md`
6. `docs/EXPERIMENT_LOG_2026-07-27_CAPTURE_PIPELINE.md`
7. `docs/EXPERIMENT_LOG_2026-07-27_P2S_OUTBOX.md`
8. `docs/EXPERIMENT_LOG_2026-07-27_OUTBOX_STATUS_UI.md`
9. `docs/EXPERIMENT_LOG_2026-07-27_DIAGNOSTIC_REPORT.md`
10. `docs/EXPERIMENT_LOG_2026-07-27_P2S_RETRY_BACKOFF.md`
11. `docs/EXPERIMENT_LOG_2026-07-27_DESKTOP_RECOVERY.md`
12. Draft PR `#4` and latest Actions

## Canonical state

- Repository: `GoodLight999/Trial-and-Error-ClipCascade`
- Baseline and `main`: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Development branch: `stability-recovery`
- Draft PR: `#4`
- Latest green product-code head: `3ea7072231a7a3bea0a7ae4eab0c94090fe31103`
- Android workflow: `30276002653` — success
- Desktop workflow: `30276012794` — success

Documentation-only commits follow the product-code head. Use the PR head for current documents, but tie product and artifact claims exactly to `3ea70722...` until later product code is verified.

## Non-negotiable rules

- Do not reset, reconstruct, re-baseline, clean rebuild, or start a replacement project.
- Do not reinvent existing Android capture, send, queue, server, or desktop reconnect paths.
- Keep `main` aligned with upstream; product work remains on `stability-recovery`.
- Preserve existing server compatibility and destinations:
  - publish: `/app/cliptext`
  - subscribe: `/user/queue/cliptext`
- Do not require root.
- Do not equate CI success with real-device success.
- Record sources, hypotheses, failures, corrections, artifacts, and exact next actions.
- Delete temporary patch helpers and write-enabled workflows immediately after use.
- Do not restore archived Android scaffolding from closed PR `#3`.

## Product objective

Highest priority: stable Android background clipboard delivery through the existing ClipCascade transport.

Allowed non-root capture/trigger paths:

- ordinary clipboard listener;
- conservative Accessibility;
- Shizuku-compatible API and UserService;
- guided READ_LOGS/ADB;
- overlay fallback;
- share sheet / PROCESS_TEXT.

Also required:

- duplicate suppression;
- persistent ordered P2S text queue and retry;
- low wakeup/battery cost;
- beginner-readable setup;
- few-tap, payload-free diagnostics;
- Windows connection/reconnect/tray status;
- APK, Windows EXE, and Linux artifacts.

## Implemented Android state

### Capture

- Existing React Native/Notifee foreground service remains transport owner.
- Ordinary listener, Shizuku, and overlay all return through one native emission/duplicate gate.
- Accessibility accepts only high-confidence copy signals.
- Generic clicks, generic text selection, and Accessibility window-content retrieval are not used.
- Accessibility/READ_LOGS requests are coalesced.
- Overlay remains the fallback when Shizuku is absent, denied, binding, failed, or non-text.

### Shizuku

- API/provider `13.1.5`, AIDL, and read-only UserService are retained.
- Direct Shizuku output is text-only; image/file URI handling remains on the existing app-process fallback.
- Previous implementation passively waited for Binder delivery and hardcoded the official manager for opening/download.
- Real-device evidence showed manager installed but Binder never received: 7 attempts, 0 successes on HONOR DNP-NX9 / Android 16.
- Current implementation discovers official or forked managers using the standard `rikka.shizuku.intent.action.REQUEST_BINDER` receiver.
- It sends an explicit targeted Binder-request broadcast at initialization and when permission/binding is requested without a Binder.
- It records detected manager label/version/package in payload-free failure text.
- It opens the detected manager; if none is discoverable, it opens this project's setup guide.
- The official Shizuku download URL is absent from the runtime.
- A fork that hides both its receiver and launcher identity through stealth settings may still require ClipCascade to be allowed explicitly.

### UI and product links

- The previous HONOR real-device setup screen rendered with gray background and black/low-contrast text.
- `BackgroundSetupActivity` now has a dedicated explicit light/dark palette rather than vendor-resolved defaults.
- Light primary text/background: `#15161A` / `#FAFAFC`.
- Dark primary text/background: `#F2F3F7` / `#111318`.
- Status/navigation icon contrast is explicit in both modes.
- App.js and runtime metadata use only this recovery repository for product navigation/update/help.
- Packaged runtime contains no Sathvik-Rao product URL and no `shizuku.rikka.app/download` marker.
- Upstream attribution remains in repository documentation and license history, not product links.

### P2S text outbox

- Persistent bounded FIFO using existing AsyncStorage.
- Scoped by server/account/encryption fingerprint.
- Existing validation and encryption run before persistence.
- One item in flight.
- Matching server echo acknowledges/removes the head.
- Own queued echo is consumed without rolling Android clipboard backward.
- Disconnect/error/shutdown/missing echo release the head.
- Restart converts persisted `inflight` to `queued`.
- Retry policy: `30s → 60s → 120s → 240s → 480s → 600s`, capped at 10 minutes, default ±20% jitter.
- `nextAttemptAt` persists through release/restart/reconnect.
- Existing UI shows queue state and retry countdown.
- Diagnostic report exposes payload-free deadline metadata.
- Image/file and P2P do not use this durable outbox.

### Diagnostics

- Native setup screen shares a report through Android Sharesheet, not clipboard copy.
- Report includes capability, capture, connection, and bounded P2S outbox metadata.
- It excludes clipboard payloads, hashes, server URLs, usernames, credentials, cookies, and keys.
- URLs and email addresses in free-form errors are redacted.
- `P2S text outbox: unavailable` is expected when server mode is P2P.

## Implemented desktop state

- Authoritative connection controller and immutable snapshots.
- Explicit disconnected/connecting/connected/reconnect/auth/stopping/fatal states.
- Capped exponential retry with jitter and stale-timer invalidation.
- Fresh STOMP client per attempt.
- No callback sleeps or recursive reconnect.
- Connected only after STOMP CONNECTED plus successful subscription.
- Automatic/manual reconnect, lost/restored notification, last send/receive observations.
- GUI/CLI tray projection.
- Windows and Ubuntu tests plus EXE/Linux package generation.

## Latest verified artifacts

Product head: `3ea7072231a7a3bea0a7ae4eab0c94090fe31103`

### Android

- Workflow: `30276002653`
- Artifact ID: `8656949852`
- Build-log artifact ID: `8656947464`
- File: `ClipCascade-Android-stability-standalone.apk`
- User-facing file: `ClipCascade-Android-stability-shizuku-fork-ui.apk`
- Size: `93,619,111` bytes
- SHA-256: `d235ab7c8de285c672cd7975ec08387ec535b2cbe03f9e68cdacf9535eff2efd`
- APK entries: `538`
- Exact `assets/index.android.bundle`: present
- ZIP integrity: passed
- Embedded checksum: matched independent recalculation
- JavaScript: 5 suites / 38 tests passed
- Gradle: `BUILD SUCCESSFUL in 3m 51s`; 505 tasks executed
- Status: debug-signed engineering APK; build-verified, not device-verified

Independent packaged-runtime checks found Binder-request/reprobe markers, compatible-manager diagnostics, dedicated setup theme/colors, and recovery-project links. The official Shizuku download URL and Sathvik-Rao product URLs were absent.

### Windows

- Workflow: `30276012794`
- Artifact ID: `8656885168`
- File: `ClipCascade-Windows-stability.exe`
- Size: `57,456,040` bytes
- SHA-256: `97f567ccc59ec98b3bc148f026201d3ec1887853ea133c34370070888157ba5b`
- Format: PE32+ GUI x86-64
- Embedded checksum: matched independent recalculation

### Linux

- Workflow: `30276012794`
- Artifact ID: `8656828720`
- File: `ClipCascade-Linux-stability.tar.gz`
- Size: `60,409` bytes
- SHA-256: `b717489dba07894d69a31615b5f86dada7612c0e51d25dbb3f25872f1b235c3f`
- Format: gzip-compressed Unix tar
- Entries: `59`
- Integrity and embedded checksum: passed

## Real-device facts now established

Previous APK on HONOR DNP-NX9 / Android 16:

- setup Activity launches;
- foreground runtime active;
- Accessibility enabled;
- overlay enabled;
- battery exemption enabled;
- P2P connected;
- Shizuku manager package visible;
- Shizuku Binder absent;
- permission and UserService absent;
- 7 Shizuku attempts, 0 successes;
- overlay emitted 2 events and duplicate gate suppressed 2;
- previous setup palette was unreadable.

This is concrete failure evidence. Do not revert it to “unproven.”

## Still unproven

Android:

- new APK obtaining the Binder from the user's exact fork;
- effect of that fork's stealth/application-hiding settings;
- permission grant, UserService bind, UID, and clipboard read on HONOR Android 16;
- corrected light/dark rendering on that device;
- background Android-to-server delivery;
- A/B/C order, process-restart recovery, own-echo suppression, and retry timing against the public server;
- Shizuku-stop overlay fallback;
- Amazon/launcher/browser/search-field non-interference;
- battery/wakeup behavior;
- image/file durability;
- remote-application acknowledgement.

Desktop:

- generated EXE against the public server;
- forced real network-loss recovery and tray controls;
- seamless in-process reauthentication;
- P2P snapshot migration;
- application-level delivery acknowledgement.

## Exact next actions

1. Install `ClipCascade-Android-stability-shizuku-fork-ui.apk` over the previous engineering APK.
2. Start the installed Shizuku fork server.
3. Ensure the fork's stealth/application-hiding mode allows ClipCascade.
4. Open **バックグラウンド設定** and inspect both light and dark contrast.
5. Tap **状態を再確認** or **Shizuku権限を許可して接続**.
6. If still unavailable, share the new report. It must name the detected manager label/version/package or state that no compatible receiver is visible.
7. If Binder becomes active, grant permission, verify UserService and UID, then run the privacy-safe read test.
8. Only after Shizuku read succeeds, test background copy, overlap deduplication, overlay fallback, P2S offline A/B/C, restart recovery, retry timing, and battery behavior.
9. Record every result in the detailed experiment log and update this handoff.
