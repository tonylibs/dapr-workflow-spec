"""Tier (c): drives this runner as a client against a real agent built from
official `a2a-sdk` server components (`sdk_agent.py`). This is the oracle that
catches a misreading `test/fake_server`'s hand-rolled mock could otherwise
share with this runner's own client code (ADR 0004 Consequences).

Runs entirely in-process via `httpx.ASGITransport` -- no separate server
process, no network -- but every request/response byte is produced by the
SDK's real server-side (de)serialization code, not by test code.
"""

from __future__ import annotations

import httpx
import pytest
from fastapi import FastAPI

from dws_call_a2a.config import AgentCardTarget, Config, ObjectParameters, ParametersSpec
from dws_call_a2a.main import create_app
from test.conformance.sdk_agent import build_sdk_agent_app
from test.conftest import make_config


def _config_for(
    *, method: str = "message/send", parameters: ParametersSpec | None = None
) -> Config:
    resolved_parameters = parameters or ObjectParameters(value={"message": "${ . }"})
    return make_config(
        target=AgentCardTarget(
            agent_card_url="https://conformance-agent.example.com", agent_card_sha256=None
        ),
        method=method,
        parameters=resolved_parameters,
    )


async def _call(config: Config, sdk_app: FastAPI, payload: dict | None) -> httpx.Response:
    sdk_transport = httpx.ASGITransport(app=sdk_app)
    runner_app = create_app(config, transport=sdk_transport)
    async with runner_app.router.lifespan_context(runner_app):
        runner_transport = httpx.ASGITransport(app=runner_app)
        async with httpx.AsyncClient(
            transport=runner_transport, base_url="http://runner"
        ) as client:
            if payload is None:
                return await client.post("/run", content=b"")
            return await client.post("/run", json=payload)


@pytest.mark.conformance
@pytest.mark.asyncio
async def test_message_send_against_real_sdk_server_legacy_dialect() -> None:
    """`enable_v0_3_compat=True` on the server side: proves this runner's
    `is_legacy_version`-driven dialect selection picks the same transport the
    real SDK expects for a `protocolVersion: "0.3.0"` interface."""
    sdk_app = build_sdk_agent_app(enable_v0_3_compat=True)
    config = _config_for()
    response = await _call(config, sdk_app, {"parts": [{"kind": "text", "text": "start please"}]})
    assert response.status_code == 200
    assert response.json()["status"]["state"] == "input-required"


@pytest.mark.conformance
@pytest.mark.asyncio
async def test_multi_turn_against_real_sdk_server() -> None:
    sdk_app = build_sdk_agent_app(enable_v0_3_compat=True)
    config = _config_for()

    first = await _call(config, sdk_app, {"parts": [{"kind": "text", "text": "start please"}]})
    task_id = first.json()["id"]

    second = await _call(
        config, sdk_app, {"taskId": task_id, "parts": [{"kind": "text", "text": "blue"}]}
    )
    assert second.status_code == 200
    body = second.json()
    assert body["status"]["state"] == "completed"
    assert body["artifacts"][0]["parts"][0]["text"] == "received: blue"


@pytest.mark.conformance
@pytest.mark.asyncio
async def test_auth_required_against_real_sdk_server() -> None:
    sdk_app = build_sdk_agent_app(enable_v0_3_compat=True)
    config = _config_for()
    response = await _call(
        config, sdk_app, {"parts": [{"kind": "text", "text": "trigger-auth-required"}]}
    )
    assert response.status_code == 200
    assert response.json()["status"]["state"] == "auth-required"


@pytest.mark.conformance
@pytest.mark.asyncio
async def test_history_stripped_against_real_sdk_server() -> None:
    sdk_app = build_sdk_agent_app(enable_v0_3_compat=True)
    config = _config_for()
    response = await _call(config, sdk_app, {"parts": [{"kind": "text", "text": "start please"}]})
    assert "history" not in response.json()


@pytest.mark.conformance
@pytest.mark.asyncio
async def test_tasks_get_against_real_sdk_server() -> None:
    sdk_app = build_sdk_agent_app(enable_v0_3_compat=True)
    send_config = _config_for()
    first = await _call(send_config, sdk_app, {"parts": [{"kind": "text", "text": "start please"}]})
    task_id = first.json()["id"]

    get_config = _config_for(
        method="tasks/get", parameters=ObjectParameters(value={"id": "${ .taskId }"})
    )
    response = await _call(get_config, sdk_app, {"taskId": task_id})
    assert response.status_code == 200
    assert response.json()["status"]["state"] == "input-required"


@pytest.mark.conformance
@pytest.mark.asyncio
async def test_modern_v1_0_dialect_against_real_sdk_server() -> None:
    """`enable_v0_3_compat=False`: the server speaks the modern protobuf-JSON
    dialect (`method: "SendMessage"`, states as `TASK_STATE_*`). This runner
    must still hand the workflow the same lowercase wire-form JSON regardless
    -- proving the ADR 0004 Decision 3 normalization holds for *both*
    dialects, not just the legacy one every other test here exercises."""
    sdk_app = build_sdk_agent_app(enable_v0_3_compat=False)
    config = _config_for()
    response = await _call(config, sdk_app, {"parts": [{"kind": "text", "text": "start please"}]})
    assert response.status_code == 200
    assert response.json()["status"]["state"] == "input-required"
