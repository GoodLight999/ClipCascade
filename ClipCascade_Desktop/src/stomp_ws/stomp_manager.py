import json
import logging
import socket
import ssl
import time
from threading import RLock

from clipboard.clipboard_manager import ClipboardManager
from connection.controller import ConnectionController
from connection.errors import ConnectionErrorCode, ConnectionErrorInfo
from connection.state import ConnectionSnapshot, ConnectionState
from core.config import Config
from core.constants import *
from interfaces.ws_interface import WSInterface
from stomp_ws.client import Client
from utils.cipher_manager import CipherManager
from utils.notification_manager import NotificationManager
from utils.request_manager import RequestManager
from utils.ssl_helper import websocket_sslopt_for_config

if PLATFORM.startswith(LINUX) and LINUX_USE_CLI_UI:
    from cli.tray import TaskbarPanel
else:
    from gui.tray import TaskbarPanel


class STOMPManager(WSInterface):
    """P2S transport backed by one authoritative connection controller."""

    def __init__(
        self,
        config: Config,
        is_login_phase=True,
        client_factory=Client,
        controller_factory=ConnectionController,
    ):
        self.config = config
        self.clipboard_manager = ClipboardManager(self.config)
        self.cipher_manager = CipherManager(self.config)
        self.notification_manager = NotificationManager(self.config)
        self.sys_tray: TaskbarPanel = None
        self.is_login_phase = is_login_phase

        self.client = None
        self._client_factory = client_factory
        self._client_lock = RLock()
        self._clipboard_monitor_started = False
        self._last_observed_state = None
        self._last_connect_message = ""

        self.connection_controller = controller_factory(
            on_connect_attempt=self._connect_attempt,
            on_stop_transport=self._stop_transport,
            on_diagnostic=self._log_connection_diagnostic,
        )
        self.connection_controller.add_observer(self._on_connection_snapshot)

    @property
    def is_connected(self) -> bool:
        return self.connection_controller.snapshot().state is ConnectionState.CONNECTED

    @property
    def is_auto_reconnecting(self) -> bool:
        return (
            self.connection_controller.snapshot().state
            is ConnectionState.RECONNECT_WAIT
        )

    @property
    def disconnected(self) -> bool:
        return self.connection_controller.snapshot().state in (
            ConnectionState.DISCONNECTED,
            ConnectionState.STOPPING,
            ConnectionState.AUTH_REQUIRED,
            ConnectionState.FATAL_ERROR,
        )

    def set_tray_ref(self, sys_tray: TaskbarPanel):
        self.sys_tray = sys_tray
        self.clipboard_manager.set_tray_ref(sys_tray)

    def get_total_timeout(self):
        snapshot = self.connection_controller.snapshot()
        retry_wait_ms = 0
        if snapshot.next_retry_at is not None:
            retry_wait_ms = max(
                0,
                int((snapshot.next_retry_at - time.monotonic()) * 1000),
            )
        return retry_wait_ms + WEBSOCKET_TIMEOUT

    def get_connection_snapshot(self) -> ConnectionSnapshot:
        return self.connection_controller.snapshot()

    def get_stats(self) -> str:
        snapshot = self.connection_controller.snapshot()
        if snapshot.state is ConnectionState.CONNECTED:
            status = "🔗 Connected"
        elif snapshot.state is ConnectionState.CONNECTING:
            status = "🔄 Connecting…"
        elif snapshot.state is ConnectionState.RECONNECT_WAIT:
            remaining = 0
            if snapshot.next_retry_at is not None:
                remaining = max(
                    0,
                    int(snapshot.next_retry_at - time.monotonic() + 0.999),
                )
            status = (
                f"🔄 Reconnecting in {remaining}s "
                f"(attempt {snapshot.reconnect_attempt})"
            )
        elif snapshot.state is ConnectionState.AUTH_REQUIRED:
            status = "🔐 Login required"
        elif snapshot.state is ConnectionState.STOPPING:
            status = "⏳ Stopping…"
        elif snapshot.state is ConnectionState.FATAL_ERROR:
            status = "❌ Connection error"
        else:
            status = "⛓️‍💥 Disconnected"

        if (
            snapshot.last_error_message
            and snapshot.state is not ConnectionState.CONNECTED
        ):
            status += f" | {snapshot.last_error_message}"
        return status

    def connect(self) -> tuple[bool, str]:
        """Request one connection attempt and return its synchronous outcome."""
        state = self.connection_controller.snapshot().state

        if state is ConnectionState.CONNECTED:
            return True, "Websocket connected"
        if state is ConnectionState.CONNECTING:
            return False, "Connection attempt already in progress"
        if state is ConnectionState.STOPPING:
            return False, "Websocket is stopping"
        if state is ConnectionState.RECONNECT_WAIT:
            accepted = self.connection_controller.manual_reconnect()
        else:
            if state in (
                ConnectionState.AUTH_REQUIRED,
                ConnectionState.FATAL_ERROR,
            ):
                self.connection_controller.configuration_replaced()
            accepted = self.connection_controller.connect()

        snapshot = self.connection_controller.snapshot()
        if snapshot.state is ConnectionState.CONNECTED:
            return True, "Websocket connected"

        message = snapshot.last_error_message or self._last_connect_message
        if not accepted and not message:
            message = f"Connection request rejected in state {snapshot.state.value}"
        return False, message or "Websocket connection failed"

    def _connect_attempt(self) -> None:
        client = None
        try:
            self._dispose_current_client()
            client = self._client_factory(
                self.config.data["websocket_url"],
                headers={
                    "Cookie": RequestManager.format_cookie(
                        self.config.data["cookie"]
                    )
                },
                on_close_callback=None,
                sslopt=websocket_sslopt_for_config(self.config),
            )
            client.on_close_callback = lambda: self._on_client_close(client)
            with self._client_lock:
                self.client = client

            client.connect(
                timeout=WEBSOCKET_TIMEOUT,
                connectCallback=lambda _frame: client.subscribe(
                    destination=SUBSCRIPTION_DESTINATION,
                    callback=self._receive,
                ),
                errorCallback=self._on_stomp_error,
            )

            with self._client_lock:
                if self.client is not client:
                    self._close_client_safely(client)
                    return

            if not self._clipboard_monitor_started:
                self.clipboard_manager.on_copy(self.send)
                self._clipboard_monitor_started = True

            if not self.connection_controller.connect_succeeded():
                self._close_client_safely(client)
                return
            self._last_connect_message = "Websocket connected"
        except Exception as exception:
            error = self._normalize_exception(exception)
            self._last_connect_message = error.message
            with self._client_lock:
                if self.client is client:
                    self.client = None
            if client is not None:
                self._close_client_safely(client)

            if (
                self.connection_controller.snapshot().state
                is not ConnectionState.CONNECTING
            ):
                return

            if error.code in (
                ConnectionErrorCode.AUTH_REJECTED,
                ConnectionErrorCode.SESSION_EXPIRED,
            ):
                self.connection_controller.connect_failed_auth(error)
            elif self.is_login_phase:
                self.connection_controller.connect_failed_fatal(error)
            elif error.recoverable:
                self.connection_controller.connect_failed_recoverable(error)
            else:
                self.connection_controller.connect_failed_fatal(error)

    def _on_stomp_error(self, frame):
        logging.error(
            "STOMP server returned ERROR: %s",
            frame.body if getattr(frame, "body", None) else frame.headers,
        )

    def _on_client_close(self, client) -> None:
        with self._client_lock:
            if self.client is not client:
                return
            self.client = None

        error = ConnectionErrorInfo(
            code=ConnectionErrorCode.TRANSPORT_CLOSED,
            message="WebSocket connection closed",
            recoverable=True,
        )
        state = self.connection_controller.snapshot().state
        if state is ConnectionState.CONNECTED:
            self.connection_controller.transport_closed_recoverable(error)
        elif state is ConnectionState.CONNECTING:
            if self.is_login_phase:
                self.connection_controller.connect_failed_fatal(error)
            else:
                self.connection_controller.connect_failed_recoverable(error)

    def _stop_transport(self) -> None:
        self._cleanup_transport_resources()
        self.connection_controller.stop_completed()

    def _cleanup_transport_resources(self) -> None:
        self._dispose_current_client()
        self.clipboard_manager.previous_clipboard_hash = 0
        if self._clipboard_monitor_started:
            try:
                self.clipboard_manager.stop()
            except Exception as exception:
                logging.error(f"Failed to stop clipboard monitoring: {exception}")
            finally:
                self._clipboard_monitor_started = False

    def _dispose_current_client(self) -> None:
        with self._client_lock:
            client = self.client
            self.client = None
        if client is not None:
            self._close_client_safely(client)

    @staticmethod
    def _close_client_safely(client) -> None:
        try:
            client.on_close_callback = None
            client.disconnect()
        except Exception:
            pass

    def _on_connection_snapshot(self, snapshot: ConnectionSnapshot) -> None:
        previous = self._last_observed_state
        self._last_observed_state = snapshot.state

        if self.is_login_phase:
            return
        if (
            snapshot.state is ConnectionState.RECONNECT_WAIT
            and previous is ConnectionState.CONNECTED
        ):
            self.notification_manager.notify(
                title=f"{APP_NAME}: WebSocket Connection Lost ⛓️‍💥",
                message="Check your internet connection. Retrying…",
            )
        elif (
            snapshot.state is ConnectionState.CONNECTED
            and snapshot.reconnect_attempt > 0
            and previous is ConnectionState.CONNECTING
        ):
            self.notification_manager.notify(
                title=f"{APP_NAME}: WebSocket Connection Restored 🔗",
                message="Connection re-established",
            )

    @staticmethod
    def _log_connection_diagnostic(code: str, message: str) -> None:
        logging.warning("connection.%s: %s", code.lower(), message)

    @staticmethod
    def _normalize_exception(exception: Exception) -> ConnectionErrorInfo:
        text = str(exception)
        lowered = text.lower()

        if isinstance(exception, TimeoutError):
            return ConnectionErrorInfo(
                ConnectionErrorCode.CONNECT_TIMEOUT,
                "WebSocket connection timed out",
                True,
                type(exception).__name__,
            )
        if isinstance(exception, socket.gaierror):
            return ConnectionErrorInfo(
                ConnectionErrorCode.DNS_FAILURE,
                "Server name could not be resolved",
                True,
                type(exception).__name__,
            )
        if isinstance(exception, ssl.SSLError):
            return ConnectionErrorInfo(
                ConnectionErrorCode.TLS_FAILURE,
                "Secure WebSocket negotiation failed",
                True,
                type(exception).__name__,
            )
        if any(
            marker in lowered
            for marker in (
                "authentication",
                "unauthorized",
                "forbidden",
                "session expired",
                " 401",
                " 403",
            )
        ):
            return ConnectionErrorInfo(
                ConnectionErrorCode.AUTH_REJECTED,
                "Login session was rejected",
                False,
                type(exception).__name__,
            )
        if isinstance(exception, (KeyError, ValueError)):
            return ConnectionErrorInfo(
                ConnectionErrorCode.LOCAL_CONFIGURATION_ERROR,
                "Local WebSocket configuration is invalid",
                False,
                type(exception).__name__,
            )
        if isinstance(exception, ConnectionError):
            return ConnectionErrorInfo(
                ConnectionErrorCode.TRANSPORT_CLOSED,
                "WebSocket or STOMP handshake failed",
                True,
                type(exception).__name__,
            )
        if isinstance(exception, OSError):
            return ConnectionErrorInfo(
                ConnectionErrorCode.NETWORK_UNREACHABLE,
                "Network connection could not be established",
                True,
                type(exception).__name__,
            )
        return ConnectionErrorInfo(
            ConnectionErrorCode.UNKNOWN_TRANSPORT_ERROR,
            "Unexpected WebSocket transport failure",
            True,
            type(exception).__name__,
        )

    def send(self, payload: str, payload_type: str = "text"):
        try:
            if not self.is_connected:
                return
            client = self.client
            if client is None:
                return
            if self.clipboard_manager.has_clipboard_changed(payload):
                if self.config.data["cipher_enabled"]:
                    payload = CipherManager.encode_to_json_string(
                        **self.cipher_manager.encrypt(payload)
                    )
                body = json.dumps({"payload": payload, "type": payload_type})
                client.send(destination=SEND_DESTINATION, body=body)
                self.connection_controller.record_send()
        except Exception as exception:
            logging.error(f"Failed to send data: {exception}")

    def _receive(self, frame: any) -> str:
        try:
            if self.is_connected:
                body = json.loads(frame.body)
                payload = body["payload"]
                payload_type = body.get("type", "text")
                if self.config.data["cipher_enabled"]:
                    payload = self.cipher_manager.decrypt(
                        **CipherManager.decode_from_json_string(payload)
                    )

                if self.clipboard_manager.has_clipboard_changed(payload):
                    self.clipboard_manager.base64_to_clipboard(
                        base64_string=payload,
                        type_=payload_type,
                    )
                self.connection_controller.record_receive()
        except json.decoder.JSONDecodeError:
            logging.error(
                "If cipher is enabled, please make sure it is enabled on all devices"
            )
        except Exception as exception:
            logging.error(f"Failed to receive data: {exception}")

    def manual_reconnect(self):
        state = self.connection_controller.snapshot().state
        if state is ConnectionState.RECONNECT_WAIT:
            return self.connection_controller.manual_reconnect()
        if state in (
            ConnectionState.AUTH_REQUIRED,
            ConnectionState.FATAL_ERROR,
        ):
            self.connection_controller.configuration_replaced()
            return self.connection_controller.connect()
        if state is ConnectionState.DISCONNECTED:
            return self.connection_controller.connect()
        return False

    def disconnect(self):
        state = self.connection_controller.snapshot().state
        if state is ConnectionState.DISCONNECTED:
            self._cleanup_transport_resources()
            return
        if state is ConnectionState.STOPPING:
            return
        self.connection_controller.disconnect()
