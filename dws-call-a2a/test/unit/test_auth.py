from __future__ import annotations

import base64

from dws_call_a2a.auth import dapr_invocation_url, rpc_target_url, static_headers
from dws_call_a2a.config import BasicAuth, BearerAuth, NoAuth, OAuth2Auth


def test_no_auth_has_no_headers() -> None:
    assert static_headers(NoAuth()) == {}


def test_bearer_auth_header() -> None:
    assert static_headers(BearerAuth(token="tok-123")) == {"Authorization": "Bearer tok-123"}


def test_basic_auth_header() -> None:
    headers = static_headers(BasicAuth(username="alice", password="s3cret"))
    decoded = base64.b64decode(headers["Authorization"].removeprefix("Basic ")).decode()
    assert decoded == "alice:s3cret"


def test_oauth2_auth_has_no_static_header() -> None:
    assert static_headers(OAuth2Auth(oauth_endpoint="agent-endpoint", dapr_http_port="3500")) == {}


def test_rpc_target_url_unchanged_for_basic() -> None:
    url = "https://agent.example.com/rpc"
    assert rpc_target_url(url, BasicAuth(username="a", password="b")) == url


def test_rpc_target_url_rerouted_for_oauth2() -> None:
    url = "https://agent.example.com/rpc?x=1"
    result = rpc_target_url(url, OAuth2Auth(oauth_endpoint="agent-endpoint", dapr_http_port="3500"))
    assert result == "http://localhost:3500/v1.0/invoke/agent-endpoint/method/rpc?x=1"


def test_dapr_invocation_url_preserves_path_and_query() -> None:
    result = dapr_invocation_url("https://agent.example.com/a2a/rpc?foo=bar", "my-endpoint", "3500")
    assert result == "http://localhost:3500/v1.0/invoke/my-endpoint/method/a2a/rpc?foo=bar"


def test_dapr_invocation_url_defaults_root_path() -> None:
    result = dapr_invocation_url("https://agent.example.com", "my-endpoint", "3500")
    assert result == "http://localhost:3500/v1.0/invoke/my-endpoint/method/"
