package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.serverlessworkflow.api.types.FlowDirective;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;

/**
 * A flow directive flattened into two plain strings so it can round-trip through Jackson as an
 * activity response. {@link FlowDirective} itself is a generated one-of type whose deserialization
 * is not symmetric with its serialization, so activities return this instead.
 *
 * <p>{@code keyword} holds the {@link FlowDirectiveEnum} constant name when the directive is one of
 * the reserved keywords; {@code target} holds the task name when it is an explicit jump. Both null
 * means "continue with the next task in the list".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowOutcome(String keyword, String target) {

  /** The implicit directive of a task that declares no {@code then}: fall through sequentially. */
  public static final FlowOutcome CONTINUE = new FlowOutcome(null, null);

  /** Flattens an SDK flow directive; a null directive means sequential continue. */
  public static FlowOutcome of(FlowDirective then) {
    if (then == null) {
      return CONTINUE;
    }
    FlowDirectiveEnum keyword = then.getFlowDirectiveEnum();
    if (keyword != null) {
      return new FlowOutcome(keyword.name(), null);
    }
    String target = then.getString();
    return (target != null && !target.isBlank()) ? new FlowOutcome(null, target) : CONTINUE;
  }

  /** The reserved keyword this outcome carries, or null when it is a jump or a plain continue. */
  public FlowDirectiveEnum directive() {
    return keyword == null ? null : FlowDirectiveEnum.valueOf(keyword);
  }
}
