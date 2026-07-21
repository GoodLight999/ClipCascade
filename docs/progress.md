# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/LATEST_FOREGROUND_RUNNER_LIFECYCLE_HANDOFF.md`
5. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
6. `docs/LATEST_GREEN_ARTIFACTS.md`
7. `docs/LATEST_SYSTEM_LOCALIZED_COPY_RECOVERY_HANDOFF.md`
8. `docs/LATEST_NOTIFICATION_LISTENER_ALPHA_HANDOFF.md`
9. `docs/LATEST_GMAIL_NOTIFICATION_RELIABILITY_HANDOFF.md`
10. `docs/TEST_MATRIX.md`
11. older focused handoffs linked from the current documents

Current phase: validate `3.2.1-extended.20-alpha.1 / 320125` on HONOR 400 Pro while preserving Extended P2P Windows-applied ACK and PR #1 Draft state.

## 2026-07-21 — `.19-alpha.2` target failure

The user tested `.19-alpha.2` and established:

- the real NotificationListener-path self-test did not complete;
- Android outbound still failed whenever the main app UI was not open;
- inbound continued to work.

`.19-alpha.2` passed CI but failed its target goals. It is not a confirmed or partial fix.

## Go fork finding

`wuxinkami/ClipCascade_go_fork` owns its connection in a sticky native foreground service and binds Accessibility directly to it. It also uses a transparent overlay and `SYSTEM_ALERT_WINDOW` for clipboard reads.

Extended must preserve P2P Windows-applied ACK and the canonical no-overlay/no-ADB requirements. It therefore adopts only the service-ownership principle.

## Confirmed Notifee registration defect

The prior Extended code registered `notifee.registerForegroundService(...)` lazily when mounted UI/recovery code called `StartForegroundService()`.

The foreground runner must be registered at JavaScript entry, outside React components. A recreated process could therefore have a native foreground-service shell without the JavaScript transport runner that drains native queues.

## `.20-alpha.1` implementation

Implementation/release SHA: `290d6690e749fe34367b2383676faf27c0a4ba76`

Changes:

- register the Notifee runner from `index.js` before `AppRegistry.registerComponent`;
- one registration per process generation;
- registration-only path does not display/restart the notification;
- runner registered/start/heartbeat/stop diagnostics;
- heartbeat every 15 seconds; stale after 45 seconds;
- settings show active/no-fresh-heartbeat;
- component/listener tests request recovery when runner is stale;
- listener test notification lifetime extended to five minutes;
- delayed active scans at 0.5, 2, and 5 seconds;
- exact localized Copy candidate search expanded to parent, children, siblings, and action labels;
- selection alone remains inert;
- no Windows implementation change.

CI:

- Android `29843413287`: success
- Windows `29843413149`: success

Artifact:

- Actions artifact `8500372630`
- ZIP SHA-256 `ba2152241fdfe8c5bb99e3087b8781faa15915a281df3e10a9e8065fad177660`
- APK SHA-256 `1b48a7bb7e6d4ab757a3a044fda233e8363ce58d6d634b071e7f90a90ac35cbe`
- APK size `147937563` bytes
- signer SHA-256 `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- expiry `2026-10-19T15:19:19Z`

## Trial and error

- Android `29842404023`: failed before APK; runner-start transform assumed old `NativeModules` formatting.
- Android `29842757548`: failed before APK; resource anchors assumed the wrong existing English/Japanese recovery labels.
- Android `29843090432`: implementation passed after both anchors were corrected.
- Windows `29843090421`: passed.
- final release SHA Android `29843413287` and Windows `29843413149`: passed.
- `_probe_should_not_create.txt` was accidentally added and removed twice during connector staging. Net tree effect is zero; no force push was used.

## Preserve

- no ADB, root, Shizuku, READ_LOGS, or overlay;
- no selection-only sends;
- internal-write suppression;
- no ordinary queue TTL or accepted-item eviction;
- capacity 16 and explicit `queue_full`;
- relay IDs and bounded native claims;
- validation before ACK;
- Windows application before peer ACK;
- peer ACK before native deletion;
- old-peer fallback;
- notification receipt guard;
- debug notification default OFF and ACK isolation;
- PR #1 open and Draft.

## Next proof

Install `.20-alpha.1` in place. Before any test, verify the settings screen reports the foreground transport runner active. Then run component/transport, listener-path, and background Copy tests in that order while recording each boundary separately. CI is not target proof.
