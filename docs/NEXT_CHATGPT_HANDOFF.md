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

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/TEST_MATRIX.md`
4. This file

## User intent

Build a practical Android ↔ Windows clipboard synchronizer with:

- no ADB, root, or Shizuku for normal use
- AccessibilityService-based ordinary text copy relay
- local verification-code extraction through user-authorized notification access
- only the extracted value sent; notification title/body remain local and are not persisted
- screen-off/locked operation on HONOR 400 Pro / Android 16 / MagicOS
- persistent queues and recovery after disconnect, sleep, process death, and reboot
- visible Windows status/control UI and self-recovery
- standalone APK and repeatable Windows/Android packages
- no personal handle in product identity

The user strongly dislikes inflated completion claims. Distinguish compilation, simulated/unit validation, local transport acceptance, peer clipboard application, and real-device verification.

## Artifact identity

- App name: `ClipCascade Extended`
- Android package: `com.clipcascade.extended`
- Version family: `3.2.1-extended.*`
- Android target artifact: ARM64 standalone APK

Older test packages containing personal branding are obsolete.

## Current architecture

### Android inputs

`ClipboardAccessibilityService`

- detects bounded Japanese/English copy cues, selection events, and Ctrl+C
- attempts direct ClipboardManager access
- falls back to recent selected text
- performs three delayed attempts but stops after first success
- enqueues into `ClipboardRelayStore`
- records content-free health state only

`NotificationCodeListenerService`

- reads user-authorized notifications locally
- applies optional source-app filter
- extracts with `OtpCodeExtractor`
- persists only extracted value, source package, timestamp, and internal ID
- never persists notification title/body
- enqueues into `OtpRelayStore`

### Queues and settings

- bounded persistent queues with TTL and duplicate suppression
- synchronous queue persistence
- disabling a relay clears its pending queue
- changing notification-source policy clears old verification values
- settings show queue counts, never contents
- settings can clear queues and content-free diagnostics

### Acknowledgement

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

P2S:

`LOCAL_TRANSPORT_ACCEPTED -> NATIVE_ACK -> DELETE`

P2P with Extended Windows:

`LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_RECEIVED -> WINDOWS_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

P2P with an old/non-Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

Relevant files:

- Android dispatchers and `RelaySettingsModule`
- `scripts/prepare_relay_transport_ack.js`
- `scripts/prepare_relay_startup_order.js`
- `scripts/prepare_service_listener_lifecycle.js`
- `scripts/prepare_p2p_peer_ack.js`
- `scripts/prepare_p2p_ack_validation_order.js`
- Windows `scripts/prepare_p2p_peer_ack.py`

The peer ACK is backward-compatible metadata/control traffic. Windows ACKs only after validated text clipboard application or a duplicate already applied. P2S still lacks peer-level ACK.

### Recovery

- `RecoveryCoordinator.kt`: native request routing and cooldown
- `RecoveryListener.js`: singleton graceful stop/restart path
- `ScheduleService.kt`: heartbeat-triggered recovery with notification fallback
- `NetworkRecoveryMonitor.kt`: queue wake-up and delayed recovery after network return
- `HeadlessTask.js` / `HeadlessTaskService.kt`: boot/health background path
- `BootReceiver.kt`: boot event and wake lock

Recovery asks the old service to terminate, waits up to eight seconds for cleanup, then force-stops only if the old generation is dead. Listener subscriptions are scoped per service generation so old shutdown cannot remove a new generation's listeners.

### Legacy ADB path removed

- no logcat clipboard parsing
- no `READ_LOGS`
- no overlay permission
- no floating clipboard activity registration
- text is owned by Accessibility relay when enabled
- image/file upstream behavior remains

### Windows

- visible status/control window
- restart/reconnect/disconnect/log/diagnostics controls
- P2P teardown and watchdog recovery
- rotating logs
- P2P Extended receiver sends clipboard-applied ACK

## CI status

P2P peer-ACK implementation passed:

Android run `28291402373`:

- all JavaScript transformation steps
- Hermes bundle
- app-scoped verification extractor unit tests
- Kotlin/Java compilation
- APK build
- embedded-bundle check
- test/build/APK artifact uploads

Windows run `28291402372`:

- P2P ACK patch
- Python compilation
- dependency installation
- PyInstaller build
- EXE artifact upload

Release workflow has been aligned with the same Android validation-order correction and Windows ACK patch. No release tag has been published.

## Highest-priority next tasks

### Priority 1 — target-device test build and guided validation

- download the latest APK and Windows EXE artifacts
- install current APK on HONOR 400 Pro
- test settings/test relay first
- test Chrome, browser/WebView, Gmail, SMS, LINE/Discord/editor copies
- inspect content-free diagnostics when a copy fails
- test screen on/background/locked/15–30 minute screen-off
- test queue delivery after Windows/network returns
- test process kill and reboot

### Priority 2 — Android 16 notification redaction

- measure actual notification fields received from SMS/email apps
- determine whether OTP content is redacted on the target device
- do not claim or implement a bypass
- if redacted, design a legitimate companion-device association or explicit manual fallback

### Priority 3 — P2S peer acknowledgement

- inspect the server relay protocol and compatibility constraints
- design an optional message ID and receipt path that old clients ignore
- ACK only after Windows clipboard application
- do not require a server migration without a compatibility mode

### Priority 4 — P2P semantics and behavior tests

- define first-peer versus all-peer acknowledgement policy
- add automated tests for ACK envelopes, validation rejection, duplicate ACK, fallback timer, and old-client behavior
- exercise real Extended Android ↔ Extended Windows P2P

### Priority 5 — regression and release

- run upstream text/image/file matrix in P2S and P2P
- test Windows recovery controls
- publish only artifacts built from the tested commit

## Important remaining limitations

- Accessibility compatibility is not universal until tested per app.
- Android 16 may redact verification-code notification content.
- P2S confirms only local STOMP publish, not Windows application.
- P2P old-client fallback confirms only local DataChannel acceptance.
- Multiple-P2P-peer ACK semantics are not explicit.
- Recovery is not proven against MagicOS process management.

## Suggested first message for a new thread

> Open `GoodLight999/ClipCascade`, branch `stability-mobile-otp`. Read `docs/progress.md`, `docs/REQUIREMENTS.md`, `docs/CURRENT_STATUS.md`, `docs/NEXT_CHATGPT_HANDOFF.md`, and `docs/TEST_MATRIX.md`. Continue Priority 1: fetch the latest Android and Windows artifacts and guide HONOR 400 Pro + Windows testing. Preserve the P2P clipboard-applied ACK, keep PR #1 Draft, and update the test matrix with actual results.
