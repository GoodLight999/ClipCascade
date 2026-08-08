import pathlib
import unittest


class WindowsGuiContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        test_file = pathlib.Path(__file__).resolve()
        candidates = (
            test_file.parents[1] / "src" / "gui" / "tray.py",
            test_file.parents[1] / "gui" / "tray.py",
        )
        source_path = next((path for path in candidates if path.is_file()), None)
        if source_path is None:
            raise FileNotFoundError(
                "GUI source not found in repository or packaged layout: "
                + ", ".join(str(path) for path in candidates)
            )
        cls.source = source_path.read_text(encoding="utf-8")

    def test_desktop_has_visible_tk_window(self):
        self.assertIn('self.root.title("ClipCascade")', self.source)
        self.assertIn('self.root.deiconify()', self.source)
        self.assertIn('self.root.mainloop()', self.source)

    def test_tray_and_gui_event_loops_are_integrated(self):
        self.assertIn('self.icon.run_detached()', self.source)
        self.assertIn('Open ClipCascade', self.source)
        self.assertIn('self._show_window', self.source)

    def test_closing_window_hides_to_tray(self):
        self.assertIn('self.root.protocol("WM_DELETE_WINDOW", self._hide_window)', self.source)
        self.assertIn('self.root.withdraw()', self.source)


if __name__ == "__main__":
    unittest.main()
