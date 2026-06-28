# Validation and Regression Matrix

Record every run with date, commit SHA, Android build/package version, Windows build version, transport mode, locale, and result. Do not mark the project complete from CI alone.

## A. Installation, identity, and localization

- [ ] Install APK without Metro, USB, or developer server
- [ ] Launch after device reboot
- [ ] Confirm app name is `ClipCascade Extended`
- [ ] Confirm package is `com.clipcascade.extended`
- [ ] Confirm no personal handle appears in app UI, package identity, diagnostics, or artifacts
- [ ] Confirm older official/upstream ClipCascade can coexist if desired
- [ ] Japanese system locale shows Japanese Extended settings and primary React UI labels
- [ ] English system locale shows English Extended settings and primary React UI labels
- [ ] Accessibility service and notification-listener labels/descriptions follow the system locale
- [ ] Distributed UI contains no obsolete READ_LOGS, overlay, or ADB setup command

## B. Guided Android setup

Start from a fresh install or reset permissions between runs.

- [ ] Persistent Sharing setup button is visible before login
- [ ] Persistent Sharing setup button is visible while sync is running
- [ ] Guided checklist displays notification permission state
- [ ] Continue requests Android 13+ notification permission when missing
- [ ] Continue opens Accessibility settings when clipboard service is missing
- [ ] Accessibility state updates after returning to the app
- [ ] Continue opens Notification access settings when listener access is missing
- [ ] Notification-access state updates after returning to the app
- [ ] Continue opens battery exemption flow when optimization remains enabled
- [ ] Battery state updates after returning to the app
- [ ] Background settings guidance explicitly covers HONOR/MagicOS auto-launch, secondary launch, and background execution
- [ ] Manual background confirmation persists after process restart
- [ ] Completed checklist reports all five steps complete
- [ ] Clipboard relay switch persists after process restart
- [ ] Verification-code relay switch persists after process restart
- [ ] Source-app picker saves and reloads selections
- [ ] Reset-to-all clears the source filter
- [ ] Clipboard test relay reaches Windows

## C. ADB-free text copy compatibility

For each app, test short text, multi-line text, Japanese text, URL, and repeated identical copy.

- [ ] Chrome
- [ ] Firefox/Floorp-compatible Android browser if installed
- [ ] Gmail
- [ ] Outlook
- [ ] Google Messages or target SMS app
- [ ] LINE
- [ ] Discord
- [ ] Notes/editor app
- [ ] Password manager non-secret test field
- [ ] WebView-based app

For each:

- [ ] One copy produces one Windows clipboard update
- [ ] No copy event means no relay
- [ ] Repeated identical copies are not permanently suppressed
- [ ] Different text copied quickly preserves order
- [ ] Source app returning no selection text is handled honestly
- [ ] Large text respects configured/local limits
- [ ] No old READ_LOGS or overlay permission is needed

## D. Verification-code extraction

Use synthetic values only. Never commit real codes.

Positive Japanese examples:

- [ ] `認証コードは 123456 です`
- [ ] `本人確認番号：482901`
- [ ] `2段階認証コード 7314`
- [ ] Full-width/grouped one-time password
- [ ] Notification containing both transaction amount and a separate one-time password

Positive English examples:

- [ ] `Your verification code is A1B2C3`
- [ ] Code before `sign-in code`
- [ ] `Use ... to verify your email address`
- [ ] Prefixed code such as `G-123456`
- [ ] Notification containing both a transaction amount and a separate security code

Notification layouts:

- [ ] Standard title + text
- [ ] BigTextStyle
- [ ] `EXTRA_TEXT_LINES`
- [ ] MessagingStyle current messages
- [ ] MessagingStyle historic messages
- [ ] Multi-line email notification

Negative examples:

- [ ] Price/amount notification without an actual code
- [ ] Date/time notification
- [ ] Year near generic verification-service text
- [ ] Phone number
- [ ] Delivery/tracking identifier
- [ ] Email address containing digits
- [ ] Ordinary message containing a six-digit number without verification context
- [ ] ClipCascade foreground notification

Filter behavior:

- [ ] Empty filter processes all context-matching apps
- [ ] Selected filter processes only selected packages
- [ ] Synthetic ClipCascade test notification bypasses source-app filter only because it is explicitly marked as synthetic
- [ ] Turning relay off stops processing immediately
- [ ] Full notification text never appears in Windows clipboard or normal logs

## E. Synthetic end-to-end verification test

Run first in Extended P2P with Windows connected.

- [ ] Test is blocked with a clear action when notification permission is missing
- [ ] Test is blocked with a clear action when notification access is missing
- [ ] Button posts a visible localized notification with a fresh synthetic code
- [ ] Settings status changes from posted to detected
- [ ] Settings status changes from detected to queued
- [ ] Windows clipboard becomes exactly the synthetic code
- [ ] Settings status changes to acknowledged after Windows clipboard application in Extended P2P
- [ ] Queue item is removed only after the existing ACK path
- [ ] Source-app filter does not suppress the explicitly marked synthetic test
- [ ] Ordinary ClipCascade foreground notifications remain ignored
- [ ] Test status and displayed synthetic value expire after five minutes
- [ ] Repeated test produces a new code and does not become permanently deduplicated
- [ ] Windows offline leaves the test queued; Windows return drains it
- [ ] P2S result is not described as Windows-applied acknowledgement

## F. Screen-off / lock / MagicOS

On HONOR 400 Pro / Android 16, run both real SMS and real email cases:

- [ ] Screen on, app foreground
- [ ] Screen on, app background
- [ ] Screen off for 1 minute
- [ ] Screen off for 15 minutes
- [ ] Screen off for 30+ minutes
- [ ] Device locked
- [ ] MagicOS battery optimization default
- [ ] MagicOS battery optimization manually relaxed
- [ ] Auto-launch / secondary launch / background execution enabled
- [ ] App removed from recents
- [ ] App process killed by system
- [ ] Device rebooted
- [ ] Reboot with the immediate Headless JS path blocked/killed; delayed WorkManager heartbeat restores sync

For every SMS/email case, record whether notification text is visible to NotificationListenerService or redacted by Android, whether extraction occurred, whether the item queued, and whether Windows acknowledged it.

## G. Network and queue durability

- [ ] Android online, Windows online
- [ ] Android online, Windows offline, then Windows returns
- [ ] Android offline, copy, then network returns
- [ ] Wi-Fi to mobile-data transition
- [ ] Mobile-data to Wi-Fi transition
- [ ] Late `onLost` for the old default network does not cancel recovery for the replacement network
- [ ] `Disconnected` is never treated as an actual connected state by recovery, clipboard dispatch, or verification-code dispatch
- [ ] Server restart
- [ ] P2P peer disconnect/reconnect
- [ ] Process death with pending clipboard item
- [ ] Process death with pending verification code
- [ ] Device reboot with pending item
- [ ] Queue TTL expiry
- [ ] Duplicate suppression window
- [ ] In-flight timeout and retry
- [ ] No item deletion before defined ACK

## H. P2S and P2P

Run all relevant relay tests in both modes.

P2S:

- [ ] WebSocket connected status accurate
- [ ] Send failure retains queue item
- [ ] Reconnect drains queue once
- [ ] UI/documentation identifies acknowledgement as local transport acceptance

P2P:

- [ ] Signaling connected but zero DataChannels does not delete queued item
- [ ] One live Extended peer drains queue once after Windows clipboard application
- [ ] Multiple peers have defined behavior
- [ ] Old/non-Extended peer uses documented compatibility fallback
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

## J. Windows authentication, UI, and recovery

Authentication/API:

- [ ] Successful login preserves every session/proxy cookie required by authenticated endpoints
- [ ] HTTP 200 that returns the login form is rejected as an authentication failure
- [ ] `/csrf-token` empty/non-JSON response is logged safely and remains non-fatal
- [ ] `/server-mode` returns valid `P2S` or `P2P` JSON before transport selection
- [ ] Empty `/server-mode` response returns to login without terminating the application
- [ ] HTML/login redirect from `/server-mode` returns to login without terminating the application
- [ ] Connection/timeout failure from an authenticated endpoint returns to login without an unexpected application crash
- [ ] Diagnostic log records endpoint/status/content type/body byte count/final path/redirect codes only
- [ ] Diagnostic log contains no response body, cookie value, credential, or private server URL

UI and recovery:

- [ ] Visible status window after login
- [ ] Tray Open Status
- [ ] Restart Sync
- [ ] Reconnect
- [ ] Disconnect
- [ ] Open Logs
- [ ] Copy Diagnostics
- [ ] Second launch focuses existing window
- [ ] Sleep/resume recovery
- [ ] Network change recovery
- [ ] P2P DataChannel-dead watchdog recovery
- [ ] Log rotation
- [ ] No false healthy status

## K. Release gate

- [ ] Android CI passes on release commit
- [ ] Windows CI passes on release commit
- [ ] Bilingual/no-ADB bundle assertions pass
- [ ] APK hash recorded
- [ ] Windows archive hash recorded
- [ ] Tested commit tagged `extended-v*`
- [ ] GitHub Release contains only tested artifacts
- [ ] Release notes state known limitations
- [ ] Draft PR remains draft until all mandatory target-device tests pass
