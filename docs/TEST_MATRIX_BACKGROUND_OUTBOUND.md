# Android Background Outbound Validation Matrix

Disable Phone Link and every competing clipboard synchronizer before every row. Never record real clipboard contents, notification bodies, verification values, account identifiers, or private URLs.

## Current green build

- [x] implementation/release `2f08e03b325eeff18ec63b1be8cbe1b08cb4f85d`
- [x] intended tag `v3.2.1-extended.21-alpha.1`
- [x] version `3.2.1-extended.21-alpha.1-standalone`
- [x] versionCode `320126`
- [x] Android CI `29888733469`, success
- [x] Windows CI `29888733458`, success
- [x] artifact ID `8517492289`
- [x] ZIP SHA-256 `9bae0a27c80ddd6b16d9e8d7df95153ad080cd4cd0b864ec5eebc5d914370c87`
- [x] APK SHA-256 `93b85d2bd8474c874d8937e76c09ec97dde90006c4b1e8d97f448557a959c7a9`
- [x] APK size `147945851` bytes
- [x] signer `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- [x] artifact expiry `2026-10-20T03:33:01Z`
- [ ] in-place installation succeeds
- [ ] settings and permissions retained

## Known target baseline

- [x] Windows-to-Android receive works with app not open
- [x] Android outbound works with app open on a prior build
- [x] `.19-alpha.2` outbound fails with app not open
- [x] `.19-alpha.2` true listener-path self-test fails to complete
- [x] real Gmail/DAWN-shaped login-code notification failed to relay
- [x] `.20-alpha.1` did not reproduce the known Go overlay acquisition path
- [ ] `.21-alpha.1` tested on target

## Required preflight

Before interpreting any queue/transport test:

- [ ] install over the existing app; do not uninstall
- [ ] reliable background clipboard fallback is ON
- [ ] Display over other apps is allowed
- [ ] settings reports `Foreground transport runner: active`
- [ ] runner health shows `runner_started / notifee_foreground / ready`
- [ ] heartbeat remains fresh for at least 60 seconds
- [ ] outbound debug notification enabled temporarily
- [ ] diagnostic history cleared

If the runner is inactive, stop. The failure is process/Notifee lifecycle, not Copy or OTP extraction.

If the overlay option is ON but permission is missing, stop. The known-success acquisition condition is not configured.

## Diagnostic boundary map

Record all available boundaries after each test:

1. runner active/heartbeat
2. explicit Copy detection or listener callback
3. clipboard acquisition path / verification extraction
4. native queue count
5. `foreground_poll / claimed`
6. outbound debug notice after local transport acceptance
7. Windows validation/application count
8. peer ACK
9. native queue after ACK

| First missing boundary | Likely area |
|---|---|
| Runner inactive | Notifee registration/process/service lifecycle |
| No Copy-detection record | App/OEM did not expose a usable explicit Copy event |
| `overlay_permission_missing` | setup/permission failure |
| `overlay_add_failed` | WindowManager/OEM overlay creation failure |
| `overlay_clipboard_empty` or denied | overlay did not obtain clipboard access |
| Copy cue, no acquisition record | capture scheduling failure |
| Acquisition not queued | empty value/dedup/queue-full |
| Queued, no foreground claim | runner poll/native module queue drain |
| Foreground claim, no debug | `sendClipBoard`/transport acceptance |
| Debug, no Windows apply | peer connectivity/validation/application |
| Windows apply, no peer ACK | peer control-envelope path |
| Peer ACK, queue remains | native acknowledgement/deletion |

## Deterministic component/transport test

This directly queues after extraction and does not prove NotificationListener or clipboard acquisition.

- [ ] runner active before test
- [ ] synthetic value queued
- [ ] OTP is foreground-claimed before ordinary clipboard
- [ ] debug notice after local transport acceptance
- [ ] Windows validates and applies once
- [ ] peer ACK received
- [ ] native queue deleted only after ACK

Failure interpretation:

- queued + no claim: runner drain failure;
- claim + no debug: transport failure;
- debug + no Windows: peer path;
- Windows + no peer ACK: Windows ACK emission;
- peer ACK + queue remains: native ACK/deletion.

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

Use the overlay fallback ON and authorized. Explicitly press Copy.

| App / locale | Runner | Copy cue | Direct read | Overlay read if needed | Queued | Claimed | Debug | Windows once | ACK delete | Status |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Chrome / system locale | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Firefox-family / system locale | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Notes/editor / system locale | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

Stop if foreground behavior regresses.

## Background explicit Copy — reliable overlay mode

Leave the main UI without force-stopping it. Use a new unique synthetic selection and explicitly press Copy. The expected acquisition path on Android 10+/MagicOS is `overlay_clipboard_manager` when the direct read is unavailable.

| App | Runner | Selection state | Explicit Copy cue | Overlay permission | Acquisition path | Queued | Claimed | Debug | Windows once | ACK delete | Status |
|---|---:|---:|---:|---:|---|---:|---:|---:|---:|---:|---|
| Chrome | ☐ | ☐ | ☐ | ☐ | — | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Firefox-family | ☐ | ☐ | ☐ | ☐ | — | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Notes/editor | ☐ | ☐ | ☐ | ☐ | — | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

Success requires the actual copied value on Windows, not merely the remembered selected range. Record only the acquisition path token, never the content.

The exact localized Copy search includes clicked node, parent, immediate children/siblings, and Accessibility action labels. Selection alone remains insufficient.

## Overlay-free control

Turn the reliable fallback OFF and repeat one background explicit Copy in each representative app.

| App | Copy cue | Selected-text fallback | Queued | Windows once | Interpretation |
|---|---:|---:|---:|---:|---|
| Chrome | ☐ | ☐ | ☐ | ☐ | untested |
| Firefox-family | ☐ | ☐ | ☐ | ☐ | untested |
| Notes/editor | ☐ | ☐ | ☐ | ☐ | untested |

A failure here does not invalidate the overlay mode. It demonstrates that the overlay-free fallback is not Go-equivalent on the target.

## Selection-only negative tests

Select text but do not press Copy. Keep the overlay option ON so this also proves selection alone cannot create the overlay.

| State | Runner | 3s | 15s | 60s | No overlay record | Queue unchanged | Windows unchanged | Status |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| App foreground | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| App background | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Third system language | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

Labels/actions `Copied`, `Copy all`, `Paste`, arbitrary text, empty label, Select all, and Share must not send.

## Overlay behavior checks

- [ ] no visible overlay or flash
- [ ] no touch interception
- [ ] no persistent overlay after success
- [ ] no persistent overlay after empty/denied/add-failure path
- [ ] permission revocation yields `overlay_permission_missing`, not a crash
- [ ] disabling the setting prevents overlay creation
- [ ] enabling the setting without permission does not silently claim setup complete
- [ ] repeated Copy does not leak WindowManager views

## Lifecycle expansion

Run only after simple background Copy succeeds in reliable overlay mode.

| State | Runner | Cue | Overlay acquisition | Queued | Claimed | Debug | Windows once | ACK delete | Status |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Removed from recents | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Device locked | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 1 minute | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 15 minutes | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 30+ minutes | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

## Gmail/DAWN/Perceptron stage matrix

Synthetic DAWN and Perceptron layouts pass extractor unit tests. Clear notification diagnostics immediately before each new real notification.

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
- [ ] each item deleted only after peer ACK
- [ ] failed transport retains item and retries after bounded claim timeout
- [ ] inbound ClipCascade writes create no outbound echo
- [ ] overlay acquisition does not bypass the queue
- [ ] overlay acquisition does not delete after local transport acceptance

## Do not claim

CI proves source/build invariants only. `.21-alpha.1` overlay acquisition, runner lifecycle on HONOR, true listener delivery, background Copy, Gmail/DAWN/Perceptron, lifecycle rows, queue durability, exactly-once, battery, and tray behavior remain unproven until isolated target evidence exists.
