from __future__ import annotations

import pytest

from dws_call_a2a.config import ObjectParameters, StringParameters
from dws_call_a2a.errors import ConfigError, RequestValidationError
from dws_call_a2a.jq_eval import evaluate_parameters, validate_no_env_access


def test_object_form_combines_expressions_into_one_result() -> None:
    spec = ObjectParameters(value={"message": "${ .input.msg }", "extra": "${ .input.count }"})
    result = evaluate_parameters(spec, {"input": {"msg": {"role": "user"}, "count": 3}})
    assert result == {"message": {"role": "user"}, "extra": 3}


def test_object_form_empty_short_circuits() -> None:
    result = evaluate_parameters(ObjectParameters(value={}), {"anything": True})
    assert result == {}


# -- Fix 2 (HIGH-1 remainder): the object form is an arbitrary nested JSON --
# structure with select whole-string `${...}`-wrapped leaves, not a flat map
# of jq-expression strings.


def test_literal_string_not_wrapped_passes_through_unchanged() -> None:
    """A plain literal value (e.g. `"role": "user"`) is not a jq expression
    and must not be evaluated or altered."""
    spec = ObjectParameters(value={"role": "user"})
    result = evaluate_parameters(spec, {"anything": True})
    assert result == {"role": "user"}


def test_fully_wrapped_string_is_evaluated() -> None:
    """A string that is entirely `${ ... }` is replaced by the jq result of
    evaluating its inner text against the request input."""
    spec = ObjectParameters(value={"text": "${ .userQuestion }"})
    result = evaluate_parameters(spec, {"userQuestion": "what is the weather?"})
    assert result == {"text": "what is the weather?"}


def test_string_merely_containing_wrapper_syntax_is_not_partially_substituted() -> None:
    """A string that *contains* `${...}` without the wrapper spanning the
    *whole* value is a literal, passed through unchanged -- this repo's
    convention is full-string-wrap only, never partial/template
    interpolation."""
    spec = ObjectParameters(value={"note": "cost is ${.price} dollars"})
    result = evaluate_parameters(spec, {"price": 42})
    assert result == {"note": "cost is ${.price} dollars"}


def test_nested_expression_two_levels_deep_is_evaluated_sibling_literals_untouched() -> None:
    spec = ObjectParameters(
        value={
            "message": {
                "role": "user",
                "parts": [{"kind": "text", "text": "${ .x }"}],
            }
        }
    )
    result = evaluate_parameters(spec, {"x": "hello"})
    assert result == {
        "message": {
            "role": "user",
            "parts": [{"kind": "text", "text": "hello"}],
        }
    }


def test_numbers_booleans_and_null_pass_through_unchanged_anywhere_in_the_structure() -> None:
    spec = ObjectParameters(
        value={"count": 3, "enabled": True, "missing": None, "nested": {"flag": False}}
    )
    result = evaluate_parameters(spec, {})
    assert result == {"count": 3, "enabled": True, "missing": None, "nested": {"flag": False}}


def test_empty_object_evaluates_to_empty_object() -> None:
    """The controller's new default when `with.parameters` is absent
    (`PARAMETERS=\"{}\"`) must evaluate cleanly, with no errors."""
    result = evaluate_parameters(ObjectParameters(value={}), {})
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
    spec = ObjectParameters(value={"safe": "${ .input.msg }", "leaky": f"${{{expression}}}"})
    with pytest.raises(ConfigError, match=r"PARAMETERS\.leaky"):
        validate_no_env_access(spec)


def test_object_form_rejects_env_access_nested_two_levels_deep() -> None:
    """Regression test for the restructuring from a flat map of expression
    strings to an arbitrary nested JSON structure: the security guard must
    still catch `env`/`$ENV` access when the offending expression is nested
    inside objects/arrays, not just at the top level."""
    spec = ObjectParameters(value={"message": {"parts": [{"text": "${ env.AUTH_TOKEN }"}]}})
    with pytest.raises(ConfigError, match=r"PARAMETERS\.message\.parts\[0\]\.text"):
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
    spec = ObjectParameters(value={"message": "${ .input.msg }", "env": "${ {env: .foo} }"})
    validate_no_env_access(spec)  # must not raise


def test_object_form_allows_unwrapped_literal_strings_that_merely_look_like_env_access() -> None:
    """A plain literal (not wrapped in `${...}`) is never evaluated as jq, so
    it can't leak the environment regardless of its text content."""
    spec = ObjectParameters(value={"note": "env.AUTH_TOKEN", "other": "$ENV.AUTH_TOKEN"})
    validate_no_env_access(spec)  # must not raise


def test_object_form_empty_is_safe() -> None:
    validate_no_env_access(ObjectParameters(value={}))  # must not raise
