package io.dws.orchestrator.dataflow;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.dws.orchestrator.dataflow.DataFlowException.Phase;
import io.serverlessworkflow.api.types.ExternalResource;
import io.serverlessworkflow.api.types.SchemaExternal;
import io.serverlessworkflow.api.types.SchemaInline;
import io.serverlessworkflow.api.types.SchemaUnion;
import org.junit.jupiter.api.Test;

/**
 * Pins the json-schema-validator 2.0.0 API this component compiles against — {@code SchemaRegistry}
 * / {@code Schema.validate -> List<Error>}, all over Jackson-2 {@code JsonNode} — and the fault
 * shape raised on failure.
 */
class SchemaValidatorTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final SchemaValidator validator =
      new SchemaValidator(
          SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12),
          new ObjectMapper());

  private static final String ORDER_SCHEMA =
      """
      {
        "type": "object",
        "properties": { "orderId": { "type": "string" }, "total": { "type": "number" } },
        "required": ["orderId"]
      }
      """;

  private SchemaUnion inlineSchema(String document, String format) throws Exception {
    SchemaInline inline = new SchemaInline(mapper.readTree(document));
    inline.setFormat(format);
    SchemaUnion union = new SchemaUnion();
    union.setSchemaInline(inline);
    return union;
  }

  private JsonNode json(String raw) throws Exception {
    return mapper.readTree(raw);
  }

  @Test
  void nullSchemaIsNotAFailure() {
    assertThatCode(() -> validator.validate(null, null, "checkInventory", Phase.INPUT))
        .doesNotThrowAnyException();
  }

  @Test
  void conformingInstancePasses() throws Exception {
    SchemaUnion schema = inlineSchema(ORDER_SCHEMA, null);

    assertThatCode(
            () ->
                validator.validate(
                    schema, json("{\"orderId\":\"o-1\",\"total\":9.5}"), "check", Phase.INPUT))
        .doesNotThrowAnyException();
  }

  @Test
  void nonConformingInstanceFailsNamingTheOffendingField() throws Exception {
    SchemaUnion schema = inlineSchema(ORDER_SCHEMA, null);

    // total is a string where the schema requires a number.
    assertThatThrownBy(
            () ->
                validator.validate(
                    schema,
                    json("{\"orderId\":\"o-1\",\"total\":\"lots\"}"),
                    "check",
                    Phase.OUTPUT))
        .isInstanceOf(DataFlowException.class)
        .hasMessageContaining("check")
        .hasMessageContaining("output")
        .hasMessageContaining("total");
  }

  @Test
  void missingRequiredPropertyFails() throws Exception {
    SchemaUnion schema = inlineSchema(ORDER_SCHEMA, null);

    assertThatThrownBy(() -> validator.validate(schema, json("{}"), "check", Phase.INPUT))
        .isInstanceOf(DataFlowException.class)
        .hasMessageContaining("orderId");
  }

  @Test
  void externalSchemaIsRejectedNamingTheUnsupportedForm() {
    SchemaUnion union = new SchemaUnion();
    union.setSchemaExternal(new SchemaExternal(new ExternalResource()));

    assertThatThrownBy(() -> validator.validate(union, null, "check", Phase.INPUT))
        .isInstanceOf(DataFlowException.class)
        .hasMessageContaining("external schema");
  }

  @Test
  void nonJsonSchemaFormatIsRejected() throws Exception {
    SchemaUnion schema = inlineSchema(ORDER_SCHEMA, "avro");

    assertThatThrownBy(() -> validator.validate(schema, json("{}"), "check", Phase.INPUT))
        .isInstanceOf(DataFlowException.class)
        .hasMessageContaining("avro");
  }

  @Test
  void faultCarriesTaskNameAndPhase() throws Exception {
    SchemaUnion schema = inlineSchema(ORDER_SCHEMA, null);

    assertThatThrownBy(() -> validator.validate(schema, json("{}"), "checkInventory", Phase.EXPORT))
        .isInstanceOfSatisfying(
            DataFlowException.class,
            fault -> {
              org.assertj.core.api.Assertions.assertThat(fault.taskName())
                  .isEqualTo("checkInventory");
              org.assertj.core.api.Assertions.assertThat(fault.phase()).isEqualTo(Phase.EXPORT);
            });
  }
}
