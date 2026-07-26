"""Immutable public state for the desktop connection controller."""

from dataclasses import dataclass
from enum import Enum
from typing import Optional


class ConnectionState(str, Enum):
    """Authoritative desktop transport states."""

    DISCONNECTED = "DISCONNECTED"
    CONNECTING = "CONNECTING"
    CONNECTED = "CONNECTED"
    RECONNECT_WAIT = "RECONNECT_WAIT"
    AUTH_REQUIRED = "AUTH_REQUIRED"
    STOPPING = "STOPPING"
    FATAL_ERROR = "FATAL_ERROR"


class ConnectionEvent(str, Enum):
    """Stable events accepted by the controller."""

    CONNECT_REQUESTED = "CONNECT_REQUESTED"
    CONNECT_SUCCEEDED = "CONNECT_SUCCEEDED"
    CONNECT_FAILED_RECOVERABLE = "CONNECT_FAILED_RECOVERABLE"
    CONNECT_FAILED_AUTH = "CONNECT_FAILED_AUTH"
    CONNECT_FAILED_FATAL = "CONNECT_FAILED_FATAL"
    TRANSPORT_CLOSED_RECOVERABLE = "TRANSPORT_CLOSED_RECOVERABLE"
    TRANSPORT_CLOSED_AUTH = "TRANSPORT_CLOSED_AUTH"
    MANUAL_RECONNECT_REQUESTED = "MANUAL_RECONNECT_REQUESTED"
    DISCONNECT_REQUESTED = "DISCONNECT_REQUESTED"
    RETRY_TIMER_EXPIRED = "RETRY_TIMER_EXPIRED"
    STOP_COMPLETED = "STOP_COMPLETED"
    CONFIGURATION_REPLACED = "CONFIGURATION_REPLACED"


@dataclass(frozen=True)
class ConnectionSnapshot:
    """Immutable state consumed by GUI, CLI, and diagnostics.

    Scheduling timestamps are monotonic seconds. A presentation layer may
    separately translate observations to wall-clock values when needed.
    """

    state: ConnectionState
    state_since: float
    reconnect_attempt: int
    next_retry_at: Optional[float]
    last_connected_at: Optional[float]
    last_disconnected_at: Optional[float]
    last_send_at: Optional[float]
    last_receive_at: Optional[float]
    last_error_code: Optional[str]
    last_error_message: Optional[str]
    last_error_recoverable: Optional[bool]
