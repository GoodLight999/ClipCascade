import unittest
from types import SimpleNamespace
from unittest.mock import Mock

import requests

from utils.request_manager import RequestManager, ServerResponseError


def make_response(
    body: str,
    *,
    status: int = 200,
    content_type: str = "application/json",
    url: str = "https://example.test/server-mode",
):
    response = requests.Response()
    response.status_code = status
    response._content = body.encode("utf-8")
    response.encoding = "utf-8"
    response.headers["Content-Type"] = content_type
    response.url = url
    response.history = []
    return response


class RequestManagerResponseTests(unittest.TestCase):
    def make_manager(self, cookie=None):
        config = SimpleNamespace(
            data={
                "server_url": "https://example.test",
                "cookie": cookie,
                "ssl_ca_bundle": "",
            }
        )
        return RequestManager(config)

    def test_format_cookie_preserves_all_session_cookies(self):
        rendered = RequestManager.format_cookie(
            {
                "JSESSIONID": "primary",
                "ROUTE": "secondary",
            }
        )
        self.assertEqual(rendered, "JSESSIONID=primary; ROUTE=secondary")

    def test_parse_json_object_accepts_object(self):
        response = make_response('{"mode":"P2P"}')
        self.assertEqual(
            RequestManager._parse_json_object(response, "/server-mode"),
            {"mode": "P2P"},
        )

    def test_parse_json_object_rejects_empty_body_with_safe_metadata(self):
        response = make_response("")
        with self.assertRaises(ServerResponseError) as raised:
            RequestManager._parse_json_object(response, "/server-mode")
        message = str(raised.exception)
        self.assertIn("empty response", message)
        self.assertIn("body_bytes=0", message)
        self.assertIn("final_path=/server-mode", message)

    def test_parse_json_object_rejects_html_without_logging_body(self):
        secret = "PRIVATE_RESPONSE_CONTENT"
        response = make_response(
            f"<html><body>{secret}</body></html>",
            content_type="text/html; charset=utf-8",
            url="https://example.test/login",
        )
        with self.assertRaises(ServerResponseError) as raised:
            RequestManager._parse_json_object(response, "/server-mode")
        message = str(raised.exception)
        self.assertIn("HTML instead of JSON", message)
        self.assertIn("final_path=/login", message)
        self.assertNotIn(secret, message)

    def test_get_server_mode_uses_saved_additional_cookies(self):
        manager = self.make_manager(
            {
                "JSESSIONID": "primary",
                "ROUTE": "secondary",
            }
        )
        manager.session.get = Mock(return_value=make_response('{"mode":"P2P"}'))

        self.assertEqual(manager.get_server_mode(), "P2P")
        self.assertEqual(manager.session.cookies.get("JSESSIONID"), "primary")
        self.assertEqual(manager.session.cookies.get("ROUTE"), "secondary")
        manager.session.get.assert_called_once()
        _, kwargs = manager.session.get.call_args
        self.assertEqual(kwargs["timeout"], RequestManager.REQUEST_TIMEOUT)

    def test_get_server_mode_rejects_unknown_mode(self):
        manager = self.make_manager()
        manager.session.get = Mock(return_value=make_response('{"mode":"UNKNOWN"}'))
        with self.assertRaises(ServerResponseError):
            manager.get_server_mode()


if __name__ == "__main__":
    unittest.main()
