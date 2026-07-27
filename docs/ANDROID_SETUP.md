# ClipCascade Android setup and acceptance guide

Last updated: 2026-07-27 (Asia/Tokyo)

This guide applies to the `stability-recovery` engineering APK. It preserves compatibility with the existing Sathvik-Rao ClipCascade server protocol.

## Recommended setup order

1. Install the latest engineering APK.
2. Long-press the ClipCascade launcher icon and open **バックグラウンド設定**.
3. Tap **Shizukuを開く／未導入なら入手する**.
4. In Shizuku, start the service using wireless debugging or the method supported by the device.
5. Return to ClipCascade and tap **Shizuku権限を許可して接続**.
6. Confirm that the setup screen shows:
   - Shizuku installed: enabled;
   - Shizuku running: enabled;
   - Shizuku permission: enabled;
   - Shizuku read service: connected, normally UID 2000 for shell mode.
7. Tap **Shizuku読み取りをテスト**. The result reports only clipboard type and length; it never displays or stores the clipboard content.
8. Enable ClipCascade's Accessibility service. It detects only high-confidence copy actions and copied confirmations. Generic clicks and generic text selections are intentionally ignored.
9. Keep overlay permission enabled as a fallback while device testing is in progress.
10. Exempt ClipCascade and Shizuku from battery optimization when the device vendor aggressively stops background services.

## What each path does

### Ordinary listener

Used while Android allows the app process to observe the clipboard normally.

### Shizuku

Preferred background read path. Accessibility or READ_LOGS supplies a copy trigger, then a shell-identity UserService reads text and returns it to the existing React Native `onClipboardChange -> sendClipBoard` path.

The first Shizuku implementation directly returns text only. Images and files use the existing app-process fallback until URI ownership is proven separately.

### Accessibility

Trigger only. It does not collect window contents and does not own transport. It accepts explicit copy actions, copy-labelled clicks, and copied-confirmation announcements or notifications.

### Overlay fallback

Used only when Shizuku is unavailable, denied, still binding, unsupported, or returns non-text content. It now returns content through the same native emission gate as ordinary and Shizuku paths.

### READ_LOGS / guided ADB fallback

An optional trigger path retained from upstream. The setup screen can copy the required commands. Root is not required.

## Payload-free diagnostics

The setup screen records only:

- trigger count;
- coalesced trigger count;
- Shizuku attempts and successes;
- overlay fallbacks;
- events emitted to React Native;
- short-window duplicates suppressed;
- ignored or unavailable stages;
- last source, stage, error class/reason, and timestamp.

Clipboard content is not retained in the diagnostic ledger.

## Real-device acceptance matrix

Run each row separately and record the result in `docs/EXPERIMENT_LOG.md`.

| Case | Expected result |
|---|---|
| App open, ordinary text copy | One outbound send |
| App background, Shizuku running, Accessibility enabled | One outbound text send without overlay focus change |
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
- Shizuku hidden clipboard invocation is build-verified but still requires device coverage across Android/vendor versions.
- Shizuku direct output is text-only in the first implementation.
- The current server protocol has no explicit application-level delivery receipt.
- A durable outbound queue is not yet implemented.
- Build success is not device/runtime proof.
