import logging
import time
from threading import Event, Thread

import websocket

from .frame import Frame

VERSIONS = "1.0,1.1"


class Client:

    def __init__(self, url, headers=None, on_close_callback=None, sslopt=None):
        self.url = url
        self._ws_sslopt = sslopt
        self.ws = websocket.WebSocketApp(self.url, headers or {})
        self.ws.on_open = self._on_open
        self.ws.on_message = self._on_message
        self.ws.on_error = self._on_error
        self.ws.on_close = self._on_close
        self.on_close_callback = on_close_callback

        self.opened = False
        self.connected = False
        self.counter = 0
        self.subscriptions = {}

        self._connectCallback = None
        self.errorCallback = None
        self._opened_event = Event()
        self._connection_result_event = Event()
        self._last_connection_error = None

    @staticmethod
    def _timeout_seconds(timeout_ms):
        return None if timeout_ms is None or timeout_ms <= 0 else timeout_ms / 1000.0

    def _connect(self, timeout=0):
        if self._ws_sslopt:
            thread = Thread(
                target=lambda: self.ws.run_forever(sslopt=self._ws_sslopt)
            )
        else:
            thread = Thread(target=self.ws.run_forever)
        thread.daemon = True
        thread.start()

        if not self._opened_event.wait(self._timeout_seconds(timeout)):
            self._abort_initial_connection()
            raise TimeoutError(f"Connection to {self.url} timed out")

        if not self.opened:
            error = self._last_connection_error
            if error is not None:
                raise ConnectionError(
                    f"Connection to {self.url} failed: {error}"
                ) from error
            raise ConnectionError(f"Connection to {self.url} closed before opening")

    def _on_open(self, ws_app, *args):
        self.opened = True
        self._opened_event.set()

    def _on_close(self, ws_app, *args):
        self.connected = False
        self.opened = False
        logging.debug("Whoops! Lost connection to " + self.ws.url)
        try:
            if self.on_close_callback is not None:
                self.on_close_callback()
        finally:
            self._clean_up()
            self._opened_event.set()
            self._connection_result_event.set()

    def _on_error(self, ws_app, error, *args):
        logging.debug(error)
        if not self.connected:
            self._last_connection_error = (
                error if isinstance(error, BaseException) else RuntimeError(str(error))
            )
            self._opened_event.set()
            self._connection_result_event.set()

    def _on_message(self, ws_app, message, *args):
        if message == "\n":
            logging.debug("Received heartbeat frame")
            self.ws.send("\n")
            logging.debug("Sent heartbeat frame")
            return

        logging.debug("\n<<< " + str(message))
        frame = Frame.unmarshall_single(message)
        _results = []
        if frame.command == "CONNECTED":
            try:
                if self._connectCallback is not None:
                    _results.append(self._connectCallback(frame))
                self.connected = True
                self._last_connection_error = None
                logging.debug("connected to server " + self.url)
            except Exception as error:
                self.connected = False
                self._last_connection_error = error
                logging.error(f"STOMP connected callback failed: {error}")
            finally:
                self._connection_result_event.set()
        elif frame.command == "MESSAGE":
            subscription = frame.headers["subscription"]

            if subscription in self.subscriptions:
                onreceive = self.subscriptions[subscription]
                messageID = frame.headers["message-id"]

                def ack(headers):
                    if headers is None:
                        headers = {}
                    return self.ack(messageID, subscription, headers)

                def nack(headers):
                    if headers is None:
                        headers = {}
                    return self.nack(messageID, subscription, headers)

                frame.ack = ack
                frame.nack = nack
                _results.append(onreceive(frame))
            else:
                info = "Unhandled received MESSAGE: " + str(frame)
                logging.debug(info)
                _results.append(info)
        elif frame.command == "RECEIPT":
            pass
        elif frame.command == "ERROR":
            error = RuntimeError(
                "STOMP error: " + (str(frame.body) if frame.body else str(frame.headers))
            )
            self._last_connection_error = error
            try:
                if self.errorCallback is not None:
                    _results.append(self.errorCallback(frame))
            finally:
                self._connection_result_event.set()
        else:
            info = "Unhandled received MESSAGE: " + frame.command
            logging.debug(info)
            _results.append(info)

        return _results

    def _transmit(self, command, headers, body=None):
        out = Frame.marshall(command, headers, body)
        logging.debug("\n>>> " + out)
        self.ws.send(out)

    def connect(
        self,
        login=None,
        passcode=None,
        headers=None,
        connectCallback=None,
        errorCallback=None,
        timeout=0,
    ):
        """Open WebSocket and wait until the STOMP CONNECTED frame is handled."""

        logging.debug("Opening web socket...")
        started_at = time.monotonic()
        self._connectCallback = connectCallback
        self.errorCallback = errorCallback
        self._connect(timeout)

        headers = headers.copy() if headers is not None else {}
        headers["host"] = self.url
        headers["accept-version"] = VERSIONS
        headers["heart-beat"] = "0,20000"

        if login is not None:
            headers["login"] = login
        if passcode is not None:
            headers["passcode"] = passcode

        self._transmit("CONNECT", headers)

        timeout_seconds = self._timeout_seconds(timeout)
        if timeout_seconds is None:
            remaining = None
        else:
            remaining = max(0.0, timeout_seconds - (time.monotonic() - started_at))

        if not self._connection_result_event.wait(remaining):
            self._abort_initial_connection()
            raise TimeoutError(f"STOMP handshake with {self.url} timed out")

        if not self.connected:
            error = self._last_connection_error
            if error is not None:
                raise ConnectionError(
                    f"STOMP handshake with {self.url} failed: {error}"
                ) from error
            raise ConnectionError(
                f"STOMP handshake with {self.url} closed before CONNECTED"
            )

    def _abort_initial_connection(self):
        try:
            self.ws.on_close = None
            self.ws.close()
        except Exception:
            pass
        self._clean_up()
        self._opened_event.set()
        self._connection_result_event.set()

    def disconnect(self, disconnectCallback=None, headers=None):
        if headers is None:
            headers = {}

        self.ws.on_close = None
        self.ws.close()
        self._clean_up()
        self._opened_event.set()
        self._connection_result_event.set()

        if disconnectCallback is not None:
            disconnectCallback()

    def _clean_up(self):
        self.connected = False
        self.opened = False

    def send(self, destination, headers=None, body=None):
        if headers is None:
            headers = {}
        if body is None:
            body = ""
        headers["destination"] = destination
        return self._transmit("SEND", headers, body)

    def subscribe(self, destination, callback=None, headers=None):
        if headers is None:
            headers = {}
        if "id" not in headers:
            headers["id"] = "sub-" + str(self.counter)
            self.counter += 1
        headers["destination"] = destination
        self.subscriptions[headers["id"]] = callback
        self._transmit("SUBSCRIBE", headers)

        def unsubscribe():
            self.unsubscribe(headers["id"])

        return headers["id"], unsubscribe

    def unsubscribe(self, id):
        del self.subscriptions[id]
        return self._transmit("UNSUBSCRIBE", {"id": id})

    def ack(self, message_id, subscription, headers):
        if headers is None:
            headers = {}
        headers["message-id"] = message_id
        headers["subscription"] = subscription
        return self._transmit("ACK", headers)

    def nack(self, message_id, subscription, headers):
        if headers is None:
            headers = {}
        headers["message-id"] = message_id
        headers["subscription"] = subscription
        return self._transmit("NACK", headers)
