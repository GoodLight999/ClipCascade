# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade`

## Latest green Android target

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

## Candidate under CI

Candidate `.17 / 320121` treats real Gmail notification ingestion as non-functional until target-device proof exists.

It adds:

- actual notification-listener binding state separate from Android authorization;
- content-free stage counters;
- settings and WorkManager rebind requests;
- active-notification scan on listener connection;
- manual reconnect/rescan;
- deeper bounded extras collection;
- Gmail-shaped extractor tests;
- a default-OFF outbound transport-accepted debug notification switch.

See `docs/LATEST_GMAIL_NOTIFICATION_RELIABILITY_HANDOFF.md`.

## Gmail / real notification status

The deterministic synthetic OTP test proves extractor, queue, transport, and ACK components. It does not prove that Gmail, Beeper, Perceptron, or SMS notifications reach `NotificationListenerService` with usable extras.

The `.17` runtime display distinguishes:

- listener not connected;
- notification unseen;
- notification seen but text extras empty;
- text present but no authentication hint;
- authentication hint present but extractor no-match;
- queued/deduplicated;
- transport accepted when optional outbound debug notification is enabled.

No real Gmail success is claimed.

## ACK-safe ordinary clipboard retention

Current behavior retained:

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

## Language-neutral copy confirmation retained

- selection events only remember the selected range;
- selection itself never queues or sends;
- OS `OnPrimaryClipChangedListener` is primary proof of a real clipboard mutation;
- ClipCascade-owned writes are filtered by `ClipboardWriteGuard`;
- ACTION_COPY and Ctrl+C schedule a 700 ms fallback only if clipboard serial did not advance;
- translated labels, content descriptions, toast text, and completion wording are not used for Copy correctness.

## Delivery acknowledgement — preserve

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

Extended P2P:

`LOCAL_TRANSPORT_ACCEPTED -> PEER_RECEIVED -> PEER_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

Old/non-Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

The outbound debug notification is observational only. Its failure is caught and cannot alter transport acceptance, relay claims, ACK ordering, or queue deletion.

## Mandatory proof

Disable Phone Link and every competing synchronizer. Install `.17` in place, confirm notification authorization and actual listener runtime both show connected, clear counters, then send a Gmail OTP whose expanded notification visibly includes the code. Use the content-free stage counters to locate the failure before changing extraction rules.

Also validate ordinary copy, long disconnect, queue-full, exactly-once, ACK deletion, no echo, battery behavior, and Windows tray behavior through the existing matrices.

## Do not claim

Do not claim `.17` green until both CIs and artifact identity are recorded. CI is not HONOR target-device proof. Do not claim real Gmail success until isolated device evidence exists.

Canonical handoff: `docs/NEXT_CHATGPT_HANDOFF.md`. Keep PR #1 Draft.
