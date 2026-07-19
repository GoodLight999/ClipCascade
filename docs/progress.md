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

Current phase: finish `.16 / 320120` CI and artifact verification, then validate ACK-safe bounded ordinary-copy delivery and language-neutral Copy confirmation on the HONOR target while preserving Extended P2P peer-applied ACK and PR #1 Draft state.

## 2026-07-19 — ACK-safe bounded queue overflow follow-up

After `.15` removed ten-minute time-based deletion, a second deterministic ACK violation was found:

- the 16-item ordinary queue admitted a new item and silently removed the oldest item on overflow;
- that oldest relay could still be awaiting Windows-applied ACK;
- bounded storage and ACK-safe deletion must both hold.

Prepared `.16 / 320120` candidate:

- accepted pending items are never evicted to admit a newer Copy;
- a full 16-item queue rejects new input with `EnqueueResult.QUEUE_FULL`;
- final transformed Accessibility diagnostics record content-free `queue_full`;
- added pure `ClipboardRelayQueuePolicyTest`;
- CI rejects overflow eviction code and verifies the final transformed queue-full branch;
- `.15` no-TTL retention and bounded idle retry remain intact;
- ACK protocol, relay claims, internal-write guard, language-neutral confirmation, OTP, and Windows code are unchanged.

The queue-full transform is intentionally last after internal-write guard and language-neutral copy transforms. Candidate implementation/CI/artifact details remain pending and must be recorded after both workflows complete.

## 2026-07-19 — ACK-safe durable clipboard queue and bounded retry

`.15 / 320119` removed the ten-minute ordinary clipboard TTL and added `3s -> 6s -> 12s -> 15s` idle retry backoff. It is green at source `1e3aae70e2052420bfbcf2e326e04638787dfc1c`, Android CI `29674843116`, Windows CI `29674843145`, artifact ID `8438520128`, ZIP SHA-256 `e3f50c87ebea56fe0039e3e08a909d282dc10631bb2dc808d6a01e86a1792e2a`, and APK SHA-256 `15ee61ad66e68f114b3a52c160773976ac705954a3a278b6b892559bae6b8ee2`.

`.15` is superseded for target-device validation by `.16` once `.16` becomes green, because `.15` still retained overflow eviction.

## 2026-07-19 — Documentation consistency correction

`docs/TEST_MATRIX.md` was rewritten to remove stale `.4 / 320107` identity and the Phone Link-contaminated Yahoo! JAPAN SMS success. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md` remains authoritative for Priority 1 device evidence.

## 2026-07-19 — Language-neutral copy confirmation redesign

`.14+` uses OS clipboard mutation, internal-write guard, and semantic ACTION_COPY/Ctrl+C fallback. Selection alone never queues. UI text and translations do not participate in correctness. Target-device multilingual and selection-only proof remains pending.

## 2026-07-18 — Background and OTP recovery history

`.12` added persistent queues, clipboard-change fallback, internal-write suppression, React bootstrap recovery, diagnostics, and Perceptron extraction coverage. The deterministic synthetic OTP test exercises extractor, queue, transport, and ACK, but real third-party notification extras exposure remains unproven.
