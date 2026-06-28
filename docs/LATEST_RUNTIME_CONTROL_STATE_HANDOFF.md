# Android Runtime Control State Handoff — 2026-06-28

## User report

The Android screen already displayed `Connected` / `接続済み` before the user pressed the upper Start button, but that button still displayed `Start` / `開始`.

## Correct behavior

A connection may already be active before the UI is opened because the foreground synchronization service can survive the UI, resume a persisted session, or be recreated by recovery. In that case:

- `Connected` / `接続済み` is valid;
- the upper control must display `Stop` / `停止` immediately;
- pressing it must stop the already-running synchronization service.

The button is a service Start/Stop toggle, not a separate authentication or connection-establishment button.

## Confirmed defect

`pollUIFlags()` read the persisted `wsIsRunning` value and used it to refresh status text and P2P details, but did not copy that value into the React `wsIsRunning` state that renders the Start/Stop control.

Therefore an independently resumed service could produce:

- persisted runtime state: `true`;
- displayed connection status: connected;
- React button state: stale `false`;
- displayed button label: Start.

## Repair

Build transform `prepare_runtime_control_state.js` now synchronizes every valid persisted `wsIsRunning` value into React state during the existing 300 ms UI polling loop.

Result:

- runtime `true` -> red `Stop` / `停止` control;
- runtime `false` -> green `Start` / `開始` control;
- service recovery, process replacement, UI reopen, and manual start/stop are reflected without requiring another button press.

Existing transport, relay queues, relay claims, and P2P peer-applied acknowledgement are unchanged.

## Build identity

- versionName: `3.2.1-extended.7-standalone`
- versionCode: `320111`
- package: `com.clipcascade.extended`
- deterministic test signer unchanged

## Mandatory real-device validation

1. Install versionCode `320111` over the existing stable-signed build.
2. Leave synchronization running, close only the Android UI, and reopen it.
3. Confirm the status is connected and the upper button displays Stop immediately when the synchronization service is active.
4. Press Stop once and confirm status becomes disconnected and the button becomes Start.
5. Press Start once and confirm the button becomes Stop after the runtime flag changes.
6. Repeat after automatic recovery and after device reboot with startup resume enabled.
7. Confirm no extra service generation or duplicate clipboard send is created by merely reopening the UI.

PR #1 remains Draft.
