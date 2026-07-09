# Windows Tray Ghost Icon Handoff — 2026-07-09

## User report

Windows 11 notification area can accumulate many ClipCascade-looking default/ghost tray icons. Observed state:

- live tray icon count: one;
- ghost icons: many;
- ghost tooltip: `ClipCascade`;
- ghost owner PID: `0`, meaning the owning HWND/process is already gone;
- the issue existed in the upstream desktop build before Extended changes;
- Extended watchdog/full restart made the symptom much worse.

Relevant runtime evidence supplied by the user:

- executable: `ClipCascade-Windows-981dae7.exe`;
- pystray tray code: `gui/tray.py` and `gui/enhanced_tray.py`;
- repeated log entries: `scheme http is invalid - goodbye`;
- watchdog log entries around once per minute: `Watchdog is performing a full synchronization restart`;
- repeated `Restarting synchronization engine`;
- persisted configuration still looked correct:
  - `server_url=https://clipcascade.sathvik.dev`
  - `websocket_url=wss://clipcascade.sathvik.dev/p2psignaling`
  - `server_mode=P2P`

Interpretation: this is not simply stale persisted `http://` configuration. It is likely a combination of transport/reconnect failure and tray lifecycle cleanup weakness.

## Fix strategy

Priority order:

1. Stop tray leaks first, without changing relay/ACK semantics.
2. Make watchdog full restart bounded and back off on fatal scheme errors.
3. Log P2P websocket URL/scheme and last transport error so `scheme http is invalid` can be tied to the actual runtime value.
4. Add tests for tray lifecycle ordering and diagnostics.

## Patch

Build transform: `ClipCascade_Desktop/src/scripts/prepare_windows_tray_lifecycle.py`

### Tray lifecycle

The transform updates `gui/tray.py` and `gui/enhanced_tray.py` so that:

- only one active `TaskbarPanel` owns a pystray icon per process;
- constructing a replacement panel disposes the previous icon first;
- all shutdown paths use one idempotent `_dispose_tray_icon(reason)` method;
- disposal order is explicit: `icon.visible = False` first, then `icon.stop()`;
- `atexit` also attempts disposal for normal interpreter shutdown;
- create/run/visible-false/stop events are logged with panel id, icon id, process id, and reason.

This is intended to force pystray's Windows backend through the Shell_NotifyIcon delete path before the tray window disappears.

### Watchdog bounds

The transform updates `core/windows_application.py` so that:

- restart diagnostics include manager type and transport diagnostics;
- full restarts are capped at three consecutive attempts;
- after the cap, watchdog backs off for 15 minutes;
- if a manager reports a fatal websocket scheme error, watchdog suppresses full restart and backs off for 15 minutes instead of restarting once per minute;
- restart code does not create or touch tray icons.

### P2P scheme diagnostics

The transform updates `p2p/p2p_manager.py` so that:

- every P2P signaling connection logs `websocket_url`, parsed scheme, and `server_url`;
- `websocket_url` is validated before constructing `websocket.WebSocketApp`;
- only `ws` and `wss` are accepted;
- WebSocket errors are logged at warning level with URL and scheme;
- the latest transport error is stored in memory and exposed through `get_connection_diagnostics()`;
- `scheme http is invalid - goodbye` is classified as a fatal scheme error even when the current config still shows `wss://`, so watchdog stops amplifying it.

## Tests

New test file: `ClipCascade_Desktop/src/tests/test_windows_tray_lifecycle.py`

Coverage:

- tray disposal sets `visible=False` before `stop()`;
- disposal is idempotent;
- replacing an active panel disposes the previous icon;
- P2P scheme diagnostics accept `wss://` and reject `http://`;
- remembered `scheme http is invalid - goodbye` is treated as fatal while preserving the current runtime URL/scheme diagnostic.

Desktop CI now runs:

- `scripts/prepare_windows_tray_lifecycle.py` before compilation;
- `tests.test_windows_tray_lifecycle` before PyInstaller packaging.

Release packaging runs the same transform and tests.

## Required real-device validation

1. Start ClipCascade normally and confirm exactly one live tray icon.
2. Force server/signaling failure and allow at least 10 reconnect/restart cycles.
3. Confirm the tray icon count remains one.
4. Confirm log contains `TrayIcon lifecycle created`, `run_detached`, and on quit `visible_false` before `stop_complete`.
5. Quit from tray and confirm no `ClipCascade` ghost remains without restarting Explorer.
6. If `scheme http is invalid - goodbye` reappears, capture the adjacent P2P diagnostics lines and confirm whether runtime `websocket_url` was still `wss://`.

## CI status

Code commit containing the transform and tests: `c34057a9cfd10599db68e32f6a98f576d9bc8ed2`.

At handoff-writing time, Android CI run `29014804216` and Windows CI run `29014804212` had been triggered but were still queued by GitHub Actions, not completed. Do not claim this patch green until those final runs complete successfully.

PR #1 remains Draft.
