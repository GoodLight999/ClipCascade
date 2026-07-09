from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_required(text: str, before: str, after: str, label: str) -> str:
    if before not in text:
        raise RuntimeError(f"Expected {label} block was not found")
    return text.replace(before, after, 1)


def prepare_tray() -> None:
    path = ROOT / "gui" / "tray.py"
    text = path.read_text(encoding="utf-8")

    text = replace_required(
        text,
        "import math\nimport os\nimport threading\nimport time\n",
        "import atexit\nimport math\nimport os\nimport threading\nimport time\n",
        "tray imports",
    )

    text = replace_required(
        text,
        "class TaskbarPanel:\n    def __init__(\n",
        '''class TaskbarPanel:\n    _active_lock = threading.RLock()\n    _active_panel = None\n\n    @classmethod\n    def _replace_active_panel(cls, panel):\n        with cls._active_lock:\n            previous = cls._active_panel\n            if previous is not None and previous is not panel:\n                logging.warning(\n                    "TrayIcon lifecycle superseding existing panel previous_panel=%s new_panel=%s",\n                    id(previous),\n                    id(panel),\n                )\n                previous._dispose_tray_icon("superseded_by_new_panel")\n            cls._active_panel = panel\n\n    def _log_tray_lifecycle(self, action: str, **fields):\n        details = " ".join(f"{key}={value}" for key, value in fields.items())\n        logging.info(\n            "TrayIcon lifecycle %s panel_id=%s icon_id=%s pid=%s %s",\n            action,\n            id(self),\n            id(getattr(self, "icon", None)),\n            os.getpid(),\n            details,\n        )\n\n    def _dispose_tray_icon(self, reason: str):\n        icon_obj = getattr(self, "icon", None)\n        if icon_obj is None:\n            return\n        if getattr(self, "_tray_icon_stopped", False):\n            self._log_tray_lifecycle("stop_skipped", reason=reason)\n            return\n\n        self._tray_icon_stopped = True\n        self._log_tray_lifecycle("stop_begin", reason=reason)\n        try:\n            icon_obj.visible = False\n            self._tray_icon_visible = False\n            self._log_tray_lifecycle("visible_false", reason=reason)\n        except Exception:\n            logging.exception("Failed to hide tray icon before stop")\n        try:\n            icon_obj.stop()\n            self._log_tray_lifecycle("stop_complete", reason=reason)\n        except Exception:\n            logging.exception("Failed to stop tray icon")\n        finally:\n            with self._active_lock:\n                if self._active_panel is self:\n                    self.__class__._active_panel = None\n\n    def _atexit_stop_icon(self):\n        self._dispose_tray_icon("atexit")\n\n    def __init__(\n''',
        "TaskbarPanel class header",
    )

    text = replace_required(
        text,
        '''        # Create the tray icon\n        self.icon = Icon(\n            "ClipCascade", self.create_clipboard_icon(), menu=self.create_menu()\n        )\n\n        self.icon.title = "ClipCascade"\n\n        self.update_stats()  # Start the stats update thread\n''',
        '''        # Create exactly one tray icon per process lifetime. Reconnect/restart paths\n        # must update this instance instead of constructing another pystray.Icon.\n        self._tray_icon_stopped = False\n        self._tray_icon_visible = False\n        self.icon = Icon(\n            "ClipCascade", self.create_clipboard_icon(), menu=self.create_menu()\n        )\n        self.icon.title = "ClipCascade"\n        self._replace_active_panel(self)\n        atexit.register(self._atexit_stop_icon)\n        self._log_tray_lifecycle("created", title=self.icon.title)\n\n        self.update_stats()  # Start the stats update thread\n''',
        "tray icon creation",
    )

    text = replace_required(
        text,
        "    def run(self):\n        self.icon.run()\n",
        "    def run(self):\n        self._tray_icon_visible = True\n        self._log_tray_lifecycle(\"run\")\n        self.icon.run()\n",
        "tray run",
    )

    text = replace_required(
        text,
        '''            if self.on_logoff_callback:\n                self.on_logoff_callback()\n            self.icon.stop()\n            self.root.quit()\n''',
        '''            if self.on_logoff_callback:\n                self.on_logoff_callback()\n            self._dispose_tray_icon("logoff")\n            self.root.quit()\n            try:\n                self.root.destroy()\n            except Exception:\n                pass\n''',
        "tray logoff shutdown",
    )

    text = replace_required(
        text,
        "    def _on_quit(self, icon, item):\n        self.icon.stop()\n        self.root.quit()\n",
        '''    def _on_quit(self, icon, item):\n        self._dispose_tray_icon("quit")\n        self.root.quit()\n        try:\n            self.root.destroy()\n        except Exception:\n            pass\n''',
        "tray quit shutdown",
    )

    path.write_text(text, encoding="utf-8")


def prepare_enhanced_tray() -> None:
    path = ROOT / "gui" / "enhanced_tray.py"
    text = path.read_text(encoding="utf-8")

    text = replace_required(
        text,
        '''    def run(self):\n        self.icon.run_detached()\n        self.root.after(0, self._show_status_on_ui_thread)\n        try:\n            self.root.mainloop()\n        finally:\n            self._stop_status_poll.set()\n            try:\n                self.icon.stop()\n            except Exception:\n                logging.exception("Failed to stop tray icon during shutdown")\n''',
        '''    def run(self):\n        self._tray_icon_visible = True\n        self._log_tray_lifecycle("run_detached")\n        self.icon.run_detached()\n        self.root.after(0, self._show_status_on_ui_thread)\n        try:\n            self.root.mainloop()\n        finally:\n            self._stop_status_poll.set()\n            self._dispose_tray_icon("enhanced_run_finally")\n''',
        "enhanced tray run",
    )

    text = replace_required(
        text,
        '''        try:\n            self.icon.stop()\n        except Exception:\n            logging.exception("Failed to stop tray icon")\n        try:\n            self.root.after(0, self._shutdown_ui)\n''',
        '''        self._dispose_tray_icon("request_shutdown")\n        try:\n            self.root.after(0, self._shutdown_ui)\n''',
        "enhanced tray shutdown",
    )

    path.write_text(text, encoding="utf-8")


def prepare_p2p_manager() -> None:
    path = ROOT / "p2p" / "p2p_manager.py"
    text = path.read_text(encoding="utf-8")

    text = replace_required(
        text,
        "import uuid\n\nfrom threading import Lock, Thread\n",
        "import uuid\nfrom urllib.parse import urlparse\n\nfrom threading import Lock, Thread\n",
        "p2p imports",
    )

    text = replace_required(
        text,
        '''        self.ws_client = None\n        self.is_connected = False\n''',
        '''        self.ws_client = None\n        self.last_transport_error = ""\n        self.last_transport_error_at = 0.0\n        self.is_connected = False\n''',
        "p2p error fields",
    )

    text = replace_required(
        text,
        '''    def connect(self) -> tuple[bool, str]:\n        """\n        Connects to the P2P websocket signaling server.\n        """\n        try:\n            if self.ws_client is not None:\n''',
        '''    def _current_websocket_url(self) -> str:\n        return str(self.config.data.get("websocket_url") or "").strip()\n\n    def _current_websocket_scheme(self) -> str:\n        return urlparse(self._current_websocket_url()).scheme.lower()\n\n    def get_connection_diagnostics(self) -> dict:\n        return {\n            "server_mode": self.config.data.get("server_mode"),\n            "server_url": self.config.data.get("server_url"),\n            "websocket_url": self._current_websocket_url(),\n            "websocket_scheme": self._current_websocket_scheme(),\n            "last_transport_error": self.last_transport_error,\n            "last_transport_error_at": self.last_transport_error_at,\n        }\n\n    def has_fatal_scheme_error(self) -> bool:\n        scheme = self._current_websocket_scheme()\n        if scheme and scheme not in ("ws", "wss"):\n            return True\n        lowered = self.last_transport_error.lower()\n        return "scheme" in lowered and "invalid" in lowered\n\n    def _remember_transport_error(self, error) -> None:\n        self.last_transport_error = str(error)\n        self.last_transport_error_at = time.time()\n\n    def connect(self) -> tuple[bool, str]:\n        """\n        Connects to the P2P websocket signaling server.\n        """\n        try:\n            websocket_url = self._current_websocket_url()\n            websocket_scheme = self._current_websocket_scheme()\n            logging.info(\n                "P2P signaling connect requested websocket_url=%s scheme=%s server_url=%s",\n                websocket_url,\n                websocket_scheme,\n                self.config.data.get("server_url"),\n            )\n            if websocket_scheme not in ("ws", "wss"):\n                msg = (\n                    "Invalid P2P websocket_url scheme: "\n                    f"websocket_url={websocket_url!r} scheme={websocket_scheme!r} "\n                    f"server_url={self.config.data.get('server_url')!r}"\n                )\n                self._remember_transport_error(msg)\n                logging.error(msg)\n                return False, msg\n\n            if self.ws_client is not None:\n''',
        "p2p diagnostics before connect",
    )

    text = replace_required(
        text,
        '''            self.ws_client = websocket.WebSocketApp(\n                url=self.config.data["websocket_url"],\n                header={"Cookie": RequestManager.format_cookie(self.config.data["cookie"])},\n''',
        '''            self.ws_client = websocket.WebSocketApp(\n                url=websocket_url,\n                header={"Cookie": RequestManager.format_cookie(self.config.data["cookie"])},\n''',
        "p2p websocket app url",
    )

    text = replace_required(
        text,
        '''            msg = f"Failed to connect websocket: {e}"\n            logging.error(msg)\n''',
        '''            msg = f"Failed to connect websocket: {e}"\n            self._remember_transport_error(msg)\n            logging.error(msg)\n''',
        "p2p connect error memory",
    )

    text = replace_required(
        text,
        '''    def _on_ws_close(self, ws, *args):\n        self.ws_client = None\n        self.is_connected = False\n''',
        '''    def _on_ws_close(self, ws, *args):\n        logging.info(\n            "P2P signaling closed websocket_url=%s scheme=%s args=%s",\n            self._current_websocket_url(),\n            self._current_websocket_scheme(),\n            args,\n        )\n        self.ws_client = None\n        self.is_connected = False\n''',
        "p2p close diagnostics",
    )

    text = replace_required(
        text,
        '''    def _on_ws_error(self, ws, error, *args):\n        logging.debug(error)\n''',
        '''    def _on_ws_error(self, ws, error, *args):\n        self._remember_transport_error(error)\n        logging.warning(\n            "P2P signaling error websocket_url=%s scheme=%s error=%s",\n            self._current_websocket_url(),\n            self._current_websocket_scheme(),\n            error,\n        )\n''',
        "p2p error diagnostics",
    )

    path.write_text(text, encoding="utf-8")


def prepare_windows_application() -> None:
    path = ROOT / "core" / "windows_application.py"
    text = path.read_text(encoding="utf-8")

    text = replace_required(
        text,
        '''        self._watchdog_thread = None\n        self._explicit_quit_requested = threading.Event()\n''',
        '''        self._watchdog_thread = None\n        self._explicit_quit_requested = threading.Event()\n        self._watchdog_restart_limit = 3\n        self._watchdog_long_backoff_seconds = 15 * 60\n''',
        "watchdog fields",
    )

    text = replace_required(
        text,
        '''            manager = self._get_ws_manager()\n            logging.info("Restarting synchronization engine")\n            self._stop_manager_for_restart(manager)\n''',
        '''            manager = self._get_ws_manager()\n            diagnostics = getattr(manager, "get_connection_diagnostics", lambda: {})()\n            logging.info(\n                "Restarting synchronization engine manager=%s diagnostics=%s",\n                type(manager).__name__,\n                diagnostics,\n            )\n            self._stop_manager_for_restart(manager)\n''',
        "restart diagnostics",
    )

    text = replace_required(
        text,
        '''        retry_delay = 10\n        next_attempt_at = 0.0\n\n        while not self._watchdog_stop.wait(2):\n''',
        '''        retry_delay = 10\n        next_attempt_at = 0.0\n        consecutive_full_restarts = 0\n\n        while not self._watchdog_stop.wait(2):\n''',
        "watchdog counters",
    )

    text = replace_required(
        text,
        '''                    unhealthy_warning_emitted = False\n                    retry_delay = 10\n                    continue\n                if self._manager_is_healthy(manager):\n                    unhealthy_since = None\n                    unhealthy_warning_emitted = False\n                    retry_delay = 10\n                    continue\n''',
        '''                    unhealthy_warning_emitted = False\n                    retry_delay = 10\n                    consecutive_full_restarts = 0\n                    continue\n                if self._manager_is_healthy(manager):\n                    unhealthy_since = None\n                    unhealthy_warning_emitted = False\n                    retry_delay = 10\n                    consecutive_full_restarts = 0\n                    continue\n''',
        "watchdog reset counters",
    )

    text = replace_required(
        text,
        '''                logging.warning(\n                    "Watchdog is performing a full synchronization restart"\n                )\n                try:\n                    self.restart_sync()\n                except Exception:\n                    logging.exception("Watchdog restart failed")\n                next_attempt_at = time.monotonic() + retry_delay\n                retry_delay = min(retry_delay * 2, 60)\n''',
        '''                fatal_scheme_error = bool(\n                    getattr(manager, "has_fatal_scheme_error", lambda: False)()\n                )\n                if fatal_scheme_error:\n                    diagnostics = getattr(manager, "get_connection_diagnostics", lambda: {})()\n                    logging.error(\n                        "Watchdog suppressed full restart after fatal websocket scheme error; diagnostics=%s",\n                        diagnostics,\n                    )\n                    next_attempt_at = time.monotonic() + self._watchdog_long_backoff_seconds\n                    continue\n\n                if consecutive_full_restarts >= self._watchdog_restart_limit:\n                    diagnostics = getattr(manager, "get_connection_diagnostics", lambda: {})()\n                    logging.error(\n                        "Watchdog reached full restart limit; backing off for %s seconds; diagnostics=%s",\n                        self._watchdog_long_backoff_seconds,\n                        diagnostics,\n                    )\n                    next_attempt_at = time.monotonic() + self._watchdog_long_backoff_seconds\n                    consecutive_full_restarts = 0\n                    continue\n\n                logging.warning(\n                    "Watchdog is performing a full synchronization restart"\n                )\n                try:\n                    self.restart_sync()\n                    consecutive_full_restarts += 1\n                except Exception:\n                    consecutive_full_restarts += 1\n                    logging.exception("Watchdog restart failed")\n                next_attempt_at = time.monotonic() + retry_delay\n                retry_delay = min(retry_delay * 2, 5 * 60)\n''',
        "watchdog bounded restart",
    )

    path.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    prepare_tray()
    prepare_enhanced_tray()
    prepare_p2p_manager()
    prepare_windows_application()
    print("Prepared Windows tray lifecycle, bounded watchdog restart, and P2P scheme diagnostics.")
