import pathlib
import unittest


class WindowsGuiContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = (
            pathlib.Path(__file__).resolve().parents[1] / "src" / "gui" / "tray.py"
        ).read_text(encoding="utf-8")

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
