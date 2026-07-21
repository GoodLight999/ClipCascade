# Notification Listener Alpha Hardening Handoff — 2026-07-21

## User evidence

- `.17 / 320121` restored ordinary synchronization that had previously been broken.
- A real Gmail OTP has not yet arrived, so Gmail notification ingestion remains unproven.
- The alpha must preserve the recovered synchronization path and Extended peer-applied ACK.

## Follow-up defects found

### 1. Existing synthetic test bypassed NotificationListener

`OtpTestNotificationManager.post()` displayed a local notification and immediately invoked `queueSyntheticValue(...)`. It deterministically tested extractor, persistent queue, transport, and ACK, but it could succeed while `NotificationListenerService` was completely dead.

### 2. Active rescan could resend one old notification

`.17` scans active notifications younger than 15 minutes on listener connection. `OtpRelayStore` deduplicates matching code content for only 90 seconds. A reconnect after that window could requeue the same still-active Gmail notification.

### 3. Synthetic marker trust was too broad

The listener treated the synthetic extra alone as trusted. The alpha requires both ClipCascade's own package and the marker before bypassing normal selected-app handling.

## Implemented alpha

- implementation SHA: `87e138380a139671168effd24a64df844f1bb879`
- tag: `v3.2.1-extended.18-alpha.1`
- versionName: `3.2.1-extended.18-alpha.1-standalone`
- versionCode: `320122`
- Android CI: `29827698937`, success
- Windows CI: `29827698930`, success
- Actions artifact ID: `8494023518`
- Actions artifact ZIP SHA-256: `ae84ba8adda4b0c33ac8dbd39f835fdf7be9142dea8788e579361f6b0917cbeb`
- APK SHA-256: `53da5cae4b5e2c7dd5cad0e6064aec47945b9d9620fbd30d9edaf391d37ac88d`
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- artifact expiry: `2026-10-19T11:50:45Z`

The alpha tag resolves exactly to the implementation SHA.

## Two separate OTP self-tests

### Deterministic component/transport test

The previous test remains, but its UI wording now states its actual scope. It posts a notification and directly queues the locally extracted value. The NotificationListener explicitly filters this component-test notification to avoid a second queue path.

It verifies:

`EXTRACTOR -> OTP_QUEUE -> TRANSPORT -> WINDOWS_APPLIED -> PEER_ACK -> DELETE`

### Real listener-path self-test

The new test posts a separate marked local notification and never calls the direct queue helper. It can reach ACK only through:

`NOTIFICATION_POSTED -> LISTENER_CALLBACK -> TEXT_COLLECTION -> EXTRACTOR -> OTP_QUEUE -> TRANSPORT -> WINDOWS_APPLIED -> PEER_ACK -> DELETE`

Synthetic privilege requires:

- `posted.packageName == packageName`; and
- the synthetic marker; and
- listener-path mode for listener processing.

## Persistent receipt guard

`NotificationDeliveryReceiptStore` claims a one-way SHA-256 fingerprint immediately before queue insertion.

- fingerprint input includes notification identity/timestamp and extracted value;
- only digest and timestamp are stored;
- raw notification text, code, package, key, tag, and account data are not stored in recoverable form;
- TTL is 30 minutes;
- capacity is 128 newest entries;
- a repeated active notification is diagnosed as `already_processed` and not requeued;
- queue exceptions release the claim, preserving retryability;
- diagnostic clear also clears receipts.

This receipt is not a delivery ACK and never removes queued data.

Pure tests cover:

- TTL boundary remains active;
- stale, future, and zero timestamps are inactive;
- newest receipt set remains bounded to 128.

## Expanded content-free diagnostics

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
- active scans
- latest active-scan candidate count
- latest callback delay
- latest collected parts/characters

No content-bearing field was added.

## Transform order

`prepare_relay_claim.js` applies:

1. `prepare_internal_clipboard_guard.js`
2. `prepare_language_neutral_clipboard_copy.js`
3. `prepare_ack_safe_queue_overflow.js`
4. `prepare_gmail_ja_anchor_compat.js`
5. `prepare_gmail_notification_reliability.js`
6. `prepare_debug_notification_icon_compat.js`
7. `prepare_notification_listener_alpha_hardening.js`

The alpha hardening script depends on the final `.17` generated listener, runtime store, settings UI, and strings. Do not move it earlier.

## Alpha release automation

`.github/workflows/alpha-prerelease.yml` runs only for a push commit containing `[alpha-release]`.

It:

- waits for matching-sha push Android and Windows CI;
- aborts if either required CI fails;
- downloads the successful Android artifact;
- creates APK, ZIP, and SHA256SUMS assets;
- creates or refreshes prerelease tag `v3.2.1-extended.18-alpha.1`;
- refuses to overwrite a tag that points to another commit.

The connector verified the tag target. Direct release-asset enumeration was unavailable; use the matching Actions artifact hashes as the independently verified build record.

## Preserved behavior

- ordinary synchronization logic was not directly changed;
- no Windows implementation file changed;
- language-neutral Copy design remains;
- internal-write echo suppression remains;
- no ordinary queue TTL or overflow eviction;
- capacity 16 and `queue_full` remain;
- Extended Windows-applied ACK remains before deletion;
- validation-before-ACK, relay IDs, claims, and fallback remain;
- outbound debug notification defaults OFF and is ACK-isolated.

## Required target proof

1. In-place install over `.17`.
2. Confirm settings and permissions survive.
3. Confirm one ordinary Copy syncs before OTP testing.
4. Confirm listener connected.
5. Run component/transport test and verify ACK.
6. Run listener-path self-test and verify listener-test, eligible, queued, Windows applied once, and ACK deletion.
7. Reconnect/rescan while the test notification remains active; verify `already_processed` without another Windows application.
8. Run a fresh listener-path test; verify a new notification is accepted.
9. Test outbound debug ON and OFF.
10. Test real Gmail only when an actual Gmail OTP arrives.

## Do not claim

No real Gmail, HONOR listener-path, duplicate receipt, alpha ordinary-sync regression, screen-off, battery, exactly-once, or tray success is claimed from CI.

PR #1 must remain open and Draft.
