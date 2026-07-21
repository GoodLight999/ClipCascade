# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/LATEST_SYSTEM_LOCALIZED_COPY_RECOVERY_HANDOFF.md`
5. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
6. `docs/LATEST_GREEN_ARTIFACTS.md`
7. `docs/LATEST_NOTIFICATION_LISTENER_ALPHA_HANDOFF.md`
8. `docs/LATEST_GMAIL_NOTIFICATION_RELIABILITY_HANDOFF.md`
9. `docs/TEST_MATRIX.md`
10. older focused handoffs linked from the current documents

Current phase: validate `3.2.1-extended.19-alpha.2 / 320124` on HONOR 400 Pro while preserving Extended P2P Windows-applied ACK and PR #1 Draft state.

## 2026-07-21 — target evidence corrected

The previous broad statement that synchronization recovered was wrong.

The user established:

- Windows-to-Android receive works while ClipCascade is not open;
- Android outbound Copy works while ClipCascade is open;
- Android outbound Copy fails while ClipCascade is not open;
- a real Gmail login-code notification was not relayed.

The broken area is Android background outbound capture and/or dispatch. Do not describe the full synchronization path as recovered.

## Original upstream mechanism

The original React Native Android client used normal clipboard callbacks where Android allowed them, plus an ADB-granted `READ_LOGS` monitor and a temporary focusable overlay when Android denied background clipboard access.

Extended intentionally removed that workaround because the product requirements prohibit ADB/READ_LOGS/overlay setup.

## Go fork findings

`wuxinkami/ClipCascade_go_fork` does include Android code.

- Accessibility detects broad Copy-related events and binds to a native foreground service.
- The foreground service owns a native Go synchronization engine and is sticky.
- It reads the clipboard through a transparent 1x1 overlay and therefore requests `SYSTEM_ALERT_WINDOW`.

Extended does not adopt its overlay hack. It adopts the useful ownership model: the live background transport runtime drains durable native queues.

## `.19-alpha.1`: Copy cue recovery

Implementation `dfff235dc293d75e28ca56787d1926a9cac33192` added:

- exact matching against Android's active-locale `android.R.string.copy` and `copyUrl` labels;
- Accessibility click/context-click cue detection;
- selection-only negative behavior;
- 700 ms serial-cancelled selected-text fallback;
- separate `copy_detection` diagnostics;
- a DAWN-shaped extractor regression using a synthetic value;
- CI guards against READ_LOGS and SYSTEM_ALERT_WINDOW.

## `.19-alpha.2`: foreground queue drain

Current implementation `ed9c009af0fcfc238cfc6264dd4c7b85a8fe82a3` additionally lets the existing Notifee foreground-service transport claim and send pending native OTP/clipboard queue items directly.

- tag `v3.2.1-extended.19-alpha.2`
- versionCode `320124`
- Android CI `29837847117`: success
- Windows CI `29837846863`: success
- Actions artifact `8498121042`
- ZIP SHA-256 `8c20eadce450c57fadb9eab39e465332fe34f8f25f7e28d44a072162dc480db3`
- APK SHA-256 `f9f7b5fe6653beb8d0b08436657ddf719fd155e3ac9b1216b0307b7ea1cf63a7`
- signer unchanged

This removes the exclusive dependency on a UI React context receiving a native event. It reuses the existing send and Extended ACK machinery.

## Gmail / DAWN

The DAWN-shaped synthetic extractor test passes. This means the supplied structure is parser-compatible if the full notification text reaches `OtpCodeExtractor`.

Real Gmail failure is therefore suspected earlier in listener delivery, filtering, extras exposure, or OEM lifecycle. This remains an inference until counters are recorded.

## Preserve

- no ADB, root, Shizuku, READ_LOGS, or overlay;
- no selection-only sends;
- internal-write suppression;
- native persistent queues;
- no ordinary queue TTL or accepted-item eviction;
- capacity 16 and `queue_full`;
- Windows application before peer ACK;
- peer ACK before native deletion;
- notification receipt guard and listener-path self-test;
- debug notification default OFF and ACK isolation;
- PR #1 open and Draft.

## Next proof

Install `.19-alpha.2` in place, disable competing synchronizers, enable debug temporarily, clear diagnostics, test foreground then background Copy, and record Copy detection, capture, foreground queue claim, local transport acceptance, Windows application, peer ACK, and queue deletion as separate boundaries.
