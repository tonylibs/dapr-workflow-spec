"""Loads and validates step configuration from the environment.

One generic image serves every `call: a2a` step; all behavior is defined by the
env vars parsed here. Every invalid or missing required value raises
`ConfigError` so the process can exit non-zero at startup (fail fast), matching
`dws-call-openapi/src/config/config.ts`.

Env var contract -- see `../CLAUDE.md` "Env var contract" for the full table and
why these names were chosen (and how they deliberately differ from
`DOCUMENT_URL`/`DOCUMENT_SHA256` and `DOC_ENDPOINT`/`DOC_SHA256`).
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from typing import Literal

from dws_call_a2a.errors import ConfigError

Method = Literal["message/send", "tasks/get"]
_VALID_METHODS = ("message/send", "tasks/get")

DEFAULT_PORT = 8080
DEFAULT_DAPR_HTTP_PORT = "3500"
DEFAULT_TIMEOUT_MS = 30_000
_SHA256_RE_LEN = 64


@dataclass(frozen=True, slots=True)
class AgentCardTarget:
    """`with.agentCard` was declared: resolve the card ourselves, then talk
    to whichever JSONRPC-bound interface it advertises."""

    agent_card_url: str
    agent_card_sha256: str | None


@dataclass(frozen=True, slots=True)
class ServerTarget:
    """`with.server` was declared instead: talk to this URL directly, no card
    fetch, no boot-time auth-requirement check (ADR 0004 Decision 4 case 5)."""

    server_url: str


Target = AgentCardTarget | ServerTarget


@dataclass(frozen=True, slots=True)
class NoAuth:
    pass


@dataclass(frozen=True, slots=True)
class BasicAuth:
    username: str
    password: str


@dataclass(frozen=True, slots=True)
class BearerAuth:
    token: str


@dataclass(frozen=True, slots=True)
class OAuth2Auth:
    """Dapr's sidecar injects the token; the runner never sees or manages one.

    The outbound RPC request is rerouted through Dapr's service-invocation
    proxy (`http://localhost:{dapr_http_port}/v1.0/invoke/{oauth_endpoint}/method...`)
    exactly like `dws-call-openapi`'s `daprInvocationUrl` -- see `auth.py`.
    """

    oauth_endpoint: str
    dapr_http_port: str


AuthConfig = NoAuth | BasicAuth | BearerAuth | OAuth2Auth

# Card-fetch auth never supports oauth2 (ADR 0004 Decision 4: "no oauth2 for
# the card path" -- there is no card-fetch-equivalent of the RPC's Dapr
# middleware rerouting since the card fetch is plain HTTPS, not a Dapr
# service invocation).
CardAuthConfig = NoAuth | BasicAuth | BearerAuth


@dataclass(frozen=True, slots=True)
class ObjectParameters:
    """`with.parameters` was an object: an arbitrary nested JSON structure
    (dicts, lists, strings, numbers, booleans, `None`) taken verbatim from
    the compiled `with.parameters` value, where select string leaves are
    exactly `${ <jq expression> }` -- the *entire* string, anchored, not a
    partial/template substitution (this repo's runtime-expression
    convention; see `dws-controller`'s `SECRET_REFERENCE` regex). Those are
    evaluated as jq programs against the request input and replaced by the
    jq result; every other leaf -- including a plain literal string that
    merely contains `${` without wrapping the whole value -- passes through
    unchanged. See `jq_eval.py`'s `evaluate_parameters` for the recursive
    walk that implements this.

    This is deliberately *not* a direct port of `dws-call-openapi`'s
    `HEADERS`/`QUERY` model (every value a full jq-expression string): that
    package's schema types every value as a full expression by construction,
    but a2a's `with.parameters` (`WithA2AParameters`) permits arbitrary
    literal JSON with select `${...}`-wrapped strings nested at any depth
    (e.g. the standard `message/send` shape
    `{"message": {"role": "user", "parts": [{"kind": "text", "text": "${ .userQuestion }"}]}}`),
    matching what `dws-controller`'s `V1OrchestratorCompiler.a2aParameters`
    actually emits -- a verbatim nested JSON dump of `with.parameters`, not a
    flattened map of expression strings.
    """

    value: dict[str, object]


@dataclass(frozen=True, slots=True)
class StringParameters:
    """`with.parameters` was a single string: the whole string is one jq
    expression, and its result *is* the entire params object."""

    expression: str


ParametersSpec = ObjectParameters | StringParameters


@dataclass(frozen=True, slots=True)
class Config:
    port: int
    task: str
    target: Target
    method: Method
    parameters: ParametersSpec
    rpc_auth: AuthConfig
    card_auth: CardAuthConfig | None
    """`None` means "reuse `rpc_auth` for the card fetch" (the documented
    two-endpoints-one-credential default, ADR 0004 Decision 4)."""
    include_history: bool
    timeout_ms: int


def load_config(env: dict[str, str] | None = None) -> Config:
    """Reads and validates configuration from `env` (defaults to `os.environ`)."""
    env = dict(os.environ if env is None else env)

    target = _parse_target(env)
    rpc_auth = _parse_rpc_auth(env)
    card_auth = _parse_card_auth(env)
    _validate_card_auth_reuse(target, rpc_auth, card_auth)

    return Config(
        port=_parse_port(env.get("PORT")),
        task=_required(env, "TASK"),
        target=target,
        method=_parse_method(env),
        parameters=_parse_parameters(env),
        rpc_auth=rpc_auth,
        card_auth=card_auth,
        include_history=_parse_bool(env.get("INCLUDE_HISTORY"), default=False),
        timeout_ms=_parse_timeout(env.get("TIMEOUT")),
    )


def _validate_card_auth_reuse(
    target: Target, rpc_auth: AuthConfig, card_auth: CardAuthConfig | None
) -> None:
    """`CARD_AUTH_SCHEME` absent (`card_auth is None`) means "reuse `AUTH_*`
    for the card fetch too" (the documented two-endpoints-one-credential
    default) -- but that default silently degrades to *no* authentication at
    all when `rpc_auth` is oauth2: oauth2 material is only ever injected by
    the Dapr sidecar on a service-invocation call, and the card fetch is
    plain HTTPS, never routed through the sidecar. Fail loudly instead of
    silently sending the card fetch unauthenticated.

    Only applies when a card fetch will actually happen (`AgentCardTarget`);
    a `ServerTarget` config never fetches a card at all, so there's nothing
    to cross-check. An explicit `CARD_AUTH_SCHEME` -- including `none`, which
    `_parse_card_auth` resolves to `NoAuth()`, not `None` -- is the author's
    deliberate choice and is always respected.
    """
    if not isinstance(target, AgentCardTarget):
        return
    if not isinstance(rpc_auth, OAuth2Auth):
        return
    if card_auth is not None:
        return
    raise ConfigError(
        "AUTH_SCHEME=oauth2 cannot be reused for the agent-card fetch (the card fetch is "
        "plain HTTPS, not a Dapr service invocation) -- set CARD_AUTH_SCHEME explicitly, or "
        "CARD_AUTH_SCHEME=none if the card genuinely requires no auth"
    )


def _required(env: dict[str, str], key: str) -> str:
    value = _non_empty(env.get(key))
    if value is None:
        raise ConfigError(f"{key} is required")
    return value


def _non_empty(value: str | None) -> str | None:
    if value is None:
        return None
    trimmed = value.strip()
    return trimmed or None


def _parse_port(raw: str | None) -> int:
    value = _non_empty(raw)
    if value is None:
        return DEFAULT_PORT
    try:
        port = int(value)
    except ValueError as exc:
        raise ConfigError(f"PORT must be an integer, got {value}") from exc
    if not (1 <= port <= 65_535):
        raise ConfigError(f"PORT must be between 1 and 65535, got {port}")
    return port


def _parse_target(env: dict[str, str]) -> Target:
    card_url = _non_empty(env.get("AGENT_CARD_URL"))
    server_url = _non_empty(env.get("SERVER_URL"))
    if card_url is not None and server_url is not None:
        raise ConfigError("exactly one of AGENT_CARD_URL or SERVER_URL may be set, got both")
    if card_url is None and server_url is None:
        raise ConfigError("exactly one of AGENT_CARD_URL or SERVER_URL is required, got neither")
    if server_url is not None:
        return ServerTarget(server_url=server_url)

    sha256 = _non_empty(env.get("AGENT_CARD_SHA256"))
    if sha256 is not None:
        sha256 = sha256.lower()
        if len(sha256) != _SHA256_RE_LEN or any(c not in "0123456789abcdef" for c in sha256):
            raise ConfigError("AGENT_CARD_SHA256 must be a 64-character hex string")
    return AgentCardTarget(agent_card_url=card_url, agent_card_sha256=sha256)  # type: ignore[arg-type]


def _parse_method(env: dict[str, str]) -> Method:
    method = _required(env, "METHOD")
    if method not in _VALID_METHODS:
        raise ConfigError(f"METHOD must be one of {_VALID_METHODS}, got {method!r}")
    return method  # type: ignore[return-value]


def _parse_parameters(env: dict[str, str]) -> ParametersSpec:
    raw = _required(env, "PARAMETERS")
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise ConfigError(f"PARAMETERS must be valid JSON: {exc}") from exc

    if isinstance(parsed, str):
        spec: ParametersSpec = StringParameters(expression=parsed)
    elif isinstance(parsed, dict) and not isinstance(parsed, bool):
        # Accepted verbatim, including empty (`dws-controller` now always
        # emits PARAMETERS, using `"{}"` when `with.parameters` is absent) --
        # every value may be arbitrary nested JSON; see `ObjectParameters`'s
        # docstring for why this isn't constrained to flat expression
        # strings the way `dws-call-openapi`'s HEADERS/QUERY are.
        spec = ObjectParameters(value=parsed)
    else:
        raise ConfigError("PARAMETERS must decode to a JSON object or a JSON string")

    # Deferred import: `jq_eval` imports `ObjectParameters`/`ParametersSpec`/
    # `StringParameters` from this module at its own top level, so importing
    # it back at *this* module's top level would be circular. By the time
    # `_parse_parameters` actually runs (during `load_config()`, well after
    # both modules have finished their own import-time execution), this
    # import just resolves against the already-fully-loaded module.
    from dws_call_a2a.jq_eval import validate_no_env_access

    validate_no_env_access(spec)
    return spec


def _parse_rpc_auth(env: dict[str, str]) -> AuthConfig:
    scheme = (_non_empty(env.get("AUTH_SCHEME")) or "none").lower()
    if scheme == "none":
        return NoAuth()
    if scheme == "basic":
        return BasicAuth(
            username=_required(env, "AUTH_USERNAME"),
            password=_required(env, "AUTH_PASSWORD"),
        )
    if scheme == "bearer":
        return BearerAuth(token=_required(env, "AUTH_TOKEN"))
    if scheme == "oauth2":
        return OAuth2Auth(
            oauth_endpoint=_required(env, "OAUTH_ENDPOINT"),
            dapr_http_port=_non_empty(env.get("DAPR_HTTP_PORT")) or DEFAULT_DAPR_HTTP_PORT,
        )
    raise ConfigError(f"AUTH_SCHEME must be one of none|basic|bearer|oauth2, got {scheme!r}")


def _parse_card_auth(env: dict[str, str]) -> CardAuthConfig | None:
    """`None` means "no card-specific auth declared" -- callers reuse `rpc_auth`."""
    scheme = _non_empty(env.get("CARD_AUTH_SCHEME"))
    if scheme is None:
        return None
    scheme = scheme.lower()
    if scheme == "none":
        return NoAuth()
    if scheme == "basic":
        return BasicAuth(
            username=_required(env, "CARD_AUTH_USERNAME"),
            password=_required(env, "CARD_AUTH_PASSWORD"),
        )
    if scheme == "bearer":
        return BearerAuth(token=_required(env, "CARD_AUTH_TOKEN"))
    raise ConfigError(
        f"CARD_AUTH_SCHEME must be one of none|basic|bearer (no oauth2 for the card path), "
        f"got {scheme!r}"
    )


def _parse_bool(raw: str | None, *, default: bool) -> bool:
    value = _non_empty(raw)
    if value is None:
        return default
    lowered = value.lower()
    if lowered in ("true", "1", "yes"):
        return True
    if lowered in ("false", "0", "no"):
        return False
    raise ConfigError(f"expected a boolean (true|false), got {value!r}")


def _parse_timeout(raw: str | None) -> int:
    """Accepts `30s`, `1m`, `500ms`, or a bare integer (milliseconds)."""
    value = _non_empty(raw)
    if value is None:
        return DEFAULT_TIMEOUT_MS
    import re

    match = re.fullmatch(r"(\d+)(ms|s|m)?", value)
    if not match:
        raise ConfigError(
            f"TIMEOUT must be like 30s, 1m, 500ms, or a bare millisecond count, got {value}"
        )
    amount = int(match.group(1))
    unit = match.group(2) or "ms"
    factor = {"m": 60_000, "s": 1_000, "ms": 1}[unit]
    ms = amount * factor
    if ms <= 0:
        raise ConfigError(f"TIMEOUT must be positive, got {value}")
    return ms
