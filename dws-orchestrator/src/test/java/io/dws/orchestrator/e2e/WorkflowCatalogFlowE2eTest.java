package io.dws.orchestrator.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.InterpreterWorkflow;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * "Works as the flow" e2e tier: every scenario of every valid catalog definition is parsed
 * with the Open Workflow Specification SDK and driven through the real {@link InterpreterWorkflow}
 * against a {@link ScriptedContext}. The assertions pin the observable behaviour of the flow:
 * which app-ids are invoked and in what order, which timers and emits fire, and the exact
 * completion output.
 */
class WorkflowCatalogFlowE2eTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Numeric-tolerant node comparison: jq arithmetic may produce a different numeric node type
   * (int/long/double) than the manifest's literal, so numbers compare by value, not node type.
   */
  private static final Comparator<JsonNode> NUMERIC_TOLERANT = (a, b) -> {
    if (a.equals(b)) {
      return 0;
    }
    if (a.isNumber() && b.isNumber() && a.decimalValue().compareTo(b.decimalValue()) == 0) {
      return 0;
    }
    return 1;
  };

  static Stream<Arguments> scenarios() {
    return E2eCatalog.cases().stream()
        .filter(c -> c.deploy().valid())
        .flatMap(c -> c.scenarios().stream()
            .map(s -> Arguments.of(Named.of(c.dir(), c), Named.of(s.name(), s))));
  }

  @ParameterizedTest(name = "{0} / {1}")
  @MethodSource("scenarios")
  void definitionExecutesAsTheFlow(E2eCatalog.CatalogCase c, E2eCatalog.Scenario s) throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromString(c.definitionText(), WorkflowFormat.YAML);
    WorkflowSupport.init(definition, definition.getDocument().getName(),
        new JqEvaluator(MAPPER), MAPPER,
        /* daprClient (unused; activities are scripted) */ null,
        mock(WorkflowTaskOptions.class), "pubsub");
    ScriptedContext ctx = new ScriptedContext(s, MAPPER);

    new InterpreterWorkflow().execute(ctx.context());

    assertThat(ctx.recordedCallAppIds())
        .as("service invocations, in order")
        .containsExactlyElementsOf(s.expectCalls());
    assertThat(ctx.recordedTimers())
        .as("timers created for wait tasks")
        .containsExactlyElementsOf(s.expectTimers());
    assertThat(ctx.recordedEmits())
        .as("pub/sub emissions")
        .extracting(e -> e.pubsub() + ":" + e.topic())
        .containsExactlyElementsOf(s.expectEmits().stream()
            .map(e -> e.pubsub() + ":" + e.topic())
            .toList());

    JsonNode output = ctx.completedOutput();
    assertThat(output).as("workflow completed with an output").isNotNull();
    assertThat(output.equals(NUMERIC_TOLERANT, s.expectOutput()))
        .as("completion output%nexpected: %s%nactual:   %s", s.expectOutput(), output)
        .isTrue();
  }
}
