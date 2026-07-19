# ACK-Safe Bounded Queue Overflow Handoff — 2026-07-19

## Follow-up defect found

After `.15` removed the ordinary clipboard queue's ten-minute TTL, a second pre-ACK deletion path remained:

```kotlin
pending += item
while (pending.size > MAX_ITEMS) pending.removeAt(0)
```

When the queue exceeded 16 items, this silently deleted the oldest unacknowledged relay. That still violated the explicit requirement that an ordinary clipboard item not be removed before the defined acknowledgement.

The requirement for a bounded queue does not justify pretending an unacknowledged item succeeded. The correct bounded behavior is to preserve every accepted pending item and reject additional input explicitly when capacity is exhausted.

## `.16 / 320120` candidate repair

- existing accepted queue items are never evicted to admit a newer Copy;
- capacity remains bounded at 16 items;
- when 16 items are pending, a new Copy returns `EnqueueResult.QUEUE_FULL`;
- no item is written and no existing relay is removed;
- the content-free clipboard diagnostic records `queue_full`;
- deduplication remains separately reported as `deduplicated`;
- ACK removal, explicit clear, and relay-disable clear remain unchanged;
- `.15` no-TTL retention and `3s -> 6s -> 12s -> 15s` idle retry backoff remain unchanged.

The final service patch is applied by `prepare_ack_safe_queue_overflow.js` after the existing final transform sequence:

1. `prepare_internal_clipboard_guard.js`
2. `prepare_language_neutral_clipboard_copy.js`
3. `prepare_ack_safe_queue_overflow.js`

This ordering is deliberate because `prepare_background_clipboard_reliability.js` edits the original enqueue block earlier in the chain.

## Candidate identity

- versionName: `3.2.1-extended.16-standalone`;
- versionCode: `320120`;
- package: `com.clipcascade.extended`;
- signer: unchanged deterministic public test signer.

## CI guards and tests

Android CI must verify:

- final transformed service contains `queue_full` handling;
- `ClipboardRelayStore` contains no `TTL_MS`;
- `ClipboardRelayStore` contains no overflow `while (pending.size ...)` eviction;
- `ClipboardRelayQueuePolicyTest` proves capacity is accepted only below 16;
- all language-neutral Copy, retry, OTP, ACK, bundle, build, and signer checks remain green.

## Required target-device proof

1. Keep the peer disconnected.
2. Fill the ordinary queue to 16 unique synthetic Copy items.
3. Perform a 17th Copy.
4. Confirm the existing 16 items remain unchanged and the latest diagnostic is `queue_full`.
5. Reconnect the peer.
6. Confirm the accepted 16 items drain in order and each is deleted only after its defined ACK.
7. After capacity becomes available, perform another Copy and confirm it is accepted and delivered exactly once.

No target-device success is claimed. At handoff-writing time, candidate CI and artifact metadata were not yet recorded.

PR #1 must remain Draft.
