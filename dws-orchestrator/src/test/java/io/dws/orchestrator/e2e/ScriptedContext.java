package io.dws.orchestrator.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.durabletask.Task;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.workflow.activity.CallRequest;
import io.dws.orchestrator.workflow.activity.CallServiceActivity;
import io.dws.orchestrator.workflow.activity.EmitEventActivity;
import io.dws.orchestrator.workflow.activity.EmitRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A {@link WorkflowContext} mock scripted from one catalog {@link E2eCatalog.Scenario}:
 * activity calls pop the scenario's scripted results in order, external events resolve by
 * the listen task's name, and everything the interpreter does (service invocations, timers,
 * emits, completion) is recorded for assertion. An unscripted call or event fails fast
 * instead of silently returning null.
 */
final class ScriptedContext {

  private final WorkflowContext ctx = mock(WorkflowContext.class);
  private final List<String> callAppIds = new ArrayList<>();
  private final List<String> timers = new ArrayList<>();
  private final List<EmitRequest> emits = new ArrayList<>();
  private final AtomicReference<JsonNode> output = new AtomicReference<>();
  private int nextCall;

  @SuppressWarnings("unchecked")
  ScriptedContext(E2eCatalog.Scenario scenario, ObjectMapper mapper) {
    JsonNode input = scenario.input() == null ? mapper.createObjectNode() : scenario.input();
    when(ctx.getInput(JsonNode.class)).thenReturn(input);

    when(ctx.callActivity(eq(CallServiceActivity.class.getName()), any(),
        any(WorkflowTaskOptions.class), eq(JsonNode.class)))
        .thenAnswer(invocation -> {
          CallRequest request = invocation.getArgument(1);
          callAppIds.add(request.appId());
          if (nextCall >= scenario.calls().size()) {
            throw new AssertionError("unscripted service call to '" + request.appId()
                + "' (scenario '" + scenario.name() + "' scripts " + scenario.calls().size() + " calls)");
          }
          E2eCatalog.ScriptedCall scripted = scenario.calls().get(nextCall++);
          if (!scripted.appId().equals(request.appId())) {
            throw new AssertionError("scenario '" + scenario.name() + "' scripted a call to '"
                + scripted.appId() + "' but the interpreter invoked '" + request.appId() + "'");
          }
          Task<JsonNode> task = mock(Task.class);
          when(task.await()).thenReturn(scripted.result());
          return task;
        });

    when(ctx.callActivity(eq(EmitEventActivity.class.getName()), any(),
        any(WorkflowTaskOptions.class), eq(Void.class)))
        .thenAnswer(invocation -> {
          emits.add(invocation.getArgument(1, EmitRequest.class));
          return mock(Task.class);
        });

    when(ctx.createTimer(any(Duration.class))).thenAnswer(invocation -> {
      timers.add(invocation.getArgument(0, Duration.class).toString());
      return mock(Task.class);
    });

    when(ctx.waitForExternalEvent(anyString(), any(Duration.class), eq(JsonNode.class)))
        .thenAnswer(invocation -> {
          String eventName = invocation.getArgument(0, String.class);
          E2eCatalog.ScriptedEvent event = scenario.events().stream()
              .filter(e -> e.name().equals(eventName))
              .findFirst()
              .orElseThrow(() -> new AssertionError("unscripted external event '" + eventName
                  + "' — events[].name must match the listen task's name"));
          Task<JsonNode> task = mock(Task.class);
          when(task.await()).thenReturn(event.payload());
          return task;
        });

    doAnswer(invocation -> {
      output.set(invocation.getArgument(0, JsonNode.class));
      return null;
    }).when(ctx).complete(any());
  }

  WorkflowContext context() {
    return ctx;
  }

  List<String> recordedCallAppIds() {
    return callAppIds;
  }

  List<String> recordedTimers() {
    return timers;
  }

  List<EmitRequest> recordedEmits() {
    return emits;
  }

  JsonNode completedOutput() {
    return output.get();
  }
}
