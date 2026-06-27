def format_connection_state(connected: bool, reconnecting: bool) -> str:
    if connected:
        return "Connected"
    if reconnecting:
        return "Reconnecting"
    return "Disconnected"
