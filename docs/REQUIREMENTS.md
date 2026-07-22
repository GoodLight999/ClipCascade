# ClipCascade Extended — Canonical Requirements

This document is the source of truth for the `stability-mobile-otp` development branch.

> Privacy note: this repository is public. Never commit credentials, tokens, private server URLs, phone numbers, real email/notification text, real verification codes, clipboard contents, or other secrets.

## Product goal

Make ClipCascade dependable enough for daily Android ↔ Windows clipboard use without ADB, root, or Shizuku, including locked/screen-off delivery of verification codes where Android exposes them through an authorized notification listener.

## Target environment

- Android target: HONOR 400 Pro, Android 16 / MagicOS
- Desktop target: Windows 11
- Existing ClipCascade modes that must remain supported: P2S and P2P
- Existing text, image, and file sharing must not regress

## Naming and distribution

1. Do not put the user's handle or personal name in the app name, package name, artifact name, version string, UI, logs, or release title.
2. Main app identity: `ClipCascade Extended`.
3. Main Android package: `com.clipcascade.extended`.
4. Listener-test helper package: `com.clipcascade.extended.testnotifier`.
5. The main APK must be directly installable and contain `index.android.bundle`; Metro must not be required.
6. Alpha packages that test the real notification-listener path must distribute both the main APK and the separately installable helper APK.
7. Main and helper APKs must use the same stable test signing certificate so the helper can be protected by a signature permission.
8. GitHub Actions and GitHub Release packaging must be repeatable.

## Android clipboard requirements

1. Ordinary Android text copy must be relayed to Windows without ADB, root, Shizuku, or `READ_LOGS`.
2. Accessibility may detect user-interaction candidates, but it must not own the durable send decision.
3. A dedicated native foreground service must own background overlay creation, `ClipboardManager` reading, clipboard-mutation proof, and durable queue insertion.
4. The native acquisition service must be independent of the Activity, started and bound from Accessibility, and return `START_STICKY`.
5. The implementation may accept broad candidate events matching the known-working Go reference, including selection changes, generic clicks, announcements, notification-state changes, semantic Copy actions, exact framework-localized Copy labels, and Ctrl+C.
6. Broad candidate events are probes only. They must never be treated as proof that a Copy occurred.
7. Selection alone must never queue or send. A selection probe must establish the old clipboard fingerprint before the delayed read, and only a changed fingerprint may become a candidate item.
8. Only a one-way fingerprint may be persisted for mutation proof; clipboard contents must not be persisted in diagnostics.
9. The known-working Go Android implementation is the reference for background clipboard acquisition: Accessibility binds a sticky native foreground service; weak events are delayed; the service adds a transparent 1×1 application overlay before reading the clipboard and removes it immediately afterward.
10. `SYSTEM_ALERT_WINDOW` / Display over other apps may be used only with explicit user authorization and an explicit settings switch.
11. The overlay must be fully transparent, 1×1, non-touchable, non-touch-modal, intentionally not `FLAG_NOT_FOCUSABLE`, and removed immediately after every read attempt, including failures.
12. Disabling the overlay path must remain possible, but overlay-free mode must not be described as equally reliable on Android 10+ or HONOR/MagicOS unless target testing proves it.
13. The native acquisition service must insert into the existing `ClipboardRelayStore`; it must not send directly, acknowledge delivery, or delete queue items.
14. Copy capture must survive temporary network disconnects by using the persistent queue.
15. A copied item must not be removed merely because a React Native event was emitted or a local transport write succeeded.
16. Extended P2P clients must remove an item only after a peer confirms that the Windows text clipboard was validated and applied.
17. Older-peer compatibility may use only the documented bounded generation-scoped fallback.
18. P2S must not be represented as peer-applied until the protocol provides an application receipt.
19. Pending queue processing must resume after process restart, boot recovery, foreground-service restart, and network recovery.
20. Duplicate suppression must avoid repeat sends from multiple probes for one clipboard mutation.
21. Ordinary clipboard queue capacity remains 16 with explicit `queue_full`; accepted items have no TTL and must never be evicted on overflow.
22. Internal ClipCascade clipboard writes must update/suppress the observation baseline and must not echo outward.
23. Image/file sharing must continue using upstream paths unless separately redesigned and tested.
24. Universal app compatibility must not be claimed without a representative app matrix.

## Verification-code notification requirements

1. ClipCascade must use Android's user-authorized notification access and process notification text locally.
2. Only an extracted verification value may enter the relay queue. Full notification title/body must not be sent to Windows or persisted.
3. Extraction must require verification-code context and reject unrelated dates, times, prices, phone numbers, tracking/order identifiers, URLs, email addresses, and ordinary notification numbers.
4. Extraction must support Japanese and English verification language, full-width characters, numeric codes, compact alphanumeric codes, and commonly grouped code formats.
5. Standard notification fields, expanded text, text lines, MessagingStyle current/historic bundles, and safely bounded loose text extras must be considered without persisting the combined text.
6. The user must be able to enable/disable verification-code relay.
7. The user must be able to restrict source apps; an empty selection means all non-self apps remain eligible subject to contextual extraction.
8. Verification values use their separate persistent queue with expiry, duplicate suppression, and receipt protection.
9. Turning the feature off or changing source policy must clear pending values created under the previous policy.
10. Android 15+ OTP redaction is an architectural constraint, not an extractor defect.
11. On Android 15+, setup must establish a user-confirmed CompanionDeviceManager association before real OTP delivery is described as configured.
12. After association, notification access should be requested through `CompanionDeviceManager.requestNotificationAccess` when available.
13. Companion association is not itself proof that HONOR exposes unredacted Gmail/SMS fields; target evidence remains mandatory.
14. `NotificationListenerService` must not be unnecessarily exported.
15. The deterministic component/transport test may queue after local extraction, but must be labelled as not proving NotificationListener delivery.
16. The true listener-path test must use a different package from the main app and must not directly insert its value into the queue.
17. The helper test app must be separately installable, signed with the same certificate, protected by a signature permission, and post a normal external notification containing a newly generated fake code.
18. True listener-test success requires: external notification posted → listener seen → eligible → text available → expected value extracted → durable queue → foreground claim → Windows apply → peer ACK → native deletion.
19. Test status must distinguish posted/launched, seen, eligible, text availability, auth context, extraction failure/redaction, queued, deduplicated, claimed, and acknowledged where available.
20. In Extended P2P, acknowledged may be described as Windows clipboard-applied. P2S must retain its local-transport limitation.
21. Locked and screen-off behavior must be tested on HONOR/MagicOS.
22. Direct SMS permission handling must not be added unless explicitly approved and designed with distribution/privacy constraints.

## Reliability requirements

1. Android foreground synchronization must recover after sleep, process termination, connectivity changes, and reboot where Android permits.
2. Health checks must trigger a bounded recovery attempt and retain a user-action fallback when Android blocks service starts.
3. Native clipboard and verification queues must resume after service recovery.
4. The native clipboard acquisition service and the existing Notifee transport runner must expose separate content-free lifecycle diagnostics.
5. P2P restart must tear down the previous WebRTC session before creating a replacement.
6. Old service generations must not remove listeners belonging to a newer generation.
7. Avoid overlapping retry loops and duplicate foreground services.
8. Build success is not functional completion. Real-device tests are mandatory.
9. Every Android outbound test must disable Phone Link and all competing clipboard synchronizers.
10. Validation must record detection/probe, baseline/fingerprint decision, acquisition method, queue, foreground claim, local transport acceptance/debug, Windows application, peer ACK, and native deletion separately.
11. Notification validation must separately record companion association, listener connected, seen, eligible, text length, auth context, extraction, queue, claim, Windows application, ACK, and deletion.
12. Failed CI and failed target runs must remain documented.

## Windows requirements

1. Show a visible status/control window after login, not only a tray icon.
2. Display connection status, server, P2S/P2P mode, peer/transfer state, reconnect activity, and latest operation.
3. Controls: Restart sync, Reconnect, Disconnect, Open logs, Copy diagnostics.
4. Second launch should focus/open the existing status window.
5. Watchdog should recover persistent unhealthy sessions, including signaling-connected but DataChannel-dead P2P states.
6. Logs must rotate rather than truncate on each launch.
7. Extended P2P Windows must recognize ACK control envelopes and ACK Android only after validated text clipboard application.
8. Windows executable packaging must be repeatable in CI.
9. Normal short ICE negotiation must not be reported as a persistent unhealthy connection.

## Settings and localization requirements

The Android settings screen must contain:

- guided setup checklist
- Android 13+ notification permission
- Accessibility state and settings route
- reliable-background overlay switch
- Display over other apps state and route
- honest explanation of the transparent 1×1 temporary overlay
- native acquisition-service state/bind diagnostics
- CompanionDeviceManager association status and action on Android 15+
- honest explanation that Android 15+ may redact OTP content without trusted association
- notification-listener state and access route
- battery-optimization state/request
- HONOR/MagicOS auto-launch, secondary-launch, and background-execution guidance
- clipboard and verification relay switches
- notification-source app picker/reset
- deterministic component/transport test
- external-package listener-path test and helper-install requirement
- pending queue counts without displaying values
- clear-pending-data action
- content-free health diagnostics and clear action
- clear explanation of data read, persisted, and sent
- no obsolete ADB or `READ_LOGS` instructions

Localization requirements:

1. Distributed main-app UI must support Japanese and English and follow system locale.
2. Helper-app user-visible permission/error text should be understandable on the target; English is acceptable only as a temporary alpha limitation if documented.
3. Native settings, service labels, descriptions, and Extended screens must not be an accidental language mixture.
4. Stable protocol and diagnostic tokens may remain English.

## Security and privacy boundaries

1. Never commit credentials, keys, tokens, private URLs, or real notification/clipboard contents.
2. Never log full notification bodies, verification values, or clipboard text in normal logs/diagnostics.
3. Full notification text remains transient on-device; only the extracted value may be queued.
4. Only a one-way clipboard fingerprint may be persisted for mutation proof.
5. Clipboard automatic relay and overlay acquisition must be explicit and disableable.
6. Turning a relay off must stop future dispatch and clear its pending sensitive values.
7. Diagnostics may store only non-content state such as timestamps, trigger category, acquisition path, fingerprint decision, and result.
8. Fake test codes may be displayed only for the active test and expire with test state.
9. The helper Activity must be callable only through the same-signature permission.
10. `SYSTEM_ALERT_WINDOW` is allowed only through explicit user authorization and the narrowly scoped behavior above.
11. The overlay must never accept touch input, display contents, or remain after capture.
12. Do not weaken Android security settings or silently bypass OS restrictions.

## Definition of done

The project is not complete until all of the following are true:

- Android and Windows CI pass on the exact final SHA.
- Main and helper APKs build, are independently hashed, and share the expected signer.
- Main APK installs in place without losing settings; helper APK installs separately.
- Main app launches without Metro.
- Japanese and English main-app UI paths are correct.
- Guided setup reaches notification permission, Accessibility, overlay permission, companion association, notification access, battery settings, and app background settings.
- Deterministic component/transport test reaches Windows, receives peer ACK, and deletes only afterward.
- External listener-path test passes the entire real listener/extractor/queue/transport/ACK path.
- Text explicitly copied in a representative app matrix reaches Windows while the main UI is closed.
- Target diagnostics show native service bound, delayed probe, changed fingerprint, overlay acquisition, queue, claim, Windows apply, ACK, and deletion.
- Selection without Copy produces no queue or Windows change.
- Background outbound works after recents removal, lock, and screen off with competing synchronizers disabled.
- Offline queue survives disconnect and process restart.
- P2P items are removed only after verified Windows clipboard application.
- Android 16 companion/redaction behavior is documented from actual Gmail/DAWN/Perceptron tests.
- Device reboot and MagicOS process-kill recovery are tested.
- Upstream text/image/file paths pass regression tests.
- A tagged prerelease contains the exact tested main APK, helper APK, Windows artifact, and checksums.
