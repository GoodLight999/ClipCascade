import os
import threading
from tkinter import ttk

from core.constants import APP_VERSION, LOG_FILE_NAME, get_program_files_directory
from gui.info import CustomDialog


class LiveStatusDialog(CustomDialog):
    def __init__(
        self,
        manager,
        config,
        restart_callback=None,
        connect_callback=None,
        disconnect_callback=None,
    ):
        self.manager = manager
        self.config = config
        self.restart_callback = restart_callback
        self.connect_callback = connect_callback
        self.disconnect_callback = disconnect_callback
        self.refresh_id = None
        self.operation_lock = threading.Lock()
        self.last_operation = "Ready"

        super().__init__(self._build_message(), msg_type="info")
        self.title("ClipCascade Status")
        self.geometry("680x420")
        self.resizable(True, True)
        self.after(750, self._release_topmost)

        controls = ttk.Frame(self, padding=(20, 0, 20, 14))
        controls.pack(fill="x")
        ttk.Button(
            controls,
            text="Restart sync",
            command=lambda: self._run_operation(
                self.restart_callback,
                "Restarting synchronization",
            ),
        ).pack(side="left")
        ttk.Button(
            controls,
            text="Reconnect",
            command=lambda: self._run_operation(
                self.connect_callback,
                "Requesting reconnect",
            ),
        ).pack(side="left", padx=(8, 0))
        ttk.Button(
            controls,
            text="Disconnect",
            command=lambda: self._run_operation(
                self.disconnect_callback,
                "Disconnecting",
            ),
        ).pack(side="left", padx=(8, 0))
        ttk.Button(
            controls,
            text="Open logs",
            command=self._open_logs,
        ).pack(side="left", padx=(18, 0))
        ttk.Button(
            controls,
            text="Copy diagnostics",
            command=self._copy_diagnostics,
        ).pack(side="left", padx=(8, 0))

        self.refresh_id = self.after(500, self._refresh)

    def _release_topmost(self):
        try:
            self.attributes("-topmost", False)
        except Exception:
            pass

    def _state(self):
        connected = bool(getattr(self.manager, "is_connected", False))
        reconnecting = bool(
            getattr(self.manager, "is_auto_reconnecting", False)
        )
        if connected:
            return "Connected"
        if reconnecting:
            return "Reconnecting"
        if getattr(self.manager, "disconnected", False):
            return "Disconnected by user"
        return "Offline"

    def _build_message(self):
        data = self.config.data if self.config is not None else {}
        stats = self.manager.get_stats() if self.manager is not None else None
        return (
            f"State: {self._state()}\n"
            f"Server: {data.get('server_url', 'unknown')}\n"
            f"Mode: {data.get('server_mode', 'unknown')}\n"
            f"Transport status: {stats or 'No active transfer'}\n"
            f"Automatic reconnect: "
            f"{bool(getattr(self.manager, 'is_auto_reconnecting', False))}\n"
            f"Last operation: {self.last_operation}"
        )

    def _run_operation(self, callback, description):
        if callback is None or not self.operation_lock.acquire(blocking=False):
            return
        self.last_operation = description

        def worker():
            try:
                result = callback()
                if result is False:
                    self.last_operation = description + " — not completed"
                else:
                    self.last_operation = description + " — completed"
            except Exception as error:
                self.last_operation = description + f" — failed: {error}"
            finally:
                self.operation_lock.release()

        threading.Thread(target=worker, daemon=True).start()

    def _diagnostics(self):
        data = self.config.data if self.config is not None else {}
        stats = self.manager.get_stats() if self.manager is not None else None
        return "\n".join(
            (
                f"ClipCascade version: {APP_VERSION}",
                f"State: {self._state()}",
                f"Server: {data.get('server_url', 'unknown')}",
                f"Mode: {data.get('server_mode', 'unknown')}",
                f"Transport: {stats or 'No active transfer'}",
                f"Auto reconnect: {bool(getattr(self.manager, 'is_auto_reconnecting', False))}",
                f"User disconnected: {bool(getattr(self.manager, 'disconnected', False))}",
                f"Last operation: {self.last_operation}",
                f"Log file: {os.path.join(get_program_files_directory(), LOG_FILE_NAME)}",
            )
        )

    def _copy_diagnostics(self):
        self.clipboard_clear()
        self.clipboard_append(self._diagnostics())
        self.update_idletasks()
        self.last_operation = "Diagnostics copied"

    def _open_logs(self):
        path = os.path.join(get_program_files_directory(), LOG_FILE_NAME)
        try:
            if not os.path.exists(path):
                raise FileNotFoundError(path)
            os.startfile(path)
            self.last_operation = "Opened log file"
        except Exception as error:
            self.last_operation = f"Could not open log file: {error}"

    def _refresh(self):
        try:
            self.text_widget.config(state="normal")
            self.text_widget.delete("1.0", "end")
            self.text_widget.insert("1.0", self._build_message())
            self.text_widget.config(state="disabled")
            self.refresh_id = self.after(1000, self._refresh)
        except Exception:
            self.refresh_id = None

    def close(self):
        if self.refresh_id is not None:
            self.after_cancel(self.refresh_id)
            self.refresh_id = None
        super().close()
