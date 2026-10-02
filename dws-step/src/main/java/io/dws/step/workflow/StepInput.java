package io.dws.step.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import java.util.Map;

/**
 * Input of the {@code Step} activity; the output is the new workflow data as a {@link JsonNode}.
 * Mirrors v1's {@code CallRequest} data fields and adds nothing else.
 *
 * @param data the current workflow data document
 * @param variables scope-local variables, e.g. the caught error inside a {@code catch} block
 * @param workflowInstanceId the <em>root</em> workflow instance ID
 * @param iterationIndex opaque encoding of the enclosing {@code for} iteration(s); {@code null}
 *     outside a loop
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StepInput(
    JsonNode data,
    Map<String, JsonNode> variables,
    String workflowInstanceId,
    String iterationIndex) {

  public StepInput {
    data = data == null ? NullNode.getInstance() : data;
    variables = variables == null ? Map.of() : Map.copyOf(variables);
  }
}
