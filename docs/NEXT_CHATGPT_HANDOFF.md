# Next ChatGPT Handoff

This is the canonical handoff. Read the listed documents before changing code.

## Repository and safety state

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- PR: `#1`
- PR state: open and Draft
- never mark Ready, merge, or weaken the Extended P2P ACK path
- current implementation anchor: `a010d7f0fb3871252580666df3264980b32c93cb`

Commits after the implementation anchor may be documentation-only. Verify current PR head and both CIs before distributing any artifact.

## Read in this exact order

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. `docs/NEXT_CHATGPT_HANDOFF.md`
5. `docs/LATEST_GMAIL_NOTIFICATION_RELIABILITY_HANDOFF.md`
6. `docs/LATEST_GREEN_ARTIFACTS.md`
7. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
8. `docs/TEST_MATRIX.md`
9. `docs/LATEST_ACK_SAFE_QUEUE_OVERFLOW_HANDOFF.md`
10. `docs/LATEST_ACK_SAFE_CLIPBOARD_QUEUE_HANDOFF.md`
11. `docs/LATEST_LANGUAGE_NEUTRAL_COPY_HANDOFF.md`
12. `docs/LATEST_SELECTION_ONLY_COPY_FALSE_POSITIVE_HANDOFF.md`
13. `docs/LATEST_BACKGROUND_CLIPBOARD_INTERMITTENT_HANDOFF.md`
14. `docs/LATEST_OTP_SELF_TEST_HANDOFF.md`
15. `docs/LATEST_BROAD_OTP_EXTRACTION_HANDOFF.md`
16. `docs/LATEST_OTP_EMAIL_EXTRACTION_HANDOFF.md`
17. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
18. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
19. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
20. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`

## Corrected historical truth

Previous Android-to-Windows success reports, including the Yahoo! JAPAN SMS row, were contaminated by Microsoft Phone Link clipboard synchronization. They are invalid as ClipCascade proof.

Windows/peer-to-Android background reception does not prove Android outbound.

The user reports that Gmail notifications have never produced a relayed code. Treat pre-`.17` Gmail notification ingestion as non-functional. Synthetic OTP tests prove extractor/queue/transport/ACK components only, not Gmail NotificationListener ingestion.

Disable Phone Link and every competing synchronizer during every validation run.

## Current green Android build

- app: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.17-standalone`
- versionCode: `320121`
- implementation anchor: `a010d7f0fb3871252580666df3264980b32c93cb`
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

CI:

- Android standalone CI: `29681462233` — success
- Desktop Windows CI: `29681462236` — success

Artifact:

- artifact ID: `8440717410`
- ZIP SHA-256: `5e545d9210a97819cfae79bde5a278e69631b395f55aa6cad0f260e4cd38134e`
- extracted APK SHA-256: `f3bba473b78d1f44f73fe529cd6c0187881269615aeb709651a6f8cd675ffb86`
- expiry: `2026-10-17T09:22:32Z`

See `docs/LATEST_GREEN_ARTIFACTS.md`.

## `.17` Gmail notification repair

Static defects fixed:

- notification-access authorization no longer masquerades as proof of a live listener binding;
- actual listener connection state is stored separately;
- silent notification gates now produce content-free stage counters;
- settings, WorkManager, and disconnect callback request listener rebind;
- listener connection scans up to 64 active notifications younger than 15 minutes;
- settings provides manual reconnect/rescan;
- notification extras collection now covers known fields, text arrays, MessagingStyle current/historic messages, public-version extras, nested bundles/lists/arrays, and SparseArray values to bounded depth;
- Gmail-shaped positive and no-code negative extractor tests are present.

Persisted diagnostics never contain notification text, code, account/email identifier, app/package name, relay ID, or server address.

## Gmail stage interpretation

After installing `.17`, clear diagnostics and send a Gmail OTP whose expanded notification visibly contains the code.

- listener not connected or seen remains zero: listener binding/delivery failure;
- seen increases, text characters remain zero: Gmail/Android exposed no text extras;
- text characters increase, auth hint remains zero: Gmail preview omitted authentication wording;
- auth hint and no-match increase: extractor defect;
- queued increases: Gmail notification ingestion succeeded;
- outbound debug notification appears: transport accepted the item;
- peer ACK removes queue: full Extended P2P path succeeded.

Do not loosen extractor regexes until the first failed stage is known.

## Outbound debug notification switch

- defaults OFF;
- when ON, appears only after P2S publish acceptance or at least one open P2P DataChannel accepts an outbound item;
- displays only source class and P2P/P2S mode;
- never displays content, code, relay ID, package name, account identifier, or server address;
- failure is caught and cannot alter transport acceptance, native relay claims, ACK ordering, retry behavior, or deletion;
- this notification proves local transport acceptance only, never Windows application or peer ACK.

## Final Android transform order

The APK behavior is not determined by raw Android source alone. `prepare_relay_claim.js` runs last and applies:

1. `prepare_internal_clipboard_guard.js`
2. `prepare_language_neutral_clipboard_copy.js`
3. `prepare_ack_safe_queue_overflow.js`
4. `prepare_gmail_ja_anchor_compat.js`
5. `prepare_gmail_notification_reliability.js`
6. `prepare_debug_notification_icon_compat.js`

Do not reorder these casually. CI preserves `gmail-transform.log` to diagnose final-transform failures.

## Trial-and-error record

- Android CI `29681080924`: final Gmail transform stopped safely; no APK.
- Android CI `29681226875`: preserved transform log identified exact Japanese resource-anchor mismatch; compatibility normalization added.
- Android CI `29681300642`: transforms/bundle passed, Kotlin found nonexistent debug icon; switched to existing native drawable.
- Android CI `29681462233`: success.
- Windows CI `29681462236`: success.

Keep these failures in the record; they were real integration defects, not infrastructure noise.

## Language-neutral Copy design — preserve

- selection events remember state only;
- selection itself never queues or sends;
- OS `OnPrimaryClipChangedListener` is primary Copy proof;
- `ClipboardWriteGuard` suppresses ClipCascade-owned writes;
- ACTION_COPY and Ctrl+C use a 700 ms fallback only if clipboard serial does not advance;
- UI labels, descriptions, toasts, and translated completion strings do not participate in correctness.

Never restore `.13` text dictionaries or add translations as a correctness mechanism.

## ACK-safe ordinary queue — preserve

- accepted ordinary items have no wall-clock expiry;
- capacity is 16;
- accepted items are never evicted to admit newer items;
- a new item while full returns `queue_full` and is not inserted;
- disconnected/no-peer/no-React retry backs off `3s -> 6s -> 12s -> 15s`;
- new work/recovery/ACK resets retry and runs immediately.

## ACK path — preserve exactly

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

Extended P2P:

`LOCAL_TRANSPORT_ACCEPTED -> PEER_RECEIVED -> PEER_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

Old/non-Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

Preserve relay ID, `ackRequested`, validation-before-ACK, generation-scoped fallback timer, native relay claim, Windows-applied ACK, and ACK-based native deletion.

## Mandatory next target-device validation

Install `.17 / 320121` over the existing stable-signed build without uninstalling.

Gmail first:

1. confirm Android notification access is authorized;
2. confirm actual listener runtime shows connected;
3. temporarily enable outbound debug notification;
4. clear diagnostics;
5. send a Gmail OTP with code visible in the expanded notification;
6. record only content-free stage/counter changes;
7. confirm queueing, transport debug notification, Windows application, peer ACK, and deletion as separate stages;
8. repeat backgrounded and removed from recents;
9. leave a Gmail OTP notification active and test manual reconnect/rescan;
10. disable outbound debug notification and confirm it stops appearing.

Then continue the ordinary Copy matrix: multilingual selection-only negatives, actual Copy at 0/3/15/60 seconds, visible/background/recents/locked/screen-off, long peer disconnect, queue-full, exactly-once, no echo, battery, and tray behavior.

Use `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`.

## Engineering procedure

For every new change:

1. verify current PR head and Draft state;
2. preserve Extended ACK;
3. make the smallest coherent change;
4. record hypothesis, failures, and limitations;
5. update focused handoff, CURRENT_STATUS, canonical handoff, and matrix;
6. run Android and Windows CI on each branch commit;
7. verify both CIs on final branch HEAD;
8. record source SHA, run IDs, artifact ID, hashes, signer, and expiry;
9. keep PR #1 Draft.

## Do not claim

CI is not HONOR device proof. Until isolated tests pass, do not claim real Gmail ingestion, listener recovery on HONOR, outbound debug ON/OFF behavior, multilingual Copy correctness, background/screen-off delivery, long-disconnect retention, queue-full behavior, exactly-once, real third-party OTP extraction, battery efficiency, or Windows tray ghost prevention.
