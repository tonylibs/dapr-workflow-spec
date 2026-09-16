"""Evaluates the configured PARAMETERS jq expression(s) against the request
input. Ports `dws-call-openapi/src/jq.ts`'s `evaluateParameters` -- object form
combines every expression into a single jq program so one jq invocation
covers all parameters; string form evaluates the whole expression as one
program and its result *is* the whole params object.

`call: a2a` has no schema validation at all (ADR 0004 Decision 1), so the
guard here -- reject a non-object result before it reaches the agent -- is
the *only* thing standing between a malformed jq expression and a nonsense
JSON-RPC `params` value reaching the agent.

Security note: this module also guards against jq's `env` builtin and `$ENV`
special variable, both of which read the *real* process environment with no
sandboxing available in the `jq` PyPI binding (it links `libjq` in-process --
see `validate_no_env_access`). Without this guard, a DSL author's
`with.parameters` jq expression could read this step's own runtime
credentials (`AUTH_TOKEN`, `AUTH_PASSWORD`, ...) and place them directly in
the outbound JSON-RPC request, or echo them back into the workflow's data
document -- bypassing the `use.secrets`/`$secrets` scoping this repository
otherwise enforces for `call` tasks (ADR 0004 Decision 6).
"""

from __future__ import annotations

import json
import re
from typing import Any

import jq as _jq

from dws_call_a2a.config import ObjectParameters, ParametersSpec, StringParameters
from dws_call_a2a.errors import ConfigError, RequestValidationError

# Matches jq's `$ENV` special variable as a whole token. `\b` after `ENV`
# rejects longer identifiers like `$ENVIRONMENT` (word boundary between two
# word characters does not fire) while still matching `$ENV` followed by
# anything that isn't a further identifier character (`.`, whitespace,
# operators, end of string, ...).
_ENV_VAR_RE = re.compile(r"\$ENV\b")

# Matches a bare reference to jq's zero-arg `env` builtin (`env`, `env.FOO`,
# `[env]`, `env | .FOO`, ...), while deliberately NOT matching:
#   - field access on some other value, e.g. `.env`, `.foo.env` (preceded by
#     `.`) or a longer identifier like `.environment`/`.envelope`/`envx`
#     (preceded by a word character, or the `env` is immediately followed by
#     another word character so `\b` doesn't fire)
#   - an object-construction KEY literally named `env`, either the `env:
#     .foo` explicit-value form or the `{env}` field-access shorthand --
#     both are excluded by requiring that `env` is not immediately followed
#     by optional whitespace and then `:`.
# jq has no `eval`/dynamic-name dispatch, so the only way a program can
# invoke this builtin is for the literal token `env` to appear in its source
# text -- a static text check is therefore sound (no false negatives from
# runtime string construction), though it can be over-broad on things like
# `env` appearing inside a string literal or comment; over-rejecting is the
# safe direction for a security guard.
_ENV_BUILTIN_RE = re.compile(r"(?<![.\w])env\b(?!\s*:)")


def _find_env_access(expression: str) -> str | None:
    """Returns the offending token (`"$ENV"` or `"env"`) if `expression`'s
    source text references jq's `env` builtin or `$ENV` variable, else
    `None`."""
    if _ENV_VAR_RE.search(expression):
        return "$ENV"
    if _ENV_BUILTIN_RE.search(expression):
        return "env"
    return None


def validate_no_env_access(spec: ParametersSpec) -> None:
    """Boot-time guard: raises `ConfigError` if any expression in `spec`
    references jq's `env` builtin or `$ENV` variable.

    Called from `config.py`'s `_parse_parameters` at config-load time --
    PARAMETERS is fixed per deployment (from the `PARAMETERS` env var), not
    something that varies per request, so this is a fail-fast boot check
    (matching `dws-call-openapi`/`dws-call-asyncapi`'s fail-fast-on-bad-config
    convention) rather than a per-request 400.
    """
    if isinstance(spec, StringParameters):
        token = _find_env_access(spec.expression)
        if token is not None:
            raise ConfigError(
                f"PARAMETERS jq expression references {token!r}, which reads this step's "
                "real process environment (including its own runtime credentials) -- this "
                "is not permitted; rewrite the expression to not use env/$ENV"
            )
        return
    if isinstance(spec, ObjectParameters):
        for name, expr in spec.expressions.items():
            token = _find_env_access(expr)
            if token is not None:
                raise ConfigError(
                    f"PARAMETERS.{name} jq expression references {token!r}, which reads this "
                    "step's real process environment (including its own runtime credentials) "
                    "-- this is not permitted; rewrite the expression to not use env/$ENV"
                )
        return
    raise TypeError(f"unknown parameters spec: {spec!r}")  # pragma: no cover -- exhaustive union


def evaluate_parameters(spec: ParametersSpec, input_data: Any) -> dict[str, Any]:
    """Returns the evaluated params object, or raises `RequestValidationError`
    if the jq program fails or the result isn't a JSON object."""
    if isinstance(spec, StringParameters):
        program = spec.expression
    elif isinstance(spec, ObjectParameters):
        if not spec.expressions:
            return {}
        fields = ", ".join(
            f"{json.dumps(name)}: ({expr})" for name, expr in spec.expressions.items()
        )
        program = f"{{ {fields} }}"
    else:  # pragma: no cover -- exhaustive union
        raise TypeError(f"unknown parameters spec: {spec!r}")

    try:
        compiled = _jq.compile(program)
        result = compiled.input_value(input_data).first()
    except StopIteration as exc:
        raise RequestValidationError(
            "PARAMETERS jq program produced no output against the current workflow data"
        ) from exc
    except ValueError as exc:
        raise RequestValidationError(f"failed to evaluate PARAMETERS jq expression: {exc}") from exc

    if not isinstance(result, dict):
        raise RequestValidationError(
            f"PARAMETERS jq program did not produce a JSON object, got {type(result).__name__}"
        )
    return result
