# Android Background Outbound Validation Matrix

Disable Phone Link and every competing clipboard synchronizer before every row. Never record real clipboard contents, notification bodies, or verification values.

## Current build

- [x] implementation/release `290d6690e749fe34367b2383676faf27c0a4ba76`
- [x] intended tag `v3.2.1-extended.20-alpha.1`
- [x] version `3.2.1-extended.20-alpha.1-standalone`
- [x] versionCode `320125`
- [x] Android CI `29843413287`
- [x] Windows CI `29843413149`
- [x] artifact `8500372630`
- [x] ZIP SHA-256 `ba2152241fdfe8c5bb99e3087b8781faa15915a281df3e10a9e8065fad177660`
- [x] APK SHA-256 `1b48a7bb7e6d4ab757a3a044fda233e8363ce58d6d634b071e7f90a90ac35cbe`
- [x] signer `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- [ ] in-place installation succeeds
- [ ] settings and permissions retained

## Known target baseline

- [x] Windows-to-Android receive works with app not open
- [x] Android outbound works with app open on prior build
- [x] `.19-alpha.2` outbound fails with app not open
- [x] `.19-alpha.2` true listener-path self-test fails to complete
- [x] real Gmail login-code notification failed to relay
- [ ] `.20-alpha.1` tested on target

## Required preflight

Before interpreting any queue/transport test:

- [ ] settings reports `Foreground transport runner: active`
- [ ] runner health shows `runner_started / notifee_foreground / ready`
- [ ] heartbeat remains fresh for at least 60 seconds
- [ ] outbound debug notification enabled temporarily
- [ ] diagnostic history cleared

If the runner is inactive, stop. The failure is process/Notifee lifecycle, not Copy or OTP extraction.

## Diagnostic boundary map

Record all available rows after each test:

1. runner active/heartbeat
2. `Copy detection` or listener callback
3. `Clipboard capture` / verification queue
4. native queue count
5. `foreground_poll / claimed`
6. outbound debug notice
7. Windows application count
8. native queue after peer ACK

| First missing boundary | Likely area |
|---|---|
| Runner inactive | Notifee registration/process/service lifecycle |
| No Copy-detection record | App/OEM did not expose a usable Copy event |
| Copy cue, no capture | selection/capture scheduling |
| Capture not queued | clipboard read/fallback/dedup/queue-full |
| Queued, no foreground claim | runner poll/native module queue drain |
| Foreground claim, no debug | `sendClipBoard`/transport acceptance |
| Debug, no Windows apply | peer connectivity/validation/application |
| Windows apply, queue remains | peer ACK/native deletion |

## Deterministic component/transport test

This directly queues after extraction and does not prove NotificationListener.

- [ ] runner active before test
- [ ] synthetic value queued
- [ ] OTP is foreground-claimed before ordinary clipboard
- [ ] debug notice after local transport acceptance
- [ ] Windows applies once
- [ ] peer ACK received
- [ ] native queue deleted only after ACK

Failure interpretation:

- queued + no claim: runner drain failure;
- claim + no debug: transport failure;
- debug + no Windows: peer path;
- Windows + queue remains: ACK/deletion.

## True NotificationListener-path test

The test notification remains active for five minutes and schedules delayed scans at 0.5, 2, and 5 seconds. It never directly queues.

- [ ] runner active before test
- [ ] listener connected
- [ ] listener-test + seen + eligible increase
- [ ] text chars > 0
- [ ] auth hint
- [ ] queued
- [ ] foreground claim
- [ ] debug notice
- [ ] Windows applies once
- [ ] peer ACK and queue deletion
- [ ] active rescan increments already-processed without another Windows application

Failure interpretation:

- connected + unseen: HONOR listener callback/active-scan exposure;
- seen/eligible + text zero: notification extras unavailable;
- text/auth + no queue: extractor/receipt boundary;
- queued + no claim: runner drain.

## Foreground Copy regression gate

| App / locale | Runner active | Copy detected | Capture queued | Foreground claimed | Debug | Windows once | ACK deletion | Status |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| Chrome / system locale | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Firefox-family / system locale | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Notes/editor / system locale | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

Stop if foreground behavior regresses.

## Background explicit Copy

Background the app without force-stopping it. Use a new unique synthetic selection and explicitly press Copy.

| App | Runner active | Selection remembered | Framework Copy cue | Fallback requested | Capture queued | Foreground claimed | Debug | Windows once | ACK deletion | Status |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Chrome | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Firefox-family | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Notes/editor | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

The exact localized Copy search includes clicked node, parent, immediate children/siblings, and Accessibility action labels. Selection alone remains insufficient.

## Selection-only negative tests

Select text but do not press Copy.

| State | Runner active | 3s | 15s | 60s | Queue unchanged | Windows unchanged | Status |
|---|---:|---:|---:|---:|---:|---:|---|
| App foreground | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| App background | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Third system language | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

Labels/actions `Copied`, `Copy all`, `Paste`, arbitrary text, empty label, Select all, and Share must not send.

## Lifecycle expansion

Run only after simple background Copy succeeds.

| State | Runner active | Cue observed | Capture queued | Foreground claimed | Debug | Windows once | ACK deletion | Status |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| Removed from recents | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Device locked | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 1 minute | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 15 minutes | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 30+ minutes | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

## Gmail stage matrix

The DAWN-shaped extractor passes with a synthetic value. Clear notification diagnostics immediately before a new real notification.

| State | Runner | Connected | Seen | Eligible | Text >0 | Auth | Queued | Claimed | Debug | Windows | ACK delete | Status |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| App visible | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | failed previously; no counters |
| App background | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Removed from recents | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Active notification + rescan | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

Never record the code or notification body.

## Queue, durability, and exactly-once

- [ ] 16 items accepted while peer disconnected
- [ ] item 17 rejected as `queue_full`
- [ ] accepted items remain in order
- [ ] no accepted item expires or is evicted
- [ ] runner drains in bounded order
- [ ] reconnect applies each once
- [ ] each item deleted only after ACK
- [ ] failed transport retains item and retries after bounded claim timeout
- [ ] inbound ClipCascade writes create no outbound echo

## Do not claim

CI proves source/build invariants only. `.20-alpha.1` runner lifecycle, self-tests, background Copy, Gmail, lifecycle rows, queue durability, exactly-once, battery, and tray behavior remain unproven until isolated target evidence exists.
