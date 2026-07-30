package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.dapr.client.DaprClient;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.serverlessworkflow.api.types.Workflow;
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

  private static <T> T require(T value, String name) {
    if (value == null) {
      throw new IllegalStateException("WorkflowSupport." + name + " not initialised");
    }
    return value;
  }
}
