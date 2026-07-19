# Android Background Outbound Validation Matrix

This matrix supersedes every older checked Android outbound or real-service success item.

Before every run:

- disable Microsoft Phone Link clipboard synchronization;
- stop every other clipboard synchronization utility;
- do not use ADB, root, or Shizuku;
- record commit SHA, Android versionCode, peer build, mode, UI language, and screen state;
- use a unique synthetic text value for every ordinary-copy row;
- never store real authentication values.

## Current build

- [ ] final Android CI green on current HEAD
- [ ] final Windows CI green on current HEAD
- [ ] matching artifact ID and hashes recorded
- [ ] Android version `3.2.1-extended.14-standalone`
- [ ] Android versionCode `320118`
- [ ] stable signer unchanged
- [ ] in-place update succeeds
- [ ] settings and permissions retained

## Language-neutral selection-only negative matrix

For every row, select a unique value and leave the localized floating toolbar open without pressing Copy.

| UI language / application | Toolbar visible 3s | Toolbar visible 15s | Toolbar visible 60s | Queue unchanged | Windows unchanged | Status |
|---|---:|---:|---:|---:|---:|---|
| Japanese / Chrome | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| English / Chrome | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Third language / Chrome | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Japanese / Firefox-family | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Third language / notes/editor | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| ClipCascade backgrounded | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

Required distinctions:

- selection event alone: never queues;
- translated toolbar label appearance: never queues;
- real OS `OnPrimaryClipChangedListener` callback with recent selection: queues exactly once;
- semantic `ACTION_COPY` or Ctrl+C with a missing callback: bounded fallback queues exactly once;
- ClipCascade-owned inbound/local clipboard write: ignored by `ClipboardWriteGuard`;
- no English/Japanese/other translated Copy wording participates in the decision.

## Ordinary-copy matrix

For each state, test immediate Copy and Copy after waiting 3, 15, and 60 seconds after selection.

| State | Immediate | 3s | 15s | 60s | Windows applied once | ACK removed queue | Status |
|---|---:|---:|---:|---:|---:|---:|---|
| ClipCascade UI visible | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| UI backgrounded 30s | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Removed from recents | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Device locked | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 1 minute | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 15 minutes | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 30+ minutes | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Peer disconnected then restored | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| React/service generation reclaimed | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

## Delivery interpretation

- queue `0`, no recent diagnostic: capture missed;
- `react_context / rebind_requested`: React generation absent;
- `transport_status / retrying`: transport unavailable;
- `p2p_peer / retrying`: no peer channel;
- `react_event / emitted` then `peer_ack / acknowledged`: Windows-applied ACK path completed.

## Exactly-once and echo prevention

- [ ] one native queue item per actual Copy;
- [ ] selection alone creates zero items;
- [ ] one JavaScript listener obtains the relay claim;
- [ ] exactly one Windows clipboard application;
- [ ] queue deleted only after defined acknowledgement;
- [ ] inbound ClipCascade writes create no outbound item;
- [ ] failed transport releases claim and later retry succeeds.

## Notification-code path

- [ ] built-in synthetic OTP succeeds through extractor, queue, transport, and ACK;
- [ ] Beeper-style real notification classified without storing content;
- [ ] Perceptron-style real notification classified without storing content;
- [ ] real SMS tested without storing content.

## Do not claim

Do not mark multilingual copy confirmation, selection-only suppression, background, removed-from-recents, locked, screen-off, exactly-once, real-SMS, real-email, battery-efficiency, or Windows-tray behavior as passed without isolated target-device evidence.
