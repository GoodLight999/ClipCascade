import logging
import threading
import time

from pystray import Menu, MenuItem as item

from gui.status_dialog import show_connection_status
from gui.tray import TaskbarPanel


class EnhancedTaskbarPanel(TaskbarPanel):
    def __init__(self, *args, on_restart_callback=None, **kwargs):
        self.on_restart_callback = on_restart_callback
        self._operation_lock = threading.Lock()
        self._stop_status_poll = threading.Event()
        super().__init__(*args, **kwargs)

    def create_menu(self, item_: tuple = None):
        base_menu = super().create_menu(item_=item_)
        base_items = tuple(getattr(base_menu, "items", ()))
        return Menu(
            item("Open ClipCascade status", self._open_status, default=True),
            item("Restart synchronization", self._restart_sync),
            Menu.SEPARATOR,
            *base_items,
        )

    def run(self):
        self.icon.run_detached()
        self.root.after(0, self._show_status_on_ui_thread)
        self.root.mainloop()

    def _show_status_on_ui_thread(self):
        show_connection_status(
            self.ws_interface,
            self.config,
            restart_callback=self.on_restart_callback,
            connect_callback=self.on_connect_callback,
            disconnect_callback=self.on_disconnect_callback,
        )

    def _open_status(self, icon=None, menu_item=None):
        self.root.after(0, self._show_status_on_ui_thread)

    def _run_manager_operation(self, callback, operation_name):
        if callback is None:
            return

        def worker():
            if not self._operation_lock.acquire(blocking=False):
                return
            try:
                callback()
            except Exception:
                logging.exception("%s failed", operation_name)
            finally:
                self._operation_lock.release()
                self._sync_tray_state()

        threading.Thread(target=worker, daemon=True).start()

    def _restart_sync(self, icon=None, menu_item=None):
        self._run_manager_operation(
            self.on_restart_callback,
            "Synchronization restart",
        )

    def _on_connect(self, icon=None, menu_item=None):
        self._run_manager_operation(self.on_connect_callback, "Reconnect")

    def _on_disconnect(self, icon=None, menu_item=None):
        self._run_manager_operation(self.on_disconnect_callback, "Disconnect")

    def _sync_tray_state(self):
        actual = bool(getattr(self.ws_interface, "is_connected", False))
        changed = actual != self.is_connected
        self.is_connected = actual
        if changed:
            try:
                self.root.after(0, self.update_menu)
            except Exception:
                logging.exception("Failed to update tray state")

    def _update_stats_thread(self):
        while not self._stop_status_poll.wait(1):
            try:
                self._sync_tray_state()
                current_stats = self.ws_interface.get_stats()
                if current_stats is not None and self.previous_stats != current_stats:
                    self.previous_stats = current_stats
                    self.previous_stats_items = (current_stats, 0, None)
                    self.root.after(0, self.update_menu)
            except Exception:
                logging.exception("Tray status polling failed")
                time.sleep(1)

    def _on_quit(self, icon, menu_item):
        self._stop_status_poll.set()
        super()._on_quit(icon, menu_item)

    def _on_logoff(self, icon, menu_item):
        self._stop_status_poll.set()
        super()._on_logoff(icon, menu_item)
