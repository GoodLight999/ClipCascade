"""Transport-independent desktop connection error model."""

from dataclasses import dataclass
from enum import Enum
from typing import Optional


class ConnectionErrorCode(str, Enum):
    NETWORK_UNREACHABLE = "NETWORK_UNREACHABLE"
    DNS_FAILURE = "DNS_FAILURE"
    CONNECT_TIMEOUT = "CONNECT_TIMEOUT"
    TLS_FAILURE = "TLS_FAILURE"
    SERVER_UNAVAILABLE = "SERVER_UNAVAILABLE"
    SESSION_EXPIRED = "SESSION_EXPIRED"
    AUTH_REJECTED = "AUTH_REJECTED"
    PROTOCOL_ERROR = "PROTOCOL_ERROR"
    LOCAL_CONFIGURATION_ERROR = "LOCAL_CONFIGURATION_ERROR"
    TRANSPORT_CLOSED = "TRANSPORT_CLOSED"
    UNKNOWN_TRANSPORT_ERROR = "UNKNOWN_TRANSPORT_ERROR"


@dataclass(frozen=True)
class ConnectionErrorInfo:
    """Sanitized error data safe to expose through connection snapshots."""

    code: ConnectionErrorCode
    message: str
    recoverable: bool
    exception_type: Optional[str] = None

    @classmethod
    def from_exception(
        cls,
        code: ConnectionErrorCode,
        message: str,
        recoverable: bool,
        exception: BaseException,
    ) -> "ConnectionErrorInfo":
        return cls(
            code=code,
            message=message,
            recoverable=recoverable,
            exception_type=type(exception).__name__,
        )
