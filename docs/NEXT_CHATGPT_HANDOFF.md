# Next ChatGPT Handoff

Read these first, in this order:

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`
5. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
6. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
7. `docs/LATEST_ANDROID_INIT_DUPLICATE_HANDOFF.md`
8. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
9. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`

## Current branch and PR

- Branch: `stability-mobile-otp`
- PR: `#1`
- PR status: Draft; keep it Draft.

## Latest work

Windows tray ghost icon containment was added after the user reported many `ClipCascade` default/ghost tray icons with owner PID `0`. The patch is staged through `ClipCascade_Desktop/src/scripts/prepare_windows_tray_lifecycle.py` and covered by `tests.test_windows_tray_lifecycle`.

Key changes:

- one active pystray icon owner per process;
- replacing a panel disposes the previous icon first;
- every shutdown path uses idempotent `_dispose_tray_icon(reason)`;
- disposal order is `icon.visible = False` then `icon.stop()`;
- lifecycle logging records create/run/visible-false/stop with panel id, icon id, pid, and reason;
- watchdog full restarts are capped at three consecutive attempts and then back off for 15 minutes;
- fatal websocket scheme errors suppress full restart amplification;
- P2P logs runtime `websocket_url`, parsed scheme, `server_url`, close args, and latest transport error;
- remembered `scheme http is invalid - goodbye` is classified as fatal for watchdog purposes.

## Important caveat

At the time this handoff was written, the final GitHub Actions runs were still queued. Do not claim the Windows tray patch green until final Android and Windows CI complete successfully on the latest HEAD.

## Android status

The user reports the latest Android build is very stable, but battery use may be high. The most recent Android package identity remains:

- `3.2.1-extended.8-standalone`
- versionCode `320112`

Android idle-power changes reduced foreground-service polling, UI polling, and Accessibility event wakeups. P2P/WebRTC keepalive remains unchanged because stability is currently good.

## Validation reminders

Disable Phone Link and every competing clipboard synchronizer before Android outbound tests.

Windows tray validation must include:

1. normal startup -> one live tray icon;
2. forced signaling failure -> at least 10 reconnect/restart cycles -> still one tray icon;
3. Quit -> no `ClipCascade` ghost without restarting Explorer;
4. if `scheme http is invalid - goodbye` reappears, preserve adjacent P2P diagnostic lines.

Preserve Extended P2P peer-applied ACK and Android persistent queue semantics.
