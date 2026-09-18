"""Assembles the boot-time singletons (config, resolved card, `Client`) once
at startup and tears them down at shutdown. Kept as a plain, explicitly
constructed object (not a global) so tests can build one directly against a
mock transport instead of going through the FastAPI lifespan.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass

import httpx
from a2a.utils.constants import TransportProtocol

from dws_call_a2a.card import resolve_agent_card, validate_card_auth
from dws_call_a2a.client import build_client
from dws_call_a2a.config import AgentCardTarget, Config, ServerTarget
from dws_call_a2a.protocols import RpcClient

logger = logging.getLogger(__name__)


@dataclass(slots=True)
class AppState:
    config: Config
    client: RpcClient
    httpx_client: httpx.AsyncClient


async def build_app_state(
    config: Config, *, transport: httpx.AsyncBaseTransport | None = None
) -> AppState:
    """`transport` is a dependency-injection seam for tests (`httpx.MockTransport`
    or `httpx.ASGITransport` pointed at a fake/conformance agent app);
    production leaves it `None` and gets a real network-backed transport for
    both the card fetch and the RPC client.
    """
    if isinstance(config.target, AgentCardTarget):
        resolved = await resolve_agent_card(
            config.target, config.rpc_auth, config.card_auth, transport=transport
        )
        validate_card_auth(resolved.card, config.rpc_auth)
        client, httpx_client = build_client(
            card=resolved.card,
            rpc_url=resolved.jsonrpc_interface.url,
            protocol_version=resolved.jsonrpc_interface.protocol_version or None,
            auth=config.rpc_auth,
            timeout_ms=config.timeout_ms,
            transport=transport,
        )
    elif isinstance(config.target, ServerTarget):
        # ADR 0004 Decision 4 case 5: no card, no auth-requirement check
        # possible. No `protocolVersion` signal exists either (there is no
        # card to declare one). `is_legacy_version(None)` resolves to the
        # *modern* dialect (matching the SDK's own default when an interface
        # omits `protocolVersion`), but for a bare `SERVER_URL` this runner
        # instead defaults to the legacy (v0.3-compatible) JSON-RPC dialect --
        # the predominant convention among currently-deployed A2A agents --
        # by passing a fixed "0.3.0" sentinel rather than `None`. See
        # CLAUDE.md "ADR gaps found" for why this default was chosen and
        # isn't itself in the ADR.
        from a2a.client.client_factory import minimal_agent_card

        card = minimal_agent_card(config.target.server_url, transports=[TransportProtocol.JSONRPC])
        client, httpx_client = build_client(
            card=card,
            rpc_url=config.target.server_url,
            protocol_version="0.3.0",
            auth=config.rpc_auth,
            timeout_ms=config.timeout_ms,
            transport=transport,
        )
    else:  # pragma: no cover -- exhaustive union
        raise TypeError(f"unknown target: {config.target!r}")

    return AppState(config=config, client=client, httpx_client=httpx_client)


async def close_app_state(state: AppState) -> None:
    await state.httpx_client.aclose()
