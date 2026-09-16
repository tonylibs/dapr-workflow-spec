from __future__ import annotations

import uuid

from dws_call_a2a.message_id import derive_message_id


def test_stable_across_retries_of_the_same_logical_invocation() -> None:
    """The whole point of ADR 0004 Decision 8: no attempt counter in the
    derivation, so two calls with identical instance/task/iteration inputs
    (simulating a retry) yield the identical id."""
    first = derive_message_id("notifyAgent", "wf-instance-1", "0")
    second = derive_message_id("notifyAgent", "wf-instance-1", "0")
    assert first == second
    # Also a valid RFC 4122 UUID (OWS says "a uuid", not "a random uuid").
    assert uuid.UUID(first).version == 5


def test_distinct_across_iteration_indices() -> None:
    """A `for` iteration index is required, or items sharing a task name
    within one workflow instance would collide (ADR 0004 Decision 8)."""
    first = derive_message_id("notifyAgent", "wf-instance-1", "0")
    second = derive_message_id("notifyAgent", "wf-instance-1", "1")
    assert first != second


def test_distinct_across_workflow_instances() -> None:
    first = derive_message_id("notifyAgent", "wf-instance-1", "0")
    second = derive_message_id("notifyAgent", "wf-instance-2", "0")
    assert first != second


def test_distinct_across_task_names() -> None:
    first = derive_message_id("notifyAgent", "wf-instance-1", "0")
    second = derive_message_id("escalateAgent", "wf-instance-1", "0")
    assert first != second


def test_falls_back_to_random_uuid4_when_instance_id_missing(caplog) -> None:
    first = derive_message_id("notifyAgent", None, "0")
    second = derive_message_id("notifyAgent", None, "0")
    assert first != second
    assert uuid.UUID(first).version == 4
    assert "dedupe disabled" in caplog.text


def test_falls_back_to_random_uuid4_when_iteration_index_missing() -> None:
    first = derive_message_id("notifyAgent", "wf-instance-1", None)
    second = derive_message_id("notifyAgent", "wf-instance-1", None)
    assert first != second
    assert uuid.UUID(first).version == 4
