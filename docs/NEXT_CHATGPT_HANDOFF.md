# Next ChatGPT Handoff

Read these first, in this order:

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. `docs/LATEST_BACKGROUND_CLIPBOARD_INTERMITTENT_HANDOFF.md`
5. `docs/LATEST_OTP_SELF_TEST_HANDOFF.md`
6. `docs/LATEST_BROAD_OTP_EXTRACTION_HANDOFF.md`
7. `docs/LATEST_OTP_EMAIL_EXTRACTION_HANDOFF.md`
8. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
9. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
10. `docs/LATEST_ANDROID_INIT_DUPLICATE_HANDOFF.md`
11. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
12. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`
13. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`

## Current branch and PR

- Branch: `stability-mobile-otp`
- PR: `#1`
- PR status: Draft; keep it Draft.

## Latest user report

Ordinary Android copy sharing became intermittent:

- often only works while the ClipCascade UI is open;
- sometimes works briefly in the background;
- exact first bad build unknown;
- Perceptron Network email code `8F92FE` may also have been missed.

Do not claim `.11` caused the ordinary-copy regression. `.11` did not touch the ordinary-copy source files.

## Latest repair

Android build target:

- `3.2.1-extended.12-standalone`
- versionCode `320116`

Validated code head before documentation commits:

- `7dda7214ed2f9cf35926dd5faedbd583b6d21341`
- Android CI `29629825353`: success
- Windows CI `29629825352`: success

Latest documentation head before this update: `6cc484e32007abf3641e18919fbe277c1f3702e4`. Check Android and Windows CI again after this final handoff update before distributing an artifact.

Implemented:

1. Restored single-source-node copy-marker inspection for clicked, context-clicked, announcement, notification-state, window-state, and window-content Accessibility events.
2. Added a three-second fallback using recent selected text plus `OnPrimaryClipChangedListener`.
3. Clears selected-text memory after completed queue/dedup handling.
4. Added `ClipboardWriteGuard` and final JavaScript/native markers so ClipCascade's own inbound/local clipboard writes do not echo back.
5. Added in-process React bootstrap when a persistent relay item outlives the React/Notifee generation.
6. React bootstrap remains allowed during the 60-second Android Service-start cooldown.
7. Added content-free delivery diagnostics for transport, peer, React context/event, and ACK stages.
8. Added the full Perceptron Network message as an extractor regression test expecting `8F92FE`.
9. Notification extraction now includes ticker text, safe nested Bundle text, and public-version text surfaces.
10. Authentication-looking notification misses record only `notification_extras / empty` or `notification_extras / no_match`.

Preserved:

- persistent clipboard and OTP queues;
- relay claim protection;
- Extended P2P peer-applied ACK;
- old-peer compatibility fallback;
- 3-second foreground-service flag polling;
- shared AsyncStorage lifecycle repair;
- deterministic signer.

## Mandatory next target-device proof

Disable Microsoft Phone Link and every competing clipboard synchronizer.

1. Install `.12 / 320116` over the current app without uninstalling.
2. Confirm Accessibility and synchronization remain enabled.
3. Copy unique values with UI visible, backgrounded, removed from recents, locked, and screen-off.
4. Include immediate Copy after selection and Copy after waiting more than three seconds.
5. After any miss, record only:
   - ordinary-copy pending queue count;
   - latest ordinary-copy diagnostic path/result;
   - latest recovery path/result.
6. Interpret:
   - queue `0`, no recent copy diagnostic -> capture miss;
   - queue `>0`, `react_context / rebind_requested` -> React generation absent;
   - queue `>0`, `transport_status / retrying` -> transport unavailable;
   - queue `>0`, `p2p_peer / retrying` -> no peer channel;
   - `react_event / emitted` then `peer_ack / acknowledged` -> peer-applied path completed.
7. Confirm exactly one Windows clipboard application per unique Copy.
8. Retry the Perceptron/Gmail notification and record whether verification diagnostics show `empty`, `no_match`, or `queued`.

## Important limitation

The `.12` code is CI-validated, not target-device validated. Do not claim background copy reliability, screen-off support, exactly-once delivery, or real Perceptron/Gmail extraction until the isolated device matrix passes.

Windows tray validation remains pending: startup, forced signaling failure, 10+ reconnect/restart cycles, tray Quit, and no ghost icon without restarting Explorer.
