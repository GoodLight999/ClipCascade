# Android Background Outbound Validation Matrix

This matrix supersedes every older checked Android outbound or real-service success item.

Before every run:

- disable Microsoft Phone Link clipboard synchronization;
- stop every other clipboard synchronization utility;
- do not use ADB, root, or Shizuku;
- record commit SHA, Android versionCode, peer build, mode, UI language, and screen state;
- use a unique synthetic text value for every ordinary-copy row;
- never store real authentication values.

## Latest green build

- [x] Android CI green at `.14` implementation anchor `20ef493a3b322ec2d95f76cee8902426b7623559` — run `29669730768`
- [x] Windows CI green at the same implementation anchor — run `29669730745`
- [x] matching Android artifact recorded — ID `8436928714`
- [x] artifact ZIP SHA-256 recorded — `a569ff44a9b998754fc6190c742993508801b030c3237ed9b67b556d8e66154a`
- [x] extracted APK SHA-256 recorded — `29c8e4a88b556aa9d94a07643b894d15d746740d5207e543671d8875196d63ba`
- [x] Android version `3.2.1-extended.14-standalone`
- [x] Android versionCode `320118`
- [x] stable signer unchanged — `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

## `.15` ACK-safe queue candidate

- [ ] implementation SHA recorded
- [ ] Android CI green
- [ ] Windows CI green
- [ ] matching `.15 / 320119` Android artifact recorded
- [ ] artifact ZIP and APK SHA-256 recorded
- [ ] ordinary clipboard store contains no wall-clock TTL
- [ ] idle retry backoff policy unit tests pass
- [ ] in-place update succeeds
- [ ] settings and permissions retained

The `.15` candidate must not be distributed or called green until the unchecked CI/artifact rows are completed.

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
- no English, Japanese, or other translated Copy wording participates in the decision.

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

## ACK-safe long-disconnect matrix

Use one unique Copy per row. Do not reconnect early merely to inspect Windows.

| Disconnected/screen-off interval | Queue retained before reconnect | Windows applied once after reconnect | Peer ACK observed | Queue deleted only after ACK | Retry latency acceptable | Status |
|---|---:|---:|---:|---:|---:|---|
| More than 10 minutes | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| More than 30 minutes | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Process/service generation recreated while pending | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

## Delivery interpretation

- queue `0`, no recent diagnostic: capture missed;
- `no_text_available`: semantic confirmation occurred but no payload was recoverable;
- `selected_text_fallback`: remembered Accessibility selection supplied the payload;
- trigger `clipboard_change`: OS clipboard mutation confirmed the Copy;
- `react_context / rebind_requested`: React generation absent;
- `transport_status / retrying`: transport unavailable;
- `p2p_peer / retrying`: no peer channel;
- `react_event / emitted` then `peer_ack / acknowledged`: Windows-applied ACK path completed;
- `dispatcher / interrupted`: unexpected dispatcher failure.

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

Allowed privacy-safe real-notification classifications:

- `local_extractor / queued`;
- `notification_extras / empty`;
- `notification_extras / no_match`.

## Do not claim

Do not mark multilingual copy confirmation, selection-only suppression, background, removed-from-recents, locked, screen-off, long-disconnect retention, exactly-once, real-SMS, real-email, battery-efficiency, or Windows-tray behavior as passed without isolated target-device evidence.
