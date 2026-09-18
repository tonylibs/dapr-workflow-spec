"""Evaluates the configured PARAMETERS jq expression(s) against the request
input.

String form (`StringParameters`) evaluates the whole configured string as one
jq program, and its result *is* the whole params object -- ported unchanged
from `dws-call-openapi/src/jq.ts`'s `evaluateParameters`, since the ADR says
the entire string form *is* a jq expression directly, no ambiguity there.

Object form (`ObjectParameters`) is NOT a port of that same approach applied
per-field: `dws-call-openapi`'s `HEADERS`/`QUERY` type every value as a full
jq-expression string by schema construction, but a2a's `with.parameters`
schema (`WithA2AParameters`) permits arbitrary literal JSON with select
`${...}`-wrapped strings nested at any depth. So instead of combining every
top-level value into one jq program, `evaluate_parameters` recursively walks
`ObjectParameters.value`: dicts and lists are walked and rebuilt with the
same shape; a string leaf is evaluated as a jq program *only* if it matches
`${...}` as a whole-string, anchored wrapper (this repo's runtime-expression
convention -- see `dws-controller`'s `SECRET_REFERENCE` regex, and
`_match_wrapped_expression` below), and the jq result (any JSON type) replaces
the string; every other leaf -- including a plain literal string that merely
*contains* `${` somewhere without wrapping the whole value -- passes through
unchanged. There is deliberately no partial/template interpolation.

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
otherwise enforces for `call` tasks (ADR 0004 Decision 6). Because object-form
expressions can now be nested at any depth, this guard walks the same
recursive structure `evaluate_parameters` does, checking every
`${...}`-wrapped string's inner text at every depth, not just the top level.
"""

from __future__ import annotations

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
#   - an object-construction KEY literally named `env` in its explicit-value
#     form (`env: .foo`), excluded by requiring that `env` is not immediately
#     followed by optional whitespace and then `:`. The `{env}` field-access
#     shorthand (jq's sugar for `{env: .env}`, which reads the *input*, not
#     the process environment) is NOT excluded and is rejected -- a false
#     positive, kept deliberately since over-rejecting is the safe direction
#     here and the shorthand has an accepted rewrite (`{env: .env}`).
#   - a local variable binding spelled `$env` (`... as $env | $env.host`),
#     excluded via `$` in the lookbehind; a jq variable cannot reach the
#     process environment, only the bare `env` builtin and `$ENV` can.
# jq has no `eval`/dynamic-name dispatch, so the only way a program can
# invoke this builtin is for the literal token `env` to appear in its source
# text -- a static text check is therefore sound (no false negatives from
# runtime string construction), though it can be over-broad on things like
# `env` appearing inside a string literal or comment; over-rejecting is the
# safe direction for a security guard.
_ENV_BUILTIN_RE = re.compile(r"(?<![.\w$])env\b(?!\s*:)")


def _match_wrapped_expression(value: str) -> str | None:
    """Returns the inner jq-expression text if `value` is entirely
    `${ ... }` (anchored, whole-string wrapper), else `None` -- including for
    a string that merely *contains* `${...}` without the wrapper spanning
    the whole value (e.g. `"cost is ${.price} dollars"`), which is a literal
    and must NOT be partially substituted.

    This is a brace-depth scan, not a regex: a regex anchored with `fullmatch`
    against `\\$\\{(.*)\\}` is *greedy* -- on a multi-placeholder literal like
    `"${firstName} ${lastName}"` it still matches the whole string (there's a
    `{` near the start and a `}` at the end), incorrectly treating it as one
    wrapper with inner text `firstName} ${lastName`. A non-greedy `.*?` doesn't
    fix this either under `fullmatch`, since `fullmatch` still forces the
    match to consume the entire string. What actually distinguishes "one
    whole-string wrapper" from "a literal that starts with `${` and ends with
    `}` but isn't one wrapper" is brace *nesting depth*: the first `{` must
    close (return to depth 0) exactly at the final character, not partway
    through. Braces inside a jq double-quoted string literal (honoring `\\"`
    escapes) don't count towards nesting, so object-construction wrappers
    like `${ {role: "user", text: .msg} }` -- and one with a literal `}`
    inside a quoted string, `${ {a: "}"} }` -- still evaluate correctly.
    """
    if len(value) < 3 or not value.startswith("${") or not value.endswith("}"):
        return None

    depth = 0
    in_string = False
    escaped = False
    for index in range(1, len(value)):
        char = value[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                # The opening `${`'s brace closed here. Only a whole-string
                # wrapper if this is also the string's last character.
                return value[2:index] if index == len(value) - 1 else None
    return None


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
        _validate_no_env_access_nested(spec.value, path="")
        return
    raise TypeError(f"unknown parameters spec: {spec!r}")  # pragma: no cover -- exhaustive union


def _validate_no_env_access_nested(value: object, *, path: str) -> None:
    """Recursively walks an `ObjectParameters.value` structure, checking every
    `${...}`-wrapped string leaf's inner text for `env`/`$ENV` references at
    any nesting depth. `path` accumulates a dotted/bracketed description of
    the leaf's location for the error message (e.g. `.message.parts[0].text`)."""
    if isinstance(value, dict):
        for key, item in value.items():
            _validate_no_env_access_nested(item, path=f"{path}.{key}")
        return
    if isinstance(value, list):
        for index, item in enumerate(value):
            _validate_no_env_access_nested(item, path=f"{path}[{index}]")
        return
    if not isinstance(value, str):
        return  # number/bool/None leaf -- nothing to check
    inner = _match_wrapped_expression(value)
    if inner is None:
        return  # a literal string, not a jq expression -- nothing to check
    token = _find_env_access(inner)
    if token is not None:
        raise ConfigError(
            f"PARAMETERS{path} jq expression references {token!r}, which reads this "
            "step's real process environment (including its own runtime credentials) "
            "-- this is not permitted; rewrite the expression to not use env/$ENV"
        )


def _run_jq_program(program: str, input_data: Any) -> Any:
    """Compiles and runs `program` against `input_data`, returning the first
    result, or raising `RequestValidationError` if the program fails to
    compile/run or produces no output."""
    try:
        compiled = _jq.compile(program)
        return compiled.input_value(input_data).first()
    except StopIteration as exc:
        raise RequestValidationError(
            "PARAMETERS jq program produced no output against the current workflow data"
        ) from exc
    except ValueError as exc:
        raise RequestValidationError(f"failed to evaluate PARAMETERS jq expression: {exc}") from exc


def _evaluate_nested(value: object, input_data: Any) -> Any:
    """Recursively walks an `ObjectParameters.value` structure: dicts/lists
    are walked and rebuilt with the same shape; a string leaf that is
    entirely `${ ... }` is replaced by the jq result of running its inner
    text against `input_data` (any JSON type, not necessarily a string);
    every other leaf -- literal strings (including ones merely containing
    `${` without wrapping the whole value), numbers, booleans, `None` --
    passes through unchanged."""
    if isinstance(value, dict):
        return {key: _evaluate_nested(item, input_data) for key, item in value.items()}
    if isinstance(value, list):
        return [_evaluate_nested(item, input_data) for item in value]
    if not isinstance(value, str):
        return value
    inner = _match_wrapped_expression(value)
    if inner is None:
        return value
    return _run_jq_program(inner, input_data)


def evaluate_parameters(spec: ParametersSpec, input_data: Any) -> dict[str, Any]:
    """Returns the evaluated params object, or raises `RequestValidationError`
    if a jq program fails or the final result isn't a JSON object."""
    if isinstance(spec, StringParameters):
        result = _run_jq_program(spec.expression, input_data)
    elif isinstance(spec, ObjectParameters):
        result = _evaluate_nested(spec.value, input_data)
    else:  # pragma: no cover -- exhaustive union
        raise TypeError(f"unknown parameters spec: {spec!r}")

    if not isinstance(result, dict):
        raise RequestValidationError(
            f"PARAMETERS jq program did not produce a JSON object, got {type(result).__name__}"
        )
    return result
