# Experiment Log — 2026-08-02 — Official Shizuku Client Path, Foreground Send Recovery, Product UI, and Visible Desktop GUI

## Triggering real-device evidence

The prior engineering APK failed on HONOR DNP-NX9 / Android 16:

- a forked Shizuku manager was running, but ClipCascade never obtained the Binder;
- the screen reported `Binder requested from ...` indefinitely;
- text copied while ClipCascade was open no longer sent;
- the product still exposed upstream-linked UI;
- the main/setup UI still rendered gray with black or otherwise poor-contrast text;
- the Windows executable ran without a visible application window.

These results invalidated the previous Shizuku manager-discovery/reprobe design and the claim that the UI corrections were effective.

## Official-source boundary

Implementation decisions were restricted to the following primary sources:

- Android AccessibilityService API:
  - `https://developer.android.com/reference/android/accessibilityservice/AccessibilityService`
  - Accessibility supplies events/actions; it does not provide a supported cross-app clipboard-content API.
- Android 10 clipboard privacy change:
  - `https://developer.android.com/about/versions/10/privacy/changes#clipboard-data`
  - a background app that is neither focused nor the default IME cannot use the ordinary clipboard API as a dependable background read path.
- Shizuku official API and demo:
  - `https://github.com/RikkaApps/Shizuku-API`
  - `https://github.com/RikkaApps/Shizuku-API/blob/master/demo/src/main/AndroidManifest.xml`
  - the client app registers `rikka.shizuku.ShizukuProvider`, listens for Binder delivery, requests permission, and starts a UserService.
- AOSP clipboard service and user-ID model:
  - `https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/clipboard/ClipboardService.java`
  - `https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/os/UserHandle.java`
  - Android 14+ `IClipboard#getPrimaryClip` uses package, attribution tag, user ID, and device ID; AOSP derives user ID as UID divided by `PER_USER_RANGE` (`100000`).

## Rejected implementation

Product head `3ea7072231a7a3bea0a7ae4eab0c94090fe31103` had introduced:

- package/label scanning for official and forked Shizuku managers;
- a hand-written `rikka.shizuku.intent.action.REQUEST_BINDER` broadcast;
- a status message claiming a Binder request had been sent;
- no explicit `rikka.shizuku.ShizukuProvider` in the application manifest;
- ordinary foreground clipboard events routed through a new shared native duplicate gate;
- UI checks that verified source strings but did not prove the actual product screen had readable defaults.

The manual Binder broadcast was not the official Shizuku client integration. It was removed completely.

## Accepted architecture

### Accessibility

Accessibility remains a high-confidence copy-operation trigger only.

It does not read clipboard contents and does not inspect arbitrary window contents. After a recognized copy event settles, it requests the shared background capture coordinator.

### Shizuku

Clipboard content is read through the official Shizuku client lifecycle:

1. `rikka.shizuku.ShizukuProvider` receives the Binder;
2. `Shizuku.addBinderReceivedListenerSticky` observes Binder availability;
3. `Shizuku.requestPermission` handles the runtime permission;
4. `Shizuku.bindUserService` starts a read-only UserService;
5. the UserService executes with the Shizuku identity and calls the AOSP clipboard Binder interface;
6. the returned text enters the existing React Native `onClipboardChange` event and existing transport.

No manager package name, fork label, private broadcast, download URL, or manager-specific protocol is used.

### Ordinary foreground clipboard path

The original `ClipboardManager.OnPrimaryClipChangedListener` path was restored independently of background capture.

An ordinary foreground copy now:

1. reads `ClipboardManager.primaryClip` using the upstream path;
2. constructs the original React Native event parameters;
3. emits `onClipboardChange` directly;
4. reaches the existing `StartForegroundService.js` sender.

Shizuku and overlay results use a separate external-emission entrypoint. They no longer interpose a second native duplicate gate in front of the ordinary listener.

### AOSP method selection

The UserService no longer chooses the `getPrimaryClip` overload with the largest parameter count and invents generic arguments.

It accepts only explicit known AOSP signatures:

- `(String)`;
- `(String, int userId)`;
- `(String, String attributionTag, int userId)`;
- `(String, String attributionTag, int userId, int deviceId)`.

Unknown signatures return a concrete unsupported-signature error. The user ID is derived as `Process.myUid() / 100000` following AOSP `UserHandle` semantics.

## Product UI correction

The main Android theme and native setup theme now share explicit light/dark defaults.

Light:

- surface `#FFFFFF`;
- primary text `#15161A`;
- secondary text `#4E515B`;
- button `#F1F3F5`.

Dark:

- surface `#111318`;
- primary text `#F2F3F7`;
- secondary text `#C5C8D1`;
- button `#292C35`.

The app theme explicitly sets window/background/text/control/system-bar values and disables automatic force-dark mutation.

The setup copy now states the actual division of responsibility:

- Accessibility detects copy operations;
- Shizuku UserService or the overlay reads content;
- the existing foreground service sends it.

## Runtime-link correction

The two mobile footers now contain only:

- `PROJECT` — this recovery repository;
- `SETUP` — this recovery branch's Android setup document;
- `SERVER` — the configured server, on the connected screen.

The product UI no longer renders a server-supplied `DONATE` link. This mattered because a server metadata response could still lead to the upstream project even after hardcoded repository constants had been changed.

The one-use patch workflow removed itself in the same bot commit. A normal user-authored source-contract commit then re-ran CI and permanently rejects:

- `Linking.openURL(donateUrl)`;
- a `DONATE` footer label;
- Sathvik-Rao product URLs;
- the official Shizuku download URL;
- the manual `REQUEST_BINDER` action.

## Windows visible GUI

The existing desktop login, connection controller, tray, and transport remain in place.

After authentication, Windows now opens a visible Tk status window containing:

- connection state and details;
- server mode and URL;
- Connect/Reconnect;
- Disconnect;
- Open logs;
- Program files;
- Hide to tray.

The tray remains active through `Icon.run_detached()` while Tk owns the GUI event loop. Closing the window hides it to the tray. The tray menu has an `Open ClipCascade` default action.

## Failed build retained

Initial AOSP user-ID code referenced `Process.myUserHandle().identifier`, which was not available to this compile SDK surface. Android run `30749600073` failed at that single Kotlin line.

The correction uses the documented AOSP relation `userId = uid / 100000`. No Binder signature or clipboard argument semantics changed.

## Final verified product head

`97ccb853f618ba7051c793fb35a488be9b405c68`

### Android

- workflow `30749894749` — success;
- job `91501945126` — success;
- artifact ID `8834136426`;
- build-log artifact ID `8834135678`;
- JavaScript: 5 suites / 40 tests passed;
- Gradle: `BUILD SUCCESSFUL in 3m 57s`;
- Gradle tasks: 505 executed;
- APK file: `ClipCascade-Android-official-shizuku-provider.apk`;
- APK size: `93,619,299` bytes;
- APK SHA-256: `b85e40021c0a3a88bfc93768a2a05d96e397e107ff6961347ec149d9554ded55`;
- APK entries: `538`;
- exact `assets/index.android.bundle`: present;
- APK archive integrity: passed;
- embedded checksum: matched independent calculation.

Independent APK inspection confirmed:

- binary Manifest provider `rikka.shizuku.ShizukuProvider`;
- authority `com.clipcascade.shizuku`;
- permission `android.permission.INTERACT_ACROSS_USERS_FULL`;
- `moe.shizuku.manager.permission.API_V23` and `moe.shizuku.client.V3_SUPPORT` from the official provider dependency;
- DEX markers for `addBinderReceivedListenerSticky`, `bindUserService`, `clipcascade-clipboard-read-v2`, explicit `IClipboard#getPrimaryClip` handling, `emitOrdinaryClipboard`, and `onClipboardChange`;
- bundle markers for the existing STOMP destinations and recovery repository;
- theme/resource names for the setup surface and text palette;
- absence of `Sathvik-Rao`, `shizuku.rikka.app/download`, `rikka.shizuku.intent.action.REQUEST_BINDER`, `Binder requested from`, `Linking.openURL(donateUrl)`, and `DONATE` in the relevant packaged runtime sections.

### Desktop

- workflow `30749894743` — success;
- Windows and Ubuntu unit/contract tests: success;
- Windows artifact ID `8834105537`;
- Windows file: `ClipCascade-Windows-visible-gui.exe`;
- Windows size: `57,490,610` bytes;
- Windows SHA-256: `49d6ab44198f0d7309658881d58751801f21275f2716bb2c2f9f34024aa56f94`;
- Windows format: PE32+ GUI x86-64;
- Linux artifact ID `8834089832`;
- Linux file: `ClipCascade-Linux-visible-gui.tar.gz`;
- Linux size: `61,663` bytes;
- Linux SHA-256: `5a702d369658af4a1a05e14a06a98e659f5f0642c9a301f09349b1f780942356`;
- Linux tar entries: `60`;
- archive integrity and embedded checksums: passed.

The packaged Linux source independently contains the visible GUI entrypoints and `test_windows_gui_contract.py`.

## Exact real-device test

1. Install `ClipCascade-Android-official-shizuku-provider.apk` over the previous engineering build.
2. Start the installed forked Shizuku server using its supported method.
3. Open ClipCascade's background setup screen and tap status refresh.
4. Confirm `Shizuku Binder`/`Shizuku running` becomes enabled.
5. Request permission and confirm UserService binding and UID.
6. Run the privacy-safe Shizuku read test.
7. With ClipCascade visible, copy fresh text and confirm ordinary Android-to-Windows delivery.
8. Put ClipCascade in the background, perform a copy action that emits a supported Accessibility event, and confirm Shizuku read plus delivery.
9. Stop Shizuku and separately test the existing overlay fallback.
10. If any stage fails, share the new diagnostic report and the exact preceding action.

## Runtime questions left to device evidence

- Binder delivery from the user's exact fork on HONOR Android 16;
- whether its stealth mode interferes with the official Provider response;
- UserService UID and hidden Binder clipboard read on that ROM;
- ordinary foreground send recovery on the device;
- Accessibility event coverage per application;
- overlay fallback behavior and focus interference;
- actual light/dark rendering on HONOR;
- public-server delivery, reconnect, retry, ordering, and battery behavior;
- Windows GUI/tray behavior on the user's Windows installation.
