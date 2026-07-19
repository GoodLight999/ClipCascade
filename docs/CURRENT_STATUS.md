# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade`

## Current Android target

- app: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.15-standalone`
- versionCode: `320119`
- deterministic signer unchanged
- implementation anchor: `1e3aae70e2052420bfbcf2e326e04638787dfc1c`
- Android CI: `29674843116`, success
- Windows CI: `29674843145`, success
- Android artifact ID: `8438520128`

Artifact hashes and expiry are recorded in `docs/LATEST_GREEN_ARTIFACTS.md`.

## Current focus

The current focus is isolated target-device proof of ACK-safe durable Android outbound delivery plus language-neutral Copy confirmation. `.15` fixes a deterministic queue-lifetime defect while retaining the `.14` semantic Copy architecture.

## ACK-safe ordinary clipboard retention

The pre-`.15` store could remove an ordinary clipboard item after ten minutes without any acknowledgement. This contradicted the 15-minute / 30+ minute screen-off matrix, peer-disconnect recovery, and the Extended P2P ACK invariant.

Current behavior:

- no wall-clock expiry for ordinary clipboard queue items;
- existing 16-item bound remains;
- explicit relay-disable and user-clear paths still clear pending sensitive values;
- defined acknowledgement removes by `relayId`;
- disconnected/no-peer/no-React retry backs off `3s -> 6s -> 12s -> 15s`;
- new work/reconnect/recovery resets the delay and attempts immediately;
- in-flight ACK waiting remains at three seconds with the existing 15-second timeout.

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

The final Android behavior is transform-produced. Preserve the final ordering documented in `docs/NEXT_CHATGPT_HANDOFF.md`.

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

Do not weaken or bypass the Extended Windows-applied acknowledgement. Do not restore ordinary-copy time-based expiry.

## OTP status

Broad extraction and deterministic synthetic OTP delivery remain present. Real Gmail/Beeper/Perceptron extraction is not proven until the target notification surfaces expose the code and device tests pass.

## Documentation correction

`docs/TEST_MATRIX.md` contains older `.4` identity and Phone Link-contaminated success history. It is historical and not authoritative for Android outbound. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md` supersedes those rows.

## Mandatory proof

Disable Phone Link and every competing clipboard synchronizer. Verify selection without Copy, actual Copy under multiple UI languages, delayed Copy up to 60 seconds, visible/background/recents/locked/screen-off states, exactly-once Windows application, ACK-based deletion, no inbound echo, and disconnected queue retention beyond ten and thirty minutes.

Use `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`.

## Do not claim

Do not claim multilingual correctness, selection-only suppression, reliable background or screen-off outbound, long-disconnect retention, exactly-once delivery, or real third-party OTP extraction until isolated target-device evidence exists.

Canonical complete handoff: `docs/NEXT_CHATGPT_HANDOFF.md`. Keep PR #1 Draft.
