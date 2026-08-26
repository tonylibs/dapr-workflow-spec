package io.dws.controller.k8s;

import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.TaskKind;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Label/annotation vocabulary stamped on every managed resource. The cluster is the source of
 * truth, so these selectors are how the controller lists, rolls out and tears down stacks — there
 * is no internal database.
 */
public final class Labels {

  public static final String WORKFLOW = "dws.io/workflow";
  public static final String VERSION = "dws.io/version";
  public static final String MANAGED_BY = "dws.io/managed-by";
  public static final String MANAGED_BY_VALUE = "dws-controller";
  public static final String DRAIN = "dws.io/drain";
  public static final String STEP_TYPE = "dws.io/step-type";

  private Labels() {}

  public static Map<String, String> forPlan(DeploymentPlan plan) {
    return of(plan.workflow(), plan.versionId());
  }

  /**
   * The {@link #STEP_TYPE} slug for one {@link TaskKind}. Must match the values hand-written into
   * the example manifests under {@code dws-call-http/k8s/}, {@code dws-call-openapi/k8s/}, and
   * {@code dws-run/k8s/} — read those before changing this mapping.
   */
  public static String stepType(TaskKind kind) {
    return switch (kind) {
      case CALL_HTTP -> "call-http";
      case CALL_OPENAPI -> "call-openapi";
      case CALL_GRPC -> "call-grpc";
      case CALL_ASYNCAPI -> "call-asyncapi";
      case RUN_SHELL -> "run-shell";
      case RUN_SCRIPT_JS -> "run-script-js";
      case RUN_SCRIPT_PYTHON -> "run-script-python";
    };
  }

  public static Map<String, String> of(String workflow, String versionId) {
    Map<String, String> labels = new LinkedHashMap<>();
    labels.put(WORKFLOW, workflow);
    labels.put(VERSION, versionId);
    labels.put(MANAGED_BY, MANAGED_BY_VALUE);
    return labels;
  }

  /** Selector matching everything this controller manages. */
  public static Map<String, String> managed() {
    return Map.of(MANAGED_BY, MANAGED_BY_VALUE);
  }

  /** Selector matching one workflow's whole stack, across versions. */
  public static Map<String, String> workflow(String workflow) {
    return Map.of(MANAGED_BY, MANAGED_BY_VALUE, WORKFLOW, workflow);
  }

  /** Selector matching exactly one version of one workflow. */
  public static Map<String, String> version(String workflow, String versionId) {
    return Map.of(MANAGED_BY, MANAGED_BY_VALUE, WORKFLOW, workflow, VERSION, versionId);
  }
}
