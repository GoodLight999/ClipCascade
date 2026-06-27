import json
import unittest
from types import SimpleNamespace

from p2p.p2p_manager import P2PManager


class FakeChannel:
    def __init__(self, ready_state="open"):
        self.readyState = ready_state
        self.sent = []

    def send(self, message):
        self.sent.append(json.loads(message))


class FakeClipboardManager:
    def __init__(self, changed=True, valid=True):
        self.changed = changed
        self.valid = valid
        self.previous_clipboard_hash = 101
        self.pasted = []
        self.base64_calls = []

    def has_clipboard_changed(self, payload):
        previous = self.previous_clipboard_hash
        if self.changed:
            self.previous_clipboard_hash = 202
            return True
        self.previous_clipboard_hash = previous
        return False

    def is_clipboard_size_within_limit(self, payload, payload_type):
        return self.valid

    def paste(self, payload, payload_type):
        self.pasted.append((payload, payload_type))

    def base64_to_clipboard(self, base64_string, type_):
        self.base64_calls.append((base64_string, type_))


class P2PPeerAckTests(unittest.TestCase):
    def make_manager(self, *, changed=True, valid=True):
        manager = P2PManager.__new__(P2PManager)
        manager.config = SimpleNamespace(
            data={
                "max_clipboard_size_local_limit_bytes": 1024 * 1024,
                "cipher_enabled": False,
            }
        )
        manager.clipboard_manager = FakeClipboardManager(
            changed=changed,
            valid=valid,
        )
        manager.receiving_fragments = {}
        manager.receiving_fragment_stats = None
        manager.sending_fragment_id = ""
        manager.sending_fragment_stats = None
        manager.reset_sending_fragment_id = lambda: setattr(
            manager,
            "sending_fragment_id",
            "",
        )
        manager.reset_receiving_fragments = lambda: setattr(
            manager,
            "receiving_fragments",
            {},
        )
        return manager

    @staticmethod
    def message(*, relay_id="relay-1", ack_requested=True, payload="hello"):
        return json.dumps(
            {
                "payload": payload,
                "type": "text",
                "metadata": {
                    "id": "message-1",
                    "isFragmented": False,
                    "index": 0,
                    "totalFragments": 1,
                    "combinedRawPayloadSizeInBytes": len(payload.encode("utf-8")),
                    "relayId": relay_id,
                    "ackRequested": ack_requested,
                },
            }
        )

    def test_ack_sent_after_text_is_applied(self):
        manager = self.make_manager(changed=True, valid=True)
        channel = FakeChannel()

        manager._receive(self.message(), channel)

        self.assertEqual([("hello", "text")], manager.clipboard_manager.pasted)
        self.assertEqual(
            [{"_cc_ack": {"relayId": "relay-1"}}],
            channel.sent,
        )

    def test_validation_rejection_restores_hash_and_sends_no_ack(self):
        manager = self.make_manager(changed=True, valid=False)
        channel = FakeChannel()
        original_hash = manager.clipboard_manager.previous_clipboard_hash

        manager._receive(self.message(), channel)

        self.assertEqual([], manager.clipboard_manager.pasted)
        self.assertEqual([], channel.sent)
        self.assertEqual(
            original_hash,
            manager.clipboard_manager.previous_clipboard_hash,
        )

    def test_duplicate_text_is_acknowledged_without_reapplying(self):
        manager = self.make_manager(changed=False, valid=True)
        channel = FakeChannel()

        manager._receive(self.message(), channel)

        self.assertEqual([], manager.clipboard_manager.pasted)
        self.assertEqual(
            [{"_cc_ack": {"relayId": "relay-1"}}],
            channel.sent,
        )

    def test_old_message_is_applied_without_ack(self):
        manager = self.make_manager(changed=True, valid=True)
        channel = FakeChannel()

        manager._receive(self.message(ack_requested=False), channel)

        self.assertEqual([("hello", "text")], manager.clipboard_manager.pasted)
        self.assertEqual([], channel.sent)

    def test_ack_control_envelope_is_not_treated_as_clipboard_data(self):
        manager = self.make_manager(changed=True, valid=True)
        channel = FakeChannel()

        manager._receive(
            json.dumps({"_cc_ack": {"relayId": "relay-1"}}),
            channel,
        )

        self.assertEqual([], manager.clipboard_manager.pasted)
        self.assertEqual([], manager.clipboard_manager.base64_calls)
        self.assertEqual([], channel.sent)


if __name__ == "__main__":
    unittest.main()
