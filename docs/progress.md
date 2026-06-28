# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/TEST_MATRIX.md`
5. `docs/LATEST_RUNTIME_FIXES_HANDOFF.md`
6. `docs/LATEST_PRIORITY1_VALIDATION_HANDOFF.md`
7. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`

Current phase: repair and prove Android outbound sending while the app is backgrounded. PR #1 remains Draft.

## 2026-06-28 — Android background-send failure

### Evidence correction

- Microsoft Phone Link clipboard synchronization was active during earlier tests.
- The apparent Android copied-text and Yahoo! JAPAN SMS deliveries were not produced by ClipCascade.
- With competing synchronization excluded, Android outbound sending failed when ClipCascade was not visible.
- Peer-to-Android reception still worked in the background and apparently with the screen off.
- All prior Android outbound and real-SMS success claims are withdrawn.

Future validation must disable Phone Link and every other clipboard synchronization utility.

### Root causes found

1. Android 10+ denies ordinary clipboard reads when the app is neither focused nor the default IME. Accessibility had been used as a trigger, followed by a foreground-only `ClipboardManager` read.
2. The selected-text fallback was lost when selection events were missed or the selection collapsed before delayed capture.
3. Native clipboard and OTP queues retried but did not request recovery when transport or React state was unavailable.
4. The first recovery request could be incorrectly suppressed during the first 60 seconds after boot.
5. Startup changed persisted synchronization intent to OFF after a missing heartbeat instead of recreating the service.
6. Windows watchdog logging warned during normal ICE negotiation even though the supplied run later reached `ICE completed`.

### Repairs implemented

- Accessibility now observes copy toolbar/window cues, includes non-important views, recognizes `ACTION_COPY`, attempts immediate capture, retains selection for 60 seconds, and scans interactive windows for the live selected range.
- It still requires an explicit Copy cue and queues only the selected substring.
- Clipboard and OTP dispatchers request bounded recovery for offline transport, missing React context, or failed event delivery.
- The first recovery request is no longer incorrectly cooldown-suppressed; repeated failures remain bounded.
- Startup now attempts to restart a missing previous service generation.
- Test wording targets connected devices rather than Windows.
- Windows watchdog delays its unhealthy warning for ten seconds while retaining the 25-second restart threshold.
- Android repair identity is `3.2.1-extended.5-standalone`, versionCode `320109`, using the existing deterministic test signer.

### Failed attempt recorded

Android run `28316609479` failed during AAPT resource linking. Kotlin uses `TYPE_VIEW_CONTEXT_CLICKED`, but the XML enum is `typeContextClicked`, not `typeViewContextClicked`.

Commit `f3ca3b45c51a48f18e9811b06786c030a7b818bb` corrected the XML spelling. The failure did not involve transport or ACK behavior.

### ACK protection

The following remain intact:

- native persistent queues;
- `relayId` and `ackRequested` metadata;
- peer ACK only after validated clipboard application;
- ACK-envelope handling before clipboard parsing;
- delayed old-peer fallback;
- native ACK-based deletion;
- queue wakeup after `SHARED_TEXT` listener registration.

### Mandatory next proof

1. Obtain a green Android and Windows artifact from the same current runtime head.
2. Disable Phone Link clipboard sync and every competing clipboard utility.
3. Update the stable-signed Android app in place.
4. Test Android outbound with the app visible, backgrounded for 30 seconds, removed from recents, locked, and screen-off.
5. Record clipboard diagnostics, pending queue count, recovery diagnostics, exact peer clipboard application, and ACK result.
6. Do not restore an Android success claim until the isolated test passes.

## Earlier work retained

The branch already contains:

- repaired Windows authentication and authenticated HTTP validation;
- visible Windows status, recovery controls, rotating logs, and Quit hardening;
- bilingual Android guided setup;
- local notification-code extraction and synthetic test path;
- persistent clipboard and OTP queues;
- Extended P2P Windows-applied ACK;
- boot, heartbeat, network-handover, and WorkManager recovery infrastructure;
- deterministic development/test signing;
- removal of obsolete ADB, READ_LOGS, overlay, upstream update, funding, and footer UI.

Detailed chronology and historical failed attempts remain in the Git history and the `LATEST_*_HANDOFF.md` files.
