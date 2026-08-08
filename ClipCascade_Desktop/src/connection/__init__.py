"""Authoritative desktop connection-state primitives.

This package is intentionally independent from STOMP, GUI, clipboard, and
platform-specific modules so its behavior can be tested deterministically.
"""

from .controller import ConnectionController
from .errors import ConnectionErrorCode, ConnectionErrorInfo
from .retry import RetryPolicy
from .state import ConnectionEvent, ConnectionSnapshot, ConnectionState

__all__ = [
    "ConnectionController",
    "ConnectionErrorCode",
    "ConnectionErrorInfo",
    "ConnectionEvent",
    "ConnectionSnapshot",
    "ConnectionState",
    "RetryPolicy",
]
