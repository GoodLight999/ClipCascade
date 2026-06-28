# Next ChatGPT Thread — Start Here

This file lets a new ChatGPT thread continue without hidden conversation history.

## Repository and branch

- Repository: `GoodLight999/ClipCascade`
- Repository visibility: **public**
- Development branch: `stability-mobile-otp`
- Draft PR: `#1`
- Base branch: `main`

A branch in a public repository is public. Do not add credentials, private server details, real notification contents, verification values, clipboard contents, cookies, or response bodies.

## Read in this order

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. This file
5. `docs/TEST_MATRIX.md`

## Non-negotiable user intent

Build a practical Android ↔ Windows clipboard synchronizer with:

- no ADB, root, or Shizuku for normal use
- AccessibilityService-based ordinary text copy relay
- local verification-code extraction through user-authorized notification access
- only the extracted value sent; notification title/body remain local and are not persisted
- **SMS and email verification codes must reach the Windows clipboard while the HONOR 400 Pro is locked and its screen has been off**
- persistent queues and recovery after disconnect, sleep, process death, reboot, and network handover
- visible Windows status/control UI and self-recovery
- standalone APK and repeatable Windows/Android packages
- no personal handle in product identity

The screen-off SMS/email requirement is already present in the canonical requirements and test matrix. Do not weaken it or claim it is satisfied before real-device testing.

The user strongly dislikes inflated completion claims. Distinguish compilation, simulated/unit validation, local transport acceptance, peer clipboard application, and real-device verification.

## Current code baseline

Latest Android behavior commit:

`230a10562456dc96d0f354e2d78e01123d10a800`

Latest Windows authentication behavior and tests:

`fae212a9fbe127f582e6bdace5c8a0c8f25fb575`

Documentation commits follow those code commits. Consult PR #1 for the current branch HEAD before downloading artifacts.

## Preserved delivery acknowledgement

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

P2S:

`LOCAL_TRANSPORT_ACCEPTED -> NATIVE_ACK -> DELETE`

P2P with Extended Windows:

`LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_RECEIVED -> WINDOWS_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

P2P with an old/non-Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

Do not remove or bypass:

- `relayId` / `ackRequested` metadata
- Windows ACK only after validated text clipboard application or duplicate-already-applied handling
- Android ACK control-envelope handling before clipboard parsing
- receive-hash commit only after validation
- generation-scoped P2P ACK timers
- queue startup only after the `SHARED_TEXT` listener is installed

The Android stability and Windows authentication changes described below did not modify the ACK transformation scripts or Windows ACK receiver.

## 2026-06-28 Android stability changes

### Redundant boot recovery

Commit `852141452014ea7265ebbf7587f4d56e71f525b8`:

- preserves the existing Headless JS boot-recovery path
- checks `relaunch_on_boot` and the enabled sync intent before recovery
- schedules a delayed one-time WorkManager heartbeat before trying Headless JS
- leaves the WorkManager path available when Android declines or blocks the direct service start
- avoids duplicate restart when the foreground service is already healthy
- records content-free outcomes only

Android CI run `28304486580`: success.

### Network handover race

Commit `0ec58ffe0facb1934010f0270b8803ea65eb6a15`:

- tracks the actual active default `Network`
- prevents a late `onLost` callback for the old Wi-Fi/mobile network from cancelling recovery scheduled for its replacement
- still resumes both persistent queues immediately on `onAvailable`

Android CI run `28304554055`: success.

### Strict connected-state parsing

The prior code used `contains("Connected", ignoreCase = true)`. This incorrectly classified `Disconnected` as connected.

Fixed in:

- `eb8dfe5a3de9d8e00faffbea5fb541b34dd1af5b` — network recovery; Android run `28304618791`: success
- `1b95ccf7074d614a97521d519f3bb9b842a1f0f6` — clipboard queue dispatch; Android run `28304684578`: success
- `230a10562456dc96d0f354e2d78e01123d10a800` — verification-code queue dispatch; Android run `28304755556`: success

Connected status is now accepted only as `Connected` or `Connected - ...`, optionally following the existing check-mark prefix. `Disconnected` no longer suppresses recovery or starts false in-flight delivery attempts.

### Corrected investigation note

An initial hypothesis that notification-listener rebind logic was absent was wrong. `NotificationCodeListenerService.onListenerDisconnected()` already calls `requestRebind(...)`. Preserve that behavior.

## 2026-06-28 Windows login/API failure and repair

### Real artifact failure

The user observed:

- login reported HTTP 200 success
- `/csrf-token` JSON decoding failed at line 1 column 1
- `/server-mode` JSON decoding failed at line 1 column 1
- the exception escaped `authenticate_and_connect()` and terminated Windows startup

The old logs did not include safe HTTP response metadata, so do not claim the exact server-side cause. Possibilities still include an empty response, HTML/login redirect, reverse-proxy interception, a missing additional session cookie, or server/client endpoint mismatch.

### Confirmed client defects

- almost any login HTTP 200 without `bad credentials` was considered successful
- login used `requests.Session`, but authenticated follow-up calls discarded it and forwarded only `JSESSIONID`
- JSON endpoints did not validate empty bodies, HTML, final redirect paths, payload shape, or server mode value
- mandatory API failures escaped the login flow as an unexpected application error

### Repair

Commits:

- `bac2ed9f33391cee98d91532df883abb370fc4b7` — retain one authenticated session, preserve all cookies, add bounded timeouts, parse JSON safely, and emit content-free response metadata; Windows `28305923495`, Android `28305923474`: success
- `9b2c04544a8ed407b44575891ebaf512f1f7fa90` — catch authenticated API validation failures in the Windows login flow, clear the rejected session, show an actionable dialog, and reopen login; Windows `28305936639`, Android `28305936584`: success
- `fb4dd2ae60c2ba4622a54a4b4368bf681a464dc0` — add initial HTTP response unit tests; Windows `28305951497`, Android `28305951534`: success
- `57293aa060efadcef87c3442108c0ed43c27ec45` — make HTTP tests mandatory in Desktop Windows CI alongside existing P2P ACK tests; Windows `28305957237`, Android `28305957212`: success
- `03126bea1369d68d3afc6565ba38f295b585ac51` — normalize authenticated connection and timeout failures into the same safe login-recovery path; Windows `28306013776`, Android `28306013777`: success
- `fae212a9fbe127f582e6bdace5c8a0c8f25fb575` — add tests for HTTP-200 login-form false positives and content-free transport-error reporting; Windows `28306019657`, Android `28306019658`: success

### New safe diagnosis

If login still fails, the log now records only:

- endpoint path
- HTTP status
- content type
- body byte count
- final path after redirects
- redirect status codes
- transport exception class where applicable

It does not record response bodies, cookie values, credentials, or the private server URL.

`/csrf-token` failure is non-fatal because the token is used for logout. `/server-mode` remains mandatory: do not guess P2S/P2P.

### Immediate next validation

Run the repaired Windows artifact against the actual deployment.

- If it connects, the discarded-session/additional-cookie problem was the operative cause.
- If it returns to login, preserve the new safe `/server-mode` diagnostic line. That line should distinguish empty body, HTML redirect, HTTP failure, or transport failure without needing Network captures containing secrets.
- Do not add a guessed server-mode fallback merely to suppress the error.

## CI and artifacts

Every code commit above passed Android standalone CI and the matching Windows workflow.

Before real-device testing:

1. Read PR #1 to obtain the current branch HEAD.
2. Fetch Android and Windows artifacts built from that same HEAD.
3. Confirm the APK contains `assets/index.android.bundle`.
4. Record APK/EXE SHA-256 hashes.
5. Do not mix an APK from one commit with an EXE from another when validating the P2P peer ACK.

Current CI artifacts are test/debug signed. Installing over an older differently signed test package may require uninstalling that older package.

## Highest-priority next work

### Priority 1 — Windows login re-test, then target-device validation

- run the repaired Windows login first
- retain the content-free `/server-mode` diagnosis only if it still fails
- install the matching APK on HONOR 400 Pro / Android 16 / MagicOS
- start with settings status and the synthetic test relay
- verify P2P Extended Android → Extended Windows delivery and clipboard-applied ACK
- run Chrome, Gmail, SMS, LINE/Discord/editor copy tests
- run screen on, background, locked, and 1/15/30+ minute screen-off cases
- test Windows offline → queued item → Windows returns
- test Wi-Fi ↔ mobile-data handover
- test app removal from recents, process kill, and reboot
- test the delayed WorkManager boot fallback by making the immediate Headless path fail or be killed
- record only content-free diagnostics and synthetic values

### Priority 2 — Android 16 notification redaction

- measure actual fields exposed to NotificationListenerService for SMS and email notifications
- determine whether OTP-like content is redacted on the target HONOR device
- distinguish absent/redacted notification content from extractor failure and transport failure
- do not claim or implement an OS-security bypass
- if redacted, design a legitimate fallback only after recording the actual behavior

### Priority 3 — extraction coverage from real notification layouts

If notification text is present but extraction fails:

- inspect which standard notification extras contain synthetic test text
- add support for required standard layouts, such as MessagingStyle, without persisting full notification content
- extend extractor tests with synthetic examples only
- keep full title/body out of queues, logs, and Windows

### Priority 4 — remaining protocol and regression work

- P2S Windows-applied acknowledgement design
- explicit multiple-P2P-peer acknowledgement policy
- automated ACK-envelope and old-client behavior tests
- upstream text/image/file regression in P2S and P2P
- Windows recovery-control and watchdog tests

## Important remaining limitations

- The repaired Windows authentication path has passed unit/CI validation but has not yet been re-tested against the user's server.
- Android 16 may redact verification-code notification content.
- Accessibility copy capture remains app-dependent until tested.
- P2S confirms local STOMP publish, not Windows clipboard application.
- P2P old-client fallback confirms only local DataChannel acceptance.
- Multiple-P2P-peer ACK semantics are not explicit.
- Recovery changes are CI/compile validated but not yet proven against MagicOS process management.
- Screen-off SMS/email delivery is still an unverified requirement, not a completed feature claim.

## Pull-request state

Keep PR #1 Draft until the mandatory HONOR 400 Pro tests and upstream regression matrix pass. Do not publish a release tag merely because CI is green.
