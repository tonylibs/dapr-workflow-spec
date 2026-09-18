package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Input to {@link CallServiceActivity}: which Dapr app-id to invoke, on which method path, and the
 * current workflow data to POST.
 *
 * <p>{@code workflowInstanceId} and {@code iterationIndex} exist solely so the activity can carry
 * them as outbound HTTP headers ({@code X-Dws-Workflow-Instance-Id} / {@code X-Dws-Iteration-Index}
 * — see {@link CallServiceActivity}) for {@code call: a2a}'s deterministic {@code messageId}
 * derivation (ADR 0004 Decision 8): {@code workflowInstanceId} is always populated — it is the
 * <em>root</em> workflow instance id threaded through {@link
 * io.dws.orchestrator.workflow.DispatchContext}, not necessarily whatever {@code
 * ctx.getInstanceId()} the dispatching child instance would report for itself — while {@code
 * iterationIndex} is {@code null} for a call task that is not nested inside a {@code for} loop, and
 * otherwise a stable, opaque encoding of the enclosing loop(s)' iteration position (see {@link
 * io.dws.orchestrator.workflow.DispatchContext#iterationIndexEncoded()}). {@code dws-call-a2a}
 * never parses this value; it only interpolates it into its {@code messageId} derivation seed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CallRequest(
    String appId, String path, JsonNode data, String workflowInstanceId, String iterationIndex)
    implements StepRequest {}
