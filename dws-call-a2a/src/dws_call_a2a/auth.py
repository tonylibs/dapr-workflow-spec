"""Turns resolved auth config into request material applied to every outbound
call -- the RPC endpoint and (by default) the card-fetch endpoint too.

Ported from `dws-call-openapi/src/auth.ts` + `src/request.ts`'s `daprInvocationUrl`.
Built by hand (not the SDK's `AuthInterceptor`/`CredentialService`) because the
SDK's built-in interceptor never emits HTTP Basic -- see
`docs/adr/0004-call-a2a-runner-design.md` Decision 4.
"""

from __future__ import annotations

import base64
from urllib.parse import urlsplit

from dws_call_a2a.config import AuthConfig, BasicAuth, BearerAuth, NoAuth, OAuth2Auth


def static_headers(auth: AuthConfig) -> dict[str, str]:
    """Headers applied on every request for basic/bearer/none.

    oauth2 is handled entirely by rerouting the request through Dapr's sidecar
    (see `rpc_target_url`) -- it contributes no static header here, and the
    sidecar's injected `Authorization` must not be overwritten.
    """
    if isinstance(auth, NoAuth):
        return {}
    if isinstance(auth, BearerAuth):
        return {"Authorization": f"Bearer {auth.token}"}
    if isinstance(auth, BasicAuth):
        raw = f"{auth.username}:{auth.password}".encode()
        return {"Authorization": f"Basic {base64.b64encode(raw).decode('ascii')}"}
    if isinstance(auth, OAuth2Auth):
        return {}
    raise TypeError(f"unknown auth config: {auth!r}")  # pragma: no cover -- exhaustive union


def rpc_target_url(url: str, auth: AuthConfig) -> str:
    """The URL the RPC transport should actually connect to.

    For everything except oauth2 this is `url` unchanged. For oauth2, Dapr's
    OAuth2 client-credentials middleware only fires on a Dapr service
    invocation, so the request is rerouted through the sidecar's invoke proxy:
    `http://localhost:{dapr_http_port}/v1.0/invoke/{oauth_endpoint}/method{path}{query}`.
    The sidecar injects the real `Authorization` header before forwarding to
    the agent's actual RPC URL -- the runner never sees or manages the token.
    """
    if not isinstance(auth, OAuth2Auth):
        return url
    return dapr_invocation_url(url, auth.oauth_endpoint, auth.dapr_http_port)


def dapr_invocation_url(url: str, endpoint: str, port: str) -> str:
    """Rewrites only the destination; path and query are preserved verbatim."""
    parts = urlsplit(url)
    path = parts.path or "/"
    query = f"?{parts.query}" if parts.query else ""
    return f"http://localhost:{port}/v1.0/invoke/{endpoint}/method{path}{query}"
