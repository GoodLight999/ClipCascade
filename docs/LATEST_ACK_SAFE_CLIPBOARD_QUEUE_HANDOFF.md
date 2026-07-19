# ACK-Safe Durable Clipboard Queue Handoff — 2026-07-19

## Static defect found

A fresh Priority 1 audit found that `ClipboardRelayStore` expired ordinary clipboard items after ten minutes by filtering them out of the persistent queue. That deletion did not require a local transport acknowledgement or a peer-applied acknowledgement.

This directly conflicted with the required behavior:

- screen-off validation includes 15 minutes and 30+ minutes;
- peer-disconnect validation requires an item to survive until reconnection;
- Extended P2P items must not be deleted before Windows applies the text and returns the peer ACK;
- `ACK前にキューを削除しない` is an explicit product requirement.

The queue was already bounded to 16 items, so the ten-minute TTL was not required to bound storage.

## Implemented repair

Implementation anchor:

- source SHA: `1e3aae70e2052420bfbcf2e326e04638787dfc1c`;
- versionName: `3.2.1-extended.15-standalone`;
- versionCode: `320119`;
- package: `com.clipcascade.extended`;
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`.

Changes:

1. Ordinary clipboard items no longer expire by wall-clock age.
2. They remain until one of the explicit lifecycle events occurs:
   - defined acknowledgement removes the relay ID;
   - user clears pending data;
   - clipboard relay is disabled and the privacy requirement clears pending values;
   - queue pressure exceeds the existing 16-item bound and the oldest item is evicted.
3. Disconnected/no-peer/no-React polling backs off from 3 seconds to 6, 12, and at most 15 seconds.
4. A new item, reconnect, recovery request, or acknowledgement resets the dispatcher and schedules an immediate attempt.
5. While an item is in flight, the dispatcher retains the 3-second cadence so the existing 15-second ACK timeout remains responsive.

## Preserved ACK path

No protocol or acknowledgement semantics were weakened.

Extended P2P remains:

`LOCAL_TRANSPORT_ACCEPTED -> PEER_RECEIVED -> PEER_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

Preserved:

- `relayId`;
- `ackRequested`;
- validation-before-ACK;
- generation-scoped old-peer fallback;
- native relay claims;
- Windows-applied acknowledgement;
- internal clipboard-write suppression;
- language-neutral Copy confirmation.

## CI, artifact, and signer verification

- Android standalone CI: `29674843116` — success;
- Desktop Windows CI: `29674843145` — success;
- Android artifact ID: `8438520128`;
- artifact ZIP SHA-256: `e3f50c87ebea56fe0039e3e08a909d282dc10631bb2dc808d6a01e86a1792e2a`;
- extracted APK SHA-256: `15ee61ad66e68f114b3a52c160773976ac705954a3a278b6b892559bae6b8ee2`;
- artifact expiry: `2026-10-17T05:26:07Z`;
- signer diagnostics independently reported the expected SHA-256 `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`.

Android CI passed the final transform chain, the language-neutral copy invariants, the no-TTL assertion, retry-policy unit tests, OTP tests, JavaScript bundle generation, Kotlin/resource compilation, APK assembly, embedded bundle verification, deterministic signer verification, and artifact upload.

Windows CI passed the retained authenticated HTTP tests, Extended P2P peer-applied ACK tests, validation-before-ACK ordering, shutdown/status tests, tray lifecycle tests, and EXE packaging.

## Trial and error record

No implementation CI failure occurred in this repair. The static defect was identified before target-device guessing. The main design tradeoff was avoiding both unsafe time-based deletion and a permanent three-second idle polling loop. The chosen 15-second cap keeps the queue durable while limiting idle wakeups and retaining prompt ACK timeout handling.

## Required real-device proof

This repair is CI-green but not target-device-proven. With Phone Link and every competing synchronizer disabled:

1. install `.15 / 320119` over the existing stable-signed build without uninstalling;
2. disconnect the Windows peer;
3. perform exactly one Android Copy;
4. leave the peer disconnected for more than ten minutes, preferably more than thirty minutes with the screen off;
5. confirm the native queue still contains one item;
6. reconnect the peer;
7. confirm exactly one Windows clipboard application;
8. confirm the queue is deleted only after peer ACK;
9. record reconnect latency and battery behavior without storing clipboard contents.

PR #1 remains Draft. Do not merge or mark ready.
