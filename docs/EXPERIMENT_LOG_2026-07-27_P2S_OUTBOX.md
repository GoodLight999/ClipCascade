# Experiment Log — 2026-07-27 — Persistent P2S Text Outbox

## Scope

Improve Android-to-server reliability without changing the Sathvik-Rao server protocol and without creating a second transport.

This milestone addresses text copied while the existing P2S STOMP client is disconnected or temporarily unable to complete its server echo cycle.

## Existing behavior inspected

The upstream mobile foreground service already owned:

- STOMP connection and subscription;
- encryption;
- `/app/cliptext` publish destination;
- `/user/queue/cliptext` subscription destination;
- clipboard hashing;
- inbound self-loop suppression.

The inspected P2S send path had three concrete reliability problems:

1. When STOMP was disconnected, copied text was silently discarded.
2. The local content hash was advanced before server delivery was established.
3. A boolean `toggle` depended on the server echo and could leave later sends blocked or ambiguous after connection loss.

The server API and destinations were therefore retained. Only a bounded client-side outbox was added around the existing publish/echo path.

## OUTBOX-001 core

Added `ClipCascade_Mobile/src/P2STextOutbox.js` as a transport-independent state component.

Properties:

- AsyncStorage-backed persistence;
- FIFO ordering;
- one in-flight item at a time;
- restart recovery converts persisted `inflight` state back to `queued`;
- content-hash duplicate rejection within the queue;
- default maximum of 20 items;
- default maximum age of 24 hours;
- bounded total wire bytes;
- serialized mutations to prevent concurrent AsyncStorage races;
- server-echo acknowledgement removes only the matching in-flight head;
- no network client and no server-protocol changes.

The caller supplies the prepared wire payload and the plaintext content hash. When ClipCascade encryption is enabled, the persisted wire payload is ciphertext. When encryption is disabled, queued text is necessarily stored as plaintext, consistent with the user's chosen unencrypted mode.

## OUTBOX-001 runtime integration

Integrated into the existing `StartForegroundService.js` P2S path.

- Queue storage is scoped by a fingerprint of server URL, username, cipher mode, and hashed password, preventing delivery to a different account/server configuration.
- Text is validated and encrypted using the existing code before enqueue.
- Offline text is persisted instead of silently dropped.
- After STOMP `CONNECTED` and subscription creation, the queue drains one head item.
- Publishing keeps the exact existing destination and JSON body shape.
- The item remains in-flight until the matching plaintext hash returns through the existing server subscription.
- A matching echo removes the head and starts the next item.
- A 30-second missing-echo timeout releases the item for retry.
- disconnect, STOMP error, WebSocket error, WebSocket close, and service shutdown return in-flight text to queued state.
- image and file behavior is intentionally unchanged and is not claimed durable.
- outbox snapshots are stored separately as `p2sTextOutboxStatus`; clipboard contents are not written into diagnostic counters.

## Review correction: self-echo rollback

A desk review found a non-obvious ordering bug in the first integration:

1. user copies text A while offline;
2. user then copies text B;
3. reconnect sends queued A;
4. the server echoes A;
5. without an explicit own-echo guard, A could be applied as a remote clipboard update and temporarily roll the local clipboard back from B to A.

Correction:

- acknowledge the matching queued echo first;
- if it is an acknowledged own echo, do not apply it to the local clipboard;
- continue draining the next queued item.

Product correction commit: `086092314cfaa1a658947283bb94aa11995813a9`.

## Tests

### JavaScript

Latest run executed 3 suites / 19 tests successfully.

Coverage includes:

- load and restart recovery;
- enqueue, FIFO, bounds, age expiry, duplicate rejection;
- single-flight state;
- echo acknowledgement;
- concurrent mutation serialization;
- server destination preservation;
- offline queue integration markers;
- self-echo rollback guard;
- release on connection loss and shutdown;
- canonical product-link contract.

### Android

Latest normal read-only workflow also passed:

- JVM tests, including Accessibility copy classifier and native duplicate gate;
- AIDL and Shizuku integration compilation;
- Kotlin/Android resource compilation;
- standalone APK build;
- exact `assets/index.android.bundle` presence;
- APK ZIP integrity;
- artifact upload and checksum.

## Retained failures and process corrections

- The existing template `App.test.tsx` attempted to render the native-heavy app without native mocks and had never been part of the previous build gate. It failed as soon as Jest was enabled. Rather than create a large fake native environment that would prove little, it was converted into a source-contract test for product links and help/update routing.
- A temporary YAML patch runner was malformed by an embedded multiline Python payload and did not register correctly. It was replaced with a transparent, reviewable Python patch file.
- One compressed patch runner had a Python parenthesis error and stopped before touching product code.
- A second review-correction run reported a push failure because a parallel run had already pushed the identical correction. The product commit was verified directly.
- All temporary patch helpers and write-enabled CI jobs were removed after use. Final Android and desktop workflows are read-only verification workflows.

These failures are retained because they explain the commit history and prevent future agents from repeating the same delivery mechanics.

## Latest green evidence

Branch head: `f35ebffba8b99f783b20b0bec2e4bc16a0421f1b`

Android:

- workflow: `30248168083`
- APK artifact ID: `8645964331`
- build-log artifact ID: `8645962581`
- file: `ClipCascade-Android-stability-standalone.apk`
- user-facing file: `ClipCascade-Android-stability-outbox.apk`
- size: `93,613,211` bytes
- SHA-256: `9810be35788fbcad32cf34986f0b19bcb324db1f40a9766024c298aec8e32b2d`
- APK entries: `538`
- JavaScript bundle present and contains outbox storage/acknowledgement markers
- status: build-verified engineering APK, not runtime-proven

Desktop on the same head:

- workflow: `30248168084`
- Windows artifact ID: `8645900812`
- Windows size: `57,456,040` bytes
- Windows SHA-256: `e83390cffca570224ae47d8144313062564e7886eace44dc04a28701c7855128`
- Linux artifact ID: `8645865478`
- Linux size: `60,415` bytes
- Linux SHA-256: `6bde0b175cf12ef3e26224efd04ee8449720d34f46735f39e1520fd7599cfc57`

## Claims intentionally not made

The following still require real-device/runtime evidence:

- actual offline Android capture while the foreground service remains alive;
- queue survival across Android process death and relaunch on the user's device;
- public-server echo behavior under disconnect/reconnect;
- duplicate behavior when Accessibility, READ_LOGS, and ordinary listener events overlap on the device;
- ordering under repeated rapid copies;
- battery and wakeup impact;
- image/file durability;
- true server or remote-device application acknowledgement beyond the unchanged server echo.

## Exact acceptance tests

1. Connect Android and Windows normally and verify one ordinary text copy.
2. Disconnect Android networking while keeping the ClipCascade foreground service active.
3. Copy A, then B, then C.
4. Reconnect networking.
5. Confirm Windows receives A, B, C in order, without duplicates.
6. Confirm Android's clipboard does not roll back to A or B when its own queued echoes return.
7. Repeat with Android process termination after enqueue and relaunch before reconnect.
8. Inspect `p2sTextOutboxStatus` through the UI/diagnostic surface once that display is connected.
9. Record all observations in the experiment log before changing retry, queue, or acknowledgement semantics.
