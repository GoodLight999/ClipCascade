# Validation and Regression Matrix

Record every run with date, commit SHA, Android build/package version, Windows build version, transport mode, and result. Do not mark the project complete from CI alone.

## A. Installation and identity

- [ ] Install APK without Metro, USB, or developer server
- [ ] Launch after device reboot
- [ ] Confirm app name is `ClipCascade Extended`
- [ ] Confirm package is `com.clipcascade.extended`
- [ ] Confirm no personal handle appears in app UI, package identity, diagnostics, or artifacts
- [ ] Confirm older official/upstream ClipCascade can coexist if desired

## B. Settings UX

- [ ] `⚙ 共有設定` is visible before login
- [ ] `⚙ 共有設定` is visible while sync is running
- [ ] Clipboard relay switch persists after process restart
- [ ] Verification-code relay switch persists after process restart
- [ ] Accessibility status updates after returning from Android settings
- [ ] Notification access status updates after returning from Android settings
- [ ] Source-app picker saves and reloads selections
- [ ] Reset-to-all clears the source filter
- [ ] Test relay action reaches Windows

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

## D. Verification-code extraction

Use synthetic test notifications where possible. Never commit real codes.

Positive examples:

- [ ] `認証コードは 123456 です`
- [ ] `確認コード: 9876`
- [ ] `Your verification code is A1B2C3`
- [ ] Multi-line notification with title and big text

Negative examples:

- [ ] Price/amount notification
- [ ] Date/time notification
- [ ] Phone number
- [ ] Delivery tracking number
- [ ] Ordinary message containing a six-digit number without verification context
- [ ] ClipCascade foreground notification

Filter behavior:

- [ ] Empty filter processes all context-matching apps
- [ ] Selected filter processes only selected packages
- [ ] Turning relay off stops processing immediately
- [ ] Full notification text never appears in Windows clipboard or normal logs

## E. Screen-off / lock / MagicOS

On HONOR 400 Pro / Android 16:

- [ ] Screen on, app foreground
- [ ] Screen on, app background
- [ ] Screen off for 1 minute
- [ ] Screen off for 15 minutes
- [ ] Screen off for 30+ minutes
- [ ] Device locked
- [ ] MagicOS battery optimization default
- [ ] MagicOS battery optimization manually relaxed
- [ ] App removed from recents
- [ ] App process killed by system
- [ ] Device rebooted
- [ ] Reboot with the immediate Headless JS path blocked/killed; delayed WorkManager heartbeat restores sync

For SMS and email sources, record whether notification text is visible to NotificationListenerService or redacted by Android.

## F. Network and queue durability

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

## G. P2S and P2P

Run all relevant relay tests in both modes.

P2S:

- [ ] WebSocket connected status accurate
- [ ] Send failure retains queue item
- [ ] Reconnect drains queue once

P2P:

- [ ] Signaling connected but zero DataChannels does not delete queued item
- [ ] One live peer drains queue once
- [ ] Multiple peers have defined behavior
- [ ] Old WebRTC session is fully torn down before restart
- [ ] Watchdog does not create overlapping sessions

## H. Upstream regression

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

## I. Windows authentication, UI, and recovery

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

## J. Release gate

- [ ] Android CI passes on release commit
- [ ] Windows CI passes on release commit
- [ ] APK hash recorded
- [ ] Windows archive hash recorded
- [ ] Tested commit tagged `extended-v*`
- [ ] GitHub Release contains only tested artifacts
- [ ] Release notes state known limitations
- [ ] Draft PR remains draft until all mandatory target-device tests pass
