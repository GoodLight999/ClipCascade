# Android Background Outbound Validation Matrix

This matrix supersedes every older checked Android outbound or real-service success item, including Phone Link-contaminated rows.

Before every run:

- disable Microsoft Phone Link clipboard synchronization;
- stop every other clipboard synchronization utility;
- do not use ADB, root, or Shizuku;
- record commit SHA, versionCode, Windows peer build, mode, UI language, and screen state;
- use a unique synthetic text value for every ordinary-copy row;
- never store real authentication values.

## Current green build

- [x] implementation SHA `f86705c513c56a9fd24e218f8513dad9cead2ed8`
- [x] Android CI `29675438972`
- [x] Windows CI `29675438978`
- [x] artifact ID `8438725636`
- [x] ZIP SHA-256 `dda947ceb29452edc4defc94ee3c09852a87529b2db581a0f1d304b0182564f6`
- [x] APK SHA-256 `1bb1301e0a44a06f42cb04cbe55de03e9abc0baa6c224738d89f4686409a5def`
- [x] signer `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- [x] expiry `2026-10-17T05:49:26Z`
- [x] Android version `3.2.1-extended.16-standalone`
- [x] versionCode `320120`
- [x] no ordinary clipboard TTL
- [x] no overflow eviction of accepted items
- [x] final transformed `queue_full` handling present
- [x] capacity and retry policy unit tests pass
- [ ] in-place update succeeds
- [ ] settings and permissions retained

CI proves transformed source and build invariants, not HONOR target-device behavior.

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

## Notification-code path

- [ ] synthetic OTP extractor -> queue -> transport -> ACK succeeds
- [ ] Beeper real notification classified without content storage
- [ ] Perceptron real notification classified without content storage
- [ ] real SMS classified without content storage

Allowed classifications:

- `local_extractor / queued`
- `notification_extras / empty`
- `notification_extras / no_match`

## Diagnostics interpretation

- queue `0`, no diagnostic: capture missed
- `no_text_available`: confirmation but no payload
- `selected_text_fallback`: Accessibility selection used
- `queue_full`: accepted items preserved; new item rejected
- `transport_status / retrying`: transport unavailable
- `p2p_peer / retrying`: no peer
- `react_context / rebind_requested`: React absent
- `react_event / emitted` then `peer_ack / acknowledged`: full ACK path
- `dispatcher / interrupted`: internal exception

## Battery/usability observation

- [ ] comparable idle interval recorded
- [ ] foreground/background active time recorded
- [ ] battery percentage recorded
- [ ] reconnect latency with 15-second cap acceptable
- [ ] queue-full behavior is understandable from diagnostics

## Do not claim

Do not mark target-device behavior passed from CI. Multilingual selection suppression, background/screen-off delivery, long-disconnect retention, queue-full preservation, exactly-once, real notification extraction, battery behavior, and Windows tray behavior remain unproven until isolated device evidence exists.
