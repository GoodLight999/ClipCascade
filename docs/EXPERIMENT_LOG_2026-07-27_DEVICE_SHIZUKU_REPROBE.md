# Experiment Log — 2026-07-27 — Real-Device Shizuku Binder Reprobe and Setup UI Recovery

## Triggering evidence

A real-device diagnostic report was captured on:

- device: HONOR DNP-NX9;
- Android: 16 / API 36;
- app: `1.0 (standalone)`;
- server mode: P2P;
- foreground/WebSocket requested: true;
- connection status: connected.

Observed Shizuku state in the previous APK:

- manager installed: yes;
- Binder running: no;
- permission: no;
- UserService: no;
- service UID: unavailable;
- repeated Shizuku attempts: 7;
- successful Shizuku reads: 0;
- last error: `Shizuku is not running`.

The user explicitly reported that the installed manager is a Shizuku fork and that ClipCascade had never recognized it successfully.

The same real-device test also established that the native background setup screen rendered, but the previous vendor-resolved palette produced an unreadable gray-background/black-text combination. This replaces the earlier “rendering unproven” status with a concrete failed rendering observation.

`[P2S text outbox] Status: unavailable` was not a failure in this report because the active server mode was P2P. The persistent text outbox belongs only to the existing P2S transport.

## Source inspection

### Previous ClipCascade implementation

`ShizukuClipboardBridge.kt` previously:

- registered `addBinderReceivedListenerSticky` and waited for passive Binder delivery;
- called `Shizuku.pingBinder()` to decide whether the server was running;
- did not send `rikka.shizuku.intent.action.REQUEST_BINDER`;
- hardcoded the official manager package only for installation/opening status;
- sent users without the official package to `https://shizuku.rikka.app/download/`.

Therefore “manager package visible” and “Binder delivered to ClipCascade” were conflated. A compatible manager could be installed and running while ClipCascade remained permanently in the no-Binder state after an unfavorable process/start order.

### Official and fork manager behavior

The official manager and the inspected `thedjchi/Shizuku` fork both expose a public receiver for:

`rikka.shizuku.intent.action.REQUEST_BINDER`

Sources inspected:

- `https://github.com/RikkaApps/Shizuku`
- `https://github.com/RikkaApps/Shizuku-API`
- `https://github.com/thedjchi/Shizuku`

The fork also documents an optional stealth mode. A manager that deliberately hides both its receiver and launcher identity from ClipCascade cannot be reliably auto-discovered through Android package visibility; the corrected diagnostics now distinguish that state instead of merely claiming “not running.”

## Accepted correction

No transport, clipboard acquisition backend, server destination, or acknowledgement protocol was replaced.

`ShizukuClipboardBridge` now:

1. discovers managers through the standard Binder-request receiver;
2. retains the official package only as a visibility fallback;
3. recognizes visible launcher identities containing `Shizuku` or `Nightzuku` as an additional fork fallback;
4. explicitly sends a targeted `REQUEST_BINDER` broadcast to every discovered compatible manager;
5. performs this reprobe at application initialization and again when permission/binding is requested without a Binder;
6. preserves the existing Shizuku listener lifecycle and UserService;
7. reports the detected manager label, version, and package in payload-free failure text;
8. opens the detected compatible manager rather than one hardcoded package;
9. opens this recovery project's Android setup guide when no compatible manager is discoverable;
10. removes the official Shizuku download URL from the product runtime.

The Android manifest now declares package visibility for the standard Binder-request action and launcher activities.

## Setup-screen UI correction

The native setup Activity previously inherited an underspecified app-wide DayNight theme. On the HONOR device this produced a low-contrast vendor-resolved palette.

The Activity now has a dedicated theme with explicit light and dark palettes.

Light:

- surface: `#FAFAFC`;
- primary text: `#15161A`;
- secondary text: `#4E515B`;
- button: `#E8EAF0`.

Dark:

- surface: `#111318`;
- primary text: `#F2F3F7`;
- secondary text: `#C5C8D1`;
- button: `#292C35`.

Status-bar and navigation-bar icon contrast are explicitly selected for each mode. Product correctness no longer depends on HONOR's interpretation of programmatic AppCompat widgets.

## Runtime-link correction

The bundled React Native UI already used the recovery repository, but runtime `metadata.json` still exposed an upstream repository object and compatibility label.

The runtime metadata now contains only recovery-project URLs. Upstream attribution remains in repository documentation and license history, not in product navigation or update/help metadata.

Source-contract tests now fail if:

- App.js returns to upstream product URLs;
- runtime metadata contains `Sathvik-Rao` or its repository URL;
- the Shizuku bridge contains the official download URL;
- the Binder-request action/query/broadcast contract disappears;
- the native setup Activity loses its explicit high-contrast theme.

## Product changes

Product-code head:

`3ea7072231a7a3bea0a7ae4eab0c94090fe31103`

Relevant commits:

- `5706068d9d86cc68947ed5e1646c8a29ed222214` — active compatible-manager Binder reprobe;
- `9cb9548b5156febd007e09096c62cc48afe5e046` — manifest discovery and setup theme assignment;
- `d1453c7b920cb8afcda383ad137275f2fcd30b71` — explicit setup-screen theme;
- `c98d6de10a0bd981a941cacc10f06e14d1569735` — light palette;
- `57db8eacc834455ecee9e8bee609b79accdf46bd` — dark palette;
- `175f91d865d2935fc84b37135b8600763efd9685` — dark system-bar contrast;
- `3047cacb55689b7dfef31ed2590b1d65a7c27f14` — runtime upstream-link removal;
- `3ea7072231a7a3bea0a7ae4eab0c94090fe31103` — source-contract coverage.

## Verification

Android workflow:

- run: `30276002653`;
- conclusion: success;
- JavaScript: 5 suites / 38 tests passed;
- Gradle: `BUILD SUCCESSFUL in 3m 51s`;
- Android JVM, AIDL/Shizuku, Kotlin/resources, standalone APK, exact bundle, ZIP integrity, checksum, and upload passed;
- APK artifact ID: `8656949852`;
- build-log artifact ID: `8656947464`.

Desktop same-product-head workflow:

- run: `30276012794`;
- conclusion: success.

Independent APK inspection:

- file: `ClipCascade-Android-stability-standalone.apk`;
- user-facing file: `ClipCascade-Android-stability-shizuku-fork-ui.apk`;
- size: `93,619,111` bytes;
- SHA-256: `d235ab7c8de285c672cd7975ec08387ec535b2cbe03f9e68cdacf9535eff2efd`;
- APK entries: `538`;
- exact `assets/index.android.bundle`: present;
- ZIP integrity: passed;
- embedded checksum: matched independent recalculation;
- DEX contains `rikka.shizuku.intent.action.REQUEST_BINDER`, compatible-manager diagnostics, and Binder-request markers;
- resources contain `Theme.ClipCascade.BackgroundSetup` and explicit setup color resources;
- bundled runtime contains recovery-project URLs;
- `shizuku.rikka.app/download`, `https://github.com/Sathvik-Rao/ClipCascade`, and raw upstream product URLs are absent from the packaged runtime.

## What this proves

- The corrected manager discovery and active Binder-request path compile.
- The official/fork receiver protocol is represented in the packaged APK.
- Explicit light/dark setup resources are packaged.
- Runtime upstream navigation/download markers covered by this experiment are absent.
- Existing Android and desktop build gates remain green.

## What remains unproven

- Whether the user's exact fork exposes its receiver and launcher identity to ClipCascade under its current stealth settings.
- Whether the new targeted Binder request reaches that fork on the HONOR DNP-NX9.
- Whether the fork grants ClipCascade permission and starts the UserService.
- Whether the returned service UID is 2000 or another expected Shizuku identity.
- Whether the hidden clipboard read succeeds on HONOR Android 16.
- Whether the explicit palettes render correctly under HONOR light and dark mode.
- Background delivery, overlap deduplication, overlay fallback, and battery behavior after Shizuku becomes usable.

CI success is not a claim that the real-device failure is resolved.

## Exact next device test

1. Install `ClipCascade-Android-stability-shizuku-fork-ui.apk` over the previous engineering APK.
2. Start the installed Shizuku fork's server.
3. Ensure any fork stealth/application-hiding setting allows ClipCascade.
4. Open ClipCascade's **バックグラウンド設定**.
5. Verify that light/dark text, background, and buttons are readable.
6. Tap **状態を再確認** or **Shizuku権限を許可して接続**.
7. If Binder delivery still fails, share the new diagnostic report; its error should name the detected fork label/version/package or state that no compatible receiver is visible.
8. If Binder becomes active, grant permission, verify UserService/UID, and run the privacy-safe read test.
9. Only after a successful read test, repeat background copy and public-server acceptance tests.
