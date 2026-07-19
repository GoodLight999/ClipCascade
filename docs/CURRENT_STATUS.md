# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade`

## Current Android target

- app: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.16-standalone`
- versionCode: `320120`
- implementation anchor: `f86705c513c56a9fd24e218f8513dad9cead2ed8`
- Android CI: `29675438972`, success
- Windows CI: `29675438978`, success
- Android artifact ID: `8438725636`
- deterministic signer unchanged

Artifact hashes and expiry are recorded in `docs/LATEST_GREEN_ARTIFACTS.md`.

## Current focus

The current focus is isolated target-device proof of ACK-safe bounded Android outbound delivery plus language-neutral Copy confirmation.

## ACK-safe ordinary clipboard retention

Current behavior:

- accepted ordinary clipboard items do not expire by wall-clock age;
- capacity remains bounded at 16 items;
- accepted items are never evicted to admit newer items;
- while full, a new Copy is rejected with `EnqueueResult.QUEUE_FULL`;
- diagnostics record `queue_full` without content;
- explicit relay-disable and user-clear paths still clear pending sensitive values;
- defined acknowledgement removes by `relayId`;
- disconnected/no-peer/no-React retry backs off `3s -> 6s -> 12s -> 15s`;
- new work/reconnect/recovery resets the delay and attempts immediately;
- in-flight ACK waiting remains at three seconds with the existing 15-second timeout.

## Trial and error retained

Initial `.16` commit `dd92a6e7...` passed transform invariants but failed Android compilation because the settings test still treated the new enqueue enum as Boolean. Corrected commit `f86705c...` added exact three-way settings handling and localized queue-full feedback. The corrected Android and Windows CIs are green.

## Language-neutral copy confirmation

- selection events only remember the selected range;
- selection itself never queues or sends;
- OS `OnPrimaryClipChangedListener` is primary proof of a real clipboard mutation;
- ClipCascade-owned writes are filtered by `ClipboardWriteGuard`;
- ACTION_COPY and Ctrl+C schedule a 700 ms fallback only if clipboard serial did not advance;
- translated labels, content descriptions, toast text, and completion wording are not used for Copy correctness;
- final behavior is transform-produced and CI rejects old localized classifier remnants.

## Background recovery retained

- persistent native clipboard and OTP queues;
- in-process React context bootstrap;
- recovery during Android service-start cooldown;
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

Do not weaken or bypass the Extended Windows-applied acknowledgement. Do not restore time-based expiry or overflow eviction of accepted unacknowledged items.

## OTP status

Broad extraction and deterministic synthetic OTP delivery remain present. Real Gmail/Beeper/Perceptron extraction is not proven until target notification surfaces expose the code and device tests pass.

## Mandatory proof

Disable Phone Link and every competing synchronizer. Verify:

- selection without Copy in Japanese, English, and a third UI language;
- actual Copy after 0/3/15/60 seconds;
- visible/background/recents/locked/screen-off states;
- disconnected retention beyond ten and thirty minutes;
- 16 accepted items plus 17th `queue_full` rejection;
- accepted items drain in order, exactly once, only after ACK;
- new Copy is accepted after capacity returns;
- no inbound echo;
- synthetic OTP and privacy-safe real-notification classification;
- battery and reconnect latency.

Use `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`.

## Do not claim

CI is not HONOR target-device proof. Do not claim multilingual correctness, selection-only suppression, background/screen-off reliability, long-disconnect retention, queue-full preservation, exactly-once, real third-party OTP extraction, battery efficiency, or tray behavior until isolated evidence exists.

Canonical handoff: `docs/NEXT_CHATGPT_HANDOFF.md`. Keep PR #1 Draft.
