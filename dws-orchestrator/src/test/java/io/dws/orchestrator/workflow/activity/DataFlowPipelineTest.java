package io.dws.orchestrator.workflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.dataflow.DataFlowException;
import io.dws.orchestrator.dataflow.DataFlowException.Phase;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives both data-flow phases against the {@code dataflow.yaml} fixture: {@code input.from} +
 * {@code input.schema}, {@code output.as} + {@code output.schema}, and {@code export.as} writing
 * the workflow context.
 */
class DataFlowPipelineTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void seedSupport() throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromClasspath("dataflow.yaml");
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "dataflow-workflow",
        "dataflow-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        /* daprClient (unused) */ null,
        mock(WorkflowTaskOptions.class),
        "pubsub");
  }

  private JsonNode json(String raw) throws Exception {
    return mapper.readTree(raw);
  }

  @Test
  void inputFromNarrowsTheDocumentBeforeTheBody() throws Exception {
    // Arrange: the raw data carries more than the task's input schema allows through.
    JsonNode raw = json("{\"orderId\":\"o-1\",\"price\":9.5,\"unrelated\":\"x\"}");

    // Act
    JsonNode input =
        DataFlowPipeline.applyInput(
            new DataFlowInputRequest("chargePayment", raw, mapper.createObjectNode()));

    // Assert: input.from projected exactly orderId + amount.
    assertThat(input.get("orderId").textValue()).isEqualTo("o-1");
    assertThat(input.get("amount").doubleValue()).isEqualTo(9.5);
    assertThat(input.has("unrelated")).isFalse();
  }

  @Test
  void inputSchemaFailureNamesTheTaskAndPhase() throws Exception {
    // orderId is missing, so the transformed input violates input.schema's required list.
    JsonNode raw = json("{\"price\":9.5}");

    assertThatThrownBy(
            () ->
                DataFlowPipeline.applyInput(
                    new DataFlowInputRequest("chargePayment", raw, mapper.createObjectNode())))
        .isInstanceOfSatisfying(
            DataFlowException.class, fault -> assertThat(fault.phase()).isEqualTo(Phase.INPUT))
        .hasMessageContaining("chargePayment")
        .hasMessageContaining("orderId");
  }

  @Test
  void objectFormOutputEvaluatesWrappedValuesAndKeepsLiterals() throws Exception {
    // Arrange: output.as is the object form — ${ .receipt } is an expression, true is a literal.
    JsonNode body = json("{\"receipt\":\"r-77\",\"noise\":1}");

    // Act
    DataFlowResult result =
        DataFlowPipeline.applyOutput(
            new DataFlowOutputRequest("chargePayment", body, mapper.createObjectNode()));

    // Assert
    assertThat(result.data().get("reference").textValue()).isEqualTo("r-77");
    assertThat(result.data().get("settled").booleanValue()).isTrue();
    assertThat(result.data().has("noise")).isFalse();
  }

  @Test
  void exportAsWritesTheWorkflowContextFromTheTransformedOutput() throws Exception {
    JsonNode body = json("{\"receipt\":\"r-77\"}");

    DataFlowResult result =
        DataFlowPipeline.applyOutput(
            new DataFlowOutputRequest("chargePayment", body, mapper.createObjectNode()));

    // export.as runs over the *transformed* output, so it sees `reference`, not `receipt`.
    assertThat(result.context().get("charged").textValue()).isEqualTo("r-77");
  }

  @Test
  void outputSchemaFailureNamesTheOffendingField() throws Exception {
    // `reference` comes out as a number, but output.schema requires a string.
    JsonNode body = json("{\"receipt\":404}");

    assertThatThrownBy(
            () ->
                DataFlowPipeline.applyOutput(
                    new DataFlowOutputRequest("chargePayment", body, mapper.createObjectNode())))
        .isInstanceOfSatisfying(
            DataFlowException.class, fault -> assertThat(fault.phase()).isEqualTo(Phase.OUTPUT))
        .hasMessageContaining("reference");
  }

  @Test
  void expressionCanReadTheIncomingContext() throws Exception {
    // recordAudit's output.as is `{ audited: $context.charged }`.
    DataFlowResult result =
        DataFlowPipeline.applyOutput(
            new DataFlowOutputRequest(
                "recordAudit", json("{\"auditEnabled\":true}"), json("{\"charged\":\"r-77\"}")));

    assertThat(result.data().get("audited").textValue()).isEqualTo("r-77");
  }

  @Test
  void taskWithoutExportLeavesTheContextUnchanged() throws Exception {
    JsonNode context = json("{\"charged\":\"r-77\"}");

    DataFlowResult result =
        DataFlowPipeline.applyOutput(new DataFlowOutputRequest("recordAudit", json("{}"), context));

    assertThat(result.context()).isEqualTo(context);
  }

  @Test
  void taskWithoutDataFlowPassesBothDocumentsThrough() throws Exception {
    JsonNode raw = json("{\"anything\":1}");

    JsonNode input =
        DataFlowPipeline.applyInput(
            new DataFlowInputRequest("passThrough", raw, mapper.createObjectNode()));
    DataFlowResult output =
        DataFlowPipeline.applyOutput(
            new DataFlowOutputRequest("passThrough", raw, mapper.createObjectNode()));

    assertThat(input).isEqualTo(raw);
    assertThat(output.data()).isEqualTo(raw);
    assertThat(output.context()).isEqualTo(mapper.createObjectNode());
  }

  @Test
  void nullContextIsTreatedAsAnEmptyObject() throws Exception {
    // A null context must not blow up an expression that reads $context.
    DataFlowResult result =
        DataFlowPipeline.applyOutput(new DataFlowOutputRequest("recordAudit", json("{}"), null));

    assertThat(result.data().get("audited").isNull()).isTrue();
  }
}
