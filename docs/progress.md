# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/TEST_MATRIX.md`
5. `docs/LATEST_GMAIL_NOTIFICATION_RELIABILITY_HANDOFF.md`
6. `docs/LATEST_GREEN_ARTIFACTS.md`
7. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
8. `docs/LATEST_ACK_SAFE_QUEUE_OVERFLOW_HANDOFF.md`
9. `docs/LATEST_ACK_SAFE_CLIPBOARD_QUEUE_HANDOFF.md`
10. `docs/LATEST_LANGUAGE_NEUTRAL_COPY_HANDOFF.md`
11. `docs/LATEST_SELECTION_ONLY_COPY_FALSE_POSITIVE_HANDOFF.md`
12. `docs/LATEST_BACKGROUND_CLIPBOARD_INTERMITTENT_HANDOFF.md`
13. `docs/LATEST_OTP_SELF_TEST_HANDOFF.md`
14. `docs/LATEST_BROAD_OTP_EXTRACTION_HANDOFF.md`
15. `docs/LATEST_OTP_EMAIL_EXTRACTION_HANDOFF.md`
16. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
17. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
18. `docs/LATEST_ANDROID_INIT_DUPLICATE_HANDOFF.md`
19. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
20. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`

Current phase: isolated target-device validation of `.17 / 320121` real Gmail notification ingestion and the optional outbound transport-accepted debug notification, while preserving Extended P2P peer-applied ACK and PR #1 Draft state.

## 2026-07-19 — `.17` Gmail listener recovery and diagnostics green

User evidence: no Gmail notification has ever produced a relayed code. Pre-`.17` Gmail behavior is therefore treated as non-functional, not merely untested. Synthetic OTP tests remain component tests only.

Static defects found:

- Android notification-access authorization was mistaken for proof of a live listener binding;
- many notification paths returned silently;
- reconnect did not reconsider already-active Gmail notifications;
- OEM/process loss could remove the listener without a useful recovery callback;
- no content-free counters distinguished listener failure, empty Gmail extras, missing authentication context, extractor no-match, or successful queueing.

Implemented `.17 / 320121`:

- actual listener connection state;
- settings, disconnect-callback, and WorkManager rebind requests;
- active-notification scan on connection, max 64 and max age 15 minutes;
- manual reconnect/rescan action;
- deeper bounded extras collection through nested bundles/lists/arrays/SparseArray and MessagingStyle/public-version fields;
- content-free stage counters and last part/character counts;
- Gmail-shaped positive and no-code negative extractor tests;
- default-OFF outbound debug notification after transport acceptance, containing only source class and P2P/P2S mode;
- debug notification errors isolated from ACK/queue behavior.

Implementation anchor and green evidence:

- source: `a010d7f0fb3871252580666df3264980b32c93cb`;
- Android CI: `29681462233`, success;
- Windows CI: `29681462236`, success;
- Android artifact ID: `8440717410`;
- ZIP SHA-256: `5e545d9210a97819cfae79bde5a278e69631b395f55aa6cad0f260e4cd38134e`;
- APK SHA-256: `f3bba473b78d1f44f73fe529cd6c0187881269615aeb709651a6f8cd675ffb86`;
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`;
- expiry: `2026-10-17T09:22:32Z`.

No real Gmail target-device success is claimed.

## 2026-07-19 — `.17` trial and error

1. Android CI `29681080924` stopped safely in the final transform; no APK was produced.
2. CI was changed to preserve `gmail-transform.log` as an artifact. Android CI `29681226875` then identified an exact Japanese resource-anchor mismatch. `prepare_gmail_ja_anchor_compat.js` normalizes the existing wording before applying the final Gmail transform.
3. Android CI `29681300642` passed transforms and JavaScript bundling but failed Kotlin compilation because the debug notifier referenced nonexistent `ic_small_icon`. `prepare_debug_notification_icon_compat.js` now uses existing `ic_notification_failure`.
4. Corrected Android and Windows runs passed completely.

These were real implementation/integration defects and are retained rather than disguised as CI noise.

## 2026-07-19 — `.16` ACK-safe bounded ordinary clipboard queue

Implementation anchor: `f86705c513c56a9fd24e218f8513dad9cead2ed8`.

- removed ten-minute wall-clock deletion before ACK;
- removed silent oldest-item overflow eviction;
- bounded capacity remains 16, with item 17 rejected as `queue_full`;
- disconnected retry backs off `3s -> 6s -> 12s -> 15s`;
- language-neutral Copy, echo suppression, relay claims, React recovery, and Windows-applied ACK remain intact.

The initial `.16` attempt failed because the settings test still treated enum enqueue result as Boolean; the corrected implementation added exact three-way handling and localized queue-full feedback.

## Earlier history

`.14+` uses OS clipboard mutation, internal-write guard, and semantic ACTION_COPY/Ctrl+C fallback; selection alone never queues and UI translations do not control correctness. `.12` introduced persistent queues, React bootstrap recovery, diagnostics, and broad OTP component coverage. Phone Link-contaminated successes are invalid as ClipCascade proof.
