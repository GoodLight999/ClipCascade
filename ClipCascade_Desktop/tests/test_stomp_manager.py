import json
import sys
import types
import unittest
from types import SimpleNamespace
from typing import Callable, List


def stub_module(name, **attributes):
    module = types.ModuleType(name)
    for key, value in attributes.items():
        setattr(module, key, value)
    sys.modules[name] = module
    return module


class StubConfig:
    def __init__(self):
        self.data = {
            "websocket_url": "ws://example.invalid/ws",
            "cookie": "session-cookie",
            "cipher_enabled": False,
        }


class StubClipboardManager:
    def __init__(self, config):
        self.config = config
        self.previous_clipboard_hash = 0
        self.monitor_start_count = 0
        self.stop_count = 0
        self.copy_callback = None
        self.received = []
        self.sent_hashes = set()
        self.tray = None

    def set_tray_ref(self, tray):
        self.tray = tray

    def on_copy(self, callback):
        self.monitor_start_count += 1
        self.copy_callback = callback

    def stop(self):
        self.stop_count += 1

    def has_clipboard_changed(self, payload):
        marker = repr(payload)
        if marker in self.sent_hashes:
            return False
        self.sent_hashes.add(marker)
        return True

    def base64_to_clipboard(self, base64_string, type_="text"):
        self.received.append((base64_string, type_))


class StubCipherManager:
    def __init__(self, _config):
        pass

    def encrypt(self, payload):
        return {"payload": payload}

    def decrypt(self, **data):
        return data["payload"]

    @staticmethod
    def encode_to_json_string(**data):
        return json.dumps(data)

    @staticmethod
    def decode_from_json_string(data):
        return json.loads(data)


class StubNotificationManager:
    def __init__(self, _config):
        self.notifications = []

    def notify(self, title, message):
        self.notifications.append((title, message))


class StubRequestManager:
    @staticmethod
    def format_cookie(cookie):
        return f"formatted:{cookie}"


class StubWSInterface:
    pass


class StubTaskbarPanel:
    pass


stub_module("clipboard.clipboard_manager", ClipboardManager=StubClipboardManager)
stub_module("core.config", Config=StubConfig)
stub_module(
    "core.constants",
    PLATFORM="Test",
    LINUX="Linux",
    LINUX_USE_CLI_UI=False,
    SUBSCRIPTION_DESTINATION="/user/queue/cliptext",
    SEND_DESTINATION="/app/cliptext",
    WEBSOCKET_TIMEOUT=3000,
    APP_NAME="ClipCascade",
)
stub_module("interfaces.ws_interface", WSInterface=StubWSInterface)
stub_module("utils.cipher_manager", CipherManager=StubCipherManager)
stub_module("utils.notification_manager", NotificationManager=StubNotificationManager)
stub_module("utils.request_manager", RequestManager=StubRequestManager)
stub_module(
    "utils.ssl_helper",
    websocket_sslopt_for_config=lambda _config: None,
)
stub_module("gui.tray", TaskbarPanel=StubTaskbarPanel)

from connection.controller import ConnectionController
from connection.state import ConnectionState
from stomp_ws.stomp_manager import STOMPManager


class FakeClock:
    def __init__(self, value=100.0):
        self.value = value

    def __call__(self):
        return self.value


class FakeHandle:
    def __init__(self, delay, callback):
        self.delay = delay
        self.callback = callback
        self.cancelled = False
        self.fired = False

    def cancel(self):
        self.cancelled = True

    def fire(self, force=False):
        if self.fired:
            return
        self.fired = True
        if force or not self.cancelled:
            self.callback()


class FakeScheduler:
    def __init__(self):
        self.handles: List[FakeHandle] = []

    def schedule(self, delay_seconds: float, callback: Callable[[], None]):
        handle = FakeHandle(delay_seconds, callback)
        self.handles.append(handle)
        return handle

    @property
    def pending(self):
        return [item for item in self.handles if not item.cancelled and not item.fired]


class FakeClient:
    def __init__(self, outcome="success"):
        self.outcome = outcome
        self.on_close_callback = None
        self.connected = False
        self.disconnect_count = 0
        self.subscriptions = []
        self.sent = []
        self.headers = None
        self.url = None

    def configure(self, url, headers, on_close_callback):
        self.url = url
        self.headers = headers
        self.on_close_callback = on_close_callback
        return self

    def connect(self, timeout, connectCallback, errorCallback):
        if isinstance(self.outcome, BaseException):
            raise self.outcome
        if self.outcome == "auth":
            raise ConnectionError("authentication failed")
        connectCallback(SimpleNamespace(command="CONNECTED"))
        self.connected = True

    def subscribe(self, destination, callback):
        self.subscriptions.append((destination, callback))
        return "sub-0", lambda: None

    def send(self, destination, body):
        self.sent.append((destination, body))

    def disconnect(self):
        self.disconnect_count += 1
        self.connected = False

    def trigger_close(self):
        self.connected = False
        if self.on_close_callback is not None:
            self.on_close_callback()


class FakeClientFactory:
    def __init__(self, outcomes):
        self.outcomes = list(outcomes)
        self.clients = []

    def __call__(self, url, headers, on_close_callback, sslopt):
        outcome = self.outcomes.pop(0) if self.outcomes else "success"
        client = FakeClient(outcome).configure(url, headers, on_close_callback)
        self.clients.append(client)
        return client


class STOMPManagerIntegrationTests(unittest.TestCase):
    def make_manager(self, outcomes=("success",)):
        clock = FakeClock()
        scheduler = FakeScheduler()
        factory = FakeClientFactory(outcomes)

        def controller_factory(**callbacks):
            return ConnectionController(
                clock=clock,
                scheduler=scheduler,
                random_source=lambda: 0.0,
                **callbacks,
            )

        manager = STOMPManager(
            StubConfig(),
            client_factory=factory,
            controller_factory=controller_factory,
        )
        return manager, factory, scheduler, clock

    def test_successful_connect_waits_for_subscription_and_starts_monitor_once(self):
        manager, factory, _scheduler, _clock = self.make_manager()

        success, message = manager.connect()

        self.assertTrue(success)
        self.assertEqual(message, "Websocket connected")
        self.assertTrue(manager.is_connected)
        self.assertEqual(manager.get_connection_snapshot().state, ConnectionState.CONNECTED)
        self.assertEqual(manager.clipboard_manager.monitor_start_count, 1)
        self.assertEqual(
            factory.clients[0].subscriptions[0][0],
            "/user/queue/cliptext",
        )
        self.assertIn("Connected", manager.get_stats())

    def test_initial_timeout_returns_failure_without_hidden_retry(self):
        manager, _factory, scheduler, _clock = self.make_manager((TimeoutError(),))

        success, message = manager.connect()

        self.assertFalse(success)
        self.assertIn("timed out", message)
        self.assertEqual(
            manager.get_connection_snapshot().state,
            ConnectionState.FATAL_ERROR,
        )
        self.assertEqual(scheduler.pending, [])

    def test_runtime_close_schedules_retry_without_callback_sleep(self):
        manager, factory, scheduler, _clock = self.make_manager(("success", "success"))
        self.assertTrue(manager.connect()[0])
        manager.is_login_phase = False

        factory.clients[0].trigger_close()

        snapshot = manager.get_connection_snapshot()
        self.assertEqual(snapshot.state, ConnectionState.RECONNECT_WAIT)
        self.assertTrue(manager.is_auto_reconnecting)
        self.assertEqual(len(scheduler.pending), 1)
        self.assertEqual(scheduler.pending[0].delay, 0.5)
        self.assertEqual(len(manager.notification_manager.notifications), 1)

    def test_manual_reconnect_cancels_wait_and_reuses_same_state_machine(self):
        manager, factory, scheduler, _clock = self.make_manager(("success", "success"))
        manager.connect()
        manager.is_login_phase = False
        factory.clients[0].trigger_close()
        pending = scheduler.pending[0]

        self.assertTrue(manager.manual_reconnect())

        self.assertTrue(pending.cancelled)
        self.assertEqual(manager.get_connection_snapshot().state, ConnectionState.CONNECTED)
        self.assertEqual(len(factory.clients), 2)
        self.assertEqual(manager.clipboard_manager.monitor_start_count, 1)
        self.assertEqual(len(manager.notification_manager.notifications), 2)

    def test_automatic_retry_uses_new_client_and_restores_connection(self):
        manager, factory, scheduler, _clock = self.make_manager(("success", "success"))
        manager.connect()
        manager.is_login_phase = False
        factory.clients[0].trigger_close()

        scheduler.pending[0].fire()

        self.assertEqual(manager.get_connection_snapshot().state, ConnectionState.CONNECTED)
        self.assertEqual(len(factory.clients), 2)
        self.assertEqual(manager.clipboard_manager.monitor_start_count, 1)

    def test_auth_failure_enters_auth_required(self):
        manager, _factory, scheduler, _clock = self.make_manager(("auth",))

        success, message = manager.connect()

        self.assertFalse(success)
        self.assertIn("rejected", message)
        self.assertEqual(
            manager.get_connection_snapshot().state,
            ConnectionState.AUTH_REQUIRED,
        )
        self.assertEqual(scheduler.pending, [])

    def test_send_and_receive_update_snapshot_observations(self):
        manager, factory, _scheduler, clock = self.make_manager()
        manager.connect()
        clock.value = 120.0

        manager.send("outbound", "text")
        frame = SimpleNamespace(
            body=json.dumps({"payload": "inbound", "type": "text"})
        )
        clock.value = 130.0
        manager._receive(frame)

        snapshot = manager.get_connection_snapshot()
        self.assertEqual(snapshot.last_send_at, 120.0)
        self.assertEqual(snapshot.last_receive_at, 130.0)
        self.assertEqual(len(factory.clients[0].sent), 1)
        self.assertEqual(manager.clipboard_manager.received, [("inbound", "text")])

    def test_disconnect_cancels_retry_and_stops_monitor(self):
        manager, factory, scheduler, _clock = self.make_manager(("success",))
        manager.connect()
        manager.is_login_phase = False
        factory.clients[0].trigger_close()
        pending = scheduler.pending[0]

        manager.disconnect()

        self.assertTrue(pending.cancelled)
        self.assertEqual(
            manager.get_connection_snapshot().state,
            ConnectionState.DISCONNECTED,
        )
        self.assertEqual(manager.clipboard_manager.stop_count, 1)
        pending.fire(force=True)
        self.assertEqual(len(factory.clients), 1)


if __name__ == "__main__":
    unittest.main()
