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
13. `docs/LATEST_BROAD_OTP_EXTRACTION_HANDOFF.md`
14. `docs/LATEST_OTP_SELF_TEST_HANDOFF.md`

Current phase: preserve the now-stable Android relay while proving isolated outbound behavior, exactly-once delivery, runtime-control consistency, acceptable battery use, Windows tray lifecycle stability, broad notification-code extraction quality, and a non-fake in-app OTP self-test path. PR #1 remains Draft.

## 2026-07-18 — OTP self-test dispatch repair

User report: the in-app synthetic verification-code test did not copy/relay the test code. The old test path posted a local synthetic notification and then depended on Android delivering the app's own notification back through `NotificationListenerService` before the value entered the OTP queue.

Implemented patch:

- the test still posts a real local notification;
- the generated notification text is immediately passed through `OtpCodeExtractor.extract()`;
- when extraction returns the expected value, a `synthetic-test:` relay ID is enqueued into `OtpRelayStore`;
- the test explicitly calls `OtpRelayDispatcher.schedule(context)`;
- listener-delivered `detected`/`deduplicated` states can no longer downgrade already queued or acknowledged self-test status;
- UI wording now states that the built-in test deterministically validates extractor + queue + transport + acknowledgement, while real third-party notifications still need separate NotificationListener validation.

Build identity:

- version: `3.2.1-extended.11-standalone`
- versionCode: `320115`

Validation before documentation-only commits:

- code head `885bac31c8d10d4a1f172624e6355248a867c411`
- Android CI `29627885148`: success
- Windows CI `29627885149`: success

## 2026-07-18 — Broad OTP extraction pass

User report: Beeper-style email OTP failed, and a single-service fix is insufficient. The extractor now models WebOTP/domain-bound SMS, SMS Retriever-style messages, SMS User Consent-style code lengths, standalone email code lines, action phrases, and multilingual verification wording.

Implemented patch:

- WebOTP/domain-bound `@domain #code` extraction;
- SMS Retriever sample support while rejecting the 11-character app hash itself;
- action phrases such as `Enter 552244 to sign in` and `Use code A8K4-P2 to authenticate`;
- standalone code lines and logo-alt-text code lines near authentication language;
- English/Japanese/Chinese/Korean/Spanish/French/German authentication keywords;
- stronger negative contexts for coupon, discount, order, tracking, status, error, phone, amount, email local-part, date, year, and app-hash false positives;
- a build transform to prevent relation words such as `is` from being swallowed into the code and to prevent candidates from spanning newline into SMS Retriever hash lines.

Tests added/expanded for Beeper, WebOTP, SMS Retriever, temporary security codes, password reset, device code, multilingual samples, and false-positive rejection.

Build identity:

- version: `3.2.1-extended.10-standalone`
- versionCode: `320114`

Validation:

- code head `26c719655491afb838e082c882bd2253ae2746ac`
- Android CI `29627309385`: success
- Windows CI `29627309390`: success

Real-device Beeper/Gmail extraction remains unproven until notification extras on the target app/device actually include the code text.

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

Code commit containing this patch: `c34057a9cfd10599db68e32f6a98f576d9bc8ed2`.

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

## Earlier work retained

The branch already contains:

- Android runtime Start/Stop state repair;
- Android initialization and duplicate-send repair;
- Android background-send recovery repairs;
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
