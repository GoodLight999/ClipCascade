import logging
import threading
import time
from logging.handlers import RotatingFileHandler

from core.application import Application
from core.constants import GITHUB_URL, LOG_LEVEL
from gui.enhanced_tray import EnhancedTaskbarPanel


class WindowsApplication(Application):
    """Windows application shell with visible status and restart controls."""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._restart_lock = threading.Lock()

    def setup_logging(self):
        formatter = logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")
        handler = RotatingFileHandler(
            self.log_file_path,
            maxBytes=2 * 1024 * 1024,
            backupCount=4,
            encoding="utf-8",
        )
        handler.setFormatter(formatter)

        root_logger = logging.getLogger()
        root_logger.handlers.clear()
        root_logger.setLevel(LOG_LEVEL)
        root_logger.addHandler(handler)

    @staticmethod
    def _stop_manager_for_restart(manager):
        """Stop both synchronous STOMP and asynchronous P2P managers completely."""
        if hasattr(manager, "schedule_task") and hasattr(manager, "_disconnect"):
            future = manager.schedule_task(manager._disconnect())
            future.result(timeout=15)
            return

        manager.disconnect()
        deadline = time.monotonic() + 5
        while getattr(manager, "is_connected", False):
            if time.monotonic() >= deadline:
                raise TimeoutError("Timed out while stopping the synchronization engine")
            time.sleep(0.05)

    def restart_sync(self):
        if not self._restart_lock.acquire(blocking=False):
            return
        try:
            manager = self._get_ws_manager()
            logging.info("Restarting synchronization engine")
            self._stop_manager_for_restart(manager)

            manager.disconnected = False
            manager.is_auto_reconnecting = False
            manager.is_login_phase = False
            success, message = manager.connect()
            if not success:
                raise RuntimeError(message or "Connection restart failed")
            logging.info("Synchronization engine restart requested successfully")
        finally:
            self._restart_lock.release()

    def run(self):
        try:
            self.banner()
            self.setup_logging()
            self.ensure_single_instance()
            self.config.load()
            self.authenticate_and_connect()
            self.config.save()
            update_available = self.get_version_update_status()
            donation_url = self.get_donation_url()

            manager = self._get_ws_manager()
            sys_tray = EnhancedTaskbarPanel(
                on_connect_callback=manager.manual_reconnect,
                on_disconnect_callback=manager.disconnect,
                on_restart_callback=self.restart_sync,
                on_logoff_callback=self.logoff_and_exit,
                new_version_available=update_available,
                github_url=GITHUB_URL,
                donation_url=donation_url,
                ws_interface=manager,
                config=self.config,
            )
            manager.set_tray_ref(sys_tray)
            sys_tray.run()
        except Exception as error:
            logging.exception("Unexpected Windows application error: %s", error)
            from gui.info import CustomDialog

            CustomDialog(
                f"An unexpected error has occurred: {error}\nCheck the log file for details.",
                msg_type="error",
            ).mainloop()
        finally:
            try:
                self._get_ws_manager().disconnect()
            except Exception:
                logging.exception("Failed to disconnect during shutdown")
