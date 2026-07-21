# Notification Listener Alpha Hardening Handoff — 2026-07-21

## User evidence

- `.17 / 320121` restored ordinary synchronization that had previously been broken.
- A real Gmail OTP has not yet arrived, so Gmail notification ingestion remains unproven.
- The next change must preserve the recovered synchronization path and Extended peer-applied ACK.

## Follow-up defects found

### 1. Existing synthetic test bypassed NotificationListener

`OtpTestNotificationManager.post()` displayed a local notification and immediately invoked `queueSyntheticValue(...)`. It deterministically tested extractor, persistent queue, transport, and ACK, but it could succeed while `NotificationListenerService` was completely dead.

### 2. Active rescan could resend one old notification

`.17` scans active notifications younger than 15 minutes on listener connection. `OtpRelayStore` deduplicates matching code content for only 90 seconds. A reconnect after that window could requeue the same still-active Gmail notification.

### 3. Synthetic marker trust was too broad

The listener treated the synthetic extra alone as trusted. An external app could theoretically set the same extra and bypass the selected-app filter. Alpha hardening requires both ClipCascade's own package and the marker.

## `.18-alpha.1 / 320122` implementation

- Adds a separate listener-path test notification that never directly queues its code.
- Keeps the existing direct deterministic component/transport test and explicitly filters that notification from listener processing to avoid double paths.
- Requires own package plus marker for synthetic-test privileges.
- Adds `NotificationDeliveryReceiptStore`:
  - one-way SHA-256 fingerprint only;
  - includes notification identity/timestamp and extracted value but persists none of them in recoverable form;
  - 30-minute TTL;
  - maximum 128 receipts;
  - atomic claim before queue insertion;
  - release on queue exception;
  - clearable with diagnostic history.
- Adds pure `NotificationReceiptPolicy` and unit tests for TTL boundary, stale/future rejection, and bounded newest retention.
- Adds content-free counters:
  - eligible notifications;
  - already-processed suppression;
  - listener-path test callbacks;
  - active-scan count and last candidate count;
  - latest callback delay.
- Preserves default-OFF transport-accepted debug notification.

## Transform order

`prepare_relay_claim.js` must apply, in order:

1. internal clipboard guard;
2. language-neutral Copy transform;
3. ACK-safe queue overflow transform;
4. Japanese Gmail anchor compatibility;
5. Gmail listener recovery/diagnostics;
6. debug notification icon compatibility;
7. notification-listener alpha hardening.

The alpha hardening script depends on the final `.17` generated listener, runtime store, settings UI, and strings. Do not move it earlier.

## Alpha release automation

A dedicated `Alpha prerelease` workflow runs only on a push commit whose message contains `[alpha-release]`.

It must:

- wait for matching-sha **push** runs of Android and Windows CI;
- abort if either required CI fails;
- download the Android artifact from the successful Android run;
- create APK, ZIP, and SHA256SUMS assets;
- create or idempotently refresh GitHub prerelease tag `v3.2.1-extended.18-alpha.1`;
- refuse to overwrite an existing tag that points to another commit.

## Required proof

- ordinary synchronization remains working;
- listener runtime is connected;
- component/transport test still reaches ACK;
- listener-path self-test increments listener-test/eligible/queued counters and reaches ACK without direct queueing;
- repeating active scan suppresses an already-processed notification;
- a new synthetic listener-test code is still accepted;
- transport debug switch works ON and OFF;
- real Gmail remains unclaimed until a real notification is observed.

## Preserve

- PR #1 open and Draft;
- no merge or Ready transition;
- Extended Windows-applied ACK before deletion;
- validation-before-ACK;
- ACK-safe ordinary queue;
- language-neutral Copy behavior;
- internal-write echo suppression;
- content-free diagnostics only.
