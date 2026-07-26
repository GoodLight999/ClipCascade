# Desktop Connection State Machine

Last updated: 2026-07-26 (Asia/Tokyo)

Status: **implemented for P2S STOMP transport and GUI/CLI status controls; P2P migration and live network-loss smoke testing remain**

## Purpose

Replace transport booleans, callback sleeps, recursive reconnect calls, and tray-owned assumptions with one authoritative connection model shared by the P2S transport and desktop user interfaces.

No server protocol was changed.

## Implemented source layout

```text
ClipCascade_Desktop/src/connection/
    __init__.py
    state.py
    errors.py
    retry.py
    controller.py
    tray_view.py

ClipCascade_Desktop/src/stomp_ws/
    client.py
    stomp_manager.py

ClipCascade_Desktop/src/gui/tray.py
ClipCascade_Desktop/src/cli/tray.py

ClipCascade_Desktop/tests/
    test_connection_controller.py
    test_retry_policy.py
    test_stomp_client.py
    test_stomp_manager.py
    test_tray_view.py
```

## Ownership rule

For P2S, `ConnectionController` owns connection truth.

- `STOMPManager` translates WebSocket/STOMP outcomes into stable controller events.
- GUI and CLI derive labels and primary actions from immutable snapshots.
- User interfaces do not decide whether P2S is connected by mutating a local boolean.
- P2P still uses the legacy boolean fallback and must be migrated separately.

## States

```text
DISCONNECTED
CONNECTING
CONNECTED
RECONNECT_WAIT
AUTH_REQUIRED
STOPPING
FATAL_ERROR
```

### `DISCONNECTED`

No active transport and no scheduled retry. An explicit connect may begin.

### `CONNECTING`

Exactly one WebSocket/STOMP connection attempt is active.

### `CONNECTED`

The WebSocket opened, the STOMP `CONNECTED` frame was processed, and the receive subscription callback completed successfully.

This is stricter than the upstream implementation, which could return after the underlying WebSocket opened but before STOMP readiness.

### `RECONNECT_WAIT`

No active connection attempt is running. Exactly one cancellable retry timer is scheduled. The snapshot exposes the attempt number and next retry time.

### `AUTH_REQUIRED`

The current authenticated session cannot recover automatically. Automatic retry is disabled. The current tray/CLI displays that login is required and directs the user to log off and sign in again.

A direct in-process login-screen reopening flow remains deferred.

### `STOPPING`

Explicit disconnect or shutdown is releasing the client and clipboard monitor. Retry callbacks cannot restart the transport.

### `FATAL_ERROR`

A non-recoverable local error prevents automatic connection attempts. An explicit retry can clear the state and start a new attempt.

## Events

```text
CONNECT_REQUESTED
CONNECT_SUCCEEDED
CONNECT_FAILED_RECOVERABLE
CONNECT_FAILED_AUTH
CONNECT_FAILED_FATAL
TRANSPORT_CLOSED_RECOVERABLE
TRANSPORT_CLOSED_AUTH
MANUAL_RECONNECT_REQUESTED
DISCONNECT_REQUESTED
RETRY_TIMER_EXPIRED
STOP_COMPLETED
CONFIGURATION_REPLACED
```

Unexpected transitions are rejected and emitted as structured diagnostics instead of silently mutating state.

## Required transition summary

| Current state | Event | Next state |
|---|---|---|
| `DISCONNECTED` | `CONNECT_REQUESTED` | `CONNECTING` |
| `CONNECTING` | `CONNECT_SUCCEEDED` | `CONNECTED` |
| `CONNECTING` | recoverable failure | `RECONNECT_WAIT` outside login phase |
| `CONNECTING` | auth failure | `AUTH_REQUIRED` |
| `CONNECTING` | fatal failure | `FATAL_ERROR` |
| `CONNECTED` | recoverable close | `RECONNECT_WAIT` |
| `CONNECTED` | auth close | `AUTH_REQUIRED` |
| `RECONNECT_WAIT` | timer expiry | `CONNECTING` |
| `RECONNECT_WAIT` | manual reconnect | `CONNECTING` |
| active state | explicit disconnect | `STOPPING` |
| `STOPPING` | stop completed | `DISCONNECTED` |
| auth/fatal state | configuration replaced | `DISCONNECTED` |

During the login phase, an initial recoverable connection failure is deliberately returned synchronously to the login flow instead of creating a hidden background retry loop.

## Retry policy

The implemented policy uses capped exponential backoff with jitter:

```text
base delay: 1 second
cap: 30 seconds
attempts: 1, 2, 3, ...
raw delay: min(30, 1 * 2^(attempt - 1))
jittered delay: uniformly selected from [0.5 * raw, raw]
```

Properties:

- one scheduled timer maximum;
- one active attempt maximum;
- timer waits are cancellable;
- explicit disconnect invalidates stale callbacks using a generation token;
- authentication and fatal errors do not retry;
- retry count resets only after 60 continuous seconds connected;
- tests inject clock, random source, and scheduler;
- socket callbacks never call `time.sleep()`.

## Public snapshot

```python
ConnectionSnapshot(
    state: ConnectionState,
    state_since: float,
    reconnect_attempt: int,
    next_retry_at: float | None,
    last_connected_at: float | None,
    last_disconnected_at: float | None,
    last_send_at: float | None,
    last_receive_at: float | None,
    last_error_code: str | None,
    last_error_message: str | None,
    last_error_recoverable: bool | None,
)
```

Monotonic timestamps are used for scheduling. Snapshot and diagnostics fields never contain clipboard payloads.

## Normalized errors

```text
NETWORK_UNREACHABLE
DNS_FAILURE
CONNECT_TIMEOUT
TLS_FAILURE
SERVER_UNAVAILABLE
SESSION_EXPIRED
AUTH_REJECTED
PROTOCOL_ERROR
LOCAL_CONFIGURATION_ERROR
TRANSPORT_CLOSED
UNKNOWN_TRANSPORT_ERROR
```

`ConnectionError` is classified before generic `OSError`, because Python's `ConnectionError` subclasses `OSError`. This avoids misclassifying a completed-but-closed handshake as generic network unreachability.

## STOMP handshake truth

`stomp_ws/client.py` now separates:

1. WebSocket open;
2. STOMP `CONNECT` transmission;
3. STOMP `CONNECTED` receipt;
4. subscription callback completion.

`connect()` returns successfully only after all four stages complete. It unblocks and raises on:

- WebSocket open timeout;
- STOMP handshake timeout;
- STOMP `ERROR` frame;
- socket error before readiness;
- close before `CONNECTED`;
- subscription callback failure.

Explicit disconnect suppresses the remote-close callback so an intentional stop cannot schedule a reconnect.

## P2S manager integration

`STOMPManager` now:

- owns one `ConnectionController`;
- creates a fresh STOMP client per connection attempt;
- removes fixed-delay callback sleeps and recursive reconnect calls;
- schedules retries through the controller;
- starts clipboard monitoring once across reconnects;
- records last accepted send and last valid receive times;
- returns snapshot-derived status text through `get_stats()`;
- exposes `get_connection_snapshot()` to GUI and CLI;
- emits lost/restored notifications on runtime transitions;
- cleans already-disconnected resources without emitting an invalid state transition.

`last_send_at` means the local client accepted the STOMP frame without raising. It is not treated as server-level delivery acknowledgement.

## GUI and CLI contract

`connection/tray_view.py` maps P2S snapshots to one primary action:

| State | Primary control |
|---|---|
| `DISCONNECTED` | Connect |
| `CONNECTING` | Cancel connection |
| `CONNECTED` | Disconnect |
| `RECONNECT_WAIT` | Reconnect now |
| `AUTH_REQUIRED` | Disabled login-required explanation |
| `STOPPING` | Disabled stopping indicator |
| `FATAL_ERROR` | Retry connection |

GUI and CLI use the same pure mapping. If an interface does not expose snapshots, the mapper deliberately falls back to the previous `is_connected` behavior for P2P compatibility.

## Automated verification

The unit suite currently covers:

- initial disconnected state;
- duplicate attempt suppression;
- recoverable retry scheduling;
- manual retry and cancellation;
- stale timer rejection;
- authentication and fatal stops;
- stable-period retry reset;
- observer and scheduler failures;
- send/receive observations;
- STOMP success, timeout, ERROR, early close, socket error, callback failure, and explicit disconnect;
- P2S manager connection, runtime close, manual and automatic recovery, auth stop, send/receive observations, and disconnect cancellation;
- disconnected cleanup and exception classification;
- every GUI/CLI primary-action mapping and P2P fallback.

The suite runs on both `ubuntu-latest` and `windows-latest`. The complete Windows executable and Linux package also build in the same workflow.

## Acceptance progress

| Gate | Status |
|---|---|
| Pure model tests pass on Windows and Linux | Complete |
| Fake transport reconnect/cancellation/auth/shutdown tests | Complete |
| Actual STOMP readiness precedes connected state | Complete |
| Snapshot-derived `get_stats()` | Complete |
| P2S GUI/CLI actions derived from snapshots | Complete |
| Windows executable builds | Complete |
| Linux package builds | Complete |
| Real server login/connect smoke test | Not yet performed |
| Forced network-loss and automatic-recovery smoke test | Not yet performed |
| P2P migration to same controller | Not yet implemented |
| Persistent full status window | Not yet implemented |
| Diagnostics export | Not yet implemented |

## Deferred decisions

- application-level acknowledgement and durable queue deletion;
- P2P peer/signaling state integration;
- reconnect-capable in-process reauthentication UI;
- full diagnostics bundle schema;
- persistent status-window layout;
- event-driven CLI redraw instead of one-second status polling.

## Exact next desktop actions

1. Perform a real Windows P2S smoke test against the unchanged public server.
2. Force DNS/network loss, verify countdown, manual reconnect, automatic recovery, and quit.
3. Record logs and observed snapshots without clipboard contents.
4. Add a diagnostics/status window backed by the existing snapshot rather than another state store.
5. Characterize P2P signaling callbacks, then migrate P2P through a separate tested adapter.
