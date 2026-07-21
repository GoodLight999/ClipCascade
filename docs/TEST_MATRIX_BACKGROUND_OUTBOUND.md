# Android Background Outbound Validation Matrix

Disable Phone Link and every competing clipboard synchronizer before every row. Never record real clipboard contents, notification bodies, or verification values.

## Current build

- [x] implementation `ed9c009af0fcfc238cfc6264dd4c7b85a8fe82a3`
- [x] tag `v3.2.1-extended.19-alpha.2`
- [x] version `3.2.1-extended.19-alpha.2-standalone`
- [x] versionCode `320124`
- [x] Android CI `29837847117`
- [x] Windows CI `29837846863`
- [x] artifact `8498121042`
- [x] ZIP SHA-256 `8c20eadce450c57fadb9eab39e465332fe34f8f25f7e28d44a072162dc480db3`
- [x] APK SHA-256 `f9f7b5fe6653beb8d0b08436657ddf719fd155e3ac9b1216b0307b7ea1cf63a7`
- [x] signer `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- [ ] in-place installation succeeds
- [ ] settings and permissions retained

## Known target baseline

- [x] Windows-to-Android receive works with app not open
- [x] Android outbound works with app open
- [x] Android outbound fails with app not open on previous alpha
- [x] real Gmail login-code notification failed to relay

## Diagnostic boundary map

Record all available rows after each Copy:

1. `Copy detection`
2. `Clipboard capture`
3. queue count
4. foreground-service delivery health (`foreground_poll` where shown)
5. outbound debug notice
6. Windows application count
7. native queue count after ACK

| First missing boundary | Likely area |
|---|---|
| No Copy-detection record | App/OEM did not expose a usable Copy event to Accessibility |
| Copy cue recorded, no fallback/capture | Selection was unavailable/stale or capture scheduling failed |
| Capture not queued | Clipboard read/selected-text fallback/dedup/queue-full |
| Queued, no foreground claim | Notifee foreground service or native queue drain |
| Foreground claimed, no debug notice | `sendClipBoard`/transport acceptance |
| Debug notice, no Windows apply | Peer connectivity/validation/transport |
| Windows apply, queue remains | Peer ACK/native deletion |

## Foreground regression gate

| App / locale | Copy detected | Capture queued | Foreground claimed | Debug | Windows once | ACK deletion | Status |
|---|---:|---:|---:|---:|---:|---:|---|
| Chrome / system locale | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Firefox-family / system locale | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Notes/editor / system locale | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

Stop if foreground behavior regresses.

## Background Copy + foreground queue drain

Background the app without force-stopping it. Use a new unique synthetic selection for every row and press the Android/system Copy toolbar item.

| App | Selection remembered | Framework Copy cue | Fallback requested | Capture queued | Foreground claimed | Debug | Windows once | ACK deletion | Status |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Chrome | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Firefox-family | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Notes/editor | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

If selection is remembered but no framework cue appears, the toolbar click is not exposed to Accessibility. Do not blame transport.

If capture queues but foreground claim is absent, Copy detection worked and the remaining defect is service/queue ownership.

## Selection-only negative tests

Select text but do not press Copy.

| State | 3s | 15s | 60s | Queue unchanged | Windows unchanged | Status |
|---|---:|---:|---:|---:|---:|---|
| App foreground | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| App background | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Third system language | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

## Direct false-positive checks

The following Accessibility labels/actions must not send:

- [ ] `Copied`
- [ ] `Copy all`
- [ ] `Paste`
- [ ] arbitrary selected text
- [ ] empty label
- [ ] Select all
- [ ] Share

Unit tests cover the first five; target UI tests remain required.

## Lifecycle expansion

Run only after the simple background row succeeds.

| State | Cue observed | Capture queued | Foreground claimed | Debug | Windows once | ACK deletion | Status |
|---|---:|---:|---:|---:|---:|---:|---|
| Removed from recents | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Device locked | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 1 minute | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 15 minutes | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 30+ minutes | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

## OTP self-tests

### Deterministic component/transport test

- [ ] synthetic value queued
- [ ] foreground service claims OTP first
- [ ] debug notice after local transport acceptance
- [ ] Windows applies once
- [ ] peer ACK
- [ ] native queue deletion after ACK

This does not prove NotificationListener.

### True listener-path test

- [ ] listener connected
- [ ] listener-test + seen + eligible
- [ ] text/auth/queued stages
- [ ] foreground service claims OTP
- [ ] debug notice
- [ ] Windows applies once
- [ ] ACK deletion
- [ ] active rescan increments already-processed without another Windows application

## Gmail stage matrix

The DAWN-shaped extractor layout passes with a synthetic value in CI. For a fresh real Gmail notification, clear notification diagnostics immediately before triggering it.

| State | Connected | Seen +1 | Eligible +1 | Text chars >0 | Auth hint | Queued | Foreground claimed | Debug | Windows once | ACK deletion | Status |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| App visible | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | failed previously; no counters recorded |
| App background | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Removed from recents | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Active notification + rescan | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

Interpretation:

- connected false: listener binding/recovery;
- connected true, seen unchanged: notification delivery;
- seen, eligible false: filtering/trust boundary;
- eligible, chars zero: Gmail/Android exposed no usable text extras;
- chars present, auth false: preview/context omitted;
- auth present, queue absent: extractor or receipt boundary;
- queued, no foreground claim: service queue drain;
- foreground claim, no debug: transport acceptance;
- debug, no Windows: peer path;
- Windows once + ACK deletion: full Extended P2P success.

## Queue, durability, and exactly-once

- [ ] 16 items accepted while peer disconnected
- [ ] item 17 rejected as `queue_full`
- [ ] accepted items remain in order
- [ ] no accepted item expires or is evicted
- [ ] foreground service drains in bounded order
- [ ] reconnect applies each once
- [ ] each item deleted only after ACK
- [ ] failed transport retains item and retries after bounded claim timeout
- [ ] inbound ClipCascade writes create no outbound echo

## Do not claim

CI proves source/build invariants only. `.19-alpha.2` background Copy, toolbar event exposure, foreground queue drain, Gmail delivery/extras, lifecycle rows, queue durability on target, exactly-once, battery, and tray behavior remain unproven.
