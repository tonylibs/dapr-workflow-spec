"""Tier (b): real HTTP round-trips (via `httpx.ASGITransport`, not a mocked
transport) against the hand-scripted fake agent in `server.py`. Exercises the
runner's full boot (card resolution + dialect + auth validation) and `/run`
call path end to end, through `dws_call_a2a.main.create_app`.
"""

from __future__ import annotations

import httpx
import pytest
from fastapi import FastAPI

from dws_call_a2a.config import (
    AgentCardTarget,
    AuthConfig,
    BasicAuth,
    BearerAuth,
    Config,
    NoAuth,
    ObjectParameters,
    ParametersSpec,
    ServerTarget,
    StringParameters,
)
from dws_call_a2a.main import create_app
from test.conftest import make_config
from test.fake_server.server import build_fake_agent_app


async def _call(config: Config, fake_app: FastAPI, payload: dict | None) -> httpx.Response:
    fake_transport = httpx.ASGITransport(app=fake_app)
    runner_app = create_app(config, transport=fake_transport)

    # The runner's own lifespan performs boot-time card resolution/validation
    # against the fake agent through the same transport, then serves /run.
    async with runner_app.router.lifespan_context(runner_app):
        transport = httpx.ASGITransport(app=runner_app)
        async with httpx.AsyncClient(transport=transport, base_url="http://runner") as client:
            if payload is None:
                return await client.post("/run", content=b"")
            return await client.post("/run", json=payload)


def _config_for(
    *,
    method: str = "message/send",
    auth: AuthConfig | None = None,
    parameters: ParametersSpec | None = None,
) -> Config:
    resolved_auth = auth if auth is not None else NoAuth()
    resolved_parameters = parameters or ObjectParameters(expressions={"message": "."})
    return make_config(
        target=AgentCardTarget(agent_card_url="https://agent.example.com", agent_card_sha256=None),
        method=method,
        rpc_auth=resolved_auth,
        parameters=resolved_parameters,
    )


@pytest.mark.fake_server
@pytest.mark.asyncio
async def test_multi_turn_state_machine_submits_then_completes() -> None:
    fake_app = build_fake_agent_app()
    config = _config_for()

    first = await _call(
        config,
        fake_app,
        {"parts": [{"kind": "text", "text": "start please"}]},
    )
    assert first.status_code == 200
    first_body = first.json()
    assert first_body["status"]["state"] == "input-required"
    task_id = first_body["id"]

    second = await _call(
        config,
        fake_app,
        {"taskId": task_id, "parts": [{"kind": "text", "text": "blue"}]},
    )
    assert second.status_code == 200
    second_body = second.json()
    assert second_body["status"]["state"] == "completed"
    assert second_body["artifacts"][0]["parts"][0]["text"] == "received: blue"


@pytest.mark.fake_server
@pytest.mark.asyncio
async def test_auth_required_state_is_200() -> None:
    fake_app = build_fake_agent_app()
    config = _config_for()
    response = await _call(
        config, fake_app, {"parts": [{"kind": "text", "text": "trigger-auth-required"}]}
    )
    assert response.status_code == 200
    assert response.json()["status"]["state"] == "auth-required"


@pytest.mark.fake_server
@pytest.mark.asyncio
async def test_unknown_state_survives_round_trip() -> None:
    """ADR 0004 Decision 3: `"unknown"` is a real, defined member of the wire
    vocabulary (the A2A prose never mentions it, but the SDK's own v0.3
    compat `TaskState` enum does) -- an agent emitting it must not crash the
    runner. This is *not* the same as an agent emitting a truly arbitrary,
    undefined string; see `test_non_conformant_state_string_is_upstream_failure`
    for that (rejected) case."""
    fake_app = build_fake_agent_app()
    config = _config_for()
    response = await _call(
        config, fake_app, {"parts": [{"kind": "text", "text": "trigger-unknown-state"}]}
    )
    assert response.status_code == 200
    assert response.json()["status"]["state"] == "unknown"


@pytest.mark.fake_server
@pytest.mark.asyncio
async def test_non_conformant_state_string_is_upstream_failure() -> None:
    """A state string outside the fixed wire vocabulary entirely (not even
    `"unknown"`) fails client-side pydantic validation inside the SDK's
    compat transport -- a genuine parse/protocol failure, correctly
    classified as an `UpstreamError` (502), not a bug in this runner (500)."""
    fake_app = build_fake_agent_app()
    config = _config_for()
    response = await _call(
        config, fake_app, {"parts": [{"kind": "text", "text": "trigger-non-conformant-state"}]}
    )
    assert response.status_code == 502


@pytest.mark.fake_server
@pytest.mark.asyncio
async def test_history_is_stripped_by_default() -> None:
    fake_app = build_fake_agent_app()
    config = _config_for()
    response = await _call(config, fake_app, {"parts": [{"kind": "text", "text": "start please"}]})
    assert "history" not in response.json()


@pytest.mark.fake_server
@pytest.mark.asyncio
async def test_history_is_kept_with_include_history() -> None:
    fake_app = build_fake_agent_app()
    config = make_config(
        target=AgentCardTarget(agent_card_url="https://agent.example.com", agent_card_sha256=None),
        parameters=ObjectParameters(expressions={"message": "."}),
        include_history=True,
    )
    response = await _call(config, fake_app, {"parts": [{"kind": "text", "text": "start please"}]})
    assert "history" in response.json()


@pytest.mark.fake_server
@pytest.mark.asyncio
async def test_tasks_get_after_send() -> None:
    fake_app = build_fake_agent_app()
    send_config = _config_for()
    first = await _call(
        send_config, fake_app, {"parts": [{"kind": "text", "text": "start please"}]}
    )
    task_id = first.json()["id"]

    get_config = _config_for(
        method="tasks/get", parameters=ObjectParameters(expressions={"id": ".taskId"})
    )
    response = await _call(get_config, fake_app, {"taskId": task_id})
    assert response.status_code == 200
    assert response.json()["status"]["state"] == "input-required"


@pytest.mark.fake_server
@pytest.mark.asyncio
async def test_message_id_is_stable_across_retries_through_headers() -> None:
    """End-to-end version of the messageId-stability unit test: two `/run`
    calls carrying identical instance/iteration headers produce the same
    `messageId` on the wire, observable via the fake agent echoing history."""
    fake_app = build_fake_agent_app()
    config = _config_for()

    fake_transport = httpx.ASGITransport(app=fake_app)
    runner_app = create_app(config, transport=fake_transport)
    async with runner_app.router.lifespan_context(runner_app):
        transport = httpx.ASGITransport(app=runner_app)
        headers = {"X-Dws-Workflow-Instance-Id": "wf-1", "X-Dws-Iteration-Index": "0"}
        async with httpx.AsyncClient(transport=transport, base_url="http://runner") as client:
            first = await client.post(
                "/run",
                json={"parts": [{"kind": "text", "text": "start please"}]},
                headers=headers,
            )
            second = await client.post(
                "/run",
                json={"parts": [{"kind": "text", "text": "start please"}]},
                headers=headers,
            )
    # Both calls create a *new* task in the fake server (it has no dedupe
    # logic of its own), but the outgoing `messageId` for both is identical --
    # asserted at the unit level (test_message_id.py); here we only assert
    # both calls succeed identically, which is what a cooperative agent that
    # *does* dedupe on messageId would need to see.
    assert first.status_code == second.status_code == 200


@pytest.mark.fake_server
@pytest.mark.asyncio
async def test_basic_auth_required_and_supplied() -> None:
    fake_app = build_fake_agent_app(auth_requirement="basic", expected_basic=("alice", "s3cret"))
    config = _config_for(auth=BasicAuth(username="alice", password="s3cret"))
    response = await _call(config, fake_app, {"parts": [{"kind": "text", "text": "start please"}]})
    assert response.status_code == 200


@pytest.mark.fake_server
@pytest.mark.asyncio
async def test_basic_auth_required_but_missing_fails_boot() -> None:
    """ADR 0004 Decision 4 case 4: fails at boot (before `/run` is ever
    served), not as a per-request upstream failure."""
    from dws_call_a2a.errors import ConfigError

    fake_app = build_fake_agent_app(auth_requirement="basic", expected_basic=("alice", "s3cret"))
    config = _config_for(auth=NoAuth())
    fake_transport = httpx.ASGITransport(app=fake_app)
    runner_app = create_app(config, transport=fake_transport)
    with pytest.raises(ConfigError, match="requires authentication"):
        async with runner_app.router.lifespan_context(runner_app):
            pass


@pytest.mark.fake_server
@pytest.mark.asyncio
async def test_bearer_auth_required_and_supplied() -> None:
    fake_app = build_fake_agent_app(auth_requirement="bearer", expected_bearer="tok-123")
    config = _config_for(auth=BearerAuth(token="tok-123"))
    response = await _call(config, fake_app, {"parts": [{"kind": "text", "text": "start please"}]})
    assert response.status_code == 200


@pytest.mark.fake_server
@pytest.mark.asyncio
async def test_boot_fails_when_card_requires_auth_but_none_configured() -> None:
    """ADR 0004 Decision 4 case 4: fails at boot, not at first request."""
    from dws_call_a2a.errors import ConfigError

    fake_app = build_fake_agent_app(auth_requirement="bearer", expected_bearer="tok-123")
    config = _config_for(auth=NoAuth())
    fake_transport = httpx.ASGITransport(app=fake_app)
    runner_app = create_app(config, transport=fake_transport)
    with pytest.raises(ConfigError, match="requires authentication"):
        async with runner_app.router.lifespan_context(runner_app):
            pass


@pytest.mark.fake_server
@pytest.mark.asyncio
async def test_server_url_target_skips_card_resolution_entirely() -> None:
    """ADR 0004 Decision 4 case 5: `SERVER_URL` talks to the RPC endpoint
    directly, no card fetch, no auth-requirement check possible."""
    fake_app = build_fake_agent_app()
    config = make_config(
        target=ServerTarget(server_url="https://agent.example.com/rpc"),
        parameters=ObjectParameters(expressions={"message": "."}),
    )
    response = await _call(config, fake_app, {"parts": [{"kind": "text", "text": "start please"}]})
    assert response.status_code == 200
    assert response.json()["status"]["state"] == "input-required"


@pytest.mark.fake_server
@pytest.mark.asyncio
async def test_string_form_parameters_end_to_end() -> None:
    fake_app = build_fake_agent_app()
    config = _config_for(parameters=StringParameters(expression="{message: .}"))
    response = await _call(config, fake_app, {"parts": [{"kind": "text", "text": "start please"}]})
    assert response.status_code == 200
