"""Unit coverage for `runner.run`'s malformed-`StreamResponse` branches --
both are agent protocol non-conformance, not this runner's own bug, and were
previously exercised only indirectly (or not at all)."""

from __future__ import annotations

from collections.abc import AsyncIterator
from typing import Any

import pytest
from a2a.types import GetTaskRequest, SendMessageRequest, StreamResponse, Task

from dws_call_a2a.errors import UpstreamError
from dws_call_a2a.runner import run
from test.conftest import FakeClient, make_config


class _EmptyStreamClient:
    """A `send_message` that produces zero `StreamResponse`s at all -- the
    other malformed-response shape `_run_send_message` guards against,
    distinct from `FakeClient`'s always-exactly-one-response behavior."""

    async def send_message(
        self, request: SendMessageRequest, **_: Any
    ) -> AsyncIterator[StreamResponse]:
        return
        yield  # pragma: no cover -- makes this an async generator; never reached

    async def get_task(self, request: GetTaskRequest, **_: Any) -> Task:
        raise AssertionError("not used in this test")  # pragma: no cover


_VALID_MESSAGE_INPUT = {"message": {"parts": [{"kind": "text", "text": "hi"}]}}


@pytest.mark.asyncio
async def test_send_message_response_with_neither_task_nor_message_is_upstream_error() -> None:
    config = make_config(method="message/send")
    client = FakeClient()  # default StreamResponse() has neither field set
    with pytest.raises(UpstreamError, match="neither a task nor a message"):
        await run(config, client, _VALID_MESSAGE_INPUT, headers={})


@pytest.mark.asyncio
async def test_send_message_empty_stream_is_upstream_error() -> None:
    config = make_config(method="message/send")
    client = _EmptyStreamClient()
    with pytest.raises(UpstreamError, match="produced no response"):
        await run(config, client, _VALID_MESSAGE_INPUT, headers={})
