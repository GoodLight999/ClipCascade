# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/TEST_MATRIX.md`
5. `docs/LATEST_BACKGROUND_CLIPBOARD_INTERMITTENT_HANDOFF.md`
6. `docs/LATEST_OTP_SELF_TEST_HANDOFF.md`
7. `docs/LATEST_BROAD_OTP_EXTRACTION_HANDOFF.md`
8. `docs/LATEST_OTP_EMAIL_EXTRACTION_HANDOFF.md`
9. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
10. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
11. `docs/LATEST_ANDROID_INIT_DUPLICATE_HANDOFF.md`
12. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
13. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`
14. `docs/LATEST_PRIORITY1_VALIDATION_HANDOFF.md`
15. `docs/LATEST_RUNTIME_FIXES_HANDOFF.md`

Current phase: restore reliable Android ordinary-copy capture and background delivery after an intermittent regression, while preserving Extended P2P peer-applied ACK, persistent queues, OTP extraction, and PR #1 Draft state.

## 2026-07-18 — Intermittent background ordinary-copy recovery

User report:

- ordinary copy sharing often worked only while the ClipCascade UI was open;
- it sometimes worked briefly in the background, so the failure was intermittent;
- the exact first bad build is unknown;
- a Perceptron Network email with standalone alphanumeric code `8F92FE` may also have been missed.

Most important finding: `.11` did not change the ordinary-copy implementation. The strongest concrete regression candidate is the `.8` idle-power transform, which stopped inspecting Accessibility source nodes for window-content, announcement, and notification-state events. Some apps/OEM toolbars expose Copy only there.

Implemented `.12 / 320116` repair:

- restored single-source-node inspection for every copy-relevant Accessibility event while retaining lightweight event-text fast paths;
- added a three-second selected-text + clipboard-change fallback for cases with no recognizable Copy UI marker;
- consumes selection after queueing to prevent stale reuse;
- added `ClipboardWriteGuard` so ClipCascade's own inbound/local writes do not echo back through the fallback;
- added in-process React context bootstrap when a queued item outlives the React/Notifee service generation;
- retained React bootstrap during the 60-second Android Service-start cooldown;
- added content-free delivery diagnostics for transport, peer, React event, and ACK stages;
- added the full Perceptron Network email as an alphanumeric OTP regression test;
- notification collection now includes ticker text and safe nested Bundle text, with content-free `empty`/`no_match` diagnostics.

Validation at code head `7dda7214ed2f9cf35926dd5faedbd583b6d21341`:

- Android standalone CI `29629825353`: success;
- Windows CI `29629825352`: success.

Real-device background copy and Perceptron/Gmail notification extraction remain unproven until the isolated target-device matrix passes.

## 2026-07-18 — OTP self-test dispatch repair

User report: the in-app synthetic verification-code test did not copy/relay the test code. The old test path posted a local synthetic notification and then depended on Android delivering the app's own notification back through `NotificationListenerService` before the value entered the OTP queue.

Implemented patch:

- the test still posts a real local notification;
- the generated notification text is immediately passed through `OtpCodeExtractor.extract()`;
- when extraction returns the expected value, a `synthetic-test:` relay ID is enqueued into `OtpRelayStore`;
- the test explicitly calls `OtpRelayDispatcher.schedule(context)`;
- listener-delivered `detected`/`deduplicated` states can no longer downgrade already queued or acknowledged self-test status;
- UI wording states that the built-in test validates extractor + queue + transport + acknowledgement, while real third-party notifications need separate NotificationListener validation.

Build identity: `3.2.1-extended.11-standalone`, versionCode `320115`.

Validation before documentation-only commits:

- code head `885bac31c8d10d4a1f172624e6355248a867c411`;
- Android CI `29627885148`: success;
- Windows CI `29627885149`: success.

## 2026-07-18 — Broad OTP extraction pass

User report: Beeper-style email OTP failed, and a single-service fix is insufficient.

Implemented:

- WebOTP/domain-bound `@domain #code` extraction;
- SMS Retriever sample support while rejecting the 11-character app hash itself;
- action phrases such as `Enter 552244 to sign in` and `Use code A8K4-P2 to authenticate`;
- standalone code lines and logo-alt-text code lines near authentication language;
- English/Japanese/Chinese/Korean/Spanish/French/German authentication keywords;
- stronger negative contexts for coupon, discount, order, tracking, status, error, phone, amount, email local-part, date, year, and app-hash false positives;
- transform fixes for relation-word swallowing and newline crossing into app-hash lines.

Build identity: `3.2.1-extended.10-standalone`, versionCode `320114`.

Validation:

- code head `26c719655491afb838e082c882bd2253ae2746ac`;
- Android CI `29627309385`: success;
- Windows CI `29627309390`: success.

## 2026-07-09 — Beeper-style email OTP extraction repair

Implemented standalone code-line extraction, logo-alt-text support, wider login-code context, larger surrounding-line scoring windows, stronger negative contexts, and loose safe CharSequence notification extras. Build identity `.9 / 320113`.

## 2026-07-09 — Windows tray ghost icon repair

Implemented centralized idempotent pystray disposal, explicit `visible = False -> stop()`, lifecycle logging, single active panel ownership, P2P URL/scheme diagnostics, fatal scheme classification, and bounded watchdog full restarts. Code commit: `c34057a9cfd10599db68e32f6a98f576d9bc8ed2`.

## 2026-07-03 — Android idle-power pass

Reduced foreground-service flag polling from one to three seconds, visible UI polling from 300 ms to one second, Accessibility notification timeout from 25 to 100 ms, and source-node work on high-volume events. The `.12` reliability pass now partially revises the source-node restriction after an intermittent capture regression report, while retaining the reduced polling rates and single-node-only inspection.

## Earlier work retained

The branch already contains:

- Android runtime Start/Stop state repair;
- Android initialization and duplicate-send repair;
- Android background-send recovery infrastructure;
- repaired Windows authentication and authenticated HTTP validation;
- visible Windows status, recovery controls, rotating logs, and Quit hardening;
- bilingual Android guided setup;
- local notification-code extraction and synthetic test path;
- persistent clipboard and OTP queues;
- Extended P2P Windows-applied ACK;
- boot, heartbeat, network-handover, and WorkManager recovery infrastructure;
- deterministic development/test signing;
- removal of obsolete ADB, READ_LOGS, overlay, upstream update, funding, and footer UI.

Detailed chronology and failed attempts remain in Git history and the `LATEST_*_HANDOFF.md` files.
