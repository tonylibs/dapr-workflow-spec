from __future__ import annotations

import json

import pytest

from dws_call_a2a.config import (
    AgentCardTarget,
    BasicAuth,
    BearerAuth,
    NoAuth,
    OAuth2Auth,
    ObjectParameters,
    ServerTarget,
    StringParameters,
    load_config,
)
from dws_call_a2a.errors import ConfigError
from test.conftest import raw_env


def test_defaults_to_replace_free_minimal_config() -> None:
    config = load_config(raw_env())
    assert config.port == 8080
    assert config.task == "notifyAgent"
    assert isinstance(config.target, AgentCardTarget)
    assert config.target.agent_card_url == "https://agent.example.com"
    assert config.method == "message/send"
    assert config.rpc_auth == NoAuth()
    assert config.card_auth is None
    assert config.include_history is False
    assert config.timeout_ms == 30_000


def test_task_is_required() -> None:
    env = raw_env()
    del env["TASK"]
    with pytest.raises(ConfigError, match="TASK"):
        load_config(env)


@pytest.mark.parametrize(
    "extra",
    [
        {"SERVER_URL": "https://server.example.com"},  # both set alongside AGENT_CARD_URL
    ],
)
def test_exactly_one_of_agent_card_url_or_server_url_both_set(extra: dict[str, str]) -> None:
    env = raw_env(**extra)
    with pytest.raises(ConfigError, match="exactly one"):
        load_config(env)


def test_exactly_one_of_agent_card_url_or_server_url_neither_set() -> None:
    env = raw_env()
    del env["AGENT_CARD_URL"]
    with pytest.raises(ConfigError, match="exactly one"):
        load_config(env)


def test_server_url_alone_is_valid() -> None:
    env = raw_env()
    del env["AGENT_CARD_URL"]
    env["SERVER_URL"] = "https://server.example.com"
    config = load_config(env)
    assert config.target == ServerTarget(server_url="https://server.example.com")


def test_agent_card_sha256_must_be_64_hex_chars() -> None:
    env = raw_env(AGENT_CARD_SHA256="not-hex")
    with pytest.raises(ConfigError, match="AGENT_CARD_SHA256"):
        load_config(env)


def test_agent_card_sha256_is_lowercased() -> None:
    sha = "A" * 64
    env = raw_env(AGENT_CARD_SHA256=sha)
    config = load_config(env)
    assert isinstance(config.target, AgentCardTarget)
    assert config.target.agent_card_sha256 == "a" * 64


def test_method_must_be_message_send_or_tasks_get() -> None:
    env = raw_env(METHOD="tasks/cancel")
    with pytest.raises(ConfigError, match="METHOD"):
        load_config(env)


def test_parameters_object_form() -> None:
    env = raw_env(PARAMETERS='{"message": ".foo", "extra": ".bar"}')
    config = load_config(env)
    assert config.parameters == ObjectParameters(value={"message": ".foo", "extra": ".bar"})


def test_parameters_object_form_allows_nested_json_not_just_flat_expression_strings() -> None:
    """`with.parameters`'s object form allows arbitrary nested JSON, not just
    a flat map of jq-expression strings -- a2a's schema (unlike
    `dws-call-openapi`'s HEADERS/QUERY) doesn't constrain every value to be a
    full expression."""
    raw_parameters: dict[str, object] = {
        "message": {
            "role": "user",
            "parts": [{"kind": "text", "text": "${ .userQuestion }"}],
        }
    }
    env = raw_env(PARAMETERS=json.dumps(raw_parameters))
    config = load_config(env)
    assert config.parameters == ObjectParameters(value=raw_parameters)


def test_parameters_object_form_empty_is_allowed() -> None:
    """`dws-controller` now always emits PARAMETERS, using the `"{}"` literal
    when `with.parameters` is absent -- this must load cleanly, not raise."""
    env = raw_env(PARAMETERS="{}")
    config = load_config(env)
    assert config.parameters == ObjectParameters(value={})


def test_parameters_string_form() -> None:
    env = raw_env(PARAMETERS='"{message: .foo}"')
    config = load_config(env)
    assert config.parameters == StringParameters(expression="{message: .foo}")


def test_parameters_must_decode_to_object_or_string() -> None:
    env = raw_env(PARAMETERS="[1,2,3]")
    with pytest.raises(ConfigError, match="PARAMETERS"):
        load_config(env)


def test_parameters_object_values_may_be_non_string_json() -> None:
    """Object-form values are no longer required to be jq-expression
    strings -- literal numbers/booleans/null are valid leaves too."""
    env = raw_env(PARAMETERS='{"message": 123, "enabled": true, "missing": null}')
    config = load_config(env)
    assert config.parameters == ObjectParameters(
        value={"message": 123, "enabled": True, "missing": None}
    )


def test_parameters_not_json_at_all() -> None:
    env = raw_env(PARAMETERS="not json")
    with pytest.raises(ConfigError, match="PARAMETERS"):
        load_config(env)


def test_auth_scheme_none_is_default() -> None:
    config = load_config(raw_env())
    assert config.rpc_auth == NoAuth()


def test_auth_scheme_basic_requires_username_and_password() -> None:
    env = raw_env(AUTH_SCHEME="basic")
    with pytest.raises(ConfigError, match="AUTH_USERNAME"):
        load_config(env)


def test_auth_scheme_basic() -> None:
    env = raw_env(AUTH_SCHEME="basic", AUTH_USERNAME="alice", AUTH_PASSWORD="s3cret")
    config = load_config(env)
    assert config.rpc_auth == BasicAuth(username="alice", password="s3cret")


def test_auth_scheme_bearer() -> None:
    env = raw_env(AUTH_SCHEME="bearer", AUTH_TOKEN="tok-123")
    config = load_config(env)
    assert config.rpc_auth == BearerAuth(token="tok-123")


def test_auth_scheme_oauth2_requires_endpoint() -> None:
    env = raw_env(AUTH_SCHEME="oauth2")
    with pytest.raises(ConfigError, match="OAUTH_ENDPOINT"):
        load_config(env)


def test_auth_scheme_oauth2() -> None:
    # CARD_AUTH_SCHEME set explicitly: this test is about rpc_auth resolution,
    # not the oauth2/card-auth-reuse cross-check (see the dedicated tests for
    # that below).
    env = raw_env(
        AUTH_SCHEME="oauth2", OAUTH_ENDPOINT="agent-oauth-endpoint", CARD_AUTH_SCHEME="none"
    )
    config = load_config(env)
    assert config.rpc_auth == OAuth2Auth(
        oauth_endpoint="agent-oauth-endpoint", dapr_http_port="3500"
    )


def test_auth_scheme_invalid() -> None:
    env = raw_env(AUTH_SCHEME="digest")
    with pytest.raises(ConfigError, match="AUTH_SCHEME"):
        load_config(env)


def test_card_auth_absent_by_default() -> None:
    config = load_config(raw_env())
    assert config.card_auth is None


def test_card_auth_basic() -> None:
    env = raw_env(CARD_AUTH_SCHEME="basic", CARD_AUTH_USERNAME="bob", CARD_AUTH_PASSWORD="pw")
    config = load_config(env)
    assert config.card_auth == BasicAuth(username="bob", password="pw")


def test_card_auth_oauth2_is_rejected() -> None:
    env = raw_env(CARD_AUTH_SCHEME="oauth2")
    with pytest.raises(ConfigError, match="no oauth2 for the card path"):
        load_config(env)


def test_include_history_default_false() -> None:
    assert load_config(raw_env()).include_history is False


def test_include_history_true() -> None:
    assert load_config(raw_env(INCLUDE_HISTORY="true")).include_history is True


@pytest.mark.parametrize(
    ("raw", "expected_ms"),
    [("30s", 30_000), ("1m", 60_000), ("500ms", 500), ("2000", 2000)],
)
def test_timeout_units(raw: str, expected_ms: int) -> None:
    assert load_config(raw_env(TIMEOUT=raw)).timeout_ms == expected_ms


def test_timeout_invalid_format() -> None:
    with pytest.raises(ConfigError, match="TIMEOUT"):
        load_config(raw_env(TIMEOUT="soon"))


def test_port_out_of_range() -> None:
    with pytest.raises(ConfigError, match="PORT"):
        load_config(raw_env(PORT="99999"))


# -- PARAMETERS env-builtin/$ENV security guard (boot-time) -----------------


def test_parameters_string_form_rejects_env_variable() -> None:
    env = raw_env(PARAMETERS=json.dumps("$ENV.AUTH_TOKEN"))
    with pytest.raises(ConfigError, match=r"\$ENV"):
        load_config(env)


def test_parameters_string_form_rejects_env_builtin() -> None:
    env = raw_env(PARAMETERS=json.dumps("env.AUTH_TOKEN"))
    with pytest.raises(ConfigError, match="'env'"):
        load_config(env)


def test_parameters_object_form_rejects_env_access_in_one_expression() -> None:
    env = raw_env(
        PARAMETERS=json.dumps({"safe": "${ .input.msg }", "leaky": "${ env.AUTH_TOKEN }"})
    )
    with pytest.raises(ConfigError, match=r"PARAMETERS\.leaky"):
        load_config(env)


def test_parameters_object_form_rejects_env_access_nested_two_levels_deep() -> None:
    """Regression test at the config-load boundary for the object-form
    restructuring: `env`/`$ENV` access nested inside objects/arrays must
    still be caught, not just at the top level."""
    env = raw_env(PARAMETERS=json.dumps({"message": {"parts": [{"text": "${ env.AUTH_TOKEN }"}]}}))
    with pytest.raises(ConfigError, match=r"PARAMETERS\.message\.parts\[0\]\.text"):
        load_config(env)


def test_parameters_object_form_allows_env_literal_key() -> None:
    env = raw_env(PARAMETERS=json.dumps({"message": "${ .input.msg }", "env": "${ {env: .foo} }"}))
    config = load_config(env)
    assert config.parameters == ObjectParameters(
        value={"message": "${ .input.msg }", "env": "${ {env: .foo} }"}
    )


# -- oauth2 RPC auth cannot be silently reused for the card fetch -----------


def test_oauth2_rpc_auth_without_card_auth_scheme_is_rejected_for_agent_card_target() -> None:
    env = raw_env(AUTH_SCHEME="oauth2", OAUTH_ENDPOINT="agent-oauth-endpoint")
    with pytest.raises(ConfigError, match="CARD_AUTH_SCHEME"):
        load_config(env)


def test_oauth2_rpc_auth_with_explicit_card_auth_scheme_none_is_allowed() -> None:
    env = raw_env(
        AUTH_SCHEME="oauth2",
        OAUTH_ENDPOINT="agent-oauth-endpoint",
        CARD_AUTH_SCHEME="none",
    )
    config = load_config(env)
    assert config.rpc_auth == OAuth2Auth(
        oauth_endpoint="agent-oauth-endpoint", dapr_http_port="3500"
    )
    assert config.card_auth == NoAuth()


def test_oauth2_rpc_auth_with_explicit_card_auth_scheme_basic_is_allowed() -> None:
    env = raw_env(
        AUTH_SCHEME="oauth2",
        OAUTH_ENDPOINT="agent-oauth-endpoint",
        CARD_AUTH_SCHEME="basic",
        CARD_AUTH_USERNAME="bob",
        CARD_AUTH_PASSWORD="pw",
    )
    config = load_config(env)
    assert config.card_auth == BasicAuth(username="bob", password="pw")


def test_oauth2_rpc_auth_without_card_auth_scheme_is_allowed_for_server_target() -> None:
    """No card fetch happens at all for a `ServerTarget`, so there's nothing
    to cross-check -- oauth2 RPC auth alone must not raise."""
    env = raw_env(AUTH_SCHEME="oauth2", OAUTH_ENDPOINT="agent-oauth-endpoint")
    del env["AGENT_CARD_URL"]
    env["SERVER_URL"] = "https://server.example.com"
    config = load_config(env)
    assert config.rpc_auth == OAuth2Auth(
        oauth_endpoint="agent-oauth-endpoint", dapr_http_port="3500"
    )
    assert config.card_auth is None
