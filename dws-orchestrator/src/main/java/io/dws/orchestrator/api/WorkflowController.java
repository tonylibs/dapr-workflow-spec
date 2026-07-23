package io.dws.orchestrator.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.workflows.client.DaprWorkflowClient;
import io.dapr.workflows.client.WorkflowState;
import io.dws.orchestrator.api.dto.InstanceStatusResponse;
import io.dws.orchestrator.api.dto.StartInstanceResponse;
import io.dws.orchestrator.workflow.WorkflowSupport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public control-plane API. This pod serves exactly one workflow definition; the start endpoint
 * accepts only that workflow's name and rejects any other with 404. The definition version is
 * pinned by the pod itself, so it is not carried in the instance input.
 *
 * <p>Request bodies are accepted as raw JSON strings and parsed with the orchestrator's own
 * Jackson mapper, decoupling the API from the web layer's serializer configuration.
 */
@RestController
@RequestMapping("/workflows")
public class WorkflowController {

  private final DaprWorkflowClient workflowClient;
  private final ObjectMapper mapper;

  public WorkflowController(DaprWorkflowClient workflowClient,
                            @Qualifier("orchestratorObjectMapper") ObjectMapper mapper) {
    this.workflowClient = workflowClient;
    this.mapper = mapper;
  }

  /** Start a new instance of this pod's workflow. Any other name yields 404. */
  @PostMapping("/{name}/instances")
  public ResponseEntity<StartInstanceResponse> start(@PathVariable String name,
                                                     @RequestBody(required = false) String body) {
    String loaded = WorkflowSupport.workflowName();
    if (!loaded.equals(name)) {
      throw new NotFoundException("this orchestrator serves only workflow '" + loaded + "'");
    }
    JsonNode data = parseBody(body);
    String instanceId = workflowClient.scheduleNewWorkflow(loaded, data);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(new StartInstanceResponse(instanceId, loaded));
  }

  /** Fetch the current status (and output, if completed) of an instance. */
  @GetMapping("/instances/{id}")
  public InstanceStatusResponse status(@PathVariable String id) {
    WorkflowState state = workflowClient.getWorkflowState(id, true);
    if (state == null) {
      throw new NotFoundException("no workflow instance '" + id + "'");
    }
    return new InstanceStatusResponse(
        id,
        String.valueOf(state.getRuntimeStatus()),
        parseOutput(state.getSerializedOutput()));
  }

  /** Raise an external event that a LISTEN task in the instance may be waiting for. */
  @PostMapping("/instances/{id}/events/{event}")
  public ResponseEntity<Void> raiseEvent(@PathVariable String id,
                                         @PathVariable String event,
                                         @RequestBody(required = false) String body) {
    workflowClient.raiseEvent(id, event, parseBody(body));
    return ResponseEntity.accepted().build();
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
