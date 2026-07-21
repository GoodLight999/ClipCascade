# Next ChatGPT Handoff

This is the canonical handoff.

## Hard constraints

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- PR: `#1`, open and Draft
- never mark Ready, merge, or enable auto-merge
- preserve Extended P2P Windows-applied ACK before native deletion
- run Android and Windows CI after every final branch change
- never claim target-device success from CI
- never commit real clipboard text, notification bodies, codes, accounts, or private URLs
- no ADB, root, Shizuku, READ_LOGS, or overlay workaround unless the user explicitly changes requirements

## Read first

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. `docs/NEXT_CHATGPT_HANDOFF.md`
5. `docs/LATEST_FOREGROUND_RUNNER_LIFECYCLE_HANDOFF.md`
6. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
7. `docs/LATEST_GREEN_ARTIFACTS.md`
8. `docs/LATEST_SYSTEM_LOCALIZED_COPY_RECOVERY_HANDOFF.md`
9. `docs/LATEST_NOTIFICATION_LISTENER_ALPHA_HANDOFF.md`
10. `docs/LATEST_GMAIL_NOTIFICATION_RELIABILITY_HANDOFF.md`

## Current implementation

- implementation/release SHA: `290d6690e749fe34367b2383676faf27c0a4ba76`
- commit: `[alpha-release] Publish foreground runner lifecycle alpha.20`
- version: `3.2.1-extended.20-alpha.1-standalone`
- versionCode: `320125`
- intended tag: `v3.2.1-extended.20-alpha.1`
- Android CI: `29843413287`, success
- Windows CI: `29843413149`, success
- Actions artifact ID: `8500372630`
- Actions ZIP SHA-256: `ba2152241fdfe8c5bb99e3087b8781faa15915a281df3e10a9e8065fad177660`
- APK SHA-256: `1b48a7bb7e6d4ab757a3a044fda233e8363ce58d6d634b071e7f90a90ac35cbe`
- APK size: `147937563` bytes
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- artifact expiry: `2026-10-19T15:19:19Z`

## Target evidence — do not soften

`.19-alpha.2` failed on HONOR:

- true NotificationListener-path self-test did not complete;
- outbound still failed whenever the main app UI was not open;
- inbound continued to work.

Thus `.19-alpha.2` did not restore background outbound. `.20-alpha.1` is untested on target.

## Go fork conclusion

`wuxinkami/ClipCascade_go_fork`:

- binds Accessibility to a sticky native service;
- owns its Go connection inside that service;
- uses a transparent overlay and `SYSTEM_ALERT_WINDOW` for actual background clipboard reads;
- disables mobile P2P and therefore cannot replace Extended's transport without losing peer-applied ACK.

Extended adopts only the lifecycle invariant: background transport registration/ownership must not depend on an activity.

## Confirmed Notifee lifecycle defect

Before `.20`, `notifee.registerForegroundService(...)` was invoked only when UI/recovery code called `StartForegroundService()`.

The runner must be registered outside React components at JavaScript entry. Android could recreate the native service process while no JavaScript transport runner had been registered. The `.19` queue poll could therefore be present in source and absent in the actual process generation.

## `.20-alpha.1` changes

### Entry registration

- `index.js` calls `StartForegroundService({registerOnly: true})` before `AppRegistry.registerComponent`;
- registration occurs synchronously before the first await;
- one registration per process generation;
- registration-only does not display/restart the service notification.

### Heartbeat

- content-free runner registered/start/heartbeat/stop timestamps;
- heartbeat every 15 seconds;
- stale after 45 seconds;
- settings show active/no-fresh-heartbeat;
- component/listener tests request bounded recovery when stale.

### Listener-path test

- no direct queue insertion;
- synthetic notifications live five minutes;
- delayed active scans at 0.5, 2, and 5 seconds after rebind.

### Copy cue expansion

- exact localized `copy` / `copyUrl` only;
- clicked node, parent, immediate children/siblings, and action labels;
- selection alone remains inert.

## Final Android transform order

`prepare_relay_claim.js` ends with:

1. `prepare_internal_clipboard_guard.js`
2. `prepare_language_neutral_clipboard_copy.js`
3. `prepare_ack_safe_queue_overflow.js`
4. `prepare_gmail_ja_anchor_compat.js`
5. `prepare_gmail_notification_reliability.js`
6. `prepare_debug_notification_icon_compat.js`
7. `prepare_notification_listener_alpha_hardening.js`
8. `prepare_system_localized_copy_recovery.js`
9. `prepare_foreground_queue_drain.js`
10. `prepare_foreground_runner_lifecycle.js`
11. `prepare_foreground_runner_lifecycle_fixups.js`

Do not move the last transforms earlier. They patch final generated service/listener/Accessibility source.

## Diagnostic test order

1. Install `.20-alpha.1` in place.
2. Disable Phone Link and competing synchronizers.
3. Open Extended settings and record runner active/inactive.
4. Clear diagnostics and enable outbound debug temporarily.
5. Run deterministic component/transport test.
6. Record queue, foreground claim, debug, Windows once, peer ACK, deletion.
7. Run true listener-path test.
8. Record listener connected/test/seen/eligible/text/auth/queued, runner claim, debug, Windows, ACK/deletion.
9. Leave main UI without force-stop and explicitly Copy one unique value.
10. Record selection, framework cue, capture queue, runner claim, debug, Windows, ACK/deletion.
11. Only after basic success test recents removal, lock, screen-off, disconnect durability, and queue-full.

## Failure map

- runner inactive: Notifee/process lifecycle still broken;
- component queued, no runner claim: foreground poll/module failure;
- runner claim, no debug: transport acceptance failure;
- listener connected but test unseen: HONOR listener delivery/rescan failure;
- listener text/auth present but no queue: extractor/receipt boundary;
- selection remembered but no Copy cue: target app does not expose usable Copy event;
- Copy queued but no runner claim: foreground drain failure;
- debug but no Windows: peer transport/application;
- Windows applied but queue remains: ACK/native deletion.

## Trial and error

- Android `29842404023`: transform failed at old `NativeModules` formatting; no APK.
- Android `29842757548`: transform failed at wrong existing resource labels; no APK.
- Android `29843090432` and Windows `29843090421`: implementation green.
- Android `29843413287` and Windows `29843413149`: final release SHA green.
- `_probe_should_not_create.txt` was accidentally added/removed twice; net tree effect zero; no force push.

## Preserve exactly

- internal-write echo suppression;
- no selection-only sends;
- ordinary queue no TTL and no accepted-item eviction;
- capacity 16 and `queue_full`;
- relay IDs and bounded native claims;
- validation before ACK;
- Windows apply before peer ACK;
- peer ACK before native deletion;
- old-peer generation-scoped fallback;
- notification receipt guard;
- debug notification default OFF and ACK isolation.

PR #1 remains Draft. CI is not HONOR proof.
