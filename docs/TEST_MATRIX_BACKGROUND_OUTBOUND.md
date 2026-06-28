# Android Background Outbound Validation Matrix

This matrix supersedes any older checked Android outbound or real-service success item.

Before every run:

- disable Microsoft Phone Link clipboard synchronization;
- stop every other clipboard synchronization utility;
- record commit SHA, Android versionCode, peer build, mode, locale, and screen state;
- use synthetic text except for the separately controlled real-service test.

## Build

- [ ] Android CI green on current HEAD
- [ ] Windows CI green on current HEAD
- [ ] Matching artifact IDs and hashes recorded
- [ ] Android version `3.2.1-extended.5-standalone`
- [ ] Android versionCode `320109`
- [ ] Stable signer unchanged
- [ ] In-place update succeeds
- [ ] Settings and permissions retained

## Copy capture stages

- [ ] Copy cue observed
- [ ] Selected substring recovered
- [ ] Selection alone does not send
- [ ] Native queue count increases
- [ ] Transport accepts the item
- [ ] Exact peer clipboard update occurs
- [ ] Defined acknowledgement occurs
- [ ] Native queue item is deleted after acknowledgement

Failure classification:

- no diagnostic and queue zero: Accessibility missed the cue or selection;
- `no_text_available`: cue observed but no selected range was recovered;
- `clipboard_denied_no_fallback`: background clipboard access unavailable and no selection fallback;
- `selected_text_fallback`: remembered selection used;
- `active_window_selection`: interactive-window scan used;
- queue remains nonzero: capture worked but transport or acknowledgement is blocked.

## Screen states

For each representative application:

- [ ] app visible
- [ ] app backgrounded for 30 seconds
- [ ] app removed from recents
- [ ] device locked
- [ ] screen off for 1 minute
- [ ] screen off for 15 minutes
- [ ] screen off for 30+ minutes

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

- [ ] Offline transport requests bounded recovery
- [ ] Missing React context requests bounded recovery
- [ ] First request after boot is not suppressed
- [ ] Rejected starts are rate-limited
- [ ] Zero P2P peers keeps the item queued without restart churn
- [ ] Network return drains queue
- [ ] Peer return drains queue
- [ ] Process replacement drains queue
- [ ] Reboot and delayed fallback drain queue

## Notification-code path

- [ ] Synthetic notification succeeds while visible
- [ ] Synthetic notification succeeds while backgrounded
- [ ] Synthetic notification succeeds while locked
- [ ] Synthetic notification succeeds after 1 minute screen-off
- [ ] Synthetic notification succeeds after 15 minutes screen-off
- [ ] Synthetic notification succeeds after 30+ minutes screen-off
- [ ] Real SMS tested without storing its contents
- [ ] Real email tested without storing its contents
- [ ] Android redaction recorded separately from extraction and transport

## Windows

- [ ] Normal ICE negotiation under ten seconds produces no unhealthy warning
- [ ] Persistent failure produces a warning after the grace period
- [ ] Tray Quit removes the process from Task Manager
- [ ] Immediate relaunch succeeds

Do not restore an Android outbound success claim until the isolated exact peer clipboard update and acknowledgement are both observed.
