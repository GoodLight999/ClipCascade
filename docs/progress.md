# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/TEST_MATRIX.md`

Current development phase: fetch the matching Android and Windows artifacts built with P2P clipboard-applied acknowledgement, then execute the HONOR 400 Pro / Windows test matrix. Keep PR #1 Draft until real-device validation and upstream regression checks pass.

## 2026-06-28 Android stability and redundancy work

Starting branch head: `912271763def1bf048f5bf626cf004c8b472e84b`.

- Read all canonical handoff documents in the required order.
- Confirmed that screen-off/locked SMS and email verification-code delivery is already an explicit product goal, verification requirement, Definition-of-Done item, handoff requirement, and test-matrix section. No duplicate requirement was added.
- Confirmed PR #1 remains open and Draft.
- Fetched the matching baseline artifacts from successful Android run `28304257167` and Windows run `28304257166`. The baseline APK contained `assets/index.android.bundle`.
- Investigation correction: an initial hypothesis that notification-listener rebind handling was missing was wrong. `NotificationCodeListenerService.onListenerDisconnected()` already calls `requestRebind(...)`; that implementation was preserved.

Implemented and CI-validated changes:

1. `852141452014ea7265ebbf7587f4d56e71f525b8` — added a delayed WorkManager heartbeat as a redundant boot-recovery path. The existing Headless JS boot path remains primary; if Android declines or blocks it, the delayed worker remains scheduled. The worker exits after a successful heartbeat and therefore does not restart an already healthy service. Android run `28304486580`: success.
2. `0ec58ffe0facb1934010f0270b8803ea65eb6a15` — made default-network handover tracking network-specific, so a late `onLost` callback for the old network cannot cancel recovery scheduled for the replacement Wi-Fi/mobile network. Android run `28304554055`: success.
3. `eb8dfe5a3de9d8e00faffbea5fb541b34dd1af5b` — corrected network recovery readiness parsing so `Disconnected` is not treated as containing a valid `Connected` state. Android run `28304618791`: success.
4. `1b95ccf7074d614a97521d519f3bb9b842a1f0f6` — applied the strict connected-state check to the persistent clipboard queue. Android run `28304684578`: success.
5. `230a10562456dc96d0f354e2d78e01123d10a800` — applied the strict connected-state check to the persistent verification-code queue. Android run `28304755556`: success.

The P2P Windows-applied ACK transformation scripts, relay IDs, peer ACK handling, fallback timer, queue deletion semantics, and Windows receiver were not modified by these changes. Every code commit above also completed the matching Windows CI successfully.

Next mandatory work:

- Download Android and Windows artifacts from the final documentation head so both display the same source commit.
- Install the APK on HONOR 400 Pro and run `docs/TEST_MATRIX.md`, beginning with settings and synthetic test relay.
- Measure real SMS and email notification fields with screen on, background, locked, and 1/15/30+ minute screen-off states.
- Record whether Android 16/MagicOS redacts OTP-like notification content. Do not claim screen-off reliability before those tests.
- Exercise Wi-Fi/mobile handover, Windows-offline queueing, process kill, reboot, and P2P clipboard-applied ACK on real devices.
