from gui.info import CustomDialog


def show_connection_status(manager, config):
    connected = bool(getattr(manager, "is_connected", False))
    reconnecting = bool(getattr(manager, "is_auto_reconnecting", False))
    state = "Connected" if connected else "Reconnecting" if reconnecting else "Disconnected"
    data = config.data if config is not None else {}
    stats = manager.get_stats() if manager is not None else None
    message = (
        f"State: {state}\n"
        f"Server: {data.get('server_url', 'unknown')}\n"
        f"Mode: {data.get('server_mode', 'unknown')}\n"
        f"Status: {stats or 'No active transfer'}"
    )
    CustomDialog(message, msg_type="info").mainloop()
