# Next ChatGPT Thread — Start Here

This file lets a new ChatGPT thread continue without hidden conversation history.

## Repository and branch

- Repository: `GoodLight999/ClipCascade`
- Repository visibility: **public**
- Development branch: `stability-mobile-otp`
- Draft PR: `#1`
- Base branch: `main`

A branch in a public repository is public. Do not add credentials, private server details, real notification contents, verification values, or clipboard contents.

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

Latest Android behavior commit at the time of this handoff:

`230a10562456dc96d0f354e2d78e01123d10a800`

Documentation commits follow that code commit. Consult PR #1 for the current branch HEAD before downloading artifacts.

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

The Android stability changes described below did not modify the ACK transformation scripts or Windows ACK receiver.

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

## CI and artifacts

Every Android code commit above passed Android standalone CI. The matching Windows workflow also passed for each commit.

Before real-device testing:

1. Read PR #1 to obtain the current branch HEAD.
2. Fetch Android and Windows artifacts built from that same HEAD.
3. Confirm the APK contains `assets/index.android.bundle`.
4. Record APK/EXE SHA-256 hashes.
5. Do not mix an APK from one commit with an EXE from another when validating the P2P peer ACK.

Current CI artifacts are test/debug signed. Installing over an older differently signed test package may require uninstalling that older package.

## Highest-priority next work

### Priority 1 — target-device build and guided validation

- install the current APK on HONOR 400 Pro / Android 16 / MagicOS
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

- Android 16 may redact verification-code notification content.
- Accessibility copy capture remains app-dependent until tested.
- P2S confirms local STOMP publish, not Windows clipboard application.
- P2P old-client fallback confirms only local DataChannel acceptance.
- Multiple-P2P-peer ACK semantics are not explicit.
- Recovery changes are CI/compile validated but not yet proven against MagicOS process management.
- Screen-off SMS/email delivery is still an unverified requirement, not a completed feature claim.

## Pull-request state

Keep PR #1 Draft until the mandatory HONOR 400 Pro tests and upstream regression matrix pass. Do not publish a release tag merely because CI is green.
