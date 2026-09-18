"""FastAPI application: `POST /run`, `GET /healthz`.

Protocol handling (`wire.py`/`client.py`), application logic (`runner.py`),
and transport concerns (`card.py`/`auth.py`) stay separated; this module only
wires them together via FastAPI's dependency injection so each layer stays
unit-testable without a live agent.
"""

from __future__ import annotations

import json
import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Annotated, Any

import httpx
from fastapi import Depends, FastAPI, Request, Response
from fastapi.responses import JSONResponse

from dws_call_a2a.app_state import AppState, build_app_state, close_app_state
from dws_call_a2a.config import Config, load_config
from dws_call_a2a.errors import RequestValidationError, UpstreamError
from dws_call_a2a.runner import run

logger = logging.getLogger(__name__)


def create_app(
    config: Config | None = None, *, transport: httpx.AsyncBaseTransport | None = None
) -> FastAPI:
    """Builds the FastAPI app. `config`/`transport` are injectable for tests
    (`transport` lets ASGI endpoint tests point the boot-time card fetch and
    RPC client at a fake or conformance agent app without touching the
    network); production boots with both `None`, reading `load_config()`
    against `os.environ` and a real network transport (see `__main__.py`)."""
    resolved_config = config or load_config()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        state = await build_app_state(resolved_config, transport=transport)
        app.state.a2a = state
        try:
            yield
        finally:
            await close_app_state(state)

    app = FastAPI(title="dws-call-a2a", lifespan=lifespan)

    @app.get("/healthz")
    async def healthz() -> dict[str, str]:
        return {"status": "ok", "task": resolved_config.task}

    @app.post("/run")
    async def run_route(
        request: Request,
        state: Annotated[AppState, Depends(get_app_state)],
    ) -> Response:
        body = await request.body()
        try:
            input_data: Any = json.loads(body) if body else {}
        except json.JSONDecodeError:
            return JSONResponse(
                {"task": state.config.task, "error": "request body must be valid JSON"},
                status_code=400,
            )
        if not isinstance(input_data, dict):
            return JSONResponse(
                {"task": state.config.task, "error": "request body must be a JSON object"},
                status_code=400,
            )

        try:
            output = await run(state.config, state.client, input_data, request.headers)
        except RequestValidationError as exc:
            return JSONResponse({"task": state.config.task, "error": str(exc)}, status_code=400)
        except UpstreamError as exc:
            logger.warning("upstream failure for task=%s: %s", state.config.task, exc)
            return JSONResponse({"task": state.config.task, "error": str(exc)}, status_code=502)
        except Exception as exc:  # noqa: BLE001 -- last-resort 500, matching dws-call-openapi
            logger.exception("unexpected failure for task=%s", state.config.task)
            return JSONResponse({"task": state.config.task, "error": str(exc)}, status_code=500)

        return JSONResponse(output)

    return app


def get_app_state(request: Request) -> AppState:
    return request.app.state.a2a  # type: ignore[no-any-return]
