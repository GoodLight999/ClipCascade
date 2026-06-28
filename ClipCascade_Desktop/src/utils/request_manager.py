import json
import logging
from urllib.parse import urlsplit

import requests
from bs4 import BeautifulSoup

from core.constants import *
from core.config import Config
from utils.ssl_helper import requests_verify_arg


class ServerResponseError(RuntimeError):
    """Raised when a ClipCascade HTTP endpoint returns an unusable response."""


class RequestManager:
    REQUEST_TIMEOUT = (10, 30)

    def __init__(self, config: Config):
        self.config = config
        # Keep one requests.Session for the complete authentication flow. The
        # previous implementation copied only JSESSIONID into later requests,
        # which discarded any additional cookies set by the server or proxy.
        self.session = requests.Session()

    def _verify(self):
        return requests_verify_arg(self.config)

    def reset_session(self):
        self.session.close()
        self.session = requests.Session()

    @staticmethod
    def format_cookie(cookie: dict) -> str:
        """Format every stored cookie without logging cookie values."""
        if not isinstance(cookie, dict):
            return ""
        return "; ".join(
            f"{str(name)}={str(value)}"
            for name, value in cookie.items()
            if name and value is not None
        )

    def _load_saved_cookies_if_needed(self):
        if len(self.session.cookies) > 0:
            return
        saved = self.config.data.get("cookie")
        if isinstance(saved, dict):
            self.session.cookies.update(saved)

    @staticmethod
    def _response_summary(response: requests.Response, endpoint: str) -> str:
        content_type = response.headers.get("Content-Type", "missing")
        content_type = content_type.split(";", 1)[0].strip() or "missing"
        body_bytes = len(response.content or b"")
        final_path = urlsplit(response.url or endpoint).path or "/"
        redirect_codes = [str(item.status_code) for item in response.history]
        redirects = "->".join(redirect_codes) if redirect_codes else "none"
        return (
            f"endpoint={endpoint}, status={response.status_code}, "
            f"content_type={content_type}, body_bytes={body_bytes}, "
            f"final_path={final_path}, redirects={redirects}"
        )

    @classmethod
    def _parse_json_object(
        cls,
        response: requests.Response,
        endpoint: str,
    ) -> dict:
        summary = cls._response_summary(response, endpoint)
        try:
            response.raise_for_status()
        except requests.RequestException as error:
            raise ServerResponseError(f"HTTP request failed ({summary})") from error

        if not response.content:
            raise ServerResponseError(f"Server returned an empty response ({summary})")

        try:
            payload = response.json()
        except (ValueError, requests.exceptions.JSONDecodeError) as error:
            content_type = response.headers.get("Content-Type", "").lower()
            body_prefix = response.text.lstrip()[:32].lower()
            response_kind = (
                "HTML"
                if "text/html" in content_type
                or body_prefix.startswith("<!doctype html")
                or body_prefix.startswith("<html")
                else "non-JSON"
            )
            raise ServerResponseError(
                f"Server returned {response_kind} instead of JSON ({summary})"
            ) from error

        if not isinstance(payload, dict):
            raise ServerResponseError(
                f"Server returned a JSON value that is not an object ({summary})"
            )
        return payload

    def _authenticated_get(self, endpoint: str) -> requests.Response:
        self._load_saved_cookies_if_needed()
        return self.session.get(
            self.config.data["server_url"] + endpoint,
            verify=self._verify(),
            timeout=self.REQUEST_TIMEOUT,
        )

    def login(self) -> tuple[bool, str, dict]:
        try:
            self.reset_session()

            response = self.session.get(
                self.config.data["server_url"] + LOGIN_URL,
                verify=self._verify(),
                timeout=self.REQUEST_TIMEOUT,
            )
            response.raise_for_status()

            soup = BeautifulSoup(response.text, "html.parser")
            csrf_input = soup.find("input", {"name": "_csrf"})
            if csrf_input is None or not csrf_input.get("value"):
                summary = self._response_summary(response, LOGIN_URL)
                msg = f"Login page did not contain a CSRF token ({summary})"
                logging.error(msg)
                return False, msg, None

            form_data = {
                "username": self.config.data["username"],
                "password": self.config.data["password"],
                "_csrf": csrf_input["value"],
            }
            response = self.session.post(
                self.config.data["server_url"] + LOGIN_URL,
                data=form_data,
                verify=self._verify(),
                timeout=self.REQUEST_TIMEOUT,
            )
            response.raise_for_status()

            response_text_lower = response.text.lower()
            post_soup = BeautifulSoup(response.text, "html.parser")
            still_has_login_form = (
                post_soup.find("input", {"name": "username"}) is not None
                and post_soup.find("input", {"name": "_csrf"}) is not None
            )
            if "bad credentials" in response_text_lower or still_has_login_form:
                summary = self._response_summary(response, LOGIN_URL)
                msg = f"Login failed or returned to the login form ({summary})"
                logging.error(msg)
                return False, msg, None

            cookie = self.session.cookies.get_dict()
            if not cookie:
                summary = self._response_summary(response, LOGIN_URL)
                msg = f"Login response did not establish a session ({summary})"
                logging.error(msg)
                return False, msg, None

            # Authentication is only considered fully validated after an
            # authenticated JSON endpoint succeeds in Application.
            logging.info("Login request accepted: %s; validating session", response.status_code)
            return True, "Login request accepted", cookie
        except Exception as error:
            msg = f"An error occurred during login: {error}"
            logging.error(msg)
            return False, msg, None

    def maxsize(self) -> int:
        try:
            response = self._authenticated_get(MAXSIZE_URL)
            payload = self._parse_json_object(response, MAXSIZE_URL)
            maxsize = int(payload.get("maxsize", MAX_SIZE))
            logging.info("Max size: %s", maxsize)
            return maxsize
        except Exception as error:
            logging.error(
                "Error fetching max size: %s; defaulting to %s Bytes",
                error,
                MAX_SIZE,
            )
            return MAX_SIZE

    def get_server_mode(self) -> str:
        try:
            response = self._authenticated_get(SERVER_MODE_URL)
            payload = self._parse_json_object(response, SERVER_MODE_URL)
            server_mode = str(payload.get("mode", "")).upper()
            if server_mode not in {"P2S", "P2P"}:
                summary = self._response_summary(response, SERVER_MODE_URL)
                raise ServerResponseError(
                    f"Server returned an unsupported or missing mode ({summary})"
                )
            logging.info("Server mode: %s", server_mode)
            return server_mode
        except Exception as error:
            logging.error("Error fetching server mode: %s", error)
            raise

    def get_stun_url(self) -> str:
        try:
            response = self._authenticated_get(STUN_URL)
            payload = self._parse_json_object(response, STUN_URL)
            stun_url = payload.get("url")
            if not isinstance(stun_url, str) or not stun_url.strip():
                summary = self._response_summary(response, STUN_URL)
                raise ServerResponseError(
                    f"Server returned a missing STUN URL ({summary})"
                )
            logging.info("STUN URL received")
            return stun_url
        except Exception as error:
            logging.error("Error fetching STUN URL: %s", error)
            raise

    def get_metadata(self) -> dict:
        try:
            response = RequestManager.get(
                url=METADATA_URL,
                verify=True,
            )
            return self._parse_json_object(response, "metadata")
        except Exception as error:
            logging.error("Error fetching metadata: %s", error)
            raise

    def logout(self):
        try:
            self._load_saved_cookies_if_needed()
            response = self.session.post(
                self.config.data["server_url"] + LOGOUT_URL,
                data={"_csrf": self.config.data.get("csrf_token", "")},
                verify=self._verify(),
                timeout=self.REQUEST_TIMEOUT,
            )
            response.raise_for_status()
            if response.status_code in {200, 204}:
                logging.info("Logout successful: %s", response.status_code)
        except Exception as error:
            logging.error("Error during logout: %s", error)

    def get_csrf_token(self) -> str:
        try:
            response = self._authenticated_get(CSRF_URL)
            payload = self._parse_json_object(response, CSRF_URL)
            token = payload.get("token", "")
            return token if isinstance(token, str) else ""
        except Exception as error:
            # The token is used for logout and is not required to establish the
            # clipboard transport. Keep this non-fatal, but record a precise,
            # content-free response diagnosis.
            logging.warning("CSRF token unavailable: %s", error)
            return ""

    @staticmethod
    def get(url: str, headers: dict = None, verify=True) -> requests.Response:
        """A generic GET mapper for public/non-session requests."""
        try:
            response = requests.get(
                url,
                headers=headers,
                verify=verify,
                timeout=RequestManager.REQUEST_TIMEOUT,
            )
            response.raise_for_status()
            return response
        except Exception as error:
            logging.error("Error during GET request: %s", error)
            raise

    @staticmethod
    def post(
        url: str,
        data: dict,
        headers: dict = None,
        verify=True,
    ) -> requests.Response:
        """A generic POST mapper for public/non-session requests."""
        try:
            response = requests.post(
                url,
                data=data,
                headers=headers,
                verify=verify,
                timeout=RequestManager.REQUEST_TIMEOUT,
            )
            response.raise_for_status()
            return response
        except Exception as error:
            logging.error("Error during POST request: %s", error)
            raise
