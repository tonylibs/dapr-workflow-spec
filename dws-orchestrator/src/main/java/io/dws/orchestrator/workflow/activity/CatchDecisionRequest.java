package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A failure raised inside a try task's body, offered to that task's catch clause.
 *
 * <p>Only the failure's <em>message</em> is carried, not the exception: a failure raised inside an
 * activity reaches the workflow method as an opaque activity failure, so the message is all there
 * is. {@code failedTaskName} is the enclosing scope's best guess, used only when the message does
 * not name its own task.
 *
 * <p>Both clock values are supplied by the caller from the workflow context's replay-safe instant
 * rather than read inside the activity, so {@code limit.duration} accounting is stable across
 * replay.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatchDecisionRequest(
    String tryTaskName,
    String failedTaskName,
    String failureMessage,
    int attempt,
    long firstFailureEpochMillis,
    long nowEpochMillis,
    JsonNode data,
    JsonNode context) {}
