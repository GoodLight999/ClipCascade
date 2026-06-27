# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Canonical requirements: `docs/REQUIREMENTS.md`

## Current high-level state

The branch contains substantial Android and Windows work. Android CI run `28291402373` and Windows CI run `28291402372` passed for the P2P peer-acknowledgement implementation. This proves the transformation scripts, JavaScript bundle, Android unit tests, Kotlin/Java compilation, APK packaging, Python compilation, PyInstaller build, and artifact uploads succeed. It does **not** prove target-device compatibility or screen-off reliability.

## Android — implemented

### Standalone packaging

- Neutral app name: ClipCascade Extended
- Test package: `com.clipcascade.extended`
- Version family: `3.2.1-extended.*`
- ARM64 target for the HONOR device
- JavaScript bundle generated before Gradle build
- CI verifies `assets/index.android.bundle` exists inside the APK
- Developer support is disabled; the distributed APK does not require Metro

### Settings UI

`RelaySettingsActivity.kt` contains:

- Clipboard relay switch
- AccessibilityService state display and settings shortcut
- Verification-code relay switch
- Notification-access state display and settings shortcut
- Notification-source app picker and reset-to-all action
- Pending clipboard/verification queue counts
- Clear-pending-data action
- Test relay action
- Content-free recent health diagnostics and clear-diagnostics action

`AppRoot.js` adds a persistent `⚙ 共有設定` entry point over the React Native UI.

### Accessibility clipboard relay

Files:

- `ClipboardAccessibilityService.kt`
- `ClipboardRelayStore.kt`
- `ClipboardRelayDispatcher.kt`
- `RelaySettingsStore.kt`
- `RelayHealthStore.kt`
- `res/xml/clipboard_accessibility_service.xml`

Behavior:

- Observes text selection, likely copy-button clicks, copy announcements, copy-completion notifications, and Ctrl+C.
- Uses bounded Japanese/English copy markers rather than unrestricted `copy` substring matching.
- Tries `ClipboardManager` after likely copy events.
- Falls back to the most recent nonblank selected text when direct clipboard access is denied or empty.
- Performs capture attempts at 80/250/700 ms but stops after the first usable result.
- Uses a bounded persistent queue with TTL and duplicate suppression.
- Keeps an item queued while waiting for acknowledgement.
- Retries an in-flight item after a 15-second acknowledgement timeout.
- Disabling clipboard relay clears pending clipboard data and prevents later dispatch.
- Records only timestamp/trigger/capture path/result for diagnostics; clipboard contents and source app names are not stored in diagnostics.

Legacy ADB path removed:

- No logcat clipboard parser
- No `READ_LOGS` permission
- No overlay permission
- No transparent floating-activity registration
- Ordinary text relay is owned by AccessibilityService when enabled
- Existing ClipboardManager listener remains for image/file behavior and text compatibility only when Accessibility relay is disabled

Important limitation:

Accessibility behavior differs by application. This remains a heuristic until the target app matrix is tested on HONOR 400 Pro.

### Notification verification-code relay

Files:

- `NotificationCodeListenerService.kt`
- `OtpCodeExtractor.kt`
- `OtpRelayStore.kt`
- `OtpRelayDispatcher.kt`
- `OtpCodeExtractorTest.kt`

Behavior:

- Uses user-authorized NotificationListenerService.
- Ignores ClipCascade notifications, ongoing events, and foreground-service notifications.
- Applies optional source-package filtering.
- Extracts a short value only near verification/authentication context.
- Normalizes full-width characters.
- Rejects likely dates, times, amounts, phone numbers, tracking/order identifiers, and unrelated numbers.
- Queues only the extracted value, source package, timestamp, and internal ID.
- Does not persist notification title or body.
- Uses TTL, duplicate suppression, retry, and in-flight acknowledgement timeout.
- Turning the feature off or changing its source-app policy clears previously queued values.
- Unit tests cover positive Japanese/English examples and major false-positive classes.

Important limitations:

- Not tested on Android 16 / MagicOS.
- Android 15/16 may redact OTP-like notification content from an ordinary notification listener.
- Extractor heuristics still require real notification examples and may need tuning.

### Queue and acknowledgement semantics

Native delivery no longer deletes on React event emission.

Common local state machine:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

P2S current completion:

`LOCAL_TRANSPORT_ACCEPTED -> NATIVE_ACK -> DELETE`

P2P with Extended Windows:

`LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_RECEIVED -> WINDOWS_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

P2P with an older/non-Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

Files/build patches:

- `ClipboardRelayDispatcher.kt`
- `OtpRelayDispatcher.kt`
- `RelaySettingsModule.kt`
- `scripts/prepare_relay_transport_ack.js`
- `scripts/prepare_relay_startup_order.js`
- `scripts/prepare_service_listener_lifecycle.js`
- `scripts/prepare_p2p_peer_ack.js`
- `scripts/prepare_p2p_ack_validation_order.js`

Behavior:

- One native in-flight item at a time per queue.
- Each item carries `relayId` and source metadata inside the local app path.
- P2S returns success only after active STOMP `publish` acceptance.
- P2P returns local acceptance only after at least one open DataChannel accepts every fragment.
- Extended P2P messages add backward-compatible `relayId`/`ackRequested` metadata.
- Extended Windows sends an ACK only after validated text clipboard application, or for a duplicate already applied.
- Android handles ACK control envelopes before clipboard parsing.
- Receive hashes are committed only after inbound validation succeeds.
- Old clients can ignore the additional metadata.
- Pending P2P ACK timers are scoped to one service generation and cleared during teardown.
- Persistent queues resume only after the `SHARED_TEXT` listener is installed.

Remaining acknowledgement limitation:

- P2S has no Windows-applied ACK because that requires a server/backward-compatible protocol design.
- P2P fallback for old peers confirms only local DataChannel acceptance.
- Multiple P2P peers currently complete on the first valid peer ACK; explicit all-peer/quorum semantics are not implemented.

### Android recovery

Files:

- `RecoveryCoordinator.kt`
- `RecoveryListener.js`
- `NetworkRecoveryMonitor.kt`
- `HeadlessTask.js`
- `HeadlessTaskService.kt`
- `BootReceiver.kt`
- `ScheduleService.kt`
- `MainApplication.kt`
- `MainActivity.kt`

Behavior:

- Periodic heartbeat failure requests bounded recovery and retains notification/user-action fallback.
- Active React context receives a recovery event; otherwise Headless JS is attempted.
- Recovery requests have a 60-second native and JavaScript cooldown.
- Old foreground-service generation is asked to stop gracefully by setting `wsIsRunning=false`.
- Recovery waits up to 8 seconds for old service teardown before native forced-stop fallback.
- New service starts only after old-generation cleanup attempt.
- Service-specific event subscriptions are removed individually; old service shutdown no longer removes a newer generation's listeners.
- Boot recovery uses a 30-second Headless task and wake lock.
- Network return immediately resumes persistent queues; after a 5-second grace period, still-offline sync requests recovery.
- MainActivity schedules unique periodic work without blocking the UI thread.
- Content-free recovery diagnostics record reason/path/outcome.

Important limitations:

- Android may reject a background foreground-service start; notification/user interaction remains necessary in some states.
- Real process-death, reboot, screen-off, and MagicOS process-kill tests are outstanding.
- The recovery path is compile/CI validated, not yet device validated.

## Windows — implemented

Files include:

- `core/windows_application.py`
- `gui/enhanced_tray.py`
- `gui/live_status_dialog.py`
- `gui/status_dialog.py`
- `main.py`
- `scripts/prepare_p2p_peer_ack.py`

Behavior:

- Visible status/control window after login.
- Connection, server, mode, peer/transfer, reconnect, and latest-operation status.
- Restart Sync, Reconnect, Disconnect, Open Logs, Copy Diagnostics.
- Second launch requests the existing process to show its window.
- P2P restart waits for teardown before replacement connection.
- Watchdog handles persistent offline state and signaling-connected/DataChannel-dead state.
- Rotating logs.
- P2P receiver recognizes control ACK envelopes and never treats them as clipboard content.
- P2P receiver sends `relayId` ACK after validated text clipboard application.
- PyInstaller CI build succeeds after the ACK patch is applied.

Important limitations:

- Windows runtime has not yet been exercised against the current Android build on real devices.
- Existing text/image/file and P2S/P2P regression matrix has not been run.

## CI and release packaging

Latest known successful Android workflow:

- Extended UI preparation: success
- Local transport ACK patch: success
- Queue startup-order patch: success
- Service-listener lifecycle patch: success
- P2P peer-ACK patch: success
- P2P validation-order correction: success
- JavaScript/Hermes bundle: success
- App-scoped Android unit tests: success
- Gradle APK build: success
- Embedded bundle verification: success
- Build/test/APK artifact uploads: success

Latest known successful Windows workflow:

- P2P peer-ACK patch: success
- Python compilation: success
- Dependency installation: success
- PyInstaller build: success
- EXE artifact upload: success

Release workflow is aligned with the same Windows and Android transformation steps, but no tag/release has been published because real-device tests remain incomplete.

## Not yet complete

- Representative ADB-free copy tests on HONOR/MagicOS
- Screen-off SMS/email verification tests
- Android 16 OTP redaction measurement and legitimate fallback decision
- P2S Windows-applied ACK
- Explicit multiple-P2P-peer ACK semantics
- Process-death/reboot/MagicOS recovery tests
- Upstream text/image/file/P2S/P2P regression tests
- Tagged release containing tested artifacts

## Do not claim

Do not describe the branch as complete, production-ready, reliably screen-off, universally compatible, or fully end-to-end acknowledged until the Definition of Done in `docs/REQUIREMENTS.md` and the real-device test matrix are satisfied.
