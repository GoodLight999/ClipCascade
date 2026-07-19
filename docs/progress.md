# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/TEST_MATRIX.md`
5. `docs/LATEST_ACK_SAFE_CLIPBOARD_QUEUE_HANDOFF.md`
6. `docs/LATEST_LANGUAGE_NEUTRAL_COPY_HANDOFF.md`
7. `docs/LATEST_GREEN_ARTIFACTS.md`
8. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
9. `docs/LATEST_SELECTION_ONLY_COPY_FALSE_POSITIVE_HANDOFF.md`
10. `docs/LATEST_BACKGROUND_CLIPBOARD_INTERMITTENT_HANDOFF.md`
11. `docs/LATEST_OTP_SELF_TEST_HANDOFF.md`
12. `docs/LATEST_BROAD_OTP_EXTRACTION_HANDOFF.md`
13. `docs/LATEST_OTP_EMAIL_EXTRACTION_HANDOFF.md`
14. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
15. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
16. `docs/LATEST_ANDROID_INIT_DUPLICATE_HANDOFF.md`
17. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
18. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`

Current phase: validate `.15 / 320119` ACK-safe durable ordinary-copy delivery and language-neutral Copy confirmation while preserving Extended P2P peer-applied ACK, OTP extraction, and PR #1 Draft state.

## 2026-07-19 — ACK-safe durable clipboard queue and bounded retry

A fresh static audit found a deterministic Priority 1 defect before additional target-device guessing:

- `ClipboardRelayStore` silently removed ordinary clipboard items after ten minutes;
- the removal did not require peer ACK;
- this contradicted the 15-minute / 30+ minute screen-off matrix, peer-disconnect recovery, and the explicit rule that Extended P2P queue deletion follows Windows-applied ACK.

Implemented `.15 / 320119`:

- removed wall-clock expiry from the ordinary clipboard queue;
- retained the existing 16-item storage bound, deduplication, explicit clear, and relay-disable clear;
- added idle retry backoff `3s -> 6s -> 12s -> 15s` to avoid replacing TTL with a permanent three-second wakeup loop;
- explicit schedule/reconnect/recovery requests reset the delay and run immediately;
- ACK waiting remains on the three-second cadence with the existing 15-second timeout;
- added pure retry-policy unit tests and CI assertions that ordinary clipboard TTL cannot return;
- preserved relay IDs, relay claims, validation-before-ACK, old-peer fallback, and Extended Windows-applied ACK.

Implementation evidence:

- source: `1e3aae70e2052420bfbcf2e326e04638787dfc1c`;
- Android CI: `29674843116`, success;
- Windows CI: `29674843145`, success;
- Android artifact ID: `8438520128`;
- artifact ZIP SHA-256: `e3f50c87ebea56fe0039e3e08a909d282dc10631bb2dc808d6a01e86a1792e2a`;
- APK SHA-256: `15ee61ad66e68f114b3a52c160773976ac705954a3a278b6b892559bae6b8ee2`;
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`;
- artifact expiry: `2026-10-17T05:26:07Z`.

No implementation CI failure occurred. No target-device success is claimed. Required proof is a disconnected Copy retained beyond ten and thirty minutes, followed by exactly one Windows application and ACK-based deletion after reconnection.

## 2026-07-19 — Documentation consistency finding

`docs/TEST_MATRIX.md` still contains historical `.4 / 320107` identity and a Yahoo! JAPAN SMS success that was later invalidated by Phone Link contamination. The canonical live matrix is `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`, which explicitly supersedes all older Android outbound success rows. Do not treat the older checked rows as current evidence.

## 2026-07-19 — Final handoff consolidation audit

The prior conversation audited the handoff documents and established `.14 / 320118` as the first language-neutral Copy candidate. Its implementation anchor was `20ef493a3b322ec2d95f76cee8902426b7623559`, with Android CI `29669730768`, Windows CI `29669730745`, and artifact ID `8436928714`. `.14` remains useful as the comparison anchor before the separate `.15` queue-lifetime repair.

No real-device success was claimed.

## 2026-07-19 — Language-neutral copy confirmation redesign

The user correctly rejected `.13`: using English/Japanese `Copy`, `Copied`, `コピー`, and `コピーしました` strings in the correctness path would fail on other UI languages. Adding translations was rejected as the wrong architecture.

Implemented `.14 / 320118` and retained in `.15`:

- selection events only remember text and never queue/send;
- `OnPrimaryClipChangedListener` is the primary proof of a real OS clipboard mutation;
- ClipCascade-owned writes are rejected by `ClipboardWriteGuard`;
- `ACTION_COPY` and Ctrl+C use a 700 ms fallback only when no clipboard callback was observed;
- floating-toolbar labels, toast text, and translated completion strings are not consulted;
- added `ClipboardCopySignalPolicy` and locale-free unit tests;
- deleted `CopyCueClassifier` and its localized tests;
- CI fails if the transformed service still references `CopyCueClassifier` or `looksLikeCopyConfirmation`.

Target-device multilingual and selection-only proof remains pending.

## 2026-07-18 — Selection-only false-positive repair

`.13 / 320117` distinguished direct copy interaction from passive toolbar appearance, but it was superseded because the distinction still depended on English/Japanese UI strings.

## 2026-07-18 — Intermittent background ordinary-copy recovery

`.12 / 320116` restored source-node inspection, added selected-text plus clipboard-change fallback, internal-write suppression, React bootstrap recovery, diagnostics, and Perceptron OTP coverage. Real-device proof remains pending.

## 2026-07-18 — OTP self-test dispatch repair

The synthetic test passes its generated notification text through the extractor, persistent OTP queue, dispatcher, transport, and acknowledgement path directly. Real third-party notification access remains a separate device test.
