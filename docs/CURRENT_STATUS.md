# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Canonical requirements: `docs/REQUIREMENTS.md`

## Current high-level state

The branch contains substantial Android and Windows work. The latest code-bearing head documented here is `ed42333ec46cf236c52e33f14b13bc36cf555479`. Android and Windows CI both passed for that head. This proves the code compiles, the standalone artifacts build, and the APK contains its JavaScript bundle. It does **not** prove the product requirements are complete.

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
- Keeps an item queued while waiting for JavaScript transport acknowledgement.
- Retries an in-flight item after a 15-second acknowledgement timeout.

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
- Uses TTL, deduplication, transport readiness checks, retry, and in-flight acknowledgement timeout.

Important limitations:

- Not tested on Android 16 / MagicOS.
- Android may redact OTP content from notifications.
- Current acknowledgement proves local P2S publish or P2P DataChannel acceptance, not Windows clipboard application.

### Transport acknowledgement

Implemented incremental state machine:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED -> NATIVE_ACK -> DELETE`

Files and build-time patches:

- `ClipboardRelayDispatcher.kt`
- `OtpRelayDispatcher.kt`
- `RelaySettingsModule.kt`
- `scripts/prepare_relay_transport_ack.js`
- `scripts/prepare_relay_startup_order.js`

Behavior:

- Native dispatchers no longer delete an item merely after `DeviceEventEmitter.emit`.
- Each item carries `relayId` and source metadata.
- P2S returns success only after an active STOMP client accepts `publish`.
- P2P returns success only when at least one open DataChannel accepts every fragment.
- JavaScript calls `acknowledgeRelay()` only after that accepted-send result.
- Native queues keep the item if no acknowledgement arrives within 15 seconds.
- Persistent queues are resumed only after the `SHARED_TEXT` listener is registered.
- Manual sharing remains on the upstream path; background relay events avoid writing the same text back to Android clipboard.

Remaining delivery limitation:

There is still no end-to-end peer acknowledgement. Required future direction:

`LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_RECEIVED -> WINDOWS_CLIPBOARD_APPLIED -> PEER_ACK -> DELETE`

The current implementation is materially safer than delete-on-emit, but it cannot prove that Windows applied the clipboard.

### Android recovery groundwork

- `HeadlessTask.js` handles boot and health-failure events.
- `HeadlessTaskService.kt` allows up to 30 seconds for recovery work.
- `BootReceiver.kt` acquires the React Native Headless JS wake lock after boot.
- `ScheduleService.kt` performs heartbeat checks independently of notification permission.
- Clipboard helper activity is isolated in its own task.
- Persistent queues survive ordinary React Native lifecycle loss.
- Foreground-service startup resumes both persistent relay queues after listeners are installed.

Important limitations:

- `ScheduleService` currently warns/notifies when heartbeat fails; it does not itself dispatch the health-failure headless event.
- The attempted automatic heartbeat-to-Headless recovery write was not applied.
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

## Build status

Latest known successful Android workflow for the current code-bearing head:

- Extended UI preparation: success
- Transport-ack patch application: success
- Relay startup-order patch application: success
- JavaScript/Hermes bundle: success
- Gradle standalone APK: success
- Embedded bundle verification: success
- Build-log artifact upload: success
- APK artifact upload: success

Latest known successful Windows workflow:

- Python source compilation: success
- Dependency installation: success
- PyInstaller standalone executable: success
- Executable artifact upload: success

## Not yet complete

- Universal/representative ADB-free clipboard operation
- Screen-off verification-code operation on target hardware
- Android 16 OTP redaction resolution
- End-to-end Windows clipboard-applied acknowledgement
- Automatic health-check-triggered restart
- Target-device test matrix
- Upstream feature regression tests
- Tagged release containing tested artifacts

## Do not claim

Do not describe the branch as complete, production-ready, reliably screen-off, universally compatible, or end-to-end acknowledged until the Definition of Done in `docs/REQUIREMENTS.md` is satisfied.
