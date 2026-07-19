# Next ChatGPT Handoff

This is the canonical handoff. Read the listed documents before changing code.

## Repository and safety state

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- PR: `#1`
- PR state: open and Draft
- never mark Ready, merge, or weaken the Extended P2P ACK path

## Read in this exact order

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. `docs/NEXT_CHATGPT_HANDOFF.md`
5. `docs/LATEST_ACK_SAFE_QUEUE_OVERFLOW_HANDOFF.md`
6. `docs/LATEST_ACK_SAFE_CLIPBOARD_QUEUE_HANDOFF.md`
7. `docs/LATEST_LANGUAGE_NEUTRAL_COPY_HANDOFF.md`
8. `docs/LATEST_GREEN_ARTIFACTS.md`
9. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
10. `docs/TEST_MATRIX.md`
11. `docs/LATEST_SELECTION_ONLY_COPY_FALSE_POSITIVE_HANDOFF.md`
12. `docs/LATEST_BACKGROUND_CLIPBOARD_INTERMITTENT_HANDOFF.md`
13. `docs/LATEST_OTP_SELF_TEST_HANDOFF.md`
14. `docs/LATEST_BROAD_OTP_EXTRACTION_HANDOFF.md`
15. `docs/LATEST_OTP_EMAIL_EXTRACTION_HANDOFF.md`
16. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
17. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
18. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
19. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`

## Corrected historical truth

Previous Android-to-Windows success reports, including the Yahoo! JAPAN SMS row, were contaminated by Microsoft Phone Link clipboard synchronization. They are invalid as ClipCascade proof.

Windows/peer-to-Android background reception does not prove Android outbound.

Disable Phone Link and every competing synchronizer during every validation run.

## Latest green build before current candidate

`.15 / 320119`:

- implementation anchor: `1e3aae70e2052420bfbcf2e326e04638787dfc1c`
- Android CI: `29674843116` — success
- Windows CI: `29674843145` — success
- Android artifact ID: `8438520128`
- ZIP SHA-256: `e3f50c87ebea56fe0039e3e08a909d282dc10631bb2dc808d6a01e86a1792e2a`
- APK SHA-256: `15ee61ad66e68f114b3a52c160773976ac705954a3a278b6b892559bae6b8ee2`
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- expiry: `2026-10-17T05:26:07Z`

`.15` removed the ten-minute ordinary clipboard TTL and added bounded retry backoff. It is superseded for target-device validation once `.16` is green because `.15` still evicted the oldest item on queue overflow.

## Current `.16 / 320120` candidate

Static follow-up audit found the second pre-ACK deletion path:

- queue capacity was 16;
- enqueueing item 17 removed item 1 even if item 1 had no ACK;
- this violated `ACK前にキューを削除しない`.

Candidate repair:

- accepted pending items are never evicted to admit newer items;
- queue remains bounded at 16;
- item 17 is rejected with `EnqueueResult.QUEUE_FULL`;
- latest clipboard diagnostic records `queue_full` without storing content;
- after ACK creates capacity, later Copy can be accepted normally;
- `.15` no-TTL retention and `3s -> 6s -> 12s -> 15s` idle retry backoff remain;
- ACK protocol, relay claims, internal-write guard, language-neutral Copy confirmation, React recovery, and OTP paths remain unchanged.

Candidate identity:

- versionName: `3.2.1-extended.16-standalone`
- versionCode: `320120`
- package: `com.clipcascade.extended`
- signer unchanged

Do not call `.16` green or distribute its artifact until implementation SHA, Android CI, Windows CI, artifact ID, ZIP/APK hashes, signer, and expiry are recorded.

## Final Android transform order

The final APK behavior is not determined by raw `ClipboardAccessibilityService.kt` alone.

The workflow runs `prepare_relay_claim.js` after every transport/listener transform. It then applies:

1. `prepare_internal_clipboard_guard.js`
2. `prepare_language_neutral_clipboard_copy.js`
3. `prepare_ack_safe_queue_overflow.js`

Do not reorder these casually. The overflow transform must run after the earlier reliability transform has finished editing the enqueue block.

CI must reject:

- final `CopyCueClassifier` references;
- final `looksLikeCopyConfirmation` references;
- ordinary clipboard `TTL_MS`;
- overflow eviction loop `while (pending.size ...)`;
- absence of final `queue_full` handling.

## Language-neutral Copy design

- `TYPE_VIEW_TEXT_SELECTION_CHANGED` remembers selection only;
- selection alone never queues or sends;
- OS `OnPrimaryClipChangedListener` is primary Copy proof;
- `ClipboardWriteGuard` suppresses ClipCascade-owned writes;
- ACTION_COPY and Ctrl+C use a 700 ms fallback only if clipboard serial does not advance;
- UI labels, content descriptions, toasts, and translated completion strings do not participate in correctness.

Never restore `.13` text dictionaries or add translations as a correctness mechanism.

## ACK path — preserve exactly

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

Extended P2P:

`LOCAL_TRANSPORT_ACCEPTED -> PEER_RECEIVED -> PEER_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

Old/non-Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

Preserve relay ID, `ackRequested`, validation-before-ACK, generation-scoped fallback timers, native relay claim, Windows-applied ACK, and ACK-based native deletion.

Do not treat local transport acceptance as Extended peer application.

## Background recovery retained

- persistent native clipboard and OTP queues;
- selected-text fallback when background clipboard reads are unavailable;
- internal-write echo suppression;
- in-process React context bootstrap;
- service-start cooldown recovery;
- relay claim against duplicate JS listeners;
- content-free capture/transport/peer/ACK diagnostics.

## OTP state

Broad OTP extraction and deterministic synthetic test remain implemented. The synthetic test exercises extractor, queue, transport, and ACK, but does not prove third-party NotificationListener extras exposure.

For real notifications record only:

- `local_extractor / queued`
- `notification_extras / empty`
- `notification_extras / no_match`

Do not store raw notification text, code, email address, app name, or package name.

## Mandatory next validation after `.16` becomes green

Install over the prior stable-signed build without uninstalling. Confirm settings and permissions survive.

1. Selection-only negative test in Japanese UI for 3, 15, and 60 seconds.
2. Repeat in English UI.
3. Repeat in a third UI language.
4. Real Copy in each language immediately and after 3, 15, and 60 seconds.
5. Repeat visible, backgrounded, removed from recents, locked, screen off 1 minute, 15 minutes, and 30+ minutes.
6. Disconnect peer, Copy once, retain more than ten minutes, reconnect, and verify one application plus ACK deletion.
7. Repeat for more than thirty minutes with screen off.
8. Fill queue with 16 unique values while peer is disconnected.
9. Copy item 17 and verify existing 16 remain plus diagnostic `queue_full`.
10. Reconnect and verify accepted 16 drain in order, exactly once, only after ACK.
11. Copy again after capacity returns and verify acceptance.
12. Verify Windows-to-Android inbound write does not echo.
13. Run synthetic OTP and one privacy-safe real notification classification.
14. Record comparable battery use and reconnect latency.

Use `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`.

## Diagnostics

- queue `0` and no capture diagnostic: capture missed;
- `no_text_available`: Copy confirmation existed but payload unavailable;
- `selected_text_fallback`: Accessibility selection supplied payload;
- `queue_full`: existing 16 accepted items were preserved and new input was rejected;
- `transport_status / retrying`: transport unavailable;
- `p2p_peer / retrying`: peer unavailable;
- `react_context / rebind_requested`: React generation absent;
- `react_event / emitted` then `peer_ack / acknowledged`: Windows-applied ACK completed;
- `dispatcher / interrupted`: dispatcher exception.

## Engineering procedure

For every new change:

1. verify current PR head and Draft state;
2. preserve Extended ACK;
3. make the smallest coherent change;
4. record hypothesis, attempts, failures, and limitations in progress plus focused handoff;
5. update CURRENT_STATUS, NEXT_CHATGPT_HANDOFF, and relevant matrix;
6. run Android and Windows CI on every branch commit;
7. verify both CIs on final branch HEAD;
8. record artifact source SHA, run ID, artifact ID, ZIP/APK hashes, signer, and expiry;
9. keep PR #1 Draft.

## Do not claim

CI is not HONOR device proof. Until isolated tests pass, do not claim selection-only suppression, multilingual correctness, background/screen-off delivery, long-disconnect retention, queue-full behavior, exactly-once, real third-party OTP extraction, battery efficiency, or Windows tray ghost prevention.
