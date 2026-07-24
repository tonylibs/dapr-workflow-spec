package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dapr.workflows.WorkflowContext;
import io.dws.orchestrator.workflow.activity.AdminEventRequest;

/**
 * Builds {@link AdminEventRequest}s carrying the CloudEvents-style envelope for orchestrator
 * instance/task lifecycle events (see {@code docs/events.md}).
 *
 * <p><strong>Replay determinism:</strong> every timestamp and id is derived from the workflow
 * context ({@link WorkflowContext#getCurrentInstant()} and {@link WorkflowContext#getInstanceId()}),
 * never from {@code Instant.now()} or a random generator, so envelopes are identical across replays.
 * The per-instance event {@code id} is a monotonic counter over the deterministic execution order.
 */
public final class AdminEventBuilder {

  static final String TOPIC = "dws.events";

  private final WorkflowContext ctx;
  private final ObjectMapper mapper;
  private final String source;
  private final String appId;
  private final String workflow;
  private final String version;
  private final String instanceId;
  private final String pubsub;
  private final String startedAt;

  private int seq;

  private AdminEventBuilder(WorkflowContext ctx, ObjectMapper mapper, String appId, String workflow,
                            String version, String instanceId, String pubsub, String startedAt) {
    this.ctx = ctx;
    this.mapper = mapper;
    this.source = "dws-orchestrator/" + appId;
    this.appId = appId;
    this.workflow = workflow;
    this.version = version;
    this.instanceId = instanceId;
    this.pubsub = pubsub;
    this.startedAt = startedAt;
  }

  /** Seeds a builder from {@link WorkflowSupport} and the context, capturing the instance start time. */
  public static AdminEventBuilder forContext(WorkflowContext ctx) {
    return new AdminEventBuilder(
        ctx,
        WorkflowSupport.mapper(),
        WorkflowSupport.appId(),
        WorkflowSupport.workflowName(),
        versionFromKey(WorkflowSupport.definitionKey()),
        ctx.getInstanceId(),
        WorkflowSupport.defaultPubsub(),
        ctx.getCurrentInstant().toString());
  }

  /** {@code order-workflow@v3} -> {@code v3}; a key without {@code @} is returned unchanged. */
  static String versionFromKey(String definitionKey) {
    int at = definitionKey.lastIndexOf('@');
    return at >= 0 ? definitionKey.substring(at + 1) : definitionKey;
  }

  public String startedAt() {
    return startedAt;
  }

  public AdminEventRequest instanceStarted() {
    ObjectNode data = instanceData();
    data.put("startedAt", startedAt);
    return envelope("io.dws.instance.started", data);
  }

  public AdminEventRequest instanceCompleted() {
    ObjectNode data = instanceData();
    data.put("startedAt", startedAt);
    data.put("endedAt", now());
    return envelope("io.dws.instance.completed", data);
  }

  public AdminEventRequest instanceFailed(String error) {
    ObjectNode data = instanceData();
    data.put("startedAt", startedAt);
    data.put("endedAt", now());
    data.put("error", error);
    return envelope("io.dws.instance.failed", data);
  }

  public AdminEventRequest taskStarted(String taskName, String taskType) {
    return envelope("io.dws.task.started", taskData(taskName, taskType));
  }

  public AdminEventRequest taskCompleted(String taskName, String taskType) {
    return envelope("io.dws.task.completed", taskData(taskName, taskType));
  }

  public AdminEventRequest taskFailed(String taskName, String taskType, String error) {
    ObjectNode data = taskData(taskName, taskType);
    data.put("error", error);
    return envelope("io.dws.task.failed", data);
  }

  private ObjectNode instanceData() {
    ObjectNode data = mapper.createObjectNode();
    data.put("instanceId", instanceId);
    data.put("workflow", workflow);
    data.put("version", version);
    data.put("appId", appId);
    return data;
  }

  private ObjectNode taskData(String taskName, String taskType) {
    ObjectNode data = mapper.createObjectNode();
    data.put("instanceId", instanceId);
    data.put("taskName", taskName);
    data.put("taskType", taskType);
    data.put("timestamp", now());
    return data;
  }

  private AdminEventRequest envelope(String type, ObjectNode data) {
    ObjectNode env = mapper.createObjectNode();
    env.put("id", instanceId + "-" + (++seq));
    env.put("source", source);
    env.put("type", type);
    env.put("time", now());
    env.put("datacontenttype", "application/json");
    env.set("data", data);
    return new AdminEventRequest(pubsub, TOPIC, env);
  }

  /** Replay-safe wall-clock: the workflow's current logical instant. */
  private String now() {
    return ctx.getCurrentInstant().toString();
  }
}
