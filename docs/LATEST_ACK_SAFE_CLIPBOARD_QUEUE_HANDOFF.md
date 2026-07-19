# ACK-Safe Durable Clipboard Queue Handoff — 2026-07-19

## Static defect found

`ClipboardRelayStore` expired ordinary clipboard items after ten minutes by filtering them out of the persistent queue. That deletion did not require a transport acknowledgement or a peer-applied acknowledgement.

This directly conflicted with Priority 1:

- screen-off validation includes 15 minutes and 30+ minutes;
- peer-disconnect validation requires an item to survive until reconnection;
- Extended P2P items must not be deleted before Windows applies the text and returns the peer ACK.

The queue was already bounded to 16 items, so the ten-minute TTL was not needed to bound storage.

## Candidate repair

Android candidate identity:

- versionName: `3.2.1-extended.15-standalone`;
- versionCode: `320119`;
- package: `com.clipcascade.extended`;
- signer: unchanged deterministic public test signer.

Changes:

1. Ordinary clipboard items no longer expire by wall-clock age.
2. They remain until one of the explicit lifecycle events occurs:
   - defined acknowledgement removes the relay ID;
   - user clears pending data;
   - clipboard relay is disabled and the privacy requirement clears pending values;
   - queue pressure exceeds the existing 16-item bound and the oldest item is evicted.
3. Disconnected/no-peer/no-React polling now backs off from 3 seconds to 6, 12, and at most 15 seconds.
4. A new item, reconnect, recovery request, or acknowledgement resets the dispatcher and schedules an immediate attempt.
5. While an item is in flight, the dispatcher retains the 3-second cadence so the existing 15-second ACK timeout remains responsive.

## Preserved ACK path

No protocol or acknowledgement semantics were weakened.

Extended P2P remains:

`LOCAL_TRANSPORT_ACCEPTED -> PEER_RECEIVED -> PEER_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

Preserved:

- `relayId`;
- `ackRequested`;
- validation-before-ACK;
- generation-scoped old-peer fallback;
- native relay claims;
- Windows-applied acknowledgement;
- internal clipboard-write suppression;
- language-neutral Copy confirmation.

## CI guards and tests

Android CI now rejects an ordinary clipboard store containing `TTL_MS`, verifies the `.15 / 320119` identity, and runs `ClipboardRelayRetryPolicyTest`.

The retry-policy tests verify:

- idle retry grows `3s -> 6s -> 12s -> 15s` and remains capped;
- ACK waiting returns to the 3-second cadence.

## Required real-device proof

This static repair does not prove target-device behavior. With Phone Link and every competing synchronizer disabled:

1. disconnect the Windows peer;
2. perform exactly one Android Copy;
3. leave the peer disconnected for more than ten minutes, preferably more than thirty minutes with the screen off;
4. confirm the native queue still contains one item;
5. reconnect the peer;
6. confirm exactly one Windows clipboard application;
7. confirm the queue is deleted only after peer ACK.

Also record whether delayed retry/reconnect latency remains acceptable. The 15-second idle cap is deliberately bounded to reduce wakeups without making recovery feel stalled.

## Status

At the time this handoff was written, the implementation commit and both CIs had not yet been recorded. Do not distribute the `.15` artifact or claim this repair green until Android standalone CI and Desktop Windows CI succeed on the implementation commit.

PR #1 must remain Draft.
