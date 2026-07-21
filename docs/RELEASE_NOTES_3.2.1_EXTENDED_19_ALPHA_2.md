# ClipCascade Extended 3.2.1-extended.19-alpha.2

This alpha combines the `.19-alpha.1` system-localized Copy recovery with a second fix for the reported state where inbound synchronization remains alive but Android outbound works only while the app UI is open.

## Added in alpha.2

- The live Notifee foreground-service transport pulls pending native clipboard and verification-code queue items directly.
- Background outbound no longer depends exclusively on `MainApplication.currentReactContext` receiving a `SHARED_TEXT` event.
- Verification-code items are drained before ordinary clipboard text because their queue has a short expiry.
- Native in-flight claims remain bounded by the existing 15-second acknowledgement timeout.
- The existing `sendClipBoard` implementation is reused unchanged for P2S/P2P payload validation, encryption, fragmentation, and debug notification behavior.
- Extended P2P still deletes a native relay only after peer application ACK, or the retained five-second compatibility fallback for old peers.

## Retained from alpha.1

- Android framework-localized Copy/Copy URL click fallback.
- Exact matching only; selection text and approximate labels do not send.
- OS clipboard callback remains the strongest Copy proof where Android delivers it.
- No `READ_LOGS`, overlay permission, root, ADB, Shizuku, or Windows implementation change.
- DAWN, Gmail-shaped, Perceptron, Beeper, multilingual, and negative OTP extractor tests remain present.

## Reference implementation

`wuxinkami/ClipCascade_go_fork` keeps its synchronization engine inside a native Android foreground service and lets Accessibility request work from that service. Extended retains its existing React Native transport and ACK protocol, but adopts the same ownership principle: the runtime that owns the live background transport drains the durable native queues rather than waiting for a UI React context.

## Required target-device validation

1. Install over the previous build without uninstalling.
2. Disable Microsoft Phone Link and every competing clipboard synchronizer.
3. Confirm inbound synchronization still works.
4. Enable the outbound transport-accepted debug notification temporarily.
5. Background the app, copy a unique text value, and verify debug notification, one Windows application, peer ACK, and native queue deletion.
6. Repeat after removing the app from recents and while locked/screen-off where Android permits.
7. Inspect Copy detection and clipboard health rows to distinguish detection from transport failure.
8. Run the true listener-path OTP test and inspect content-free stage counters.
9. Test a genuine Gmail/DAWN notification when one becomes available.

CI proves source transforms, unit tests, Android compilation, APK signing, and retained Windows ACK tests. It does not prove HONOR/MagicOS background delivery or real Gmail notification visibility.
