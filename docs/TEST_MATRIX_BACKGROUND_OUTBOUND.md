# Android Background Outbound Validation Matrix

This matrix supersedes every older checked Android outbound or real-service success item, including Phone Link-contaminated rows.

Before every run:

- disable Microsoft Phone Link clipboard synchronization;
- stop every other clipboard synchronization utility;
- do not use ADB, root, or Shizuku;
- record commit SHA, versionCode, Windows peer build, mode, UI language, and screen state;
- use unique synthetic values;
- never store real authentication values or raw notification text.

## Current green build

- [x] implementation SHA `a010d7f0fb3871252580666df3264980b32c93cb`
- [x] Android CI `29681462233`
- [x] Windows CI `29681462236`
- [x] artifact ID `8440717410`
- [x] ZIP SHA-256 `5e545d9210a97819cfae79bde5a278e69631b395f55aa6cad0f260e4cd38134e`
- [x] APK SHA-256 `f3bba473b78d1f44f73fe529cd6c0187881269615aeb709651a6f8cd675ffb86`
- [x] signer `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- [x] expiry `2026-10-17T09:22:32Z`
- [x] Android version `3.2.1-extended.17-standalone`
- [x] versionCode `320121`
- [x] Gmail-shaped positive and no-code negative unit tests
- [x] listener rebind/rescan and content-free diagnostic source assertions
- [x] outbound debug notification source/ACK-isolation assertions
- [x] no ordinary clipboard TTL or overflow eviction
- [x] final transformed `queue_full` handling
- [ ] in-place update succeeds
- [ ] settings and permissions retained

CI proves transformed source and build invariants, not HONOR target-device behavior.

## Gmail / notification-listener stage matrix

Before each row:

1. confirm Android notification access is authorized;
2. confirm actual listener runtime shows connected;
3. enable outbound debug notification only for the diagnostic run;
4. clear content-free diagnostics/counters;
5. use a fresh Gmail OTP whose expanded notification visibly contains the code.

| State | Runtime connected | Seen +1 | Text chars > 0 | Auth hint +1 | Queued +1 | Debug notice | Windows applied once | Peer ACK deletion | Status |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Settings/UI visible | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| App backgrounded 30s | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Removed from recents | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Device locked | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 1 minute | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Active Gmail notification + manual reconnect/rescan | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

Interpretation:

- runtime disconnected: listener binding/recovery failure;
- runtime connected but seen remains zero: notification delivery failure;
- seen increases and text characters remain zero: Gmail/Android exposed no text extras;
- text exists and auth hint remains zero: Gmail preview omitted authentication wording;
- auth hint increases and no-match increases: extractor defect;
- queued increases: notification ingestion succeeded;
- debug notification appears: P2S publish or at least one open P2P DataChannel accepted the item;
- Windows applied once plus peer ACK deletion: full Extended P2P path succeeded.

The debug notification proves transport acceptance only. It must contain no code, clipboard text, package name, relay ID, account identifier, or server address.

After the diagnostic rows:

- [ ] turn outbound debug notification OFF
- [ ] send another accepted outbound item
- [ ] confirm no debug notification appears

## Selection-only negative matrix

| UI language / application | 3s | 15s | 60s | Queue unchanged | Windows unchanged | Status |
|---|---:|---:|---:|---:|---:|---|
| Japanese / Chrome | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| English / Chrome | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Third language / Chrome | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Japanese / Firefox-family | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Third language / notes/editor | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| ClipCascade backgrounded | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

## Ordinary-copy state matrix

Test immediate Copy and Copy after 3, 15, and 60 seconds after selection.

| State | Immediate | 3s | 15s | 60s | Applied once | ACK deletion | Status |
|---|---:|---:|---:|---:|---:|---:|---|
| UI visible | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Backgrounded 30s | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Removed from recents | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Locked | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 1 minute | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 15 minutes | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 30+ minutes | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Peer disconnected/restored | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| React/service generation recreated | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

## Long-disconnect durability

| Interval | Queue retained | Applied once after reconnect | Peer ACK | Deleted only after ACK | Status |
|---|---:|---:|---:|---:|---|
| More than 10 minutes | ☐ | ☐ | ☐ | ☐ | untested |
| More than 30 minutes | ☐ | ☐ | ☐ | ☐ | untested |
| Process/service generation recreated | ☐ | ☐ | ☐ | ☐ | untested |

## Bounded queue-full matrix

Keep the peer disconnected and use 17 unique values.

- [ ] items 1–16 are accepted as distinct pending relays
- [ ] item 17 is not inserted
- [ ] item 1 remains present and unchanged
- [ ] no accepted item is deleted before ACK
- [ ] latest content-free diagnostic is `queue_full`
- [ ] settings test displays localized queue-full feedback
- [ ] reconnect drains accepted items in order
- [ ] each accepted item is applied once
- [ ] each accepted item is removed only after ACK
- [ ] a new Copy is accepted after capacity returns

## Exactly-once and echo prevention

- [ ] one native item per actual Copy
- [ ] selection alone creates zero items
- [ ] one JS listener obtains relay claim
- [ ] exactly one Windows application
- [ ] queue deletion only after defined ACK
- [ ] inbound ClipCascade writes create no outbound item
- [ ] failed transport releases claim and later retry succeeds

## Other notification-code paths

- [ ] synthetic OTP extractor -> queue -> transport -> ACK succeeds
- [ ] Beeper real notification classified without content storage
- [ ] Perceptron real notification classified without content storage
- [ ] real SMS classified without content storage

## Battery/usability observation

- [ ] comparable idle interval recorded
- [ ] foreground/background active time recorded
- [ ] battery percentage recorded
- [ ] reconnect latency with 15-second cap acceptable
- [ ] listener rebind/rescan controls are understandable
- [ ] outbound debug switch defaults OFF and is easy to disable

## Do not claim

Do not mark target-device behavior passed from CI. Real Gmail ingestion, listener recovery, debug ON/OFF behavior, multilingual selection suppression, background/screen-off delivery, long-disconnect retention, queue-full preservation, exactly-once, other real notification extraction, battery behavior, and Windows tray behavior remain unproven until isolated target-device evidence exists.
