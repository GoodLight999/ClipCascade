# Validation and Regression Matrix

Record every run with date, commit SHA, Android build/package version, Windows build version, transport mode, locale, and result. Do not mark the project complete from CI alone.

## Evidence correction and authority

Earlier checked Android outbound and Yahoo! JAPAN SMS success rows were contaminated by Microsoft Phone Link clipboard synchronization and are invalid as ClipCascade evidence.

For Priority 1 Android outbound, the authoritative live matrix is:

- `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`

That matrix supersedes every older Android outbound success item. This file remains the broad installation, feature, transport, Windows, and release regression checklist.

## Current green build identity

- implementation anchor: `1e3aae70e2052420bfbcf2e326e04638787dfc1c`
- Android version: `3.2.1-extended.15-standalone`
- versionCode: `320119`
- package: `com.clipcascade.extended`
- Android CI: `29674843116` — success
- Windows CI: `29674843145` — success
- Android artifact ID: `8438520128`
- APK signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

CI is build evidence only. No HONOR target-device row below is passed unless explicitly recorded in the authoritative live matrix.

## A. Installation, identity, update, and localization

- [ ] Install APK without Metro, USB, or developer server
- [ ] Launch after device reboot
- [ ] Confirm app name is `ClipCascade Extended`
- [ ] Confirm package is `com.clipcascade.extended`
- [ ] Confirm version is `3.2.1-extended.15-standalone`, versionCode `320119`
- [ ] Confirm installed certificate matches the recorded signer SHA-256
- [ ] Install in place over the prior stable-signed build
- [ ] Confirm app data, login, Accessibility, notification access, battery/background, and boot-resume settings survive update
- [ ] Japanese system locale shows Japanese Extended UI
- [ ] English system locale shows English Extended UI
- [ ] Accessibility and notification-listener labels follow system locale
- [ ] Distributed UI contains no obsolete READ_LOGS, overlay, or ADB setup command

## B. Guided Android setup

- [ ] Persistent Sharing setup remains reachable before login and while sync is running
- [ ] Bottom bar does not cover application content
- [ ] Boot-resume control persists after process restart
- [ ] Guided checklist displays notification permission state
- [ ] Checklist opens Accessibility settings when needed
- [ ] Checklist opens notification access settings when needed
- [ ] Checklist opens battery exemption flow when needed
- [ ] HONOR/MagicOS guidance covers auto-launch, secondary launch, and background execution
- [ ] Manual manufacturer-setting confirmation persists
- [ ] Clipboard relay switch persists
- [ ] Verification-code relay switch persists
- [ ] Source-app picker saves/reloads and reset-to-all works
- [ ] Clipboard test relay reaches connected peers

## C. ADB-free text copy compatibility

Test short text, multi-line text, Japanese text, URL, repeated identical Copy, and rapid different values in:

- [ ] Chrome
- [ ] Firefox-family browser
- [ ] Gmail
- [ ] Outlook
- [ ] target SMS app
- [ ] LINE
- [ ] Discord
- [ ] notes/editor app
- [ ] password-manager non-secret test field
- [ ] WebView-based app

For each:

- [ ] Selection without Copy produces zero queue items and zero Windows updates
- [ ] One real Copy produces one native item and one Windows update
- [ ] Repeated identical Copy is not permanently suppressed
- [ ] Rapid different values preserve order
- [ ] Source app returning no selected text is diagnosed honestly
- [ ] Large text respects configured/local limits
- [ ] No ADB, root, Shizuku, READ_LOGS, or overlay permission is required

See `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md` for UI-language, screen-state, long-disconnect, exactly-once, and ACK-deletion rows.

## D. Verification-code extraction

Use synthetic values only in committed tests. Never commit real codes.

Positive families:

- [ ] Japanese verification/security/login phrases
- [ ] English OTP/verification/security/login/sign-in phrases
- [ ] Chinese and Korean common verification labels
- [ ] Spanish/French/German samples in unit corpus
- [ ] Full-width/grouped values
- [ ] Numeric and compact alphanumeric values
- [ ] Code before label, label before code, and standalone code line
- [ ] WebOTP/domain-bound SMS
- [ ] SMS Retriever message with app hash
- [ ] Beeper-style and Perceptron-style email layout

Notification layouts:

- [ ] Standard title + text
- [ ] BigTextStyle
- [ ] `EXTRA_TEXT_LINES`
- [ ] MessagingStyle current messages
- [ ] MessagingStyle historic messages
- [ ] Multi-line email notification

Negative families:

- [ ] Price/amount
- [ ] Date/time/year
- [ ] Phone number
- [ ] delivery/tracking/order/booking/ticket/invoice identifiers
- [ ] email address digits
- [ ] coupon/promo/discount/voucher/referral values without strong auth context
- [ ] ordinary message containing a number without verification context
- [ ] ClipCascade foreground notification

Privacy/filter behavior:

- [ ] Empty filter processes all context-matching apps
- [ ] Selected filter processes only selected packages
- [ ] Changing source policy clears pending values as documented
- [ ] Turning relay off stops processing and clears pending values
- [ ] Full notification text never appears in Windows clipboard or diagnostics
- [ ] Only extracted value, timestamp, and opaque relay ID are persisted

## E. Synthetic end-to-end verification test

Run first in Extended P2P with Windows connected.

- [ ] Test presents a fresh synthetic value
- [ ] Extractor accepts the generated notification text
- [ ] Value enters the persistent OTP queue
- [ ] Windows clipboard becomes exactly the synthetic value
- [ ] Status becomes acknowledged after Windows clipboard application
- [ ] Queue item is removed only after the defined ACK path
- [ ] Repeated test creates a fresh value
- [ ] Windows offline retains the item and return drains it once
- [ ] P2S result is not described as Windows-applied acknowledgement

This deterministic test does not prove third-party NotificationListener extras exposure.

## F. Screen-off / lock / MagicOS

All rows are unproven until recorded in `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md` with Phone Link and every competing synchronizer disabled.

- [ ] Screen on, app foreground
- [ ] Screen on, app background
- [ ] Removed from recents
- [ ] Device locked
- [ ] Screen off 1 minute
- [ ] Screen off 15 minutes
- [ ] Screen off 30+ minutes
- [ ] MagicOS battery optimization default
- [ ] MagicOS battery optimization relaxed
- [ ] Auto-launch / secondary launch / background execution enabled
- [ ] App process killed by system
- [ ] Device rebooted
- [ ] WorkManager recovery after immediate Headless JS path is blocked/killed
- [ ] Real SMS notification classification
- [ ] Real email/Perceptron/Beeper notification classification

## G. Network and queue durability

- [ ] Android online, Windows online
- [ ] Windows offline then returns
- [ ] Android offline then network returns
- [ ] Wi-Fi/mobile-data transitions
- [ ] Late `onLost` for old network does not cancel replacement-network recovery
- [ ] `Disconnected` is never treated as connected
- [ ] Server restart
- [ ] P2P peer disconnect/reconnect
- [ ] Ordinary clipboard item survives more than ten minutes without ACK
- [ ] Ordinary clipboard item survives more than thirty minutes without ACK
- [ ] Process death with pending clipboard item
- [ ] Process death with pending OTP item
- [ ] Device reboot with pending item
- [ ] Queue bound behavior at more than 16 ordinary items
- [ ] Duplicate-suppression window
- [ ] In-flight timeout and retry
- [ ] No item deletion before defined acknowledgement

## H. P2S and P2P

P2S:

- [ ] WebSocket connected status is accurate
- [ ] Send failure retains queue item
- [ ] Reconnect drains queue once
- [ ] UI/documentation identifies acknowledgement as local transport acceptance

P2P:

- [ ] Signaling connected with zero DataChannels retains queue item
- [ ] One live Extended peer drains once after Windows clipboard application
- [ ] Multiple-peer behavior is defined
- [ ] Old/non-Extended peer uses bounded compatibility fallback
- [ ] Old WebRTC session is fully torn down before restart
- [ ] Watchdog does not create overlapping sessions

## I. Upstream regression

- [ ] Manual text sharing Android → Windows
- [ ] Text sharing Windows → Android
- [ ] Image sharing
- [ ] Single file sharing
- [ ] Multiple file sharing
- [ ] File download directory selection
- [ ] Cipher enabled
- [ ] Cipher disabled where supported
- [ ] Saved login/session behavior
- [ ] Relaunch on boot
- [ ] Foreground service notification

## J. Windows authentication, UI, recovery, and shutdown

Authentication/API:

- [ ] Authenticated endpoints preserve required session/proxy cookies
- [ ] HTTP 200 login form is rejected as authentication failure
- [ ] Empty/non-JSON CSRF response is non-fatal and safely logged
- [ ] `/server-mode` validates `P2S` or `P2P`
- [ ] Empty/HTML/redirect/error responses return to login without crash
- [ ] Diagnostics contain no response body, cookies, credentials, private server URL, or clipboard content

UI/recovery:

- [ ] Visible status window after login
- [ ] Tray Open Status
- [ ] Restart Sync / Reconnect / Disconnect
- [ ] Open Logs / Copy Diagnostics
- [ ] Recovery state wording is accurate
- [ ] Second launch focuses existing window
- [ ] Sleep/resume and network-change recovery
- [ ] P2P DataChannel-dead watchdog recovery
- [ ] Log rotation
- [ ] No false healthy status

Shutdown/tray:

- [ ] Tray Quit closes status UI and removes live icon
- [ ] Hidden root, watchdog, transport, and asyncio loop stop
- [ ] No EXE remains in Task Manager
- [ ] Immediate relaunch succeeds
- [ ] Repeated Quit signaling does not race or hang
- [ ] Reconnect/restart cycles do not accumulate ghost icons

## K. Release gate

- [x] Android implementation-anchor CI passed
- [x] Windows implementation-anchor CI passed
- [x] APK artifact, direct hash, expiry, and signer recorded
- [ ] Mandatory HONOR 400 Pro matrix completed
- [ ] In-place stable-signed update proven
- [ ] Windows Quit/tray behavior proven on real EXE
- [ ] Upstream regression matrix completed
- [ ] Private production release signing selected and protected
- [ ] Tested commit tagged `extended-v*`
- [ ] GitHub Release contains only tested artifacts
- [ ] Release notes state known limitations
- [ ] Draft PR remains Draft until all mandatory target-device tests pass
