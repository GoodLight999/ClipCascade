# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade`

## Latest green Android target

- app: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.15-standalone`
- versionCode: `320119`
- implementation anchor: `1e3aae70e2052420bfbcf2e326e04638787dfc1c`
- Android CI: `29674843116`, success
- Windows CI: `29674843145`, success
- Android artifact ID: `8438520128`
- deterministic signer unchanged

`.15` removed time-based queue expiry, but it is superseded for new validation by the `.16` candidate because `.15` could still evict the oldest unacknowledged item when the 16-item queue overflowed.

## Candidate under CI

- versionName: `3.2.1-extended.16-standalone`
- versionCode: `320120`
- package and signer unchanged

Candidate `.16` behavior:

- existing accepted ordinary clipboard items are never evicted before ACK to admit a newer Copy;
- capacity remains bounded at 16;
- a new Copy while full is rejected with `EnqueueResult.QUEUE_FULL`;
- diagnostics record `queue_full` without content;
- `.15` no-TTL retention and bounded idle retry remain;
- ACK protocol, relay claims, language-neutral Copy confirmation, internal-write guard, React recovery, and OTP paths remain unchanged.

See `docs/LATEST_ACK_SAFE_QUEUE_OVERFLOW_HANDOFF.md`.

## Delivery acknowledgement — preserve

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

Extended P2P:

`LOCAL_TRANSPORT_ACCEPTED -> PEER_RECEIVED -> PEER_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

Old/non-Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

Do not weaken or bypass the Extended Windows-applied acknowledgement. Do not restore time-based expiry or overflow eviction of accepted unacknowledged items.

## Language-neutral copy confirmation

- selection only remembers state and never sends;
- OS `OnPrimaryClipChangedListener` is the primary proof of Copy;
- ClipCascade-owned writes are filtered by `ClipboardWriteGuard`;
- ACTION_COPY and Ctrl+C use a 700 ms serial-cancelled fallback;
- translated labels, descriptions, toast text, and completion wording are not used;
- final behavior is transform-produced and CI rejects old localized classifier remnants.

## Background recovery and OTP

Persistent clipboard/OTP queues, React bootstrap recovery, content-free diagnostics, relay claims, broad OTP extraction, and deterministic synthetic OTP delivery remain present. Real third-party notification extraction remains unproven.

## Mandatory proof

After `.16` is green, disable Phone Link and every competing synchronizer and run:

- selection-only negative tests in Japanese, English, and a third UI language;
- real Copy visible/background/recents/locked/screen-off;
- disconnected retention beyond ten and thirty minutes;
- 16-item capacity plus a 17th `queue_full` rejection;
- exactly-once Windows application, ACK-based deletion, and no inbound echo;
- synthetic OTP and one privacy-safe real notification classification;
- comparable battery/reconnect-latency observation.

Use `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`.

## Do not claim

Do not claim `.16` green until both CIs and artifact identity are recorded. Do not claim target-device behavior from CI.

Canonical handoff: `docs/NEXT_CHATGPT_HANDOFF.md`. Keep PR #1 Draft.
