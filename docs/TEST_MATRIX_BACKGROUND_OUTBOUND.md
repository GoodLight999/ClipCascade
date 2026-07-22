# Android Background Outbound Validation Matrix

Disable Phone Link and every competing clipboard synchronizer before every row. Never record real clipboard contents, notification bodies, verification values, account identifiers, or private URLs.

## Current candidate

- [x] version `3.2.1-extended.22-alpha.1-standalone`
- [x] versionCode `320127`
- [x] intended tag `v3.2.1-extended.22-alpha.1`
- [x] staging SHA `f8ec5245f9bd2eeaac6400a4f0f57d85ad6d429e`
- [x] staging Android CI `29915789910`, success
- [x] staging main and helper APKs compile and share the expected signer
- [ ] exact final `stability-mobile-otp` implementation SHA recorded
- [ ] exact final Android CI green
- [ ] exact final Windows CI green
- [ ] final artifact ID, ZIP digest, both APK hashes/sizes, signer, and expiry recorded
- [ ] in-place main-APK installation succeeds
- [ ] existing settings and permissions retained
- [ ] helper APK installs separately

Staging artifacts are not final release artifacts.

## Known target baseline

- [x] Windows-to-Android receive works with the main UI closed
- [x] Android-to-Windows worked with the main UI open on a prior build
- [x] `.19-alpha.2` UI-closed outbound failed
- [x] `.19-alpha.2` true listener-path test failed
- [x] `.21-alpha.1` background outbound failed
- [x] `.21-alpha.1` true listener-path test failed
- [x] `.21` was CI-green but did not reproduce the full Go event/ownership structure
- [x] target runs Android 16, where untrusted listeners may receive OTP-redacted content
- [ ] `.22-alpha.1` tested on target

## Required installation and setup

1. Install the `.22` main APK over the existing signed Extended app. Do not uninstall.
2. Install `ClipCascade-Notification-Test-Sender-22-alpha.1.apk` separately.
3. Disable Phone Link and all competing clipboard synchronizers.
4. Enable ClipCascade Accessibility.
5. Enable reliable background clipboard acquisition.
6. Allow Display over other apps.
7. Complete the trusted CompanionDeviceManager association in Extended settings.
8. Grant notification access through the companion flow.
9. Set ClipCascade battery use to unrestricted and enable MagicOS auto-launch/background switches.
10. Temporarily enable outbound debug and clear diagnostics.

Stop before interpretation if any required setup state is missing.

## Runtime preflight

- [ ] existing foreground transport runner reports active
- [ ] runner heartbeat remains fresh for at least 60 seconds
- [ ] native clipboard acquisition service reports started
- [ ] Accessibility reports native service bound
- [ ] overlay permission reports granted
- [ ] trusted companion reports associated
- [ ] notification listener reports connected
- [ ] helper APK launch resolves without signature/installation error

| Missing state | Interpretation |
|---|---|
| transport runner inactive | Notifee/process/transport-runner lifecycle failure |
| native service not started | foreground-service start/manifest failure |
| native service not bound | Accessibility/service bind failure |
| overlay permission missing | setup failure; background clipboard read condition absent |
| companion missing | Android 15+ OTP trust setup incomplete |
| listener disconnected | NotificationListener binding/access failure |
| helper cannot launch | helper install, package, or signature-permission failure |

## Boundary order

### Clipboard path

1. Accessibility probe
2. pre-selection clipboard baseline where applicable
3. delayed native observation
4. fingerprint decision: baseline / unchanged / changed
5. overlay acquisition path
6. native durable queue
7. foreground runner claim
8. local transport acceptance/debug
9. Windows validation/application
10. peer ACK
11. native deletion

### Notification path

1. helper notification posted
2. listener connected
3. seen
4. eligible
5. text characters available
6. auth context / extraction
7. durable verification queue
8. foreground claim
9. local transport acceptance/debug
10. Windows validation/application
11. peer ACK
12. native deletion

## Deterministic component/transport test

This test may queue after local extraction and does not prove NotificationListener, CompanionDeviceManager, helper delivery, or clipboard acquisition.

- [ ] transport runner active
- [ ] synthetic value queued
- [ ] foreground claim
- [ ] debug after local transport acceptance
- [ ] Windows validates and applies once
- [ ] peer ACK received
- [ ] native item deleted only after ACK

Failure interpretation:

- queue + no claim: foreground drain/module failure
- claim + no debug: transport acceptance failure
- debug + no Windows: peer connectivity/application failure
- Windows + no peer ACK: Windows ACK emission failure
- peer ACK + queue remains: native ACK/deletion failure

## External NotificationListener-path self-test

The main app launches the separately installed, same-signed helper APK. The helper posts a normal external notification containing a fake code. No direct queue insertion is permitted.

- [ ] helper Activity launches
- [ ] helper notification appears
- [ ] listener connected
- [ ] seen count increases
- [ ] eligible count increases
- [ ] text characters > 0
- [ ] expected fake value extracted
- [ ] verification item queued
- [ ] foreground claim
- [ ] debug after local transport acceptance
- [ ] Windows applies once
- [ ] peer ACK received
- [ ] native item deleted
- [ ] active rescan records already processed without a second Windows application

Failure interpretation:

| First missing boundary | Likely area |
|---|---|
| helper does not launch | helper missing, wrong package, or signature permission mismatch |
| helper launches but no notification | helper notification permission/channel failure |
| notification visible but unseen | HONOR listener delivery/binding failure |
| seen but ineligible | source filter/foreground/ongoing classification failure |
| eligible but text 0/redacted | Companion association/trust or Android redaction failure |
| text available but no extraction | extractor/context boundary |
| extraction but no queue | receipt/dedup/storage boundary |
| queued but no claim | transport-runner drain failure |
| Windows applied but queue remains | ACK/native deletion failure |

## Foreground Copy regression gate

Run with main UI visible before testing UI-closed behavior.

| App / locale | Native bound | Probe | Baseline | Delayed read | Fingerprint changed | Overlay read | Queued | Claimed | Windows once | ACK delete | Status |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Chrome / system locale | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Firefox-family / system locale | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Notes/editor / system locale | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

Stop if foreground Copy regresses.

## Background explicit Copy

Leave the main UI without force-stopping it. Select a unique value and explicitly Copy it.

The expected path when selection is exposed is:

`selection_probe -> pre_selection_baseline -> delayed native observation -> changed fingerprint -> overlay_clipboard_manager -> queue -> claim -> Windows -> ACK/delete`

A strong semantic/system Copy event may use the shorter 300 ms probe, but it is no longer required for entry.

| App | Native bound | Selection/click probe | Old baseline | Delayed observation | Changed fingerprint | Acquisition path | Queued | Claimed | Debug | Windows once | ACK delete | Status |
|---|---:|---:|---:|---:|---:|---|---:|---:|---:|---:|---:|---|
| Chrome | ☐ | ☐ | ☐ | ☐ | ☐ | — | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Firefox-family | ☐ | ☐ | ☐ | ☐ | ☐ | — | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Notes/editor | ☐ | ☐ | ☐ | ☐ | ☐ | — | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

Success requires the actual copied value on Windows. Never record the value itself.

## Selection-only negative test

Select text but do not press Copy. Keep overlay mode enabled.

Expected behavior:

- selection probe may occur;
- old clipboard baseline may be recorded;
- delayed overlay read may occur;
- fingerprint remains unchanged;
- no native queue item;
- no Windows change.

| State | Probe | Baseline | Delayed observation | Fingerprint unchanged | Queue unchanged | Windows unchanged | Status |
|---|---:|---:|---:|---:|---:|---:|---|
| App foreground | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Main UI closed | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Third system language | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

A selection-only overlay read is permitted as a mutation probe. A selection-only queue/send is forbidden.

## Same-value Copy behavior

Copying the same value already present in the clipboard may produce an unchanged fingerprint and no send because no clipboard mutation is observable.

- [ ] document target behavior for same-value Copy
- [ ] do not treat same-value non-send as proof that unique-value Copy is broken
- [ ] use a fresh unique test value for positive rows

## Overlay behavior

- [ ] no visible flash
- [ ] no touch interception
- [ ] no persistent overlay after success
- [ ] no persistent overlay after empty/denied/error path
- [ ] permission revocation yields content-free failure, not crash
- [ ] disabling reliable mode prevents overlay creation
- [ ] repeated probes do not leak WindowManager views

## Native service lifecycle expansion

Run only after simple UI-closed Copy succeeds.

| State | Native service | Bound/recovered | Probe | Changed fingerprint | Overlay read | Queued | Claimed | Windows once | ACK delete | Status |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Removed from recents | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Device locked | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 1 minute | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Screen off 15 minutes | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| Device reboot | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

## Real Gmail / DAWN / Perceptron notifications

Run only after companion association and the external helper listener test succeed.

| Source/state | Companion | Listener | Seen | Eligible | Text >0 | Not redacted | Extracted | Queued | Claimed | Windows | ACK delete | Status |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Gmail visible | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | failed on prior build |
| Gmail background | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |
| DAWN-shaped mail | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | failed on prior build |
| Perceptron-shaped mail | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | failed on prior build |
| Locked/screen off | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | untested |

Never record the code or notification body.

## Queue, durability, and exactly-once

- [ ] 16 ordinary clipboard items accepted while peer disconnected
- [ ] item 17 rejected as `queue_full`
- [ ] accepted items remain ordered
- [ ] no accepted ordinary item expires or is evicted
- [ ] native acquisition never bypasses `ClipboardRelayStore`
- [ ] foreground runner drains in bounded order
- [ ] failed transport retains item after claim timeout
- [ ] reconnect applies each item once
- [ ] each item deletes only after peer ACK
- [ ] inbound ClipCascade writes update/suppress the observation baseline and do not echo
- [ ] helper notification receipt prevents duplicate application after active rescan

## Do not claim

CI proves source/build invariants only. `.22-alpha.1` remains unproven for HONOR/MagicOS native service survival, clipboard mutation timing, background Copy, CompanionDeviceManager OTP visibility, external listener delivery, Gmail/DAWN/Perceptron, lifecycle rows, exactly-once, battery, and tray behavior until the corresponding target rows pass.
