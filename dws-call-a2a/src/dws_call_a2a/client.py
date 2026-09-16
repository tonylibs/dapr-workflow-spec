"""Builds the a2a-sdk `Client` used for the actual JSON-RPC RPC call.

Restricted to the JSONRPC transport only (ADR 0004 Decision 2/3 -- `call: a2a`
never speaks gRPC or REST). Constructed directly from `BaseClient` + a
JSON-RPC transport rather than via `create_client()`/`ClientFactory`, for two
reasons:

1. Decision 3's interface selection (and its "fail, naming what was found"
   error) is already done by `card.select_jsonrpc_interface` -- redoing it via
   `ClientFactory`'s own selection would duplicate that work and produce a
   less specific error (`ValueError('no compatible transports found.')`).
2. oauth2 auth (Decision 4) requires rerouting the RPC URL through Dapr's
   sidecar invoke proxy (see `auth.rpc_target_url`) *before* the transport is
   constructed -- `ClientFactory` has no hook for rewriting the destination
   URL it resolves from the card.

Dialect selection (legacy v0.3-compatible vs modern protobuf-JSON, see
`CLAUDE.md` "ADR verification notes") still uses the SDK's own
`is_legacy_version` -- the exact function `ClientFactory` uses internally --
so this runner's behavior matches what `create_client()` would have chosen.
"""

from __future__ import annotations

import httpx
from a2a.client import BaseClient, ClientConfig
from a2a.client.transports.jsonrpc import JsonRpcTransport
from a2a.compat.v0_3.jsonrpc_transport import CompatJsonRpcTransport
from a2a.compat.v0_3.versions import is_legacy_version
from a2a.types import AgentCard
from a2a.utils.constants import PROTOCOL_VERSION_CURRENT, VERSION_HEADER, TransportProtocol

from dws_call_a2a.auth import rpc_target_url, static_headers
from dws_call_a2a.config import AuthConfig


def build_client(
    *,
    card: AgentCard,
    rpc_url: str,
    protocol_version: str | None,
    auth: AuthConfig,
    timeout_ms: int,
    transport: httpx.AsyncBaseTransport | None = None,
) -> tuple[BaseClient, httpx.AsyncClient]:
    """Returns the `Client` plus its owned `httpx.AsyncClient` (the caller is
    responsible for closing the latter, e.g. via `Client.close()`).

    `transport` is a dependency-injection seam for tests (`httpx.MockTransport`
    or `httpx.ASGITransport`); production leaves it `None` and gets a real
    network-backed transport.
    """
    target_url = rpc_target_url(rpc_url, auth)
    httpx_client = httpx.AsyncClient(
        headers=static_headers(auth),
        timeout=timeout_ms / 1000,
        transport=transport,
    )
    # The modern (non-compat) transport relies on this default header to
    # negotiate the "1.0" dialect -- `CompatJsonRpcTransport` overrides it
    # per-request regardless, but setting it unconditionally here matches
    # exactly what `ClientFactory.__init__` does, so behavior doesn't depend
    # on which transport ends up selected below.
    httpx_client.headers.setdefault(VERSION_HEADER, PROTOCOL_VERSION_CURRENT)

    transport_cls = (
        CompatJsonRpcTransport if is_legacy_version(protocol_version) else JsonRpcTransport
    )
    rpc_transport = transport_cls(httpx_client, card, target_url)

    config = ClientConfig(
        streaming=False,
        polling=False,
        httpx_client=httpx_client,
        supported_protocol_bindings=[TransportProtocol.JSONRPC],
    )
    client = BaseClient(card, config, rpc_transport, interceptors=[])
    return client, httpx_client
