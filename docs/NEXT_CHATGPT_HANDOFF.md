# Next ChatGPT Thread — Start Here

This file is written so a new ChatGPT thread can continue the work without relying on hidden conversation history.

## Repository and branch

- Repository: `GoodLight999/ClipCascade`
- Repository visibility at the time of writing: **public**
- Development branch: `stability-mobile-otp`
- Draft PR: `#1`
- Base branch: `main`

Because the repository is public, this branch and these documents are also public. Do not add secrets. If private development is required, first move/copy this branch to a private repository.

## Read these files in order

1. `docs/REQUIREMENTS.md` — canonical product requirements and Definition of Done
2. `docs/CURRENT_STATUS.md` — what is implemented and what is not
3. `docs/TEST_MATRIX.md` — required real-device and regression tests
4. This file — continuation instructions

## User intent

The user wants a practical daily-use Android ↔ Windows clipboard synchronizer with these defining features:

- No ADB, root, or Shizuku for normal use
- Accessibility-based ordinary text copy relay from Android to Windows
- NotificationListenerService-based local extraction of verification codes from SMS/email/app notifications
- Only the extracted code is sent, not the notification body
- Screen-off/locked operation on HONOR 400 Pro / Android 16 / MagicOS
- Persistent queues and recovery after disconnect, sleep, process death, and reboot
- Visible Windows status/control UI and self-recovery
- Installable standalone APK and repeatable releases
- No personal handle in product identity

The user strongly dislikes inflated completion claims. Distinguish compile success, implementation, local transport acceptance, peer receipt, and real-device verification.

## Current artifact identity

- App display name: `ClipCascade Extended`
- Android package: `com.clipcascade.extended`
- Current development version family: `3.2.1-extended.*`
- Build target: ARM64

Older test packages containing personal branding are obsolete and should not be used.

## Current branch architecture

### Android input sources

1. `ClipboardAccessibilityService`
   - Watches likely copy-related accessibility events.
   - Attempts ClipboardManager read.
   - Falls back to recent selected text.
   - Enqueues into `ClipboardRelayStore`.

2. `NotificationCodeListenerService`
   - Reads user-authorized notifications.
   - Applies optional package filter.
   - Extracts a code using `OtpCodeExtractor`.
   - Enqueues only the extracted code into `OtpRelayStore`.

### Native queues

- `ClipboardRelayStore`: bounded persistent text queue, TTL, dedupe
- `OtpRelayStore`: bounded persistent code queue, TTL, dedupe

### Dispatch and acknowledgement

- `ClipboardRelayDispatcher`
- `OtpRelayDispatcher`
- `RelaySettingsModule.acknowledgeRelay()`
- `scripts/prepare_relay_transport_ack.js`
- `scripts/prepare_relay_startup_order.js`

Implemented behavior:

1. Native emits one queued item with `relayId` and source metadata but keeps it queued/in-flight.
2. JavaScript performs the P2S or P2P send.
3. P2S reports accepted only after an active STOMP client accepts `publish`.
4. P2P reports accepted only after at least one open DataChannel accepts every fragment.
5. JavaScript calls the native acknowledgement method only after that accepted-send result.
6. Native removes the item after acknowledgement.
7. A missing acknowledgement times out after 15 seconds and the item retries.
8. Persistent queues resume after `SHARED_TEXT` listener registration.

Remaining limitation:

This is a **local transport acceptance acknowledgement**, not an end-to-end Windows clipboard-applied acknowledgement. A future protocol extension is needed for:

`WINDOWS_RECEIVED -> WINDOWS_CLIPBOARD_APPLIED -> PEER_ACK`

### Android settings

- `RelaySettingsActivity`
- `RelaySettingsModule` / `RelaySettingsPackage`
- `AppRoot.js` persistent settings button

### Recovery

- `HeadlessTask.js`: boot and health-failure entry points
- `HeadlessTaskService.kt`: 30-second timeout
- `BootReceiver.kt`: starts Headless task and acquires wake lock
- `ScheduleService.kt`: heartbeat worker

Current gap:

- `ScheduleService` still notifies after heartbeat failure instead of launching the health-failure task itself.
- An attempted direct automatic-recovery write was not applied.
- Foreground-service startup does resume both native relay queues after listener registration.

### Windows

- `WindowsApplication` wrapper
- Enhanced tray
- Live status dialog
- Watchdog and rotating logs

## Completed development milestone

### Incremental transport acknowledgement

Completed and CI-validated:

- No deletion immediately after React event emission
- One native in-flight item at a time
- 15-second retry timeout
- P2S accepted-send result
- P2P open-channel/all-fragment accepted-send result
- JS-to-native ACK
- Queue resume after listener registration
- Manual-share behavior preserved separately from background relay

Do not call this end-to-end delivery acknowledgement.

## Highest-priority next tasks

### Priority 1 — recovery wiring

- Make heartbeat failure launch actual recovery where Android permits.
- Keep notification/user-action fallback because Android may reject background service starts.
- Prevent duplicate restart loops.
- Verify `wsIsRunning` semantics after process death.
- Ensure clipboard and OTP queues resume after boot, foreground-service replacement, and connectivity recovery.
- Run CI after each coherent recovery change.

### Priority 2 — peer-level acknowledgement design

- Define a backward-compatible envelope containing a message/relay ID.
- Windows receiver should ACK only after clipboard application succeeds.
- Android should retain the queue item until peer ACK or a bounded retry policy.
- Decide explicit semantics for multiple P2P peers.
- Do not break older upstream clients without a compatibility mode.

### Priority 3 — accessibility correctness

- Add diagnostics that never log clipboard contents: event type, source package, capture path, queue result, ACK stage.
- Add per-app compatibility testing.
- Reduce false positives from generic buttons containing `copy`.
- Confirm `canRetrieveWindowContent` and service metadata on the target device.
- Consider a user-triggered accessibility action fallback when automatic detection is impossible.

### Priority 4 — Android 16 notification behavior

Use current official Android documentation and real-device evidence. Determine whether OTP text is redacted for the target notification sources. Do not promise a bypass. If redacted, document which legitimate architecture can satisfy the use case, such as an approved companion-device relationship or user-selected/manual fallback.

### Priority 5 — regression and release

- Test P2S and P2P.
- Test upstream text/image/file paths.
- Test Windows control/recovery.
- Publish only after the matrix passes.

## Latest known CI result

Latest code-bearing head documented before this handoff refresh:

`ed42333ec46cf236c52e33f14b13bc36cf555479`

Android workflow passed:

- Extended UI preparation
- Relay transport ACK transformation
- Relay startup ordering
- JavaScript/Hermes bundle
- Gradle APK build
- Embedded bundle verification
- Artifact upload

Windows workflow passed:

- Python source compilation
- Dependency installation
- PyInstaller executable build
- Artifact upload

Documentation-only commits may appear after that code-bearing head and can trigger another CI run even though code is unchanged.

## Useful CI files

Android CI workflow: `.github/workflows/android-mobile-ci.yml`

It:

- installs JS dependencies
- rewrites obsolete ADB instructions for the distributed bundle
- patches transport acknowledgement behavior
- orders queue startup after listener registration
- creates `index.android.bundle`
- builds signed debug/standalone APK
- verifies the bundle exists inside the APK
- uploads `ClipCascade-Extended-standalone`

Release workflow: `.github/workflows/fork-release.yml`

Release tag prefix: `extended-v*`

## Safety and privacy constraints

- Do not commit actual codes, notification contents, credentials, or server details.
- Do not log clipboard text or notification text.
- Do not attempt to evade Android platform restrictions.
- User authorization through Accessibility and Notification Access settings is required.

## Suggested first message for the next ChatGPT thread

> Open `GoodLight999/ClipCascade`, branch `stability-mobile-otp`. Read `docs/progress.md`, `docs/REQUIREMENTS.md`, `docs/CURRENT_STATUS.md`, `docs/NEXT_CHATGPT_HANDOFF.md`, and `docs/TEST_MATRIX.md`. Continue Priority 1: recovery wiring. Preserve the transport-ACK behavior already implemented, keep PR #1 draft, and run Android CI after each coherent change.
