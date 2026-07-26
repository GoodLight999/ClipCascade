import threading
import unittest
from unittest.mock import patch

from stomp_ws.client import Client
from stomp_ws.frame import Frame


class FakeWebSocketApp:
    def __init__(self, url, headers, behavior: str) -> None:
        self.url = url
        self.headers = headers
        self.behavior = behavior
        self.on_open = None
        self.on_message = None
        self.on_error = None
        self.on_close = None
        self.sent = []
        self.closed = False
        self.connect_sent = threading.Event()

    def run_forever(self, **_kwargs) -> None:
        self.on_open(self)
        if not self.connect_sent.wait(1.0):
            return

        if self.behavior == "connected":
            self.on_message(self, Frame.marshall("CONNECTED", {"version": "1.1"}, ""))
        elif self.behavior == "error":
            self.on_message(
                self,
                Frame.marshall("ERROR", {"message": "authentication failed"}, "denied"),
            )
        elif self.behavior == "close":
            self.on_close(self, 1006, "closed")
        elif self.behavior == "socket_error":
            self.on_error(self, RuntimeError("socket failed"))
        elif self.behavior == "timeout":
            return
        else:
            raise AssertionError(f"Unknown behavior: {self.behavior}")

    def send(self, payload: str) -> None:
        self.sent.append(payload)
        if payload.startswith("CONNECT\n"):
            self.connect_sent.set()

    def close(self) -> None:
        self.closed = True
        if self.on_close is not None:
            self.on_close(self, 1000, "closed by client")


class STOMPClientHandshakeTests(unittest.TestCase):
    def make_client(self, behavior: str, on_close_callback=None):
        holder = {}

        def factory(url, headers):
            app = FakeWebSocketApp(url, headers, behavior)
            holder["app"] = app
            return app

        patcher = patch("stomp_ws.client.websocket.WebSocketApp", side_effect=factory)
        patcher.start()
        self.addCleanup(patcher.stop)
        client = Client(
            "ws://example.invalid/ws",
            headers={"Cookie": "session=test"},
            on_close_callback=on_close_callback,
        )
        return client, holder

    def test_connect_returns_only_after_connected_callback_succeeds(self) -> None:
        client, holder = self.make_client("connected")
        callbacks = []

        client.connect(
            timeout=500,
            connectCallback=lambda frame: callbacks.append(frame.command),
        )

        self.assertTrue(client.opened)
        self.assertTrue(client.connected)
        self.assertEqual(callbacks, ["CONNECTED"])
        self.assertTrue(any(item.startswith("CONNECT\n") for item in holder["app"].sent))

    def test_handshake_timeout_closes_socket(self) -> None:
        client, holder = self.make_client("timeout")

        with self.assertRaises(TimeoutError):
            client.connect(timeout=50)

        self.assertTrue(holder["app"].closed)
        self.assertFalse(client.connected)

    def test_stomp_error_unblocks_wait_and_raises(self) -> None:
        client, _holder = self.make_client("error")
        errors = []

        with self.assertRaises(ConnectionError) as raised:
            client.connect(
                timeout=500,
                errorCallback=lambda frame: errors.append(frame.command),
            )

        self.assertIn("STOMP handshake", str(raised.exception))
        self.assertEqual(errors, ["ERROR"])
        self.assertFalse(client.connected)

    def test_close_before_connected_unblocks_wait_and_raises(self) -> None:
        close_calls = []
        client, _holder = self.make_client(
            "close", on_close_callback=lambda: close_calls.append(True)
        )

        with self.assertRaises(ConnectionError):
            client.connect(timeout=500)

        self.assertEqual(close_calls, [True])
        self.assertFalse(client.connected)

    def test_socket_error_before_connected_unblocks_open_wait(self) -> None:
        client, _holder = self.make_client("socket_error")

        with self.assertRaises(ConnectionError) as raised:
            client.connect(timeout=500)

        self.assertIn("socket failed", str(raised.exception))
        self.assertFalse(client.connected)

    def test_connected_callback_failure_is_not_reported_as_connected(self) -> None:
        client, _holder = self.make_client("connected")

        def broken_callback(_frame):
            raise RuntimeError("subscription failed")

        with self.assertRaises(ConnectionError) as raised:
            client.connect(timeout=500, connectCallback=broken_callback)

        self.assertIn("subscription failed", str(raised.exception))
        self.assertFalse(client.connected)

    def test_explicit_disconnect_suppresses_remote_close_callback(self) -> None:
        close_calls = []
        client, holder = self.make_client(
            "connected", on_close_callback=lambda: close_calls.append(True)
        )
        client.connect(timeout=500)

        client.disconnect()

        self.assertTrue(holder["app"].closed)
        self.assertEqual(close_calls, [])
        self.assertFalse(client.opened)
        self.assertFalse(client.connected)


if __name__ == "__main__":
    unittest.main()
