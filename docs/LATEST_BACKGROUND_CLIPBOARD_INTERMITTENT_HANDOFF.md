# Android Background Clipboard Intermittency Handoff — 2026-07-18

## User reports

The user reported that ordinary Android copy sharing had regressed severely:

- it often worked only while the ClipCascade UI was open;
- it also worked briefly at some moments, so the failure was intermittent rather than absolute;
- the exact build where the regression began is unknown;
- a Perceptron Network email with standalone alphanumeric login code `8F92FE` may also have been missed.

Do not attribute the regression specifically to `.11`. The user noticed it around that time, but does not know when it began.

## Evidence and likely failure classes

Comparing the last user-described very stable point (`.8`) with `.11` showed that the intervening OTP work did not modify the ordinary clipboard implementation files. Two independent intermittent failure classes were therefore investigated:

1. **Capture miss:** the `.8` idle-power transform stopped inspecting the Accessibility source node for `TYPE_WINDOW_CONTENT_CHANGED`, `TYPE_ANNOUNCEMENT`, and `TYPE_NOTIFICATION_STATE_CHANGED`. Some floating toolbars and custom copy controls expose their Copy marker only through that source node, making detection app/OEM/event-order dependent.
2. **Delivery-generation loss:** Accessibility and the persistent native queue may remain alive after Android reclaims the React/Notifee service generation. The dispatcher then queues data but cannot emit `SHARED_TEXT`; the previous recovery path depended on Android accepting a background `startService()` call. Reopening the UI recreates React, matching the user's “works while the app is open” symptom.

The branch does not yet prove which class dominated on the target device. The repair covers both and adds content-free diagnostics.

## Implemented ordinary-copy repairs

### 1. Restore reliable copy-cue source inspection

`prepare_background_clipboard_reliability.js` keeps the `.8` lightweight event-text fast path, but again inspects the single event source node for all copy-relevant event types:

- clicked;
- context-clicked;
- announcement;
- notification-state change;
- window-state change;
- window-content change.

It does not restore a whole-window traversal for every high-frequency event.

### 2. Add a second copy trigger

`ClipboardAccessibilityService` now registers `ClipboardManager.OnPrimaryClipChangedListener` as a fallback.

The callback is accepted only when Accessibility remembered a non-empty external selection during the preceding three seconds. This allows the OS clipboard-change signal to confirm that Copy occurred even when the UI never emits a recognizable “Copied” marker and Android refuses background clipboard reads. The queued payload still comes from the recent Accessibility selection fallback.

After a successful queue/deduplication result, the remembered selection is consumed and cleared.

### 3. Prevent clipboard echo loops

A new process-local `ClipboardWriteGuard` marks clipboard writes performed by ClipCascade itself without retaining clipboard contents.

The final transformed JavaScript calls `NativeBridgeModule.markInternalClipboardWrite()` before text `Clipboard.setString()` calls. Native image clipboard writes are also marked. The Accessibility clipboard-change fallback consumes and ignores the matching internal write signal.

This prevents peer -> Android clipboard application and ClipCascade's own local writes from being mistaken for a new user Copy and sent back.

### 4. Recover a missing React generation in-process

`RecoveryCoordinator` now requests creation of the React context through `ReactInstanceManager.createReactContextInBackground()` when no active Catalyst instance exists.

It polls for the active context for up to four seconds and emits the existing `CLIPCASCADE_RECOVERY_REQUEST` once available. The existing Headless JS service request remains as a compatibility fallback.

The 60-second Android Service-start cooldown remains, but it no longer blocks the lighter in-process React bootstrap. A queued item can therefore retry React recovery during the cooldown instead of waiting a full minute or requiring the UI to open.

### 5. Add delivery-stage diagnostics

`ClipboardRelayDispatcher` now records content-free stages in `RelayHealthStore`:

- `transport_enabled / sync_disabled`;
- `transport_status / retrying`;
- `p2p_peer / retrying`;
- `react_context / rebind_requested`;
- `react_event / emitted`;
- `peer_ack / retrying`;
- `peer_ack / acknowledged`;
- `dispatcher / interrupted`.

No clipboard content, source-app name, account identifier, or relay value is stored.

## Perceptron Network OTP follow-up

The complete reported message was added as an extractor regression test:

- phrase: `Use this code to login:`;
- code on the following standalone line;
- six-character alphanumeric code;
- destination email address elsewhere in the message;
- expiry duration after the code.

Expected extraction is `8F92FE`.

`NotificationCodeListenerService` was also expanded to collect:

- `tickerText`;
- known notification text extras;
- message/historic-message bundles;
- safe CharSequence values in nested Bundles to depth two;
- public-version ticker/extras where present.

For an authentication-looking notification that still produces no value, diagnostics record only `notification_extras / empty` or `notification_extras / no_match`. Notification text, package name, email address, and code are not persisted.

The extractor test succeeding does not prove that Gmail or another mail app exposes the code in notification extras on the target device.

## Build identity

- versionName: `3.2.1-extended.12-standalone`
- versionCode: `320116`
- package: `com.clipcascade.extended`
- deterministic signer unchanged

## Validation at code head

Code head before documentation commits: `7dda7214ed2f9cf35926dd5faedbd583b6d21341`.

- Android standalone CI run `29629825353`: success.
- Desktop Windows CI run `29629825352`: success.

Earlier intermediate failure:

- Android CI run `29629386165` failed only because the workflow still asserted `.11 / 320115` after the code was bumped to `.12 / 320116`;
- the workflow was then updated and the actual transformed source, unit tests, Kotlin compilation, APK assembly, embedded bundle, and signer verification passed.

## Mandatory target-device test

Keep Microsoft Phone Link and every competing clipboard synchronizer disabled.

1. Install `.12` over the existing app without uninstalling.
2. Confirm the Accessibility service and background synchronization are still enabled.
3. With ClipCascade UI closed/backgrounded, select and copy unique values from several apps.
4. Test immediate Copy after selection and Copy after waiting more than three seconds.
5. Repeat after removing ClipCascade from recents, locking, and screen-off.
6. After a miss, open ClipCascade settings and record only:
   - ordinary clipboard pending queue count;
   - the latest ordinary-copy diagnostic path/result;
   - the latest recovery path/result.
7. Interpret:
   - queue count `0` and no recent copy diagnostic: capture event was missed;
   - queue count `>0` plus `react_context / rebind_requested`: React generation was absent;
   - queue count `>0` plus `transport_status / retrying`: transport not connected;
   - queue count `>0` plus `p2p_peer / retrying`: no open peer;
   - `react_event / emitted` followed by `peer_ack / acknowledged`: native queue reached validated peer application and ACK.
8. Send the Perceptron-style email again if possible, then record whether verification diagnostics show `notification_extras / empty`, `notification_extras / no_match`, or `local_extractor / queued`.

## Preserve

- Extended P2P peer-applied ACK;
- generation-scoped fallback for old peers;
- persistent clipboard and OTP queues;
- relay claim protection;
- shared AsyncStorage lifecycle fix;
- current 3-second foreground-service flag polling;
- PR #1 Draft state.

## Do not claim

Do not claim that background copy reliability, locked/screen-off copy, Perceptron/Gmail OTP extraction, or exactly-once behavior is target-device proven until the isolated device matrix passes.
