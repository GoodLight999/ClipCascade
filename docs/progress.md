# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/TEST_MATRIX.md`
5. `docs/LATEST_NOTIFICATION_LISTENER_ALPHA_HANDOFF.md`
6. `docs/LATEST_GMAIL_NOTIFICATION_RELIABILITY_HANDOFF.md`
7. `docs/LATEST_GREEN_ARTIFACTS.md`
8. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
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
19. `docs/LATEST_ANDROID_INIT_DUPLICATE_HANDOFF.md`
20. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
21. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`

Current phase: validate `3.2.1-extended.18-alpha.1 / 320122` on the HONOR target while preserving the synchronization recovery observed on `.17`, Extended P2P peer-applied ACK, and PR #1 Draft state.

## 2026-07-21 — synchronization recovered on `.17`

Target-device evidence from the user:

- ordinary synchronization is working again;
- it had been broken before `.17`;
- no real Gmail OTP arrived during the observation window, so Gmail extraction remains unverified for lack of a test notification.

Treat “ordinary synchronization recovered” as genuine target-device evidence, but do not broaden it into claims about Gmail, screen-off reliability, exactly-once, queue durability, or battery use.

## 2026-07-21 — `.18-alpha.1` notification-listener hardening

A static follow-up audit found two important gaps plus one trust-boundary issue:

1. the old deterministic OTP self-test posted a notification and then directly inserted the same value into the OTP queue, so it did not prove the actual Android NotificationListener callback;
2. `.17` rescans notifications up to 15 minutes old, while OTP content deduplication lasts only 90 seconds, allowing the same still-active notification to be reconsidered after a later reconnect;
3. the synthetic marker was trusted without also requiring ClipCascade's own package.

Implemented in one alpha commit:

- implementation SHA: `87e138380a139671168effd24a64df844f1bb879`;
- tag: `v3.2.1-extended.18-alpha.1`;
- versionName: `3.2.1-extended.18-alpha.1`;
- versionCode: `320122`;
- Android CI: `29827698937`, success;
- Windows CI: `29827698930`, success;
- Actions artifact ID: `8494023518`;
- Actions artifact ZIP SHA-256: `ae84ba8adda4b0c33ac8dbd39f835fdf7be9142dea8788e579361f6b0917cbeb`;
- APK SHA-256: `53da5cae4b5e2c7dd5cad0e6064aec47945b9d9620fbd30d9edaf391d37ac88d`;
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`;
- Actions artifact expiry: `2026-10-19T11:50:45Z`.

Alpha changes:

- a separate listener-path self-test that cannot succeed through direct queue insertion;
- the original deterministic component/transport test remains separate;
- synthetic privilege requires both ClipCascade's own package and the marker;
- a bounded one-way notification receipt fingerprint prevents one active notification from being re-sent after the 90-second queue-deduplication window;
- receipt TTL 30 minutes, capacity 128, and rollback on queue exception;
- pure receipt policy unit tests;
- content-free eligible/already-processed/listener-test/scan/callback-delay counters;
- release workflow waits for matching-sha push Android and Windows CI before creating the alpha prerelease.

The alpha tag was verified to resolve exactly to `87e13838...`. No Windows implementation or ACK semantics changed.

## Preserved invariants

- selection alone never sends;
- language-neutral OS clipboard mutation remains primary Copy proof;
- ClipCascade-owned clipboard writes remain suppressed;
- accepted ordinary clipboard items have no wall-clock expiry;
- accepted items are never evicted before ACK;
- queue capacity remains 16 with explicit `queue_full` rejection;
- Extended P2P deletion still requires Windows clipboard application followed by peer ACK;
- validation-before-ACK, relay IDs, native relay claims, and old-peer fallback remain intact;
- transport debug notification defaults OFF and cannot alter ACK or queue state.

## Mandatory next proof

1. Install alpha over `.17` without uninstalling.
2. Confirm settings and permissions survive.
3. Confirm ordinary synchronization still works once before testing OTP.
4. Confirm actual notification-listener runtime shows connected.
5. Run the deterministic component/transport test and verify ACK.
6. Run the separate listener-path self-test and verify listener-test, eligible, queued, Windows-applied, and ACK-deleted stages.
7. Re-scan the still-active test notification and verify `already_processed` increases without a second Windows application.
8. Run a fresh listener-path test and verify it is accepted.
9. Test outbound debug notification ON, then OFF.
10. When a real Gmail OTP arrives, clear diagnostics and record the first boundary reached without storing the code.

No real Gmail success is claimed.
