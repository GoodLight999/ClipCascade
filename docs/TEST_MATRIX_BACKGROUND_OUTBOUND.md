# Android Background Outbound Validation Matrix

This matrix supersedes older checked Android outbound or real-service success items contaminated by Microsoft Phone Link.

Before every run:

- disable Phone Link clipboard synchronization;
- stop every competing clipboard synchronization utility;
- do not use ADB, root, or Shizuku;
- record implementation SHA, versionCode, Windows peer build, mode, UI language, and device state;
- use unique synthetic values;
- never record real authentication values or raw notification text.

## Current alpha build

- [x] implementation SHA `87e138380a139671168effd24a64df844f1bb879`
- [x] tag `v3.2.1-extended.18-alpha.1`
- [x] Android CI `29827698937`
- [x] Windows CI `29827698930`
- [x] Actions artifact ID `8494023518`
- [x] Actions ZIP SHA-256 `ae84ba8adda4b0c33ac8dbd39f835fdf7be9142dea8788e579361f6b0917cbeb`
- [x] APK SHA-256 `53da5cae4b5e2c7dd5cad0e6064aec47945b9d9620fbd30d9edaf391d37ac88d`
- [x] signer `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- [x] expiry `2026-10-19T11:50:45Z`
- [x] version `3.2.1-extended.18-alpha.1-standalone`
- [x] versionCode `320122`
- [x] listener-path self-test source present
- [x] component test cannot take listener path
- [x] synthetic privilege requires own package
- [x] persistent receipt guard present
- [x] receipt TTL/capacity policy tests pass
- [x] no ordinary clipboard TTL or overflow eviction
- [x] final transformed `queue_full` handling present
- [ ] in-place update succeeds
- [ ] settings and permissions retained

## Ordinary synchronization regression gate

The user confirmed ordinary synchronization worked on `.17`. This is the first alpha gate.

| State | Unique Copy queued | Windows applied once | Peer ACK | Queue deleted after ACK | Status |
|---|---:|---:|---:|---:|---|
| UI visible immediately after alpha install | ☐ | ☐ | ☐ | ☐ | untested |
| App backgrounded | ☐ | ☐ | ☐ | ☐ | untested |
| Removed from recents | ☐ | ☐ | ☐ | ☐ | untested |

Stop OTP testing and inspect diagnostics if the first row regresses.

## OTP component/transport self-test

This test directly queues after local extraction. It does not prove NotificationListener.

- [ ] expected synthetic value generated
- [ ] queue count increases
- [ ] outbound transport accepts
- [ ] Windows applies once
- [ ] peer ACK is observed
- [ ] queue item is removed only after ACK
- [ ] listener-test counter does not falsely claim this test

## Real notification-listener path self-test

This test must not directly queue.

| Boundary | Expected evidence | Status |
|---|---|---|
| listener runtime | connected | untested |
| callback | listener-test count +1 and seen +1 | untested |
| filters | eligible +1 | untested |
| extras | text characters > 0 | untested |
| extraction | auth context and expected value accepted | untested |
| persistence | OTP queue +1 | untested |
| transport | optional debug notice when ON | untested |
| peer | Windows applied exactly once | untested |
| ACK | queue deleted after peer ACK | untested |

## Active-notification duplicate receipt

Keep the successful listener-path test notification active.

- [ ] invoke reconnect/rescan within 30 minutes
- [ ] active-scan count increases
- [ ] candidate count includes the active notification
- [ ] `already_processed` increases
- [ ] OTP queue does not gain another item
- [ ] Windows clipboard is not applied again
- [ ] peer ACK count does not falsely advance for a duplicate
- [ ] post a fresh listener-path notification
- [ ] fresh notification is accepted and delivered once

## Real Gmail stage matrix

Run only when a genuine Gmail OTP notification is available and its expanded UI visibly contains a synthetic/non-sensitive test code.

| State | Connected | Seen +1 | Eligible +1 | Text > 0 | Auth hint | Queued | Windows once | ACK deletion | Status |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| App visible | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | unavailable so far |
| Backgrounded | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Removed from recents | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Active notification + manual rescan | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

Interpretation:

- disconnected: binding/recovery failure;
- connected but seen unchanged: Android/Gmail delivery failure;
- seen but not eligible: filter/trust-boundary rejection;
- eligible but text empty: extras exposure failure;
- text but no auth context: Gmail preview omitted required context;
- auth context plus no match: extractor defect;
- queued: Gmail ingestion succeeded;
- Windows once plus ACK deletion: full Extended path succeeded.

## Outbound debug notification

- [ ] default state is OFF after in-place update
- [ ] enable ON
- [ ] accepted ordinary Copy produces one temporary content-free notification
- [ ] accepted listener-path OTP produces one temporary content-free notification
- [ ] notification contains source class and P2P/P2S only
- [ ] disable OFF
- [ ] next accepted item produces no debug notification
- [ ] debug notification failure/denial does not alter queue or ACK

## Selection-only negative matrix

| UI language / application | 3s | 15s | 60s | Queue unchanged | Windows unchanged | Status |
|---|---:|---:|---:|---:|---:|---|
| Japanese / Chrome | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| English / Chrome | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Third language / Chrome | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Japanese / Firefox-family | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Third language / notes/editor | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

## Ordinary-copy state matrix

| State | Immediate | 3s | 15s | 60s | Applied once | ACK deletion | Status |
|---|---:|---:|---:|---:|---:|---:|---|
| UI visible | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Backgrounded 30s | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Removed from recents | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Locked | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 1 minute | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 15 minutes | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 30+ minutes | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

## Long-disconnect durability

| Interval | Queue retained | Applied once after reconnect | Peer ACK | Deleted only after ACK | Status |
|---|---:|---:|---:|---:|---|
| More than 10 minutes | ☐ | ☐ | ☐ | ☐ | untested |
| More than 30 minutes | ☐ | ☐ | ☐ | ☐ | untested |
| Process/service generation recreated | ☐ | ☐ | ☐ | ☐ | untested |

## Bounded queue-full matrix

- [ ] items 1–16 accepted while peer disconnected
- [ ] item 17 rejected
- [ ] item 1 remains
- [ ] no accepted item deleted before ACK
- [ ] diagnostic `queue_full`
- [ ] reconnect drains accepted items in order
- [ ] each accepted item applied once and removed after ACK
- [ ] new Copy accepted after capacity returns

## Exactly-once and echo prevention

- [ ] one native item per real Copy
- [ ] selection alone creates zero items
- [ ] one JS listener obtains relay claim
- [ ] exactly one Windows application
- [ ] deletion only after defined ACK
- [ ] inbound ClipCascade writes create no outbound item
- [ ] failed transport releases claim and retry succeeds

## Do not claim

CI proves transformed source/build invariants only. Alpha ordinary-sync safety, listener self-test, receipt suppression, Gmail, background/screen-off delivery, long-disconnect durability, queue-full behavior, exactly-once, battery, and tray behavior remain unproven until isolated target evidence exists.
