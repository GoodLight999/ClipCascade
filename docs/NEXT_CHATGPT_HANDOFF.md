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

The user strongly dislikes inflated completion claims. Distinguish compile success, implementation, and real-device verification.

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

### Current dispatchers

- `ClipboardRelayDispatcher`
- `OtpRelayDispatcher`

Both wait for React context and local transport-ready state, then emit `SHARED_TEXT`.

**Known correctness bug:** both delete after local event emission rather than transport or peer acknowledgement.

### Android settings

- `RelaySettingsActivity`
- `RelaySettingsModule` / `RelaySettingsPackage`
- `AppRoot.js` persistent settings button

### Recovery

- `HeadlessTask.js`: boot and health-failure entry points
- `ScheduleService.kt`: heartbeat worker

**Known gap:** worker currently notifies on failure rather than actually launching the health-failure recovery event; queues are not explicitly resumed from every recovery entry point.

### Windows

- `WindowsApplication` wrapper
- Enhanced tray
- Live status dialog
- Watchdog and rotating logs

## Highest-priority next tasks

### Priority 1 — transport acknowledgement

Do not delete native queue items directly after `DeviceEventEmitter.emit`.

Recommended incremental design:

1. Native dispatcher emits one item with `relayId` and source metadata but leaves it queued/in-flight.
2. JavaScript `SHARED_TEXT` handler performs the existing P2S/P2P send.
3. JavaScript calls a native acknowledgement module only after the actual transport send function accepts the item.
4. Native store marks/removes the matching item.
5. Add in-flight timeout so a missing acknowledgement returns the item to retry.
6. Later, if feasible, extend the wire protocol with peer-level ACK after Windows applies the clipboard.

Inspect `StartForegroundService.js` for `SHARED_TEXT` listener and P2S/P2P send paths before editing. Preserve ordinary manual share behavior.

### Priority 2 — recovery wiring

- Make heartbeat failure launch actual foreground-service recovery.
- On boot/service start/reconnect, call both queue dispatchers.
- Prevent duplicate restart loops.
- Verify `wsIsRunning` semantics so recovery does not incorrectly no-op after process death.

### Priority 3 — accessibility correctness

- Add observable diagnostics that do not log clipboard contents: event type, source package, capture path, queue result.
- Add per-app compatibility testing.
- Reduce false positives from generic buttons containing `copy`.
- Confirm `canRetrieveWindowContent` and service metadata are sufficient on the target device.
- Consider a user-triggered accessibility action fallback when automatic detection is impossible.

### Priority 4 — Android 16 notification behavior

Use current official Android documentation and real-device evidence. Determine whether OTP text is redacted for the target notification sources. Do not promise a bypass. If redacted, document which legitimate architecture can satisfy the use case, such as an approved companion-device relationship or user-selected/manual fallback.

### Priority 5 — regression and release

- Test P2S and P2P.
- Test upstream text/image/file paths.
- Test Windows control/recovery.
- Publish only after the matrix passes.

## Useful commands / CI

Android CI workflow: `.github/workflows/android-mobile-ci.yml`

It:

- installs JS dependencies
- rewrites obsolete ADB instructions for the distributed bundle
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

> Open `GoodLight999/ClipCascade`, branch `stability-mobile-otp`. Read `docs/REQUIREMENTS.md`, `docs/CURRENT_STATUS.md`, `docs/NEXT_CHATGPT_HANDOFF.md`, and `docs/TEST_MATRIX.md`. Continue Priority 1: implement JS-to-native transport acknowledgement so native clipboard/OTP queues are not deleted merely on React event emission. Keep the PR draft and run Android CI after each coherent change.
