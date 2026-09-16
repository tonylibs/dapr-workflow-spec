from __future__ import annotations

import pytest
from a2a.types import Message, Part, Role, Task, TaskState, TaskStatus

from dws_call_a2a.errors import RequestValidationError
from dws_call_a2a.wire import (
    build_get_task_request,
    build_send_message_request,
    shape_message_result,
    shape_task_result,
)


def test_build_send_message_request_injects_default_role_and_message_id() -> None:
    evaluated = {"message": {"parts": [{"kind": "text", "text": "hi"}]}}
    request = build_send_message_request(
        evaluated, task_name="notifyAgent", workflow_instance_id="wf-1", iteration_index="0"
    )
    assert request.message.role == Role.ROLE_USER
    assert request.message.message_id  # non-empty
    assert request.message.parts[0].text == "hi"


def test_build_send_message_request_author_supplied_message_id_wins() -> None:
    evaluated = {
        "message": {"messageId": "author-chosen-id", "parts": [{"kind": "text", "text": "hi"}]}
    }
    request = build_send_message_request(
        evaluated, task_name="notifyAgent", workflow_instance_id="wf-1", iteration_index="0"
    )
    assert request.message.message_id == "author-chosen-id"


def test_build_send_message_request_author_supplied_role_wins() -> None:
    evaluated = {"message": {"role": "agent", "parts": [{"kind": "text", "text": "hi"}]}}
    request = build_send_message_request(
        evaluated, task_name="notifyAgent", workflow_instance_id="wf-1", iteration_index="0"
    )
    assert request.message.role == Role.ROLE_AGENT


def test_build_send_message_request_converts_configuration_when_present() -> None:
    """`parsed.configuration` is `None` unless the author's PARAMETERS
    included a `configuration` object -- exercise the branch that converts
    and attaches it to the core pb2 request when it *is* present."""
    evaluated = {
        "message": {"parts": [{"kind": "text", "text": "hi"}]},
        "configuration": {"blocking": True, "acceptedOutputModes": ["text"]},
    }
    request = build_send_message_request(
        evaluated, task_name="notifyAgent", workflow_instance_id="wf-1", iteration_index="0"
    )
    assert request.HasField("configuration")
    # The compat->core conversion inverts `blocking` into `return_immediately`
    # (there is no `blocking` field on the core pb2 message).
    assert request.configuration.return_immediately is False
    assert list(request.configuration.accepted_output_modes) == ["text"]


def test_build_send_message_request_guard_rejects_missing_message() -> None:
    with pytest.raises(RequestValidationError, match="message/send"):
        build_send_message_request(
            {}, task_name="t", workflow_instance_id=None, iteration_index=None
        )


def test_build_send_message_request_guard_rejects_missing_parts() -> None:
    with pytest.raises(RequestValidationError):
        build_send_message_request(
            {"message": {}}, task_name="t", workflow_instance_id=None, iteration_index=None
        )


def test_build_get_task_request_forces_history_length_zero_by_default() -> None:
    request = build_get_task_request({"id": "task-1"}, include_history=False)
    assert request.id == "task-1"
    assert request.history_length == 0


def test_build_get_task_request_include_history_lets_author_value_through() -> None:
    request = build_get_task_request({"id": "task-1", "historyLength": 5}, include_history=True)
    assert request.history_length == 5


def test_build_get_task_request_guard_rejects_missing_id() -> None:
    with pytest.raises(RequestValidationError, match="tasks/get"):
        build_get_task_request({}, include_history=False)


def _make_task(state: TaskState, *, with_history: bool = False) -> Task:
    task = Task(id="task-1", context_id="ctx-1")
    task.status.CopyFrom(TaskStatus(state=state))
    if with_history:
        task.history.append(Message(message_id="m1", role=Role.ROLE_USER, parts=[Part(text="hi")]))
    return task


def test_shape_task_result_strips_history_by_default() -> None:
    task = _make_task(TaskState.TASK_STATE_COMPLETED, with_history=True)
    result = shape_task_result(task, include_history=False)
    assert "history" not in result


def test_shape_task_result_keeps_history_when_include_history() -> None:
    task = _make_task(TaskState.TASK_STATE_COMPLETED, with_history=True)
    result = shape_task_result(task, include_history=True)
    assert "history" in result
    assert len(result["history"]) == 1


@pytest.mark.parametrize(
    ("core_state", "wire_state"),
    [
        (TaskState.TASK_STATE_SUBMITTED, "submitted"),
        (TaskState.TASK_STATE_WORKING, "working"),
        (TaskState.TASK_STATE_COMPLETED, "completed"),
        (TaskState.TASK_STATE_FAILED, "failed"),
        (TaskState.TASK_STATE_CANCELED, "canceled"),
        (TaskState.TASK_STATE_INPUT_REQUIRED, "input-required"),
        (TaskState.TASK_STATE_REJECTED, "rejected"),
        (TaskState.TASK_STATE_AUTH_REQUIRED, "auth-required"),
        (TaskState.TASK_STATE_UNSPECIFIED, "unknown"),
    ],
)
def test_shape_task_result_state_is_lowercase_wire_form(
    core_state: TaskState, wire_state: str
) -> None:
    """ADR 0004 Decision 3: the wire form the workflow's `switch` sees is
    lowercase, including the "unknown" state the A2A prose never mentions."""
    task = _make_task(core_state)
    result = shape_task_result(task, include_history=False)
    assert result["status"]["state"] == wire_state


def test_input_required_and_auth_required_are_returned_not_raised() -> None:
    """ADR 0004 Decision 6: these are successful results, not step failures --
    `shape_task_result` itself never raises for any task state."""
    for state in (TaskState.TASK_STATE_INPUT_REQUIRED, TaskState.TASK_STATE_AUTH_REQUIRED):
        result = shape_task_result(_make_task(state), include_history=False)
        assert result["status"]["state"] in ("input-required", "auth-required")


def test_shape_message_result_has_no_history_field_to_strip() -> None:
    message = Message(message_id="m1", role=Role.ROLE_AGENT, parts=[Part(text="hello")])
    result = shape_message_result(message)
    assert result["kind"] == "message"
    assert "history" not in result
    assert result["role"] == "agent"
