package io.dws.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * One named task in a {@link WorkflowDef}. The active fields depend on {@link #type()};
 * every task carries a flow directive via {@code then} (preferred) or {@code next}.
 *
 * @param type        task kind (required)
 * @param service     CALL: target Dapr app-id to invoke
 * @param path        CALL: method path on the target service (default {@code run})
 * @param cases       SWITCH: ordered branches, first truthy {@code when} wins
 * @param defaultCase SWITCH: fallback target when no case matches
 * @param set         SET: map of output field to jq expression evaluated over current data
 * @param duration    WAIT: ISO-8601 duration (e.g. {@code PT5S})
 * @param event       LISTEN: external event name to wait for
 * @param timeout     LISTEN: ISO-8601 timeout for the wait
 * @param pubsub      EMIT: Dapr pub/sub component name
 * @param topic       EMIT: topic to publish current data to
 * @param then        flow directive: next task name (or {@code end})
 * @param next        alias for {@code then}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskDef(
    TaskType type,
    String service,
    String path,
    List<SwitchCase> cases,
    @JsonProperty("default") String defaultCase,
    Map<String, String> set,
    String duration,
    String event,
    String timeout,
    String pubsub,
    String topic,
    String then,
    String next
) {

  /** Method path for CALL tasks, defaulting to {@code run}. */
  public String pathOrDefault() {
    return (path == null || path.isBlank()) ? "run" : path;
  }

  /** Resolved flow target, preferring {@code then} over the {@code next} alias. */
  public String flowTarget() {
    if (then != null && !then.isBlank()) {
      return then;
    }
    return next;
  }
}
