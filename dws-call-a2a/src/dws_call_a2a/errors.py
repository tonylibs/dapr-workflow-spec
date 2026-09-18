"""Exception hierarchy for the runner.

Mirrors the sibling step-services' pattern (see `dws-call-openapi/src/runner.ts`):
custom exception classes per failure category, mapped to an HTTP status at the
FastAPI route boundary rather than threading status codes through call sites.
"""

from __future__ import annotations


class ConfigError(Exception):
    """A configuration error that should stop startup (fail fast, exit non-zero).

    Never HTTP-mapped -- raised only from `config.py`/`cardvalidate.py` during
    application startup, matching `dws-call-openapi`'s `ConfigError`.
    """


class RequestValidationError(Exception):
    """The request body or evaluated PARAMETERS was structurally invalid.

    Maps to HTTP 400 per the shared step-service contract.
    """


class UpstreamError(Exception):
    """A genuine transport/protocol failure calling the agent (card fetch or RPC).

    Maps to HTTP 502 per the shared step-service contract, so the orchestrator's
    retry policy re-invokes the step. `input-required`/`auth-required` task
    states are NOT this -- they are successful results (ADR 0004 Decision 6).
    """
