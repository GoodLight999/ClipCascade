# Gmail / Notification Listener Reliability Handoff — 2026-07-19

## User report

The user states that ClipCascade has never relayed a verification code from a Gmail notification. Therefore the real Gmail notification path must be treated as non-functional and unproven. Synthetic extractor/queue tests are not evidence that Gmail notification ingestion works.

## Static audit findings

The pre-`.17` implementation had several observability and recovery defects:

1. Android notification-access authorization was displayed as if it proved a live `NotificationListenerService` binding. It did not expose whether the listener instance was actually connected in the current process.
2. Many rejection paths returned silently: relay disabled, selected-app mismatch, self notification, ongoing/foreground notification, missing notification object, text without authentication context.
3. `onListenerConnected()` did not inspect already-active notifications. Any Gmail notification posted while the listener generation was dead could be missed permanently after rebind.
4. Listener recovery depended mainly on `onListenerDisconnected()`. OEM/process death can remove the listener generation without a useful callback in the surviving app generation.
5. Notification text collection did not expose part count or total character count, so `Gmail notification arrived but body/code was not present in extras` was indistinguishable from `listener never received the notification`.

## Candidate `.17 / 320121` repair

- Tracks actual listener connection state separately from Android authorization.
- Records content-free stage counters: notification seen, text available, auth hint, queued, no match, empty extras, source-filtered, active scans, last part count, and last collected character count.
- Rebinds the notification listener from settings and the existing WorkManager heartbeat when authorization exists but no listener instance is active.
- Scans up to 64 currently active notifications younger than 15 minutes whenever the listener connects.
- Adds a manual `Reconnect listener and scan current notifications` action.
- Expands known and nested notification extras collection to depth four, including text arrays, MessagingStyle current/historic messages, public-version extras, nested arrays/lists, and SparseArray values.
- Keeps raw notification text, codes, account identifiers, package names, and email addresses out of persisted diagnostics.
- Adds Gmail-shaped extractor regression tests using title + preview + expanded body, and a negative case where Gmail exposes authentication wording but not the code.

## Outbound debug notification switch

A new settings switch defaults to OFF. When enabled, Android displays a temporary notification only after the P2S publish call or at least one open P2P DataChannel accepts the outbound item.

The notification contains only:

- source class: ordinary copied text, verification code, or manual shared data;
- transport mode: P2P or P2S.

It never contains clipboard text, verification values, relay IDs, package names, or server addresses. Debug notification failure is isolated and cannot alter transport acceptance, relay claims, ACK ordering, or queue deletion.

## Preserve

- language-neutral Copy confirmation;
- internal clipboard-write guard;
- ACK-safe bounded ordinary clipboard queue;
- Extended P2P Windows-applied ACK before native deletion;
- validation-before-ACK;
- native relay claims;
- OTP persistent queue and synthetic self-test;
- PR #1 Draft state.

## Required real-device proof

1. Install `.17` in place.
2. Open Extended settings and confirm notification authorization and actual listener runtime both show connected.
3. Clear diagnostics.
4. Send one Gmail message whose expanded notification visibly contains a fresh synthetic OTP.
5. Record only the content-free counters/stage:
   - seen remains zero: listener delivery/binding failure;
   - seen increases, text chars zero: Gmail/Android exposed no text extras;
   - text increases, auth hint zero: Gmail preview omitted authentication wording;
   - auth hint increases, no-match increases: extractor defect;
   - queued increases: notification ingestion succeeded;
   - outbound debug notification appears: transport accepted the queued value;
   - peer ACK removes the queue: full Extended P2P path succeeded.
6. Repeat with the app backgrounded and after removing it from recents.
7. Leave a Gmail OTP notification active, force/reproduce listener generation loss, then use manual reconnect/rescan and confirm the active notification is processed if younger than 15 minutes.

No real Gmail success is claimed until these target-device steps pass.
