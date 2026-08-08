"""Pure presentation mapping for desktop tray and CLI connection controls."""

from dataclasses import dataclass
from enum import Enum
from typing import Any, Optional

from .state import ConnectionState


class TrayPrimaryAction(str, Enum):
    CONNECT = "CONNECT"
    RECONNECT = "RECONNECT"
    DISCONNECT = "DISCONNECT"
    NONE = "NONE"


@dataclass(frozen=True)
class TrayConnectionView:
    label: str
    action: TrayPrimaryAction
    enabled: bool
    authoritative: bool
    state: Optional[ConnectionState]


def derive_tray_connection_view(
    ws_interface: Any,
    *,
    legacy_is_connected: bool,
) -> TrayConnectionView:
    """Map transport truth to one primary tray action.

    P2S exposes ``get_connection_snapshot`` and therefore receives the complete
    state mapping. P2P remains on its existing boolean contract until it is
    migrated to the same controller; the fallback preserves current behavior.
    """

    snapshot_getter = getattr(ws_interface, "get_connection_snapshot", None)
    if callable(snapshot_getter):
        try:
            state = snapshot_getter().state
        except Exception:
            # A broken status getter must not remove the user's disconnect path.
            state = None
        else:
            if state is ConnectionState.DISCONNECTED:
                return TrayConnectionView(
                    "🔗 Connect",
                    TrayPrimaryAction.CONNECT,
                    True,
                    True,
                    state,
                )
            if state is ConnectionState.CONNECTING:
                return TrayConnectionView(
                    "⛓️‍💥 Cancel connection",
                    TrayPrimaryAction.DISCONNECT,
                    True,
                    True,
                    state,
                )
            if state is ConnectionState.CONNECTED:
                return TrayConnectionView(
                    "⛓️‍💥 Disconnect",
                    TrayPrimaryAction.DISCONNECT,
                    True,
                    True,
                    state,
                )
            if state is ConnectionState.RECONNECT_WAIT:
                return TrayConnectionView(
                    "🔄 Reconnect now",
                    TrayPrimaryAction.RECONNECT,
                    True,
                    True,
                    state,
                )
            if state is ConnectionState.AUTH_REQUIRED:
                return TrayConnectionView(
                    "🔐 Login required — log off and sign in again",
                    TrayPrimaryAction.NONE,
                    False,
                    True,
                    state,
                )
            if state is ConnectionState.STOPPING:
                return TrayConnectionView(
                    "⏳ Stopping…",
                    TrayPrimaryAction.NONE,
                    False,
                    True,
                    state,
                )
            if state is ConnectionState.FATAL_ERROR:
                return TrayConnectionView(
                    "🔄 Retry connection",
                    TrayPrimaryAction.CONNECT,
                    True,
                    True,
                    state,
                )

    if legacy_is_connected:
        return TrayConnectionView(
            "⛓️‍💥 Disconnect",
            TrayPrimaryAction.DISCONNECT,
            True,
            False,
            None,
        )
    return TrayConnectionView(
        "🔗 Connect",
        TrayPrimaryAction.CONNECT,
        True,
        False,
        None,
    )
