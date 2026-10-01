package io.dws.step.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.durabletask.DataConverter;
import io.dapr.durabletask.JacksonDataConverter;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The envelope must survive the exact converter Dapr's workflow runtime applies to activities. */
class StepInputRoundTripTest {

  private final DataConverter converter = new JacksonDataConverter();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void fullEnvelopeRoundTrips() throws Exception {
    StepInput input =
        new StepInput(
            mapper.readTree("{\"order\":{\"id\":\"o-1\",\"lines\":[1,2.5,null]}}"),
            Map.of("error", mapper.readTree("{\"status\":502,\"detail\":\"x\"}")),
            "root-instance-1",
            "0.2");

    StepInput copy = roundTrip(input);

    assertThat(copy).isEqualTo(input);
  }

  @Test
  void nullIterationIndexAndEmptyVariablesRoundTrip() throws Exception {
    StepInput input = new StepInput(mapper.readTree("{\"a\":1}"), Map.of(), "root-1", null);

    StepInput copy = roundTrip(input);

    assertThat(copy).isEqualTo(input);
    assertThat(copy.iterationIndex()).isNull();
    assertThat(copy.variables()).isEmpty();
  }

  @Test
  void absentVariablesAndDataNormalise() {
    StepInput input = new StepInput(null, null, "root-1", null);

    assertThat(input.variables()).isEmpty();
    assertThat(input.data().isNull()).isTrue();
    assertThat(roundTrip(input)).isEqualTo(input);
  }

  @Test
  void outputWorkflowDataRoundTrips() throws Exception {
    JsonNode output = mapper.readTree("{\"valid\":true,\"items\":[{\"sku\":\"a\"}],\"n\":null}");

    JsonNode copy = converter.deserialize(converter.serialize(output), JsonNode.class);

    assertThat(copy).isEqualTo(output);
  }

  @Test
  void envelopeCarriesExactlyTheFourDocumentedInputs() {
    assertThat(StepInput.class.getRecordComponents())
        .extracting(c -> c.getName())
        .containsExactly("data", "variables", "workflowInstanceId", "iterationIndex");
  }

  private StepInput roundTrip(StepInput input) {
    return converter.deserialize(converter.serialize(input), StepInput.class);
  }
}
