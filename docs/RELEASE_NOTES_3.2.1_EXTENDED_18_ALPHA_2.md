# ClipCascade Extended 3.2.1-extended.18-alpha.2

This alpha targets the HONOR/MagicOS failure where inbound synchronization remains alive and Android outbound works only while the app UI is open.

## What changed

- The live Notifee foreground-service transport now pulls pending native clipboard and verification-code queue items directly.
- Background outbound no longer depends exclusively on `MainApplication.currentReactContext` receiving a `SHARED_TEXT` event.
- Verification-code items are claimed before ordinary clipboard items because they have a short expiry.
- The existing transport send functions, relay IDs, Extended P2P peer-applied ACK, native ACK deletion, queue retention, and old-peer fallback remain in place.
- The exact DAWN email shape reported on the target device is now a positive extractor regression test.
- No overlay permission, root, ADB, Shizuku, SMS permission, or Windows implementation change was added.

## Why this design

The reference implementation in `wuxinkami/ClipCascade_go_fork` keeps its synchronization engine inside a native foreground service and lets Accessibility request work from that service. ClipCascade Extended retains its existing React Native transport and ACK protocol, but adopts the same ownership principle: the runtime that actually owns the live background transport drains the durable native queues.

## Required target-device validation

1. Install over alpha.1 without uninstalling.
2. Disable Microsoft Phone Link and all competing clipboard synchronizers.
3. Confirm inbound synchronization still works.
4. Enable the outbound debug notification temporarily.
5. Background the app, copy a unique text value, and verify local transport acceptance, one Windows application, peer ACK, and native queue deletion.
6. Repeat after removing the app from recents, then while locked/screen-off where Android permits.
7. Run the listener-path synthetic OTP test and inspect content-free stage counters.
8. Test a genuine Gmail/DAWN notification when one becomes available.

CI verifies build integrity and preserved ACK source invariants. It does not prove HONOR target-device behavior, Gmail notification visibility, or background delivery.
