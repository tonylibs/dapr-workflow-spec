package io.dws.step.failure;

import org.apache.commons.lang3.StringUtils;

/**
 * Non-retryable payload rejection: {@code validation failed: <detail>}. v1 classifies it as
 * validation. Unlike the other two it carries no {@code step '<task>'} prefix, matching what v1's
 * step services emit.
 */
public final class StepValidationException extends StepFailureException {

  static final String MARKER = "validation failed:";

  public StepValidationException(String detail) {
    super(MARKER + StringUtils.SPACE + detail, null);
  }

  @Override
  public boolean retryable() {
    return false;
  }
}
