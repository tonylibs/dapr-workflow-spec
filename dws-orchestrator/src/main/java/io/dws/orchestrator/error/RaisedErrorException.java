package io.dws.orchestrator.error;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * An error a workflow author deliberately raised through a {@code raise} task.
 *
 * <p>The already-resolved five-field error object is folded into the <em>message</em> as JSON,
 * behind {@link WorkflowErrors#RAISE_MARKER} — only an exception's message survives the Dapr
 * activity boundary, the same constraint {@link StepInvocationException} works around.
 *
 * <p>The marker exists so {@link WorkflowErrors#of} can read the object back out and return it
 * unchanged. A raised error is author-authoritative: it already has the shape that {@link
 * WorkflowErrors#classify} exists to infer for failures the runtime merely observes, so classifying
 * it would overwrite the author's own {@code type}/{@code status}/{@code title} with a guess.
 */
public class RaisedErrorException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public RaisedErrorException(JsonNode error) {
    super(WorkflowErrors.RAISE_MARKER + error);
  }
}
