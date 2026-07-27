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

## Canonical repository state

- Repository: `GoodLight999/Trial-and-Error-ClipCascade`
- Baseline and `main`: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Active branch: `stability-recovery`
- Draft PR: `#4`
- Latest green product-code head: `3ea7072231a7a3bea0a7ae4eab0c94090fe31103`
- Android workflow: `30276002653` — success
- Desktop workflow: `30276012794` — success

Documentation-only commits follow the product-code head. Product and artifact claims remain tied to `3ea70722...` until later product code is verified.

## Absolute rules

- No reset, reconstruction, re-baseline, clean rebuild, or replacement project.
- No wheel reinvention or parallel Android transport/capture/queue state owner.
- Keep `main` upstream-aligned; product work belongs on `stability-recovery`.
- Preserve server compatibility and destinations:
  - publish `/app/cliptext`
  - subscribe `/user/queue/cliptext`
- Root must not be required.
- CI success is not real-device proof.
- Preserve experiment failures, corrections, artifacts, and exact next actions.
- Remove temporary write workflows and patch helpers after use.
- Do not restore archived Android scaffolding from closed PR `#3`.

## Product goal

The highest priority is reliable Android background clipboard delivery through the existing ClipCascade foreground-service/STOMP transport.

Supported non-root paths:

- ordinary clipboard listener;
- conservative Accessibility trigger;
- Shizuku-compatible API/UserService;
- guided READ_LOGS/ADB trigger;
- overlay fallback;
- share sheet / PROCESS_TEXT.

Also required:

- duplicate suppression;
- persistent ordered P2S text outbox;
- bounded low-wakeup retry;
- beginner-readable setup and diagnostics;
- Windows reconnect/tray state;
- APK, Windows EXE, and Linux package generation.

## Android implementation state

### Capture pipeline

- Existing React Native/Notifee foreground service remains the only transport owner.
- Ordinary listener, Shizuku, and overlay return through one native emission/duplicate gate.
- Accessibility accepts only high-confidence copy signals.
- Generic clicks, generic selection, and Accessibility window-content retrieval are not used.
- Accessibility/READ_LOGS requests are coalesced.
- Overlay remains fallback for unavailable/denied/binding/failed/non-text Shizuku results.

### Shizuku and fork compatibility

Previous APK behavior:

- waited only for passive Shizuku Binder delivery;
- hardcoded official manager package/open/download behavior;
- reported only `Shizuku is not running` when no Binder arrived.

Real-device failure on HONOR DNP-NX9 / Android 16:

- Shizuku manager visible;
- Binder absent;
- permission absent;
- UserService absent;
- 7 attempts, 0 successes;
- installed manager is a fork.

Current product head:

- retains Shizuku API/provider `13.1.5`, AIDL, and read-only UserService;
- discovers compatible managers through `rikka.shizuku.intent.action.REQUEST_BINDER`;
- sends targeted Binder-request broadcasts at initialization and when permission/binding is requested without a Binder;
- retains official package only as a visibility fallback;
- also recognizes visible launcher identity containing Shizuku/Nightzuku;
- records manager label/version/package in payload-free failure text;
- opens the detected manager dynamically;
- opens this project's setup guide when none is discoverable;
- contains no official Shizuku download URL.

A fork that hides both receiver and launcher identity through stealth settings may require ClipCascade to be explicitly allowed.

Direct Shizuku output remains text-only. Image/file URI handling remains on the existing app-process path.

### Setup UI and runtime links

Previous HONOR evidence:

- setup Activity launched;
- vendor-resolved palette produced gray background with black/low-contrast text.

Correction:

- dedicated explicit light/dark theme for `BackgroundSetupActivity`;
- light text/background `#15161A` / `#FAFAFC`;
- dark text/background `#F2F3F7` / `#111318`;
- explicit button, status-bar, navigation-bar, and system-icon contrast.

Runtime links:

- App.js and runtime metadata use only `GoodLight999/Trial-and-Error-ClipCascade` for navigation/update/help;
- packaged runtime contains no Sathvik-Rao product URL;
- packaged runtime contains no `shizuku.rikka.app/download`;
- upstream attribution remains in repository documentation/license history only.

### P2S text outbox and retry

- Persistent bounded FIFO on existing AsyncStorage.
- Scoped by server/account/encryption fingerprint.
- Existing validation/encryption runs before persistence.
- One in-flight head.
- Matching server echo acknowledges/removes the head.
- Acknowledged own queued echo does not roll Android clipboard backward.
- Disconnect/error/shutdown/missing echo release the head.
- Restart converts persisted `inflight` to `queued`.
- Retry no-jitter sequence: `30s → 60s → 120s → 240s → 480s → 600s`.
- 10-minute cap and default ±20% jitter.
- `nextAttemptAt` persists across release/restart/reconnect.
- Existing UI displays count/state/bytes/attempts/drops/retry countdown.
- Diagnostic report exposes payload-free deadline metadata.
- P2P, images, and files do not use this durable outbox.

### Diagnostics

- Native setup screen shares through Android Sharesheet, not clipboard copy.
- Report includes device/capability/capture/connection/P2S-outbox metadata.
- It excludes payloads, hashes, server URLs, accounts, credentials, cookies, and keys.
- URLs and email addresses are redacted from free-form errors.
- `P2S text outbox: unavailable` is expected in P2P mode.

## Desktop implementation state

- Authoritative connection controller and immutable snapshots.
- Explicit disconnected/connecting/connected/reconnect/auth/stopping/fatal states.
- Capped exponential retry with jitter and stale-timer invalidation.
- Fresh STOMP client per attempt.
- No callback sleeps or recursive reconnect.
- Connected only after STOMP CONNECTED and subscription success.
- Automatic/manual reconnect, lost/restored notification, last send/receive state.
- GUI/CLI tray projection.
- Ubuntu/Windows tests plus EXE/Linux package generation.

## Latest verified artifacts

Product-code head: `3ea7072231a7a3bea0a7ae4eab0c94090fe31103`

### Android

- Workflow: `30276002653`
- Artifact ID: `8656949852`
- Build-log artifact ID: `8656947464`
- User-facing file: `ClipCascade-Android-stability-shizuku-fork-ui.apk`
- Size: `93,619,111` bytes
- SHA-256: `d235ab7c8de285c672cd7975ec08387ec535b2cbe03f9e68cdacf9535eff2efd`
- APK entries: `538`
- Exact `assets/index.android.bundle`: present
- ZIP integrity and embedded checksum: passed
- JavaScript: 5 suites / 38 tests passed
- Gradle: `BUILD SUCCESSFUL in 3m 51s`; 505 tasks executed
- Status: debug-signed engineering APK; build-verified, not device-verified

Packaged-runtime inspection confirmed Binder-request/reprobe markers, compatible-manager diagnostics, dedicated theme/color resources, recovery-project links, and absence of the official Shizuku download URL and Sathvik-Rao product URLs.

### Windows

- Workflow: `30276012794`
- Artifact ID: `8656885168`
- File: `ClipCascade-Windows-stability.exe`
- Size: `57,456,040` bytes
- SHA-256: `97f567ccc59ec98b3bc148f026201d3ec1887853ea133c34370070888157ba5b`
- Format: PE32+ GUI x86-64
- Embedded checksum: matched

### Linux

- Workflow: `30276012794`
- Artifact ID: `8656828720`
- File: `ClipCascade-Linux-stability.tar.gz`
- Size: `60,409` bytes
- SHA-256: `b717489dba07894d69a31615b5f86dada7612c0e51d25dbb3f25872f1b235c3f`
- Format: gzip Unix tar, 59 entries
- Integrity and embedded checksum: passed

## Real-device facts now established

Previous APK on HONOR DNP-NX9 / Android 16:

- setup Activity launches;
- foreground runtime active;
- Accessibility enabled;
- overlay enabled;
- battery exemption enabled;
- P2P connected;
- Shizuku manager visible but Binder/permission/UserService absent;
- 7 Shizuku attempts and 0 successes;
- overlay emitted 2 events;
- duplicate gate suppressed 2;
- previous setup palette unreadable.

Do not revert these concrete failures to “unproven.”

## Retained documentation-operation failures

- Commit `24f116b0895417b434692ca26db853b0e8bc5c51` temporarily replaced this Handoff with `# invalid` during a mistaken documentation update. It was immediately restored; product code/artifacts were unaffected.
- Commit `68d37b0d7030ddc82026f171439df31ab5617324` mistakenly shortened the restored Handoff during another documentation-only operation. This commit restores the complete canonical document. Do not use either temporary commit as a continuation source.

## Still unproven

Android:

- Binder delivery from the user's exact fork under its current stealth settings;
- permission/UserService/UID/read success on HONOR Android 16;
- corrected light/dark rendering on that device;
- background Android-to-server delivery;
- P2S A/B/C order, restart recovery, own-echo suppression, retry timing;
- Shizuku-stop overlay fallback;
- Amazon/launcher/browser/search-field non-interference;
- battery/wakeup behavior;
- image/file durability;
- remote-application acknowledgement.

Desktop:

- generated EXE against public server;
- forced real network-loss recovery and tray controls;
- seamless in-process reauthentication;
- P2P snapshot migration;
- application-level delivery acknowledgement.

## Exact next actions

1. Install `ClipCascade-Android-stability-shizuku-fork-ui.apk` over the prior engineering APK.
2. Start the installed Shizuku fork server.
3. Ensure stealth/application-hiding settings allow ClipCascade.
4. Open **バックグラウンド設定** and inspect light/dark contrast.
5. Tap **状態を再確認** or **Shizuku権限を許可して接続**.
6. If unavailable, share the new report. It should name the detected manager label/version/package or state no compatible receiver is visible.
7. If Binder is active, grant permission, verify UserService/UID, and run the privacy-safe read test.
8. Only after successful Shizuku read, continue background copy, dedup, overlay fallback, P2S A/B/C, restart, retry, and battery acceptance.
9. Record the result in the detailed experiment log and update this Handoff.
