# ClipCascade Extended 3.2.1-extended.19-alpha.1

This is an **alpha prerelease** for isolated Android background outbound validation. Install it over the previous deterministic-signed Extended build without uninstalling.

## Why this alpha exists

Target-device evidence corrected the previous interpretation:

- Android-to-Windows **receive** remains functional while ClipCascade is not open;
- Android outbound text works while the app is open;
- Android outbound Copy detection fails when the app is not open;
- a real Gmail verification notification with a standard standalone six-digit login-code layout was not relayed.

The failure boundary is therefore Android-side background event capture, not the Windows receive path.

## What upstream ClipCascade originally did

The original Android client registered `ClipboardManager.OnPrimaryClipChangedListener`, but modern Android suppresses reliable background clipboard access. Its background workaround required ADB-granted `READ_LOGS`, watched `ClipboardService` denial logs, and launched a temporary focusable overlay activity to read the clipboard.

That mechanism is intentionally **not** restored because Extended must remain ADB-free and must not reintroduce privileged log reading or overlay setup.

The referenced `ClipCascade_go_fork` currently contains server and desktop clients, not an Android clipboard detector.

## Changes in this alpha

- Adds a conservative Accessibility fallback for explicit Copy clicks.
- Retrieves Android's own localized framework labels through `android.R.string.copy` and `android.R.string.copyUrl`.
- Works from the current system locale rather than hard-coding Japanese and English.
- Requires an exact normalized framework-label match on a click/context-click event.
- Selection alone still never sends.
- Waits 700 ms and runs only when no OS clipboard callback changed the serial.
- Uses the remembered Accessibility selection when Android suppresses background clipboard reads.
- Keeps native persistent queueing and Extended peer-applied ACK semantics.
- Adds a separate content-free `Copy detection` diagnostic row showing the latest OS callback, semantic Copy action, framework-label click, and delayed fallback decision.
- Adds positive locale tests and negative tests for `Copied`, `Copy all`, `Paste`, arbitrary selected text, and blank values.
- Adds a DAWN-shaped standalone numeric login-code extractor regression using a synthetic value; no real code or email content is stored in the repository.
- CI explicitly rejects reintroduction of `READ_LOGS` and `SYSTEM_ALERT_WINDOW`.

## Preserved behavior

- internal ClipCascade clipboard writes remain suppressed;
- accepted ordinary relay items do not expire by age;
- accepted items are never evicted before ACK;
- queue capacity remains 16 with explicit `queue_full` rejection;
- Extended P2P deletes only after Windows applies the text clipboard and sends peer ACK;
- notification-listener self-test, Gmail diagnostics, receipt guard, and outbound debug notification remain intact;
- Windows implementation is unchanged.

## Mandatory test order

1. Install over the previous alpha without uninstalling.
2. Confirm settings and permissions survive.
3. Disable Phone Link and every competing clipboard synchronizer.
4. With ClipCascade open, copy a unique value and confirm one Windows application plus ACK deletion.
5. Put ClipCascade in the background without force-stopping it.
6. Select text and press the Android/system Copy item.
7. Inspect `Copy detection`:
   - `system-localized Copy click / Accessibility + Android framework label / recovery requested` means the new cue was seen;
   - `... / delayed Copy fallback / recovery requested` means no OS callback arrived and the fallback fired;
   - no Copy-detection record means the OEM/app did not expose the toolbar click to Accessibility.
8. Inspect `Clipboard capture` for queue/no-text/deduplication result.
9. Verify Windows applies exactly once and the queue is removed only after peer ACK.
10. Repeat after removal from recents, lock, and screen-off only after the background row succeeds.
11. For Gmail, clear notification diagnostics before a fresh message and record connected/seen/eligible/text/auth/queued boundaries without recording the code.

## Limitations

This alpha does not claim universal background clipboard capture. Some apps or OEM toolbars may not expose their Copy menu item as an Accessibility click. The framework-label fallback is a standards-based, multilingual attempt that remains constrained by Android's security model.

A successful extractor unit test does not prove that Gmail exposed the code in notification extras or that NotificationListener received the notification on HONOR/MagicOS.

PR #1 remains Draft.
