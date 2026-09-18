"""A hand-written, scripted fake A2A JSON-RPC agent for tier (b) tests.

Deliberately does **not** import anything from `a2a.server.*` or use any
a2a-sdk type to build responses -- it speaks raw JSON-RPC 2.0 dicts in the
legacy (v0.3-compatible) wire vocabulary by hand. This is intentional: tier
(c)'s conformance job exists specifically because a hand-written mock can
share the runner author's own misreading of the protocol and go green for
the wrong reasons (ADR 0004 Consequences). This fake server's job is multi-
turn *state-machine* coverage (task lifecycle, resumption, auth-required,
an "unknown" state) that's tedious to script against a real SDK-backed agent
executor; tier (c) is the independent oracle that the wire shapes used here
are actually right.

Publishes a **legacy-shaped** agent card (top-level `url`/`security`/
`securitySchemes`, no `supportedInterfaces`) on purpose, matching what a real,
currently-deployed v0.3 agent publishes -- this also exercises
`a2a.client.card_resolver`'s legacy-card-upgrade path (`_handle_connection_
fields_compatibility` / `_handle_security_compatibility`) rather than only the
already-upgraded shape.
"""

from __future__ import annotations

import base64
import uuid
from typing import Any, Literal

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

AuthRequirement = Literal["none", "basic", "bearer"]


def build_fake_agent_app(
    *,
    auth_requirement: AuthRequirement = "none",
    expected_basic: tuple[str, str] = ("alice", "s3cret"),
    expected_bearer: str = "tok-123",
) -> FastAPI:
    """Builds a fresh fake-agent FastAPI app with its own in-memory task store."""
    app = FastAPI()
    tasks: dict[str, dict[str, Any]] = {}

    @app.get("/.well-known/agent-card.json")
    async def agent_card(request: Request) -> dict[str, Any]:
        base_url = str(request.base_url).rstrip("/")
        card: dict[str, Any] = {
            "name": "fake-agent",
            "description": "hand-scripted fake agent for dws-call-a2a tier (b) tests",
            "version": "1.0.0",
            "url": f"{base_url}/rpc",
            "protocolVersion": "0.3.0",
            "defaultInputModes": ["text"],
            "defaultOutputModes": ["text"],
            "capabilities": {},
            "skills": [],
        }
        if auth_requirement == "basic":
            card["securitySchemes"] = {"basicAuth": {"type": "http", "scheme": "basic"}}
            card["security"] = [{"basicAuth": []}]
        elif auth_requirement == "bearer":
            card["securitySchemes"] = {"bearerAuth": {"type": "http", "scheme": "bearer"}}
            card["security"] = [{"bearerAuth": []}]
        return card

    def _check_auth(request: Request) -> JSONResponse | None:
        if auth_requirement == "none":
            return None
        header = request.headers.get("authorization", "")
        if auth_requirement == "basic":
            expected = (
                "Basic "
                + base64.b64encode(f"{expected_basic[0]}:{expected_basic[1]}".encode()).decode()
            )
            if header != expected:
                return JSONResponse(status_code=401, content={"error": "unauthorized"})
        elif auth_requirement == "bearer":
            if header != f"Bearer {expected_bearer}":
                return JSONResponse(status_code=401, content={"error": "unauthorized"})
        return None

    @app.post("/rpc")
    async def rpc(request: Request) -> JSONResponse:
        unauthorized = _check_auth(request)
        if unauthorized is not None:
            return unauthorized

        body = await request.json()
        request_id = body.get("id")
        method = body.get("method")
        params = body.get("params") or {}

        if method == "message/send":
            return JSONResponse(_handle_send_message(tasks, request_id, params))
        if method == "tasks/get":
            return JSONResponse(_handle_get_task(tasks, request_id, params))
        return JSONResponse(
            {
                "jsonrpc": "2.0",
                "id": request_id,
                "error": {"code": -32601, "message": f"method not found: {method}"},
            }
        )

    return app


def _rpc_result(request_id: Any, result: dict[str, Any]) -> dict[str, Any]:
    return {"jsonrpc": "2.0", "id": request_id, "result": result}


def _handle_send_message(
    tasks: dict[str, dict[str, Any]], request_id: Any, params: dict[str, Any]
) -> dict[str, Any]:
    message = params.get("message") or {}
    task_id = message.get("taskId")
    text = _first_text(message)

    if task_id is None:
        # New task. Special trigger texts drive the scripted state machine.
        task_id = str(uuid.uuid4())
        context_id = str(uuid.uuid4())
        if text == "trigger-auth-required":
            state = "auth-required"
            status_message = _agent_text_message(task_id, "please authenticate out of band")
        elif text == "trigger-unknown-state":
            # "unknown" is a real, defined member of the v0.3 wire vocabulary
            # (ADR 0004 Decision 3) -- distinct from a genuinely bogus string.
            state = "unknown"
            status_message = None
        elif text == "trigger-non-conformant-state":
            # A state string *outside* the wire vocabulary entirely -- not
            # even "unknown". A conformant agent would never emit this; it
            # exists to prove the runner fails safely (502) rather than
            # silently accepting garbage.
            state = "totally-not-a-real-state"
            status_message = None
        else:
            state = "input-required"
            status_message = _agent_text_message(task_id, "what is your favorite color?")

        tasks[task_id] = {
            "id": task_id,
            "contextId": context_id,
            "status": {"state": state, **({"message": status_message} if status_message else {})},
            "history": [message, *([status_message] if status_message else [])],
            "artifacts": [],
        }
        return _rpc_result(request_id, tasks[task_id])

    # Resuming an existing task: any second message/send completes it.
    task = tasks[task_id]
    task["status"] = {"state": "completed"}
    task["history"] = [*task["history"], message]
    task["artifacts"] = [
        {
            "artifactId": str(uuid.uuid4()),
            "name": "answer",
            "parts": [{"kind": "text", "text": f"received: {text}"}],
        }
    ]
    return _rpc_result(request_id, task)


def _handle_get_task(
    tasks: dict[str, dict[str, Any]], request_id: Any, params: dict[str, Any]
) -> dict[str, Any]:
    task_id: str = params["id"]
    history_length = params.get("historyLength")
    task = dict(tasks[task_id])

    if history_length is None or history_length > 0:
        task["history"] = task.get("history", [])[: history_length if history_length else None]
    else:
        task.pop("history", None)

    return _rpc_result(request_id, task)


def _first_text(message: dict[str, Any]) -> str | None:
    for part in message.get("parts", []):
        if part.get("kind") == "text":
            return part.get("text")
    return None


def _agent_text_message(task_id: str, text: str) -> dict[str, Any]:
    return {
        "kind": "message",
        "messageId": str(uuid.uuid4()),
        "role": "agent",
        "taskId": task_id,
        "parts": [{"kind": "text", "text": text}],
    }
