# Next ChatGPT Handoff

This is the canonical handoff. Read the listed documents before changing code.

## Repository and safety state

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- PR: `#1`
- PR state: open and Draft
- never mark Ready, merge, or weaken the Extended P2P ACK path
- current implementation anchor: `f86705c513c56a9fd24e218f8513dad9cead2ed8`

Commits after the implementation anchor may be documentation-only. Verify current PR head and both CIs before distributing any artifact.

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

## Current green Android build

- app: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.16-standalone`
- versionCode: `320120`
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- implementation anchor: `f86705c513c56a9fd24e218f8513dad9cead2ed8`

CI:

- Android standalone CI: `29675438972` — success
- Desktop Windows CI: `29675438978` — success

Artifact:

- artifact ID: `8438725636`
- ZIP SHA-256: `dda947ceb29452edc4defc94ee3c09852a87529b2db581a0f1d304b0182564f6`
- extracted APK SHA-256: `1bb1301e0a44a06f42cb04cbe55de03e9abc0baa6c224738d89f4686409a5def`
- expiry: `2026-10-17T05:49:26Z`

See `docs/LATEST_GREEN_ARTIFACTS.md`.

## Priority 1 defects fixed in `.15` and `.16`

Two deterministic pre-ACK deletion paths were found:

1. ordinary clipboard items expired after ten minutes without ACK;
2. item 17 silently evicted item 1 from the 16-item queue without ACK.

Current `.16` behavior:

- no wall-clock expiry for accepted ordinary clipboard items;
- bounded capacity remains 16;
- accepted items are never evicted to admit newer items;
- when full, the new Copy returns `EnqueueResult.QUEUE_FULL` and is not inserted;
- content-free diagnostics record `queue_full`;
- settings test shows localized English/Japanese queue-full feedback;
- disconnected/no-peer/no-React retry backs off `3s -> 6s -> 12s -> 15s`;
- new work/reconnect/recovery/ACK resets retry and runs immediately;
- in-flight ACK waiting remains at three seconds with existing 15-second timeout.

Do not restore time-based deletion or overflow eviction.

## Trial-and-error record

Initial `.16` commit `dd92a6e7c566f2f830201f55b4176eb8011c7412` passed all final transform assertions but Android CI `29675268087` failed at Kotlin compilation because the settings-screen test still treated the new enqueue enum as Boolean.

Corrected commit `f86705c...` transforms the settings test to explicit `QUEUED / DEDUPLICATED / QUEUE_FULL` handling and adds localized queue-full strings. Corrected Android and Windows CIs are green.

This was an implementation omission, not CI infrastructure noise. Keep it in the record.

## Final Android transform order

The final APK behavior is not determined by raw `ClipboardAccessibilityService.kt` alone.

`prepare_relay_claim.js` runs last and applies:

1. `prepare_internal_clipboard_guard.js`
2. `prepare_language_neutral_clipboard_copy.js`
3. `prepare_ack_safe_queue_overflow.js`

Do not reorder these casually. The overflow transform must run after the earlier reliability transform edits the enqueue block.

CI rejects:

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
- UI labels, content descriptions, toasts, and translated completion strings do not participate in Copy correctness.

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

## Mandatory next target-device validation

Install `.16 / 320120` over the prior stable-signed build without uninstalling. Confirm settings and permissions survive.

1. Selection-only negative test in Japanese UI for 3, 15, and 60 seconds.
2. Repeat in English UI.
3. Repeat in a third UI language.
4. Real Copy in each language immediately and after 3, 15, and 60 seconds.
5. Repeat visible, backgrounded, removed from recents, locked, screen off 1 minute, 15 minutes, and 30+ minutes.
6. Disconnect peer, Copy once, retain more than ten minutes, reconnect, and verify one application plus ACK deletion.
7. Repeat for more than thirty minutes with screen off.
8. Fill queue with 16 unique values while peer is disconnected.
9. Copy item 17 and verify existing 16 remain plus diagnostic `queue_full`.
10. Verify settings test shows localized queue-full feedback.
11. Reconnect and verify accepted 16 drain in order, exactly once, only after ACK.
12. Copy again after capacity returns and verify acceptance.
13. Verify Windows-to-Android inbound write does not echo.
14. Run synthetic OTP and one privacy-safe real notification classification.
15. Record comparable battery use and reconnect latency.

Use `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`.

## Diagnostics

- queue `0` and no capture diagnostic: capture missed;
- `no_text_available`: confirmation but no payload;
- `selected_text_fallback`: Accessibility selection used;
- `queue_full`: existing accepted items preserved and new input rejected;
- `transport_status / retrying`: transport unavailable;
- `p2p_peer / retrying`: peer unavailable;
- `react_context / rebind_requested`: React absent;
- `react_event / emitted` then `peer_ack / acknowledged`: full Windows-applied ACK path;
- `dispatcher / interrupted`: internal exception.

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
