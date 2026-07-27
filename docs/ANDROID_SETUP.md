# ClipCascade Android setup and acceptance guide

Last updated: 2026-07-27 (Asia/Tokyo)

This guide applies to the `stability-recovery` engineering APK. It preserves the existing ClipCascade server protocol and does not require root.

## Recommended setup order

1. Install the latest engineering APK.
2. Long-press the ClipCascade launcher icon and open **バックグラウンド設定**.
3. Tap **Shizukuを開く／未導入なら入手する**.
4. In the installed official or compatible forked Shizuku manager, start the server using wireless debugging or the method supported by the device.
5. Return to ClipCascade and tap **Shizuku権限を許可して接続**. ClipCascade actively asks every visible compatible manager to resend its Binder; it no longer waits only for passive process-start delivery.
6. Confirm that the setup screen shows:
   - Shizuku installed: enabled;
   - Shizuku running: enabled;
   - Shizuku permission: enabled;
   - Shizuku read service: connected, normally UID 2000 for shell mode.
7. Tap **Shizuku読み取りをテスト**. The result reports only clipboard type and length; it never displays or stores clipboard content.
8. Enable ClipCascade's Accessibility service. It detects only high-confidence copy actions and copied confirmations. Generic clicks and generic text selections are intentionally ignored.
9. Keep overlay permission enabled as a fallback while device testing is in progress.
10. Exempt ClipCascade and the selected Shizuku manager from battery optimization when the device vendor aggressively stops background services.

## Shizuku

ClipCascade discovers a Shizuku-compatible manager through the standard `rikka.shizuku.intent.action.REQUEST_BINDER` receiver rather than depending only on the official manager package name.

When the Binder is absent, ClipCascade explicitly sends a targeted Binder-request broadcast to each discovered compatible manager. The setup status records the detected manager label, version, and package without exposing clipboard contents.

For a fork with a stealth or application-hiding feature, configure the fork so that ClipCascade is allowed to discover and use its API. If the fork deliberately hides both its Binder receiver and launcher identity from ClipCascade, Android package visibility prevents reliable automatic discovery.

The **Shizukuを開く** action opens the detected compatible manager. If none is discoverable, it opens this recovery project's setup guide rather than directing the user to one upstream download site.

## What each path does

### Ordinary listener

Used while Android allows the app process to observe the clipboard normally.

### Shizuku

Preferred background read path. Accessibility or READ_LOGS supplies a copy trigger, then a shell-identity UserService reads text and returns it to the existing React Native `onClipboardChange -> sendClipBoard` path.

The first Shizuku implementation directly returns text only. Images and files use the existing app-process fallback until URI ownership is proven separately.

### Accessibility

Trigger only. It does not collect window contents and does not own transport. It accepts explicit copy actions, copy-labelled clicks, and copied-confirmation announcements or notifications.

### Overlay fallback

Used only when Shizuku is unavailable, denied, still binding, unsupported, or returns non-text content. It returns content through the same native emission gate as ordinary and Shizuku paths.

### READ_LOGS / guided ADB fallback

An optional trigger path retained from the existing application. The setup screen can copy the required commands. Root is not required.

## Payload-free diagnostics

The setup screen records only:

- trigger count;
- coalesced trigger count;
- Shizuku attempts and successes;
- overlay fallbacks;
- events emitted to React Native;
- short-window duplicates suppressed;
- ignored or unavailable stages;
- last source, stage, error class/reason, and timestamp;
- P2S text-outbox count/state/attempt/deadline metadata when server mode is P2S.

Clipboard content is not retained in the diagnostic ledger.

`[P2S text outbox] Status: unavailable` is expected while the application is configured for P2P mode; the durable text outbox belongs only to the existing P2S transport.

## Real-device acceptance matrix

Run each row separately and record the result in `docs/EXPERIMENT_LOG.md`.

| Case | Expected result |
|---|---|
| App open, ordinary text copy | One outbound send |
| App background, Shizuku running, Accessibility enabled | One outbound text send without overlay focus change |
| Fork manager running before ClipCascade starts | Explicit reprobe obtains the Binder or reports the exact detected manager |
| Shizuku stopped, overlay enabled | Automatic overlay fallback and one outbound send |
| Shizuku denied, overlay disabled | No focus change; diagnostic reports no usable path |
| Repeat same copy through overlapping triggers | One JS emission; duplicate/coalesced counter increases |
| Copy identical text again after a deliberate pause | Treated as a new user action |
| Amazon search field click/type | No capture trigger and no focus loss |
| Browser search field click/type | No capture trigger and no focus loss |
| Launcher drawer interaction | No dismissal or focus change |
| Generic text selection without Copy | No capture trigger |
| Explicit Copy action from selection toolbar | One capture attempt |
| Image/file copy | Existing app-process fallback; no claim of direct Shizuku URI transfer |
| Device reboot | Verify ClipCascade service policy and restart Shizuku as required by the device setup method |

## Evidence to capture for a failure

Do not include clipboard content. Record:

- Android version and device model;
- manager label/version/package shown in the Shizuku error;
- which path was enabled;
- setup-screen capability status;
- diagnostic counters before and after the action;
- last source, stage, and error;
- whether the UI lost focus, closed, or changed;
- whether the server received the item and whether a remote device applied it;
- whether duplicates appeared;
- approximate battery observation period.

## Current limitations

- Engineering APK uses debug signing.
- Fork discovery still depends on the fork exposing the standard Binder-request receiver or a recognizable launcher identity to ClipCascade.
- Shizuku hidden clipboard invocation is build-verified but still requires device coverage across Android/vendor versions.
- Shizuku direct output is text-only in the first implementation.
- P2S text has a persistent bounded FIFO outbox; P2P, images, and files do not use that outbox.
- The current server protocol has no explicit remote-application delivery receipt.
- Build success is not device/runtime proof.
