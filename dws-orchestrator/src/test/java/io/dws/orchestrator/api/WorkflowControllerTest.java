package io.dws.orchestrator.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.workflows.client.DaprWorkflowClient;
import io.dapr.workflows.client.WorkflowRuntimeStatus;
import io.dapr.workflows.client.WorkflowState;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.grpc.Status;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Start/status contract of {@link WorkflowController} (workflow-start-contract spec, owner
 * dws-orchestrator). Runs the controller in a standalone MockMvc against a mocked {@link
 * DaprWorkflowClient}; no Spring context, no sidecar.
 */
class WorkflowControllerTest {

  private static final String WORKFLOW = "order-sync";
  private static final String FIRE_ID = "dws-sched:order-sync:20261105T000000Z";

  private final ObjectMapper mapper = new ObjectMapper();
  private DaprWorkflowClient client;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    WorkflowSupport.init(
        null, WORKFLOW, "dws-order-sync", WORKFLOW, null, mapper, null, null, null);
    client = mock(DaprWorkflowClient.class);
    // The Java SDK never returns null for an unknown ID (ADR 0013, Amendment 1); default to that.
    WorkflowState unknown = unknownState();
    when(client.getWorkflowState(anyString(), anyBoolean())).thenReturn(unknown);
    mvc = MockMvcBuilders.standaloneSetup(new WorkflowController(client, mapper)).build();
  }

  @Test
  void startWithoutInstanceIdIsUnchanged() throws Exception {
    when(client.scheduleNewWorkflow(eq(WORKFLOW), any(Object.class))).thenReturn("generated-1");

    mvc.perform(post("/workflows/order-sync/instances").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.instanceId").value("generated-1"))
        .andExpect(jsonPath("$.workflowName").value(WORKFLOW));

    verify(client, never()).getWorkflowState(anyString(), anyBoolean());
    verify(client, never()).scheduleNewWorkflow(anyString(), any(), anyString());
  }

  @Test
  void startWithNewInstanceIdUsesIt() throws Exception {
    when(client.scheduleNewWorkflow(eq(WORKFLOW), any(Object.class), eq(FIRE_ID)))
        .thenReturn(FIRE_ID);

    mvc.perform(
            post("/workflows/order-sync/instances")
                .queryParam("instanceId", FIRE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.instanceId").value(FIRE_ID))
        .andExpect(jsonPath("$.workflowName").value(WORKFLOW));

    ArgumentCaptor<Object> input = ArgumentCaptor.forClass(Object.class);
    verify(client).scheduleNewWorkflow(eq(WORKFLOW), input.capture(), eq(FIRE_ID));
    assertThat(input.getValue()).isInstanceOf(JsonNode.class);
    assertThat(((JsonNode) input.getValue()).isEmpty()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = WorkflowRuntimeStatus.class,
      names = {"RUNNING", "COMPLETED", "TERMINATED", "FAILED", "PENDING", "SUSPENDED"})
  void existingInstanceInAnyStatusIsReturnedNotRestarted(WorkflowRuntimeStatus runtimeStatus)
      throws Exception {
    WorkflowState existing = existingState(runtimeStatus);
    when(client.getWorkflowState(FIRE_ID, false)).thenReturn(existing);

    mvc.perform(
            post("/workflows/order-sync/instances")
                .queryParam("instanceId", FIRE_ID)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instanceId").value(FIRE_ID))
        .andExpect(jsonPath("$.workflowName").value(WORKFLOW));

    verify(client, never()).scheduleNewWorkflow(anyString(), any(), anyString());
    verify(client, never()).scheduleNewWorkflow(anyString(), any());
  }

  @Test
  void concurrentAlreadyExistsFromDaprMapsToOk() throws Exception {
    when(client.scheduleNewWorkflow(eq(WORKFLOW), any(Object.class), eq(FIRE_ID)))
        .thenThrow(
            Status.ALREADY_EXISTS
                .withDescription("an active workflow with ID '" + FIRE_ID + "' already exists")
                .asRuntimeException());

    mvc.perform(
            post("/workflows/order-sync/instances")
                .queryParam("instanceId", FIRE_ID)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instanceId").value(FIRE_ID))
        .andExpect(jsonPath("$.workflowName").value(WORKFLOW));
  }

  @Test
  void alreadyExistsWrappedByTheSdkIsStillRecognised() throws Exception {
    when(client.scheduleNewWorkflow(eq(WORKFLOW), any(Object.class), eq(FIRE_ID)))
        .thenThrow(new IllegalStateException("start failed", Status.ALREADY_EXISTS.asException()));

    mvc.perform(
            post("/workflows/order-sync/instances")
                .queryParam("instanceId", FIRE_ID)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instanceId").value(FIRE_ID));
  }

  @Test
  void unknownIdShapeFromTheJavaSdkIsNotMistakenForAnExistingInstance() throws Exception {
    // getWorkflowState returns name '', RUNNING, createdAt epoch for an unknown ID; setUp's
    // default.
    when(client.scheduleNewWorkflow(eq(WORKFLOW), any(Object.class), eq(FIRE_ID)))
        .thenReturn(FIRE_ID);

    mvc.perform(
            post("/workflows/order-sync/instances")
                .queryParam("instanceId", FIRE_ID)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted());

    verify(client).getWorkflowState(FIRE_ID, false);
    verify(client).scheduleNewWorkflow(eq(WORKFLOW), any(Object.class), eq(FIRE_ID));
  }

  @Test
  void nullStateIsTreatedAsNotFound() throws Exception {
    when(client.getWorkflowState(FIRE_ID, false)).thenReturn(null);
    when(client.scheduleNewWorkflow(eq(WORKFLOW), any(Object.class), eq(FIRE_ID)))
        .thenReturn(FIRE_ID);

    mvc.perform(
            post("/workflows/order-sync/instances")
                .queryParam("instanceId", FIRE_ID)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "bad id/with spaces", "a/b", "a b", "a?b", "ünïcode", "a\tb"})
  void invalidInstanceIdIsRejected(String instanceId) throws Exception {
    mvc.perform(
            post("/workflows/order-sync/instances")
                .queryParam("instanceId", instanceId)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());

    verify(client, never()).getWorkflowState(anyString(), anyBoolean());
    verify(client, never()).scheduleNewWorkflow(anyString(), any(), anyString());
    verify(client, never()).scheduleNewWorkflow(anyString(), any());
  }

  @Test
  void instanceIdLongerThan256CharactersIsRejected() throws Exception {
    mvc.perform(
            post("/workflows/order-sync/instances")
                .queryParam("instanceId", "a".repeat(257))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());

    verify(client, never()).scheduleNewWorkflow(anyString(), any(), anyString());
  }

  @Test
  void instanceIdOfExactly256CharactersIsAccepted() throws Exception {
    String id = "a".repeat(256);
    when(client.scheduleNewWorkflow(eq(WORKFLOW), any(Object.class), eq(id))).thenReturn(id);

    mvc.perform(
            post("/workflows/order-sync/instances")
                .queryParam("instanceId", id)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted());
  }

  @Test
  void otherWorkflowNameIsStillNotFound() throws Exception {
    mvc.perform(
            post("/workflows/other/instances")
                .queryParam("instanceId", FIRE_ID)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());

    verify(client, never()).scheduleNewWorkflow(anyString(), any(), anyString());
  }

  @Test
  void otherSchedulingFailuresAreNotSwallowed() {
    when(client.scheduleNewWorkflow(eq(WORKFLOW), any(Object.class), eq(FIRE_ID)))
        .thenThrow(Status.UNAVAILABLE.asRuntimeException());

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                mvc.perform(
                    post("/workflows/order-sync/instances")
                        .queryParam("instanceId", FIRE_ID)
                        .contentType(MediaType.APPLICATION_JSON)))
        .hasRootCauseInstanceOf(io.grpc.StatusRuntimeException.class);
  }

  @Test
  void statusOfUnknownInstanceIsNotFound() throws Exception {
    mvc.perform(get("/workflows/instances/" + FIRE_ID)).andExpect(status().isNotFound());
  }

  @Test
  void statusOfExistingInstanceIsReturned() throws Exception {
    WorkflowState existing = existingState(WorkflowRuntimeStatus.COMPLETED);
    when(existing.getSerializedOutput()).thenReturn("{\"ok\":true}");
    when(client.getWorkflowState(FIRE_ID, true)).thenReturn(existing);

    mvc.perform(get("/workflows/instances/" + FIRE_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instanceId").value(FIRE_ID))
        .andExpect(jsonPath("$.runtimeStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.output.ok").value(true));
  }

  private static WorkflowState unknownState() {
    WorkflowState state = mock(WorkflowState.class);
    when(state.getName()).thenReturn("");
    when(state.getRuntimeStatus()).thenReturn(WorkflowRuntimeStatus.RUNNING);
    when(state.getCreatedAt()).thenReturn(Instant.EPOCH);
    return state;
  }

  private static WorkflowState existingState(WorkflowRuntimeStatus runtimeStatus) {
    WorkflowState state = mock(WorkflowState.class);
    when(state.getName()).thenReturn(WORKFLOW);
    when(state.getWorkflowId()).thenReturn(FIRE_ID);
    when(state.getRuntimeStatus()).thenReturn(runtimeStatus);
    when(state.getCreatedAt()).thenReturn(Instant.parse("2026-11-05T00:00:01Z"));
    return state;
  }
}
