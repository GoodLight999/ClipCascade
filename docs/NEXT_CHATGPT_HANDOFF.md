# Next ChatGPT Handoff

Read these first, in order:

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. `docs/LATEST_LANGUAGE_NEUTRAL_COPY_HANDOFF.md`
5. `docs/LATEST_SELECTION_ONLY_COPY_FALSE_POSITIVE_HANDOFF.md`
6. `docs/LATEST_BACKGROUND_CLIPBOARD_INTERMITTENT_HANDOFF.md`
7. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`

## Current branch and PR

- Repository: `GoodLight999/ClipCascade`
- Branch: `stability-mobile-otp`
- PR: `#1`
- Keep PR Draft.

## Latest correction

The `.13` repair was rejected because it depended on English/Japanese Copy command and completion strings. That was not an internationalizable correctness path.

Current target:

- `3.2.1-extended.14-standalone`
- versionCode `320118`

Ordinary copy confirmation is now language-neutral:

- selection only remembers text and never queues/sends;
- `ClipboardManager.OnPrimaryClipChangedListener` confirms the real OS mutation;
- ClipCascade-owned writes are ignored;
- `ACTION_COPY` and Ctrl+C provide a 700 ms fallback only if no clipboard callback arrives;
- translated toolbar labels, toast text, and completion wording are ignored;
- `CopyCueClassifier` and its localized tests were deleted;
- CI rejects any transformed service that still references the old classifier.

Preserve Extended P2P peer-applied ACK, persistent clipboard/OTP queues, relay claims, internal-write suppression, React bootstrap recovery, broad OTP extraction, and the Draft PR state.

## Mandatory target-device proof

With Phone Link and all competing synchronizers disabled:

1. Select text without pressing Copy: no queue and no delivery.
2. Press Copy on Japanese, English, and at least one other phone UI language: exactly one delivery.
3. Repeat after delays up to 60 seconds.
4. Repeat visible, backgrounded, removed from recents, locked, and screen-off.
5. Confirm inbound writes do not echo.
6. Do not claim multilingual or selection-only success until target-device evidence exists.
