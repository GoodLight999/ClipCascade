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
    def schedule(self, _delay_seconds: float, _callback: Callable[[], None]) -> FakeHandle:
        raise RuntimeError("scheduler unavailable")


def recoverable_error() -> ConnectionErrorInfo:
    return ConnectionErrorInfo(
        code=ConnectionErrorCode.NETWORK_UNREACHABLE,
        message="Network is unavailable",
        recoverable=True,
    )


def auth_error() -> ConnectionErrorInfo:
    return ConnectionErrorInfo(
        code=ConnectionErrorCode.AUTH_REJECTED,
        message="Login is required",
        recoverable=False,
    )


def fatal_error() -> ConnectionErrorInfo:
    return ConnectionErrorInfo(
        code=ConnectionErrorCode.LOCAL_CONFIGURATION_ERROR,
        message="Configuration is invalid",
        recoverable=False,
    )


class ConnectionControllerTests(unittest.TestCase):
    def setUp(self) -> None:
        self.clock = FakeClock()
        self.scheduler = FakeScheduler()
        self.connect_calls = 0
        self.stop_calls = 0
        self.diagnostics = []

        def connect_action() -> None:
            self.connect_calls += 1

        def stop_action() -> None:
            self.stop_calls += 1

        self.controller = ConnectionController(
            clock=self.clock,
            scheduler=self.scheduler,
            retry_policy=RetryPolicy(),
            random_source=lambda: 0.5,
            on_connect_attempt=connect_action,
            on_stop_transport=stop_action,
            on_diagnostic=lambda code, message: self.diagnostics.append((code, message)),
        )

    def connect_successfully(self) -> None:
        self.assertTrue(self.controller.connect())
        self.assertTrue(self.controller.connect_succeeded())

    def test_initial_snapshot_is_disconnected_and_observer_receives_it(self) -> None:
        observed = []
        self.controller.add_observer(observed.append)

        snapshot = self.controller.snapshot()
        self.assertEqual(snapshot.state, ConnectionState.DISCONNECTED)
        self.assertEqual(snapshot.state_since, 100.0)
        self.assertEqual(snapshot.reconnect_attempt, 0)
        self.assertIsNone(snapshot.next_retry_at)
        self.assertEqual(observed, [snapshot])

    def test_duplicate_connect_request_creates_one_attempt(self) -> None:
        self.assertTrue(self.controller.connect())
        self.assertFalse(self.controller.connect())

        self.assertEqual(self.connect_calls, 1)
        self.assertEqual(self.controller.snapshot().state, ConnectionState.CONNECTING)
        self.assertEqual(self.diagnostics[-1][0], "INVALID_CONNECTION_TRANSITION")

    def test_success_records_connection_and_transport_observations(self) -> None:
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
        self.assertEqual(received.last_send_at, 105.0)
        self.assertEqual(received.last_receive_at, 109.0)
        self.assertIsNone(received.last_error_code)

    def test_recoverable_failure_schedules_exactly_one_retry(self) -> None:
        self.controller.connect()
        self.clock.advance(1)
        self.assertTrue(self.controller.connect_failed_recoverable(recoverable_error()))

        snapshot = self.controller.snapshot()
        self.assertEqual(snapshot.state, ConnectionState.RECONNECT_WAIT)
        self.assertEqual(snapshot.reconnect_attempt, 1)
        self.assertAlmostEqual(snapshot.next_retry_at, 101.75)
        self.assertEqual(len(self.scheduler.pending), 1)
        self.assertAlmostEqual(self.scheduler.pending[0].delay, 0.75)
        self.assertEqual(snapshot.last_error_code, ConnectionErrorCode.NETWORK_UNREACHABLE.value)
        self.assertTrue(snapshot.last_error_recoverable)

    def test_retry_timer_expiry_starts_one_new_attempt(self) -> None:
        self.controller.connect()
        self.controller.connect_failed_recoverable(recoverable_error())
        retry = self.scheduler.pending[0]

        retry.fire()

        self.assertEqual(self.controller.snapshot().state, ConnectionState.CONNECTING)
        self.assertEqual(self.connect_calls, 2)
        self.assertEqual(len(self.scheduler.pending), 0)

    def test_manual_reconnect_cancels_pending_timer(self) -> None:
        self.controller.connect()
        self.controller.connect_failed_recoverable(recoverable_error())
        retry = self.scheduler.pending[0]

        self.assertTrue(self.controller.manual_reconnect())

        self.assertTrue(retry.cancelled)
        self.assertEqual(self.controller.snapshot().state, ConnectionState.CONNECTING)
        self.assertEqual(self.connect_calls, 2)

    def test_disconnect_cancels_retry_and_stale_callback_is_ignored(self) -> None:
        self.controller.connect()
        self.controller.connect_failed_recoverable(recoverable_error())
        retry = self.scheduler.pending[0]

        self.assertTrue(self.controller.disconnect())
        self.assertTrue(retry.cancelled)
        self.assertEqual(self.controller.snapshot().state, ConnectionState.STOPPING)
        self.assertEqual(self.stop_calls, 1)

        retry.fire(force=True)
        self.assertEqual(self.connect_calls, 1)
        self.assertEqual(self.controller.snapshot().state, ConnectionState.STOPPING)

        self.assertTrue(self.controller.stop_completed())
        snapshot = self.controller.snapshot()
        self.assertEqual(snapshot.state, ConnectionState.DISCONNECTED)
        self.assertEqual(snapshot.reconnect_attempt, 0)
        self.assertIsNone(snapshot.next_retry_at)

    def test_auth_failure_stops_automatic_retry(self) -> None:
        self.controller.connect()
        self.assertTrue(self.controller.connect_failed_auth(auth_error()))

        snapshot = self.controller.snapshot()
        self.assertEqual(snapshot.state, ConnectionState.AUTH_REQUIRED)
        self.assertEqual(snapshot.last_error_code, ConnectionErrorCode.AUTH_REJECTED.value)
        self.assertEqual(len(self.scheduler.pending), 0)

        self.assertTrue(self.controller.configuration_replaced())
        self.assertEqual(self.controller.snapshot().state, ConnectionState.DISCONNECTED)

    def test_fatal_failure_stops_automatic_retry(self) -> None:
        self.controller.connect()
        self.assertTrue(self.controller.connect_failed_fatal(fatal_error()))

        snapshot = self.controller.snapshot()
        self.assertEqual(snapshot.state, ConnectionState.FATAL_ERROR)
        self.assertFalse(snapshot.last_error_recoverable)
        self.assertEqual(len(self.scheduler.pending), 0)

    def test_transport_close_from_connected_schedules_retry(self) -> None:
        self.connect_successfully()
        self.clock.advance(5)

        self.assertTrue(self.controller.transport_closed_recoverable(recoverable_error()))

        snapshot = self.controller.snapshot()
        self.assertEqual(snapshot.state, ConnectionState.RECONNECT_WAIT)
        self.assertEqual(snapshot.last_disconnected_at, 105.0)
        self.assertEqual(len(self.scheduler.pending), 1)

    def test_stable_connection_resets_attempt_only_after_sixty_seconds(self) -> None:
        self.controller.connect()
        self.controller.connect_failed_recoverable(recoverable_error())
        self.scheduler.pending[0].fire()
        self.controller.connect_succeeded()

        self.assertEqual(self.controller.snapshot().reconnect_attempt, 1)
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
        self.assertEqual(self.controller.snapshot().state, ConnectionState.RECONNECT_WAIT)
        self.assertEqual(self.connect_calls, 2)

        current.fire()
        self.assertEqual(self.controller.snapshot().state, ConnectionState.CONNECTING)
        self.assertEqual(self.connect_calls, 3)

    def test_observer_exception_does_not_corrupt_state(self) -> None:
        def broken_observer(_snapshot: ConnectionSnapshot) -> None:
            raise RuntimeError("observer failed")

        self.controller.add_observer(broken_observer)
        self.assertTrue(self.controller.connect())

        self.assertEqual(self.controller.snapshot().state, ConnectionState.CONNECTING)
        self.assertTrue(
            any(code == "CONNECTION_OBSERVER_FAILED" for code, _ in self.diagnostics)
        )

    def test_scheduler_failure_enters_fatal_error(self) -> None:
        controller = ConnectionController(
            clock=self.clock,
            scheduler=BrokenScheduler(),
            random_source=lambda: 0.5,
            on_connect_attempt=lambda: None,
        )
        controller.connect()
        controller.connect_failed_recoverable(recoverable_error())

        snapshot = controller.snapshot()
        self.assertEqual(snapshot.state, ConnectionState.FATAL_ERROR)
        self.assertEqual(
            snapshot.last_error_code,
            ConnectionErrorCode.LOCAL_CONFIGURATION_ERROR.value,
        )
        self.assertIsNone(snapshot.next_retry_at)

    def test_connect_action_failure_is_normalized_as_fatal(self) -> None:
        def broken_connect() -> None:
            raise RuntimeError("adapter failed")

        controller = ConnectionController(
            clock=self.clock,
            scheduler=self.scheduler,
            on_connect_attempt=broken_connect,
        )
        self.assertTrue(controller.connect())

        snapshot = controller.snapshot()
        self.assertEqual(snapshot.state, ConnectionState.FATAL_ERROR)
        self.assertEqual(
            snapshot.last_error_code,
            ConnectionErrorCode.LOCAL_CONFIGURATION_ERROR.value,
        )

    def test_stop_action_failure_does_not_leave_stopping_state(self) -> None:
        diagnostics = []

        def broken_stop() -> None:
            raise RuntimeError("stop failed")

        controller = ConnectionController(
            clock=self.clock,
            scheduler=self.scheduler,
            on_connect_attempt=lambda: None,
            on_stop_transport=broken_stop,
            on_diagnostic=lambda code, message: diagnostics.append((code, message)),
        )
        controller.connect()
        controller.connect_succeeded()
        self.assertTrue(controller.disconnect())

        self.assertEqual(controller.snapshot().state, ConnectionState.DISCONNECTED)
        self.assertTrue(any(code == "STOP_ACTION_FAILED" for code, _ in diagnostics))

    def test_snapshot_contains_no_clipboard_payload_field(self) -> None:
        fields = set(ConnectionSnapshot.__dataclass_fields__)
        self.assertNotIn("payload", fields)
        self.assertNotIn("clipboard", fields)
        self.assertNotIn("content", fields)


if __name__ == "__main__":
    unittest.main()
