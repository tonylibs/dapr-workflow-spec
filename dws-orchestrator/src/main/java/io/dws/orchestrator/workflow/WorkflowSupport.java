package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.dapr.client.DaprClient;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.serverlessworkflow.api.types.DoTimeout;
import io.serverlessworkflow.api.types.DurationInline;
import io.serverlessworkflow.api.types.TaskTimeout;
import io.serverlessworkflow.api.types.Timeout;
import io.serverlessworkflow.api.types.TimeoutAfter;
import io.serverlessworkflow.api.types.UseTimeouts;
import io.serverlessworkflow.api.types.Workflow;
import java.time.Duration;
import lombok.experimental.UtilityClass;

/**
 * Static bridge between Spring-managed collaborators and the Dapr workflow runtime.
 *
 * <p>{@code WorkflowRuntimeBuilder} instantiates the workflow and activity classes reflectively via
 * their no-arg constructors, so those classes cannot receive Spring injection. This holder is
 * populated once during bootstrap. Each orchestrator pod serves exactly one, immutable workflow
 * definition for its whole lifetime, so the held {@link Workflow} never changes.
 */
@UtilityClass
public class WorkflowSupport {

  /**
   * Process-wide JSON Schema registry for {@code input.schema}/{@code output.schema} validation.
   * Thread-safe and reusable, so it is built once at class load rather than per validation. It
   * needs no per-workflow configuration, which is why it is not an {@link #init} parameter. An
   * explicit {@code $schema} in a schema document still wins over this default dialect.
   */
  private static final SchemaRegistry SCHEMA_REGISTRY =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

  private static volatile Workflow definition;
  private static volatile String workflowName;
  private static volatile String appId;
  private static volatile String definitionKey;
  private static volatile JqEvaluator jqEvaluator;
  private static volatile ObjectMapper mapper;
  private static volatile DaprClient daprClient;
  private static volatile WorkflowTaskOptions defaultTaskOptions;
  private static volatile String defaultPubsub;

  public static void init(
      Workflow definition,
      String workflowName,
      String appId,
      String definitionKey,
      JqEvaluator jqEvaluator,
      ObjectMapper mapper,
      DaprClient daprClient,
      WorkflowTaskOptions defaultTaskOptions,
      String defaultPubsub) {
    WorkflowSupport.definition = definition;
    WorkflowSupport.workflowName = workflowName;
    WorkflowSupport.appId = appId;
    WorkflowSupport.definitionKey = definitionKey;
    WorkflowSupport.jqEvaluator = jqEvaluator;
    WorkflowSupport.mapper = mapper;
    WorkflowSupport.daprClient = daprClient;
    WorkflowSupport.defaultTaskOptions = defaultTaskOptions;
    WorkflowSupport.defaultPubsub = defaultPubsub;
  }

  public static Workflow definition() {
    return require(definition, "definition");
  }

  public static String workflowName() {
    return require(workflowName, "workflowName");
  }

  public static String appId() {
    return require(appId, "appId");
  }

  /** Immutable versioned key for this pod's definition, e.g. {@code order-workflow@v3}. */
  public static String definitionKey() {
    return require(definitionKey, "definitionKey");
  }

  public static JqEvaluator jq() {
    return require(jqEvaluator, "jqEvaluator");
  }

  public static ObjectMapper mapper() {
    return require(mapper, "mapper");
  }

  /** Shared JSON Schema registry used to compile task input/output schemas. */
  public static SchemaRegistry schemaRegistry() {
    return SCHEMA_REGISTRY;
  }

  public static DaprClient daprClient() {
    return require(daprClient, "daprClient");
  }

  public static WorkflowTaskOptions defaultTaskOptions() {
    return require(defaultTaskOptions, "defaultTaskOptions");
  }

  public static String defaultPubsub() {
    return require(defaultPubsub, "defaultPubsub");
  }

  /**
   * Converts an Open Workflow Specification inline/ISO-8601 duration to a {@link Duration}. Shared
   * by every duration-bearing DSL field ({@code catch.retry.delay}, {@code listen} timeout, task
   * and workflow {@code timeout}, {@code retry.limit.attempt.duration}) so the conversion rule
   * lives in exactly one place.
   */
  public static Duration durationOf(TimeoutAfter after) {
    if (after == null) {
      return Duration.ZERO;
    }
    DurationInline inline = after.getDurationInline();
    if (inline != null) {
      return Duration.ofDays(inline.getDays())
          .plusHours(inline.getHours())
          .plusMinutes(inline.getMinutes())
          .plusSeconds(inline.getSeconds())
          .plusMillis(inline.getMilliseconds());
    }
    String literal =
        after.getDurationExpression() != null
            ? after.getDurationExpression()
            : after.getDurationLiteral();
    return (literal != null && !literal.isBlank()) ? Duration.parse(literal) : Duration.ZERO;
  }

  /**
   * The duration a task's {@code timeout} declares, inline or by reference into the document's
   * {@code use.timeouts}, or {@code null} when the task declares no {@code timeout}.
   */
  public static Duration taskTimeoutOf(TaskTimeout timeout) {
    if (timeout == null) {
      return null;
    }
    Timeout resolved =
        timeout.getTaskTimeoutDefinition() != null
            ? timeout.getTaskTimeoutDefinition()
            : namedTimeout(timeout.getTaskTimeoutReference());
    return resolved == null ? null : durationOf(resolved.getAfter());
  }

  /**
   * The duration a workflow document's top-level {@code timeout} declares, inline or by reference
   * into {@code use.timeouts}, or {@code null} when the document declares no {@code timeout}.
   */
  public static Duration workflowTimeoutOf(DoTimeout timeout) {
    if (timeout == null) {
      return null;
    }
    Timeout resolved =
        timeout.getTimeoutDefinition() != null
            ? timeout.getTimeoutDefinition()
            : namedTimeout(timeout.getTimeoutReference());
    return resolved == null ? null : durationOf(resolved.getAfter());
  }

  /**
   * A name resolved against the document's {@code use.timeouts}; fails loudly when unresolvable.
   */
  private static Timeout namedTimeout(String name) {
    UseTimeouts timeouts =
        definition().getUse() == null ? null : definition().getUse().getTimeouts();
    Timeout named =
        (timeouts == null || timeouts.getAdditionalProperties() == null)
            ? null
            : timeouts.getAdditionalProperties().get(name);
    if (named == null) {
      throw new IllegalStateException(
          "timeout '" + name + "' is not defined in the document's use.timeouts");
    }
    return named;
  }

  private static <T> T require(T value, String name) {
    if (value == null) {
      throw new IllegalStateException("WorkflowSupport." + name + " not initialised");
    }
    return value;
  }
}
