from pathlib import Path


SOURCE = Path(__file__).resolve().parents[1] / "p2p" / "p2p_manager.py"


def replace_exact(source: str, before: str, after: str, label: str) -> str:
    if before not in source:
        raise RuntimeError(f"Unable to apply {label}: expected source text was not found")
    return source.replace(before, after, 1)


def main() -> None:
    source = SOURCE.read_text(encoding="utf-8")

    source = replace_exact(
        source,
        '        def on_message(message):\n            self._receive(message)\n',
        '        def on_message(message):\n            self._receive(message, channel)\n',
        "reply channel propagation",
    )

    source = replace_exact(
        source,
        '    def _receive(self, frame: any) -> str:\n',
        '    def _receive(self, frame: any, reply_channel=None) -> str:\n',
        "P2P receive signature",
    )

    source = replace_exact(
        source,
        '            if isinstance(body, dict) and body.get("_cc_keepalive") is True:\n'
        '                return\n',
        '            if isinstance(body, dict) and body.get("_cc_keepalive") is True:\n'
        '                return\n'
        '            if isinstance(body, dict) and body.get("_cc_ack") is not None:\n'
        '                # Windows currently has no persistent outbound relay queue.\n'
        '                # Ignore peer ACK envelopes without treating them as clipboard data.\n'
        '                return\n',
        "P2P acknowledgement envelope handling",
    )

    source = replace_exact(
        source,
        '            if self.clipboard_manager.has_clipboard_changed(payload):\n'
        '                self.reset_receiving_fragments()\n'
        '                self.clipboard_manager.base64_to_clipboard(\n'
        '                    base64_string=payload, type_=payload_type\n'
        '                )\n',
        '            changed = self.clipboard_manager.has_clipboard_changed(payload)\n'
        '            clipboard_applied = not changed and payload_type == "text"\n'
        '            if changed:\n'
        '                self.reset_receiving_fragments()\n'
        '                if payload_type == "text":\n'
        '                    if self.clipboard_manager.is_clipboard_size_within_limit(\n'
        '                        payload, payload_type\n'
        '                    ):\n'
        '                        self.clipboard_manager.paste(payload, payload_type)\n'
        '                        clipboard_applied = True\n'
        '                    else:\n'
        '                        self.clipboard_manager.previous_clipboard_hash = 0\n'
        '                else:\n'
        '                    self.clipboard_manager.base64_to_clipboard(\n'
        '                        base64_string=payload, type_=payload_type\n'
        '                    )\n'
        '\n'
        '            if (\n'
        '                clipboard_applied\n'
        '                and isinstance(metadata, dict)\n'
        '                and metadata.get("ackRequested") is True\n'
        '                and metadata.get("relayId")\n'
        '                and reply_channel is not None\n'
        '                and getattr(reply_channel, "readyState", "") == "open"\n'
        '            ):\n'
        '                reply_channel.send(\n'
        '                    json.dumps(\n'
        '                        {"_cc_ack": {"relayId": str(metadata["relayId"])}}\n'
        '                    )\n'
        '                )\n',
        "clipboard-applied P2P acknowledgement",
    )

    SOURCE.write_text(source, encoding="utf-8")
    print("Prepared Windows P2P clipboard-applied acknowledgement.")


if __name__ == "__main__":
    main()
