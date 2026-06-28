# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade` (public)  
Canonical requirements: `docs/REQUIREMENTS.md`

## Current high-level state

ClipCascade Extended now has a working Windows client, ADB-free Android copied-text relay, bilingual guided setup, persistent verification-code relay, synthetic full-path notification test, bounded recovery, and Extended P2P Windows-applied acknowledgement.

The user has successfully used a real Yahoo! JAPAN SMS verification notification: the extracted value reached the Windows clipboard. This is the first real-service verification-code success. The exact lock/screen-off duration was not recorded, so it does **not** complete the locked or 1/15/30+ minute screen-off matrix.

The project is suitable for personal beta use, but is not yet production-ready. PR #1 must remain Draft until the mandatory HONOR 400 Pro and regression matrix passes.

## Current branch/artifact baseline

Latest fully synchronized documentation handoff begins after code/docs head:

- runtime implementation and artifact baseline: `0a570d5b9d0cc7a8fae40cf823696a68c72fa9e3`
- Android CI run `28312648447`: success
- Windows CI run `28312648425`: success
- Android artifact ID `7931441696`
- Windows artifact ID `7931437099`

Always read PR #1 for the actual current branch HEAD before changing code or fetching new artifacts. Documentation-only commits after this baseline do not change runtime behavior, but branch-head build identity will differ.

## User-validated facts

- repaired Windows authentication works against the real deployment;
- Windows GUI and synchronization work;
- Android copied-text relay works through Accessibility in the user's current scenario;
- no old READ_LOGS/overlay/ADB setup is required;
- a real Yahoo! JAPAN SMS verification value reached the Windows clipboard;
- prior Windows builds could remain alive after tray Quit;
- prior Android CI builds frequently conflicted because each runner used a different debug certificate.

## Android package and update identity

- app name: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- current version: `3.2.1-extended.4-standalone`
- versionCode: `320107`
- standalone APK embeds `assets/index.android.bundle`
- Gradle uses a deterministic public development/test signer
- signer certificate SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- CI verifies the completed APK certificate with `apksigner`

Migration rule:

- the currently installed ephemeral-signed APK must be uninstalled once;
- install the first `extended.4` stable-test-signed APK and redo app permissions/settings;
- subsequent builds with the same signer and increasing versionCode should update in place and retain data;
- that in-place update/data-retention behavior still requires one real-device proof with the next versionCode.

The deterministic signer is intentionally public. It is appropriate for repeatable personal/test builds, not publisher authentication or a public production release.

## Guided Android setup and localization

The always-reachable Sharing setup flow covers:

1. Android 13+ notification permission
2. Accessibility clipboard-sharing service
3. notification-listener access
4. battery-optimization exemption
5. HONOR/MagicOS auto-launch, secondary launch, and background-execution confirmation

Default resources are English; `values-ja` supplies Japanese. Native settings, service labels, primary React labels, and displayed status follow the system locale. Internal protocol/storage tokens remain stable English.

The persistent bottom bar exposes:

- Sharing setup
- `Resume sync at startup` / `起動時に同期を再開`

The boot switch writes the same `relaunch_on_boot` value used by `BootReceiver`. Immediate Headless JS recovery and the delayed WorkManager fallback are preserved.

Fresh-install guided setup, bottom-bar layout, locale switching, and boot-switch persistence still require target-device testing.

## Accessibility clipboard relay

Implemented:

- user-authorized AccessibilityService copy-cue detection;
- direct ClipboardManager read with recent-selection fallback;
- bounded persistent queue with TTL and duplicate suppression;
- dispatch only in a genuinely connected state;
- no queue deletion merely because React emitted an event;
- old logcat/READ_LOGS/overlay path removed.

The user confirmed ordinary copied text works in the current scenario. Representative application compatibility remains untested.

## Verification-code notification relay

Implemented:

- user-authorized NotificationListenerService;
- `requestRebind` after listener disconnection;
- optional source-package filter;
- transient combination of standard text, BigText, text lines, conversation title, and MessagingStyle current/historic messages;
- persistence of only extracted value, timestamp, and opaque relay ID;
- bounded persistent queue, TTL, deduplication, retry, and acknowledgement timeout;
- queue clearing when relay/source policy changes;
- dispatch only in an actual connected state.

Extractor coverage includes Japanese and English authentication language, numeric/alphanumeric/prefixed/grouped/full-width values, code-before/code-after patterns, transaction notifications containing a separate amount, and verification addressed to an email address. False-positive controls cover dates, times, years, amounts, phones, tracking IDs, URL/email substrings, and unrelated numbers.

Real Yahoo! JAPAN SMS succeeded once. Gmail/Outlook/email formats, Android 16 redaction, and long screen-off behavior remain unverified.

## Synthetic end-to-end notification test

The settings screen can post a real local notification containing a fresh synthetic six-digit value.

Normal path:

`LOCAL_NOTIFICATION -> NOTIFICATION_LISTENER -> EXTRACTOR -> PERSISTENT_QUEUE -> REACT_TRANSPORT -> EXISTING_ACK -> DELETE`

Status states include posted, detected, queued, extraction failed, deduplicated, post failed, and acknowledged. The optional source-app filter is bypassed only for the explicitly marked synthetic test. Ordinary ClipCascade notifications remain ignored. Test value/status expire after five minutes.

The code path and unit tests pass CI, but the synthetic test has not yet been run end-to-end on the HONOR 400 Pro.

## Delivery acknowledgement — preserve exactly

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

P2S completion:

`LOCAL_TRANSPORT_ACCEPTED -> NATIVE_ACK -> DELETE`

P2P with Extended Windows:

`LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_RECEIVED -> WINDOWS_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

P2P with old/non-Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

Do not remove or bypass:

- `relayId` / `ackRequested` metadata;
- Windows ACK only after validated text clipboard application or duplicate-already-applied handling;
- Android ACK control-envelope handling before clipboard parsing;
- receive-hash commit only after validation;
- generation-scoped P2P ACK timers;
- queue startup only after the `SHARED_TEXT` listener is installed.

Limitations remain: P2S and old-peer fallback are not Windows-applied acknowledgement; multiple-peer completion occurs on the first valid ACK.

## Android recovery

Implemented and CI-tested:

- heartbeat-triggered bounded recovery;
- immediate Headless JS boot recovery;
- delayed one-time WorkManager heartbeat fallback;
- generation-scoped listener lifecycle;
- replacement-network tracking so late old-network `onLost` cannot cancel the new network;
- clipboard and verification queues resume on network availability;
- strict connected-state parsing;
- content-free diagnostics.

MagicOS process kill, long sleep, reboot, delayed fallback, and Wi-Fi/mobile handover remain unverified.

## Windows authentication and API handling

Implemented and user-validated:

- one persistent `requests.Session` across login and authenticated APIs;
- preservation of all server/proxy cookies;
- rejection of HTTP-200 login-form false positives;
- bounded timeouts;
- explicit validation of empty, HTML, non-JSON, non-object, redirected, and unsupported-mode responses;
- rejected authenticated sessions return to login rather than terminating startup;
- diagnostics expose metadata only, never response bodies, credentials, cookie values, private URLs, or real clipboard/notification content.

CI runs authenticated HTTP tests before packaging.

## Windows status, recovery, and Quit

Implemented:

- visible status/control window;
- restart, reconnect, disconnect, logs, and diagnostics controls;
- second-launch window activation;
- watchdog for persistent offline and signaling-connected/DataChannel-dead states;
- rotating logs;
- complete P2P teardown before replacement;
- Extended P2P clipboard-applied ACK receiver.

The ambiguous `Automatic reconnect: False` display was replaced by:

- `Automatic recovery: Standing by`
- `Automatic recovery: Retrying now`
- `Automatic recovery: Paused by user`

Explicit tray Quit now:

- closes the active status window;
- stops the tray and destroys the Tk root on the UI thread;
- stops and joins the watchdog;
- awaits transport teardown;
- stops, joins, and closes the P2P asyncio loop;
- flushes logging;
- applies a final explicit-exit guarantee after graceful cleanup.

Shutdown/status tests pass CI. Real Windows validation still must confirm that no EXE remains in Task Manager and that immediate relaunch is not blocked by a stale mutex.

## Important trial-and-error record

- grouped OTP regex initially joined prose/date/code tokens; narrowed patterns fixed it;
- raw Japanese grep against Metro output was invalid because Metro escaped Unicode; checks moved to transformed source;
- `verify <email-address>` initially lacked context support; extractor corpus and implementation were extended;
- broad boot-control transformation consumed unrelated login rows; replaced with index-based isolation around the unique storage marker;
- direct binary/encoded test-key commit was blocked; signer is now deterministically generated in the ignored build directory;
- first signer CI check falsely failed because `apksigner` output contains multiple colon fields; expected/generated/APK certificate digests were identical, and parsing was corrected to use the final field.

Full details are in `docs/progress.md` and `docs/LATEST_RUNTIME_FIXES_HANDOFF.md`.

## Highest-priority next work

1. Install the first stable-test-signed `extended.4` APK after one final uninstall and restore permissions/settings.
2. Run the new Windows EXE, select tray Quit, confirm the process disappears, then relaunch immediately.
3. Run the guided setup and synthetic notification test in Extended P2P on HONOR 400 Pro.
4. Build the next Android versionCode and prove in-place update plus retained settings.
5. Execute real SMS and email tests with screen on/background/locked and screen off for 1, 15, and 30+ minutes.
6. Execute process-kill, reboot, delayed WorkManager fallback, Wi-Fi/mobile handover, and queue durability tests.
7. Execute representative app copy and upstream text/image/file regressions.

## Not yet complete

- one-time stable-signer migration on the device;
- in-place update/data retention proof;
- Windows Quit real-process proof;
- guided setup and synthetic full-path test on target device;
- representative application compatibility;
- complete screen-off SMS/email matrix;
- Android 16 notification redaction measurement;
- MagicOS process-death/reboot/handover recovery;
- P2S Windows-applied acknowledgement;
- explicit multiple-peer ACK policy;
- complete upstream regression matrix;
- private production release signing and tested tagged release.

## Do not claim

Do not describe the branch as complete, production-ready, universally compatible, or reliably screen-off until the mandatory target-device and regression matrix passes. It is accurate to describe it as a working personal beta with one successful real Yahoo! JAPAN SMS verification flow.