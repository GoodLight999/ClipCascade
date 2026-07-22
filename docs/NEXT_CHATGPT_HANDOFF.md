# Next ChatGPT Handoff

This is the canonical handoff.

## Hard constraints

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- PR: `#1`, Open and Draft
- never mark Ready, merge, enable auto-merge, or force-push
- preserve Extended P2P Windows-applied ACK before native deletion
- run Android and Windows CI after every final branch change
- never claim target-device success from CI
- never commit real clipboard text, notification bodies, codes, accounts, or private URLs
- no ADB, root, Shizuku, or `READ_LOGS`
- the only permitted overlay path is the explicit user-authorized, fully transparent 1×1, non-touchable, immediately removed clipboard-acquisition fallback described below

## Repository roles — do not blur them

1. `Sathvik-Rao/ClipCascade` is upstream and the formal primary source.
2. `GoodLight999/ClipCascade` is the Extended repair repository.
3. `wuxinkami/ClipCascade_go_fork` is not upstream. It is the surviving fork of a vanished Go improvement project whose Android background outbound implementation was known to work. Treat it as a successful reference implementation, not a speculative example.

## Read first

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. `docs/NEXT_CHATGPT_HANDOFF.md`
5. `docs/LATEST_GO_OVERLAY_CLIPBOARD_RECOVERY_HANDOFF.md`
6. `docs/LATEST_FOREGROUND_RUNNER_LIFECYCLE_HANDOFF.md`
7. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
8. `docs/LATEST_GREEN_ARTIFACTS.md`
9. `docs/LATEST_SYSTEM_LOCALIZED_COPY_RECOVERY_HANDOFF.md`
10. `docs/LATEST_NOTIFICATION_LISTENER_ALPHA_HANDOFF.md`
11. `docs/LATEST_GMAIL_NOTIFICATION_RELIABILITY_HANDOFF.md`

## Current implementation candidate

- implementation/release candidate SHA: `2ede4caf7b59b0cb9d56f06aa976e4d61c63be18`
- version: `3.2.1-extended.21-alpha.1-standalone`
- versionCode: `320126`
- intended tag: `v3.2.1-extended.21-alpha.1`
- Android CI: pending final-branch validation
- Windows CI: pending final-branch validation
- artifact metadata: pending successful final Android run
- signer must remain: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

Do not publish the alpha prerelease until matching Android and Windows CI are green on the exact release SHA. Do not use `[alpha-release]` on a documentation-only handoff commit.

## Target evidence — do not soften

`.19-alpha.2` failed on HONOR:

- true NotificationListener-path self-test did not complete;
- Android outbound failed whenever the main app UI was not open;
- inbound continued to work.

`.20-alpha.1` has not been proven on the HONOR target. It repaired foreground-runner registration/lifecycle, but it did not reproduce the Go implementation's overlay-based clipboard acquisition. Therefore `.20-alpha.1` never had sufficient evidence for reliable background Copy capture.

`.21-alpha.1` restores the known Go clipboard-acquisition condition, but it also remains unproven until the target matrix passes.

## Complete Go-fork conclusion

Inspected Go revision: `0ff3ba4b28daccc1a51e7c09907792bc0f8e53a8`.

The known-working Android path:

1. Accessibility runs independently of the Activity.
2. Accessibility starts and binds a sticky native foreground service.
3. The foreground service owns the gomobile Go engine and connection.
4. Copy-like Accessibility events are debounced.
5. The service briefly adds a fully transparent 1×1 `TYPE_APPLICATION_OVERLAY` view.
6. The overlay is non-touchable but intentionally not `FLAG_NOT_FOCUSABLE`.
7. The service reads `ClipboardManager.primaryClip` and immediately removes the overlay.
8. The Go engine sends via STOMP/P2S; mobile P2P is deliberately disabled.
9. The Go implementation has no durable outbound queue and no Windows-applied peer ACK.

Import only the successful Android lifecycle/clipboard-acquisition structure. Never import the mobile-P2P disable or treat a WebSocket write as delivery proof.

## `.20-alpha.1` lifecycle repair retained

- `index.js` registers the Notifee foreground runner before `AppRegistry.registerComponent`;
- one registration per process generation;
- registration-only does not display/restart the notification;
- content-free runner registered/start/heartbeat/stop timestamps;
- heartbeat every 15 seconds, stale after 45 seconds;
- settings expose runner active/stale;
- tests can request bounded recovery;
- listener-path test uses the real listener path with delayed active scans;
- Copy detection uses semantic `ACTION_COPY` and exact Android framework-localized Copy/Copy URL labels;
- selection alone remains inert.

## `.21-alpha.1` clipboard acquisition

The final transform `prepare_overlay_clipboard_acquisition.js` runs after all runner/listener transforms and:

- declares `SYSTEM_ALERT_WINDOW`;
- adds a user setting, default ON, that can be disabled;
- adds permission status and a settings shortcut in English and Japanese;
- first attempts a normal clipboard read;
- if the direct value is unavailable, the option is ON, and permission is granted, creates a 1×1 fully transparent `TYPE_APPLICATION_OVERLAY` view;
- keeps `FLAG_NOT_TOUCHABLE | FLAG_NOT_TOUCH_MODAL` and intentionally omits `FLAG_NOT_FOCUSABLE`;
- reads the actual clipboard and calls `removeViewImmediate` in `finally`;
- records only content-free acquisition paths;
- passes the value into the existing durable `ClipboardRelayStore`;
- does not alter transport acceptance, peer ACK, native acknowledgement, or deletion code.

Selection does not call capture. The overlay can be reached only from the existing explicit Copy/OS clipboard-change capture path.

## Overlay requirement decision

Overlay-free mode remains available, but it is not claimed to be Go-equivalent on Android 10+ or HONOR/MagicOS. If the user keeps the overlay disabled, reliable background capture cannot currently be guaranteed.

The allowed overlay must be:

- explicitly authorized through Android's Display over other apps setting;
- enabled/disabled by the user;
- created only after an explicit Copy path requests capture;
- 1×1, alpha 0, transparent, and non-touchable;
- removed immediately even after read failure;
- prohibited from changing queue or ACK semantics.

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
12. `prepare_overlay_clipboard_acquisition.js`

Do not move the final transform earlier. It patches the final generated Accessibility/settings/manifest sources and must not be overwritten.

## Required target test order

1. Install `.21-alpha.1` over the existing signed build; do not uninstall.
2. Disable Phone Link and all competing clipboard synchronizers.
3. Open Extended settings.
4. Enable the reliable background clipboard fallback and grant Display over other apps.
5. Confirm `Foreground transport runner: active` / `常駐通信ランナー: 動作中` and a fresh heartbeat for at least 60 seconds.
6. Clear diagnostics and temporarily enable outbound debug.
7. Run deterministic component/transport test.
8. Require native queue, foreground claim, debug after transport acceptance, one Windows apply, peer ACK, and native deletion.
9. Run the true NotificationListener-path test and record connected/test/seen/eligible/text/auth/queued/claim/debug/Windows/ACK/deletion.
10. Leave the main UI without force-stop and explicitly Copy one unique value.
11. Require Copy detection, `overlay_clipboard_manager`, queue, foreground claim, debug, one Windows apply, peer ACK, and deletion.
12. Select text without Copy and require no overlay/capture/queue/Windows change.
13. Disable the overlay and repeat a background Copy as an explicit control. A failure there demonstrates the non-overlay fallback is insufficient; it does not invalidate the overlay path.
14. Only after simple success test recents removal, lock, screen-off, disconnect durability, and queue-full.

## Failure map

- runner inactive: Notifee/process lifecycle failure;
- component queued, no runner claim: foreground poll/module failure;
- runner claim, no debug: transport acceptance failure;
- Copy cue but `overlay_permission_missing`: setup failure;
- Copy cue and `overlay_add_failed`: WindowManager/OEM overlay failure;
- `overlay_clipboard_empty` or denied: overlay did not obtain clipboard access on target;
- selection remembered but no Copy cue: target app exposes no usable explicit Copy event;
- capture queued but no claim: foreground drain failure;
- debug but no Windows: peer transport/application failure;
- Windows applied but queue remains: peer ACK/native deletion failure;
- listener connected but test unseen: HONOR listener delivery/rescan failure;
- listener text/auth but no queue: extractor/receipt boundary.

## Preserve exactly

- internal-write echo suppression;
- no selection-only sends;
- ordinary queue has no TTL and never evicts accepted items;
- capacity 16 and `queue_full`;
- opaque relay IDs and bounded native claims;
- validation before ACK;
- Windows apply before peer ACK;
- peer ACK before native deletion;
- old-peer generation-scoped fallback;
- notification receipt guard;
- debug notification default OFF and ACK isolation.

## Trial and error

Earlier retained failures:

- Android `29842404023`: transform anchor failure; no APK.
- Android `29842757548`: localized resource anchor failure; no APK.
- Android `29843090432` and Windows `29843090421`: `.20` implementation green.
- Android `29843413287` and Windows `29843413149`: `.20` release SHA green.
- `_probe_should_not_create.txt` was accidentally added/removed twice; net tree effect zero; no force push.

Current `.21` work:

- fully inspected Go Android service, Accessibility, manifest, boot receiver, gomobile bridge, and Go engine;
- corrected the earlier incomplete claim that `.20` had sufficient background-capture evidence;
- added the Go-proven overlay acquisition transform without modifying Extended transport/ACK code;
- added a temporary branch-only Android validation workflow; remove it before advancing `stability-mobile-otp`;
- final Android/Windows run IDs and artifact hashes must be appended after exact-SHA green validation.

PR #1 must remain Draft. CI is not HONOR proof.
