# Next ChatGPT Handoff

This is the canonical single-document handoff for the next conversation. Read the listed source documents before changing code, but this file contains enough state to prevent accidental rollback or false success claims.

## 1. Repository, branch, and PR

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- PR: `#1`
- PR status: open and Draft
- mandatory: keep PR #1 Draft; do not merge or mark ready
- implementation anchor: `20ef493a3b322ec2d95f76cee8902426b7623559`

Commits after the implementation anchor may be documentation-only handoff consolidation. Verify the current PR head and CI before distributing a new artifact.

## 2. Read in this exact order

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. `docs/NEXT_CHATGPT_HANDOFF.md`
5. `docs/LATEST_LANGUAGE_NEUTRAL_COPY_HANDOFF.md`
6. `docs/LATEST_GREEN_ARTIFACTS.md`
7. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
8. `docs/LATEST_SELECTION_ONLY_COPY_FALSE_POSITIVE_HANDOFF.md`
9. `docs/LATEST_BACKGROUND_CLIPBOARD_INTERMITTENT_HANDOFF.md`
10. `docs/LATEST_OTP_SELF_TEST_HANDOFF.md`
11. `docs/LATEST_BROAD_OTP_EXTRACTION_HANDOFF.md`
12. `docs/LATEST_OTP_EMAIL_EXTRACTION_HANDOFF.md`
13. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
14. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
15. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
16. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`

## 3. User requirements that must not be weakened

Priority 1 is Android outbound recovery without ADB, root, or Shizuku.

The target behavior is:

- ordinary text copied on Android reaches Windows while ClipCascade is visible, backgrounded, removed from recents, locked, and screen-off;
- merely selecting text must never send it;
- UI language must not affect correctness;
- ClipCascade's own inbound/local clipboard writes must not echo back;
- persistent queue items are deleted only after the defined acknowledgement path;
- real OTP extraction must not be claimed without real third-party notification proof;
- Microsoft Phone Link and every competing clipboard synchronizer must be disabled during validation.

## 4. Corrected historical truth

Earlier apparent Android-to-Windows successes were contaminated by Microsoft Phone Link. Isolated ClipCascade Android outbound was not proven and failed particularly when the app UI was not visible.

Windows/peer-to-Android reception has worked backgrounded and apparently screen-off, but this does not prove Android outbound.

Never restore old success claims without isolated real-device evidence.

## 5. Current Android build

- app: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.14-standalone`
- versionCode: `320118`
- deterministic signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

Latest implementation-anchor CI:

- Android standalone CI: `29669730768` — success
- Desktop Windows CI: `29669730745` — success

Latest Android artifact:

- artifact ID: `8436928714`
- artifact ZIP SHA-256: `a569ff44a9b998754fc6190c742993508801b030c3237ed9b67b556d8e66154a`
- extracted APK SHA-256: `29c8e4a88b556aa9d94a07643b894d15d746740d5207e543671d8875196d63ba`
- expiry: `2026-10-17T02:08:43Z`

See `docs/LATEST_GREEN_ARTIFACTS.md` for recovery details.

## 6. Latest architecture correction: language-neutral copy confirmation

The `.13` approach was rejected by the user because it used English/Japanese strings such as `Copy`, `Copied`, `コピー`, and `コピーしました`. That design would fail on other UI languages. Do not restore it and do not solve it by adding more translations.

The `.14` design is semantic and language-neutral:

1. `TYPE_VIEW_TEXT_SELECTION_CHANGED` only remembers the selected range.
2. Selection itself never queues or sends.
3. `ClipboardManager.OnPrimaryClipChangedListener` is the primary proof that the OS clipboard actually changed.
4. `ClipboardWriteGuard` rejects ClipCascade-owned writes.
5. `AccessibilityNodeInfo.ACTION_COPY` and Ctrl+C schedule a bounded 700 ms fallback only when the clipboard-change serial did not advance.
6. Floating-toolbar labels, button text, content descriptions, toast wording, completion messages, and locale strings do not participate in correctness.

Key files:

- `ClipCascade_Mobile/src/android/app/src/main/java/com/clipcascade/ClipboardCopySignalPolicy.kt`
- `ClipCascade_Mobile/src/android/app/src/test/java/com/clipcascade/ClipboardCopySignalPolicyTest.kt`
- `ClipCascade_Mobile/src/scripts/prepare_language_neutral_clipboard_copy.js`
- `ClipCascade_Mobile/src/scripts/prepare_relay_claim.js`
- `ClipCascade_Mobile/src/android/app/src/main/java/com/clipcascade/ClipboardAccessibilityService.kt`
- `ClipCascade_Mobile/src/android/app/src/main/java/com/clipcascade/ClipboardWriteGuard.kt`

Important transform detail:

- CI invokes `prepare_relay_claim.js` last in the Android transform chain.
- `prepare_relay_claim.js` invokes `prepare_internal_clipboard_guard.js` and then `prepare_language_neutral_clipboard_copy.js`.
- The final transformed `ClipboardAccessibilityService.kt`, not merely the raw source file, determines the APK behavior.
- Android CI must continue rejecting any final transformed reference to `CopyCueClassifier` or `looksLikeCopyConfirmation`.

The old localized `CopyCueClassifier.kt` and its tests were deleted.

## 7. Background-delivery recovery retained from `.12`

The intermittent symptom was: ordinary sharing often worked only while the UI was open, but sometimes worked briefly in the background. The exact first bad build is unknown.

Retained repair mechanisms:

- persistent native clipboard queue;
- selected-text recovery when Android hides clipboard contents from background processes;
- `ClipboardWriteGuard` echo suppression;
- in-process React context bootstrap when the native queue outlives the React/Notifee generation;
- bootstrap attempts during the Android service-start cooldown;
- content-free diagnostics for capture, transport, peer, React event, and ACK stages;
- relay claim protection against duplicate JS listeners.

Do not attribute the regression solely to `.11`; `.11` did not modify ordinary-copy source files. The strongest concrete earlier regression candidate was the `.8` idle-power transform, but real-device evidence has not isolated one cause.

## 8. Acknowledgement path that must be preserved

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

Extended P2P path:

`LOCAL_TRANSPORT_ACCEPTED -> PEER_RECEIVED -> PEER_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

Old/non-Extended peer compatibility path:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

Preserve:

- `relayId`;
- `ackRequested`;
- validation-before-ACK;
- generation-scoped timers;
- native relay claims;
- acknowledgement-based deletion;
- the already implemented Windows-applied ACK.

Do not replace Extended P2P ACK with local transport acceptance or P2S semantics.

## 9. OTP state

Broad OTP extraction remains implemented, including standalone alphanumeric codes such as the Perceptron Network example `8F92FE`.

The built-in synthetic OTP test now deterministically exercises:

`extractor -> persistent OTP queue -> dispatcher -> transport -> ACK`

It still posts a real local notification, but it does not depend on Android delivering the app's own notification back through `NotificationListenerService`. Therefore it is not a pure third-party notification-listener test.

Real Gmail, Beeper, Perceptron, or SMS extraction remains unproven. If the synthetic test succeeds but a real notification fails, first inspect the privacy-safe classification:

- `local_extractor / queued`;
- `notification_extras / empty`;
- `notification_extras / no_match`.

Do not immediately loosen the regex. Confirm selected source app, notification access, expanded notification surfaces, and whether the code is exposed in notification extras. Never store raw notification text, email addresses, app/package names, or authentication codes in diagnostics.

## 10. Diagnostics interpretation

For ordinary copy:

- queue `0` and no recent copy diagnostic: capture was missed;
- `no_text_available`: a semantic cue existed but no readable clipboard or selected text was available;
- `selected_text_fallback`: Accessibility selection supplied the payload;
- trigger `clipboard_change`: the OS clipboard callback confirmed the mutation;
- queue `>0` plus `react_context / rebind_requested`: React generation was absent;
- queue `>0` plus `transport_status / retrying`: transport unavailable;
- queue `>0` plus `p2p_peer / retrying`: no open P2P peer channel;
- `react_event / emitted` then `peer_ack / acknowledged`: peer-applied ACK path completed;
- `dispatcher / interrupted`: unexpected dispatcher exception.

Do not store clipboard text or source-app identity in diagnostics.

## 11. Mandatory next target-device validation

Use the `.14 / 320118` APK installed over the existing build without uninstalling.

Before testing:

- disable Microsoft Phone Link clipboard synchronization;
- stop every other clipboard synchronizer;
- do not use ADB, root, or Shizuku;
- confirm Accessibility, notification access, battery exclusions, and synchronization settings survived the in-place update;
- record Android build, Windows peer build, mode, UI language, screen state, and unique test value.

Run in this order:

1. Japanese UI: select unique text and leave the toolbar open for 3, 15, and 60 seconds without pressing Copy. Queue and Windows clipboard must remain unchanged.
2. English UI: repeat the same negative test.
3. At least one third UI language: repeat the same negative test.
4. In each language, press the localized Copy command immediately, then after 3, 15, and 60 seconds. Each action must create exactly one delivery.
5. Repeat actual Copy with ClipCascade visible, backgrounded 30 seconds, removed from recents, device locked, screen off 1 minute, screen off 15 minutes, and screen off 30+ minutes.
6. Confirm exactly one Windows clipboard application per unique Copy.
7. Confirm native queue deletion occurs only after peer ACK.
8. Confirm Windows-to-Android inbound writes do not create a new Android outbound item.
9. Disconnect the peer, copy once, reconnect, and confirm the persistent queue drains once.
10. Rerun the synthetic OTP test.
11. Retry one real Perceptron/Gmail-style notification and record only `queued`, `empty`, or `no_match` classification.

Use `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md` as the record. Do not mark any row passed without isolated target-device evidence.

## 12. Required engineering procedure for every new change

1. Read the documents in the order above.
2. Check the current PR head and confirm PR #1 is still Draft.
3. Preserve the Extended P2P ACK path.
4. Make the smallest coherent change.
5. Record the hypothesis, attempted repair, failures, and limitations in `docs/progress.md` and a focused latest handoff document.
6. Update `docs/CURRENT_STATUS.md`, `docs/NEXT_CHATGPT_HANDOFF.md`, and the relevant test matrix.
7. Let both Android standalone CI and Desktop Windows CI run on every branch commit.
8. Before distributing an artifact, verify both CIs on the final branch HEAD, not only an earlier code commit.
9. Record artifact ID, ZIP hash, extracted binary hash, signer, source SHA, and expiry.
10. Keep PR #1 Draft.

If a GitHub write is rejected because a blob SHA is stale, refetch the file and retry. Do not omit the documentation update.

## 13. Do not claim

Until isolated target-device evidence exists, do not claim:

- selection-only suppression is proven;
- multilingual behavior is proven;
- Android background, removed-from-recents, locked, or screen-off outbound is reliable;
- exactly-once delivery is proven on the target device;
- real third-party OTP extraction works;
- battery efficiency is proven;
- Windows tray ghost prevention is proven.

CI proves build consistency and unit-level invariants, not HONOR target-device behavior.