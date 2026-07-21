# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade`

## Current candidate

- app: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.19-alpha.2-standalone`
- versionCode: `320124`
- implementation anchor: `ed9c009af0fcfc238cfc6264dd4c7b85a8fe82a3`
- intended alpha tag: `v3.2.1-extended.19-alpha.2`
- Android CI: `29837847117`, success
- Windows CI: `29837846863`, success
- Android Actions artifact ID: `8498121042`
- deterministic signer: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

Artifact hashes and expiry are recorded in `docs/LATEST_GREEN_ARTIFACTS.md`.

## Latest target-device truth

The latest report supersedes the earlier broad statement that ordinary synchronization had recovered:

- Android inbound works while the app UI is not open;
- Android outbound works while the app UI is open;
- Android outbound fails while the app UI is not open;
- ordinary Copy performed with the UI closed does not reach Windows;
- a genuine Gmail DAWN notification containing `Your code is 713642` was not relayed;
- previous apparent ordinary success was intermittent and must not be treated as stable background success.

Phone Link and all competing clipboard synchronizers must be disabled for every outbound validation.

## Why `.19-alpha.2` exists

The symptom does not imply that Copy detection alone is dead. Native clipboard and OTP dispatchers can queue an item but previously attempted delivery by emitting `SHARED_TEXT` through `MainApplication.currentReactContext`.

The Notifee foreground-service Headless React runtime can remain alive and receive inbound traffic while that UI/MainApplication React context is absent. This fits:

`INBOUND WORKS + UI-OPEN OUTBOUND WORKS + UI-CLOSED OUTBOUND FAILS`

`.19-alpha.2` lets the live foreground-service transport pull durable native queue items directly.

## Reference implementation used

`wuxinkami/ClipCascade_go_fork` keeps its Go synchronization engine inside a native sticky foreground service and lets Accessibility request work from that service.

Extended adopts the transport-ownership principle only. It does not add the fork's transparent overlay, `SYSTEM_ALERT_WINDOW`, `READ_LOGS`, root, ADB, or Shizuku dependency.

## Foreground queue drain

### Native claim

- verification values are offered before ordinary text;
- `ClipboardRelayDispatcher` and `OtpRelayDispatcher` reuse existing native in-flight IDs and timestamps;
- claim does not delete or acknowledge an item;
- existing 15-second acknowledgement timeout releases a stuck claim for retry.

### Live transport

The existing 3-second Notifee service poll calls `drainNativeRelayQueue()` and reuses final `sendClipBoard(...)` plus existing ACK helpers.

P2S:

`NATIVE_QUEUE -> FOREGROUND_CLAIM -> LOCAL_PUBLISH_ACCEPTED -> NATIVE_ACK -> DELETE`

Extended P2P:

`NATIVE_QUEUE -> FOREGROUND_CLAIM -> DATA_CHANNEL_ACCEPTED -> WINDOWS_VALIDATED -> WINDOWS_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

Old/non-Extended P2P:

`DATA_CHANNEL_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

A failed send never deletes the native item.

## Copy detection retained from `.19-alpha.1`

- OS clipboard mutation remains the strongest Copy proof;
- exact Android framework-localized Copy/Copy URL click is a conservative fallback;
- selection events only remember text;
- selection alone cannot enter the fallback;
- internal ClipCascade writes are filtered;
- no hard-coded Japanese/English menu dictionary.

This candidate addresses both plausible boundaries: background Copy detection and post-queue transport dispatch.

## DAWN result interpretation

The exact supplied DAWN body shape is covered by `extractsDawnStandaloneNumericLoginCode`, and Android unit tests pass. The text itself is extractor-compatible.

The real failure can still be at:

`LISTENER_BINDING -> CALLBACK -> TEXT_EXTRAS -> AUTH_CONTEXT -> EXTRACTION -> OTP_QUEUE -> FOREGROUND_CLAIM -> TRANSPORT -> WINDOWS -> ACK`

Do not loosen the extractor without locating the first failed content-free counter.

## Notification validation retained

- deterministic component/transport test remains separate from listener validation;
- true listener-path test cannot directly insert into the queue;
- synthetic privilege requires ClipCascade's own package plus marker;
- active-notification receipts use bounded one-way fingerprints;
- no raw notification text, code, account, package, relay ID, or server URL is persisted in diagnostics.

## Outbound debug notification

- defaults OFF;
- appears after local P2S publish acceptance or at least one open P2P DataChannel accepts the item;
- displays source class and P2P/P2S only;
- does not prove Windows application or peer ACK;
- failure cannot alter claims, retry, ACK ordering, or deletion.

## Preserved queue and ACK invariants

- no ordinary clipboard TTL;
- no overflow eviction of accepted items;
- capacity 16 with explicit `queue_full` rejection;
- disconnected retry cap 15 seconds;
- relay IDs and native claims remain;
- Extended peer application precedes ACK;
- peer ACK precedes native deletion;
- validation-before-ACK remains;
- old-peer fallback remains five seconds.

## Mandatory proof

1. Install alpha.2 in place without uninstalling.
2. Confirm settings, notification access, Accessibility, and sync configuration survive.
3. Disable Phone Link and competitors.
4. Confirm inbound with UI closed.
5. Enable outbound debug notification temporarily.
6. Open once, confirm connection, then leave the UI.
7. Copy one unique value and record:
   - Copy detection health;
   - clipboard capture/delivery health;
   - `foreground_poll / claimed`;
   - debug notification;
   - Windows application count;
   - peer ACK and native deletion.
8. Repeat removed from recents and locked/screen-off where Android permits.
9. Run component/transport and true listener-path OTP tests.
10. Test real Gmail/DAWN only when a genuine notification arrives.

## Do not claim

CI is not HONOR target-device proof. Do not claim background Copy, native queue insertion, foreground drain, real Gmail, listener survival, removed-from-recents/screen-off delivery, exactly-once target behavior, battery efficiency, or real tray behavior until isolated evidence exists.

Canonical handoff: `docs/NEXT_CHATGPT_HANDOFF.md`. Keep PR #1 open and Draft.
