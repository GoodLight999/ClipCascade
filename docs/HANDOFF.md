# ClipCascade Stability Recovery Handoff

Last updated: 2026-08-02 (Asia/Tokyo)

## Read first

1. `docs/HANDOFF.md`
2. `docs/EXPERIMENT_LOG.md`
3. `docs/EXPERIMENT_LOG_2026-08-02_OFFICIAL_SHIZUKU_AND_GUI.md`
4. `docs/EXPERIMENT_LOG_2026-07-27_DEVICE_SHIZUKU_REPROBE.md`
5. `docs/EXPERIMENT_LOG_2026-07-27_SHIZUKU.md`
6. `docs/EXPERIMENT_LOG_2026-07-27_CAPTURE_PIPELINE.md`
7. `docs/EXPERIMENT_LOG_2026-07-27_P2S_OUTBOX.md`
8. `docs/EXPERIMENT_LOG_2026-07-27_P2S_RETRY_BACKOFF.md`
9. `docs/EXPERIMENT_LOG_2026-07-27_DESKTOP_RECOVERY.md`
10. Draft PR `#4` and latest Actions

## Canonical state

- Repository: `GoodLight999/Trial-and-Error-ClipCascade`
- Baseline and `main`: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Active branch: `stability-recovery`
- Draft PR: `#4`
- Latest green product-code head: `97ccb853f618ba7051c793fb35a488be9b405c68`
- Android workflow: `30749894749` — success
- Desktop workflow: `30749894743` — success

Documentation-only commits follow the product-code head. Tie executable and APK claims to `97ccb853...` until later product code is built and inspected.

## Absolute rules

- Do not reset, reconstruct, re-baseline, clean rebuild, or start a replacement project.
- Do not create a parallel Android transport, capture backend, queue owner, or synthetic diagnostic runtime.
- Keep `main` aligned with upstream; product work remains on `stability-recovery`.
- Preserve existing server destinations:
  - publish `/app/cliptext`
  - subscribe `/user/queue/cliptext`
- Root must not be required.
- Do not infer Android behavior from CI alone; preserve exact real-device evidence.
- Record failed ideas and corrections instead of silently rewriting history.
- Remove temporary write workflows and patch helpers immediately after use.
- Do not restore archived Android scaffolding from closed PR `#3`.

## Official-source architecture

### Accessibility

Accessibility is a copy-operation trigger only.

- It may receive high-confidence copy-related `AccessibilityEvent`s.
- It does not read clipboard contents.
- It does not inspect arbitrary window contents.
- Generic clicks and generic text selection are ignored.
- Event coverage depends on whether each source application emits a recognizable Accessibility event.

Primary source:

- `https://developer.android.com/reference/android/accessibilityservice/AccessibilityService`

### Ordinary clipboard listener

The upstream foreground path is restored independently of all background mechanisms.

- `ClipboardManager.OnPrimaryClipChangedListener` reads `primaryClip` while Android permits it.
- It constructs the original React Native event parameters.
- It emits `onClipboardChange` directly.
- It enters the existing `StartForegroundService.js` transport.
- It no longer passes through the background native duplicate gate.

### Shizuku

The previous manual manager-discovery and `REQUEST_BINDER` broadcast design was wrong and has been removed.

The current path follows the official Shizuku client model:

1. Manifest registers `rikka.shizuku.ShizukuProvider`.
2. The provider receives the Binder from any compatible Shizuku implementation using the official API contract.
3. `Shizuku.addBinderReceivedListenerSticky` observes Binder availability.
4. `Shizuku.requestPermission` requests the client permission.
5. `Shizuku.bindUserService` starts the read-only clipboard UserService.
6. The UserService calls explicit supported AOSP `IClipboard#getPrimaryClip` signatures.
7. Returned text is emitted to the existing React Native sender.

No Shizuku manager package name, fork label, private broadcast, manager scan, or Shizuku download URL is used.

Official sources:

- `https://github.com/RikkaApps/Shizuku-API`
- `https://github.com/RikkaApps/Shizuku-API/blob/master/demo/src/main/AndroidManifest.xml`
- `https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/clipboard/ClipboardService.java`
- `https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/os/UserHandle.java`

### AOSP clipboard signature handling

The UserService accepts only explicit known signatures:

- `(String)`;
- `(String, int userId)`;
- `(String, String attributionTag, int userId)`;
- `(String, String attributionTag, int userId, int deviceId)`.

Unknown signatures fail with their actual parameter list. It no longer selects the largest overload and invents generic arguments.

The Android user ID is derived as `Process.myUid() / 100000`, matching AOSP `UserHandle` semantics.

### Overlay and READ_LOGS

- Overlay remains the existing fallback when Shizuku is unavailable, denied, binding, failed, or returns non-text content.
- READ_LOGS remains an optional ADB-granted trigger path.
- Both paths feed the existing React Native event and transport.

## Product UI state

### Android palette

The main Android app theme and native setup screen use explicit colors.

Light:

- background `#FFFFFF`;
- primary text `#15161A`;
- secondary text `#4E515B`;
- button `#F1F3F5`.

Dark:

- background `#111318`;
- primary text `#F2F3F7`;
- secondary text `#C5C8D1`;
- button `#292C35`.

The theme explicitly controls window/background/text/control/system-bar values and disables automatic force-dark mutation.

### Runtime links

Mobile footers contain only:

- `PROJECT` — this recovery repository;
- `SETUP` — this branch's Android setup document;
- `SERVER` — the configured server on the connected screen.

Removed from product UI/runtime:

- Sathvik-Rao product URLs;
- `shizuku.rikka.app/download`;
- server-supplied `DONATE` link;
- `Linking.openURL(donateUrl)`;
- manual Shizuku `REQUEST_BINDER` action and status message.

Upstream authorship/license history remains in source documentation where appropriate; it is not a product navigation target.

## Desktop state

The desktop application keeps the existing login, transport, reconnect controller, and tray.

Windows now opens a visible Tk status window after authentication with:

- current connection state and details;
- server mode and URL;
- Connect/Reconnect;
- Disconnect;
- Open logs;
- Program files;
- Hide to tray.

The tray runs through `Icon.run_detached()` while Tk owns the visible GUI loop. Closing the window hides it to the tray. The default tray action is `Open ClipCascade`.

## Retained failed attempts

### Manual Binder reprobe

Product head `3ea70722...` scanned manager packages/labels and sent `rikka.shizuku.intent.action.REQUEST_BINDER` manually. On the real device this produced `Binder requested from ...` but no Binder. The APK also lacked the official `ShizukuProvider` registration.

This design is rejected and removed.

### Foreground send regression

The previous shared native duplicate gate was inserted in front of the ordinary foreground clipboard listener. Real-device evidence showed that copying while ClipCascade was open no longer sent.

The upstream ordinary listener is now restored as an independent path.

### Compile failure

Android run `30749600073` failed because `Process.myUserHandle().identifier` was not available to the compile SDK surface. It was replaced with the AOSP relation `Process.myUid() / 100000`; no Binder signature changed.

### One-use link patch

A one-use workflow removed the two server-supplied donation-link render blocks and deleted itself in the same bot commit. A subsequent normal user-authored test commit re-ran CI and permanently checks that those product links do not return.

### Earlier documentation-operation errors

Do not use these temporary Handoff replacement/shortening commits as continuation sources:

- `24f116b0895417b434692ca26db853b0e8bc5c51`
- `68d37b0d7030ddc82026f171439df31ab5617324`
- `47364e00c785be932fba61b52062aeddeecb8a8b`
- `5f212cdd48294f60ff9ec5d9246ffb29f2ee15b7`

## Existing P2S text reliability work

- Persistent bounded FIFO in existing AsyncStorage.
- Scope includes server/account/encryption fingerprint.
- Existing validation and encryption run before persistence.
- One in-flight head.
- Matching server echo acknowledges and removes the head.
- Own queued echo does not roll Android clipboard backward.
- Disconnect/error/shutdown/missing echo release the head.
- Restart converts persisted `inflight` to `queued`.
- Retry sequence without jitter: `30s → 60s → 120s → 240s → 480s → 600s`.
- Maximum 10 minutes; default ±20% jitter.
- `nextAttemptAt` persists across release/restart/reconnect.
- Existing UI shows queue state and retry countdown.
- P2P, images, and files do not use this durable text outbox.

## Latest verified artifacts

Product-code head: `97ccb853f618ba7051c793fb35a488be9b405c68`

### Android

- Workflow: `30749894749`
- Job: `91501945126`
- Artifact ID: `8834136426`
- Build-log artifact ID: `8834135678`
- File: `ClipCascade-Android-official-shizuku-provider.apk`
- Size: `93,619,299` bytes
- SHA-256: `b85e40021c0a3a88bfc93768a2a05d96e397e107ff6961347ec149d9554ded55`
- APK entries: `538`
- JavaScript: 5 suites / 40 tests passed
- Gradle: `BUILD SUCCESSFUL in 3m 57s`
- Gradle tasks: 505 executed
- Exact `assets/index.android.bundle`: present
- APK ZIP integrity: passed
- Embedded checksum: matched

Independent package inspection confirmed:

- binary Manifest provider `rikka.shizuku.ShizukuProvider`;
- authority `com.clipcascade.shizuku`;
- permission `android.permission.INTERACT_ACROSS_USERS_FULL`;
- official provider metadata/permission;
- DEX markers for Binder listener, `bindUserService`, UserService tag, explicit AOSP clipboard handling, ordinary listener, and `onClipboardChange`;
- existing STOMP destinations in the bundle;
- recovery repository and `PROJECT / SETUP / SERVER` labels;
- absence of the rejected Binder action/status and upstream product links in the relevant packaged runtime sections.

### Windows

- Workflow: `30749894743`
- Artifact ID: `8834105537`
- File: `ClipCascade-Windows-visible-gui.exe`
- Size: `57,490,610` bytes
- SHA-256: `49d6ab44198f0d7309658881d58751801f21275f2716bb2c2f9f34024aa56f94`
- Format: PE32+ Windows GUI x86-64
- Windows and Ubuntu tests: passed
- Embedded checksum: matched

### Linux

- Workflow: `30749894743`
- Artifact ID: `8834089832`
- File: `ClipCascade-Linux-visible-gui.tar.gz`
- Size: `61,663` bytes
- SHA-256: `5a702d369658af4a1a05e14a06a98e659f5f0642c9a301f09349b1f780942356`
- Format: gzip Unix tar
- Entries: `60`
- Integrity and embedded checksum: passed
- Packaged source contains the visible GUI entrypoints and GUI contract test.

## Established real-device facts from the previous APK

HONOR DNP-NX9 / Android 16:

- setup Activity launched;
- foreground runtime reported active;
- Accessibility and overlay enabled;
- battery exemption enabled;
- P2P connected;
- forked Shizuku manager running but Binder/permission/UserService unavailable;
- 7 attempts and 0 Shizuku successes;
- copying while ClipCascade was open did not send;
- setup/main UI retained gray/black poor contrast;
- Windows executable had no visible application window.

Do not downgrade these failures to unknown status.

## Exact next device test

1. Install `ClipCascade-Android-official-shizuku-provider.apk` over the previous engineering APK.
2. Start the installed forked Shizuku server using its supported method.
3. Open **バックグラウンド設定** and tap **状態を再確認**.
4. Confirm `Shizuku Binder` and `Shizuku起動` become enabled.
5. Tap **Shizuku権限を許可して接続** and grant permission.
6. Confirm UserService connection and UID, then run the privacy-safe read test.
7. Keep ClipCascade visible, copy fresh text, and confirm Android-to-Windows delivery through the ordinary listener.
8. Put ClipCascade in the background, perform a supported copy action, and confirm Accessibility trigger → Shizuku read → existing send path.
9. Stop Shizuku and test the existing overlay fallback separately.
10. Run `ClipCascade-Windows-visible-gui.exe` and confirm the status window opens, updates, hides to tray, and reopens from the tray.
11. Share the new diagnostic report and exact preceding action for any failed stage.

## Still requiring device/runtime evidence

Android:

- official Provider Binder delivery from the user's exact fork on HONOR Android 16;
- effect of that fork's stealth mode on the official Provider response;
- permission, UserService UID, and hidden clipboard Binder call on that ROM;
- ordinary foreground send recovery;
- Accessibility event coverage per source application;
- overlay focus behavior;
- actual light/dark appearance on HONOR;
- public-server delivery, reconnect, ordering, retry timing, and battery behavior;
- image/file behavior.

Desktop:

- visible GUI/tray behavior on the user's Windows installation;
- public-server connection and real network loss/restoration;
- seamless reauthentication and application-level acknowledgement.
