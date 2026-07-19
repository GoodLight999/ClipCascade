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

## First CI attempt and repair

Initial candidate commit:

- SHA: `dd92a6e7c566f2f830201f55b4176eb8011c7412`;
- Android CI: `29675268087` — failure during Kotlin compilation;
- every source transform and final invariant check passed before compilation.

Root cause:

- `ClipboardRelayStore.enqueue()` changed from Boolean to `EnqueueResult`;
- the Accessibility path was transformed correctly;
- `RelaySettingsActivity.enqueueClipboardTest()` still used the return value as a Boolean in two places.

Repair:

- final transform now updates the settings test path to the same three-way `queued / deduplicated / queue_full` branch;
- manual test diagnostics always record the exact result;
- English and Japanese queue-full Toast text was added;
- English and Japanese diagnostic labels for `queue_full` were added;
- no ACK, transport, queue retention, or Windows code changed.

This failure is retained as part of the trial-and-error record. Do not hide or reinterpret it as an infrastructure failure.

## CI guards and tests

Android CI must verify:

- final transformed service contains `queue_full` handling;
- `ClipboardRelayStore` contains no `TTL_MS`;
- `ClipboardRelayStore` contains no overflow `while (pending.size ...)` eviction;
- `ClipboardRelayQueuePolicyTest` proves capacity is accepted only below 16;
- transform execution successfully patches both Accessibility and settings-test paths;
- all language-neutral Copy, retry, OTP, ACK, bundle, build, resource, and signer checks remain green.

## Required target-device proof

1. Keep the peer disconnected.
2. Fill the ordinary queue to 16 unique synthetic Copy items.
3. Perform a 17th Copy.
4. Confirm the existing 16 items remain unchanged and the latest diagnostic is `queue_full`.
5. Confirm the settings test displays the localized queue-full result when capacity is exhausted.
6. Reconnect the peer.
7. Confirm the accepted 16 items drain in order and each is deleted only after its defined ACK.
8. After capacity becomes available, perform another Copy and confirm it is accepted and delivered exactly once.

No target-device success is claimed. Corrected candidate CI and artifact metadata remain pending.

PR #1 must remain Draft.
