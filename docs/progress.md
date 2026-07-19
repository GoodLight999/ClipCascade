# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/TEST_MATRIX.md`
5. `docs/LATEST_ACK_SAFE_QUEUE_OVERFLOW_HANDOFF.md`
6. `docs/LATEST_ACK_SAFE_CLIPBOARD_QUEUE_HANDOFF.md`
7. `docs/LATEST_LANGUAGE_NEUTRAL_COPY_HANDOFF.md`
8. `docs/LATEST_GREEN_ARTIFACTS.md`
9. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
10. `docs/LATEST_SELECTION_ONLY_COPY_FALSE_POSITIVE_HANDOFF.md`
11. `docs/LATEST_BACKGROUND_CLIPBOARD_INTERMITTENT_HANDOFF.md`
12. `docs/LATEST_OTP_SELF_TEST_HANDOFF.md`
13. `docs/LATEST_BROAD_OTP_EXTRACTION_HANDOFF.md`
14. `docs/LATEST_OTP_EMAIL_EXTRACTION_HANDOFF.md`
15. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
16. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
17. `docs/LATEST_ANDROID_INIT_DUPLICATE_HANDOFF.md`
18. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
19. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`

Current phase: validate `.16 / 320120` ACK-safe bounded ordinary-copy delivery and language-neutral Copy confirmation on the HONOR target while preserving Extended P2P peer-applied ACK and PR #1 Draft state.

## 2026-07-19 — `.16` green: no TTL and no overflow eviction

A fresh audit found two deterministic pre-ACK deletion paths in the ordinary clipboard queue:

1. wall-clock expiry after ten minutes;
2. silent oldest-item eviction when the 16-item queue overflowed.

The combined `.16 / 320120` result:

- no wall-clock expiry;
- no eviction of accepted items to admit newer items;
- bounded capacity remains 16;
- item 17 is rejected with content-free `queue_full` while items 1–16 remain;
- idle disconnected retry backs off `3s -> 6s -> 12s -> 15s`;
- new work/recovery/ACK resets retry and runs immediately;
- in-flight ACK timeout behavior remains unchanged;
- language-neutral Copy confirmation, internal-write guard, relay claims, React recovery, OTP, and Windows peer-applied ACK remain intact.

Green implementation evidence:

- source: `f86705c513c56a9fd24e218f8513dad9cead2ed8`;
- Android CI: `29675438972`, success;
- Windows CI: `29675438978`, success;
- Android artifact ID: `8438725636`;
- ZIP SHA-256: `dda947ceb29452edc4defc94ee3c09852a87529b2db581a0f1d304b0182564f6`;
- APK SHA-256: `1bb1301e0a44a06f42cb04cbe55de03e9abc0baa6c224738d89f4686409a5def`;
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`;
- expiry: `2026-10-17T05:49:26Z`.

No target-device success is claimed.

## 2026-07-19 — `.16` first CI failure and targeted repair

Initial commit `dd92a6e7c566f2f830201f55b4176eb8011c7412` passed transforms/invariants but Android CI `29675268087` failed because `RelaySettingsActivity` still treated enum enqueue result as Boolean. Corrected commit `f86705c...` transformed the settings test to three-way result handling and added localized queue-full feedback. This was a real implementation omission and is retained in the record.

## 2026-07-19 — `.15` intermediate repair

`.15 / 320119` removed the ten-minute TTL and added bounded retry, green at source `1e3aae70...`, but was superseded because overflow eviction remained.

## 2026-07-19 — Documentation and language-neutral history

`docs/TEST_MATRIX.md` was rewritten to remove stale `.4 / 320107` identity and Phone Link-contaminated success. `.14+` uses OS clipboard mutation, internal-write guard, and semantic ACTION_COPY/Ctrl+C fallback; selection alone never queues and UI translations do not control correctness.

## 2026-07-18 — Background and OTP recovery history

`.12` added persistent queues, clipboard-change fallback, internal-write suppression, React bootstrap recovery, diagnostics, and Perceptron extraction coverage. The deterministic synthetic OTP test exercises extractor, queue, transport, and ACK, but real third-party notification extras exposure remains unproven.
