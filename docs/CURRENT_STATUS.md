# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Canonical requirements: `docs/REQUIREMENTS.md`

## Current high-level state

The branch contains working Windows synchronization, ADB-free Android copied-text relay, guided Android setup, bilingual Extended UI, persistent verification-code relay, and a synthetic full-path verification test.

Current behavior baselines before final documentation commits:

- Android recovery/queue baseline: `230a10562456dc96d0f354e2d78e01123d10a800`
- Windows authentication repair/tests: `fae212a9fbe127f582e6bdace5c8a0c8f25fb575`
- Guided setup/localization/synthetic test/extractor: `5433eb8e8ffaae47ed6873af67ea1c1df6824333`

Consult PR #1 for the current branch HEAD before fetching artifacts.

User-validated facts:

- The repaired Windows artifact logs into the real deployment and the Windows GUI works.
- Android automatic copied-text relay works through Accessibility in the user's current test scenario.
- The current Extended clipboard path does not require the old ADB commands.

These reports are real-device validation, but they do not yet constitute the complete app matrix, screen-off SMS/email matrix, or recovery matrix.

## Android — implemented

### Packaging, localization, and no-ADB identity

- App name: `ClipCascade Extended`
- Test package: `com.clipcascade.extended`
- Version: `3.2.1-extended.2-standalone`
- Standalone APK contains `assets/index.android.bundle`; Metro is not required
- Default resources are English and `values-ja` provides Japanese
- Native settings, service labels/descriptions, persistent settings entry, primary React labels, and displayed status select Japanese/English from the system locale
- Protocol/storage state remains stable English internally; status translation is display-only
- CI checks transformed source for Japanese and English labels
- CI rejects transformed UI containing the old READ_LOGS grant or overlay command
- Android manifest contains neither READ_LOGS nor overlay permission

### Guided setup

The always-reachable Sharing setup screen presents five ordered steps:

1. Android 13+ notification permission
2. Accessibility clipboard-sharing service
3. notification-listener access
4. battery-optimization exemption
5. HONOR/MagicOS background/auto-launch confirmation

Behavior:

- `Continue setup` opens the next missing Android setting
- status refreshes after returning from Android settings
- HONOR/MagicOS guidance names auto-launch, secondary launch, and background execution
- manufacturer-specific switches are manually confirmed because Android cannot reliably query every vendor implementation through one common API
- first-run prompt routes into this same guided setup
- existing clipboard/verification switches, source-app filter, queue controls, diagnostics, and clipboard test remain available

This flow is compile/CI validated but has not yet been executed from a fresh permission state on the target HONOR device.

### Accessibility clipboard relay

- User-authorized AccessibilityService detects bounded copy cues, selection events, likely copy actions, announcements, and Ctrl+C
- direct ClipboardManager read with recent-selection fallback
- persistent bounded queue with TTL and duplicate suppression
- no removal merely because a React event was emitted
- dispatch waits for an actual connected state; `Disconnected` is not misclassified as connected
- legacy logcat/READ_LOGS/overlay clipboard path is removed

The user confirmed this path works in the current Android test scenario. Representative-app compatibility remains untested.

### Verification-code notification relay

- User-authorized NotificationListenerService with rebind request after listener disconnection
- optional source-package filter; empty selection means contextual matching across all apps
- notification title/text, expanded text, text lines, conversation title, and MessagingStyle current/historic message text are combined transiently
- combined notification content is not persisted or sent
- only the extracted value, timestamp, and opaque relay ID enter the persistent queue
- persistent queue has TTL, duplicate suppression, retry, and acknowledgement timeout
- turning relay off or changing source policy clears pending values
- dispatch waits for an actual connected state

Bilingual extraction supports:

- Japanese authentication, confirmation, login/sign-in, one-time password, identity verification, two-step authentication, and security-code language
- English OTP, one-time password/passcode, verification, security, auth, login/sign-in, confirmation, access, and two-factor language
- full-width normalization
- numeric, compact alphanumeric, prefixed, and grouped values
- code-before-keyword and code-after-keyword forms
- transaction notifications containing a separate amount
- verification addressed directly to a destination email address

False-positive controls cover dates, times, years, nearby monetary values, phone numbers, tracking identifiers, URL/email substrings, ordinary numbers, and generic postal/promotion/error codes.

The expanded extractor corpus passed Android CI run `28308127548`. Real Gmail/SMS/Outlook layouts and Android 16 redaction remain target-device work.

### Synthetic end-to-end verification test

The settings screen can post a real local notification containing a fresh random synthetic six-digit value.

The test follows the normal path:

`LOCAL_NOTIFICATION -> NOTIFICATION_LISTENER -> LOCAL_EXTRACTOR -> PERSISTENT_QUEUE -> REACT_TRANSPORT -> EXISTING_ACK -> DELETE`

Status distinguishes:

- posted
- detected
- queued
- extraction failed
- deduplicated
- post failed
- acknowledged

Additional behavior:

- test is blocked with a guided action when notification permission/access or relay enablement is missing
- explicitly marked synthetic notifications bypass the optional source-app filter
- ordinary ClipCascade notifications remain ignored
- synthetic value/status expire after five minutes
- the existing ACK implementation is reused; no test-only deletion path was added
- in Extended P2P, ACK follows validated Windows clipboard application
- P2S retains its local-transport acknowledgement limitation

The code path is compile/CI validated but has not yet been run on the HONOR 400 Pro.

### Android recovery and redundancy

- heartbeat-triggered bounded recovery
- active React event or Headless JS recovery path
- graceful old-generation stop followed by bounded native-shell fallback
- generation-scoped listener lifecycle
- delayed one-time WorkManager heartbeat as redundant boot recovery
- replacement-network tracking prevents late old-network `onLost` from cancelling recovery
- both persistent queues resume on network availability
- content-free recovery diagnostics

These paths remain unverified against MagicOS process management.

## Delivery acknowledgement

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

P2S current completion:

`LOCAL_TRANSPORT_ACCEPTED -> NATIVE_ACK -> DELETE`

P2P with Extended Windows:

`LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_RECEIVED -> WINDOWS_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

P2P with old/non-Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

Preserved behavior:

- `relayId` / `ackRequested` metadata
- Windows ACK after validated text clipboard application or duplicate-already-applied handling
- Android ACK control-envelope handling before clipboard parsing
- receive-hash commit only after validation
- generation-scoped P2P ACK timers
- queue startup only after the `SHARED_TEXT` listener is installed

Remaining limitations:

- P2S has no Windows-applied ACK
- old-peer fallback confirms local DataChannel acceptance only
- multiple P2P peers complete on the first valid ACK

## Windows — implemented and partly validated

- visible status/control window
- restart, reconnect, disconnect, open logs, and copy diagnostics controls
- second launch focuses existing process
- complete P2P teardown before replacement
- watchdog for persistent offline and signaling-connected/DataChannel-dead states
- rotating logs
- P2P clipboard-applied ACK receiver

Authentication repair:

- login rejects an HTTP-200 response that is still a login form
- one `requests.Session` is retained through login and authenticated API calls
- all server/proxy cookies are preserved
- authenticated calls have bounded timeouts
- empty, HTML, non-JSON, non-object, and unsupported-mode responses are diagnosed explicitly
- rejected sessions return to login instead of terminating Windows startup
- logs contain response metadata but not bodies, credentials, cookie values, or private URLs
- HTTP handling and P2P ACK tests run before PyInstaller packaging

The user confirmed this repair works against the actual deployment.

## CI caveats and resolved failures

- `000d964408d7f4824cd0f16fb9d47954ccddf425` failed extractor tests because a grouped candidate regex joined prose with date/code tokens. The regex was narrowed.
- `abb9ca720ab728c56d8ee490132f0c9c1f6ae572` failed because raw Japanese grep was performed on a Metro bundle that escaped Unicode. Verification was moved to transformed source.
- `bc7cbfc7005fa925662bc0dc3e9969e0b799a9a2` failed one new positive test because `verify <email-address>` was not treated as verification context. `5433eb8e8ffaae47ed6873af67ea1c1df6824333` fixed it and Android run `28308127548` succeeded.

See `docs/progress.md` for commit-by-commit details.

## Highest-priority validation

1. Fetch matching Android and Windows artifacts from the final branch HEAD.
2. Install the new APK on HONOR 400 Pro.
3. Run guided setup from a fresh or permission-reset state.
4. Start Extended P2P sync and run the synthetic notification test.
5. Confirm Windows clipboard becomes the synthetic value and Android status reaches acknowledged.
6. Repeat with Windows offline and after reconnect.
7. Run real SMS and email tests at screen-on, background, lock, and 1/15/30+ minute screen-off intervals.
8. Record exposed/redacted notification fields separately from extraction and transport results.
9. Test Wi-Fi/mobile handover, process kill, reboot, delayed boot fallback, and upstream text/image/file paths.

## Not yet complete

- guided setup on target HONOR/MagicOS
- synthetic full-path notification test on target devices
- representative Android copy compatibility matrix
- screen-off SMS/email verification-code delivery
- Android 16 OTP redaction measurement
- process-death/reboot/MagicOS recovery validation
- P2S Windows-applied ACK
- explicit multiple-P2P-peer ACK policy
- complete upstream regression matrix
- tested tagged release

## Do not claim

Do not describe the branch as complete, production-ready, reliably screen-off, universally compatible, or empirically superior to another OTP application until the target-device and comparison matrix passes.
