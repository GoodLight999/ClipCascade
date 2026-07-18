# Android Background Outbound Validation Matrix

This matrix supersedes every older checked Android outbound or real-service success item.

Before every run:

- disable Microsoft Phone Link clipboard synchronization;
- stop every other clipboard synchronization utility;
- do not use ADB, root, or Shizuku;
- record commit SHA, Android versionCode, peer build, mode, locale, and screen state;
- use a unique synthetic text value for every ordinary-copy row;
- never store real authentication values.

## Current build

- [x] Android code CI green at code head `7dda7214ed2f9cf35926dd5faedbd583b6d21341` — run `29629825353`
- [x] Windows CI green at the same code head — run `29629825352`
- [ ] final documentation HEAD CI green
- [ ] matching artifact ID and hashes recorded
- [ ] Android version `3.2.1-extended.12-standalone`
- [ ] Android versionCode `320116`
- [ ] stable signer unchanged
- [ ] in-place update succeeds
- [ ] settings and permissions retained

## Initialization stability

- [ ] five consecutive cold launches complete without a closed connection-pool error
- [ ] close and reopen the Android UI while synchronization is active
- [ ] reopen does not create an additional outbound listener
- [ ] foreground service remains connected after UI reopen
- [ ] AsyncStorage settings remain readable after queue and recovery checks

## Ordinary-copy matrix

For each state, test both immediate Copy after selecting and Copy after waiting more than three seconds.

| State | Immediate Copy | Copy after >3s | Pending queue after miss | Latest copy diagnostic | Latest recovery diagnostic | Windows applied once | ACK removed queue | Status |
|---|---:|---:|---:|---|---|---:|---:|---|
| ClipCascade UI visible | ☐ | ☐ | — | — | — | ☐ | ☐ | untested |
| UI backgrounded 30s | ☐ | ☐ | — | — | — | ☐ | ☐ | untested |
| UI reopened then backgrounded | ☐ | ☐ | — | — | — | ☐ | ☐ | untested |
| Removed from recents | ☐ | ☐ | — | — | — | ☐ | ☐ | untested |
| Device locked | ☐ | ☐ | — | — | — | ☐ | ☐ | untested |
| Screen off 1 minute | ☐ | ☐ | — | — | — | ☐ | ☐ | untested |
| Screen off 15 minutes | ☐ | ☐ | — | — | — | ☐ | ☐ | untested |
| Screen off 30+ minutes | ☐ | ☐ | — | — | — | ☐ | ☐ | untested |
| Peer disconnected then restored | ☐ | ☐ | — | — | — | ☐ | ☐ | untested |
| React/service generation reclaimed | ☐ | ☐ | — | — | — | ☐ | ☐ | untested |

## Capture-stage interpretation

- queue `0` and no recent ordinary-copy diagnostic: Accessibility/clipboard-change capture was missed;
- `no_text_available`: a cue occurred but no selected range or readable clipboard value was recovered;
- `clipboard_denied_no_fallback`: Android denied direct background clipboard access and no selection fallback existed;
- `selected_text_fallback`: remembered Accessibility selection supplied the payload;
- trigger `clipboard_change`: the selected-text + OS clipboard-change fallback fired;
- queue nonzero: capture worked and failure is downstream.

## Delivery-stage interpretation

- `transport_enabled / sync_disabled`: synchronization intent is off;
- `transport_status / retrying`: transport is not connected;
- `p2p_peer / retrying`: no open peer channel;
- `react_context / rebind_requested`: native queue survived but React generation is absent;
- `react_event / emitted`: native queue item reached the JavaScript listener;
- `peer_ack / retrying`: transport accepted earlier but ACK timed out;
- `peer_ack / acknowledged`: validated peer application and native deletion path completed;
- `dispatcher / interrupted`: unexpected dispatcher exception.

## Exactly-once relay

For each case use one unique synthetic string and perform one Copy action.

- [ ] one native queue item is created
- [ ] one JavaScript listener obtains the relay claim
- [ ] other listeners are rejected for the same relay ID
- [ ] ClipCascade's internal clipboard writes do not create a second outbound item
- [ ] exactly one peer clipboard application occurs
- [ ] exactly one peer acknowledgement completes the item
- [ ] queue returns to zero only after acknowledgement
- [ ] failed transport releases the claim and later retry succeeds
- [ ] same text deliberately copied after more than five seconds is allowed as a new action

Failure classification:

- two native queue items: capture duplicate or internal-write echo;
- one queue item and two peer applications: listener/transport duplicate;
- one peer application and two UI/history observations: receiver UI or another clipboard observer.

## Representative applications

- [ ] Chrome
- [ ] Firefox-family browser
- [ ] Gmail
- [ ] Outlook
- [ ] SMS application
- [ ] LINE
- [ ] Discord
- [ ] notes/editor
- [ ] WebView application

## Recovery

- [ ] missing React context triggers in-process bootstrap without opening UI
- [ ] React bootstrap remains available during Android Service-start cooldown
- [ ] rejected Service starts remain rate-limited
- [ ] zero P2P peers keeps the item queued without restart churn
- [ ] network return drains queue
- [ ] peer return drains queue
- [ ] process/service generation replacement drains queue
- [ ] reboot and delayed fallback drain queue

## Notification-code path

| Source | Expected classification | Status |
|---|---|---|
| Built-in synthetic OTP | extractor -> queue -> transport -> ACK | untested on target |
| Beeper email | `local_extractor / queued`, `notification_extras / empty`, or `notification_extras / no_match` | untested |
| Perceptron Network `8F92FE` email | same three-way classification | unit test green; real notification untested |
| Real SMS numeric OTP | same three-way classification | untested |
| WebOTP/domain-bound sample | code extracted; domain line not relayed | unit-tested only |
| SMS Retriever sample | code extracted; 11-char app hash rejected | unit-tested only |

- [ ] built-in synthetic OTP succeeds visible
- [ ] built-in synthetic OTP succeeds backgrounded
- [ ] built-in synthetic OTP succeeds locked
- [ ] built-in synthetic OTP succeeds after screen-off
- [ ] Beeper-style real email classified without storing content
- [ ] Perceptron-style real email classified without storing content
- [ ] real SMS tested without storing content
- [ ] selected mail/SMS apps are confirmed in the app picker

## Windows

- [ ] normal ICE negotiation under ten seconds produces no unhealthy warning
- [ ] persistent failure produces warning after grace period
- [ ] normal startup shows exactly one ClipCascade tray icon
- [ ] ten reconnect/restart cycles create no tray ghosts
- [ ] tray Quit removes the process
- [ ] Quit leaves zero ghost icons without restarting Explorer
- [ ] immediate relaunch succeeds

## Do not claim

Do not mark Android background, removed-from-recents, locked, screen-off, exactly-once, real-SMS, real-email, battery-efficiency, or Windows-tray rows as passed without isolated target-device evidence.
