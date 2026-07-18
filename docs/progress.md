# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/TEST_MATRIX.md`
5. `docs/LATEST_RUNTIME_FIXES_HANDOFF.md`
6. `docs/LATEST_PRIORITY1_VALIDATION_HANDOFF.md`
7. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
8. `docs/LATEST_ANDROID_INIT_DUPLICATE_HANDOFF.md`
9. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
10. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
11. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`
12. `docs/LATEST_OTP_EMAIL_EXTRACTION_HANDOFF.md`

Current phase: preserve the now-stable Android relay while proving isolated outbound behavior, exactly-once delivery, runtime-control consistency, acceptable battery use, Windows tray lifecycle stability, and notification-code extraction quality. PR #1 remains Draft.

## 2026-07-09 — Beeper-style email OTP extraction repair

User report: a Beeper login email did not relay the visible six-digit login code, while the extractor still produced too many false positives.

Implemented patch:

- added line-structured extraction for standalone code lines near authentication language;
- added support for logo-alt-text layouts such as `Beeper logo 774464` when login-code context is nearby;
- widened English authentication context for `login code`, `sign-in code`, `enter the login code above`, `2FA`, and `MFA`;
- expanded keyword windows and surrounding-line scoring so email title/body/reordered notification extras are less fragile;
- added negative contexts for coupon, promo, discount, postal, error, status, tracking, and order-like values unless strong authentication context is present;
- notification listener now also folds loose `CharSequence` extras into the extraction text, without logging contents.

Tests added:

- Beeper standalone code line;
- title/preview-before-body variant;
- logo-alt-text same-line variant;
- coupon/discount false-positive rejection near login-button text.

Build identity:

- version: `3.2.1-extended.9-standalone`
- versionCode: `320113`

Final Android and Windows CI must still be checked on the latest HEAD before calling this pass green.

## 2026-07-09 — Windows tray ghost icon repair

User report: Windows notification area accumulates many default-looking ClipCascade ghost icons with `tip=ClipCascade` and owner `pid=0`. This existed upstream before Extended, but Extended watchdog full restarts made it worse. Runtime config still showed `websocket_url=wss://clipcascade.sathvik.dev/p2psignaling`, so the repeated `scheme http is invalid - goodbye` logs must be diagnosed at the runtime P2P path rather than dismissed as stale persisted config.

Implemented patch:

- `scripts/prepare_windows_tray_lifecycle.py` transforms the desktop source before build/test;
- pystray icon lifecycle is centralized in an idempotent `_dispose_tray_icon(reason)` path;
- disposal order is explicitly `icon.visible = False` before `icon.stop()`;
- create/run/visible-false/stop lifecycle events are logged with panel id, icon id, process id, and reason;
- same-process replacement of `TaskbarPanel` disposes the previous icon first;
- `EnhancedTaskbarPanel` uses the same disposal path for explicit quit, logoff, and run-finally;
- P2P signaling logs current `websocket_url`, parsed scheme, `server_url`, close arguments, and last transport error;
- only `ws` and `wss` schemes are accepted before constructing `websocket.WebSocketApp`;
- remembered `scheme http is invalid - goodbye` is treated as fatal and suppresses watchdog full restart amplification;
- watchdog full restarts are capped at three consecutive attempts, then backed off for 15 minutes.

Tests added:

- tray `visible=False -> stop()` order;
- idempotent stop;
- replacement panel disposes previous icon;
- P2P `wss://` accepted and `http://` rejected;
- remembered `scheme http is invalid - goodbye` classified as fatal while preserving current `wss://` diagnostics.

Code commit containing this patch: `c34057a9cfd10599db68e32f6a98f576d9bc8ed2`. At handoff writing time, final GitHub Actions runs had been triggered but were still queued. Do not claim this patch green until final Android and Windows CI complete.

## 2026-07-03 — Android idle-power pass

User report: the current Android build is functioning very well, but battery consumption may be too high.

Confirmed continuous costs:

1. The foreground JavaScript service synchronously read four AsyncStorage/SQLite flags once every second for its entire lifetime.
2. The visible UI read status flags every 300 ms.
3. Accessibility accepted high-volume window-content events with a 25 ms notification timeout and fetched the source node for every possible copy cue.

Low-risk repair:

- foreground-service flag polling: 1 second -> 3 seconds;
- visible UI polling: 300 ms -> 1 second;
- Accessibility notification timeout: 25 ms -> 100 ms;
- high-volume events inspect lightweight event text first;
- source-node inspection remains for direct click, context-click, and window-state events;
- the 15-minute worker heartbeat allows up to 4 seconds for the slower service loop.

The P2P/WebRTC connection and its 20-second application keepalive were deliberately left unchanged because the user reports that the transport is currently very stable. If drain remains high after this pass, measure that transport separately before changing it.

Build identity:

- version: `3.2.1-extended.8-standalone`
- versionCode: `320112`

Transport ACK, persistent queues, relay claims, startup recovery, and runtime Start/Stop synchronization remain intact.

## 2026-06-28 — Android runtime Start/Stop state repair

An already-connected service before opening the UI is valid when a previous session survived or recovery recreated it. The upper control must then display Stop, not Start.

`pollUIFlags()` previously refreshed status text from persisted `wsIsRunning` without updating the React state that renders the button. `prepare_runtime_control_state.js` now keeps those states synchronized.

Repair build:

- version: `3.2.1-extended.7-standalone`
- versionCode: `320111`

## 2026-06-28 — Android initialization and duplicate-send repair

User-observed failures:

- first launch could show `Cannot perform this operation because the connection pool has been closed`;
- reopening the application cleared the error;
- one copied value could be sent twice.

Confirmed causes and repairs:

1. `AsyncStorageBridge.disconnect()` closed React Native AsyncStorage's shared singleton database. The build transform now removes that close call and CI rejects it if it remains.
2. After failed initialization/reopen, more than one JavaScript service listener could process the same native queue event. Native `RelaySettingsModule` now atomically claims each relay ID; only one listener may send it. Failed sends release the claim, native acknowledgement releases it, and stale claims expire after five seconds.
3. Persistent queue, relay IDs, Extended P2P peer-applied acknowledgement, delayed compatibility fallback, and native acknowledgement-based deletion were preserved.

Repair build:

- version: `3.2.1-extended.6-standalone`
- versionCode: `320110`
- code/release-workflow commit: `fb31ebca8fc99a7ff9504163cf584f35e52127ba`
- Android CI `28317382119`: success
- Windows CI `28317382112`: success
- Android artifact ID `7933086597`
- Windows artifact ID `7933081818`

Exact hashes and the real-device test procedure are in `docs/LATEST_ANDROID_INIT_DUPLICATE_HANDOFF.md`.

## 2026-06-28 — Android background-send failure

### Evidence correction

- Microsoft Phone Link clipboard synchronization was active during earlier tests.
- The apparent Android copied-text and Yahoo! JAPAN SMS deliveries were not produced by ClipCascade.
- With competing synchronization excluded, Android outbound sending failed when ClipCascade was not visible.
- Peer-to-Android reception still worked in the background and apparently with the screen off.
- All prior Android outbound and real-SMS success claims are withdrawn.

Future validation must disable Phone Link and every other clipboard synchronization utility.

### Root causes found

1. Android 10+ denies ordinary clipboard reads when the app is neither focused nor the default IME. Accessibility had been used as a trigger, followed by a foreground-only `ClipboardManager` read.
2. The selected-text fallback was lost when selection events were missed or the selection collapsed before delayed capture.
3. Native clipboard and OTP queues retried but did not request recovery when transport or React state was unavailable.
4. The first recovery request could be incorrectly suppressed during the first 60 seconds after boot.
5. Startup changed persisted synchronization intent to OFF after a missing heartbeat instead of recreating the service.
6. Windows watchdog logging warned during normal ICE negotiation even though the supplied run later reached `ICE completed`.

### Repairs implemented

- Accessibility observes copy toolbar/window cues, includes non-important views, recognizes `ACTION_COPY`, attempts immediate capture, retains selection for 60 seconds, and scans interactive windows for the live selected range.
- It still requires an explicit Copy cue and queues only the selected substring.
- Clipboard and OTP dispatchers request bounded recovery for offline transport, missing React context, or failed event delivery.
- The first recovery request is no longer incorrectly cooldown-suppressed; repeated failures remain bounded.
- Startup attempts to restart a missing previous service generation.
- Test wording targets connected devices rather than Windows.
- Windows watchdog delays its unhealthy warning for ten seconds while retaining the 25-second restart threshold.

### Failed attempt recorded

Android run `28316609479` failed during AAPT resource linking. Kotlin uses `TYPE_VIEW_CONTEXT_CLICKED`, but the XML enum is `typeContextClicked`, not `typeViewContextClicked`.

Commit `f3ca3b45c51a48f18e9811b06786c030a7b818bb` corrected the XML spelling. The failure did not involve transport or ACK behavior.

### ACK protection

The following remain intact:

- native persistent queues;
- `relayId` and `ackRequested` metadata;
- peer ACK only after validated clipboard application;
- ACK-envelope handling before clipboard parsing;
- delayed old-peer fallback;
- native ACK-based deletion;
- queue wakeup after `SHARED_TEXT` listener registration.

### Mandatory next proof

1. Disable Phone Link clipboard sync and every competing clipboard utility.
2. Update the stable-signed Android app without uninstalling.
3. Confirm no connection-pool initialization error.
4. Confirm an active service displays Stop immediately after reopening the UI.
5. Test exactly one outbound application per Copy action while visible, backgrounded, reopened, removed from recents, locked, and screen-off.
6. Test synthetic and real notification-code paths.
7. Compare battery consumption over matched idle intervals before considering a P2P keepalive change.
8. On Windows, force reconnect/restart failures and confirm tray icon count stays one, then Quit leaves zero ghosts without restarting Explorer.

## Earlier work retained

The branch already contains:

- repaired Windows authentication and authenticated HTTP validation;
- visible Windows status, recovery controls, rotating logs, and Quit hardening;
- bilingual Android guided setup;
- local notification-code extraction and synthetic test path;
- persistent clipboard and OTP queues;
- Extended P2P Windows-applied ACK;
- boot, heartbeat, network-handover, and WorkManager recovery infrastructure;
- deterministic development/test signing;
- removal of obsolete ADB, READ_LOGS, overlay, upstream update, funding, and footer UI.

Detailed chronology and historical failed attempts remain in the Git history and the `LATEST_*_HANDOFF.md` files.
