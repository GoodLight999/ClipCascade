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
- only the extracted value sent; full notification title/body remain transient and are not persisted
- **SMS and email verification codes must reach the Windows clipboard while the HONOR 400 Pro is locked and its screen has been off**
- guided permission/background setup that a normal user can follow
- Japanese and English UI and verification-language support
- a synthetic test notification proving the normal extraction/queue/transport path
- persistent queues and recovery after disconnect, sleep, process death, reboot, and network handover
- visible Windows status/control UI and self-recovery
- standalone APK and repeatable Windows/Android packages
- no personal handle in product identity

The user strongly dislikes inflated completion claims. Distinguish compilation, unit tests, synthetic notification tests, local transport acceptance, peer clipboard application, and real-device screen-off verification.

## Current real-world validation

The user has confirmed:

- the repaired Windows artifact logs into the actual deployment;
- the Windows GUI works;
- Android copied-text relay works through Accessibility in the current test scenario;
- the current path works without rerunning the old ADB commands.

Do not widen that into universal app compatibility or screen-off OTP reliability. The target-device matrix remains outstanding.

## Current code baselines

- Android recovery/queue baseline: `230a10562456dc96d0f354e2d78e01123d10a800`
- Windows authentication repair/tests: `fae212a9fbe127f582e6bdace5c8a0c8f25fb575`
- Guided setup/localization/synthetic test/final bilingual extractor: `5433eb8e8ffaae47ed6873af67ea1c1df6824333`

Documentation commits follow. Always read PR #1 for the actual branch HEAD before fetching artifacts.

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

The synthetic test uses this existing ACK path. There is no test-only queue deletion shortcut.

## Android stability already present

- redundant boot recovery: immediate Headless JS plus delayed WorkManager heartbeat
- replacement-network tracking so a late old-network `onLost` does not cancel recovery
- strict connected-state parsing; `Disconnected` is never accepted as `Connected`
- notification-listener rebind request after listener disconnection
- persistent clipboard and verification queues
- content-free recovery diagnostics

These paths are CI validated but still require MagicOS process-kill, reboot, sleep, and handover tests.

## Windows authentication repair

The former artifact logged HTTP 200 login success and then crashed decoding `/csrf-token` and `/server-mode`.

Repairs retain one `requests.Session`, preserve all server/proxy cookies, validate authenticated JSON responses, reject HTTP-200 login-form returns, add bounded timeouts, and return failed sessions to the login flow with content-free response metadata.

Windows CI runs HTTP handling tests and existing P2P ACK tests before PyInstaller. The user confirmed the repair works against the real deployment.

## Guided bilingual setup

Files:

- `RelaySettingsActivity.kt`
- `SetupPermissionHelper.kt`
- `NotificationAccessPrompt.kt`
- `RelaySettingsStore.kt`
- `res/values/strings.xml`
- `res/values-ja/strings.xml`
- `RelaySettingsModule.kt`
- `AppRoot.js`
- `scripts/prepare_extended_bundle.js`
- `scripts/prepare_extended_status_i18n.js`

Five setup steps:

1. Android 13+ runtime notification permission
2. Accessibility clipboard-sharing service
3. notification-listener access
4. battery-optimization exemption
5. HONOR/MagicOS background/auto-launch confirmation

`Continue setup` opens the next missing setting. The MagicOS text explicitly names auto-launch, secondary launch, and background execution. The final manufacturer-specific step is user-confirmed because Android does not expose one reliable cross-vendor query API.

The distributed Extended UI uses the system locale:

- default resources: English
- `values-ja`: Japanese
- service labels/descriptions: localized
- persistent Sharing setup entry: localized
- primary React labels: transformed to Japanese/English at build time
- connection/login/P2P status: translated only when displayed; internal protocol/storage tokens remain unchanged

Old READ_LOGS and overlay commands are removed from the transformed UI. CI asserts their absence and asserts bilingual transformed source before Metro bundling.

## Synthetic verification notification test

Files:

- `OtpTestNotificationManager.kt`
- `OtpTestStatusStore.kt`
- `NotificationCodeListenerService.kt`
- `OtpRelayDispatcher.kt`
- `RelaySettingsActivity.kt`

Flow:

`REAL LOCAL TEST NOTIFICATION -> NotificationListenerService -> OtpCodeExtractor -> OtpRelayStore -> OtpRelayDispatcher -> React transport -> existing native ACK`

Behavior:

- fresh random fake six-digit value
- localized notification/channel text
- explicitly marked synthetic extra
- bypasses optional source-app filter only for the marked synthetic test
- ordinary ClipCascade foreground notifications remain ignored
- verifies extracted value equals the expected synthetic value before queueing
- status: posted, detected, queued, extraction failed, deduplicated, post failed, acknowledged
- settings refreshes status every second while visible
- status/value expires after five minutes
- test requires verification relay, notification permission, and notification access
- Extended P2P acknowledgement follows validated Windows clipboard application
- P2S still provides only its documented local transport acceptance

The test path is compiled and CI validated but has not yet been run on HONOR 400 Pro.

## Verification-code extractor

Current coverage:

- Japanese authentication/confirmation/login/sign-in/one-time/identity/two-step/security language
- English OTP/one-time/verification/security/auth/login/sign-in/confirmation/access/two-factor language
- full-width normalization
- numeric, compact alphanumeric, prefixed, and grouped formats
- candidate before or after the authentication phrase
- standard title/text, BigText, text lines, conversation title, MessagingStyle current/historic messages
- transaction amount or destination email address may coexist with a separate legitimate OTP
- `verify <email-address>` is recognized as authentication context

False-positive controls:

- dates, times, years
- monetary values adjacent to a candidate
- phone numbers
- tracking/delivery references
- candidate substrings inside URLs/email addresses
- generic postal, promotion, and error codes
- ordinary numbers without authentication context

Important trial and error:

- `000d964408d7f4824cd0f16fb9d47954ccddf425` failed because an over-broad grouped pattern joined prose and following values.
- `acd5cb9cf0dad674fb58b1d1b691d4f3cdf941ee` restricted spaces to grouped digits and hyphens to grouped alphanumerics.
- `aa799b3553b2bfa160f793324972b072edead18b` expanded the bilingual corpus; Android run `28307672613` succeeded.
- `9d06c1b175aaff118f09be7df929486155cc67e9` added transaction-notification positives; Android run `28307814762` succeeded.
- `bc7cbfc7005fa925662bc0dc3e9969e0b799a9a2` failed one new `verify <email-address>` positive case in Android run `28308003116`.
- `5433eb8e8ffaae47ed6873af67ea1c1df6824333` added that context; Android run `28308127548` and Windows run `28308127573` succeeded.

## CI verification nuance

Commit `abb9ca720ab728c56d8ee490132f0c9c1f6ae572` failed only because CI grepped raw Japanese text inside a Metro bundle that escaped Unicode. It did not prove a product failure.

Commit `9b4e5da9b62c6a3054421c90697cf6363ce67134` moved assertions to transformed `App.js`/`AppRoot.js`. Run `28307959797` completed status localization, bilingual/no-ADB assertions, bundling, unit tests, APK assembly, embedded-bundle verification, and artifact upload successfully.

## Highest-priority next work

### Priority 1 — final CI and matching artifacts

- obtain current PR head
- confirm Android and Windows CI success on that exact head
- fetch Android and Windows artifacts from the same head
- confirm APK contains `assets/index.android.bundle`
- record SHA-256 hashes
- keep PR #1 Draft

### Priority 2 — guided setup and synthetic test on HONOR

- install current APK
- start from fresh install or reset permissions
- follow all five setup steps
- confirm Japanese UI under Japanese locale and English UI under English locale
- start Extended P2P with matching Windows build
- post synthetic notification
- confirm status reaches posted -> detected -> queued -> acknowledged
- confirm Windows clipboard equals the displayed synthetic value
- repeat with Windows offline and after reconnect

### Priority 3 — real SMS/email screen-off matrix

- SMS and email with screen on/background/locked
- screen off 1, 15, and 30+ minutes
- MagicOS battery default and relaxed
- auto-launch/secondary launch/background execution enabled
- app removed from recents, process killed, device rebooted
- record notification visibility/redaction, extractor result, queue result, transport result, and ACK independently

### Priority 4 — remaining regression/protocol work

- representative app copy matrix
- Wi-Fi/mobile handover
- delayed boot fallback
- P2S Windows-applied acknowledgement design
- explicit multiple-P2P-peer ACK policy
- upstream text/image/file regression
- Windows sleep/resume/watchdog controls

## Remaining limitations

- synthetic notification test is not yet target-device validated
- Android 16 may redact real SMS/email verification content
- screen-off SMS/email delivery is unverified
- Accessibility capture remains app-dependent outside the user's current successful scenario
- P2S ACK is not Windows-applied
- old-peer P2P fallback is not Windows-applied
- multiple-peer ACK semantics are not explicit
- no empirical comparison against OTP Helper has been completed; the implementation now has broader designed coverage and stronger staged diagnostics, but superiority must be demonstrated by a shared corpus/device matrix

## Pull-request state

Keep PR #1 Draft until mandatory HONOR 400 Pro tests and upstream regressions pass. Do not tag or publish a release merely because CI is green.
