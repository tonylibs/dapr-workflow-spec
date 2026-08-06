package io.dws.orchestrator.workflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link RaiseErrorActivity} directly, mirroring {@link CatchPolicyTest}'s style: seed
 * {@link WorkflowSupport} from an inline definition, then assert on the resolved error object.
 *
 * <p>The literal-vs-expression split these tests pin is the SDK's own: its deserializer routes a
 * {@code ${ ... }}-wrapped string to the expression accessor and a plain string to the literal one,
 * so the activity only ever asks which accessor is set — it never re-inspects the string.
 */
class RaiseErrorActivityTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private void seed(String yaml) throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromString(yaml, WorkflowFormat.YAML);
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "raise-workflow",
        "raise-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        /* daprClient (unused) */ null,
        mock(WorkflowTaskOptions.class),
        "pubsub");
  }

  private RaiseErrorRequest request(String taskName, JsonNode data) {
    return new RaiseErrorRequest(taskName, data, Map.of());
  }

  /** A single-task definition whose {@code raise.error} body is the supplied YAML. */
  private static String raiseYaml(String errorBody) {
    return """
        document:
          dsl: 1.0.0
          namespace: examples
          name: raise-workflow
          version: '1.0.0'
        do:
          - explode:
              raise:
                error:
        """
        + errorBody.stripTrailing().indent(10);
  }

  @Test
  void literalFieldsAreUsedUnchanged() throws Exception {
    seed(
        raiseYaml(
            """
            type: https://example.com/errors/insufficient-funds
            status: 402
            title: Insufficient funds
            detail: fixed detail text
            """));

    ObjectNode error = RaiseErrorActivity.apply(request("explode", mapper.createObjectNode()));

    assertThat(error.get("type").textValue())
        .isEqualTo("https://example.com/errors/insufficient-funds");
    assertThat(error.get("status").intValue()).isEqualTo(402);
    assertThat(error.get("title").textValue()).isEqualTo("Insufficient funds");
    assertThat(error.get("detail").textValue()).isEqualTo("fixed detail text");
  }

  @Test
  void expressionFieldsReadTheTaskData() throws Exception {
    seed(
        raiseYaml(
            """
            type: https://example.com/errors/insufficient-funds
            status: 402
            title: '${ "Insufficient funds for " + .who }'
            detail: '${ "balance " + (.balance|tostring) }'
            """));

    JsonNode data = mapper.readTree("{\"who\":\"alice\",\"balance\":10}");
    ObjectNode error = RaiseErrorActivity.apply(request("explode", data));

    assertThat(error.get("title").textValue()).isEqualTo("Insufficient funds for alice");
    assertThat(error.get("detail").textValue()).isEqualTo("balance 10");
  }

  @Test
  void statusIsUsedVerbatim() throws Exception {
    // The SDK models status as a primitive int with no expression variant, so there is nothing to
    // evaluate — the declared value is the value.
    seed(
        raiseYaml(
            """
            type: https://example.com/errors/x
            status: 418
            title: Teapot
            detail: short and stout
            """));

    assertThat(
            RaiseErrorActivity.apply(request("explode", mapper.createObjectNode()))
                .get("status")
                .intValue())
        .isEqualTo(418);
  }

  @Test
  void absentInstanceDefaultsToTheRaisingTask() throws Exception {
    seed(
        raiseYaml(
            """
            type: https://example.com/errors/x
            status: 400
            title: Bad
            detail: bad
            """));

    ObjectNode error = RaiseErrorActivity.apply(request("explode", mapper.createObjectNode()));

    assertThat(error.get("instance").textValue()).isEqualTo("/explode");
  }

  @Test
  void declaredInstanceIsHonoured() throws Exception {
    seed(
        raiseYaml(
            """
            type: https://example.com/errors/x
            status: 400
            instance: /custom/path
            title: Bad
            detail: bad
            """));

    ObjectNode error = RaiseErrorActivity.apply(request("explode", mapper.createObjectNode()));

    assertThat(error.get("instance").textValue()).isEqualTo("/custom/path");
  }

  @Test
  void declaredInstanceMayBeAnExpression() throws Exception {
    seed(
        raiseYaml(
            """
            type: https://example.com/errors/x
            status: 400
            instance: '${ "/orders/" + .orderId }'
            title: Bad
            detail: bad
            """));

    JsonNode data = mapper.readTree("{\"orderId\":\"o-1\"}");

    assertThat(RaiseErrorActivity.apply(request("explode", data)).get("instance").textValue())
        .isEqualTo("/orders/o-1");
  }

  @Test
  void namedErrorDefinitionResolvesFromUseErrors() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: raise-workflow
          version: '1.0.0'
        use:
          errors:
            paymentDeclined:
              type: https://example.com/errors/payment-declined
              status: 402
              title: Payment declined
              detail: declined by processor
        do:
          - explode:
              raise:
                error: paymentDeclined
        """);

    ObjectNode error = RaiseErrorActivity.apply(request("explode", mapper.createObjectNode()));

    assertThat(error.get("type").textValue())
        .isEqualTo("https://example.com/errors/payment-declined");
    assertThat(error.get("status").intValue()).isEqualTo(402);
    assertThat(error.get("title").textValue()).isEqualTo("Payment declined");
    assertThat(error.get("detail").textValue()).isEqualTo("declined by processor");
    // The reference form is not a second dialect: instance still defaults to the raising task.
    assertThat(error.get("instance").textValue()).isEqualTo("/explode");
  }

  @Test
  void unresolvableErrorNameFailsLoudly() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: raise-workflow
          version: '1.0.0'
        do:
          - explode:
              raise:
                error: doesNotExist
        """);

    assertThatThrownBy(
            () -> RaiseErrorActivity.apply(request("explode", mapper.createObjectNode())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("doesNotExist")
        .hasMessageContaining("use.errors");
  }

  @Test
  void aTaskThatIsNotARaiseTaskIsRejected() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: raise-workflow
          version: '1.0.0'
        do:
          - notARaise:
              set:
                x: '"y"'
        """);

    assertThatThrownBy(
            () -> RaiseErrorActivity.apply(request("notARaise", mapper.createObjectNode())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("notARaise");
  }
}
