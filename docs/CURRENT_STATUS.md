# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade` (public)  
Canonical requirements: `docs/REQUIREMENTS.md`

## Current focus

Current focus is Beeper-style email OTP extraction, Windows tray ghost-icon containment, and continued Android isolated validation. PR #1 remains Draft.

## Android notification-code extraction

Latest user report: a Beeper login email did not relay its visible six-digit login code. The sample layout was a standalone six-digit code line near `Your login code for Beeper` and `manually enter the login code above` text.

Patch status:

- `OtpCodeExtractor` now has line-structured extraction for standalone code lines near authentication language;
- logo-alt-text residue such as `Beeper logo 774464` is allowed when login-code context is nearby;
- English authentication context now includes `login code`, `sign-in code`, `signin code`, `enter the login code above`, `2FA`, and `MFA`;
- keyword windows and surrounding-line scoring were widened for email title/body/reordered notification extras;
- negative contexts were added for promo/coupon/discount/postal/error/status/tracking/order-like values unless strong authentication context is present;
- `NotificationCodeListenerService` keeps explicit `Notification.EXTRA_*` collection and also folds all safe string-like extras into the extraction text;
- notification contents are not logged.

New tests cover:

- Beeper-style standalone code line;
- notification-order variant where title/preview precede expanded body;
- logo-alt-text same-line variant;
- coupon/discount false-positive rejection near login-button text.

Current Android build identity:

- app: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.9-standalone`
- versionCode: `320113`
- deterministic test signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

Do not claim this extraction pass green until final Android and Windows CI complete on the latest HEAD.

## Windows tray ghost-icon repair

User-reported Windows 11 evidence:

- many default-looking ClipCascade tray ghosts remain in the notification area;
- ghost tooltip is `ClipCascade`;
- owner PID is `0`, meaning the tray HWND/process is already gone;
- the issue existed upstream before Extended;
- Extended watchdog/restart behavior made it much worse;
- repeated logs include `scheme http is invalid - goodbye`, repeated `Restarting synchronization engine`, and watchdog full restart roughly every minute;
- persisted config still showed `server_url=https://clipcascade.sathvik.dev`, `websocket_url=wss://clipcascade.sathvik.dev/p2psignaling`, and `server_mode=P2P`.

Patch status:

- `scripts/prepare_windows_tray_lifecycle.py` adds tray lifecycle control, watchdog bounds, and P2P scheme diagnostics during desktop build/test;
- pystray disposal is centralized and idempotent;
- disposal order is `icon.visible = False` followed by `icon.stop()`;
- tray create/run/visible-false/stop lifecycle events are logged with panel id, icon id, process id, and reason;
- only one active `TaskbarPanel` may own a pystray icon per process;
- constructing a replacement panel disposes the previous icon first;
- explicit Quit, Logoff, and run-finally all use the same disposal path;
- P2P signaling validates that the URL scheme is `ws` or `wss` before constructing the WebSocket;
- P2P logs runtime `websocket_url`, parsed scheme, `server_url`, close args, and latest transport error;
- remembered `scheme http is invalid - goodbye` is classified as fatal for watchdog purposes;
- watchdog full restarts are capped at three consecutive attempts and then backed off for 15 minutes;
- fatal scheme errors suppress full restart amplification and trigger the long backoff.

New tests:

- `tests.test_windows_tray_lifecycle` verifies visible-false-before-stop, idempotent stop, replacement-panel disposal, P2P scheme diagnostics, and remembered fatal scheme errors.

Code commit for the tray patch: `c34057a9cfd10599db68e32f6a98f576d9bc8ed2`.

## Android status

Earlier Android outbound successes were misattributed to ClipCascade because Microsoft Phone Link clipboard synchronization was active. Phone Link and every competing clipboard synchronizer must remain disabled for validation.

Valid user evidence:

- Windows authentication, GUI, and synchronization work;
- peer -> Android reception works while ClipCascade is backgrounded and apparently while the screen is off;
- Android -> peer background sending was not previously working when isolated from Phone Link;
- recent repaired Android build is reported by the user as very stable, but formal isolated matrix proof remains pending.

## Android idle-power repair

Implemented after user reported large battery use despite good stability:

- foreground-service flag poll: 1 second -> 3 seconds;
- visible-UI poll: 300 ms -> 1 second;
- Accessibility notification timeout: 25 ms -> 100 ms;
- lightweight event text is checked before source-node binder access;
- source-node inspection remains for click, context-click, and window-state events;
- the 15-minute WorkManager heartbeat waits up to 4 seconds for the slower service loop.

P2P/WebRTC and its 20-second application-level keepalive remain unchanged because transport stability is currently good. If battery drain remains high, measure that layer separately before changing keepalive cadence.

## Android runtime Start/Stop state

A foreground synchronization service may already be active when the UI opens because a previous session survived, resumed at startup, or was recreated by recovery. In that case `Connected` / `接続済み` is valid before the user presses the upper control.

The upper control is a service Start/Stop toggle. Persisted `wsIsRunning` is now synchronized into React state, so:

- runtime `true` -> `Stop` / `停止`;
- runtime `false` -> `Start` / `開始`.

## Android background capture architecture

Android 10+ does not provide ordinary background clipboard reads unless the application is focused or is the default IME. Therefore Accessibility recovers explicitly selected text around an explicit Copy action instead of merely triggering `ClipboardManager.primaryClip`.

Current implementation:

- observes selection and copy-related Accessibility events;
- retains recent selected text for 60 seconds;
- scans interactive Accessibility windows for a live selected range when necessary;
- queues only the selected substring;
- requires a Copy cue rather than sending on selection alone;
- uses a persistent native queue with TTL and text deduplication;
- requests bounded transport recovery when transport or React state is unavailable.

Target-device matrix proof remains pending.

## Android initialization repair

Reported error:

`Cannot perform this operation because the connection pool has been closed.`

Confirmed cause and repair:

- `AsyncStorageBridge` received the singleton database owned by React Native AsyncStorage;
- its `disconnect()` method closed that shared database;
- the transformed bridge no longer closes the shared database and only releases its local reference;
- Android CI rejects the transformed source if the unsafe close remains.

## Exactly-once relay repair

A single native queue item could be observed by more than one JavaScript service listener after failed initialization and reopen.

Current implementation:

- native `RelaySettingsModule` owns a process-wide synchronized relay-claim map;
- each JavaScript listener must claim the opaque relay ID before sending;
- only the first listener succeeds;
- an unaccepted send releases the claim;
- native acknowledgement releases the claim;
- stale claims expire after five seconds so retry remains possible.

## Delivery acknowledgement — preserve

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

P2S:

`LOCAL_TRANSPORT_ACCEPTED -> NATIVE_ACK -> DELETE`

Extended P2P:

`LOCAL_TRANSPORT_ACCEPTED -> PEER_RECEIVED -> PEER_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

Old/non-Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

Preserve:

- `relayId` and `ackRequested`;
- peer ACK only after validated clipboard application or duplicate-already-applied handling;
- ACK-envelope handling before clipboard parsing;
- receive-hash commit only after validation;
- generation-scoped timers;
- native acknowledgement-based deletion;
- queue wakeup after `SHARED_TEXT` listener registration.

P2S and old-peer fallback are not peer-applied acknowledgement.

## Mandatory next proof

With Phone Link and all competing synchronizers disabled:

1. update Android without uninstalling and repeat the isolated matrix;
2. confirm no connection-pool initialization error across repeated cold launches;
3. confirm an active service opens with `停止`, not `開始`;
4. confirm Stop completes within about three seconds and Start reconnects;
5. copy one unique value once and confirm exactly one peer clipboard application;
6. repeat visible, backgrounded, reopened, removed from recents, locked, and screen-off;
7. confirm queue deletion only after the defined acknowledgement;
8. run synthetic OTP, then real SMS and email without storing their contents;
9. test Beeper-style real email notification and record whether the expanded notification visibly contains the code;
10. compare Android battery usage over matched idle intervals;
11. on Windows, force at least 10 reconnect/restart cycles and confirm tray icon count remains one;
12. Quit from tray and confirm no `ClipCascade` ghost remains without restarting Explorer;
13. if `scheme http is invalid - goodbye` reappears, preserve adjacent P2P diagnostic lines.

## Do not claim

Do not describe Android outbound synchronization as beta-ready, exactly-once, screen-off capable, real-SMS validated, real-email validated, notification-code reliable, or battery-efficient until the isolated target-device tests pass.

Do not describe the Windows tray ghost fix or Beeper OTP extraction pass as green until final Android and Windows CI complete after the latest documentation HEAD.
