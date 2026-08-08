import logging
import math
import os
import threading
import time
import tkinter as tk
from tkinter import filedialog, ttk
import webbrowser

from PIL import Image, ImageDraw
from pystray import Icon, MenuItem as item, Menu

from connection.tray_view import TrayPrimaryAction, derive_tray_connection_view
from core.config import Config
from core.constants import *
from gui.info import CustomDialog

if PLATFORM != WINDOWS:
    import subprocess


class TaskbarPanel:
    def __init__(
        self,
        on_connect_callback: callable = None,
        on_disconnect_callback: callable = None,
        on_logoff_callback: callable = None,
        new_version_available: list = None,
        github_url: str = GITHUB_URL,
        donation_url: str = None,
        ws_interface=None,
        config: Config = None,
    ):
        self.on_connect_callback = on_connect_callback
        self.on_disconnect_callback = on_disconnect_callback
        self.on_logoff_callback = on_logoff_callback
        self.new_version_available = new_version_available
        self.github_url = github_url
        self.donation_url = donation_url
        self.ws_interface = ws_interface
        self.config = config

        self.is_disconnecting = False
        self.disconnecting_items = None
        self.is_file_download_enabled = False
        self.file_download_items = None
        self.previous_stats: str = ""
        self.previous_stats_items = None
        self._closing = False

        self.root = tk.Tk()
        self.root.title("ClipCascade")
        self.root.geometry("560x340")
        self.root.minsize(500, 300)
        self.root.protocol("WM_DELETE_WINDOW", self._hide_window)

        if PLATFORM == MACOS:
            try:
                from AppKit import NSApplication, NSApplicationActivationPolicyAccessory

                NSApplication.sharedApplication().setActivationPolicy_(
                    NSApplicationActivationPolicyAccessory
                )
            except ImportError:
                pass

        self.is_connected = bool(getattr(self.ws_interface, "is_connected", False))
        self._build_window()

        self.icon = Icon(
            "ClipCascade",
            self.create_clipboard_icon(),
            menu=self.create_menu(),
        )
        self.icon.title = "ClipCascade"
        self.update_stats()
        self._schedule_window_refresh()

    def run(self):
        # pystray's detached mode keeps the native tray pump alive while Tk owns
        # the GUI main loop. Windows users therefore receive a real status
        # window instead of an invisible tray-only process.
        try:
            self.icon.run_detached()
        except (AttributeError, NotImplementedError):
            threading.Thread(target=self.icon.run, daemon=True).start()
        self.root.deiconify()
        self.root.lift()
        self.root.mainloop()

    def _build_window(self):
        outer = ttk.Frame(self.root, padding=20)
        outer.pack(fill=tk.BOTH, expand=True)

        ttk.Label(
            outer,
            text="ClipCascade",
            font=("Segoe UI", 20, "bold"),
        ).pack(anchor=tk.W)
        ttk.Label(
            outer,
            text="Clipboard synchronization status",
        ).pack(anchor=tk.W, pady=(0, 18))

        self.window_status_var = tk.StringVar(value="Starting…")
        self.window_detail_var = tk.StringVar(value="")
        self.window_server_var = tk.StringVar(value=self._server_summary())

        status_frame = ttk.LabelFrame(outer, text="Connection", padding=12)
        status_frame.pack(fill=tk.X)
        ttk.Label(
            status_frame,
            textvariable=self.window_status_var,
            font=("Segoe UI", 12, "bold"),
        ).pack(anchor=tk.W)
        ttk.Label(
            status_frame,
            textvariable=self.window_detail_var,
            wraplength=490,
        ).pack(anchor=tk.W, pady=(6, 0))
        ttk.Label(
            status_frame,
            textvariable=self.window_server_var,
            wraplength=490,
        ).pack(anchor=tk.W, pady=(6, 0))

        controls = ttk.Frame(outer)
        controls.pack(fill=tk.X, pady=(18, 0))
        ttk.Button(
            controls,
            text="Connect / Reconnect",
            command=lambda: self._on_connect(None, None),
        ).pack(side=tk.LEFT)
        ttk.Button(
            controls,
            text="Disconnect",
            command=lambda: self._on_disconnect(None, None),
        ).pack(side=tk.LEFT, padx=(8, 0))
        ttk.Button(
            controls,
            text="Open logs",
            command=lambda: self._open_logs(None, None),
        ).pack(side=tk.LEFT, padx=(8, 0))

        secondary = ttk.Frame(outer)
        secondary.pack(fill=tk.X, pady=(10, 0))
        ttk.Button(
            secondary,
            text="Program files",
            command=lambda: self._open_program_location(None, None),
        ).pack(side=tk.LEFT)
        ttk.Button(
            secondary,
            text="Hide to tray",
            command=self._hide_window,
        ).pack(side=tk.RIGHT)

        ttk.Label(
            outer,
            text="Closing this window keeps ClipCascade running in the task tray.",
        ).pack(anchor=tk.W, pady=(18, 0))

    def _server_summary(self):
        if self.config is None:
            return ""
        data = getattr(self.config, "data", {}) or {}
        server_mode = data.get("server_mode") or "unknown mode"
        server_url = data.get("server_url") or "server not configured"
        return f"{server_mode} · {server_url}"

    def _schedule_window_refresh(self):
        if self._closing:
            return
        self._refresh_window()
        self.root.after(1000, self._schedule_window_refresh)

    def _refresh_window(self):
        try:
            view = self._connection_view()
            self.window_status_var.set(view.label)
            details = ""
            if self.ws_interface is not None:
                details = self.ws_interface.get_stats() or ""
            self.window_detail_var.set(details)
            self.window_server_var.set(self._server_summary())
        except Exception as error:
            logging.error(f"Failed to refresh GUI status: {error}")
            self.window_status_var.set("Status unavailable")
            self.window_detail_var.set(str(error))

    def _show_window(self, icon=None, item_=None):
        def show():
            self.root.deiconify()
            self.root.lift()
            try:
                self.root.focus_force()
            except tk.TclError:
                pass

        self.root.after(0, show)

    def _hide_window(self):
        if not self._closing:
            self.root.withdraw()

    def _shutdown_window(self):
        self._closing = True
        try:
            self.root.after(0, self.root.destroy)
        except tk.TclError:
            pass

    def _connection_view(self):
        return derive_tray_connection_view(
            self.ws_interface,
            legacy_is_connected=self.is_connected,
        )

    def _create_clipboard_base_image(self):
        width, height = 64, 64
        image = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        draw = ImageDraw.Draw(image)

        fill_color = (220, 220, 220)
        outline_color = (255, 255, 255)

        board_coords = (12, 12, 52, 57)
        try:
            draw.rounded_rectangle(
                board_coords,
                radius=5,
                fill=None,
                outline=outline_color,
                width=3,
            )
        except (AttributeError, TypeError):
            draw.rectangle(board_coords, fill=None, outline=outline_color)

        clip_coords = (22, 7, 42, 17)
        try:
            draw.rounded_rectangle(
                clip_coords,
                radius=3,
                fill=fill_color,
                outline=outline_color,
                width=3,
            )
        except (AttributeError, TypeError):
            draw.rectangle(clip_coords, fill=fill_color, outline=outline_color)

        return image

    def create_clipboard_icon(self):
        return self._create_clipboard_base_image()

    def create_clipboard_icon_with_dot(self):
        image = self._create_clipboard_base_image().copy()
        draw = ImageDraw.Draw(image)
        width, height = 64, 64

        dot_radius = 8
        dot_center_x = width - dot_radius - 5
        dot_center_y = dot_radius + 5
        dot_bbox = [
            dot_center_x - dot_radius,
            dot_center_y - dot_radius,
            dot_center_x + dot_radius,
            dot_center_y + dot_radius,
        ]
        draw.ellipse(dot_bbox, fill=(0, 128, 255, 255))

        highlight_radius = dot_radius // 2
        highlight_center_x = dot_center_x - highlight_radius // 2
        highlight_center_y = dot_center_y - highlight_radius // 2
        highlight_bbox = [
            highlight_center_x - highlight_radius,
            highlight_center_y - highlight_radius,
            highlight_center_x + highlight_radius,
            highlight_center_y + highlight_radius,
        ]
        draw.ellipse(highlight_bbox, fill=(255, 255, 255, 180))
        return image

    def create_menu(self, item_: tuple = None):
        menu_items = [
            item("🪟 Open ClipCascade", self._show_window, default=True),
            Menu.SEPARATOR,
            item("🗒️ Open Logs", self._open_logs),
            item("📂 Program Files", self._open_program_location),
            Menu.SEPARATOR,
            item("🏠 Homepage", self._open_homepage),
            item("❓ Help", self._open_help),
            item("💟 Donate", self._open_donate),
            item("🌐 GitHub", self._open_github),
            Menu.SEPARATOR,
            item("🔒 Logoff and Quit", self._on_logoff),
            item("❌ Quit", self._on_quit),
        ]

        connection_view = self._connection_view()
        if (
            not connection_view.authoritative
            and self.is_disconnecting
            and self.disconnecting_items is not None
        ):
            menu_items.insert(
                1,
                item(self.disconnecting_items[0], self.disconnecting_items[2]),
            )
        else:
            if connection_view.action in (
                TrayPrimaryAction.CONNECT,
                TrayPrimaryAction.RECONNECT,
            ):
                callback = self._on_connect
            elif connection_view.action is TrayPrimaryAction.DISCONNECT:
                callback = self._on_disconnect
            else:
                callback = None
            menu_items.insert(
                1,
                item(
                    connection_view.label,
                    callback,
                    enabled=connection_view.enabled,
                ),
            )

        if self.new_version_available is not None and self.new_version_available[0]:
            menu_items.insert(
                len(menu_items) - 3,
                item(
                    f"🔄 Update ({self.new_version_available[2]} ➞ {self.new_version_available[1]})",
                    self._on_update,
                ),
            )

        if self.is_file_download_enabled and self.file_download_items is not None:
            menu_items.insert(
                self.file_download_items[1] + 1,
                item(
                    self.file_download_items[0],
                    self.file_download_items[2],
                    default=False,
                ),
            )

        if self.previous_stats_items is not None:
            menu_items.insert(
                2,
                item(
                    self.previous_stats_items[0],
                    self.previous_stats_items[2],
                    enabled=self.previous_stats_items[2] is not None,
                ),
            )

        return Menu(*menu_items)

    def update_menu(self, item_: tuple = None):
        self.icon.menu = self.create_menu(item_=item_)

    def update_stats(self):
        threading.Thread(target=self._update_stats_thread, daemon=True).start()

    def _update_stats_thread(self):
        while not self._closing:
            try:
                current_stats = self.ws_interface.get_stats()
                if current_stats is not None and self.previous_stats != current_stats:
                    self.previous_stats = current_stats
                    self.previous_stats_items = (current_stats, 0, None)
                    self.update_menu()
            except Exception as error:
                logging.error(f"Failed to update tray connection status: {error}")
            time.sleep(1)

    @staticmethod
    def open_webbrowser(url):
        try:
            webbrowser.open(url)
        except Exception as e:
            CustomDialog(
                f"Failed to open the browser. Here is the URL: {url}\nError: {e}",
                msg_type="error",
            ).mainloop()

    def _on_update(self, icon, item_):
        TaskbarPanel.open_webbrowser(self.new_version_available[3])

    def _on_connect(self, icon, item_):
        connection_view = self._connection_view()
        if self.on_connect_callback:
            try:
                self.on_connect_callback()
            except Exception as error:
                logging.error(f"Manual connection request failed: {error}")
        if not connection_view.authoritative:
            self.is_connected = True
        self.update_menu()
        self._refresh_window()

    def _on_disconnect(self, icon, item_):
        connection_view = self._connection_view()
        if not self.on_disconnect_callback:
            return

        try:
            self.on_disconnect_callback()
        except Exception as error:
            logging.error(f"Manual disconnect request failed: {error}")
            return

        if connection_view.authoritative:
            self.update_menu()
            self._refresh_window()
            return

        if self.ws_interface is not None and self.ws_interface.is_auto_reconnecting:
            threading.Thread(
                target=self._wait_to_disconnect,
                args=(icon, item_),
                daemon=True,
            ).start()
        else:
            self.is_connected = False
            self.update_menu()
            self._refresh_window()

    def _wait_to_disconnect(self, icon, item_):
        if self.ws_interface is None:
            return

        timeout = math.ceil(self.ws_interface.get_total_timeout() / 1000)
        while timeout > 0:
            self.is_disconnecting = True
            self.disconnecting_items = (
                f"⏳ Disconnecting... ({timeout} sec)",
                0,
                None,
            )
            self.update_menu()
            time.sleep(1)
            timeout -= 1
        self.is_disconnecting = False
        self.disconnecting_items = None
        self.ws_interface.is_auto_reconnecting = False
        self.is_connected = False
        self.update_menu()
        self.root.after(0, self._refresh_window)

    def _open_homepage(self, icon, item_):
        TaskbarPanel.open_webbrowser(self.config.data["server_url"])

    def _open_github(self, icon, item_):
        TaskbarPanel.open_webbrowser(self.github_url)

    def _open_help(self, icon, item_):
        TaskbarPanel.open_webbrowser(HELP_URL)

    def _open_donate(self, icon, item_):
        if self.donation_url is not None:
            TaskbarPanel.open_webbrowser(self.donation_url)

    def open_location(self, path):
        if PLATFORM == WINDOWS:
            os.startfile(path)
        elif PLATFORM == MACOS:
            subprocess.run(["open", path])
        elif PLATFORM.startswith(LINUX):
            subprocess.run(["xdg-open", path])

    def _open_logs(self, icon, item_):
        log_file_path = os.path.join(get_program_files_directory(), LOG_FILE_NAME)
        if os.path.exists(log_file_path):
            try:
                self.open_location(log_file_path)
            except Exception as e:
                CustomDialog(
                    f"Failed to open the log file '{log_file_path}'.\nError: {e}",
                    msg_type="error",
                ).mainloop()
        else:
            CustomDialog(
                f"Log file not found at '{log_file_path}'.",
                msg_type="error",
            ).mainloop()

    def _open_program_location(self, icon, item_):
        program_location = get_program_files_directory()
        try:
            self.open_location(program_location)
        except Exception as e:
            CustomDialog(
                f"Failed to open the program location '{program_location}'.\nError: {e}",
                msg_type="error",
            ).mainloop()

    def enable_files_download(self, files):
        self.is_file_download_enabled = True
        self.icon.icon = self.create_clipboard_icon_with_dot()
        self.file_download_items = (
            "📥 Download File(s)",
            0,
            lambda icon, item_: self._on_download(icon, item_, files),
        )
        self.update_menu()

    def disable_files_download(self):
        self.is_file_download_enabled = False
        self.file_download_items = None
        self.icon.icon = self.create_clipboard_icon()
        self.update_menu()

    def _on_download(self, icon, item_, files):
        try:
            try:
                if self.config.data["default_file_download_location"] != "":
                    target_directory = self.config.data[
                        "default_file_download_location"
                    ]
                else:
                    target_directory = filedialog.askdirectory(
                        title="Select location to Save File(s)",
                        initialdir=get_downloads_folder(),
                    )
                if not target_directory:
                    logging.debug("No directory selected. Exiting.")
                    return
            except RuntimeError as re:
                target_directory = os.path.join(
                    get_program_files_directory(),
                    "downloads",
                )
                if not os.path.exists(target_directory):
                    os.makedirs(target_directory)
                logging.error(
                    "A runtime error occurred while starting the directory picker. "
                    f"Error: {re}. Using '{target_directory}'."
                )
                CustomDialog(
                    f"ClipCascade 📥: Saving files to '{target_directory}'.",
                    msg_type="info",
                    timeout=5000,
                ).mainloop()

            for filename, file_obj in files.items():
                file_path = os.path.join(target_directory, filename)
                with open(file_path, "wb") as file_handle:
                    file_handle.write(file_obj.getvalue())
                logging.debug(f"Saved: {file_path}")

        except Exception as e:
            msg = f"An error occurred while downloading files. Error: {e}"
            logging.error(msg)
            CustomDialog(msg, msg_type="error").mainloop()

    def _on_logoff(self, icon, item_):
        try:
            if self.on_logoff_callback:
                self.on_logoff_callback()
            self.icon.stop()
            self._shutdown_window()
        except Exception as e:
            CustomDialog(
                f"An error occurred while logging off: {e}",
                msg_type="error",
            ).mainloop()

    def _on_quit(self, icon, item_):
        self.icon.stop()
        self._shutdown_window()
