#!/usr/bin/env python3

# ClipCascade - A seamless clipboard syncing utility
# Repository: https://github.com/Sathvik-Rao/ClipCascade
#
# Author: Sathvik Rao Poladi
# License: GPL-3.0

from core.constants import PLATFORM, WINDOWS

if PLATFORM == WINDOWS:
    from core.windows_application import WindowsApplication as Application
else:
    from core.application import Application


class Main:
    def __init__(self):
        Application().run()


def main():
    Main()


if __name__ == "__main__":
    main()
