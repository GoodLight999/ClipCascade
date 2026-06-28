# Next ChatGPT Thread — Start Here

This file is the operational handoff for a new ChatGPT thread. It is designed to be sufficient without hidden conversation history.

## Repository and safety boundary

- Repository: `GoodLight999/ClipCascade`
- Visibility: **public**
- Development branch: `stability-mobile-otp`
- Draft PR: `#1`
- Base branch: `main`

Never commit credentials, cookies, private server URLs/details, response bodies, real notification contents, real verification values, or real clipboard contents. Logs and diagnostics must remain content-free.

## Required reading order

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. This file
5. `docs/TEST_MATRIX.md`
6. `docs/LATEST_RUNTIME_FIXES_HANDOFF.md`

Then inspect PR #1 and confirm its current head and Draft state before changing code or fetching artifacts.

## Non-negotiable user intent

Build a practical Android ↔ Windows clipboard synchronizer with:

- no ADB, root, or Shizuku for ordinary use;
- AccessibilityService-based copied-text relay;
- local verification-code extraction through user-authorized notification access;
- only the extracted value sent; notification title/body remain transient and are not persisted;
- SMS and email verification values reaching Windows while HONOR 400 Pro is locked and screen-off;
- guided permission/background setup usable by a normal person;
- Japanese and English UI/extraction support;
- persistent queues and recovery across disconnect, sleep, process death, reboot, and handover;
- visible Windows status/control and self-recovery;
- standalone repeatable Android and Windows packages;
- no personal handle in product identity.

The user strongly dislikes inflated completion claims. Always distinguish compile/unit-test success, synthetic notification success, local transport acceptance, Windows clipboard application, peer ACK, and real-device screen-off proof.

## Current real-world evidence

Confirmed by the user:

- repaired Windows authentication works against the actual deployment;
- Windows GUI and synchronization work;
- Android copied-text relay works via Accessibility in the tested scenario;
- no old READ_LOGS/overlay/ADB commands are required;
- a real Yahoo! JAPAN SMS verification value reached the Windows clipboard.

The Yahoo! result is a genuine real-service end-to-end success. Exact lock/screen-off duration was not recorded, so do not count it as completion of the locked or 1/15/30+ minute screen-off matrix.

## Latest synchronized runtime baseline

Runtime/docs baseline before this final handoff documentation:

- commit: `0a570d5b9d0cc7a8fae40cf823696a68c72fa9e3`
- Android CI: `28312648447` — success
- Windows CI: `28312648425` — success
- Android artifact ID: `7931441696`
- Windows artifact ID: `7931437099`
- Android artifact ZIP digest: `sha256:c16215cd6ed23e28b2e76f131460fbb7ca838fbcba2411a71080f5a1d27487d4`
- Windows artifact ZIP digest: `sha256:b0a40a83915518a1b59969484caecbe3e656ff4f9f6a427df2397757de2a4e9e`
- extracted APK SHA-256: `77d0600560c42556eb8dff3edb3564f32268136324598cee251b8d0b4d7f502c`
- extracted EXE SHA-256: `48d21f7779cfb92aabdf9bef3b5ad403ab3a833334cbb33636531ff8ec7666b1`

Documentation commits after this point do not change runtime logic, but their embedded build identity differs. Prefer a newly green artifact from the current PR head when available. Never mix Android and Windows artifacts from different runtime heads while diagnosing behavior.

## Android update-conflict repair

Previous CI APKs used runner-local default debug certificates. Successive builds therefore had the same package but different signers, causing Android's package-conflict error.

Current implementation:

- version: `3.2.1-extended.4-standalone`
- versionCode: `320107`
- package: `com.clipcascade.extended`
- deterministic public test signer generated at build time;
- certificate SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`;
- CI verifies the completed APK certificate using `apksigner`.

Required migration:

1. uninstall the currently installed old ephemeral-signed APK once;
2. install the first stable-test-signed `extended.4` APK;
3. restore login, Accessibility, notification access, battery/background, and boot-resume settings;
4. on the next versionCode, install over `extended.4` without uninstalling and verify settings/data are retained.

The signer is intentionally public and for repeatable personal/test builds only. Do not represent it as production publisher authentication.

## Windows Quit repair

Reported defect: tray Quit removed visible state but left the PyInstaller EXE running until Task Manager killed it.

Current shutdown path:

- closes active status dialog;
- stops accepting tray/status/reconnect work;
- stops tray icon;
- quits and destroys the Tk root on the Tk thread;
- stops and joins the watchdog;
- awaits P2P/STOMP teardown;
- stops, joins, and closes the P2P asyncio loop;
- flushes logging;
- after graceful cleanup, explicit Quit applies a final process-exit guarantee.

`tests/test_windows_shutdown.py` verifies idempotent Quit signaling and status/root/icon closure. HTTP authentication and P2P ACK tests remain mandatory.

Required real test:

1. kill the old lingering process once;
2. start the new EXE;
3. choose tray Quit;
4. confirm no ClipCascade process remains in Task Manager;
5. relaunch immediately and confirm no stale mutex blocks startup.

## Automatic recovery wording

Old `Automatic reconnect: False` did **not** mean recovery was disabled. It meant the manager was not currently inside a reconnect attempt, which is normal while connected.

Current display:

- `Automatic recovery: Standing by`
- `Automatic recovery: Retrying now`
- `Automatic recovery: Paused by user`

This is display-only and does not alter transport or ACK behavior.

## Delivery acknowledgement — do not break

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

P2S:

`LOCAL_TRANSPORT_ACCEPTED -> NATIVE_ACK -> DELETE`

P2P with Extended Windows:

`LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_RECEIVED -> WINDOWS_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

P2P with old/non-Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

Preserve:

- `relayId` / `ackRequested` metadata;
- Windows ACK only after validated text clipboard application or duplicate-already-applied handling;
- Android ACK envelope handling before clipboard parsing;
- receive-hash commit only after validation;
- generation-scoped ACK timers;
- queue startup only after the `SHARED_TEXT` listener is installed.

Do not describe P2S or old-peer fallback as Windows-applied acknowledgement.

## Android features already implemented

- ADB-free copied-text relay with recent-selection fallback;
- persistent clipboard and verification queues;
- bilingual extractor and UI;
- guided five-step setup;
- persistent Sharing setup and boot-resume bottom bar;
- synthetic local notification full-path test;
- notification-listener rebind request;
- strict connected-state handling;
- immediate Headless JS plus delayed WorkManager boot recovery;
- replacement-network tracking;
- content-free diagnostics.

## Windows features already implemented

- persistent authenticated `requests.Session` and all-cookie preservation;
- safe validation of authenticated API responses;
- visible status/control window;
- restart, reconnect, disconnect, logs, diagnostics;
- rotating logs;
- second-launch activation;
- watchdog and complete P2P restart teardown;
- Windows clipboard-applied P2P ACK;
- explicit Quit cleanup and process-exit guarantee.

## Trial-and-error that must not be repeated

- Do not broaden grouped OTP patterns so prose/date fragments become candidates.
- Do not grep raw Japanese text in the Metro bundle; Metro escapes Unicode. Validate transformed source.
- Preserve `verify <email-address>` context support while excluding digits inside the address itself.
- Do not use a broad regex around the boot checkbox; isolate the unique `relaunch_on_boot` marker.
- Do not rely on GitHub runner default debug signing.
- Do not commit a binary or encoded private-key blob; deterministic test signing is generated in ignored build output.
- `apksigner` certificate output may contain multiple colon-delimited fields; parse the final field, not field 2.
- `NotificationCodeListenerService.onListenerDisconnected()` already calls `requestRebind`; do not "fix" it by duplicating rebind behavior.

## Immediate next work, in order

### Priority 1 — migrate and verify the latest builds

- confirm current PR head and both CIs;
- fetch matching Android and Windows artifacts;
- one-time uninstall and stable-signed Android installation;
- restore permissions/settings;
- test Windows tray Quit and immediate relaunch;
- record results in `docs/progress.md`, `docs/CURRENT_STATUS.md`, and `docs/TEST_MATRIX.md`.

### Priority 2 — guided setup and synthetic path on HONOR

- fresh/reset permission run through all five setup steps;
- verify Japanese and English UI;
- start Extended P2P with matching Windows build;
- run synthetic notification;
- verify posted -> detected -> queued -> Windows clipboard -> acknowledged;
- repeat with Windows offline and after reconnect.

### Priority 3 — stable update proof

- increment Android versionCode for the next build;
- install over `extended.4` without uninstalling;
- prove login/settings/permissions that Android retains are still present;
- do not rotate the test signer.

### Priority 4 — real SMS/email and recovery matrix

- SMS and email with screen on, background, locked;
- screen off 1, 15, and 30+ minutes;
- record Android redaction separately from extractor/queue/transport/ACK;
- MagicOS battery default and relaxed;
- auto-launch/secondary launch/background execution enabled;
- remove from recents, process kill, reboot, delayed fallback;
- Wi-Fi/mobile handover and offline queues.

### Priority 5 — regressions/protocol debt

- representative app copy matrix;
- Android ↔ Windows text regression;
- image/file paths;
- P2S Windows-applied ACK design;
- explicit multiple-peer ACK policy;
- private production signing and tested tagged release only after mandatory validation.

## CI discipline

- preserve existing transport ACK tests;
- run Android and Windows CI for every branch change;
- record failed attempts and their actual cause;
- keep PR #1 Draft;
- do not publish/tag merely because CI is green;
- when work stops, update all handoff documents so a new thread can continue without inference.

## Current limitations

- first stable-signer migration not yet performed on device;
- next-build in-place update not yet proven;
- Windows Quit fix not yet verified against a real lingering EXE;
- synthetic full-path test not yet run on HONOR;
- complete locked/screen-off SMS/email matrix not done;
- Android 16 redaction not measured;
- MagicOS process-death/reboot/handover not verified;
- representative app and image/file regression incomplete;
- P2S is not Windows-applied ACK;
- multiple-peer ACK policy remains implicit;
- no private production release signer.

## Pull-request state

PR #1 must remain Draft until mandatory target-device and upstream regression tests pass. The accurate product description is: **working personal beta with one successful real Yahoo! JAPAN SMS verification flow, with durability and broad compatibility still under validation**.