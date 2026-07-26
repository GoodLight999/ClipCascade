# Desktop Connection State Machine

Last updated: 2026-07-26 (Asia/Tokyo)

Status: implementation contract; product code not yet migrated

## Purpose

Replace the current collection of transport booleans, fixed sleeps, recursive reconnect calls, and tray-owned assumptions with one authoritative state model shared by the WebSocket implementation and every desktop UI.

This specification applies first to the P2S STOMP client. P2P integration must consume the same public snapshot contract rather than adding a separate UI state vocabulary.

## Non-goals for the first change

- No server protocol changes.
- No durable clipboard queue yet.
- No redesign of authentication screens.
- No Android changes.
- No broad visual redesign.
- No attempt to infer delivery acknowledgement semantics that the server does not expose.

## Ownership rule

The transport layer owns connection truth.

The tray, CLI, login flow, and future status window may request actions and subscribe to snapshots, but must not maintain an independent `is_connected` value.

A state transition is valid only when it passes through the state-machine API. Direct mutation from callbacks or UI code is prohibited.

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

No active transport and no scheduled automatic retry.

Entered after:

- application initialization;
- an explicit user disconnect;
- completion of shutdown;
- cancellation of a pending reconnect.

Permitted actions:

- connect;
- quit;
- log off.

### `CONNECTING`

Exactly one connection attempt is active.

Entered after:

- user connect from `DISCONNECTED`;
- manual reconnect;
- reconnect timer expiry.

A second concurrent connection attempt must not be started.

### `CONNECTED`

The transport handshake completed and the receive subscription was established.

Entering this state records `last_connected_at`, clears `next_retry_at`, and makes the retry attempt visible as zero after the connection has remained stable for the configured reset interval.

### `RECONNECT_WAIT`

No active connection attempt is running. One cancellable retry is scheduled.

The snapshot must expose:

- current attempt number;
- next retry timestamp;
- last recoverable error.

Manual reconnect cancels the timer and moves immediately to `CONNECTING`.

### `AUTH_REQUIRED`

The transport cannot recover without credentials or a new authenticated session.

Automatic retries are disabled. The UI must expose a log-in action rather than displaying an endless reconnect loop.

### `STOPPING`

Shutdown or explicit disconnect is in progress. New transport callbacks may be recorded for diagnostics but must not schedule reconnects.

### `FATAL_ERROR`

A non-recoverable local configuration or implementation error prevents further connection attempts.

Automatic retries are disabled. A manual retry is permitted only after the caller explicitly clears or replaces the faulty configuration.

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

Transport adapters translate library callbacks and exceptions into these stable events. UI code does not translate raw socket exceptions.

## Required transitions

| Current state | Event | Next state | Required effect |
|---|---|---|---|
| `DISCONNECTED` | `CONNECT_REQUESTED` | `CONNECTING` | Start one attempt |
| `CONNECTING` | `CONNECT_SUCCEEDED` | `CONNECTED` | Record connection time and clear error |
| `CONNECTING` | `CONNECT_FAILED_RECOVERABLE` | `RECONNECT_WAIT` | Record error and schedule bounded retry |
| `CONNECTING` | `CONNECT_FAILED_AUTH` | `AUTH_REQUIRED` | Record actionable auth error; no retry |
| `CONNECTING` | `CONNECT_FAILED_FATAL` | `FATAL_ERROR` | Record fatal error; no retry |
| `CONNECTED` | `TRANSPORT_CLOSED_RECOVERABLE` | `RECONNECT_WAIT` | Record close reason and schedule retry |
| `CONNECTED` | `TRANSPORT_CLOSED_AUTH` | `AUTH_REQUIRED` | Stop retrying and request login |
| `RECONNECT_WAIT` | `RETRY_TIMER_EXPIRED` | `CONNECTING` | Clear timer and start one attempt |
| `RECONNECT_WAIT` | `MANUAL_RECONNECT_REQUESTED` | `CONNECTING` | Cancel timer and start one attempt |
| Any except `STOPPING` | `DISCONNECT_REQUESTED` | `STOPPING` | Cancel timer and stop transport |
| `STOPPING` | `STOP_COMPLETED` | `DISCONNECTED` | Clear active transport and retry metadata |
| `AUTH_REQUIRED` | `CONFIGURATION_REPLACED` | `DISCONNECTED` | Permit a fresh explicit connect |
| `FATAL_ERROR` | `CONFIGURATION_REPLACED` | `DISCONNECTED` | Permit a fresh explicit connect |

Unexpected events must not silently mutate state. They are rejected and emitted as a structured diagnostic event.

## Retry policy

The first implementation uses capped exponential backoff with jitter:

```text
base delay: 1 second
cap: 30 seconds
attempt exponent: 0, 1, 2, 3, ...
raw delay: min(cap, base * 2^attempt)
jittered delay: uniformly selected from [0.5 * raw, raw]
```

Properties:

- one scheduled timer maximum;
- one active connection attempt maximum;
- all waits cancellable;
- explicit disconnect disables retries;
- authentication and fatal errors disable retries;
- attempt counter resets only after 60 continuous seconds in `CONNECTED`;
- test code injects the clock, random source, and scheduler.

The exact delay selected for each retry is recorded without clipboard content.

## Public snapshot

The state machine exposes an immutable snapshot suitable for GUI and CLI rendering:

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

Timestamps use a monotonic clock for scheduling and wall-clock timestamps only for user display or exported diagnostics. The implementation must not compare wall-clock timestamps to schedule retries.

## Error model

Transport-specific exceptions are normalized into stable error categories:

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

Each normalized error includes:

- stable code;
- concise user-facing message;
- recoverable flag;
- original exception class for diagnostics;
- no clipboard payload.

Raw exception text may be written to the local technical log, but exported diagnostics must redact credentials, cookies, URLs containing secrets, and clipboard data.

## Send and receive observations

The first state-machine change records transport observations but does not pretend they are server-level delivery acknowledgements.

- `last_send_at`: updated after the client library accepts a frame for transmission without raising.
- `last_receive_at`: updated after a valid subscribed frame reaches the client.
- send failure while connected records an error observation; whether it forces a state transition depends on the transport status.
- no outbound item may be deleted from a future durable queue solely because `last_send_at` changed.

## UI contract

The desktop tray and future status panel render snapshots only.

Minimum labels:

- `Connected`
- `Connecting…`
- `Reconnecting in N s — attempt M`
- `Disconnected`
- `Login required`
- `Stopping…`
- `Error — action required`

Minimum actions:

- Connect when `DISCONNECTED`.
- Reconnect now when `RECONNECT_WAIT`.
- Disconnect when `CONNECTING`, `CONNECTED`, or `RECONNECT_WAIT`.
- Log in when `AUTH_REQUIRED`.
- Open logs and diagnostics for any error-bearing state.

The tray icon must not start as connected. Its first rendered value comes from the initial `DISCONNECTED` snapshot.

## Threading contract

- State mutation is serialized through one lock or one event-loop owner.
- Observer callbacks receive snapshots after mutation, outside the mutation lock.
- Socket callbacks do not sleep.
- Retry waits run through an injected scheduler, not `time.sleep()` inside `_on_close()`.
- Observer failure is isolated and logged; it does not corrupt transport state.
- shutdown cancels timers before closing the socket.

## Proposed source layout

```text
ClipCascade_Desktop/src/connection/
    __init__.py
    state.py
    errors.py
    retry.py
    controller.py

ClipCascade_Desktop/tests/
    test_connection_state.py
    test_retry_policy.py
    test_connection_controller.py
```

The first commit should contain the pure model, error types, retry calculator, and tests only. The second commit may adapt `STOMPManager`. The third may migrate tray rendering. This separation is mandatory to keep regressions attributable.

## Required tests before transport integration

1. Initial snapshot is `DISCONNECTED`, never `CONNECTED`.
2. Duplicate connect requests create one attempt.
3. Recoverable failure schedules exactly one retry.
4. Retry timer expiry starts exactly one attempt.
5. Manual reconnect cancels the pending timer.
6. Explicit disconnect cancels retry and prevents close callbacks from rescheduling.
7. Auth failure enters `AUTH_REQUIRED` with no timer.
8. Fatal failure enters `FATAL_ERROR` with no timer.
9. Stable connection resets the retry attempt only after 60 seconds.
10. Backoff never exceeds 30 seconds and remains inside the jitter interval.
11. Stale timer callbacks are ignored by generation token.
12. Observer exception does not alter state.
13. Shutdown reaches `DISCONNECTED` and leaves no timer or active attempt.
14. Snapshot timestamps and error fields update deterministically under an injected clock.
15. No test fixture or diagnostic snapshot contains clipboard content.

## Acceptance gate for replacing existing reconnect logic

The existing `STOMPManager` fixed-delay reconnect code may be removed only when:

- the pure state-machine tests pass on Windows and Linux CI;
- a fake transport integration test proves reconnect, cancellation, auth stop, and shutdown;
- `get_stats()` or its replacement returns a snapshot-derived status;
- the tray no longer owns an independent connection boolean;
- the Windows executable and Linux package still build;
- manual smoke testing confirms connect, disconnect, forced network loss, automatic recovery, and quit.

## Deferred decisions

- Application-level delivery acknowledgement and durable queue deletion.
- P2P-specific peer-count and signaling detail fields.
- Full diagnostics bundle schema.
- Persistent status-window visual design.
- Whether the CLI should use event subscription or low-frequency snapshot polling.
