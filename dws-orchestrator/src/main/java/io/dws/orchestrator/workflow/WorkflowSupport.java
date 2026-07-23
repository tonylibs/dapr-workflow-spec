package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.client.DaprClient;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.registry.DefinitionRegistry;

/**
 * Static bridge between Spring-managed collaborators and the Dapr workflow runtime.
 *
 * <p>{@code WorkflowRuntimeBuilder} instantiates workflow and activity classes reflectively
 * via their no-arg constructors, so those classes cannot receive Spring injection. This holder
 * is populated once during bootstrap and read by {@link InterpreterWorkflow} and the activities.
 * All held collaborators are effectively immutable and thread-safe.
 */
public final class WorkflowSupport {

  private static volatile DefinitionRegistry registry;
  private static volatile JqEvaluator jqEvaluator;
  private static volatile ObjectMapper mapper;
  private static volatile DaprClient daprClient;
  private static volatile WorkflowTaskOptions defaultTaskOptions;
  private static volatile String defaultPubsub;

  private WorkflowSupport() {
  }

  public static void init(DefinitionRegistry registry,
                          JqEvaluator jqEvaluator,
                          ObjectMapper mapper,
                          DaprClient daprClient,
                          WorkflowTaskOptions defaultTaskOptions,
                          String defaultPubsub) {
    WorkflowSupport.registry = registry;
    WorkflowSupport.jqEvaluator = jqEvaluator;
    WorkflowSupport.mapper = mapper;
    WorkflowSupport.daprClient = daprClient;
    WorkflowSupport.defaultTaskOptions = defaultTaskOptions;
    WorkflowSupport.defaultPubsub = defaultPubsub;
  }

  public static DefinitionRegistry registry() {
    return require(registry, "registry");
  }

  public static JqEvaluator jq() {
    return require(jqEvaluator, "jqEvaluator");
  }

  public static ObjectMapper mapper() {
    return require(mapper, "mapper");
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
