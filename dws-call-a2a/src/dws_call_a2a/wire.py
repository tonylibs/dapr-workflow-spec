"""Bridges the OWS-visible, lowercase-wire-form JSON vocabulary (author-facing
`with.parameters`, and the JSON handed back as the new workflow data) to the
a2a-sdk's modern protobuf-backed `a2a.types`/`Client` I/O contract.

Why two type systems: `a2a-sdk` 1.1.2's top-level `a2a.types` is entirely
protobuf-generated (`SendMessageRequest.message`, `Role.ROLE_USER`,
`TaskState.TASK_STATE_WORKING`, ...) -- see `CLAUDE.md` "ADR verification
notes" for how this was confirmed and why it differs from what
`docs/adr/0004-call-a2a-runner-design.md` reads as. The pydantic models that
match the OWS/JSON-RPC wire vocabulary the ADR describes (`Message.messageId`,
`MessageSendParams`, `TaskQueryParams`, `TaskState.working` == `"working"`,
...) live at `a2a.compat.v0_3.types`, an SDK-internal module explicitly
branded "backward compatibility". This module uses those compat types as the
*validation and construction vocabulary* for request-building (matching what
workflow authors actually write in `with.parameters`), then converts to core
protobuf types via `a2a.compat.v0_3.conversions` to drive the `Client`, and
converts results back the same way to produce OWS-visible JSON.
"""

from __future__ import annotations

from typing import Any

import pydantic
from a2a.compat.v0_3 import conversions
from a2a.compat.v0_3 import types as wire
from a2a.types import GetTaskRequest, Message, SendMessageRequest, Task

from dws_call_a2a.errors import RequestValidationError
from dws_call_a2a.message_id import derive_message_id

_DEFAULT_ROLE = "user"


def build_send_message_request(
    evaluated_params: dict[str, Any],
    *,
    task_name: str,
    workflow_instance_id: str | None,
    iteration_index: str | None,
) -> SendMessageRequest:
    """Validates `evaluated_params` as a `MessageSendParams`-shaped object
    (the guard: ADR 0004 Decision 1's "no schema validation at all" note),
    injecting the OWS-normative `message.messageId`/`message.role` defaults
    when the author didn't supply them, then converts to the core pb2 request
    the `Client` speaks.
    """
    raw_message = evaluated_params.get("message")
    if raw_message is None or isinstance(raw_message, dict):
        message = dict(raw_message or {})
        message.setdefault("role", _DEFAULT_ROLE)
        if not message.get("messageId"):
            message["messageId"] = derive_message_id(
                task_name, workflow_instance_id, iteration_index
            )
        params = {**evaluated_params, "message": message}
    else:
        # A truthy, non-dict `message` (str/int/bool/list, ...) is left
        # exactly as the author's PARAMETERS produced it -- `dict(...)` would
        # raise a bare `ValueError`/`TypeError` here (e.g. `dict("hello")`),
        # which is not a `pydantic.ValidationError` and would escape the
        # `except` below, surfacing as an unhandled 500 instead of the 400
        # this is supposed to be. Passing it through unchanged lets pydantic
        # reject it the normal way.
        params = evaluated_params

    try:
        parsed = wire.MessageSendParams.model_validate(params)
    except pydantic.ValidationError as exc:
        raise RequestValidationError(
            f"PARAMETERS is not a valid message/send params object: {exc}"
        ) from exc

    core_message = conversions.to_core_message(parsed.message)
    request = SendMessageRequest(message=core_message)
    if parsed.configuration is not None:
        request.configuration.CopyFrom(
            conversions.to_core_send_message_configuration(parsed.configuration)
        )
    return request


def build_get_task_request(
    evaluated_params: dict[str, Any], *, include_history: bool
) -> GetTaskRequest:
    """Validates `evaluated_params` as a `TaskQueryParams`-shaped object, then
    forces `historyLength: 0` unless `INCLUDE_HISTORY=true` (ADR 0004
    Decision 6's primary history-suppression mechanism)."""
    params = dict(evaluated_params)
    if not include_history:
        params["historyLength"] = 0

    try:
        parsed = wire.TaskQueryParams.model_validate(params)
    except pydantic.ValidationError as exc:
        raise RequestValidationError(
            f"PARAMETERS is not a valid tasks/get params object: {exc}"
        ) from exc

    # `history_length` is an optional protobuf field: passing it at all marks
    # it present (`HasField` True) even when the value is 0, and a present
    # `historyLength: 0` tells the agent to omit history explicitly. Only
    # pass it when there's an actual value to send -- `include_history=False`
    # forced one above via `params["historyLength"] = 0`, so `parsed.
    # history_length` is only `None` here when `include_history=True` and the
    # author's params didn't request a specific length, in which case the
    # field must stay unset so the agent applies its own default instead of
    # being told "send zero history".
    if parsed.history_length is None:
        return GetTaskRequest(id=parsed.id)
    return GetTaskRequest(id=parsed.id, history_length=parsed.history_length)


def shape_task_result(task: Task, *, include_history: bool) -> dict[str, Any]:
    """Converts a core pb2 `Task` result into the OWS-visible, lowercase wire
    JSON, stripping `history` unless `INCLUDE_HISTORY=true` (Decision 6's
    backstop -- `message/send` may return history regardless of any hint)."""
    compat_task = conversions.to_compat_task(task)
    if not include_history:
        compat_task = compat_task.model_copy(update={"history": None})
    return compat_task.model_dump(by_alias=True, exclude_none=True, mode="json")


def shape_message_result(message: Message) -> dict[str, Any]:
    """Converts a core pb2 `Message` result into the OWS-visible wire JSON.

    A bare `Message` result (no `Task` was created) carries no `history`
    field at all, so there is nothing to strip here.
    """
    compat_message = conversions.to_compat_message(message)
    return compat_message.model_dump(by_alias=True, exclude_none=True, mode="json")
