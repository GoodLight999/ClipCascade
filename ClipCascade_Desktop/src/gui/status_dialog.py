from gui.live_status_dialog import LiveStatusDialog


def show_connection_status(manager, config):
    LiveStatusDialog(manager, config).mainloop()
