package io.dws.orchestrator.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.workflows.client.DaprWorkflowClient;
import io.dapr.workflows.client.WorkflowState;
import io.dws.orchestrator.api.dto.InstanceStatusResponse;
import io.dws.orchestrator.api.dto.StartInstanceResponse;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.grpc.Status;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public control-plane API. This pod serves exactly one workflow definition; the start endpoint
 * accepts only that workflow's name and rejects any other with 404. The definition version is
 * pinned by the pod itself, so it is not carried in the instance input.
 *
 * <p>Request bodies are accepted as raw JSON strings and parsed with the orchestrator's own Jackson
 * mapper, decoupling the API from the web layer's serializer configuration.
 *
 * <p><b>Idempotent start (workflow-start-contract).</b> A caller may name the instance with the
 * {@code instanceId} query parameter; trigger components (dws-schedule-time, later
 * dws-schedule-event) send a deterministic {@code <source>:<workflow>:<fire-key>} so a repeated
 * delivery of one fire starts at most one instance. Dapr itself only refuses a duplicate ID while
 * that instance is <em>running</em>: a completed, failed or terminated instance with the same ID is
 * silently replaced and run again (ADR 0013, Amendment 1). So the existence check here is what
 * makes the start idempotent, and it treats an instance in any runtime status as existing. The
 * check and the start are not atomic; if a concurrent start wins the race while the instance is
 * still running, Dapr's {@code ALREADY_EXISTS} is mapped to the same {@code 200}. The remaining
 * window (a racing instance that already completed) is closed on the scheduler side by its
 * state-store start claim.
 */
@RestController
@RequestMapping("/workflows")
public class WorkflowController {

  /** Caller-supplied instance IDs: 1-256 characters, safe in a URL path and a Dapr state key. */
  private static final Pattern INSTANCE_ID = Pattern.compile("[A-Za-z0-9:._-]{1,256}");

  private final DaprWorkflowClient workflowClient;
  private final ObjectMapper mapper;

  public WorkflowController(
      DaprWorkflowClient workflowClient,
      @Qualifier("orchestratorObjectMapper") ObjectMapper mapper) {
    this.workflowClient = workflowClient;
    this.mapper = mapper;
  }

  /**
   * Start an instance of this pod's workflow. Any other name yields 404.
   *
   * <p>Without {@code instanceId} the ID is generated and the response is {@code 202}, as before.
   * With a valid {@code instanceId} the instance is started under that ID ({@code 202}) unless one
   * already exists, in which case nothing is started and {@code 200} names the existing instance.
   * An invalid {@code instanceId} is rejected with {@code 400}.
   */
  @PostMapping("/{name}/instances")
  public ResponseEntity<StartInstanceResponse> start(
      @PathVariable String name,
      @RequestParam(name = "instanceId", required = false) String instanceId,
      @RequestBody(required = false) String body) {
    String loaded = WorkflowSupport.workflowName();
    if (!loaded.equals(name)) {
      throw new NotFoundException("this orchestrator serves only workflow '" + loaded + "'");
    }
    if (instanceId != null && !INSTANCE_ID.matcher(instanceId).matches()) {
      throw new BadRequestException("instanceId must be 1-256 characters from [A-Za-z0-9:._-]");
    }
    JsonNode data = parseBody(body);
    if (instanceId == null) {
      String generated = workflowClient.scheduleNewWorkflow(loaded, data);
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(new StartInstanceResponse(generated, loaded));
    }
    if (exists(workflowClient.getWorkflowState(instanceId, false))) {
      return ResponseEntity.ok(new StartInstanceResponse(instanceId, loaded));
    }
    try {
      String started = workflowClient.scheduleNewWorkflow(loaded, data, instanceId);
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(new StartInstanceResponse(started, loaded));
    } catch (RuntimeException e) {
      if (Status.fromThrowable(e).getCode() == Status.Code.ALREADY_EXISTS) {
        return ResponseEntity.ok(new StartInstanceResponse(instanceId, loaded));
      }
      throw e;
    }
  }

  /** Fetch the current status (and output, if completed) of an instance. */
  @GetMapping("/instances/{id}")
  public InstanceStatusResponse status(@PathVariable String id) {
    WorkflowState state = workflowClient.getWorkflowState(id, true);
    if (!exists(state)) {
      throw new NotFoundException("no workflow instance '" + id + "'");
    }
    return new InstanceStatusResponse(
        id, String.valueOf(state.getRuntimeStatus()), parseOutput(state.getSerializedOutput()));
  }

  /** Raise an external event that a LISTEN task in the instance may be waiting for. */
  @PostMapping("/instances/{id}/events/{event}")
  public ResponseEntity<Void> raiseEvent(
      @PathVariable String id,
      @PathVariable String event,
      @RequestBody(required = false) String body) {
    workflowClient.raiseEvent(id, event, parseBody(body));
    return ResponseEntity.accepted().build();
  }

  /**
   * Whether a state returned by {@code getWorkflowState} describes a real instance. The Java SDK
   * never returns {@code null} for an unknown ID: it returns a state with an empty name, status
   * {@code RUNNING} and an epoch {@code createdAt} (ADR 0013, Amendment 1). So "not found" is that
   * shape, not {@code state == null}; a {@code null} is still treated as not found defensively.
   */
  static boolean exists(WorkflowState state) {
    return Optional.ofNullable(state)
        .filter(s -> s.getName() != null && !s.getName().isEmpty())
        .map(WorkflowState::getCreatedAt)
        .filter(createdAt -> !Instant.EPOCH.equals(createdAt))
        .isPresent();
  }

  private JsonNode parseBody(String body) {
    if (body == null || body.isBlank()) {
      return mapper.createObjectNode();
    }
    try {
      return mapper.readTree(body);
    } catch (JsonProcessingException e) {
      throw new BadRequestException("request body is not valid JSON: " + e.getOriginalMessage());
    }
  }

  private Object parseOutput(String serialized) {
    if (serialized == null || serialized.isBlank()) {
      return null;
    }
    try {
      return mapper.readValue(serialized, Object.class);
    } catch (JsonProcessingException e) {
      return serialized;
    }
  }
}
