"""The narrow interface `runner.py`/`app_state.py` actually depend on.

Typed as a `Protocol` rather than the concrete `a2a.client.BaseClient` class
so tests can supply a lightweight duck-typed double (`test/conftest.py`'s
`FakeClient`) instead of constructing a real SDK client -- the DI seam the
task brief asked for between application logic and the a2a-sdk transport.
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from typing import Protocol

from a2a.client import ClientCallContext
from a2a.types import GetTaskRequest, SendMessageRequest, StreamResponse, Task


class RpcClient(Protocol):
    def send_message(
        self, request: SendMessageRequest, *, context: ClientCallContext | None = None
    ) -> AsyncIterator[StreamResponse]: ...

    async def get_task(
        self, request: GetTaskRequest, *, context: ClientCallContext | None = None
    ) -> Task: ...
