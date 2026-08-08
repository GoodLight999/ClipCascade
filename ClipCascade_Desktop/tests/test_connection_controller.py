import unittest
from typing import Callable, List

from connection.controller import ConnectionController
from connection.errors import ConnectionErrorCode, ConnectionErrorInfo
from connection.retry import RetryPolicy
from connection.state import ConnectionSnapshot, ConnectionState


class FakeClock:
    def __init__(self, initial: float = 100.0) -> None:
        self.value = initial

    def __call__(self) -> float:
        return self.value

    def advance(self, seconds: float) -> None:
        self.value += seconds


class FakeHandle:
    def __init__(self, delay: float, callback: Callable[[], None]) -> None:
        self.delay = delay
        self.callback = callback
        self.cancelled = False
        self.fired = False

    def cancel(self) -> None:
        self.cancelled = True

    def fire(self, *, force: bool = False) -> None:
        if self.fired:
            return
        self.fired = True
        if force or not self.cancelled:
            self.callback()


class FakeScheduler:
    def __init__(self) -> None:
        self.handles: List[FakeHandle] = []

    def schedule(self, delay_seconds: float, callback: Callable[[], None]) -> FakeHandle:
        handle = FakeHandle(delay_seconds, callback)
        self.handles.append(handle)
        return handle

    @property
    def pending(self) -> List[FakeHandle]:
        return [handle for handle in self.handles if not handle.cancelled and not handle.fired]


class BrokenScheduler:
    def schedule(self, _delay_seconds: float, _callback: Callable[[], None]):
        raise RuntimeError("scheduler unavailable")


def recoverable_error() -> ConnectionErrorInfo:
    return ConnectionErrorInfo(
        ConnectionErrorCode.NETWORK_UNREACHABLE,
        "Network is unavailable",
        True,
    )


def auth_error() -> ConnectionErrorInfo:
    return ConnectionErrorInfo(
        ConnectionErrorCode.AUTH_REJECTED,
        "Login is required",
        False,
    )


class ConnectionControllerTests(unittest.TestCase):
    def setUp(self) -> None:
        self.clock = FakeClock()
        self.scheduler = FakeScheduler()
        self.connect_calls = 0
        self.stop_calls = 0
        self.diagnostics = []
        self.controller = ConnectionController(
            clock=self.clock,
            scheduler=self.scheduler,
            retry_policy=RetryPolicy(),
            random_source=lambda: 0.5,
            on_connect_attempt=self._connect,
            on_stop_transport=self._stop,
            on_diagnostic=lambda code, message: self.diagnostics.append((code, message)),
        )

    def _connect(self) -> None:
        self.connect_calls += 1

    def _stop(self) -> None:
        self.stop_calls += 1

    def test_initial_state_and_duplicate_connect(self) -> None:
        observed = []
        self.controller.add_observer(observed.append)
        self.assertEqual(observed[0].state, ConnectionState.DISCONNECTED)
        self.assertTrue(self.controller.connect())
        self.assertFalse(self.controller.connect())
        self.assertEqual(self.connect_calls, 1)
        self.assertEqual(self.controller.snapshot().state, ConnectionState.CONNECTING)
        self.assertEqual(self.diagnostics[-1][0], "INVALID_CONNECTION_TRANSITION")

    def test_connect_success_and_send_receive_observations(self) -> None:
        self.controller.connect()
        self.clock.advance(2)
        self.controller.connect_succeeded()
        self.clock.advance(3)
        sent = self.controller.record_send()
        self.clock.advance(4)
        received = self.controller.record_receive()
        self.assertEqual(received.state, ConnectionState.CONNECTED)
        self.assertEqual(received.last_connected_at, 102.0)
        self.assertEqual(sent.last_send_at, 105.0)
        self.assertEqual(received.last_receive_at, 109.0)

    def test_recoverable_failure_schedules_bounded_retry(self) -> None:
        self.controller.connect()
        self.controller.connect_failed_recoverable(recoverable_error())
        snapshot = self.controller.snapshot()
        self.assertEqual(snapshot.state, ConnectionState.RECONNECT_WAIT)
        self.assertEqual(snapshot.reconnect_attempt, 1)
        self.assertEqual(len(self.scheduler.pending), 1)
        self.assertAlmostEqual(self.scheduler.pending[0].delay, 0.75)

    def test_retry_timer_starts_one_fresh_attempt(self) -> None:
        self.controller.connect()
        self.controller.connect_failed_recoverable(recoverable_error())
        self.scheduler.pending[0].fire()
        self.assertEqual(self.controller.snapshot().state, ConnectionState.CONNECTING)
        self.assertEqual(self.connect_calls, 2)
        self.assertEqual(self.scheduler.pending, [])

    def test_manual_reconnect_cancels_pending_timer(self) -> None:
        self.controller.connect()
        self.controller.connect_failed_recoverable(recoverable_error())
        retry = self.scheduler.pending[0]
        self.assertTrue(self.controller.manual_reconnect())
        self.assertTrue(retry.cancelled)
        self.assertEqual(self.connect_calls, 2)

    def test_disconnect_cancels_retry_and_invalidates_stale_callback(self) -> None:
        self.controller.connect()
        self.controller.connect_failed_recoverable(recoverable_error())
        retry = self.scheduler.pending[0]
        self.assertTrue(self.controller.disconnect())
        self.assertTrue(retry.cancelled)
        self.assertEqual(self.stop_calls, 1)
        retry.fire(force=True)
        self.assertEqual(self.connect_calls, 1)
        self.assertEqual(self.controller.snapshot().state, ConnectionState.STOPPING)
        self.controller.stop_completed()
        self.assertEqual(self.controller.snapshot().state, ConnectionState.DISCONNECTED)

    def test_auth_failure_stops_automatic_retry(self) -> None:
        self.controller.connect()
        self.controller.connect_failed_auth(auth_error())
        snapshot = self.controller.snapshot()
        self.assertEqual(snapshot.state, ConnectionState.AUTH_REQUIRED)
        self.assertEqual(snapshot.last_error_code, ConnectionErrorCode.AUTH_REJECTED.value)
        self.assertEqual(self.scheduler.pending, [])

    def test_stable_connection_resets_attempt_after_threshold(self) -> None:
        self.controller.connect()
        self.controller.connect_failed_recoverable(recoverable_error())
        self.scheduler.pending[0].fire()
        self.controller.connect_succeeded()
        self.clock.advance(59.9)
        self.assertEqual(self.controller.snapshot().reconnect_attempt, 1)
        self.clock.advance(0.1)
        self.assertEqual(self.controller.snapshot().reconnect_attempt, 0)

    def test_stale_timer_generation_cannot_start_connection(self) -> None:
        self.controller.connect()
        self.controller.connect_failed_recoverable(recoverable_error())
        stale = self.scheduler.pending[0]
        self.controller.manual_reconnect()
        self.controller.connect_failed_recoverable(recoverable_error())
        current = self.scheduler.pending[0]
        stale.fire(force=True)
        self.assertEqual(self.connect_calls, 2)
        current.fire()
        self.assertEqual(self.connect_calls, 3)

    def test_observer_exception_does_not_corrupt_state(self) -> None:
        def broken(_snapshot: ConnectionSnapshot) -> None:
            raise RuntimeError("observer failed")

        self.controller.add_observer(broken)
        self.controller.connect()
        self.assertEqual(self.controller.snapshot().state, ConnectionState.CONNECTING)
        self.assertTrue(any(code == "CONNECTION_OBSERVER_FAILED" for code, _ in self.diagnostics))

    def test_scheduler_failure_becomes_fatal(self) -> None:
        controller = ConnectionController(
            clock=self.clock,
            scheduler=BrokenScheduler(),
            random_source=lambda: 0.5,
        )
        controller.connect()
        controller.connect_failed_recoverable(recoverable_error())
        snapshot = controller.snapshot()
        self.assertEqual(snapshot.state, ConnectionState.FATAL_ERROR)
        self.assertEqual(snapshot.last_error_code, ConnectionErrorCode.LOCAL_CONFIGURATION_ERROR.value)

    def test_snapshot_contains_no_clipboard_payload(self) -> None:
        fields = set(ConnectionSnapshot.__dataclass_fields__)
        self.assertTrue({"payload", "clipboard", "content"}.isdisjoint(fields))


if __name__ == "__main__":
    unittest.main()
