import ctypes
import logging
import os
import sys
import threading
import time
from logging.handlers import RotatingFileHandler

from core.application import Application
from core.constants import GITHUB_URL, LOG_LEVEL
from gui.enhanced_tray import EnhancedTaskbarPanel, get_show_window_request_path


class WindowsApplication(Application):
    """Windows application shell with visible status and supervised recovery."""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._restart_lock = threading.Lock()
        self._watchdog_stop = threading.Event()
        self._watchdog_thread = None

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

    def ensure_single_instance(self):
        ctypes.windll.kernel32.CreateMutexW(None, False, self.mutex_identifier)
        if ctypes.windll.kernel32.GetLastError() != 183:
            return

        request_path = get_show_window_request_path()
        try:
            os.makedirs(os.path.dirname(request_path), exist_ok=True)
            with open(request_path, "w", encoding="utf-8") as request_file:
                request_file.write(str(time.time()))
        except OSError:
            logging.exception("Failed to ask the running instance to show its window")
        sys.exit(0)

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
            return False
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
            return True
        finally:
            self._restart_lock.release()

    @staticmethod
    def _manager_is_healthy(manager):
        if not bool(getattr(manager, "is_connected", False)):
            return False

        # P2P signaling can remain connected while every data channel is dead.
        peers = getattr(manager, "peers", None)
        if peers is not None:
            try:
                remote_peer_count = len(peers) - (
                    1 if getattr(manager, "my_peer_id", None) in peers else 0
                )
                if remote_peer_count > 0:
                    if hasattr(manager, "_sync_live_connections_count"):
                        manager._sync_live_connections_count()
                    return int(getattr(manager, "live_connections", 0)) > 0
            except Exception:
                logging.exception("Failed to inspect P2P transport health")
        return True

    def _watchdog_loop(self, manager):
        unhealthy_since = None
        retry_delay = 10
        next_attempt_at = 0.0

        while not self._watchdog_stop.wait(2):
            try:
                if bool(getattr(manager, "disconnected", False)):
                    unhealthy_since = None
                    retry_delay = 10
                    continue

                if self._manager_is_healthy(manager):
                    unhealthy_since = None
                    retry_delay = 10
                    continue

                now = time.monotonic()
                if unhealthy_since is None:
                    unhealthy_since = now
                    logging.warning(
                        "Synchronization watchdog detected an unhealthy connection"
                    )
                    continue

                # Give the manager's native reconnect path time to recover first.
                if now - unhealthy_since < 25 or now < next_attempt_at:
                    continue

                logging.warning(
                    "Watchdog is performing a full synchronization restart"
                )
                try:
                    self.restart_sync()
                except Exception:
                    logging.exception("Watchdog restart failed")
                next_attempt_at = time.monotonic() + retry_delay
                retry_delay = min(retry_delay * 2, 60)
            except Exception:
                logging.exception("Synchronization watchdog iteration failed")

    def _start_watchdog(self, manager):
        self._watchdog_stop.clear()
        self._watchdog_thread = threading.Thread(
            target=self._watchdog_loop,
            args=(manager,),
            name="ClipCascadeSyncWatchdog",
            daemon=True,
        )
        self._watchdog_thread.start()

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
            self._start_watchdog(manager)
            sys_tray.run()
        except SystemExit:
            raise
        except Exception as error:
            logging.exception("Unexpected Windows application error: %s", error)
            from gui.info import CustomDialog

            CustomDialog(
                f"An unexpected error has occurred: {error}\nCheck the log file for details.",
                msg_type="error",
            ).mainloop()
        finally:
            self._watchdog_stop.set()
            try:
                self._get_ws_manager().disconnect()
            except Exception:
                logging.exception("Failed to disconnect during shutdown")
