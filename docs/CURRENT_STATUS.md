# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade`

## Current Android target

- app: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.17-standalone`
- versionCode: `320121`
- implementation anchor: `a010d7f0fb3871252580666df3264980b32c93cb`
- Android CI: `29681462233`, success
- Windows CI: `29681462236`, success
- Android artifact ID: `8440717410`
- deterministic signer unchanged

Artifact hashes and expiry are recorded in `docs/LATEST_GREEN_ARTIFACTS.md`.

## Current focus

The immediate focus is isolated HONOR target-device proof of real Gmail notification ingestion. The user reports that Gmail has never produced a relayed code, so pre-`.17` Gmail behavior is treated as non-functional.

`.17` adds actual listener binding state, automatic/manual rebind, active-notification rescan, deeper bounded extras collection, content-free stage counters, Gmail-shaped tests, and a default-OFF outbound transport-accepted debug notification.

See `docs/LATEST_GMAIL_NOTIFICATION_RELIABILITY_HANDOFF.md`.

## Gmail diagnostic stages

The settings screen now distinguishes:

- Android authorization granted but listener not connected;
- listener connected but notification unseen;
- notification seen but text extras empty;
- text available but no authentication hint;
- authentication hint available but extractor no-match;
- queued or deduplicated;
- outbound transport accepted when the optional debug notification is enabled;
- peer ACK and native deletion through existing diagnostics/queue state.

No raw notification text, code, account identifier, app/package name, relay ID, or server address is stored in these diagnostics.

No real Gmail success is claimed until target-device evidence exists.

## Outbound debug notification

- settings switch defaults OFF;
- when ON, notification appears after P2S publish acceptance or at least one open P2P DataChannel accepts the item;
- notification shows only source class and P2P/P2S mode;
- debug notification failure is caught and cannot affect transport acceptance, relay claims, ACK ordering, retries, or deletion.

## Delivery acknowledgement — preserve

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

Extended P2P:

`LOCAL_TRANSPORT_ACCEPTED -> PEER_RECEIVED -> PEER_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

Old/non-Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

Do not treat the outbound debug notification as peer application or ACK. It proves transport acceptance only.

## Retained ordinary-copy invariants

- language-neutral OS clipboard-change confirmation;
- selection alone never sends;
- internal-write echo suppression;
- accepted ordinary items do not expire by wall-clock age;
- accepted items are not evicted on overflow;
- bounded capacity 16 with explicit `queue_full` for new input;
- disconnected retry backoff capped at 15 seconds;
- Extended queue deletion remains ACK-based.

## Trial and error retained

- CI `29681080924`: final Gmail transform stopped safely; no APK produced.
- CI `29681226875`: diagnostic artifact identified exact Japanese string-anchor mismatch; compatibility normalization added.
- CI `29681300642`: all transforms passed, Kotlin compilation found nonexistent debug-notification drawable; changed to existing native resource.
- corrected CI `29681462233`: Android fully green.
- corrected CI `29681462236`: Windows fully green.

## Mandatory proof

Install `.17` in place without uninstalling. Confirm notification access and actual listener connection both show active. Enable debug notification temporarily, clear counters, and send a Gmail OTP whose expanded notification visibly includes the code. Use the stage counters to locate the first failed boundary rather than loosening the extractor blindly.

Repeat backgrounded, removed from recents, locked/screen-off where feasible, and with a pre-existing Gmail notification followed by manual reconnect/rescan.

## Do not claim

CI is not HONOR target-device proof. Do not claim real Gmail ingestion, background Gmail operation, outbound debug ON/OFF behavior, multilingual Copy correctness, ordinary background/screen-off delivery, exactly-once, battery efficiency, or Windows tray behavior until isolated evidence exists.

Canonical handoff: `docs/NEXT_CHATGPT_HANDOFF.md`. Keep PR #1 Draft.
