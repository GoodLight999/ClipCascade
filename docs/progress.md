# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/TEST_MATRIX.md`
5. `docs/LATEST_LANGUAGE_NEUTRAL_COPY_HANDOFF.md`
6. `docs/LATEST_SELECTION_ONLY_COPY_FALSE_POSITIVE_HANDOFF.md`
7. `docs/LATEST_BACKGROUND_CLIPBOARD_INTERMITTENT_HANDOFF.md`
8. `docs/LATEST_OTP_SELF_TEST_HANDOFF.md`
9. `docs/LATEST_BROAD_OTP_EXTRACTION_HANDOFF.md`
10. `docs/LATEST_OTP_EMAIL_EXTRACTION_HANDOFF.md`
11. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
12. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
13. `docs/LATEST_ANDROID_INIT_DUPLICATE_HANDOFF.md`
14. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
15. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`

Current phase: prove language-neutral Android Copy confirmation and reliable background delivery while preserving Extended P2P peer-applied ACK, persistent queues, OTP extraction, and PR #1 Draft state.

## 2026-07-19 — Language-neutral copy confirmation redesign

The user correctly rejected `.13`: using English/Japanese `Copy`, `Copied`, `コピー`, and `コピーしました` strings in the correctness path would fail on other UI languages. Adding translations was rejected as the wrong architecture.

Implemented `.14 / 320118`:

- selection events only remember text and never queue/send;
- `OnPrimaryClipChangedListener` is the primary proof of a real OS clipboard mutation;
- ClipCascade-owned writes are rejected by `ClipboardWriteGuard`;
- `ACTION_COPY` and Ctrl+C use a 700 ms fallback only when no clipboard callback was observed;
- floating-toolbar labels, toast text, and translated completion strings are not consulted;
- added `ClipboardCopySignalPolicy` and locale-free unit tests;
- deleted `CopyCueClassifier` and its localized tests;
- CI fails if transformed service code still references `CopyCueClassifier` or `looksLikeCopyConfirmation`.

Target-device multilingual and selection-only proof remains pending.

## 2026-07-18 — Selection-only false-positive repair

The user reported that an older build could relay text after selection alone. Audit showed that `.12` still had a gap: opening the floating selection toolbar could emit a passive window event whose node contained the `Copy` / `コピー` command, and the generic marker check could treat that appearance as completion.

Implemented `.13 / 320117` repair:

- generic `Copy` / `コピー` labels were accepted only for direct click or context-click events;
- passive events required completion wording;
- `ACTION_COPY`, clipboard-change fallback, and internal-write suppression remained.

This was CI-green but superseded by `.14` because the text-based distinction was language-dependent.

## 2026-07-18 — Intermittent background ordinary-copy recovery

User report: ordinary sharing often worked only while the UI was open, sometimes worked briefly in the background, and the first bad build was unknown. `.12 / 320116` restored source-node inspection, added selected-text plus clipboard-change fallback, internal-write suppression, React bootstrap recovery, diagnostics, and Perceptron OTP coverage. Real-device proof remains pending.

## 2026-07-18 — OTP self-test dispatch repair

The synthetic test now passes its generated notification text through the extractor, persistent OTP queue, dispatcher, transport, and acknowledgement path directly. Real third-party notification access remains a separate device test.
