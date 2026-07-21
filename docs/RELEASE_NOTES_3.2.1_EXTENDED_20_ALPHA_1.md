# ClipCascade Extended 3.2.1-extended.20-alpha.1

This alpha follows a failed HONOR/MagicOS validation of `.19-alpha.2`:

- the real NotificationListener self-test did not complete;
- Android outbound still failed whenever the main app UI was not open.

`.19-alpha.2` therefore did not prove or restore background outbound.

## Confirmed lifecycle defect addressed

Notifee requires the foreground-service runner to be registered outside React components and as early as possible from the JavaScript entry point. The previous implementation registered `notifee.registerForegroundService(...)` only when UI/recovery code called `StartForegroundService()`.

After Android recreated the process, the native foreground-service shell could exist without the JavaScript transport runner having been registered. Inbound and stale status state could therefore be misleading while native queues had no live runner to drain them.

Alpha.20:

- registers the Notifee runner from `index.js` before `AppRegistry.registerComponent`;
- guards registration so one process generation registers exactly once;
- separates registration-only from notification/service start;
- records content-free runner registered/start/heartbeat/stop state;
- sends a heartbeat every 15 seconds and treats 45 seconds as stale;
- shows runner heartbeat state in Extended settings;
- has component and listener-path tests request bounded recovery when the runner is stale.

## Listener-path self-test recovery

- the listener test notification remains active for five minutes;
- rebind is followed by delayed active-notification scans at 0.5, 2, and 5 seconds;
- the value is still never inserted directly by the listener-path test;
- normal NotificationListener, extraction, persistent queue, transport, peer ACK, and deletion remain required.

## Copy cue coverage

The exact Android-framework-localized Copy rule is retained, but candidates now include:

- the clicked Accessibility node;
- its parent;
- immediate children and siblings;
- Accessibility action labels.

Matching remains exact against Android's active-locale `copy` and `copyUrl` strings. Selection alone remains inert.

## Fork relationship

`wuxinkami/ClipCascade_go_fork` owns its Go connection in a sticky native foreground service and binds Accessibility directly to it. It also relies on a transparent overlay for background clipboard reads.

Extended retains P2P and Windows-applied ACK, so it does not replace the transport with the fork's P2S-only Go engine. It adopts the same lifecycle requirement—background transport ownership must survive without an activity—but does not add the fork's overlay, `SYSTEM_ALERT_WINDOW`, ADB, root, or Shizuku.

## Preserved invariants

- no selection-only send;
- internal-write echo suppression;
- no ordinary queue TTL or accepted-item eviction;
- queue capacity 16 with explicit `queue_full` rejection;
- existing P2S local-acceptance semantics;
- Extended P2P Windows application before peer ACK;
- peer ACK before native deletion;
- validation-before-ACK;
- relay IDs, bounded native claims, and old-peer fallback;
- debug notification remains default OFF and cannot affect ACK/deletion.

## Required HONOR validation

1. Install in place without uninstalling.
2. Disable Phone Link and every competing clipboard synchronizer.
3. Open Extended settings and verify `Foreground transport runner: active`.
4. Run the deterministic component/transport test first.
5. Run the real listener-path test and record listener counters separately.
6. Leave the main app UI, perform a unique explicit Copy, and record Copy detection, queue, foreground claim, debug notice, Windows application, ACK, and deletion.
7. Only after basic success test removed-from-recents, lock, and screen-off.

CI proves registration order, transforms, compilation, tests, APK signing, and retained Windows ACK tests. It does not prove HONOR behavior.
