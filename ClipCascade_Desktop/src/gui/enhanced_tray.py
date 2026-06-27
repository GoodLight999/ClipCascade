import threading

from pystray import Menu, MenuItem as item

from gui.status_dialog import show_connection_status
from gui.tray import TaskbarPanel


class EnhancedTaskbarPanel(TaskbarPanel):
    def __init__(self, *args, on_restart_callback=None, **kwargs):
        self.on_restart_callback = on_restart_callback
        self._operation_lock = threading.Lock()
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

    def _open_status(self, icon=None, menu_item=None):
        show_connection_status(self.ws_interface, self.config)

    def _restart_sync(self, icon=None, menu_item=None):
        if self.on_restart_callback is None:
            return

        def worker():
            if not self._operation_lock.acquire(blocking=False):
                return
            try:
                self.on_restart_callback()
            finally:
                self._operation_lock.release()
                self.is_connected = bool(
                    getattr(self.ws_interface, "is_connected", False)
                )
                self.update_menu()

        threading.Thread(target=worker, daemon=True).start()
