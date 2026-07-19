# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/TEST_MATRIX.md`
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
15. `docs/LATEST_ANDROID_INIT_DUPLICATE_HANDOFF.md`
16. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
17. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`

Current phase: prove language-neutral Android Copy confirmation and reliable background delivery while preserving Extended P2P peer-applied ACK, persistent queues, OTP extraction, and PR #1 Draft state.

## 2026-07-19 — Final handoff consolidation audit

The long development conversation was audited before handoff.

Findings:

- `NEXT_CHATGPT_HANDOFF.md`, `CURRENT_STATUS.md`, and the focused `.14` handoff already described the language-neutral architecture;
- `LATEST_GREEN_ARTIFACTS.md` was stale at `.5` and was replaced with the `.14` implementation anchor, CI runs, artifact ID, hashes, signer, expiry, and unproven scope;
- `TEST_MATRIX_BACKGROUND_OUTBOUND.md` had not recorded the green `.14` CI and artifact and was corrected;
- the canonical next-thread handoff was expanded to include history, architecture, transform ordering, ACK invariants, OTP limits, diagnostics, exact device-test order, and mandatory engineering procedure;
- all handoff-only commits continue to trigger Android and Windows CI; verify both on the final documentation HEAD before closing the conversation.

Implementation anchor and matching build:

- source: `20ef493a3b322ec2d95f76cee8902426b7623559`;
- Android CI: `29669730768`, success;
- Windows CI: `29669730745`, success;
- Android artifact ID: `8436928714`;
- artifact ZIP SHA-256: `a569ff44a9b998754fc6190c742993508801b030c3237ed9b67b556d8e66154a`;
- APK SHA-256: `29c8e4a88b556aa9d94a07643b894d15d746740d5207e543671d8875196d63ba`.

No new real-device success was claimed.

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