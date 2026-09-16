from __future__ import annotations

import httpx
import pytest
from a2a.types import Message, Part, Role, Task, TaskState, TaskStatus

from dws_call_a2a.app_state import AppState
from dws_call_a2a.main import create_app, get_app_state
from test.conftest import FakeClient, make_config


async def _app_client(app, state: AppState) -> httpx.AsyncClient:
    app.dependency_overrides[get_app_state] = lambda: state
    transport = httpx.ASGITransport(app=app)
    return httpx.AsyncClient(transport=transport, base_url="http://testserver")


@pytest.mark.asyncio
async def test_healthz() -> None:
    app = create_app(make_config(task="notifyAgent"))
    async with await _app_client(
        app, AppState(config=make_config(), client=FakeClient(), httpx_client=httpx.AsyncClient())
    ) as client:
        response = await client.get("/healthz")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "task": "notifyAgent"}


@pytest.mark.asyncio
async def test_run_empty_body_is_treated_as_empty_object() -> None:
    """PARAMETERS here is a fixed literal (no dependency on input fields), so
    an empty `/run` body succeeds -- proving empty-body-as-`{}` doesn't 400
    before the jq evaluation even runs."""
    from dws_call_a2a.config import StringParameters

    task = Task(id="t1", context_id="c1")
    task.status.CopyFrom(TaskStatus(state=TaskState.TASK_STATE_COMPLETED))
    fake_client = FakeClient(send_message_result=task)
    config = make_config(
        parameters=StringParameters(expression='{message: {parts: [{kind: "text", text: "ping"}]}}')
    )
    app = create_app(config)

    async with await _app_client(
        app, AppState(config=config, client=fake_client, httpx_client=httpx.AsyncClient())
    ) as client:
        response = await client.post("/run", content=b"")

    assert response.status_code == 200
    assert response.json()["status"]["state"] == "completed"


@pytest.mark.asyncio
async def test_run_non_object_body_is_400() -> None:
    config = make_config()
    app = create_app(config)
    async with await _app_client(
        app, AppState(config=config, client=FakeClient(), httpx_client=httpx.AsyncClient())
    ) as client:
        response = await client.post("/run", json=[1, 2, 3])
    assert response.status_code == 400


@pytest.mark.asyncio
async def test_run_invalid_json_body_is_400() -> None:
    config = make_config()
    app = create_app(config)
    async with await _app_client(
        app, AppState(config=config, client=FakeClient(), httpx_client=httpx.AsyncClient())
    ) as client:
        response = await client.post(
            "/run", content=b"{not json", headers={"content-type": "application/json"}
        )
    assert response.status_code == 400


@pytest.mark.asyncio
async def test_run_malformed_parameters_is_400() -> None:
    from dws_call_a2a.config import StringParameters

    config = make_config(parameters=StringParameters(expression=".missing.deeply.nested"))
    app = create_app(config)
    async with await _app_client(
        app, AppState(config=config, client=FakeClient(), httpx_client=httpx.AsyncClient())
    ) as client:
        response = await client.post("/run", json={})
    assert response.status_code == 400


@pytest.mark.asyncio
async def test_run_upstream_failure_is_502() -> None:
    config = make_config()
    fake_client = FakeClient(raises=httpx.ConnectError("connection refused"))
    app = create_app(config)
    async with await _app_client(
        app, AppState(config=config, client=fake_client, httpx_client=httpx.AsyncClient())
    ) as client:
        response = await client.post(
            "/run", json={"message": {"parts": [{"kind": "text", "text": "hi"}]}}
        )
    assert response.status_code == 502


@pytest.mark.asyncio
async def test_run_jsonrpc_error_response_is_502() -> None:
    from a2a.utils.errors import InternalError

    config = make_config()
    fake_client = FakeClient(raises=InternalError("agent blew up"))
    app = create_app(config)
    async with await _app_client(
        app, AppState(config=config, client=fake_client, httpx_client=httpx.AsyncClient())
    ) as client:
        response = await client.post(
            "/run", json={"message": {"parts": [{"kind": "text", "text": "hi"}]}}
        )
    assert response.status_code == 502


@pytest.mark.asyncio
async def test_run_input_required_is_a_200_not_a_failure() -> None:
    task = Task(id="t1", context_id="c1")
    task.status.CopyFrom(TaskStatus(state=TaskState.TASK_STATE_INPUT_REQUIRED))
    fake_client = FakeClient(send_message_result=task)
    config = make_config()
    app = create_app(config)
    async with await _app_client(
        app, AppState(config=config, client=fake_client, httpx_client=httpx.AsyncClient())
    ) as client:
        response = await client.post(
            "/run", json={"message": {"parts": [{"kind": "text", "text": "hi"}]}}
        )
    assert response.status_code == 200
    assert response.json()["status"]["state"] == "input-required"


@pytest.mark.asyncio
async def test_run_bare_message_result() -> None:
    message = Message(message_id="m1", role=Role.ROLE_AGENT, parts=[Part(text="hello")])
    fake_client = FakeClient(send_message_result=message)
    config = make_config()
    app = create_app(config)
    async with await _app_client(
        app, AppState(config=config, client=fake_client, httpx_client=httpx.AsyncClient())
    ) as client:
        response = await client.post(
            "/run", json={"message": {"parts": [{"kind": "text", "text": "hi"}]}}
        )
    assert response.status_code == 200
    body = response.json()
    assert body["kind"] == "message"
    assert body["role"] == "agent"


@pytest.mark.asyncio
async def test_run_tasks_get() -> None:
    task = Task(id="t1", context_id="c1")
    task.status.CopyFrom(TaskStatus(state=TaskState.TASK_STATE_WORKING))
    fake_client = FakeClient(get_task_result=task)
    from dws_call_a2a.config import ObjectParameters

    config = make_config(
        method="tasks/get", parameters=ObjectParameters(expressions={"id": ".taskId"})
    )
    app = create_app(config)
    async with await _app_client(
        app, AppState(config=config, client=fake_client, httpx_client=httpx.AsyncClient())
    ) as client:
        response = await client.post("/run", json={"taskId": "t1"})
    assert response.status_code == 200
    assert response.json()["status"]["state"] == "working"
