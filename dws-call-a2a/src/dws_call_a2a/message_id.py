"""Deterministic `messageId` derivation (ADR 0004 Decision 8).

    messageId = uuid5(DWS_NAMESPACE, f"{workflowInstanceId}/{taskName}/{iterationIndex}")

`dws-orchestrator`'s `CallServiceActivity` sends `X-Dws-Workflow-Instance-Id`
on every call this package receives -- it is always populated, sourced from
`WorkflowContext.getInstanceId()`, which is never null. `X-Dws-Iteration-Index`
is genuinely, correctly *absent* (not empty-string) for a call that isn't
nested inside a `for` loop -- the common case of a top-level `call: a2a` --
and is only ever present as a dot-joined string of non-negative integers
(`"0"`, `"1.0"`, ...; see `DispatchContext.iterationIndexEncoded()`), never
empty and never anything else. See `dws-call-a2a/CLAUDE.md` "messageId
dedupe" for the full cross-component writeup.

Because a missing iteration index means "no loop", not "unknown loop
position", `derive_message_id` only requires `workflow_instance_id` to derive
deterministically -- `iteration_index` folds into the seed when present, and
a fixed `"-"` placeholder segment stands in for it when absent, so a
top-level call's seed shape is still `f"{instance}/{task}/-"` rather than
falling back to a random id. `"-"` can never collide with a genuine header
value, since the real header is always digits-and-dots per
`iterationIndexEncoded()`. Only a missing `workflow_instance_id` (never
observed in practice, since the orchestrator always sends it, but not
provably impossible from this package's side) falls back to a *fresh random
UUID4 per request*, logging a warning: retries are **not** deduplicated in
that case, but the id is never wrong in the more dangerous direction
(colliding across unrelated workflow instances/iterations, which a naive
placeholder-based fallback would risk).
"""

from __future__ import annotations

import logging
import uuid

logger = logging.getLogger(__name__)

WORKFLOW_INSTANCE_ID_HEADER = "X-Dws-Workflow-Instance-Id"
ITERATION_INDEX_HEADER = "X-Dws-Iteration-Index"

# Fixed, deterministic namespace UUID (not a magic random constant): the RFC
# 4122 uuid5 of a fixed DNS-style name under `uuid.NAMESPACE_DNS`, computed
# once here so it is always identical across processes/versions.
DWS_NAMESPACE = uuid.uuid5(uuid.NAMESPACE_DNS, "workflow-runtime.dws.io")

# Placeholder segment folded into the seed when `iteration_index` is absent
# (a top-level call, not nested in any `for` loop). Safe as a sentinel: a
# real header value is always a dot-joined string of non-negative integers
# (`DispatchContext.iterationIndexEncoded()`), so it can never literally be
# `"-"`.
_NO_ITERATION_INDEX_PLACEHOLDER = "-"


def derive_message_id(
    task_name: str,
    workflow_instance_id: str | None,
    iteration_index: str | None,
) -> str:
    """Returns a deterministic uuid5 when `workflow_instance_id` is available
    (folding in `iteration_index`, or a fixed placeholder when it's absent),
    else a fresh uuid4 (logging that dedupe is disabled for this call)."""
    if workflow_instance_id:
        segment = (
            iteration_index if iteration_index is not None else _NO_ITERATION_INDEX_PLACEHOLDER
        )
        seed = f"{workflow_instance_id}/{task_name}/{segment}"
        return str(uuid.uuid5(DWS_NAMESPACE, seed))

    logger.warning(
        "messageId dedupe disabled for task=%s: %s header missing. Falling back to a "
        "non-deterministic uuid4; a retried call will not be recognized by the agent as "
        "the same logical invocation.",
        task_name,
        WORKFLOW_INSTANCE_ID_HEADER,
    )
    return str(uuid.uuid4())
