# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Canonical requirements: `docs/REQUIREMENTS.md`

## Current high-level state

The branch contains substantial Android and Windows reliability work. The current code baselines are:

- Android behavior: `230a10562456dc96d0f354e2d78e01123d10a800`
- Windows authentication behavior/tests: `fae212a9fbe127f582e6bdace5c8a0c8f25fb575`

Documentation commits follow those code commits. Consult PR #1 for the current branch HEAD before fetching artifacts.

All code commits listed in `docs/progress.md` passed both Android standalone CI and Desktop Windows CI. This proves transformation scripts, JavaScript bundling, Android unit tests, Kotlin/Java compilation, APK packaging, desktop unit tests, Python compilation, PyInstaller packaging, and artifact upload. It does **not** prove HONOR/MagicOS behavior, the user's deployed server behavior, or screen-off SMS/email OTP delivery.

## Android — implemented

### Packaging and settings

- App name: `ClipCascade Extended`
- Test package: `com.clipcascade.extended`
- Standalone APK contains `assets/index.android.bundle`; Metro is not required
- Settings entry remains reachable before login and while synchronization runs
- Clipboard relay, verification-code relay, accessibility status, notification-access status, source-app filter, queue counts, clear actions, test relay, and content-free diagnostics are present

### Accessibility clipboard relay

- User-authorized AccessibilityService detects bounded copy cues, selection events, likely copy actions, announcements, and Ctrl+C
- Direct ClipboardManager read with recent-selection fallback
- Delayed capture attempts stop after the first usable result
- Persistent bounded queue with TTL and duplicate suppression
- No removal merely because a React event was emitted
- Dispatch waits for an actual connected state; `Disconnected` is no longer misclassified as `Connected`
- Legacy logcat/READ_LOGS/overlay clipboard path remains removed

Accessibility behavior is still app-dependent and requires the HONOR 400 Pro application matrix.

### Verification-code notification relay

- User-authorized NotificationListenerService
- Existing `onListenerDisconnected()` notification-listener rebind request preserved
- Optional source-package filter
- Local contextual extraction with full-width normalization and false-positive rejection
- Only the extracted value, timestamp, and opaque ID enter persistent storage
- Notification title/body are not persisted or sent
- Persistent queue with TTL, duplicate suppression, retry, and acknowledgement timeout
- Dispatch waits for an actual connected state

Android 15/16 may redact OTP-like notification content. Screen-off SMS/email capture remains unverified on the target device.

### Android recovery and redundancy

- Heartbeat-triggered bounded recovery
- Active React event or Headless JS recovery path
- Graceful old-generation stop followed by bounded native-shell fallback
- Generation-scoped listener lifecycle
- Delayed one-time WorkManager heartbeat as a redundant boot-recovery path
- Network recovery tracks the actual replacement default `Network`
- A late `onLost` callback for the old network cannot cancel replacement-network recovery
- Both persistent queues are scheduled immediately on network availability
- Content-free recovery diagnostics

These paths are CI/compile validated but not yet proven against MagicOS process management.

## Delivery acknowledgement

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

P2S currently completes after local STOMP publish acceptance:

`LOCAL_TRANSPORT_ACCEPTED -> NATIVE_ACK -> DELETE`

P2P with Extended Windows completes after validated Windows text clipboard application:

`LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_RECEIVED -> WINDOWS_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

P2P with an old/non-Extended peer uses the documented five-second compatibility fallback.

Preserved behavior:

- `relayId` / `ackRequested` metadata
- Windows ACK only after validated text clipboard application or duplicate-already-applied handling
- Android ACK control-envelope handling before clipboard parsing
- receive-hash commit only after validation
- generation-scoped P2P ACK timers
- queue startup only after the `SHARED_TEXT` listener is installed

Remaining limitations:

- P2S has no Windows-applied ACK
- old-peer P2P fallback confirms local DataChannel acceptance only
- multiple-P2P-peer completion still uses the first valid ACK

## Windows — implemented

### Status and recovery

- Visible status/control window
- Restart, reconnect, disconnect, open logs, and copy diagnostics controls
- Second launch focuses the existing process
- Complete P2P teardown before replacement
- Watchdog for persistent offline and signaling-connected/DataChannel-dead states
- Rotating logs
- P2P clipboard-applied ACK receiver remains intact

### Authentication/API repair

A real Windows artifact reported HTTP 200 for login, then failed to decode `/csrf-token` and `/server-mode` as JSON. The old log did not contain enough safe response metadata to determine whether the deployment returned an empty body, HTML/login redirect, proxy interception, an incomplete cookie session, or an incompatible endpoint.

Confirmed client defects were repaired:

- login no longer accepts a returned login form merely because the final HTTP status is 200
- one `requests.Session` is retained through login and authenticated API calls
- every server/proxy cookie is preserved instead of forwarding only `JSESSIONID`
- authenticated requests have bounded connect/read timeouts
- empty, HTML, non-JSON, non-object, and unsupported-mode responses are rejected explicitly
- mandatory API failures clear the rejected session and return to the login flow instead of terminating Windows startup
- connection and timeout exceptions use the same controlled recovery path
- `/csrf-token` remains non-fatal because it is only needed for logout
- `/server-mode` remains mandatory; P2S/P2P is never guessed

Safe diagnostics include endpoint path, status, content type, body byte count, final redirect path, redirect status codes, and transport exception class. Response bodies, cookie values, credentials, and private server URLs are not logged.

Desktop CI now runs both the authenticated HTTP test suite and the existing P2P ACK suite before PyInstaller packaging.

The repaired authentication path is unit/CI validated but has not yet been retried against the user's actual deployment.

## Highest-priority validation

1. Fetch matching Android and Windows artifacts from the current branch HEAD.
2. Retry Windows login against the real deployment.
3. If login still fails, preserve only the new content-free `/server-mode` diagnostic line.
4. After Windows connects, install the matching APK on HONOR 400 Pro.
5. Verify settings and synthetic test relay first.
6. Verify Extended Android → Extended Windows P2P clipboard-applied ACK.
7. Run screen-on, background, locked, and 1/15/30+ minute screen-off SMS/email tests.
8. Record whether Android 16/MagicOS exposes or redacts notification content.
9. Test Windows-offline queueing, Wi-Fi/mobile handover, process kill, reboot, and delayed boot fallback.
10. Run upstream text/image/file regression in P2S and P2P.

## Not yet complete

- Repaired Windows login against the user's server
- Representative ADB-free copy matrix on HONOR/MagicOS
- Screen-off SMS/email verification-code delivery
- Android 16 OTP redaction measurement
- Process-death/reboot/MagicOS recovery validation
- P2S Windows-applied ACK
- explicit multiple-P2P-peer ACK semantics
- complete upstream regression matrix
- tested tagged release

## Do not claim

Do not describe the branch as complete, production-ready, reliably screen-off, universally compatible, or fully end-to-end acknowledged until the real-device and deployed-server validation matrix passes.
