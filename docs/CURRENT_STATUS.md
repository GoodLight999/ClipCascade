# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Canonical requirements: `docs/REQUIREMENTS.md`

## Current high-level state

The branch contains substantial Android and Windows work and both CI workflows have passed on commit `710a81d27b8a27f3015cf3859335eec6b93887df`. This proves the code compiles and the APK contains its JavaScript bundle. It does **not** prove the product requirements are complete.

## Android — implemented

### Standalone packaging

- Neutral app name: ClipCascade Extended
- Test package: `com.clipcascade.extended`
- Version family: `3.2.1-extended.*`
- ARM64 target for the HONOR device
- JavaScript bundle generated before Gradle build
- CI verifies `assets/index.android.bundle` exists inside the APK
- Developer support is disabled so the distributed APK does not require Metro

### Settings UI

`RelaySettingsActivity.kt` contains:

- Clipboard relay switch
- AccessibilityService state display
- Open Accessibility Settings button
- Verification-code relay switch
- Notification-access state display
- Open Notification Listener Settings button
- Notification source app picker
- Reset to all apps
- Test relay button

`AppRoot.js` adds a persistent `⚙ 共有設定` entry point over the React Native UI.

### Accessibility clipboard prototype

Files:

- `ClipboardAccessibilityService.kt`
- `ClipboardRelayStore.kt`
- `ClipboardRelayDispatcher.kt`
- `RelaySettingsStore.kt`
- `res/xml/clipboard_accessibility_service.xml`

Behavior:

- Observes selection, clicks, announcements, notification-state events, and Ctrl+C.
- Looks for copy-related markers in Japanese and English.
- Attempts to read `ClipboardManager` after likely copy events.
- Falls back to the most recent selected text when clipboard access is denied.
- Stores text in a bounded persistent queue with TTL and duplicate suppression.
- Retries while the local transport is unavailable.

Important limitation:

This is an accessibility-event heuristic, not a proven universal Android clipboard monitor. It has not been validated on the target device/app matrix.

### Notification verification-code prototype

Files:

- `NotificationCodeListenerService.kt`
- `OtpCodeExtractor.kt`
- `OtpRelayStore.kt`
- `OtpRelayDispatcher.kt`

Behavior:

- Uses user-authorized NotificationListenerService.
- Ignores ClipCascade's own notifications, ongoing events, and foreground-service notifications.
- Applies optional source-package filtering.
- Extracts a short code only when verification context is present.
- Queues only the extracted value; notification text is not passed into the relay queue.
- Uses TTL, deduplication, transport readiness checks, and retry.

Important limitations:

- Not tested on Android 16 / MagicOS.
- Android may redact OTP content from notifications.
- Queue deletion currently occurs after local React event acceptance, not Windows receipt.

### Android recovery groundwork

- `HeadlessTask.js` handles boot and health-failure events.
- `ScheduleService.kt` performs heartbeat checks independently of notification permission.
- Clipboard helper activity is isolated in its own task.
- Persistent queues survive ordinary React Native lifecycle loss.

Important limitations:

- `ScheduleService` currently warns/notifies when heartbeat fails; it does not itself dispatch the health-failure headless event.
- Queue dispatchers are not explicitly restarted from every boot/service recovery path.
- Real process-death, reboot, MagicOS kill, and connectivity recovery tests are outstanding.

## Windows — implemented

Files:

- `core/windows_application.py`
- `gui/enhanced_tray.py`
- `gui/live_status_dialog.py`
- `gui/status_dialog.py`
- modified `main.py`

Behavior:

- Visible status/control window after login.
- Connection, server, mode, peer/transfer, reconnect, and operation status.
- Restart Sync, Reconnect, Disconnect, Open Logs, Copy Diagnostics.
- Tray controls use real manager state.
- Second launch requests the existing process to show its status window.
- P2P restart waits for teardown before replacement connection.
- Watchdog handles persistent offline state and P2P signaling-alive/DataChannel-dead state.
- Rotating logs.
- PyInstaller CI build.

Important limitations:

- Windows runtime has not been tested against the current Android build in this thread.
- The show-window marker still follows the upstream program-directory storage pattern.
- Existing image/file/P2S/P2P regression matrix has not been run.

## Delivery semantics — current flaw

Both native dispatchers currently remove queued items after emitting `SHARED_TEXT` to React Native while the local transport reports connected.

Current effective state machine:

`QUEUED -> REACT_EVENT_EMITTED -> DELETE`

Required direction:

`QUEUED -> JS_ACCEPTED -> NETWORK_SEND_ACCEPTED -> PEER_RECEIVED/CLIPBOARD_APPLIED -> ACK -> DELETE`

At minimum, the next implementation must add a JS-to-native acknowledgement only after the P2S WebSocket send or P2P DataChannel send path reports that it actually accepted the item. End-to-end peer acknowledgement should follow if protocol changes are feasible.

## Build status

Latest known successful Android workflow:

- UI preparation: success
- JS bundle: success
- Gradle standalone APK: success
- Embedded bundle verification: success
- APK artifact upload: success

Latest known successful Windows workflow:

- Python/Windows packaging CI: success

## Not yet complete

- Universal/representative ADB-free clipboard operation
- Screen-off verification-code operation on target hardware
- Android 16 OTP redaction resolution
- Transport acknowledgement
- Automatic queue restart on every recovery path
- Automatic health-check-triggered restart
- Target-device test matrix
- Upstream feature regression tests
- Tagged release containing tested artifacts

## Do not claim

Do not describe the branch as complete, production-ready, reliably screen-off, universally compatible, or fully acknowledged until the Definition of Done in `docs/REQUIREMENTS.md` is satisfied.
