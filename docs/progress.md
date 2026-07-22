# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/LATEST_GO_OVERLAY_CLIPBOARD_RECOVERY_HANDOFF.md`
5. `docs/LATEST_FOREGROUND_RUNNER_LIFECYCLE_HANDOFF.md`
6. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
7. `docs/LATEST_GREEN_ARTIFACTS.md`
8. `docs/LATEST_SYSTEM_LOCALIZED_COPY_RECOVERY_HANDOFF.md`
9. `docs/LATEST_NOTIFICATION_LISTENER_ALPHA_HANDOFF.md`
10. `docs/LATEST_GMAIL_NOTIFICATION_RELIABILITY_HANDOFF.md`
11. `docs/TEST_MATRIX.md`
12. older focused handoffs linked from the current documents

Current phase: `3.2.1-extended.21-alpha.1 / 320126` is Android/Windows CI-green. Next, install it in place and test the Go-proven overlay clipboard-acquisition path on HONOR 400 Pro while preserving Extended P2P Windows-applied ACK and PR #1 Draft state.

## 2026-07-21 — `.19-alpha.2` target failure

The user tested `.19-alpha.2` and established:

- the real NotificationListener-path self-test did not complete;
- Android outbound failed whenever the main app UI was not open;
- inbound continued to work.

`.19-alpha.2` passed CI but failed its target goals. It is not a confirmed or partial fix.

## 2026-07-21 — `.20-alpha.1` runner lifecycle repair

Implementation/release SHA: `290d6690e749fe34367b2383676faf27c0a4ba76`.

Changes:

- register the Notifee runner from `index.js` before `AppRegistry.registerComponent`;
- one registration per process generation;
- registration-only path does not display/restart the notification;
- runner registered/start/heartbeat/stop diagnostics;
- heartbeat every 15 seconds; stale after 45 seconds;
- settings show active/no-fresh-heartbeat;
- component/listener tests request recovery when runner is stale;
- listener test notification lifetime five minutes;
- delayed active scans at 0.5, 2, and 5 seconds;
- exact localized Copy candidate search expanded to parent, children, siblings, and action labels;
- selection alone remains inert;
- no Windows implementation change.

CI:

- Android `29843413287`: success;
- Windows `29843413149`: success.

Artifact:

- Actions artifact `8500372630`;
- ZIP SHA-256 `ba2152241fdfe8c5bb99e3087b8781faa15915a281df3e10a9e8065fad177660`;
- APK SHA-256 `1b48a7bb7e6d4ab757a3a044fda233e8363ce58d6d634b071e7f90a90ac35cbe`;
- APK size `147937563` bytes;
- signer SHA-256 `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`;
- expiry `2026-10-19T15:19:19Z`.

Corrected limitation: `.20-alpha.1` repaired transport-runner registration, but it did not reproduce the known-working Go clipboard-acquisition path. It therefore had insufficient evidence for reliable background Copy capture.

## 2026-07-22 — complete Go Android analysis

Reference repository: `wuxinkami/ClipCascade_go_fork`.  
Reference revision: `0ff3ba4b28daccc1a51e7c09907792bc0f8e53a8`.

Files inspected:

- `mobile/android/app/src/main/AndroidManifest.xml`;
- `ClipCascadeBackgroundService.kt`;
- `ClipCascadeAccessibilityService.kt`;
- `BootReceiver.kt`;
- `MainActivity.kt`;
- Accessibility XML configuration;
- gomobile bridge;
- Go mobile engine.

Confirmed known-success structure:

1. Accessibility starts and binds a sticky native foreground service.
2. The service owns the Go engine and connection independently of the Activity.
3. Copy-like Accessibility events request a clipboard read.
4. The service creates a fully transparent 1×1 `TYPE_APPLICATION_OVERLAY` view.
5. It keeps the view non-touchable while omitting `FLAG_NOT_FOCUSABLE`.
6. It reads `ClipboardManager.primaryClip` and removes the view immediately.
7. The Go transport uses STOMP/P2S and intentionally disables mobile P2P.
8. The Go implementation does not have Extended's durable outbound queue or Windows-applied peer ACK.

Conclusion: import the overlay acquisition condition, not the Go transport.

## 2026-07-22 — `.21-alpha.1` implementation and green validation

Implementation/release SHA: `2f08e03b325eeff18ec63b1be8cbe1b08cb4f85d`.

Changes:

- version `3.2.1-extended.21-alpha.1-standalone`, versionCode `320126`;
- `prepare_overlay_clipboard_acquisition.js` runs last after lifecycle fixups;
- declares `SYSTEM_ALERT_WINDOW`;
- adds an explicit reliable-background fallback switch, default ON but user-disableable;
- adds Display over other apps permission status and launcher;
- English/Japanese explanation states 1×1, transparent, non-touchable, Copy-only, and immediate removal;
- direct clipboard read remains first;
- when direct read is unavailable and permission is present, adds the Go-equivalent overlay, reads, and removes in `finally`;
- diagnostic paths distinguish direct read, overlay read, missing permission, denial, empty read, and add failure without storing content;
- unit policy verifies overlay runs only when enabled, authorized, and still required;
- Android CI requires the overlay structure and still verifies selection-only negative behavior, queue capacity/no-TTL, foreground claim, and ACK plumbing;
- alpha release workflow and release notes updated to `.21-alpha.1`;
- transport/ACK implementation unchanged.

Exact-SHA CI:

- Android `29888733469`: success;
- Windows `29888733458`: success.

Android artifact:

- Actions artifact `8517492289`;
- ZIP SHA-256 `9bae0a27c80ddd6b16d9e8d7df95153ad080cd4cd0b864ec5eebc5d914370c87`;
- APK SHA-256 `93b85d2bd8474c874d8937e76c09ec97dde90006c4b1e8d97f448557a959c7a9`;
- APK size `147945851` bytes;
- signer diagnostics artifact `8517490898`;
- signer diagnostics ZIP SHA-256 `51c29398a4bfed2699ef79268d51d73be30b762785d2257551f127c0a52f4f62`;
- signer SHA-256 `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`;
- expiry `2026-10-20T03:33:01Z`.

The downloaded Actions ZIP digest matched GitHub's artifact digest. APK hash/size and signer diagnostics were independently verified.

CI proof includes production transforms, overlay structure, selection-only negative policy, durable queue/ACK assertions, JS bundle, Kotlin/resources/unit tests, APK assembly, embedded bundle, stable signer, and artifact upload. Windows peer-ACK and validation-before-ACK tests remained green.

## Trial and error

Retained earlier runs:

- Android `29842404023`: failed before APK; runner-start transform assumed old `NativeModules` formatting.
- Android `29842757548`: failed before APK; resource anchors assumed wrong existing English/Japanese labels.
- Android `29843090432`: `.20` implementation passed after both anchors were corrected.
- Windows `29843090421`: passed.
- final `.20` release SHA Android `29843413287` and Windows `29843413149`: passed.
- `_probe_should_not_create.txt` was accidentally added and removed twice; net tree effect zero; no force push.

Current `.21` staging history:

- a temporary branch-only workflow was added to try pre-final Android validation;
- GitHub did not report a run for that newly introduced non-default-branch workflow, so it was not counted as validation;
- the temporary workflow was removed before advancing `stability-mobile-otp`;
- a fast-forward ref update alone did not expose an Actions run;
- a normal contents commit (`2f08e03b...`) was made to trigger exact-SHA push/PR CI;
- both required workflows then completed successfully;
- no force push, Ready conversion, merge, or auto-merge occurred.

## Preserve

- no ADB, root, Shizuku, or `READ_LOGS`;
- overlay only through explicit user authorization and the narrow 1×1 Copy-only implementation;
- no selection-only sends or overlay creation;
- internal-write suppression;
- no ordinary queue TTL or accepted-item eviction;
- capacity 16 and explicit `queue_full`;
- relay IDs and bounded native claims;
- validation before ACK;
- Windows application before peer ACK;
- peer ACK before native deletion;
- generation-scoped old-peer fallback;
- notification receipt guard;
- debug notification default OFF and ACK isolation;
- PR #1 Open and Draft.

## Next proof

1. Install `.21-alpha.1` in place without uninstalling.
2. Enable the reliable fallback and grant Display over other apps.
3. Verify the foreground runner is active with a fresh heartbeat.
4. Run component/transport, true listener-path, and background explicit Copy tests in that order.
5. Require `overlay_clipboard_manager`, native queue, foreground claim, Windows apply, peer ACK, and native deletion.
6. Verify selection without Copy never creates an overlay or sends.
7. Only after simple success test recents removal, lock, screen-off, long disconnect, and queue-full.

CI is not target proof.
