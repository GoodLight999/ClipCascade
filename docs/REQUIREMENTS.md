# ClipCascade Extended — Canonical Requirements

This document is the source of truth for the `stability-mobile-otp` development branch.

> Privacy note: this repository is currently **public**. GitHub cannot make one branch private inside a public repository. Do not put credentials, tokens, private server URLs, phone numbers, email contents, clipboard contents, or other secrets in this branch. Move the repository to Private or copy this branch to a Private repository if the development record itself must be non-public.

## Product goal

Make ClipCascade dependable enough for daily Android ↔ Windows clipboard use without ADB, root, or Shizuku, including screen-off delivery of verification codes from Android notifications.

## Target environment

- Android target device: HONOR 400 Pro, Android 16 / MagicOS
- Desktop target: Windows 11
- Existing ClipCascade modes that must remain supported: P2S and P2P
- Existing text, image, and file sharing must not regress

## Naming and distribution

1. Do not put the user's handle or personal name in the app name, package name, artifact name, version string, UI, logs, or release title.
2. Working app identity: `ClipCascade Extended`.
3. Android package identity for the standalone test build: `com.clipcascade.extended`.
4. APK must be directly installable and must contain `index.android.bundle`; Metro must not be required.
5. Provide repeatable GitHub Actions builds and GitHub Release packaging.

## Android clipboard requirements

1. Ordinary Android text copy must be relayed to Windows without ADB, root, or Shizuku.
2. The primary implementation may use a user-authorized AccessibilityService.
3. The service must expose clear enable/disable state and a route to Android accessibility settings.
4. Copy capture should survive temporary network disconnects by using a persistent queue.
5. A copied item must not be removed merely because a React Native event was emitted.
6. P2P Extended clients should remove an item only after a peer confirms that the Windows text clipboard was applied. Older peer compatibility may use a clearly documented bounded fallback.
7. P2S must not be represented as peer-applied until the server/client protocol provides an application receipt.
8. Pending queue processing must resume after process restart, boot recovery, foreground-service restart, and network recovery.
9. Duplicate suppression must avoid repeated sends caused by multiple accessibility events for one copy action.
10. Do not claim universal compatibility until tested across representative apps; accessibility events differ by app.
11. Image/file sharing must continue using the upstream paths unless separately redesigned and tested.
12. Android 10+ background clipboard restrictions must be treated as architectural constraints. Accessibility must recover the explicitly selected text rather than assuming a background `ClipboardManager` read will succeed.
13. Merely selecting text must not send it; an explicit user Copy action or equivalent copy cue is required.

## Verification-code notification requirements

1. ClipCascade must use Android's user-authorized notification access and process notification text locally.
2. Only an extracted verification value may enter the relay queue. The full notification title/body must not be sent to Windows or persisted.
3. Extraction must require verification-code context and reject unrelated dates, times, prices, phone numbers, tracking/order identifiers, URLs, email addresses, and ordinary notification numbers.
4. Extraction must support both Japanese and English verification language, full-width characters, numeric codes, compact alphanumeric codes, and commonly grouped code formats.
5. Standard notification fields, expanded text, text lines, and MessagingStyle current/historic message bundles must be considered without persisting the combined notification text.
6. User must be able to enable/disable verification-code relay.
7. User must be able to restrict processing to selected notification-source apps; empty selection means all apps subject to contextual extraction.
8. Values must use a persistent queue with expiry and duplicate suppression.
9. Turning the feature off or changing the source-app policy must clear pending values created under the prior policy.
10. The app must provide a synthetic end-to-end test notification containing a newly generated fake code.
11. The synthetic test must use the normal notification-listener, extractor, persistent queue, transport, and acknowledgement path rather than injecting directly into the queue.
12. Test status must distinguish notification posted, value detected, queued, failed extraction, deduplicated, and acknowledged.
13. In Extended P2P, an acknowledged synthetic test may be described as Windows clipboard-applied. P2S must retain its documented local-transport acknowledgement limitation.
14. Screen-off and locked-device behavior must be tested on HONOR/MagicOS.
15. Android 15/16 OTP redaction must be treated as an unresolved platform constraint until verified. Do not claim reliable SMS/email OTP capture solely because NotificationListenerService compiles.
16. Do not add direct SMS permission handling unless explicitly approved and designed with appropriate distribution/privacy constraints.

## Reliability requirements

1. Android foreground synchronization must recover after sleep, app/process termination, connectivity changes, and device restart where Android permits.
2. Health checks must trigger an actual bounded recovery attempt and retain a user-action fallback when Android blocks a background foreground-service start.
3. Native clipboard and verification queues must resume automatically after service recovery.
4. P2P restart must fully tear down the previous WebRTC session before creating a replacement.
5. Old service generations must not remove listeners belonging to a newer generation.
6. Avoid overlapping retry loops and duplicate foreground services.
7. Use bounded queues, TTL, deduplication, synchronous durable queue writes, and rotating logs.
8. Build success is not functional completion. Real-device tests are mandatory.
9. Every Android outbound validation run must disable Phone Link and all other competing clipboard synchronization features so delivery can be attributed to ClipCascade alone.
10. Validation must record capture, queue, transport, peer clipboard application, and acknowledgement as separate stages.

## Windows requirements

1. Show a visible status/control window after login, not only a tray icon.
2. Display connection status, server, P2S/P2P mode, P2P peer/transfer state, reconnect activity, and latest operation.
3. Controls: Restart sync, Reconnect, Disconnect, Open logs, Copy diagnostics.
4. Second app launch should focus/open the existing status window.
5. Watchdog should recover persistent unhealthy sessions, including signaling-connected but DataChannel-dead P2P states.
6. Logs must rotate rather than truncate on each launch.
7. Extended P2P Windows must recognize ACK control envelopes and ACK Android only after validated text clipboard application.
8. Windows executable packaging must be repeatable in CI.
9. Normal short ICE negotiation must not be reported as a persistent unhealthy connection.

## Settings and localization requirements

The Android app must provide an always-reachable settings screen containing:

- A guided setup checklist that opens the next missing Android setting
- Android 13+ runtime notification permission state and request
- AccessibilityService state and a route to accessibility settings
- Notification-listener state and a route to notification access settings
- Battery-optimization exemption state and request
- Clear HONOR/MagicOS guidance for auto-launch, secondary launch, and background execution
- Honest manual confirmation for manufacturer-specific switches that Android cannot reliably query through a common API
- Clipboard relay master switch
- Verification-code relay master switch
- Notification-source app picker and reset-to-all action
- Synthetic end-to-end verification-code test and live status
- Pending queue counts without displaying contents
- Clear-pending-data action
- Test text relay action with target-neutral wording
- Content-free recent health diagnostics and clear action
- Clear explanation of what data is read, persisted, and sent
- No obsolete ADB, READ_LOGS, or overlay setup instructions in the distributed UI

Localization:

1. The distributed Android UI must support Japanese and English and default from the Android system locale.
2. Native settings, service labels/descriptions, the persistent settings entry, and the Extended React Native screens must not be left as a mixture of untranslated Japanese and English.
3. Protocol strings and diagnostic identifiers may remain stable internal English tokens; user-visible labels must be localized.

## Security and privacy boundaries

1. Never commit credentials, keys, tokens, private server URLs, or real notification/clipboard contents.
2. Never log full notification bodies, verification values, or clipboard text in normal logs or diagnostics.
3. Full notification text stays transiently on device; only the extracted value may be queued.
4. Notification titles are not needed for delivery and must not be persisted.
5. Clipboard contents are inherently sensitive. Settings must make automatic relay explicit and disableable.
6. Turning a relay off must stop future dispatch and clear pending sensitive values for that relay.
7. Diagnostics may store only non-content state such as timestamp, trigger category, capture path, and result.
8. Synthetic test codes may be shown in the settings screen solely for the active test; they must be clearly described as fake and must expire with the normal verification queue.
9. Do not weaken Android security settings or attempt to bypass OS restrictions.

## Definition of done

The project is not complete until all of the following are true:

- Android and Windows CI pass.
- Standalone APK installs and launches without Metro.
- A Japanese-locale device shows Japanese UI and an English-locale device shows English UI for the Extended paths.
- Guided setup can reach notification permission, Accessibility, notification access, battery settings, and app background settings without ADB.
- The synthetic test notification is detected, extracted, queued, transported, and acknowledged through the normal path.
- Accessibility settings and notification settings can be opened from the app.
- Test relay reaches connected peers in P2S and P2P.
- Text copied in a representative app matrix reaches Windows without ADB.
- Android outbound works with ClipCascade backgrounded, removed from recents, locked, and screen-off while every competing clipboard synchronizer is disabled.
- Offline queue survives disconnect and process restart.
- P2P Extended delivery is removed after verified Windows text clipboard application.
- P2S acknowledgement limitations are explicitly tested/documented or an application receipt is implemented.
- Screen-off SMS and email verification-code cases are tested on the target HONOR device.
- Japanese and English positive/negative extraction corpora pass, including MessagingStyle layouts observed on the target apps.
- Android 16 redaction behavior is documented from actual tests.
- Device reboot and MagicOS process-kill recovery are tested.
- Upstream text/image/file paths pass regression tests.
- A tagged GitHub Release contains the tested Android and Windows artifacts.
