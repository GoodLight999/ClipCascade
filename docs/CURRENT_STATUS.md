# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade`

## Current alpha target

- app: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.18-alpha.1-standalone`
- versionCode: `320122`
- implementation anchor: `87e138380a139671168effd24a64df844f1bb879`
- alpha tag: `v3.2.1-extended.18-alpha.1`
- Android CI: `29827698937`, success
- Windows CI: `29827698930`, success
- Android Actions artifact ID: `8494023518`
- deterministic signer unchanged

Artifact hashes and expiry are recorded in `docs/LATEST_GREEN_ARTIFACTS.md`.

## Target-device evidence

The user reports that ordinary synchronization is working again on `.17`; it had previously been broken. This is the most important observed result and must be protected against regression.

No real Gmail OTP arrived, so real Gmail extraction has not been tested. Do not interpret the absence of a failure as Gmail success.

## Why `.18-alpha.1` exists

The alpha makes notification validation truthful and prevents reconnect/rescan duplicates without changing ordinary synchronization or Extended ACK semantics.

### True listener-path self-test

The previous deterministic OTP test directly inserted the extracted value into the queue after posting its notification. It remains useful for extractor/queue/transport/ACK testing but cannot prove NotificationListener delivery.

The alpha adds a separate listener-path test that posts a marked local notification and does not directly queue the value. Success requires:

`NOTIFICATION_POSTED -> LISTENER_CALLBACK -> TEXT_COLLECTED -> EXTRACTED -> OTP_QUEUE -> TRANSPORT -> WINDOWS_APPLIED -> PEER_ACK -> DELETE`

Synthetic privileges are granted only when the notification comes from ClipCascade's own package and contains the marker.

### Active-notification duplicate prevention

A successful notification/code pair receives a one-way SHA-256 receipt fingerprint before queue insertion. The fingerprint is bounded to 128 entries and 30 minutes, contains no recoverable notification text/code/package/key/account data, and is released if queue insertion throws.

This prevents a still-active notification from being resent after the OTP queue's 90-second content-deduplication window when the listener reconnects or performs a manual rescan.

### Expanded content-free diagnostics

The settings screen adds:

- eligible notifications;
- already-processed suppression;
- listener-path test callbacks;
- active-scan count and last candidate count;
- latest callback delay;
- existing text/auth/queued/no-match/empty/filtered counters.

## Outbound debug notification

- defaults OFF;
- appears after local transport acceptance only;
- displays source class and P2P/P2S mode only;
- never proves Windows application or peer ACK;
- failure cannot alter relay claims, retry, ACK ordering, or queue deletion.

## ACK path — preserve exactly

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

Extended P2P:

`LOCAL_TRANSPORT_ACCEPTED -> PEER_RECEIVED -> PEER_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

Old/non-Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

No alpha change modifies Windows code, P2P message ACK metadata, validation-before-ACK, native relay claims, or native ACK deletion.

## Ordinary synchronization invariants

- selection events only remember selected text;
- selection alone never queues or sends;
- OS clipboard mutation is primary Copy proof;
- ACTION_COPY/Ctrl+C fallback remains serial-cancelled and bounded;
- internal ClipCascade writes are filtered;
- accepted ordinary items do not expire by age;
- accepted items are not evicted to admit newer items;
- capacity remains 16 with explicit `queue_full` for new input;
- disconnected retry remains capped at 15 seconds.

## Alpha release state

The tag `v3.2.1-extended.18-alpha.1` resolves exactly to implementation commit `87e13838...`. The release workflow is constrained to create the prerelease only after matching-sha push Android and Windows CI succeed.

The connector independently verified the tag and source commit. The local APK and Actions artifact were downloaded and hashed. Direct release-asset enumeration was not available through the connector; use the release page or recorded Actions artifact if asset recovery is needed.

## Mandatory proof

Install over `.17`, preserve data, then test in this order:

1. ordinary synchronization regression;
2. actual listener runtime connected;
3. deterministic component/transport test;
4. real listener-path self-test;
5. repeated active rescan suppresses duplicate;
6. fresh listener-path test still succeeds;
7. outbound debug ON and OFF;
8. real Gmail only when a genuine notification arrives.

## Do not claim

CI is not HONOR target-device proof. Do not claim alpha ordinary-sync regression safety, listener-path success, receipt suppression, real Gmail ingestion, OEM listener survival, background/screen-off Gmail delivery, exactly-once target behavior, battery efficiency, or tray behavior until isolated evidence exists.

Canonical handoff: `docs/NEXT_CHATGPT_HANDOFF.md`. Keep PR #1 open and Draft.
