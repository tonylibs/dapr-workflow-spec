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


def test_falls_back_to_random_uuid4_when_both_missing() -> None:
    first = derive_message_id("notifyAgent", None, None)
    second = derive_message_id("notifyAgent", None, None)
    assert first != second
    assert uuid.UUID(first).version == 4


# -- Fix (HIGH-2): a top-level call (no `for`-loop, no iteration index) -----
# must still dedupe deterministically, not fall back to a random uuid4.


def test_stable_across_retries_when_iteration_index_absent() -> None:
    """A top-level `call: a2a` not nested in any `for` loop never receives
    `X-Dws-Iteration-Index` at all -- it's genuinely, correctly absent (not
    an empty string; `DispatchContext.iterationIndexEncoded()` never emits
    `""`). That must still dedupe deterministically on
    `(workflow_instance_id, task_name)` alone, since there's no loop and
    therefore no "many items share a task name" collision risk."""
    first = derive_message_id("notifyAgent", "wf-instance-1", None)
    second = derive_message_id("notifyAgent", "wf-instance-1", None)
    assert first == second
    assert uuid.UUID(first).version == 5


def test_absent_iteration_index_is_distinct_from_a_present_one() -> None:
    """The `"-"` placeholder segment used when `iteration_index` is absent
    must never collide with a real, present iteration index -- real header
    values are always digit-and-dot strings
    (`DispatchContext.iterationIndexEncoded()`), so `"-"` is a safe sentinel
    that can never equal a genuine value."""
    without_index = derive_message_id("notifyAgent", "wf-instance-1", None)
    with_index = derive_message_id("notifyAgent", "wf-instance-1", "0")
    assert without_index != with_index
