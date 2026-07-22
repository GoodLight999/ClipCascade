# Go-proven Android clipboard acquisition handoff — 2026-07-22

## Fixed repository roles

1. `Sathvik-Rao/ClipCascade` is the upstream and primary source.
2. `GoodLight999/ClipCascade`, branch `stability-mobile-otp`, is the Extended repair branch. PR #1 must remain Open and Draft.
3. `wuxinkami/ClipCascade_go_fork` is the surviving fork of a vanished Go improvement project. It is not upstream, but its Android background outbound implementation was known to work and must be treated as a successful reference implementation rather than a speculative design.

Go reference revision inspected: `0ff3ba4b28daccc1a51e7c09907792bc0f8e53a8`.

## Corrected assessment of `.20-alpha.1`

`.20-alpha.1` repaired a real transport-runner lifecycle defect by registering the Notifee foreground runner before `AppRegistry.registerComponent` and by adding runner heartbeat/staleness diagnostics.

That repair did not reproduce the Go implementation's clipboard-acquisition condition. It added no `SYSTEM_ALERT_WINDOW` permission and no temporary overlay. Therefore:

- `.20-alpha.1` has a technical basis for keeping the transport runner alive;
- `.20-alpha.1` does not yet have an adequate technical basis for claiming reliable HONOR/MagicOS background clipboard capture;
- Android/Windows CI success is not target-device proof.

## File-level Go analysis

| Concern | Go implementation | Extended before `.21` | Gap / decision |
|---|---|---|---|
| Android module | `mobile/android` | `ClipCascade_Mobile/src/android` | Different shells; compare behavior, not file names |
| Accessibility owner | `ClipCascadeAccessibilityService.kt` | `ClipboardAccessibilityService.kt` | Both are Activity-independent |
| Service start/bind | Accessibility calls `startForegroundService` then `bindService(BIND_AUTO_CREATE)` | Accessibility queues native items and asks React/Notifee recovery | `.20` improved runner recreation but did not bind clipboard acquisition to a native service |
| Foreground service | `ClipCascadeBackgroundService.kt` | Notifee `ForegroundService` + early JS runner | Go service owns its runtime directly; Extended retains JS transport to preserve its protocol/ACK path |
| Sticky restart | `onStartCommand` returns `START_STICKY` | Notifee lifecycle + boot/recovery coordinator | Different mechanism; `.20` heartbeat now diagnoses it |
| Boot/package replacement | `BootReceiver` handles `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` | Boot recovery exists; package replacement was not the clipboard-acquisition gap | Preserve Extended recovery semantics |
| Clipboard trigger | Selection/click/announcement events are debounced, then service is asked to read | Exact framework-localized Copy cues, semantic `ACTION_COPY`, OS callback; selection alone is state only | Extended detection is more conservative and must remain so |
| Clipboard read | Service adds overlay, reads `ClipboardManager.primaryClip`, removes overlay | Direct read, then selected-text fallback | This was the critical missing known-success condition |
| Overlay | Fully transparent `LinearLayout`, 1×1 pixel, `TYPE_APPLICATION_OVERLAY`, non-touchable, focusable because `FLAG_NOT_FOCUSABLE` is omitted | Explicitly prohibited | `.21` restores this only after explicit Copy and explicit permission |
| Overlay lifetime | Added immediately before read, removed immediately after read | None | `.21` uses `removeViewImmediate` in `finally` |
| Overlay visibility/input | Alpha 0, transparent, 1×1, `FLAG_NOT_TOUCHABLE`; not a persistent UI | None | `.21` preserves no-touch and immediate removal |
| Connection owner | Sticky service owns gomobile `Engine` | React/Notifee transport runner | Do not replace Extended transport ownership without target evidence |
| Go runtime init | `Bridge.newEngine` in service thread after credentials are available | React transport initialized by current app/runtime path | Not imported |
| P2P | Deliberately disabled on mobile; STOMP/P2S only | Extended supports P2P and P2S | Go choice must not be copied |
| STOMP | `/clipsocket`, subscribe `/user/queue/cliptext`, send `/app/cliptext` | Existing upstream/Extended transport | Not imported |
| Retry | Connection reconnect backoff; no durable outbound item queue | Durable native queue and retry policy | Preserve Extended |
| Dedup/echo | One in-memory `lastWrittenText` | Internal-write guard, relay IDs, queue dedup | Preserve Extended |
| Delivery proof | WebSocket write success only | Windows validates and applies, then peer ACK removes native item | Extended is strictly stronger and must remain |
| Process death | Sticky service and boot/package receiver recreate engine; outbound item itself is not durable | Native durable queue survives transport generation changes | Preserve Extended queue |
| Battery handling | Requests ignore-battery-optimizations; user setup | Extended guided HONOR/MagicOS setup | Preserve and test |

## Overlay decision

The previous absolute overlay prohibition is no longer technically defensible for reliable background capture on the known HONOR target.

Android 10+ restricts generic background clipboard reads. Accessibility can expose a Copy action or selected range, but that does not prove that the process may read the final clipboard value. The known-working Go implementation explicitly used an authorized application overlay to create an active view/focus condition before reading the clipboard.

Therefore the honest requirement is:

- overlay-disabled mode remains available and keeps the selected-text fallback;
- overlay-disabled mode is not claimed to provide Go-equivalent background capture reliability;
- reliable-background mode requires the user's `SYSTEM_ALERT_WINDOW` authorization;
- the overlay may run only after an explicit Copy cue, must be 1×1, fully transparent, non-touchable, and immediately removed;
- selection alone must never create the overlay, queue an item, or send.

## `.21-alpha.1` implementation

Identity:

- version: `3.2.1-extended.21-alpha.1-standalone`;
- versionCode: `320126`;
- intended tag: `v3.2.1-extended.21-alpha.1`.

The final production transform `prepare_overlay_clipboard_acquisition.js`:

1. declares `SYSTEM_ALERT_WINDOW`;
2. adds an overlay preference defaulting ON, with an explicit OFF mode;
3. adds permission status and a settings shortcut in English and Japanese;
4. first attempts the normal clipboard read;
5. only if the direct value is unavailable, the option is enabled, permission is granted, and an existing explicit Copy path invoked capture, adds a transparent 1×1 overlay;
6. intentionally omits `FLAG_NOT_FOCUSABLE`, while retaining `FLAG_NOT_TOUCHABLE | FLAG_NOT_TOUCH_MODAL`;
7. reads the actual clipboard and removes the view immediately in `finally`;
8. records content-free paths such as `overlay_clipboard_manager`, `overlay_permission_missing`, `overlay_clipboard_empty`, and `overlay_add_failed`;
9. feeds the resulting text into the existing `ClipboardRelayStore` without changing transport or acknowledgement code.

## ACK and queue invariants retained

The required order remains:

`NATIVE_QUEUE -> NATIVE_IN_FLIGHT -> FOREGROUND_RUNNER_CLAIM -> LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_VALIDATE -> WINDOWS_APPLY -> PEER_ACK -> NATIVE_ACK -> DELETE`

Preserve all of the following:

- no ACK before Windows clipboard application;
- no deletion after local WebSocket/DataChannel acceptance alone;
- no ordinary-item TTL;
- capacity 16 and `queue_full` without evicting accepted items;
- internal-write echo suppression;
- selection-only negative behavior;
- debug notifications default OFF and outside ACK logic;
- generation-scoped old-peer fallback.

## Required target test

1. Install over the existing signed build; do not uninstall.
2. Confirm the overlay fallback is ON and grant Display over other apps.
3. Confirm the foreground transport runner remains active with a fresh heartbeat for at least 60 seconds.
4. Run the deterministic component/transport test and verify queue, claim, debug, one Windows apply, peer ACK, and deletion.
5. Close only the main UI, explicitly press Copy, and require:
   - Copy cue;
   - `overlay_clipboard_manager`;
   - native queue;
   - foreground claim;
   - debug notification after transport acceptance;
   - one Windows apply;
   - peer ACK;
   - native deletion.
6. Select without Copy and verify no overlay/capture/queue/Windows change.
7. Repeat with overlay OFF. Treat failure there as confirmation that the non-overlay fallback is insufficient, not as an overlay regression.
8. Only after the simple row succeeds, test recents removal, lock, screen-off, disconnect durability, and queue-full behavior.

No HONOR/MagicOS success is claimed until these rows pass.
