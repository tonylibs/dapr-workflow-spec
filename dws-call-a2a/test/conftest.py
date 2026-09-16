from __future__ import annotations

import json
from collections.abc import AsyncIterator
from typing import Any

import pytest
from a2a.types import Message, StreamResponse, Task

from dws_call_a2a.config import AgentCardTarget, Config, NoAuth, ObjectParameters


def make_config(**overrides: Any) -> Config:
    """A minimally-valid `Config` for unit tests, with sane overridable defaults."""
    defaults: dict[str, Any] = {
        "port": 8080,
        "task": "notifyAgent",
        "target": AgentCardTarget(
            agent_card_url="https://agent.example.com", agent_card_sha256=None
        ),
        "method": "message/send",
        "parameters": ObjectParameters(expressions={"message": ".message"}),
        "rpc_auth": NoAuth(),
        "card_auth": None,
        "include_history": False,
        "timeout_ms": 30_000,
    }
    defaults.update(overrides)
    return Config(**defaults)


def raw_env(**overrides: str) -> dict[str, str]:
    """A minimally-valid raw env dict for `load_config`, with sane overridable defaults."""
    defaults = {
        "TASK": "notifyAgent",
        "AGENT_CARD_URL": "https://agent.example.com",
        "METHOD": "message/send",
        "PARAMETERS": json.dumps({"message": ".message"}),
    }
    defaults.update(overrides)
    return defaults


class FakeClient:
    """A duck-typed stand-in for `a2a.client.BaseClient` -- `runner.run` only
    calls `send_message`/`get_task`, so a full `BaseClient` isn't needed."""

    def __init__(
        self,
        *,
        send_message_result: Task | Message | None = None,
        get_task_result: Task | None = None,
        raises: Exception | None = None,
    ):
        self._send_message_result = send_message_result
        self._get_task_result = get_task_result
        self._raises = raises
        self.sent_requests: list[Any] = []

    async def send_message(self, request: Any, **_: Any) -> AsyncIterator[StreamResponse]:
        self.sent_requests.append(request)
        if self._raises is not None:
            raise self._raises
        response = StreamResponse()
        if isinstance(self._send_message_result, Task):
            response.task.CopyFrom(self._send_message_result)
        elif isinstance(self._send_message_result, Message):
            response.message.CopyFrom(self._send_message_result)
        yield response

    async def get_task(self, request: Any, **_: Any) -> Task:
        self.sent_requests.append(request)
        if self._raises is not None:
            raise self._raises
        assert self._get_task_result is not None
        return self._get_task_result


@pytest.fixture
def fake_client_factory():
    return FakeClient
