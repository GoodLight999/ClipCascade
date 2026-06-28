import threading
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

from core.windows_application import WindowsApplication
from gui.enhanced_tray import EnhancedTaskbarPanel
from gui.live_status_dialog import LiveStatusDialog


class _FakeRoot:
    def __init__(self):
        self.quit_called = False
        self.destroy_called = False
        self.after_calls = 0

    def after(self, _delay, callback):
        self.after_calls += 1
        callback()

    def quit(self):
        self.quit_called = True

    def destroy(self):
        self.destroy_called = True


class _FakeIcon:
    def __init__(self):
        self.stop_calls = 0

    def stop(self):
        self.stop_calls += 1


class WindowsShutdownTests(unittest.TestCase):
    def test_recovery_status_describes_activity_instead_of_boolean(self):
        dialog = object.__new__(LiveStatusDialog)

        dialog.manager = SimpleNamespace(
            is_auto_reconnecting=False,
            disconnected=False,
        )
        self.assertEqual(dialog._automatic_recovery_status(), "Standing by")

        dialog.manager.is_auto_reconnecting = True
        self.assertEqual(dialog._automatic_recovery_status(), "Retrying now")

        dialog.manager.is_auto_reconnecting = False
        dialog.manager.disconnected = True
        self.assertEqual(dialog._automatic_recovery_status(), "Paused by user")

    def test_explicit_quit_closes_status_window_root_and_icon_once(self):
        tray = object.__new__(EnhancedTaskbarPanel)
        tray._shutdown_requested = threading.Event()
        tray._stop_status_poll = threading.Event()
        tray.on_quit_callback = Mock()
        tray.icon = _FakeIcon()
        tray.root = _FakeRoot()

        with patch("gui.enhanced_tray.close_connection_status") as close_status:
            tray._request_shutdown()
            tray._request_shutdown()

        self.assertTrue(tray._shutdown_requested.is_set())
        self.assertTrue(tray._stop_status_poll.is_set())
        tray.on_quit_callback.assert_called_once_with()
        close_status.assert_called_once_with()
        self.assertEqual(tray.icon.stop_calls, 1)
        self.assertTrue(tray.root.quit_called)
        self.assertTrue(tray.root.destroy_called)

    def test_application_records_explicit_quit(self):
        app = object.__new__(WindowsApplication)
        app._explicit_quit_requested = threading.Event()
        app._request_explicit_quit()
        self.assertTrue(app._explicit_quit_requested.is_set())


if __name__ == "__main__":
    unittest.main()
