# Next ChatGPT Handoff

This is the canonical handoff.

## Hard constraints

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- PR: `#1`, open and Draft
- never mark Ready, merge, or enable auto-merge
- preserve Extended P2P Windows-applied ACK before native deletion
- run Android and Windows CI after every final branch change
- never claim target-device success from CI
- never commit real clipboard text, notification bodies, codes, accounts, or private URLs

## Read first

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. `docs/NEXT_CHATGPT_HANDOFF.md`
5. `docs/LATEST_SYSTEM_LOCALIZED_COPY_RECOVERY_HANDOFF.md`
6. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
7. `docs/LATEST_GREEN_ARTIFACTS.md`
8. `docs/LATEST_NOTIFICATION_LISTENER_ALPHA_HANDOFF.md`
9. `docs/LATEST_GMAIL_NOTIFICATION_RELIABILITY_HANDOFF.md`

## Current implementation

- SHA: `ed9c009af0fcfc238cfc6264dd4c7b85a8fe82a3`
- commit: `[alpha-release] Stage foreground transport queue drain alpha.2`
- tag: `v3.2.1-extended.19-alpha.2`
- version: `3.2.1-extended.19-alpha.2-standalone`
- versionCode: `320124`
- Android CI: `29837847117`, success
- Windows CI: `29837846863`, success
- Actions artifact ID: `8498121042`
- Actions ZIP SHA-256: `8c20eadce450c57fadb9eab39e465332fe34f8f25f7e28d44a072162dc480db3`
- APK SHA-256: `f9f7b5fe6653beb8d0b08436657ddf719fd155e3ac9b1216b0307b7ea1cf63a7`
- APK size: `147933819` bytes
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- artifact expiry: `2026-10-19T14:10:20Z`

The tag resolves exactly to the implementation SHA.

## Corrected device evidence

Previous handoff text saying synchronization recovered was wrong.

Actual observation:

- receive works while Android app is not open;
- Android outbound works while app is open;
- Android outbound Copy fails while app is not open;
- a real Gmail verification notification failed to relay.

Treat Android background outbound capture/dispatch as broken until `.19-alpha.2` target evidence says otherwise.

## Original upstream answer

The original React Native Android client used `OnPrimaryClipChangedListener`, but its real background workaround required:

- ADB grant of `android.permission.READ_LOGS`;
- logcat monitoring for ClipboardService access-denial lines;
- launch of a temporary focusable overlay activity;
- clipboard read while focused.

Extended removed that path by design. Do not reintroduce it unless the user explicitly changes the canonical ADB-free/no-overlay requirements.

## Go fork answer

`wuxinkami/ClipCascade_go_fork` does include Android code.

Its AccessibilityService binds to a sticky native foreground service that owns the Go synchronization engine. Copy-related events cause Accessibility to ask that service to read/send clipboard data. The service obtains background clipboard access through a transparent 1x1 `TYPE_APPLICATION_OVERLAY` and requests `SYSTEM_ALERT_WINDOW`.

Extended does not copy that overlay workaround. It adopts only the ownership principle: the live foreground transport drains native durable queues rather than waiting for an app-UI React context.

## Final transform order

`prepare_relay_claim.js` ends with:

1. `prepare_internal_clipboard_guard.js`
2. `prepare_language_neutral_clipboard_copy.js`
3. `prepare_ack_safe_queue_overflow.js`
4. `prepare_gmail_ja_anchor_compat.js`
5. `prepare_gmail_notification_reliability.js`
6. `prepare_debug_notification_icon_compat.js`
7. `prepare_notification_listener_alpha_hardening.js`
8. `prepare_system_localized_copy_recovery.js`
9. `prepare_foreground_queue_drain.js`

Do not move the last two earlier. They patch the final generated Accessibility/dispatcher/service source.

## `.19-alpha.1`: Copy cue recovery

- generated `SystemCopyCuePolicy.kt` and tests;
- exact match against device-localized Android `copy` and `copyUrl` strings;
- click/context-click candidates from event/node text or content description;
- OS clipboard callback remains primary;
- explicit Copy cue waits 700 ms;
- callback serial cancels the fallback;
- fallback uses only recent remembered selection;
- selection alone remains inert;
- separate `copy_detection` health category;
- CI rejects READ_LOGS and SYSTEM_ALERT_WINDOW;
- DAWN-shaped extractor regression uses a synthetic value.

## `.19-alpha.2`: native queue drain by the live transport

`prepare_foreground_queue_drain.js` adds:

- `ClipboardRelayDispatcher.claimForForegroundService()`;
- `OtpRelayDispatcher.claimForForegroundService()`;
- `RelaySettingsModule.claimPendingForegroundRelay()`;
- `drainNativeRelayQueue()` in `StartForegroundService.js`;
- a drain call in the existing 3-second foreground-service poll.

The Notifee foreground service already owns the live P2S/P2P transport. It now claims native OTP first, then ordinary clipboard items, and sends through the unchanged `sendClipBoard` path.

The native queue's existing in-flight ID/timeout remains authoritative. Failed sends retain the item. Extended P2P still requires Windows application followed by peer ACK before native deletion.

## Diagnostic interpretation

After clearing health history:

- no `Copy detection` record after pressing Copy: app/OEM did not expose a usable Accessibility event;
- framework Copy cue recorded, delayed fallback requested: capture trigger worked despite no OS callback;
- `Clipboard capture / queued`: native persistent queue accepted the item;
- `foreground_poll / claimed`: the live foreground transport claimed it;
- outbound debug notice: local transport accepted it;
- Windows applied once plus peer ACK and pending queue zero: full Extended P2P success.

If capture queues but `foreground_poll` never appears, focus the queue-drain/service lifecycle. If `foreground_poll` appears without debug, focus send/transport. If debug appears without Windows application, focus peer path. If Windows applies and native item remains, focus peer ACK/deletion.

## Gmail / DAWN

A synthetic corpus matching the reported structure passes `OtpCodeExtractor`. Therefore do not loosen the parser first.

For the next genuine notification, record:

1. listener connected;
2. seen;
3. eligible;
4. text characters;
5. auth hint;
6. queued;
7. `foreground_poll / claimed`;
8. debug notice;
9. Windows application;
10. peer ACK/native deletion.

If seen remains zero, focus listener binding/delivery. If seen increases but characters are zero, Gmail/Android exposed no usable extras. If queued increases but no foreground claim, focus service drain.

## Mandatory target test order

1. Install `.19-alpha.2` over the existing build without uninstalling.
2. Confirm settings, Accessibility, notification access, and battery configuration survive.
3. Disable Phone Link and all competing clipboard synchronizers.
4. Enable outbound debug temporarily and clear diagnostics.
5. Foreground unique Copy; record all stages.
6. Background without force-stop; new unique selection + system Copy; record all stages.
7. Test Chrome and Firefox-family.
8. Only after basic background success: removed from recents, locked, screen-off, long disconnect, and queue-full rows.
9. For the next Gmail OTP, clear notification diagnostics immediately before triggering it.

## Preserve exactly

- no ADB/READ_LOGS/overlay;
- internal-write echo suppression;
- no selection-only sends;
- ordinary queue no TTL and no accepted-item eviction;
- queue capacity 16 and `queue_full`;
- relay IDs, native claims, and bounded timeout;
- validation-before-ACK;
- Windows clipboard application before peer ACK;
- peer ACK before native deletion;
- old-peer generation-scoped fallback;
- notification receipt guard;
- debug notification default OFF and ACK isolation.

PR #1 remains Draft.
