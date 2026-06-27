import logging
import os
import threading
import time
from logging.handlers import RotatingFileHandler

from core.application import Application
from core.constants import GITHUB_URL, LOG_FORMAT if False else None
from core.constants import LINUX, MACOS, PLATFORM
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
        root_logger.setLevel(logging.INFO)
        root_logger.addHandler(handler)

    def restart_sync(self):
        if not self._restart_lock.acquire(blocking=False):
            return
        try:
            manager = self._get_ws_manager()
            manager.disconnect()
            time.sleep(0.25)
            manager.disconnected = False
            manager.is_login_phase = False
            success, message = manager.connect()
            if not success:
                raise RuntimeError(message or "Connection restart failed")
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

            sys_tray = EnhancedTaskbarPanel(
                on_connect_callback=self._get_ws_manager().manual_reconnect,
                on_disconnect_callback=self._get_ws_manager().disconnect,
                on_restart_callback=self.restart_sync,
                on_logoff_callback=self.logoff_and_exit,
                new_version_available=update_available,
                github_url=GITHUB_URL,
                donation_url=donation_url,
                ws_interface=self._get_ws_manager(),
                config=self.config,
            )
            self._get_ws_manager().set_tray_ref(sys_tray)
            sys_tray.run()
        except Exception as error:
            logging.exception("Unexpected Windows application error: %s", error)
            from gui.info import CustomDialog

            CustomDialog(
                f"An unexpected error has occurred: {error}\nCheck the log file for details.",
                msg_type="error",
            ).mainloop()
        finally:
            self._get_ws_manager().disconnect()
            if PLATFORM == MACOS or PLATFORM.startswith(LINUX):
                if getattr(self, "lock_file", None) is not None:
                    self.lock_file.close()
                    try:
                        os.remove(self.mutex_identifier)
                    except OSError:
                        pass
