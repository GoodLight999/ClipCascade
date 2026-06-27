from gui.info import CustomDialog


class LiveStatusDialog(CustomDialog):
    def __init__(self, manager, config):
        self.manager = manager
        self.config = config
        self.refresh_id = None
        super().__init__(self._build_message(), msg_type="info")
        self.refresh_id = self.after(1000, self._refresh)

    def _build_message(self):
        connected = bool(getattr(self.manager, "is_connected", False))
        reconnecting = bool(
            getattr(self.manager, "is_auto_reconnecting", False)
        )
        if connected:
            state = "Connected"
        elif reconnecting:
            state = "Reconnecting"
        else:
            state = "Disconnected"

        data = self.config.data if self.config is not None else {}
        stats = self.manager.get_stats() if self.manager is not None else None
        return (
            f"State: {state}\n"
            f"Server: {data.get('server_url', 'unknown')}\n"
            f"Mode: {data.get('server_mode', 'unknown')}\n"
            f"Status: {stats or 'No active transfer'}"
        )

    def _refresh(self):
        self.text_widget.config(state="normal")
        self.text_widget.delete("1.0", "end")
        self.text_widget.insert("1.0", self._build_message())
        self.text_widget.config(state="disabled")
        self.refresh_id = self.after(1000, self._refresh)

    def close(self):
        if self.refresh_id is not None:
            self.after_cancel(self.refresh_id)
            self.refresh_id = None
        super().close()
