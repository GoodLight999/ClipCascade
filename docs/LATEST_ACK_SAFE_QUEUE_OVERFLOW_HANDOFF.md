# ACK-Safe Bounded Queue Overflow Handoff — 2026-07-19

## Defect found

After `.15` removed the ordinary clipboard queue's ten-minute TTL, a second pre-ACK deletion path remained:

```kotlin
pending += item
while (pending.size > MAX_ITEMS) pending.removeAt(0)
```

When the queue exceeded 16 items, this silently deleted the oldest unacknowledged relay. That violated the explicit requirement that an accepted ordinary clipboard item not be removed before the defined acknowledgement.

A bounded queue must not represent silent loss as success. The correct bounded behavior is to preserve every accepted pending item and reject additional input explicitly when capacity is exhausted.

## Implemented `.16 / 320120` repair

Implementation anchor:

- source SHA: `f86705c513c56a9fd24e218f8513dad9cead2ed8`;
- versionName: `3.2.1-extended.16-standalone`;
- versionCode: `320120`;
- package: `com.clipcascade.extended`;
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`.

Behavior:

- existing accepted queue items are never evicted to admit a newer Copy;
- capacity remains bounded at 16 items;
- when 16 items are pending, a new Copy returns `EnqueueResult.QUEUE_FULL`;
- no item is written and no existing relay is removed;
- content-free diagnostics record `queue_full`;
- settings test shows localized English/Japanese queue-full feedback;
- deduplication remains separately reported as `deduplicated`;
- ACK removal, explicit clear, and relay-disable clear remain unchanged;
- `.15` no-TTL retention and `3s -> 6s -> 12s -> 15s` idle retry backoff remain unchanged.

## Final transform order

`prepare_relay_claim.js` runs last and applies:

1. `prepare_internal_clipboard_guard.js`
2. `prepare_language_neutral_clipboard_copy.js`
3. `prepare_ack_safe_queue_overflow.js`

This ordering is deliberate because the earlier reliability transform edits the original enqueue block.

## First CI attempt and repair

Initial candidate commit:

- SHA: `dd92a6e7c566f2f830201f55b4176eb8011c7412`;
- Android CI: `29675268087` — failure during Kotlin compilation;
- every source transform and final invariant check passed before compilation.

Root cause:

- `ClipboardRelayStore.enqueue()` changed from Boolean to `EnqueueResult`;
- the Accessibility path was transformed correctly;
- `RelaySettingsActivity.enqueueClipboardTest()` still used the return value as a Boolean.

Corrected commit:

- SHA: `f86705c513c56a9fd24e218f8513dad9cead2ed8`;
- settings test now handles `QUEUED / DEDUPLICATED / QUEUE_FULL` explicitly;
- exact result is recorded in content-free diagnostics;
- English and Japanese queue-full Toast and diagnostic labels were added.

This was a real implementation omission, not an infrastructure failure, and remains recorded to prevent repetition.

## CI and artifact evidence

- Android standalone CI: `29675438972` — success;
- Desktop Windows CI: `29675438978` — success;
- Android artifact ID: `8438725636`;
- artifact ZIP SHA-256: `dda947ceb29452edc4defc94ee3c09852a87529b2db581a0f1d304b0182564f6`;
- extracted APK SHA-256: `1bb1301e0a44a06f42cb04cbe55de03e9abc0baa6c224738d89f4686409a5def`;
- artifact expiry: `2026-10-17T05:49:26Z`;
- signer diagnostics confirmed the expected V2 signer SHA-256.

Android CI verified final transformed queue-full handling, no TTL, no overflow eviction, pure capacity policy tests, language-neutral Copy invariants, retry policy, OTP tests, Kotlin/resources, APK assembly, embedded bundle, and signer.

Windows CI revalidated the retained peer-applied ACK and packaging path.

## Required target-device proof

1. Keep the peer disconnected.
2. Fill the ordinary queue to 16 unique synthetic Copy items.
3. Perform a 17th Copy.
4. Confirm the existing 16 items remain unchanged and the latest diagnostic is `queue_full`.
5. Confirm the settings test displays localized queue-full feedback when capacity is exhausted.
6. Reconnect the peer.
7. Confirm the accepted 16 items drain in order and each is deleted only after its defined ACK.
8. After capacity becomes available, perform another Copy and confirm it is accepted and delivered exactly once.

No target-device success is claimed. PR #1 remains Draft.
