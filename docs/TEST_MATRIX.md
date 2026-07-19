# Validation and Regression Matrix

Record every run with date, commit SHA, Android build/package version, Windows build version, transport mode, locale, and result. Do not mark the project complete from CI alone.

## Evidence correction and authority

Earlier checked Android outbound and Yahoo! JAPAN SMS success rows were contaminated by Microsoft Phone Link clipboard synchronization and are invalid as ClipCascade evidence.

For Priority 1 Android outbound, the authoritative live matrix is `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`. It supersedes every older Android outbound success item. This file remains the broad installation, feature, transport, Windows, and release regression checklist.

## Current green build identity

- implementation anchor: `f86705c513c56a9fd24e218f8513dad9cead2ed8`
- Android version: `3.2.1-extended.16-standalone`
- versionCode: `320120`
- package: `com.clipcascade.extended`
- Android CI: `29675438972` — success
- Windows CI: `29675438978` — success
- Android artifact ID: `8438725636`
- APK signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

CI is build evidence only. No HONOR target-device row below is passed unless explicitly recorded in the authoritative live matrix.

## A. Installation, identity, update, and localization

- [ ] Install APK without Metro, USB, or developer server
- [ ] Launch after device reboot
- [ ] Confirm app name/package/version/versionCode
- [ ] Confirm installed certificate matches the recorded signer
- [ ] Install in place over prior stable-signed build
- [ ] Confirm app data, login, Accessibility, notification access, battery/background, and boot-resume settings survive update
- [ ] Japanese and English UI localization
- [ ] Accessibility and notification-listener localization
- [ ] No obsolete READ_LOGS, overlay, ADB, root, or Shizuku requirement

## B. Guided Android setup

- [ ] Setup remains reachable before login and while sync runs
- [ ] Boot-resume control persists
- [ ] Notification, Accessibility, notification access, and battery steps work
- [ ] HONOR/MagicOS auto-launch/secondary/background guidance works
- [ ] Relay switches and source-app picker persist
- [ ] Clipboard manual test handles queued, duplicate, and queue-full states correctly

## C. ADB-free ordinary Copy

Test Chrome, Firefox-family, Gmail, Outlook, SMS app, LINE, Discord, notes/editor, WebView, and a non-secret password-manager field.

- [ ] Selection without Copy sends nothing
- [ ] One real Copy creates one relay and one Windows application
- [ ] Repeated identical Copy is not permanently suppressed
- [ ] Rapid different values preserve order
- [ ] No-text cases are diagnosed honestly
- [ ] Large text respects limits

See `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md` for UI-language, screen-state, long-disconnect, queue-full, exactly-once, and ACK-deletion rows.

## D. Verification-code extraction

- [ ] Japanese, English, Chinese, Korean, Spanish, French, and German auth contexts
- [ ] Numeric and compact alphanumeric values
- [ ] WebOTP/domain-bound SMS and SMS Retriever
- [ ] Beeper/Perceptron email layouts
- [ ] Standard, BigText, text-lines, MessagingStyle, and multi-line email notifications
- [ ] Reject amount/date/year/phone/tracking/order/coupon and ordinary-number false positives
- [ ] Source filter and privacy behavior

## E. Synthetic OTP end-to-end

- [ ] Extractor accepts synthetic notification text
- [ ] Persistent OTP queue receives value
- [ ] Windows applies exact synthetic value
- [ ] Queue removes only after defined ACK
- [ ] Offline queue retains and later drains once
- [ ] P2S is not mislabeled as Windows-applied ACK

Synthetic test does not prove third-party NotificationListener extras exposure.

## F. HONOR / screen-off / lock

All unproven until recorded in the authoritative matrix:

- [ ] foreground and background
- [ ] removed from recents
- [ ] locked
- [ ] screen off 1, 15, and 30+ minutes
- [ ] MagicOS battery default and relaxed settings
- [ ] process death and reboot recovery
- [ ] real SMS/email/Beeper/Perceptron classification

## G. Network and queue durability

- [ ] Windows offline then returns
- [ ] Android offline then network returns
- [ ] Wi-Fi/mobile-data transitions
- [ ] server and P2P peer restart/reconnect
- [ ] accepted ordinary item survives more than ten and thirty minutes without ACK
- [ ] 16 accepted items remain intact when item 17 is rejected as `queue_full`
- [ ] process death/reboot with pending clipboard or OTP item
- [ ] duplicate suppression and in-flight retry
- [ ] no accepted item deletion before defined ACK

## H. P2S and P2P

P2S:

- [ ] connected status accurate
- [ ] send failure retains item
- [ ] reconnect drains once
- [ ] acknowledgement wording means local transport acceptance

P2P:

- [ ] zero DataChannels retains item
- [ ] Extended peer drains after Windows clipboard application
- [ ] multiple-peer behavior defined
- [ ] old peer compatibility fallback bounded
- [ ] no overlapping WebRTC sessions/watchdogs

## I. Upstream regression

- [ ] Android → Windows text
- [ ] Windows → Android text
- [ ] image and file sharing
- [ ] download directory
- [ ] cipher on/off
- [ ] login/session, boot, and foreground notification

## J. Windows authentication, UI, recovery, and shutdown

- [ ] authenticated endpoint and login-page detection
- [ ] safe diagnostics without secrets/content
- [ ] status/tray/recovery controls
- [ ] sleep/network/DataChannel recovery
- [ ] clean Quit, no lingering EXE/mutex, immediate relaunch
- [ ] no tray ghost accumulation

## K. Release gate

- [x] Android implementation-anchor CI passed
- [x] Windows implementation-anchor CI passed
- [x] APK artifact, hashes, expiry, and signer recorded
- [ ] Mandatory HONOR matrix completed
- [ ] In-place stable-signed update proven
- [ ] Windows Quit/tray proven on real EXE
- [ ] Upstream regression completed
- [ ] Production signing selected/protected
- [ ] Tested commit tagged and release artifacts verified
- [ ] Release notes state limitations
- [ ] PR #1 remains Draft until mandatory device tests pass
