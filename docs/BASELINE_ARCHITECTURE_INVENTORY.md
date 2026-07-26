# Baseline Architecture Inventory

Date: 2026-07-26
Baseline: `Sathvik-Rao/ClipCascade@fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`

This document describes the pristine upstream implementation only. It intentionally excludes the archived patchwork branches.

## Repository layout

- `ClipCascade_Mobile/src`: React Native mobile client.
- `ClipCascade_Desktop/src`: Python desktop client for Windows, macOS, and Linux.
- `ClipCascade_Server`: server implementation; out of modification scope for this project.
- `.github`: upstream automation and repository configuration; full workflow inventory remains pending.

## Mobile stack

Evidence: `ClipCascade_Mobile/src/package.json`

- React Native `0.80.2`
- React `19.1.0`
- Node engine `>=18`
- STOMP/WebSocket transport via `@stomp/stompjs`
- foreground execution via `@notifee/react-native`
- clipboard bridge via `@react-native-clipboard/clipboard`
- Android native implementation in Kotlin

The primary service implementation is concentrated in `ClipCascade_Mobile/src/StartForegroundService.js`. It owns transport, encryption, clipboard event listeners, duplicate suppression, file/image handling, and foreground-service behavior in one large module. This concentration creates a high regression radius and must be decomposed behind stable interfaces before adding multiple acquisition mechanisms.

## Existing Android components

Evidence: `ClipCascade_Mobile/src/android/app/src/main/AndroidManifest.xml`

Declared permissions include:

- `INTERNET`
- `POST_NOTIFICATIONS`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_REMOTE_MESSAGING`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `SYSTEM_ALERT_WINDOW`
- protected `READ_LOGS`
- `RECEIVE_BOOT_COMPLETED`

Declared runtime components include:

- `MainActivity`
- `ClipboardFloatingActivity`
- Notifee foreground service
- `BootReceiver`
- `HeadlessTaskService`
- `FileProvider`

## Existing Android clipboard acquisition path

Evidence:

- `ClipboardListenerModule.kt`
- `ClipboardFloatingActivity.kt`
- `StartForegroundService.js`

The current implementation combines two paths:

1. Register `ClipboardManager.OnPrimaryClipChangedListener` and directly read `primaryClip`.
2. When `READ_LOGS` is granted on Android 10+, spawn a dedicated thread and process running:

   `logcat -T <timestamp> ClipboardService:E *:S`

   If a matching line contains the application ID, launch `ClipboardFloatingActivity`.

`ClipboardFloatingActivity` creates a `TYPE_APPLICATION_OVERLAY` view, temporarily removes `FLAG_NOT_FOCUSABLE`, reads `ClipboardManager.primaryClip`, emits `onClipboardChange` to the React Native context, then removes the overlay and finishes.

### Consequences

- Background acquisition depends on recognizing a system log entry and successfully launching a focus-taking overlay at the correct time.
- The logcat process and reader thread are long-lived while monitoring is active.
- The overlay path is timing-sensitive and depends on manufacturer behavior, background-activity restrictions, overlay permission, React context availability, and the clipboard still containing the expected content.
- The same JS service owns acquisition events and network delivery, making it difficult to distinguish capture failure from transport failure.

These are architectural risk statements, not yet device-test conclusions.

## Existing Android service and transport behavior

Evidence: `StartForegroundService.js`

- Notifee registers the foreground service.
- A STOMP client is configured with:
  - reconnect delay: 10 seconds;
  - connection timeout: 5 seconds;
  - incoming heartbeat: 20 seconds;
  - outgoing heartbeat: disabled.
- Clipboard events are forwarded directly into the send path.
- Status is written into AsyncStorage and optionally surfaced by notifications.
- The service removes all listeners during cleanup.

Evidence: `HeadlessTaskService.kt`

- Headless JS task name: `Restart`
- timeout: 5 seconds
- foreground execution allowed

## Desktop stack

Evidence:

- `ClipCascade_Desktop/src`
- upstream README build instructions

- Python application
- Tkinter and pystray GUI/tray implementation
- STOMP/WebSocket transport
- PyInstaller specifications for Windows and macOS
- environment-specific Linux clipboard backends

Windows source build command documented upstream:

```bash
pip3 install -r requirements_win.txt
python3 -m PyInstaller ClipCascade_win.spec
```

## Existing desktop reconnect and status behavior

Evidence: `ClipCascade_Desktop/src/stomp_ws/stomp_manager.py`

- `get_stats()` returns `None`, so the tray cannot display transport statistics.
- `_on_close()` sleeps for a fixed reconnect interval and calls `connect()` again from the close callback.
- reconnect state is represented by several booleans rather than one explicit state machine.
- send failures are logged but not queued for later delivery.
- several disconnect exceptions are silently swallowed.

Evidence: `ClipCascade_Desktop/src/gui/tray.py`

- tray state initializes as connected before transport truth is consulted.
- the menu exposes Connect/Disconnect, logs, program files, links, and quit actions.
- a background thread polls `get_stats()` every second, but the current STOMP implementation always returns `None`.
- there is no durable status panel showing last error, last send/receive, reconnect attempt, or next retry.

## External behavioral references

Octoclip documentation describes two no-root families that are relevant as behavioral references:

- Accessibility-triggered monitoring, with acknowledged missed events when copy actions do not emit recognizable accessibility events.
- Shizuku and Shizuku Enhanced modes, with guided installation, activation, permission checks, post-setup verification, and different reboot recovery tradeoffs.

No Octoclip source code is available or used. Only its user-visible capability model and setup workflow are relevant.

## Clean architecture direction

No product code should be changed until this direction is refined into interfaces and tests.

### Android

Introduce separate layers:

1. `ClipboardAcquisitionBackend`
   - ordinary foreground listener;
   - accessibility event trigger;
   - Shizuku-backed trigger/acquisition;
   - ADB-granted log trigger as compatibility fallback.
2. `ClipboardEventNormalizer`
   - read/coerce content;
   - assign event identity;
   - suppress duplicates;
   - attach acquisition-method metadata without logging content.
3. `OutboundDeliveryQueue`
   - persist pending events;
   - send only through the existing server protocol;
   - retain on transient failure;
   - remove on confirmed local transport success/defined acknowledgement semantics.
4. `ConnectionStateMachine`
   - authoritative state and retry schedule;
   - capability-aware health reporting.
5. `DiagnosticsCoordinator`
   - one-action checks;
   - sanitized event trace;
   - exportable bundle.

### Desktop

Introduce one explicit connection state machine shared by transport and GUI:

- `DISCONNECTED`
- `CONNECTING`
- `CONNECTED`
- `RECONNECT_WAIT`
- `STOPPING`
- `AUTH_REQUIRED`
- `FATAL_ERROR`

The tray/status UI must render this state rather than maintaining an independent `is_connected` assumption.

## Immediate verification gates

Before architecture implementation:

1. Reproduce pristine Android debug and release builds.
2. Reproduce pristine Windows executable build.
3. Reproduce pristine Linux run/package path.
4. Capture exact toolchain versions and commands.
5. Confirm package names and artifact paths.
6. Run existing tests and linters.
7. Record whether upstream CI can generate unsigned test artifacts without secrets.

## Open questions

- Exact GitHub Actions workflow inventory and current artifact retention.
- Whether server delivery offers an application-level acknowledgement suitable for durable queue deletion.
- Android version and ROM matrix for real-device validation.
- Shizuku implementation technique that preserves server compatibility and avoids a permanent high-frequency poller.
- Accessibility event types that provide useful triggers without manipulating or obstructing other applications.
