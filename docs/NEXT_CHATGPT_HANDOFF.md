# Next ChatGPT Handoff

Read these first, in this order:

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. `docs/LATEST_SELECTION_ONLY_COPY_FALSE_POSITIVE_HANDOFF.md`
5. `docs/LATEST_BACKGROUND_CLIPBOARD_INTERMITTENT_HANDOFF.md`
6. `docs/LATEST_OTP_SELF_TEST_HANDOFF.md`
7. `docs/LATEST_BROAD_OTP_EXTRACTION_HANDOFF.md`
8. `docs/LATEST_OTP_EMAIL_EXTRACTION_HANDOFF.md`
9. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
10. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
11. `docs/LATEST_ANDROID_INIT_DUPLICATE_HANDOFF.md`
12. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
13. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`
14. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`

## Current branch and PR

- Branch: `stability-mobile-otp`
- PR: `#1`
- PR status: Draft; keep it Draft.

## Latest user report

The user is still on an older Android build and reports that merely selecting text can relay the selected value without pressing Copy.

Audit result: `.12` did not fully fix this. Selection itself only remembered the range, but opening the floating selection toolbar could emit passive window events whose source node contained `Copy` / `コピー`. The generic `.12` marker logic could mistake toolbar appearance for copy completion.

## Latest repair

Android build target:

- `3.2.1-extended.13-standalone`
- versionCode `320117`

Validated code head before documentation commits:

- `594c37224bdcfbd96c54b41bd0509cf97f22ef5b`
- Android CI `29632041698`: success
- Windows CI `29632041722`: success

Implemented:

1. Added `CopyCueClassifier`.
2. `ACTION_COPY` remains authoritative.
3. Generic `Copy` / `コピー` is accepted only on direct click or context-click events.
4. Passive announcement, notification-state, window-state, and window-content events require completion wording such as `Copied`, `Copied to clipboard`, `コピーしました`, `クリップボードにコピーしました`, or `コピー済み`.
5. A passive toolbar label containing only `Copy` / `コピー` is rejected.
6. The three-second recent-selection plus real clipboard-change fallback remains.
7. `ClipboardWriteGuard` still suppresses ClipCascade's own inbound/local clipboard writes.
8. Unit tests cover selection-toolbar rejection, direct-copy acceptance, completion acceptance, and ordinary explanatory text rejection.

Preserved:

- persistent clipboard and OTP queues;
- relay claim protection;
- Extended P2P peer-applied ACK;
- old-peer compatibility fallback;
- React-generation recovery;
- content-free diagnostics;
- 3-second foreground-service flag polling;
- shared AsyncStorage lifecycle repair;
- deterministic signer.

## Mandatory next target-device proof

Disable Microsoft Phone Link and every competing clipboard synchronizer.

1. Install `.13 / 320117` over the current app without uninstalling.
2. Confirm Accessibility and synchronization remain enabled.
3. Select a unique value and leave the floating toolbar open without pressing Copy.
4. Confirm Windows receives nothing and the native pending queue does not increase.
5. Dismiss the selection and confirm there is no delayed relay.
6. Repeat in Chrome, Firefox-family browser, Gmail, a notes/editor app, and at least one WebView app.
7. Then actually press Copy and confirm exactly one Windows clipboard application and peer ACK.
8. Repeat visible, backgrounded, removed from recents, locked, and screen-off.
9. After any miss, record only:
   - ordinary-copy pending queue count;
   - latest ordinary-copy diagnostic path/result;
   - latest recovery path/result.
10. Retry the Perceptron/Gmail notification and record whether verification diagnostics show `empty`, `no_match`, or `queued`.

## Important limitation

The `.13` classifier and build are CI-validated, not target-device validated. Do not claim selection-only suppression, background copy reliability, screen-off support, exactly-once delivery, or real Perceptron/Gmail extraction until the isolated device matrix passes.

Windows tray validation remains pending: startup, forced signaling failure, 10+ reconnect/restart cycles, tray Quit, and no ghost icon without restarting Explorer.
