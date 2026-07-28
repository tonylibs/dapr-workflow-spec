package io.dws.orchestrator.dataflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import io.dws.orchestrator.dataflow.DataFlowException.Phase;
import io.serverlessworkflow.api.types.SchemaInline;
import io.serverlessworkflow.api.types.SchemaUnion;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Validates a task's transformed input/output against the JSON Schema declared on {@code
 * input.schema} / {@code output.schema} / {@code export.schema}.
 *
 * <p>Only <em>inline</em> schemas ({@code schema.document}) in the default {@code json} format are
 * supported. An external schema resource or a non-{@code json} format is rejected with a {@link
 * DataFlowException} naming the unsupported form rather than silently skipped — an unvalidated
 * document that the author asked to have validated is exactly the post-deployment mystery this
 * codebase avoids elsewhere.
 */
public final class SchemaValidator {

  /** The only {@code schema.format} this phase supports; {@code null} means the default, json. */
  private static final String JSON_FORMAT = "json";

  private final SchemaRegistry registry;
  private final ObjectMapper mapper;

  public SchemaValidator(SchemaRegistry registry, ObjectMapper mapper) {
    this.registry = registry;
    this.mapper = mapper;
  }

  /**
   * Validates {@code instance} against {@code schemaUnion}, if one is declared. A {@code null}
   * union means the task declared no schema, which is not a failure.
   */
  public void validate(SchemaUnion schemaUnion, JsonNode instance, String taskName, Phase phase) {
    if (schemaUnion == null) {
      return;
    }
    if (schemaUnion.getSchemaExternal() != null) {
      throw new DataFlowException(
          taskName, phase, "external schema resources are not supported (use an inline schema)");
    }
    SchemaInline inline = schemaUnion.getSchemaInline();
    if (inline == null || inline.getDocument() == null) {
      throw new DataFlowException(taskName, phase, "schema is declared but has no inline document");
    }
    String format = inline.getFormat();
    if (format != null && !JSON_FORMAT.equalsIgnoreCase(format)) {
      throw new DataFlowException(
          taskName, phase, "unsupported schema format '" + format + "' (only 'json' is supported)");
    }

    Schema schema;
    try {
      schema = registry.getSchema(mapper.valueToTree(inline.getDocument()));
    } catch (RuntimeException e) {
      throw new DataFlowException(
          taskName, phase, "schema document is not a valid JSON Schema: " + e.getMessage(), e);
    }

    List<Error> errors = schema.validate(instance == null ? mapper.nullNode() : instance);
    if (!errors.isEmpty()) {
      throw new DataFlowException(taskName, phase, "schema validation failed: " + describe(errors));
    }
  }

  /** Renders validation errors as {@code <instanceLocation>: <message>}, joined. */
  private static String describe(List<Error> errors) {
    return errors.stream()
        .map(
            error -> {
              String location = String.valueOf(error.getInstanceLocation());
              String at = location.isEmpty() ? "<root>" : location;
              return at + ": " + error.getMessage();
            })
        .collect(Collectors.joining("; "));
  }
}
