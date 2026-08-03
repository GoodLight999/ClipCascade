#!/usr/bin/env python3

# ClipCascade - A seamless clipboard syncing utility
# Recovery repository: https://github.com/GoodLight999/Trial-and-Error-ClipCascade
# Original project and author: Sathvik Rao Poladi
# License: GPL-3.0
#
# This script serves as the entry point for the ClipCascade application,
# initializing and running the core application logic.

from core.application import Application


class Main:
    def __init__(self):
        Application().run()

def main():
    Main()

if __name__ == "__main__":
    main()
