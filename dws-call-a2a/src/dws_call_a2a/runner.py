"""Executes one `/run` call: evaluate PARAMETERS, build the JSON-RPC request,
call the agent, and shape the result.

`input-required`/`auth-required` task states are returned as **successful**
results (ADR 0004 Decision 6) -- only a genuine transport/protocol failure
(a JSON-RPC error response, a connection failure, a timeout) is an
`UpstreamError`. There is no `OUTPUT=replace|merge` knob here: `A2AArguments`
carries no data-flow output concept in the OWS schema (unlike the HTTP-shaped
call kinds), and Decision 6 already specifies a *fixed* runner policy for
result shaping (history-stripped JSON-RPC result, always). Inventing a
replace/merge choice on top of that would contradict the fixed policy, so this
runner always returns the shaped result as the new workflow data.
"""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

import httpx
import pydantic
from a2a.utils.errors import A2AError

from dws_call_a2a.config import Config
from dws_call_a2a.errors import UpstreamError
from dws_call_a2a.jq_eval import evaluate_parameters
from dws_call_a2a.message_id import ITERATION_INDEX_HEADER, WORKFLOW_INSTANCE_ID_HEADER
from dws_call_a2a.protocols import RpcClient
from dws_call_a2a.wire import (
    build_get_task_request,
    build_send_message_request,
    shape_message_result,
    shape_task_result,
)


async def run(
    config: Config, client: RpcClient, input_data: Any, headers: Mapping[str, str]
) -> dict[str, Any]:
    evaluated = evaluate_parameters(config.parameters, input_data)

    try:
        if config.method == "message/send":
            return await _run_send_message(config, client, evaluated, headers)
        return await _run_get_task(config, client, evaluated)
    except (A2AError, httpx.HTTPError) as exc:
        raise UpstreamError(f"agent call failed: {exc}") from exc
    except pydantic.ValidationError as exc:
        # A response that fails to parse against the wire vocabulary (e.g. a
        # task state string outside the fixed enum) is a genuine protocol
        # non-conformance on the agent's side, not a bug in this runner.
        raise UpstreamError(f"agent response failed protocol validation: {exc}") from exc


async def _run_send_message(
    config: Config,
    client: RpcClient,
    evaluated: dict[str, Any],
    headers: Mapping[str, str],
) -> dict[str, Any]:
    request = build_send_message_request(
        evaluated,
        task_name=config.task,
        workflow_instance_id=headers.get(WORKFLOW_INSTANCE_ID_HEADER),
        iteration_index=headers.get(ITERATION_INDEX_HEADER),
    )

    async for stream_response in client.send_message(request):
        if stream_response.HasField("task"):
            return shape_task_result(stream_response.task, include_history=config.include_history)
        if stream_response.HasField("message"):
            return shape_message_result(stream_response.message)
        raise UpstreamError("agent's message/send response carried neither a task nor a message")

    raise UpstreamError("agent's message/send call produced no response")


async def _run_get_task(
    config: Config, client: RpcClient, evaluated: dict[str, Any]
) -> dict[str, Any]:
    request = build_get_task_request(evaluated, include_history=config.include_history)
    task = await client.get_task(request)
    return shape_task_result(task, include_history=config.include_history)
