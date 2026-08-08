# ClipCascade Android setup and acceptance guide

Last updated: 2026-08-08 (Asia/Tokyo)

This guide applies to the `stability-recovery` engineering APK. It preserves the existing ClipCascade server protocol and STOMP destinations, uses debug signing, and does not require root.

## Recommended setup order

1. Install the latest engineering APK over the previous engineering build.
2. Open ClipCascade and complete the existing server/login setup.
3. Long-press the launcher icon and open **バックグラウンド設定**.
4. Start the installed official or compatible forked Shizuku server using the method supported by that manager and device.
5. Return to ClipCascade, refresh the setup status, and confirm that the Shizuku Binder is available.
6. Tap **Shizuku権限を許可して接続** and approve the request in the installed manager.
7. Confirm that the setup screen reports:
   - Shizuku Binder: available;
   - Shizuku permission: granted;
   - Shizuku read service: connected, normally UID 2000 in shell mode;
   - ClipCascade runtime: active while the foreground service is running.
8. Tap **Shizuku読み取りをテスト**. The result reports only clipboard type and length; it does not display clipboard content.
9. Enable ClipCascade's Accessibility service. Android can bind it because the service is exported while protected by `android.permission.BIND_ACCESSIBILITY_SERVICE`.
10. Keep overlay permission enabled for the fallback path.
11. On ROMs that aggressively stop background services, open the system battery-optimization settings and exempt ClipCascade and the selected Shizuku manager manually.

If updating from an earlier 3.2.0 engineering APK, open/refresh ClipCascade after installation so the Shizuku UserService is rebound. The current build uses a dedicated UserService implementation version; it is intentionally independent of the unchanged application versionCode.

## One automatic capture coordinator

Foreground and background automatic copies do not use separate payload-reading implementations.

```text
ClipboardManager change notification
Accessibility exact ACTION_COPY
optional READ_LOGS trigger
        ↓
BackgroundClipboardCapture
        ↓
Shizuku UserService read
        ↓ unavailable / denied / binding / failed / non-text
overlay fallback read
        ↓
onClipboardChange
        ↓
existing StartForegroundService.js transport
```

The ordinary `ClipboardManager` listener is a trigger only. It does not read `primaryClip` and send it through a privileged foreground-only shortcut.

## Shizuku

ClipCascade uses the official Shizuku client lifecycle:

- `rikka.shizuku.ShizukuProvider` in the manifest;
- sticky Binder-received listener;
- Binder-dead listener;
- official permission request;
- `Shizuku.bindUserService`;
- explicit UserService disconnect, binding-death, and null-binding handling;
- a dedicated UserService implementation version so changed privileged code replaces an already-running older service even when the app versionCode is unchanged.

It does **not**:

- scan manager package names or launcher labels;
- identify forks by product text;
- send `rikka.shizuku.intent.action.REQUEST_BINDER` manually;
- use a private manager broadcast;
- open a hard-coded Shizuku download URL;
- directly reflect or invoke hidden `android.content.IClipboard` from the UserService;
- guess OEM Binder arguments or select hidden overloads by shape.

### Clipboard read boundary

The privileged UserService delegates clipboard Binder details to the framework installed on the device:

```text
Shizuku v13 supplied Context
    -> verify Android user
    -> create same-user `com.android.shell` package Context
    -> get framework ClipboardManager
    -> ClipboardManager.primaryClip
```

This is deliberate. A 2026-08-08 HONOR DNP-NX9 / Android 16 test proved that the device exposes a vendor-extended hidden `IClipboard#getPrimaryClip(String, String, int, int, String)` method while current AOSP uses four parameters. The previous exact-AOSP-signature reflection therefore failed with 3 attempts / 0 successes.

The current implementation does not infer the meaning of HONOR's fifth `String`. The device's own framework is responsible for its private Binder ABI.

The Shizuku shell UserService normally runs as UID 2000. The shell package Context is used so clipboard package identity matches the shell privilege model. Direct Shizuku output remains text-only; images and files fall back to the app-process overlay path.

Shizuku's official guide warns that UserService Context is not identical to a normal application process Context. Therefore compilation and CI are not sufficient: the framework `ClipboardManager.primaryClip` call must still be proven on the target ROM with **Shizuku読み取りをテスト**.

## Accessibility

Accessibility is a copy-operation trigger only.

The service reacts only when Android reports `AccessibilityEvent.action == AccessibilityNodeInfo.ACTION_COPY`. It does not infer copying from:

- translated phrases such as “copied” or “コピーしました”;
- button labels or content descriptions;
- generic clicks;
- generic text selection;
- arbitrary window content;
- notification or toast text.

## Overlay fallback

The overlay is used when Shizuku cannot complete the read or the content requires the existing app-process URI path.

The capture coordinator remains active until the overlay performs its platform clipboard read and is destroyed. A listener and Accessibility trigger for the same copy therefore cannot launch concurrent overlays.

Pending triggers are compared with the actual monotonic read-completion timestamp. A trigger already covered by the completed read is discarded; a trigger that arrived after the read is processed next. There is no arbitrary debounce or duplicate time window.

## App-owned clipboard writes

Text and image content written to the local clipboard by ClipCascade itself carries an explicit `ClipDescription.extras` marker. This covers:

- received P2S/P2P content;
- content shared into ClipCascade;
- setup commands copied by ClipCascade.

The automatic listener ignores this marker, preventing resend loops without relying on hashes, delays, event ordering, or a “block once” flag.

## Shared-content handoff

Android share intents are placed in a bounded native queue. React Native receives only a wake signal and atomically drains the queue after the foreground-service transport is ready. This prevents cold-start shares from being emitted before JavaScript listeners exist.

The queue is process-local and bounded to 64 events. If that bound is reached, the oldest pending share is discarded and a warning is logged. It is not a durable cross-process outbox.

## READ_LOGS / guided ADB fallback

READ_LOGS remains an optional trigger path inherited from the existing application. The setup screen can copy the required commands. Root is not required. The copied setup commands are explicitly marked as application-owned so they are not synchronized.

## Payload-free diagnostics

The setup screen records only operational metadata:

- trigger and coalesced-trigger counts;
- Shizuku attempts and successes;
- overlay fallbacks;
- events emitted to React Native;
- ignored or unavailable stages;
- last source, stage, error class/reason, and timestamp;
- P2S text-outbox count/state/attempt/deadline metadata when server mode is P2S.

Clipboard content is not retained in the diagnostic ledger.

`[P2S text outbox] Status: unavailable` is expected in P2P mode. The persistent text outbox belongs only to the existing P2S transport.

## Real-device acceptance matrix

Run each case separately and record the exact preceding action and resulting diagnostic report.

| Case | Expected result |
|---|---|
| Manual **Shizuku読み取りをテスト** after installing the latest APK | `Shizuku successes` increments; no hidden-signature error |
| App visible, fresh text copied in another app | Unified coordinator runs and exactly one outbound send occurs |
| App background, Shizuku running, Accessibility enabled | `ACTION_COPY → Shizuku read → existing sender`, without overlay focus change |
| ClipCascade starts after the manager | Official Provider/listener lifecycle receives the Binder or reports the actual failure |
| Shizuku stopped, overlay enabled | One overlay fallback and one outbound send |
| Shizuku denied, overlay disabled | No clipboard payload read; diagnostic reports no usable path |
| Listener and Accessibility report the same copy | One completed read/emission; the covered trigger is coalesced |
| A new copy occurs after the previous read | It is processed as a new request, even if the text is identical |
| Generic click, typing, or text selection without Copy | No Accessibility capture trigger |
| Explicit Copy action from a supported selection toolbar | One capture attempt |
| P2S/P2P content is received locally | Clipboard is updated with the app-owned marker and is not sent back |
| App receives text/image/file through Android share while cold | Native queue retains it until JavaScript transport readiness |
| Image/file copy | Existing overlay/app-process URI path; no direct Shizuku URI-transfer claim |
| Device reboot | Verify ClipCascade restart policy and restart Shizuku as required by its manager |

## Evidence to capture for a failure

Do not include clipboard content. Record:

- Android version, ROM, and device model;
- installed Shizuku manager/fork and its version;
- Shizuku Binder/permission/UserService status;
- Accessibility, overlay, READ_LOGS, and battery-exemption status;
- diagnostic counters before and after the action;
- last source, stage, and error;
- whether the UI lost focus or an overlay appeared;
- whether the server received the item;
- whether a remote device applied it;
- whether a duplicate appeared;
- the exact action immediately before failure.

## Current limitations

- The engineering APK uses debug signing.
- The framework-delegated Shizuku clipboard read is source/CI-testable but remains target-ROM runtime work until the new HONOR manual read succeeds.
- A Binder/framework transaction that never returns has no arbitrary timeout; this remains a real-device liveness test item rather than a guessed constant.
- Shizuku direct output is text-only.
- P2S text has a persistent bounded FIFO outbox; P2P, images, files, and pending Android share intents do not use that durable outbox.
- The current server protocol has no explicit remote-application delivery receipt.
- Static checks and successful CI are not runtime proof.
