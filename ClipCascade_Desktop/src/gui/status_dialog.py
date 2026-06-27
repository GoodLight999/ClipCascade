from gui.live_status_dialog import LiveStatusDialog


def show_connection_status(
    manager,
    config,
    restart_callback=None,
    connect_callback=None,
    disconnect_callback=None,
):
    LiveStatusDialog(
        manager,
        config,
        restart_callback=restart_callback,
        connect_callback=connect_callback,
        disconnect_callback=disconnect_callback,
    ).mainloop()
