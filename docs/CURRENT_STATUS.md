# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade` (public)  
Canonical requirements: `docs/REQUIREMENTS.md`

## Current focus

Current focus is broad notification-code extraction, Windows tray ghost-icon containment, and continued Android isolated validation. PR #1 remains Draft.

## Android notification-code extraction

Latest user report: a Beeper login email did not relay its visible six-digit login code, and the user explicitly rejected a Beeper-only fix.

Current patch scope:

- `OtpCodeExtractor` now models WebOTP/domain-bound SMS, SMS Retriever-style messages, SMS User Consent-style code lengths, email/push standalone code lines, action phrases, and multilingual authentication labels;
- WebOTP/domain-bound `@domain #code` is accepted;
- SMS Retriever app-hash lines are rejected while the actual one-time code is extracted;
- standalone code lines and logo-alt-text lines are accepted only near authentication language;
- action phrases such as `Enter 552244 to sign in` and `Use code A8K4-P2 to authenticate` are covered;
- English, Japanese, Chinese, Korean, Spanish, French, and German samples are covered by unit tests;
- negative contexts reject coupon, promo, discount, postal, error, status, tracking, shipment, delivery, order, booking, ticket, invoice, amount, phone, URL/email-local-part, date/year, and SMS Retriever hash-alone false positives;
- `NotificationCodeListenerService` folds safe string-like extras into extraction input without logging contents.

Current Android build identity:

- app: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.10-standalone`
- versionCode: `320114`
- deterministic test signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

Latest validated code head before documentation commits:

- head: `26c719655491afb838e082c882bd2253ae2746ac`
- Android CI `29627309385`: success
- Windows CI `29627309390`: success

Do not claim real-device Beeper/Gmail extraction until the target notification app exposes the code text through notification extras and the device test passes.

## Windows tray ghost-icon repair

User-reported Windows 11 evidence:

- many default-looking ClipCascade tray ghosts remain in the notification area;
- ghost tooltip is `ClipCascade`;
- owner PID is `0`, meaning the tray HWND/process is already gone;
- the issue existed upstream before Extended;
- Extended watchdog/restart behavior made it worse;
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
9. test Beeper/Gmail-style real email notifications and record whether notification extras contain the code;
10. compare Android battery usage over matched idle intervals;
11. on Windows, force at least 10 reconnect/restart cycles and confirm tray icon count stays one;
12. Quit from tray and confirm no `ClipCascade` ghost remains without restarting Explorer;
13. if `scheme http is invalid - goodbye` reappears, preserve adjacent P2P diagnostic lines.

## Do not claim

Do not describe Android outbound synchronization as beta-ready, exactly-once, screen-off capable, real-SMS validated, real-email validated, notification-code reliable, or battery-efficient until the isolated target-device tests pass.

Do not describe the Windows tray ghost fix or broad OTP extraction pass as real-device proven until target-device tests pass.
