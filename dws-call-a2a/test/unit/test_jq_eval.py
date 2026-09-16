from __future__ import annotations

import pytest

from dws_call_a2a.config import ObjectParameters, StringParameters
from dws_call_a2a.errors import ConfigError, RequestValidationError
from dws_call_a2a.jq_eval import evaluate_parameters, validate_no_env_access


def test_object_form_combines_expressions_into_one_result() -> None:
    spec = ObjectParameters(expressions={"message": ".input.msg", "extra": ".input.count"})
    result = evaluate_parameters(spec, {"input": {"msg": {"role": "user"}, "count": 3}})
    assert result == {"message": {"role": "user"}, "extra": 3}


def test_object_form_empty_short_circuits() -> None:
    result = evaluate_parameters(ObjectParameters(expressions={}), {"anything": True})
    assert result == {}


def test_string_form_result_is_the_whole_params_object() -> None:
    spec = StringParameters(expression="{message: .input.msg}")
    result = evaluate_parameters(spec, {"input": {"msg": {"role": "user"}}})
    assert result == {"message": {"role": "user"}}


def test_guard_rejects_non_object_result() -> None:
    spec = StringParameters(expression=".input.msg.role")  # a string, not an object
    with pytest.raises(RequestValidationError, match="did not produce a JSON object"):
        evaluate_parameters(spec, {"input": {"msg": {"role": "user"}}})


def test_guard_rejects_array_result() -> None:
    spec = StringParameters(expression="[1,2,3]")
    with pytest.raises(RequestValidationError, match="did not produce a JSON object"):
        evaluate_parameters(spec, {})


def test_invalid_jq_syntax_raises_request_validation_error() -> None:
    spec = StringParameters(expression=".foo +")
    with pytest.raises(RequestValidationError, match="failed to evaluate"):
        evaluate_parameters(spec, {})


def test_jq_runtime_error_raises_request_validation_error() -> None:
    spec = StringParameters(expression=".foo.bar")
    with pytest.raises(RequestValidationError, match="failed to evaluate"):
        evaluate_parameters(spec, {"foo": 1})


def test_jq_program_producing_no_output_raises() -> None:
    spec = StringParameters(expression="empty")
    with pytest.raises(RequestValidationError, match="no output"):
        evaluate_parameters(spec, {})


# -- validate_no_env_access: security guard against jq's `env`/`$ENV` -------


@pytest.mark.parametrize(
    "expression",
    [
        "$ENV.AUTH_TOKEN",
        "{token: $ENV.AUTH_TOKEN}",
        "env",
        "env.AUTH_TOKEN",
        "[env]",
        "env | .AUTH_TOKEN",
        ". as $x | env.AUTH_TOKEN",
    ],
)
def test_string_form_rejects_env_access(expression: str) -> None:
    spec = StringParameters(expression=expression)
    with pytest.raises(ConfigError, match="reads this step's real process environment"):
        validate_no_env_access(spec)


@pytest.mark.parametrize(
    "expression",
    [
        "$ENV.AUTH_TOKEN",
        "env.AUTH_TOKEN",
    ],
)
def test_object_form_rejects_env_access_in_any_expression(expression: str) -> None:
    spec = ObjectParameters(expressions={"safe": ".input.msg", "leaky": expression})
    with pytest.raises(ConfigError, match=r"PARAMETERS\.leaky"):
        validate_no_env_access(spec)


@pytest.mark.parametrize(
    "expression",
    [
        ".input.msg",
        ".environment",
        ".envelope",
        ".foo.env",
        ".env",
        "{env: .foo}",
        "{env : .foo}",
        "envx.FOO",
        "xenv.FOO",
        "$ENVIRONMENT",
    ],
)
def test_allows_expressions_that_merely_look_like_env_access(expression: str) -> None:
    spec = StringParameters(expression=expression)
    validate_no_env_access(spec)  # must not raise


def test_object_form_allows_safe_expressions() -> None:
    spec = ObjectParameters(expressions={"message": ".input.msg", "env": "{env: .foo}"})
    validate_no_env_access(spec)  # must not raise


def test_object_form_empty_is_safe() -> None:
    validate_no_env_access(ObjectParameters(expressions={}))  # must not raise
