# Next ChatGPT Handoff

This is the canonical handoff. Read the listed documents before changing code.

## Repository and hard constraints

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- PR: `#1`
- PR state: open and Draft
- never mark Ready, merge, or enable auto-merge
- preserve Extended P2P Windows-applied ACK before native deletion
- run Android and Windows CI for every branch change
- record hypotheses, failures, artifacts, and unproven claims

## Read in this exact order

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. `docs/NEXT_CHATGPT_HANDOFF.md`
5. `docs/LATEST_NOTIFICATION_LISTENER_ALPHA_HANDOFF.md`
6. `docs/LATEST_GMAIL_NOTIFICATION_RELIABILITY_HANDOFF.md`
7. `docs/LATEST_GREEN_ARTIFACTS.md`
8. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
9. `docs/TEST_MATRIX.md`
10. `docs/LATEST_ACK_SAFE_QUEUE_OVERFLOW_HANDOFF.md`
11. `docs/LATEST_ACK_SAFE_CLIPBOARD_QUEUE_HANDOFF.md`
12. `docs/LATEST_LANGUAGE_NEUTRAL_COPY_HANDOFF.md`
13. `docs/LATEST_SELECTION_ONLY_COPY_FALSE_POSITIVE_HANDOFF.md`
14. `docs/LATEST_BACKGROUND_CLIPBOARD_INTERMITTENT_HANDOFF.md`
15. `docs/LATEST_OTP_SELF_TEST_HANDOFF.md`
16. `docs/LATEST_BROAD_OTP_EXTRACTION_HANDOFF.md`
17. `docs/LATEST_OTP_EMAIL_EXTRACTION_HANDOFF.md`
18. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
19. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
20. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
21. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`

## Current alpha implementation

- implementation SHA: `87e138380a139671168effd24a64df844f1bb879`
- commit message: `[alpha-release] Harden notification listener validation and receipts`
- tag: `v3.2.1-extended.18-alpha.1`
- versionName: `3.2.1-extended.18-alpha.1-standalone`
- versionCode: `320122`
- package: `com.clipcascade.extended`
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

Implementation CI:

- Android `29827698937` — success
- Windows `29827698930` — success

Artifact:

- Actions artifact ID: `8494023518`
- Actions artifact ZIP SHA-256: `ae84ba8adda4b0c33ac8dbd39f835fdf7be9142dea8788e579361f6b0917cbeb`
- extracted APK SHA-256: `53da5cae4b5e2c7dd5cad0e6064aec47945b9d9620fbd30d9edaf391d37ac88d`
- Actions artifact expiry: `2026-10-19T11:50:45Z`

The alpha tag was verified to resolve exactly to `87e13838...`.

## Target-device truth

The user reports that ordinary Android/Windows synchronization is working again on `.17`; it had been broken before. Protect this recovery above all else.

No real Gmail verification code arrived during the observation period. Real Gmail ingestion therefore remains untested—not failed in the alpha, and not proven.

Earlier Android-to-Windows “successes” contaminated by Microsoft Phone Link remain invalid. Disable Phone Link and competing clipboard synchronizers during every test.

## Alpha audit findings

### Existing OTP self-test did not prove NotificationListener

`OtpTestNotificationManager.post()` posted a local notification and directly invoked `queueSyntheticValue(...)`. It proved extractor, queue, transport, and ACK components, but could succeed with a dead listener.

### Active rescan could reconsider one notification

`.17` scans active notifications up to 15 minutes old. OTP queue content deduplication lasts 90 seconds. Reconnect/rescan after 90 seconds could send the same still-active notification again.

### Synthetic marker needed an origin boundary

Synthetic privilege now requires both ClipCascade's own package and the marker. External notifications cannot use the marker to bypass app selection.

## Alpha implementation details

### Two distinct self-tests

1. **Deterministic component/transport test**
   - directly exercises extractor, persistent queue, transport, and ACK;
   - intentionally does not claim listener coverage.

2. **Real listener-path self-test**
   - posts a marked local notification;
   - does not directly insert the code into the queue;
   - must pass through NotificationListener callback, text collection, extractor, persistent queue, transport, Windows application, peer ACK, and native deletion.

The listener ignores the component-test notification to prevent dual queue paths.

### Persistent notification receipt guard

`NotificationDeliveryReceiptStore` claims a one-way SHA-256 fingerprint before queue insertion.

- TTL: 30 minutes
- capacity: 128 newest receipts
- persisted fields: fingerprint and timestamp only
- fingerprint material includes notification identity/timestamp and extracted value, but none is recoverable from the stored digest
- queue exception releases the claim
- diagnostic clear also clears receipts

This receipt guard is not an ACK and must never delete queue items. It only suppresses reprocessing of the same source notification.

### Content-free listener diagnostics

- seen
- eligible
- already processed
- listener tests
- text available
- authentication hint
- queued
- no match
- empty
- filtered
- active scan count
- last active-scan candidate count
- last callback delay
- last collected part/character counts

Never add raw notification text, code, package/app name, account/email, relay ID, or server address to diagnostics.

## Final Android transform order

`prepare_relay_claim.js` applies:

1. `prepare_internal_clipboard_guard.js`
2. `prepare_language_neutral_clipboard_copy.js`
3. `prepare_ack_safe_queue_overflow.js`
4. `prepare_gmail_ja_anchor_compat.js`
5. `prepare_gmail_notification_reliability.js`
6. `prepare_debug_notification_icon_compat.js`
7. `prepare_notification_listener_alpha_hardening.js`

The alpha hardening transform depends on final `.17` generated source. Do not move it earlier.

## Release automation

`.github/workflows/alpha-prerelease.yml` runs only when a push commit message contains `[alpha-release]`.

It waits for matching-sha **push** Android and Windows CI, aborts on either failure, downloads the Android artifact, produces APK/ZIP/SHA256SUMS, and creates or idempotently refreshes the prerelease. It refuses to overwrite a tag pointing to another commit.

Tag `v3.2.1-extended.18-alpha.1` resolves to the implementation SHA. Direct asset enumeration was not available through the connector; the matching Actions artifact and local APK were independently downloaded and hashed.

## Copy/queue/ACK invariants — preserve

- selection alone never sends;
- OS clipboard mutation is primary Copy proof;
- internal writes are suppressed;
- ACTION_COPY/Ctrl+C fallback remains bounded and serial-cancelled;
- no ordinary clipboard TTL;
- no overflow eviction of accepted items;
- capacity 16 with explicit `queue_full` rejection;
- retry cap 15 seconds;
- Extended peer application precedes ACK;
- peer ACK precedes native deletion;
- validation-before-ACK remains;
- relay ID and native claim remain;
- old-peer compatibility fallback remains generation-scoped.

The transport debug notification proves local transport acceptance only. It is not peer application or ACK.

## Mandatory next target-device sequence

1. Install alpha over `.17` without uninstalling.
2. Confirm settings, notification access, Accessibility, and synchronization configuration survive.
3. Test one unique ordinary Copy first. Stop if ordinary synchronization regresses.
4. Confirm listener runtime reports connected.
5. Run deterministic component/transport test and verify Windows application plus ACK deletion.
6. Run listener-path self-test and verify:
   - listener-test counter increases;
   - eligible increases;
   - text/auth/queued stages advance;
   - Windows applies once;
   - peer ACK removes queue.
7. While its notification remains active, invoke reconnect/rescan. Verify `already_processed` increases and Windows does not apply it again.
8. Run a new listener-path test. Verify the new value is not blocked by the receipt guard.
9. Enable outbound debug notification, send once, verify it appears only after transport acceptance.
10. Disable it, send again, verify no debug notification.
11. When a real Gmail OTP arrives, clear diagnostics and record stage transitions without recording the code.
12. Continue ordinary multilingual/background/screen-off/long-disconnect/queue-full/exactly-once matrix only after the basic regression passes.

## Do not claim

CI does not prove alpha behavior on HONOR. Do not claim:

- ordinary synchronization remains fixed after alpha installation;
- listener-path self-test succeeds on target;
- receipt suppression works on target;
- real Gmail extraction;
- OEM listener survival;
- background/screen-off Gmail delivery;
- exactly-once target behavior;
- battery efficiency;
- Windows tray ghost prevention.

Keep PR #1 Draft.
