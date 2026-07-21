# Foreground Queue Drain Handoff — 2026-07-21

## Triggering target-device evidence

The latest HONOR/MagicOS report is:

- inbound synchronization still works when the app UI is not open;
- ordinary Android outbound works only while the app UI is open;
- copying while the app UI is not open does not reach Windows;
- a genuine Gmail notification for DAWN did not relay `713642`;
- earlier reports of ordinary synchronization recovery were intermittent and must not be treated as stable background success.

This pattern separates transport ownership from receive health. It does not prove that the Copy detector is dead: an item may be detected/queued while the native dispatcher cannot reach the live background transport.

## Reference repository finding

The user found `wuxinkami/ClipCascade_go_fork`, derived from the original ClipCascade Go direction. Its Android implementation keeps the Go synchronization engine inside a native `START_STICKY` foreground service. Accessibility binds to that service and asks it to perform clipboard work. The useful principle is that the runtime owning the live transport performs outbound work independently of an activity/UI runtime.

Extended does not copy the fork's transparent overlay or add `SYSTEM_ALERT_WINDOW`. It retains the existing React Native transport and Extended ACK protocol.

## Root-cause hypothesis implemented

Before alpha.2, `ClipboardRelayDispatcher` and `OtpRelayDispatcher` queued values natively but attempted delivery by emitting `SHARED_TEXT` through `MainApplication.currentReactContext`. When that context was null, they requested React recovery and left the value queued.

The Notifee foreground service can remain alive and receive inbound traffic in a Headless React runtime even when `MainApplication.currentReactContext` is absent or is not the transport-owning runtime. This explains:

`INBOUND WORKS + UI-OPEN OUTBOUND WORKS + UI-CLOSED OUTBOUND FAILS`

The prior React bootstrap remains as a fallback. Alpha.2 adds a direct pull path from the live foreground-service runtime.

## Alpha.2 implementation

Version:

- versionName: `3.2.1-extended.19-alpha.2-standalone`
- versionCode: `320124`
- implementation anchor: `ed9c009af0fcfc238cfc6264dd4c7b85a8fe82a3`

Transform: `ClipCascade_Mobile/src/scripts/prepare_foreground_queue_drain.js`

### Native claim path

- `ClipboardRelayDispatcher.claimForForegroundService()` claims the oldest durable ordinary item using the existing `inFlightId` / `inFlightSince` state.
- `OtpRelayDispatcher.claimForForegroundService()` does the same for verification values.
- Existing 15-second native acknowledgement timeout releases a stuck claim for bounded retry.
- Disabled settings still clear the corresponding native queue exactly as the existing dispatcher does.
- No content is written to diagnostics.

### React Native bridge

`RelaySettingsModule.claimPendingForegroundRelay()` returns at most one item:

1. verification code first;
2. ordinary clipboard second.

The returned map contains the existing relay ID, text, and source class. The method does not delete or acknowledge the item.

### Foreground transport drain

The existing 3-second Notifee foreground-service poll loop calls `drainNativeRelayQueue()`.

The drain reuses final `sendClipBoard(...)` and existing ACK helpers:

- P2S: local publish acceptance -> native acknowledge/delete;
- Extended P2P: stage relay ID -> DataChannel send -> peer-applied ACK -> native acknowledge/delete;
- old/non-Extended P2P: retained five-second compatibility fallback;
- failed send: no native delete; current in-flight claim times out and retries;
- debug notification: final send wrapper still posts only after local transport acceptance when enabled.

No Windows source was changed.

## Copy detection retained from `.19-alpha.1`

The implementation also retains the Android-framework-localized Copy/Copy URL click fallback:

- exact framework label matching only;
- supports the device's active Android locale;
- selection events only remember text;
- selection alone cannot reach the fallback;
- direct OS clipboard callback remains preferred;
- no hard-coded Japanese/English menu dictionary;
- no `READ_LOGS`, overlay, root, ADB, or Shizuku.

This means alpha.2 tests both possible boundaries:

1. whether background Copy is detected and queued;
2. whether the live foreground transport drains the queue without a UI context.

## DAWN interpretation

The exact DAWN shape is covered by `extractsDawnStandaloneNumericLoginCode` and passes Android unit tests. Therefore the supplied body text is extractor-compatible.

That does not prove Gmail exposed the same text to `NotificationListenerService`. A real DAWN failure can still occur at:

- listener not connected;
- Gmail notification callback unseen;
- visible notification but zero collected text extras;
- collected preview omitted the authentication wording or code;
- extraction/receipt/queue stage;
- background transport drain stage.

Use the content-free stage counters to locate the first failing boundary. Do not loosen the extractor merely because Gmail failed.

## CI and artifact

- Android CI `29837847117`: success
- Windows CI `29837846863`: success
- Actions artifact ID `8498121042`
- Actions ZIP SHA-256 `8c20eadce450c57fadb9eab39e465332fe34f8f25f7e28d44a072162dc480db3`
- APK SHA-256 `f9f7b5fe6653beb8d0b08436657ddf719fd155e3ac9b1216b0307b7ea1cf63a7`
- signer SHA-256 `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- artifact expiry `2026-10-19T14:10:20Z`

CI proves transforms, compilation, unit tests, existing Windows ACK tests, and artifact integrity. It does not prove target-device background behavior.

## Mandatory target-device test order

1. Install alpha.2 in place; do not uninstall.
2. Disable Microsoft Phone Link and all competing clipboard sync tools.
3. Confirm inbound still works with the Android UI closed.
4. Turn outbound debug notification ON temporarily.
5. Open ClipCascade once, confirm connection, then leave the app UI.
6. Copy a unique ordinary text value.
7. Record both health rows:
   - Copy detection: trigger/path/result;
   - Clipboard capture/delivery: trigger/path/result.
8. Record whether the transport-accepted debug notification appeared.
9. Record whether Windows applied the value once.
10. Record whether the native queue deleted only after peer ACK.
11. Repeat after removal from recents.
12. Repeat locked/screen-off where Android permits.
13. Run the true listener-path OTP test and record the first failed stage.
14. Test a real Gmail/DAWN notification only when one naturally arrives.

## Failure-boundary interpretation

- no Copy detection row: Accessibility/copy cue delivery problem;
- Copy detection requested, no clipboard queue/capture result: selection/clipboard read/capture problem;
- queue/capture accepted, no `foreground_poll claimed`: foreground service/module poll problem;
- `foreground_poll claimed`, no debug notification: transport acceptance problem;
- debug notification, no Windows application: peer transport/application problem;
- Windows application, queue remains: peer ACK/native deletion problem;
- DAWN listener connected but seen count zero: Gmail callback/delivery problem;
- seen count positive and text chars zero: Gmail exposed no usable extras;
- text chars positive, auth hint zero: notification preview omitted authentication context;
- auth hint positive, no-match positive: extractor boundary;
- queued positive, no foreground claim/debug: background transport boundary.

## Preserve exactly

- PR #1 remains open and Draft;
- selection alone never sends;
- OS clipboard mutation remains the primary Copy proof;
- framework-localized click is a conservative explicit-Copy fallback;
- internal writes are suppressed;
- ordinary queue has no TTL and no overflow eviction;
- capacity remains 16 with explicit `queue_full` rejection;
- relay IDs and native in-flight claims remain intact;
- Extended P2P deletes only after Windows-applied peer ACK;
- validation happens before ACK;
- old-peer fallback remains five seconds;
- no content/code/package/account/server URL in debug notifications or persistent diagnostics.
