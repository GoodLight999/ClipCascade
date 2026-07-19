# Gmail / Notification Listener Reliability Handoff — 2026-07-19

## User report and corrected status

The user reports that ClipCascade has never relayed a verification code from a Gmail notification. Therefore the pre-`.17` real Gmail path is treated as non-functional, not merely untested. Synthetic extractor/queue tests are not evidence that Gmail notification ingestion works.

No real Gmail success is claimed by this handoff. `.17` creates a recoverable and diagnosable path that now requires isolated HONOR target-device proof.

## Static defects found

The pre-`.17` implementation had several recovery and observability defects:

1. Android notification-access authorization was displayed as if it proved a live `NotificationListenerService` binding. It did not expose whether a listener instance was connected in the current process.
2. Many notification gates returned silently: relay disabled, selected-app mismatch, self notification, ongoing/foreground notification, missing notification object, and text without authentication context.
3. `onListenerConnected()` did not inspect already-active notifications. A Gmail notification posted while the listener generation was dead could be missed permanently after rebind.
4. Listener recovery depended mainly on `onListenerDisconnected()`. OEM/process death can remove the listener generation without a useful callback in the surviving app generation.
5. Notification text collection did not expose even content-free part/character counts, so `listener never received Gmail` was indistinguishable from `Gmail exposed no usable extras` or `extractor rejected available text`.

## Implemented `.17 / 320121` repair

Implementation anchor:

- source SHA: `a010d7f0fb3871252580666df3264980b32c93cb`;
- versionName: `3.2.1-extended.17-standalone`;
- versionCode: `320121`;
- package: `com.clipcascade.extended`;
- signer unchanged.

Notification-listener behavior:

- tracks actual listener connection state separately from Android authorization;
- requests listener rebind from settings and the existing WorkManager heartbeat;
- requests rebind after `onListenerDisconnected()`;
- scans up to 64 currently active notifications younger than 15 minutes whenever the listener connects;
- provides a manual `Reconnect listener and scan current notifications` action;
- records content-free stages/counters: seen, text available, authentication hint, queued, deduplicated, no match, empty extras, source-filtered, active scans, last part count, and last collected character count;
- expands bounded text collection to known notification fields, text arrays, MessagingStyle current/historic messages, public-version extras, nested bundles/lists/arrays, and `SparseArray` values to depth four;
- never persists raw notification text, codes, account identifiers, email addresses, app names, or package names.

Extractor tests:

- Gmail-shaped title + preview + expanded-body layout containing the Perceptron-style alphanumeric code;
- negative Gmail layout with authentication wording but no code, proving the extractor does not invent a value.

## Outbound debug notification switch

A new settings switch defaults to OFF. When enabled, Android displays a temporary local notification only after:

- the P2S publish call reports acceptance; or
- at least one open P2P DataChannel accepts the outbound item.

The notification contains only:

- source class: ordinary copied text, verification code, or manual shared data;
- transport mode: P2P or P2S.

It contains no clipboard text, verification value, relay ID, package name, account identifier, or server address. Notification creation is wrapped independently: failure cannot change transport acceptance, relay claims, ACK ordering, retry behavior, or queue deletion.

## Trial and error retained

1. Android CI `29681080924` stopped in the final transform. No APK was produced.
2. A dedicated transform-log artifact was added. Android CI `29681226875` then identified an exact Japanese resource-anchor mismatch: the existing Japanese explanation included `抽出した`, while the new transform expected the otherwise equivalent shorter wording. `prepare_gmail_ja_anchor_compat.js` now normalizes that anchor before the Gmail transform.
3. Android CI `29681300642` passed every transform invariant and JavaScript bundling, then failed Kotlin compilation because the debug notifier referenced nonexistent native drawable `ic_small_icon`. The notifier now uses the existing native `ic_notification_failure` resource.
4. Corrected implementation `a010d7f...` passed Android and Windows CI.

These were real implementation/integration defects, not infrastructure noise, and remain recorded.

## CI and artifact evidence

- Android standalone CI: `29681462233` — success;
- Desktop Windows CI: `29681462236` — success;
- Android artifact ID: `8440717410`;
- artifact ZIP SHA-256: `5e545d9210a97819cfae79bde5a278e69631b395f55aa6cad0f260e4cd38134e`;
- extracted APK SHA-256: `f3bba473b78d1f44f73fe529cd6c0187881269615aeb709651a6f8cd675ffb86`;
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`;
- artifact expiry: `2026-10-17T09:22:32Z`.

Android CI passed production-order transforms, Gmail-shaped OTP tests, all retained Copy/queue/retry invariants, JavaScript bundle generation, Kotlin/resources, APK assembly, embedded-bundle verification, deterministic signer verification, and artifact upload.

Windows CI revalidated authenticated HTTP handling, Extended peer-applied ACK, validation-before-ACK, shutdown/tray behavior, and EXE packaging. No Windows implementation or ACK semantics changed in `.17`.

## Preserve

- language-neutral Copy confirmation;
- internal clipboard-write guard;
- ACK-safe bounded ordinary clipboard queue;
- Extended P2P Windows-applied ACK before native deletion;
- validation-before-ACK;
- native relay claims;
- generation-scoped old-peer fallback;
- OTP persistent queue and deterministic synthetic self-test;
- PR #1 Draft state.

## Required real-device proof

1. Install `.17` over the existing stable-signed build without uninstalling.
2. Open Extended settings and confirm both notification authorization and actual listener runtime show connected.
3. Enable the outbound debug notification only for the diagnostic run.
4. Clear diagnostics/counters.
5. Send one Gmail message whose expanded notification visibly contains a fresh synthetic OTP.
6. Record only the content-free stage values:
   - listener disconnected or seen remains zero: listener binding/delivery failure;
   - seen increases, text characters remain zero: Gmail/Android exposed no text extras;
   - text characters increase, auth hint remains zero: Gmail preview omitted authentication wording;
   - auth hint and no-match increase: extractor defect;
   - queued increases: notification ingestion succeeded;
   - outbound debug notification appears: transport accepted the queued value;
   - peer ACK removes the queue: full Extended P2P application/ACK path succeeded.
7. Repeat while backgrounded and after removing the app from recents.
8. Leave a Gmail OTP notification active, invoke manual reconnect/rescan, and confirm an active notification younger than 15 minutes is reconsidered.
9. Turn the outbound debug notification switch OFF after the diagnostic run and confirm no further debug notifications appear.

No real Gmail success is claimed until these target-device steps pass.
