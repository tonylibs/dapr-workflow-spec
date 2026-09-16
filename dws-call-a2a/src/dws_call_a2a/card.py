"""Agent-card resolution and boot-time validation.

ADR 0004 Decision 1: the card is always resolved by the runner itself, via
`A2ACardResolver` over a runner-owned `httpx.AsyncClient`, never by passing a
bare URL to `create_client`/`ClientFactory` (which would resolve the card with
an httpx client the caller does not control -- the only place card-fetch
credentials can be applied is here).

ADR 0004 Decision 3: normalizes the protocol dialect -- selects the
`supported_interfaces` entry whose `protocol_binding` is `JSONRPC`, and fails
as a configuration error naming which bindings *were* found when none exists.

ADR 0004 Decision 4: the 5-case boot-time auth-validation decision table.
"""

from __future__ import annotations

import hashlib
import logging
from dataclasses import dataclass

import httpx
from a2a.client import A2ACardResolver
from a2a.types import AgentCard, AgentInterface
from a2a.utils.constants import TransportProtocol

from dws_call_a2a.auth import static_headers
from dws_call_a2a.config import (
    AgentCardTarget,
    AuthConfig,
    BasicAuth,
    BearerAuth,
    CardAuthConfig,
    NoAuth,
    OAuth2Auth,
)
from dws_call_a2a.errors import ConfigError, UpstreamError

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class ResolvedCard:
    card: AgentCard
    jsonrpc_interface: AgentInterface


def _card_auth(card_auth: CardAuthConfig | None, rpc_auth: AuthConfig) -> AuthConfig:
    """Two endpoints, one credential by default (ADR 0004 Decision 4)."""
    return rpc_auth if card_auth is None else card_auth


async def resolve_agent_card(
    target: AgentCardTarget,
    rpc_auth: AuthConfig,
    card_auth: CardAuthConfig | None,
    *,
    transport: httpx.AsyncBaseTransport | None = None,
) -> ResolvedCard:
    """Fetches, optionally integrity-checks, and validates the agent card.

    `transport` is a dependency-injection seam for tests (`httpx.MockTransport`);
    production leaves it `None` and gets a real network-backed transport.
    """
    headers = static_headers(_card_auth(card_auth, rpc_auth))
    async with httpx.AsyncClient(headers=headers, timeout=30.0, transport=transport) as client:
        resolver = A2ACardResolver(client, target.agent_card_url)
        if target.agent_card_sha256 is not None:
            card_document_url = f"{target.agent_card_url}/{resolver.agent_card_path.lstrip('/')}"
            await _verify_card_sha256(client, card_document_url, target.agent_card_sha256)

        try:
            card = await resolver.get_agent_card()
        except Exception as exc:  # noqa: BLE001 -- re-raised as our own UpstreamError
            raise UpstreamError(
                f"failed to resolve agent card from {target.agent_card_url}: {exc}"
            ) from exc

    interface = select_jsonrpc_interface(card)
    return ResolvedCard(card=card, jsonrpc_interface=interface)


async def _verify_card_sha256(client: httpx.AsyncClient, url: str, expected_sha256: str) -> None:
    try:
        response = await client.get(url)
        response.raise_for_status()
    except httpx.HTTPError as exc:
        raise UpstreamError(
            f"failed to fetch agent card from {url} for integrity check: {exc}"
        ) from exc

    actual = hashlib.sha256(response.content).hexdigest()
    if actual != expected_sha256:
        raise ConfigError(
            f"AGENT_CARD_SHA256 mismatch for {url}: expected {expected_sha256}, got {actual}"
        )


def select_jsonrpc_interface(card: AgentCard) -> AgentInterface:
    """ADR 0004 Decision 3: pick the `supportedInterfaces` entry whose
    `protocolBinding` is `JSONRPC`; fail loudly, naming what *was* found, when
    none exists."""
    bindings_found: list[str] = []
    for interface in card.supported_interfaces:
        bindings_found.append(interface.protocol_binding)
        if interface.protocol_binding == TransportProtocol.JSONRPC:
            return interface

    raise ConfigError(
        "agent card declares no JSONRPC-bound interface in supportedInterfaces "
        f"(only supported protocol_binding for call: a2a per ADR 0004 Decision 2/3); "
        f"bindings found: {bindings_found or '(none)'}"
    )


# ADR 0004 Decision 4's boot-time decision table, cases 1/2/3/4. Case 5 (no
# card at all, `SERVER_URL` used) is handled by the caller never invoking
# this function for a `ServerTarget`.
def validate_card_auth(card: AgentCard, rpc_auth: AuthConfig) -> None:
    required_kinds = _required_auth_kinds(card)
    my_kind = _auth_kind(rpc_auth)

    if my_kind is None:
        if required_kinds:
            raise ConfigError(
                "agent card requires authentication "
                f"({sorted(required_kinds)}) but no authentication is configured "
                "(AUTH_SCHEME=none) -- failing at boot per ADR 0004 Decision 4 case 4"
            )
        return  # case 3: absent + empty -> proceed unauthenticated

    if required_kinds and my_kind not in required_kinds:
        logger.warning(
            "configured auth scheme %r does not match any scheme the agent card "
            "requires (%s) -- applying it anyway per ADR 0004 Decision 4 case 2 "
            "('security' has OR semantics across multiple schemes)",
            my_kind,
            sorted(required_kinds),
        )
    # case 1 (match) or the card declaring no requirement at all: apply, proceed.


def _auth_kind(auth: AuthConfig) -> str | None:
    if isinstance(auth, NoAuth):
        return None
    if isinstance(auth, BasicAuth):
        return "basic"
    if isinstance(auth, BearerAuth):
        return "bearer"
    if isinstance(auth, OAuth2Auth):
        return "oauth2"
    raise TypeError(f"unknown auth config: {auth!r}")  # pragma: no cover


def _required_auth_kinds(card: AgentCard) -> set[str]:
    kinds: set[str] = set()
    for requirement in card.security_requirements:
        for scheme_name in requirement.schemes:
            scheme = card.security_schemes.get(scheme_name)
            if scheme is None:
                continue
            kinds.add(_scheme_kind(scheme))
    return kinds


def _scheme_kind(scheme: object) -> str:
    which = scheme.WhichOneof("scheme")  # type: ignore[attr-defined]
    if which == "http_auth_security_scheme":
        return scheme.http_auth_security_scheme.scheme.lower()  # type: ignore[attr-defined]
    if which == "oauth2_security_scheme":
        return "oauth2"
    if which == "api_key_security_scheme":
        return "apikey"
    if which == "open_id_connect_security_scheme":
        return "openidconnect"
    if which == "mtls_security_scheme":
        return "mtls"
    return "unknown"  # pragma: no cover -- exhaustive over current SDK schema
