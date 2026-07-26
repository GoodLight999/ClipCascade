import unittest
from types import SimpleNamespace

from connection.state import ConnectionState
from connection.tray_view import (
    TrayPrimaryAction,
    derive_tray_connection_view,
)


class SnapshotInterface:
    def __init__(self, state):
        self.state = state

    def get_connection_snapshot(self):
        return SimpleNamespace(state=self.state)


class BrokenSnapshotInterface:
    def get_connection_snapshot(self):
        raise RuntimeError("snapshot unavailable")


class LegacyInterface:
    pass


class TrayViewTests(unittest.TestCase):
    def test_disconnected_offers_connect(self):
        view = derive_tray_connection_view(
            SnapshotInterface(ConnectionState.DISCONNECTED),
            legacy_is_connected=True,
        )
        self.assertEqual(view.action, TrayPrimaryAction.CONNECT)
        self.assertTrue(view.authoritative)

    def test_connecting_offers_cancellation(self):
        view = derive_tray_connection_view(
            SnapshotInterface(ConnectionState.CONNECTING),
            legacy_is_connected=False,
        )
        self.assertEqual(view.action, TrayPrimaryAction.DISCONNECT)
        self.assertIn("Cancel", view.label)

    def test_connected_offers_disconnect(self):
        view = derive_tray_connection_view(
            SnapshotInterface(ConnectionState.CONNECTED),
            legacy_is_connected=False,
        )
        self.assertEqual(view.action, TrayPrimaryAction.DISCONNECT)

    def test_reconnect_wait_offers_immediate_retry(self):
        view = derive_tray_connection_view(
            SnapshotInterface(ConnectionState.RECONNECT_WAIT),
            legacy_is_connected=True,
        )
        self.assertEqual(view.action, TrayPrimaryAction.RECONNECT)
        self.assertIn("Reconnect now", view.label)

    def test_auth_required_is_explicit_and_disabled(self):
        view = derive_tray_connection_view(
            SnapshotInterface(ConnectionState.AUTH_REQUIRED),
            legacy_is_connected=True,
        )
        self.assertEqual(view.action, TrayPrimaryAction.NONE)
        self.assertFalse(view.enabled)
        self.assertIn("Login required", view.label)

    def test_stopping_is_disabled(self):
        view = derive_tray_connection_view(
            SnapshotInterface(ConnectionState.STOPPING),
            legacy_is_connected=True,
        )
        self.assertEqual(view.action, TrayPrimaryAction.NONE)
        self.assertFalse(view.enabled)

    def test_fatal_error_offers_explicit_retry(self):
        view = derive_tray_connection_view(
            SnapshotInterface(ConnectionState.FATAL_ERROR),
            legacy_is_connected=False,
        )
        self.assertEqual(view.action, TrayPrimaryAction.CONNECT)
        self.assertIn("Retry", view.label)

    def test_legacy_connected_fallback_preserves_p2p_behavior(self):
        view = derive_tray_connection_view(
            LegacyInterface(),
            legacy_is_connected=True,
        )
        self.assertEqual(view.action, TrayPrimaryAction.DISCONNECT)
        self.assertFalse(view.authoritative)

    def test_broken_snapshot_falls_back_without_hiding_controls(self):
        view = derive_tray_connection_view(
            BrokenSnapshotInterface(),
            legacy_is_connected=False,
        )
        self.assertEqual(view.action, TrayPrimaryAction.CONNECT)
        self.assertFalse(view.authoritative)


if __name__ == "__main__":
    unittest.main()
