# ClipCascade Extended 3.2.1-extended.18-alpha.1

This is an **alpha prerelease** for isolated Android validation. Install it over the previous deterministic-signed Extended build without uninstalling.

## Why this alpha exists

The `.17` build restored ordinary synchronization on the target device, but no real Gmail verification notification has yet been available for confirmation. Static follow-up debugging found two remaining weaknesses in the notification-code validation path:

- the deterministic synthetic OTP test posted a notification and then directly queued the same code, so it did not prove that Android's actual `NotificationListenerService` callback worked;
- active-notification rescans could reconsider the same Gmail notification after the OTP queue's 90-second content-deduplication window elapsed.

## Alpha changes

- Adds a separate **real notification-listener path self-test**. It posts a marked local notification but does not directly queue the value. Success requires the actual listener callback, text collection, extractor, persistent OTP queue, transport, and ACK path.
- Keeps the previous deterministic component/transport test as a separate test.
- Accepts synthetic-test markers only from ClipCascade's own package; another app cannot spoof the marker to bypass selected-app filtering.
- Adds a persistent, bounded 30-minute one-way receipt fingerprint for successfully claimed notification/code pairs, preventing reconnect/rescan from sending the same active notification again after 90 seconds.
- Rolls back the receipt claim if queue insertion throws, preserving retryability.
- Adds content-free counters for eligible notifications, already-processed suppression, listener-path test callbacks, active-scan candidates, and callback delay.
- Keeps the outbound transport debug notification default OFF.

## Preserved invariants

- selection alone never sends;
- language-neutral clipboard mutation remains the primary Copy proof;
- internal ClipCascade clipboard writes remain suppressed;
- accepted ordinary clipboard items have no wall-clock expiry and are never evicted before ACK;
- queue capacity remains 16 with explicit `queue_full` rejection;
- Extended P2P deletion still requires Windows clipboard application followed by peer ACK;
- validation-before-ACK, relay IDs, native relay claims, and old-peer compatibility fallback remain unchanged.

## Validation order

1. Confirm ordinary Android-to-Windows synchronization still works once.
2. Open Extended settings and confirm the notification listener shows connected.
3. Run **real notification-listener path self-test**.
4. Confirm the listener-test counter increments, the code is queued, Windows applies it once, and peer ACK removes it.
5. Enable outbound debug notification temporarily and confirm it appears only after transport acceptance.
6. Disable the debug switch and confirm subsequent accepted sends produce no debug notification.
7. When a real Gmail OTP becomes available, clear diagnostics and record the first failed or completed stage without storing the code.

## Status and limitations

This prerelease is CI-validated but not a claim that Gmail works on the HONOR target. Real Gmail extras exposure, OEM listener survival, background/screen-off Gmail delivery, and active-notification rescan behavior still require device evidence.

PR #1 remains Draft. This alpha must not be treated as a stable release.
