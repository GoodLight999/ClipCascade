import threading
import unittest
from types import SimpleNamespace

from core.windows_application import WindowsApplication
from gui.tray import TaskbarPanel
from p2p.p2p_manager import P2PManager


class _FakeIcon:
    def __init__(self):
        self.visible_changes = []
        self.stop_calls = 0
        self._visible = True

    @property
    def visible(self):
        return self._visible

    @visible.setter
    def visible(self, value):
        self.visible_changes.append(value)
        self._visible = value

    def stop(self):
        self.stop_calls += 1


class TrayLifecycleTests(unittest.TestCase):
    def tearDown(self):
        TaskbarPanel._active_panel = None

    def _panel(self):
        panel = object.__new__(TaskbarPanel)
        panel.icon = _FakeIcon()
        panel._tray_icon_stopped = False
        panel._tray_icon_visible = True
        return panel

    def test_dispose_hides_before_stop_and_is_idempotent(self):
        panel = self._panel()
        TaskbarPanel._active_panel = panel

        panel._dispose_tray_icon("unit_test")
        panel._dispose_tray_icon("unit_test_repeat")

        self.assertEqual(panel.icon.visible_changes, [False])
        self.assertEqual(panel.icon.stop_calls, 1)
        self.assertTrue(panel._tray_icon_stopped)
        self.assertIsNone(TaskbarPanel._active_panel)

    def test_replacing_active_panel_disposes_previous_icon(self):
        previous = self._panel()
        replacement = self._panel()
        TaskbarPanel._active_panel = previous

        TaskbarPanel._replace_active_panel(replacement)

        self.assertEqual(previous.icon.visible_changes, [False])
        self.assertEqual(previous.icon.stop_calls, 1)
        self.assertIs(TaskbarPanel._active_panel, replacement)


class P2PDiagnosticsTests(unittest.TestCase):
    def _manager(self, websocket_url):
        manager = object.__new__(P2PManager)
        manager.config = SimpleNamespace(
            data={
                "server_mode": "P2P",
                "server_url": "https://clipcascade.sathvik.dev",
                "websocket_url": websocket_url,
            }
        )
        manager.last_transport_error = ""
        manager.last_transport_error_at = 0.0
        return manager

    def test_p2p_scheme_diagnostics_accepts_ws_and_wss_only(self):
        self.assertFalse(self._manager("wss://clipcascade.sathvik.dev/p2psignaling").has_fatal_scheme_error())
        self.assertTrue(self._manager("http://clipcascade.sathvik.dev/p2psignaling").has_fatal_scheme_error())

    def test_p2p_remembers_websocket_library_scheme_errors(self):
        manager = self._manager("wss://clipcascade.sathvik.dev/p2psignaling")
        manager._remember_transport_error("scheme http is invalid - goodbye")
        self.assertTrue(manager.has_fatal_scheme_error())
        diagnostics = manager.get_connection_diagnostics()
        self.assertEqual(diagnostics["websocket_scheme"], "wss")
        self.assertIn("scheme http is invalid", diagnostics["last_transport_error"])


class WatchdogBoundTests(unittest.TestCase):
    def test_windows_application_defines_restart_limit_and_long_backoff(self):
        app = object.__new__(WindowsApplication)
        app._restart_lock = threading.Lock()
        app._watchdog_stop = threading.Event()
        app._watchdog_thread = None
        app._explicit_quit_requested = threading.Event()
        app._watchdog_restart_limit = 3
        app._watchdog_long_backoff_seconds = 15 * 60

        self.assertEqual(app._watchdog_restart_limit, 3)
        self.assertGreaterEqual(app._watchdog_long_backoff_seconds, 15 * 60)


if __name__ == "__main__":
    unittest.main()
