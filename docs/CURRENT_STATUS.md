# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade`

## Current Android target

- app: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.14-standalone`
- versionCode: `320118`
- deterministic signer unchanged

## Current focus

The current focus is language-neutral Android copy confirmation plus recovery of intermittent background delivery. The user correctly rejected `.13` because it relied on English/Japanese Copy command and completion strings.

## Language-neutral copy confirmation

Current design:

- selection events only remember the selected range;
- selection itself never queues or sends;
- the OS `OnPrimaryClipChangedListener` callback is the primary proof of a real clipboard mutation;
- ClipCascade-owned writes are filtered by `ClipboardWriteGuard`;
- `ACTION_COPY` and Ctrl+C schedule a 700 ms fallback only if the clipboard-change serial did not advance;
- translated button labels, content descriptions, toast text, and completion wording are not used;
- localized `CopyCueClassifier` source and tests were deleted;
- CI fails if the transformed Accessibility service still contains the old classifier or `looksLikeCopyConfirmation`.

## Background recovery retained

- persistent native clipboard and OTP queues;
- in-process React context bootstrap when the service generation disappears;
- recovery attempts during Android service-start cooldown;
- content-free delivery diagnostics;
- internal-write echo suppression;
- relay claim protection.

## Delivery acknowledgement — preserve

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

Extended P2P:

`LOCAL_TRANSPORT_ACCEPTED -> PEER_RECEIVED -> PEER_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

Old/non-Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

Do not weaken or bypass the Extended Windows-applied acknowledgement.

## OTP status

Broad extraction and deterministic synthetic OTP delivery remain present. Real Gmail/Beeper/Perceptron extraction is not proven until the target notification surfaces expose the code and device tests pass.

## Mandatory proof

Disable Phone Link and every competing clipboard synchronizer. Verify selection without Copy, actual Copy under multiple UI languages, delayed Copy up to 60 seconds, visible/background/recents/locked/screen-off states, exactly-once Windows application, ACK-based deletion, and no inbound echo.

## Do not claim

Do not claim multilingual correctness, selection-only suppression, reliable background or screen-off outbound, exactly-once delivery, or real third-party OTP extraction until isolated target-device evidence exists.
