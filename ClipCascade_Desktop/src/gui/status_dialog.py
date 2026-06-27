from gui.live_status_dialog import LiveStatusDialog


_active_dialog = None


def show_connection_status(
    manager,
    config,
    restart_callback=None,
    connect_callback=None,
    disconnect_callback=None,
):
    global _active_dialog

    if _active_dialog is not None:
        try:
            if _active_dialog.winfo_exists():
                _active_dialog.deiconify()
                _active_dialog.lift()
                _active_dialog.focus_force()
                return
        except Exception:
            _active_dialog = None

    dialog = LiveStatusDialog(
        manager,
        config,
        restart_callback=restart_callback,
        connect_callback=connect_callback,
        disconnect_callback=disconnect_callback,
    )
    _active_dialog = dialog
    try:
        dialog.mainloop()
    finally:
        _active_dialog = None
