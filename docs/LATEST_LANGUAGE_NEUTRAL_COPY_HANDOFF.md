# Language-Neutral Android Copy Confirmation Handoff — 2026-07-19

## User correction

The `.13` selection-only false-positive repair was architecturally insufficient. It distinguished a displayed Copy command from a completion notice using English and Japanese strings such as `Copy`, `Copied`, `コピー`, and `コピーしました`.

The user correctly pointed out that this made ordinary copy detection dependent on the phone UI language. Adding more translated words would not solve the design defect.

## Correct design

Ordinary copy confirmation must be semantic and language-neutral:

1. `TYPE_VIEW_TEXT_SELECTION_CHANGED` only remembers the selected range. It never queues or sends anything.
2. `ClipboardManager.OnPrimaryClipChangedListener` is the primary proof that clipboard state actually changed.
3. ClipCascade-owned writes are rejected by `ClipboardWriteGuard` before capture.
4. `AccessibilityNodeInfo.ACTION_COPY` and Ctrl+C start a bounded 700 ms fallback only when no clipboard-change callback was observed.
5. Floating-toolbar labels, toast text, content descriptions, resource translations, and completion wording are not part of the ordinary-copy decision.

## Implementation

- Added `ClipboardCopySignalPolicy.kt` with no locale strings.
- Added `ClipboardCopySignalPolicyTest.kt` covering selection-only rejection, real clipboard changes, internal-write rejection, stale selections, and semantic-action fallback cancellation.
- Added `prepare_language_neutral_clipboard_copy.js`.
- `prepare_relay_claim.js` applies the internal clipboard-write guard and then the language-neutral copy transform at the end of the Android transform sequence.
- The final transformed `ClipboardAccessibilityService` increments a clipboard-change serial, treats selection only as state, and schedules fallback only for `ACTION_COPY` and Ctrl+C.
- Removed `looksLikeCopyConfirmation`, every transformed `CopyCueClassifier` reference, the localized classifier source, and its tests.
- Android CI rejects final transformed code that restores the old classifier or text-based confirmation function.

## Build identity

- implementation anchor: `20ef493a3b322ec2d95f76cee8902426b7623559`
- versionName: `3.2.1-extended.14-standalone`
- versionCode: `320118`
- package: `com.clipcascade.extended`
- deterministic signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

## CI and artifact

- Android standalone CI `29669730768`: success
- Desktop Windows CI `29669730745`: success
- Android artifact ID: `8436928714`
- artifact ZIP SHA-256: `a569ff44a9b998754fc6190c742993508801b030c3237ed9b67b556d8e66154a`
- extracted APK SHA-256: `29c8e4a88b556aa9d94a07643b894d15d746740d5207e543671d8875196d63ba`

Android CI passed source transforms, locale-free policy tests, Kotlin/resource compilation, APK assembly, embedded JavaScript bundle verification, deterministic signer verification, and artifact upload. Windows CI passed at the same implementation anchor.

## Preserve

- Extended P2P Windows-applied acknowledgement;
- persistent clipboard and OTP queues;
- relay claim protection;
- internal-write echo suppression;
- React generation recovery;
- broad OTP extraction;
- PR #1 Draft state.

## Validation requirements

With Phone Link and every competing clipboard synchronizer disabled:

1. Select text on phones configured for Japanese, English, and at least one other language; do not press Copy. Nothing should be queued or delivered.
2. Press the localized Copy command. Exactly one item should be queued and exactly one Windows clipboard application should occur.
3. Repeat after waiting 3, 15, and 60 seconds after selection.
4. Repeat visible, backgrounded, removed from recents, locked, and screen-off.
5. Confirm ClipCascade inbound writes do not echo back.
6. Confirm queue deletion occurs only after peer ACK.

CI success is not target-device proof. Do not mark selection-only suppression, multilingual behavior, background delivery, or exactly-once behavior as passed until isolated device testing succeeds.