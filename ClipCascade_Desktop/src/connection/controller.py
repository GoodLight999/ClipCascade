"""Thread-safe, transport-independent desktop connection controller."""

import random
import threading
import time
from typing import Callable, List, Optional, Protocol, Tuple

from .errors import ConnectionErrorCode, ConnectionErrorInfo
from .retry import RetryPolicy
from .state import ConnectionEvent, ConnectionSnapshot, ConnectionState


class ScheduledHandle(Protocol):
    def cancel(self) -> None:
        """Cancel the scheduled callback if it has not run."""


class Scheduler(Protocol):
    def schedule(
        self, delay_seconds: float, callback: Callable[[], None]
    ) -> ScheduledHandle:
        """Schedule one callback and return a cancellable handle."""


class ThreadingScheduler:
    """Default scheduler used by the eventual transport integration."""

    def schedule(
        self, delay_seconds: float, callback: Callable[[], None]
    ) -> ScheduledHandle:
        timer = threading.Timer(delay_seconds, callback)
        timer.daemon = True
        timer.start()
        return timer


Observer = Callable[[ConnectionSnapshot], None]
DiagnosticSink = Callable[[str, str], None]


class ConnectionController:
    """Own all desktop connection truth and retry scheduling.

    Transport and UI integrations request events through this object. They must
    never keep an independent connection-state boolean.
    """

    def __init__(
        self,
        *,
        clock: Callable[[], float] = time.monotonic,
        scheduler: Optional[Scheduler] = None,
        retry_policy: Optional[RetryPolicy] = None,
        random_source: Callable[[], float] = random.random,
        on_connect_attempt: Optional[Callable[[], None]] = None,
        on_stop_transport: Optional[Callable[[], None]] = None,
        on_diagnostic: Optional[DiagnosticSink] = None,
    ) -> None:
        self._clock = clock
        self._scheduler = scheduler or ThreadingScheduler()
        self._retry_policy = retry_policy or RetryPolicy()
        self._random_source = random_source
        self._on_connect_attempt = on_connect_attempt or (lambda: None)
        self._on_stop_transport = on_stop_transport or (lambda: None)
        self._on_diagnostic = on_diagnostic or (lambda _code, _message: None)

        now = self._clock()
        self._lock = threading.RLock()
        self._state = ConnectionState.DISCONNECTED
        self._state_since = now
        self._reconnect_attempt = 0
        self._next_retry_at: Optional[float] = None
        self._last_connected_at: Optional[float] = None
        self._last_disconnected_at: Optional[float] = None
        self._last_send_at: Optional[float] = None
        self._last_receive_at: Optional[float] = None
        self._last_error: Optional[ConnectionErrorInfo] = None

        self._retry_handle: Optional[ScheduledHandle] = None
        self._retry_generation = 0
        self._observers: List[Observer] = []

    def add_observer(self, observer: Observer, *, emit_initial: bool = True) -> None:
        with self._lock:
            self._observers.append(observer)
            snapshot = self._snapshot_locked()
        if emit_initial:
            self._notify_one(observer, snapshot)

    def remove_observer(self, observer: Observer) -> None:
        with self._lock:
            try:
                self._observers.remove(observer)
            except ValueError:
                return

    def snapshot(self) -> ConnectionSnapshot:
        with self._lock:
            return self._snapshot_locked()

    def connect(self) -> bool:
        return self._apply(ConnectionEvent.CONNECT_REQUESTED)

    def connect_succeeded(self) -> bool:
        return self._apply(ConnectionEvent.CONNECT_SUCCEEDED)

    def connect_failed_recoverable(self, error: ConnectionErrorInfo) -> bool:
        return self._apply(ConnectionEvent.CONNECT_FAILED_RECOVERABLE, error)

    def connect_failed_auth(self, error: ConnectionErrorInfo) -> bool:
        return self._apply(ConnectionEvent.CONNECT_FAILED_AUTH, error)

    def connect_failed_fatal(self, error: ConnectionErrorInfo) -> bool:
        return self._apply(ConnectionEvent.CONNECT_FAILED_FATAL, error)

    def transport_closed_recoverable(self, error: ConnectionErrorInfo) -> bool:
        return self._apply(ConnectionEvent.TRANSPORT_CLOSED_RECOVERABLE, error)

    def transport_closed_auth(self, error: ConnectionErrorInfo) -> bool:
        return self._apply(ConnectionEvent.TRANSPORT_CLOSED_AUTH, error)

    def manual_reconnect(self) -> bool:
        return self._apply(ConnectionEvent.MANUAL_RECONNECT_REQUESTED)

    def disconnect(self) -> bool:
        return self._apply(ConnectionEvent.DISCONNECT_REQUESTED)

    def stop_completed(self) -> bool:
        return self._apply(ConnectionEvent.STOP_COMPLETED)

    def configuration_replaced(self) -> bool:
        return self._apply(ConnectionEvent.CONFIGURATION_REPLACED)

    def record_send(self) -> ConnectionSnapshot:
        return self._record_observation(send=True)

    def record_receive(self) -> ConnectionSnapshot:
        return self._record_observation(send=False)

    def _record_observation(self, *, send: bool) -> ConnectionSnapshot:
        with self._lock:
            now = self._clock()
            if send:
                self._last_send_at = now
            else:
                self._last_receive_at = now
            snapshot = self._snapshot_locked()
            observers = tuple(self._observers)
        self._publish(snapshot, observers)
        return snapshot

    def _apply(
        self,
        event: ConnectionEvent,
        error: Optional[ConnectionErrorInfo] = None,
    ) -> bool:
        cancel_handle: Optional[ScheduledHandle] = None
        schedule_retry: Optional[Tuple[int, float]] = None
        run_connect = False
        run_stop = False
        diagnostic: Optional[Tuple[str, str]] = None

        with self._lock:
            self._maybe_reset_retry_attempt_locked()
            now = self._clock()
            accepted = True

            if event is ConnectionEvent.CONNECT_REQUESTED:
                if self._state is not ConnectionState.DISCONNECTED:
                    accepted = False
                else:
                    self._set_state_locked(ConnectionState.CONNECTING, now)
                    run_connect = True

            elif event is ConnectionEvent.CONNECT_SUCCEEDED:
                if self._state is not ConnectionState.CONNECTING:
                    accepted = False
                else:
                    self._set_state_locked(ConnectionState.CONNECTED, now)
                    self._last_connected_at = now
                    self._next_retry_at = None
                    self._last_error = None

            elif event is ConnectionEvent.CONNECT_FAILED_RECOVERABLE:
                if self._state is not ConnectionState.CONNECTING or error is None:
                    accepted = False
                else:
                    schedule_retry = self._enter_reconnect_wait_locked(error, now)

            elif event is ConnectionEvent.CONNECT_FAILED_AUTH:
                if self._state is not ConnectionState.CONNECTING or error is None:
                    accepted = False
                else:
                    self._last_error = error
                    self._last_disconnected_at = now
                    self._next_retry_at = None
                    self._set_state_locked(ConnectionState.AUTH_REQUIRED, now)

            elif event is ConnectionEvent.CONNECT_FAILED_FATAL:
                if self._state is not ConnectionState.CONNECTING or error is None:
                    accepted = False
                else:
                    self._last_error = error
                    self._last_disconnected_at = now
                    self._next_retry_at = None
                    self._set_state_locked(ConnectionState.FATAL_ERROR, now)

            elif event is ConnectionEvent.TRANSPORT_CLOSED_RECOVERABLE:
                if self._state is not ConnectionState.CONNECTED or error is None:
                    accepted = False
                else:
                    schedule_retry = self._enter_reconnect_wait_locked(error, now)

            elif event is ConnectionEvent.TRANSPORT_CLOSED_AUTH:
                if self._state is not ConnectionState.CONNECTED or error is None:
                    accepted = False
                else:
                    self._last_error = error
                    self._last_disconnected_at = now
                    self._next_retry_at = None
                    self._set_state_locked(ConnectionState.AUTH_REQUIRED, now)

            elif event is ConnectionEvent.MANUAL_RECONNECT_REQUESTED:
                if self._state is not ConnectionState.RECONNECT_WAIT:
                    accepted = False
                else:
                    cancel_handle = self._detach_retry_locked()
                    self._set_state_locked(ConnectionState.CONNECTING, now)
                    run_connect = True

            elif event is ConnectionEvent.DISCONNECT_REQUESTED:
                if self._state in (
                    ConnectionState.DISCONNECTED,
                    ConnectionState.STOPPING,
                ):
                    accepted = False
                else:
                    cancel_handle = self._detach_retry_locked()
                    self._set_state_locked(ConnectionState.STOPPING, now)
                    run_stop = True

            elif event is ConnectionEvent.STOP_COMPLETED:
                if self._state is not ConnectionState.STOPPING:
                    accepted = False
                else:
                    cancel_handle = self._detach_retry_locked()
                    self._last_disconnected_at = now
                    self._next_retry_at = None
                    self._reconnect_attempt = 0
                    self._last_error = None
                    self._set_state_locked(ConnectionState.DISCONNECTED, now)

            elif event is ConnectionEvent.CONFIGURATION_REPLACED:
                if self._state not in (
                    ConnectionState.AUTH_REQUIRED,
                    ConnectionState.FATAL_ERROR,
                ):
                    accepted = False
                else:
                    cancel_handle = self._detach_retry_locked()
                    self._reconnect_attempt = 0
                    self._last_error = None
                    self._set_state_locked(ConnectionState.DISCONNECTED, now)

            else:
                accepted = False

            if accepted:
                snapshot = self._snapshot_locked()
                observers = tuple(self._observers)
            else:
                snapshot = self._snapshot_locked()
                observers = ()
                diagnostic = (
                    "INVALID_CONNECTION_TRANSITION",
                    f"Ignored {event.value} while in {self._state.value}",
                )

        if cancel_handle is not None:
            self._cancel_handle(cancel_handle)
        if diagnostic is not None:
            self._diagnose(*diagnostic)
        if not accepted:
            return False

        self._publish(snapshot, observers)

        if schedule_retry is not None:
            generation, delay_seconds = schedule_retry
            self._install_retry(generation, delay_seconds)
        if run_connect:
            self._invoke_connect_action()
        if run_stop:
            self._invoke_stop_action()
        return True

    def _enter_reconnect_wait_locked(
        self, error: ConnectionErrorInfo, now: float
    ) -> Tuple[int, float]:
        stale_handle = self._detach_retry_locked()
        if stale_handle is not None:
            self._diagnose(
                "RETRY_TIMER_REPLACED",
                "A stale retry timer was detached before scheduling a new one",
            )

        self._last_error = error
        self._last_disconnected_at = now
        self._reconnect_attempt += 1
        delay_seconds = self._retry_policy.delay_for_attempt(
            self._reconnect_attempt,
            self._random_source(),
        )
        self._retry_generation += 1
        generation = self._retry_generation
        self._next_retry_at = now + delay_seconds
        self._set_state_locked(ConnectionState.RECONNECT_WAIT, now)
        return generation, delay_seconds

    def _install_retry(self, generation: int, delay_seconds: float) -> None:
        try:
            handle = self._scheduler.schedule(
                delay_seconds,
                lambda: self._retry_timer_fired(generation),
            )
        except Exception as exception:
            error = ConnectionErrorInfo.from_exception(
                ConnectionErrorCode.LOCAL_CONFIGURATION_ERROR,
                "Unable to schedule a reconnect attempt",
                False,
                exception,
            )
            self._fail_retry_scheduler(generation, error)
            return

        cancel_new_handle = False
        with self._lock:
            if (
                self._state is ConnectionState.RECONNECT_WAIT
                and self._retry_generation == generation
                and self._retry_handle is None
            ):
                self._retry_handle = handle
            else:
                cancel_new_handle = True
        if cancel_new_handle:
            self._cancel_handle(handle)

    def _retry_timer_fired(self, generation: int) -> None:
        with self._lock:
            if (
                self._state is not ConnectionState.RECONNECT_WAIT
                or self._retry_generation != generation
            ):
                return
            self._retry_handle = None
            self._next_retry_at = None
            self._set_state_locked(ConnectionState.CONNECTING, self._clock())
            snapshot = self._snapshot_locked()
            observers = tuple(self._observers)

        self._publish(snapshot, observers)
        self._invoke_connect_action()

    def _fail_retry_scheduler(
        self, generation: int, error: ConnectionErrorInfo
    ) -> None:
        with self._lock:
            if (
                self._state is not ConnectionState.RECONNECT_WAIT
                or self._retry_generation != generation
            ):
                return
            self._retry_handle = None
            self._next_retry_at = None
            self._last_error = error
            self._set_state_locked(ConnectionState.FATAL_ERROR, self._clock())
            snapshot = self._snapshot_locked()
            observers = tuple(self._observers)
        self._publish(snapshot, observers)

    def _detach_retry_locked(self) -> Optional[ScheduledHandle]:
        handle = self._retry_handle
        self._retry_handle = None
        self._next_retry_at = None
        self._retry_generation += 1
        return handle

    def _invoke_connect_action(self) -> None:
        try:
            self._on_connect_attempt()
        except Exception as exception:
            error = ConnectionErrorInfo.from_exception(
                ConnectionErrorCode.LOCAL_CONFIGURATION_ERROR,
                "The local connection adapter failed to start",
                False,
                exception,
            )
            self.connect_failed_fatal(error)

    def _invoke_stop_action(self) -> None:
        try:
            self._on_stop_transport()
        except Exception as exception:
            self._diagnose(
                "STOP_ACTION_FAILED",
                f"Transport stop action raised {type(exception).__name__}",
            )
            self.stop_completed()

    def _set_state_locked(self, state: ConnectionState, now: float) -> None:
        if self._state is state:
            return
        self._state = state
        self._state_since = now

    def _maybe_reset_retry_attempt_locked(self) -> None:
        if (
            self._state is ConnectionState.CONNECTED
            and self._reconnect_attempt > 0
            and self._last_connected_at is not None
            and self._clock() - self._last_connected_at
            >= self._retry_policy.stable_reset_seconds
        ):
            self._reconnect_attempt = 0

    def _snapshot_locked(self) -> ConnectionSnapshot:
        self._maybe_reset_retry_attempt_locked()
        error = self._last_error
        return ConnectionSnapshot(
            state=self._state,
            state_since=self._state_since,
            reconnect_attempt=self._reconnect_attempt,
            next_retry_at=self._next_retry_at,
            last_connected_at=self._last_connected_at,
            last_disconnected_at=self._last_disconnected_at,
            last_send_at=self._last_send_at,
            last_receive_at=self._last_receive_at,
            last_error_code=error.code.value if error else None,
            last_error_message=error.message if error else None,
            last_error_recoverable=error.recoverable if error else None,
        )

    def _publish(
        self,
        snapshot: ConnectionSnapshot,
        observers: Tuple[Observer, ...],
    ) -> None:
        for observer in observers:
            self._notify_one(observer, snapshot)

    def _notify_one(self, observer: Observer, snapshot: ConnectionSnapshot) -> None:
        try:
            observer(snapshot)
        except Exception as exception:
            self._diagnose(
                "CONNECTION_OBSERVER_FAILED",
                f"Connection observer raised {type(exception).__name__}",
            )

    @staticmethod
    def _cancel_handle(handle: ScheduledHandle) -> None:
        try:
            handle.cancel()
        except Exception:
            pass

    def _diagnose(self, code: str, message: str) -> None:
        try:
            self._on_diagnostic(code, message)
        except Exception:
            pass
