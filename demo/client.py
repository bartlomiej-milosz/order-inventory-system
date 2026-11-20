"""Small HTTP client shared by the example scenarios."""

import base64
import json
import sys
from getpass import getpass
from http.cookiejar import CookieJar
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import HTTPCookieProcessor, HTTPRedirectHandler, Request, build_opener


class DemoError(Exception):
    """A request or its business result differed from the walkthrough."""


def expect_fields(response, **expected):
    for field, value in expected.items():
        if field not in response or response[field] != value:
            raise DemoError(f"Expected {field}={value!r}, received {response!r}")


def read_password(prompt):
    if sys.stdin.isatty():
        return getpass(prompt + ": ")
    line = sys.stdin.readline()
    if not line:
        raise DemoError(f"Missing input for {prompt.lower()}")
    return line.rstrip("\r\n")


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Each scenario expects the original endpoint to answer directly.
        return None


class Api:
    def __init__(self, base_url, *, verbose=False):
        url = urlsplit(base_url)
        if (
            url.scheme not in ("http", "https")
            or not url.hostname
            or url.username is not None
            or url.password is not None
            or url.query
            or url.fragment
        ):
            raise DemoError(
                "BASE_URL must be an HTTP(S) base URL without credentials, query or fragment"
            )
        self.verbose = verbose
        self.base_url = base_url.rstrip("/")
        self.http = build_opener(HTTPCookieProcessor(CookieJar()), NoRedirect())
        self.auth = {}

    def sign_in(self, email, *, admin=False):
        if not email or any(char in email for char in ":\r\n"):
            raise DemoError("Enter an employee email address")
        password = read_password("Employee password")
        token = base64.b64encode(f"{email}:{password}".encode()).decode("ascii")
        self.auth = {"Authorization": f"Basic {token}"}
        employee = self.request("GET", "/api/v1/auth/me")
        print(f"Signed in as {employee['email']} ({employee['role']}), ID {employee['id']}")
        if admin:
            expect_fields(employee, role="ADMIN")

    def request(self, method, path, body=None, *, status=200):
        headers = {"Accept": "application/json", **self.auth}
        if method != "GET":
            # A previous response may have cleared the cookie. Obtain a matching pair for this write.
            csrf = self._send("GET", "/api/v1/auth/csrf", self.auth, None, 200, show_body=False)
            headers[csrf["headerName"]] = csrf["token"]
        return self._send(method, path, headers, body, status, show_body=True)

    def _send(self, method, path, headers, body, status, *, show_body):
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode()
        req = Request(self.base_url + path, data=data, headers=headers, method=method)
        try:
            response = self.http.open(req, timeout=15)
        except HTTPError as error:
            response = error  # Expected 401 and 409 responses are part of the examples.
        with response:
            print(f"{method} {path} -> {response.status} (expected {status})", flush=True)
            raw = response.read()
            if response.status != status:
                reason = response.reason
                if response.headers.get_content_type() == "application/problem+json":
                    reason = json.loads(raw).get("detail", reason)
                raise DemoError(f"{method} {path}: {response.status} {reason}")
        if not raw:
            return None
        result = json.loads(raw)
        if self.verbose and show_body:
            print(json.dumps(result, ensure_ascii=False, indent=2))
        return result
