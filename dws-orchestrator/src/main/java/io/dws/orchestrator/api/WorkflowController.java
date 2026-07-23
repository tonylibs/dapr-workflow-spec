package io.dws.orchestrator.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.workflows.client.DaprWorkflowClient;
import io.dapr.workflows.client.WorkflowState;
import io.dws.orchestrator.api.dto.InstanceStatusResponse;
import io.dws.orchestrator.api.dto.StartInstanceResponse;
import io.dws.orchestrator.model.WorkflowDef;
import io.dws.orchestrator.registry.DefinitionRegistry;
import io.dws.orchestrator.workflow.InterpreterWorkflow;
import io.dws.orchestrator.workflow.WorkflowInput;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public control-plane API for starting instances of loaded workflows, checking their status,
 * and raising external events consumed by LISTEN tasks.
 *
 * <p>Request bodies are accepted as raw JSON strings and parsed with the orchestrator's own
 * Jackson mapper, decoupling the API from the web layer's serializer configuration.
 */
@RestController
@RequestMapping("/workflows")
public class WorkflowController {

  private final DaprWorkflowClient workflowClient;
  private final DefinitionRegistry registry;
  private final ObjectMapper mapper;

  public WorkflowController(DaprWorkflowClient workflowClient,
                            DefinitionRegistry registry,
                            @Qualifier("orchestratorObjectMapper") ObjectMapper mapper) {
    this.workflowClient = workflowClient;
    this.registry = registry;
    this.mapper = mapper;
  }

  /** Start a new instance of {@code name}, pinning its latest loaded definition version. */
  @PostMapping("/{name}/instances")
  public ResponseEntity<StartInstanceResponse> start(@PathVariable String name,
                                                     @RequestBody(required = false) String body) {
    WorkflowDef def = registry.findLatest(name)
        .orElseThrow(() -> new NotFoundException("no workflow named '" + name + "'"));

    WorkflowInput input = new WorkflowInput(def.name(), def.version(), parseBody(body));
    String instanceId = workflowClient.scheduleNewWorkflow(InterpreterWorkflow.class, input);

    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(new StartInstanceResponse(instanceId, def.name(), def.version()));
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
