"""Deterministic `messageId` derivation (ADR 0004 Decision 8).

    messageId = uuid5(DWS_NAMESPACE, f"{workflowInstanceId}/{taskName}/{iterationIndex}")

**Known gap, not fixed by this package** (flagged back per the task brief, not
silently worked around): `dws-orchestrator`'s `CallServiceActivity`/`CallRequest`
today sends only `(appId, path, data)` to a step service's `POST /run` -- no
workflow-instance-id, task-name, or for-loop iteration-index is threaded onto
the request as a header or field. Neither `dws-call-openapi` nor any other
sibling runner reads any such identifier either (verified: no runner reads
`request.headers` for anything but logging). So the two identifiers this
formula needs are **not currently available** to any step service, a2a
included, over the existing step-service contract.

This module defines the identifiers the derivation *would* need as optional
inbound HTTP headers -- `X-Dws-Workflow-Instance-Id` / `X-Dws-Iteration-Index`
-- as a documented, forward-compatible extension point. If `dws-orchestrator`
starts sending them, deduplication starts working with no change to this
runner. Until then, every call falls back to a *fresh random UUID4 per
request* and logs a warning: retries are **not** deduplicated, but the id is
never wrong in the more dangerous direction (colliding across unrelated
workflow instances/iterations, which a naive placeholder-based fallback would
risk). See `dws-call-a2a/CLAUDE.md` "Known gap: messageId dedupe" for the
full writeup and `dws-call-a2a/README.md` for the operator-facing summary.
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


def derive_message_id(
    task_name: str,
    workflow_instance_id: str | None,
    iteration_index: str | None,
) -> str:
    """Returns a deterministic uuid5 when both identifiers are available, else
    a fresh uuid4 (logging that dedupe is disabled for this call)."""
    if workflow_instance_id and iteration_index:
        seed = f"{workflow_instance_id}/{task_name}/{iteration_index}"
        return str(uuid.uuid5(DWS_NAMESPACE, seed))

    logger.warning(
        "messageId dedupe disabled for task=%s: %s/%s header missing "
        "(dws-orchestrator does not yet send workflow-instance-id/iteration-index "
        "to step services -- see CLAUDE.md 'Known gap: messageId dedupe'). "
        "Falling back to a non-deterministic uuid4; a retried call will not be "
        "recognized by the agent as the same logical invocation.",
        task_name,
        WORKFLOW_INSTANCE_ID_HEADER,
        ITERATION_INDEX_HEADER,
    )
    return str(uuid.uuid4())
