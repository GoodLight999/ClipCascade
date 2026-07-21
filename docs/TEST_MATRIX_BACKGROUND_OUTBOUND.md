# Android Background Outbound Validation Matrix

This matrix supersedes older checked Android outbound or real-service success items contaminated by Microsoft Phone Link or intermittent observations.

Before every run:

- disable Phone Link clipboard synchronization;
- stop every competing clipboard synchronization utility;
- do not use ADB, root, overlay workarounds, or Shizuku;
- record implementation SHA, versionCode, Windows peer build, P2P/P2S mode, UI language, and device state;
- use unique synthetic ordinary values;
- never record real authentication values or raw notification text;
- clear content-free health counters before an isolated boundary test.

## Current candidate

- [x] implementation SHA `ed9c009af0fcfc238cfc6264dd4c7b85a8fe82a3`
- [x] intended tag `v3.2.1-extended.19-alpha.2`
- [x] version `3.2.1-extended.19-alpha.2-standalone`
- [x] versionCode `320124`
- [x] Android CI `29837847117`
- [x] Windows CI `29837846863`
- [x] Actions artifact ID `8498121042`
- [x] Actions ZIP SHA-256 `8c20eadce450c57fadb9eab39e465332fe34f8f25f7e28d44a072162dc480db3`
- [x] APK SHA-256 `f9f7b5fe6653beb8d0b08436657ddf719fd155e3ac9b1216b0307b7ea1cf63a7`
- [x] signer `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- [x] expiry `2026-10-19T14:10:20Z`
- [x] framework-localized exact Copy fallback present
- [x] selection-alone negative tests pass
- [x] native foreground queue claims present
- [x] foreground transport poll-drain present
- [x] no ordinary clipboard TTL or overflow eviction
- [x] listener-path self-test and receipt guard present
- [x] DAWN body extractor test passes
- [ ] in-place update succeeds
- [ ] settings and permissions retained

## Last known target-device baseline

| Direction / state | Result on last tested build |
|---|---|
| Windows -> Android, app UI closed | works |
| Android -> Windows, app UI visible | works |
| Android -> Windows, app UI closed | fails |
| ordinary Copy, app UI closed | does not reach Windows |
| real Gmail DAWN notification | not relayed |

Alpha.2 is a candidate fix. None of the rows below are checked from CI alone.

## Ordinary outbound boundary matrix

Record the first boundary that does not advance.

| Device state | Copy detection requested | Capture/queue accepted | `foreground_poll claimed` | Debug notice | Windows once | Peer ACK | Queue deleted after ACK | Status |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| UI visible | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Backgrounded 30s | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Removed from recents | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Locked | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 1 minute | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 15 minutes | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 30+ minutes | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

Interpretation:

- no Copy detection row: Accessibility/copy-cue delivery failure;
- Copy detection requested but no capture/queue: selected-text or clipboard-read/capture failure;
- queue accepted but no `foreground_poll claimed`: live foreground service or RN module poll failure;
- foreground claim but no debug notice with debug ON: transport did not locally accept;
- debug notice but no Windows application: peer transport/application failure;
- Windows applied but native queue remains: peer ACK/native deletion failure;
- more than one Windows application: duplicate claim, fallback, or echo defect.

## System-localized Copy detection

For each row, select text first, then explicitly press the system/app Copy command. Selection alone must remain negative.

| UI language / application | Framework Copy cue | Queue one item | Windows one item | Status |
|---|---:|---:|---:|---|
| Japanese / Chrome | ☐ | ☐ | ☐ | untested |
| English / Chrome | ☐ | ☐ | ☐ | untested |
| Third language / Chrome | ☐ | ☐ | ☐ | untested |
| Japanese / Firefox-family | ☐ | ☐ | ☐ | untested |
| Third language / notes/editor | ☐ | ☐ | ☐ | untested |

Negative selection-only matrix:

| UI language / application | Wait 3s | Wait 15s | Wait 60s | Queue unchanged | Windows unchanged | Status |
|---|---:|---:|---:|---:|---:|---|
| Japanese / Chrome | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| English / Chrome | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Third language / Chrome | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Japanese / Firefox-family | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Third language / notes/editor | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

## Inbound regression gate

Inbound is the only direction currently observed to work with the Android UI closed. Protect it.

| State | Android applied once | No outbound echo | Connection remains healthy | Status |
|---|---:|---:|---:|---|
| UI visible | ☐ | ☐ | ☐ | untested on alpha.2 |
| Backgrounded | ☐ | ☐ | ☐ | untested on alpha.2 |
| Removed from recents | ☐ | ☐ | ☐ | untested on alpha.2 |
| Locked/screen off | ☐ | ☐ | ☐ | untested on alpha.2 |

## OTP component/transport self-test

This test directly queues after local extraction. It does not prove NotificationListener.

- [ ] expected synthetic value generated
- [ ] queue count increases
- [ ] `foreground_poll claimed` appears with UI closed
- [ ] outbound transport debug notice appears when ON
- [ ] Windows applies once
- [ ] peer ACK is observed
- [ ] queue item is removed only after ACK
- [ ] listener-test counter does not falsely claim this test

## Real NotificationListener-path self-test

This test posts a marked local notification and must not directly queue.

| Boundary | Expected evidence | Status |
|---|---|---|
| listener runtime | connected | untested |
| callback | listener-test count +1 and seen +1 | untested |
| filter | eligible +1 | untested |
| extras | collected characters > 0 | untested |
| extraction | auth hint and expected value accepted | untested |
| persistence | OTP queue +1 | untested |
| foreground drain | `foreground_poll claimed` | untested |
| transport | debug notice when ON | untested |
| peer | Windows applied exactly once | untested |
| ACK | queue deleted after peer ACK | untested |

## Real Gmail / DAWN stage matrix

The exact DAWN body is extractor-compatible in unit tests. The last real DAWN notification nevertheless failed. The next real notification must be diagnosed stage by stage.

| State | Connected | Seen +1 | Eligible +1 | Text chars > 0 | Auth hint | Extracted/queued | Foreground claimed | Debug notice | Windows once | ACK deletion | Status |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| App visible | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | previous real DAWN failed; stages unknown |
| Backgrounded | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested on alpha.2 |
| Removed from recents | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Locked/screen off | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Active notification + manual rescan | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

Interpretation:

- disconnected: listener binding/recovery failure;
- connected but seen unchanged: Android/Gmail callback delivery failure;
- seen but not eligible: filter/trust-boundary rejection;
- eligible but text empty: Gmail exposed no usable extras;
- text present but no auth hint: Gmail preview omitted authentication context;
- auth hint plus no-match: extractor defect;
- queued but no foreground claim: background transport ownership/poll defect;
- foreground claim but no debug notice: transport acceptance failure;
- debug notice but no Windows application: peer delivery/application failure;
- Windows application plus queue remaining: peer ACK/native deletion failure.

Never record the real code or notification body in the matrix.

## Active-notification duplicate receipt

Keep a successful listener-path test notification active.

- [ ] invoke reconnect/rescan within 30 minutes
- [ ] active-scan count increases
- [ ] candidate count includes the active notification
- [ ] `already_processed` increases
- [ ] OTP queue does not gain another item
- [ ] Windows clipboard is not applied again
- [ ] peer ACK count does not falsely advance
- [ ] post a fresh listener-path notification
- [ ] fresh notification is accepted and delivered once

## Outbound debug notification

- [ ] default state remains OFF after in-place update
- [ ] enable ON
- [ ] accepted ordinary Copy produces one temporary content-free notification
- [ ] accepted listener-path OTP produces one temporary content-free notification
- [ ] notification contains source class and P2P/P2S only
- [ ] debug notice does not appear before transport acceptance
- [ ] disable OFF
- [ ] next accepted item produces no debug notification
- [ ] debug notification denial/failure does not alter queue or ACK

## Long-disconnect durability

| Interval | Queue retained | Foreground claim after reconnect | Applied once | Peer ACK | Deleted only after ACK | Status |
|---|---:|---:|---:|---:|---:|---|
| More than 10 minutes | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| More than 30 minutes | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Process/service generation recreated | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

## Bounded queue-full matrix

- [ ] items 1–16 accepted while peer disconnected
- [ ] item 17 rejected
- [ ] item 1 remains
- [ ] no accepted item deleted before ACK
- [ ] diagnostic `queue_full`
- [ ] reconnect/foreground transport drains accepted items in order
- [ ] each accepted item applied once and removed after ACK
- [ ] new Copy accepted after capacity returns

## Exactly-once and echo prevention

- [ ] one native item per explicit real Copy
- [ ] selection alone creates zero items
- [ ] native event path and foreground poll cannot both send the same relay ID
- [ ] exactly one Windows application
- [ ] deletion only after defined ACK
- [ ] inbound ClipCascade writes create no outbound item
- [ ] failed transport leaves item queued and bounded retry succeeds
- [ ] old-peer fallback does not race a later peer ACK into duplicate deletion

## Do not claim

CI proves source transforms, tests, compilation, and artifact integrity only. System-localized Copy behavior, native queue insertion, foreground drain, listener self-test, receipt suppression, Gmail, removed-from-recents/screen-off delivery, long-disconnect durability, queue-full behavior, exactly-once, battery, and tray behavior remain unproven until isolated target evidence exists.
