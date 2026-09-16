from __future__ import annotations

import hashlib
import json

import httpx
import pytest
from a2a.client.card_resolver import parse_agent_card
from a2a.types import AgentCard, AgentInterface

from dws_call_a2a.card import (
    resolve_agent_card,
    select_jsonrpc_interface,
    validate_card_auth,
)
from dws_call_a2a.config import AgentCardTarget, BasicAuth, BearerAuth, NoAuth
from dws_call_a2a.errors import ConfigError, UpstreamError


def _card_with_interfaces(*bindings: str) -> AgentCard:
    return parse_agent_card(
        {
            "name": "test",
            "version": "1",
            "defaultInputModes": [],
            "defaultOutputModes": [],
            "skills": [],
            "capabilities": {},
            "supportedInterfaces": [
                {
                    "url": f"https://agent.example.com/{b.lower()}",
                    "protocolBinding": b,
                    "protocolVersion": "0.3.0",
                }
                for b in bindings
            ],
        }
    )


def test_select_jsonrpc_interface_found() -> None:
    card = _card_with_interfaces("GRPC", "JSONRPC")
    interface = select_jsonrpc_interface(card)
    assert isinstance(interface, AgentInterface)
    assert interface.protocol_binding == "JSONRPC"


def test_select_jsonrpc_interface_missing_names_found_bindings() -> None:
    card = _card_with_interfaces("GRPC", "HTTP+JSON")
    with pytest.raises(ConfigError, match=r"GRPC.*HTTP\+JSON|HTTP\+JSON.*GRPC"):
        select_jsonrpc_interface(card)


# --- ADR 0004 Decision 4's boot-time decision table -------------------------


def _card_requiring(scheme_type: str, scheme_name: str = "http") -> AgentCard:
    return parse_agent_card(
        {
            "name": "test",
            "version": "1",
            "defaultInputModes": [],
            "defaultOutputModes": [],
            "skills": [],
            "capabilities": {},
            "securitySchemes": {scheme_name: {"type": "http", "scheme": scheme_type}},
            "security": [{scheme_name: []}],
        }
    )


def _card_with_no_requirement() -> AgentCard:
    return parse_agent_card(
        {
            "name": "test",
            "version": "1",
            "defaultInputModes": [],
            "defaultOutputModes": [],
            "skills": [],
            "capabilities": {},
        }
    )


def test_case1_declared_matches_card_requirement_proceeds() -> None:
    validate_card_auth(_card_requiring("basic"), BasicAuth(username="a", password="b"))  # no raise


def test_case2_declared_mismatches_card_requirement_warns_and_proceeds(caplog) -> None:
    validate_card_auth(_card_requiring("bearer"), BasicAuth(username="a", password="b"))
    assert "does not match" in caplog.text


def test_case3_absent_and_card_empty_proceeds() -> None:
    validate_card_auth(_card_with_no_requirement(), NoAuth())  # no raise


def test_case4_absent_and_card_requires_fails_boot() -> None:
    with pytest.raises(ConfigError, match="requires authentication"):
        validate_card_auth(_card_requiring("bearer"), NoAuth())


def test_declared_and_card_requires_nothing_proceeds_without_warning(caplog) -> None:
    """Not one of the ADR's 5 tabulated rows -- see CLAUDE.md 'card
    boot-table gap'. Resolved here as: apply unconditionally, no warning."""
    validate_card_auth(_card_with_no_requirement(), BearerAuth(token="t"))
    assert "does not match" not in caplog.text


# --- resolve_agent_card end-to-end over a mocked transport ------------------


def _legacy_card_json(*, rpc_url: str, auth: str | None) -> bytes:
    card: dict = {
        "name": "mock-agent",
        "version": "1.0",
        "url": rpc_url,
        "protocolVersion": "0.3.0",
        "defaultInputModes": ["text"],
        "defaultOutputModes": ["text"],
        "skills": [],
        "capabilities": {},
    }
    if auth == "basic":
        card["securitySchemes"] = {"basicAuth": {"type": "http", "scheme": "basic"}}
        card["security"] = [{"basicAuth": []}]
    return json.dumps(card).encode()


@pytest.mark.asyncio
async def test_resolve_agent_card_upgrades_legacy_shape() -> None:
    card_bytes = _legacy_card_json(rpc_url="https://agent.example.com/rpc", auth=None)

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/.well-known/agent-card.json"
        return httpx.Response(200, content=card_bytes)

    transport = httpx.MockTransport(handler)
    target = AgentCardTarget(agent_card_url="https://agent.example.com", agent_card_sha256=None)
    resolved = await resolve_agent_card(target, NoAuth(), None, transport=transport)

    assert resolved.jsonrpc_interface.protocol_binding == "JSONRPC"
    assert resolved.jsonrpc_interface.url == "https://agent.example.com/rpc"


@pytest.mark.asyncio
async def test_resolve_agent_card_sha256_match() -> None:
    card_bytes = _legacy_card_json(rpc_url="https://agent.example.com/rpc", auth=None)
    expected = hashlib.sha256(card_bytes).hexdigest()

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, content=card_bytes)

    transport = httpx.MockTransport(handler)
    target = AgentCardTarget(agent_card_url="https://agent.example.com", agent_card_sha256=expected)
    resolved = await resolve_agent_card(target, NoAuth(), None, transport=transport)
    assert resolved.card.name == "mock-agent"


@pytest.mark.asyncio
async def test_resolve_agent_card_sha256_mismatch_raises_config_error() -> None:
    card_bytes = _legacy_card_json(rpc_url="https://agent.example.com/rpc", auth=None)

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, content=card_bytes)

    transport = httpx.MockTransport(handler)
    target = AgentCardTarget(agent_card_url="https://agent.example.com", agent_card_sha256="0" * 64)
    with pytest.raises(ConfigError, match="AGENT_CARD_SHA256 mismatch"):
        await resolve_agent_card(target, NoAuth(), None, transport=transport)


@pytest.mark.asyncio
async def test_resolve_agent_card_sha256_check_fetch_http_error_raises_upstream_error() -> None:
    """`_verify_card_sha256`'s own fetch (the integrity-check GET, distinct
    from `A2ACardResolver.get_agent_card()`'s own request) failing with a
    non-2xx status must surface as `UpstreamError`, not propagate raw."""

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, content=b"internal error")

    transport = httpx.MockTransport(handler)
    target = AgentCardTarget(agent_card_url="https://agent.example.com", agent_card_sha256="0" * 64)
    with pytest.raises(UpstreamError, match="integrity check"):
        await resolve_agent_card(target, NoAuth(), None, transport=transport)


@pytest.mark.asyncio
async def test_resolve_agent_card_transport_failure_raises_upstream_error() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused")

    transport = httpx.MockTransport(handler)
    target = AgentCardTarget(agent_card_url="https://agent.example.com", agent_card_sha256=None)
    with pytest.raises(UpstreamError):
        await resolve_agent_card(target, NoAuth(), None, transport=transport)


@pytest.mark.asyncio
async def test_resolve_agent_card_uses_card_auth_header_not_rpc_auth() -> None:
    seen_headers: list[str | None] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen_headers.append(request.headers.get("authorization"))
        return httpx.Response(
            200, content=_legacy_card_json(rpc_url="https://agent.example.com/rpc", auth=None)
        )

    transport = httpx.MockTransport(handler)
    target = AgentCardTarget(agent_card_url="https://agent.example.com", agent_card_sha256=None)
    await resolve_agent_card(
        target,
        rpc_auth=BearerAuth(token="rpc-token"),
        card_auth=BearerAuth(token="card-token"),
        transport=transport,
    )
    assert seen_headers == ["Bearer card-token"]


@pytest.mark.asyncio
async def test_resolve_agent_card_reuses_rpc_auth_when_card_auth_absent() -> None:
    seen_headers: list[str | None] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen_headers.append(request.headers.get("authorization"))
        return httpx.Response(
            200, content=_legacy_card_json(rpc_url="https://agent.example.com/rpc", auth=None)
        )

    transport = httpx.MockTransport(handler)
    target = AgentCardTarget(agent_card_url="https://agent.example.com", agent_card_sha256=None)
    await resolve_agent_card(
        target, rpc_auth=BearerAuth(token="rpc-token"), card_auth=None, transport=transport
    )
    assert seen_headers == ["Bearer rpc-token"]
