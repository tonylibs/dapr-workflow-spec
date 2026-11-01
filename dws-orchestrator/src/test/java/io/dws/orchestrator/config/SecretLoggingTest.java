package io.dws.orchestrator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.client.DaprClient;
import io.dapr.client.domain.ConfigurationItem;
import io.dapr.client.domain.HttpExtension;
import io.dapr.workflows.WorkflowActivityContext;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.error.StepInvocationException;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.logging.DwsJsonLogFormatter;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.dws.orchestrator.workflow.activity.AdminEventActivity;
import io.dws.orchestrator.workflow.activity.AdminEventRequest;
import io.dws.orchestrator.workflow.activity.CallRequest;
import io.dws.orchestrator.workflow.activity.CallServiceActivity;
import io.dws.orchestrator.workflow.activity.EmitEventActivity;
import io.dws.orchestrator.workflow.activity.EmitRequest;
import io.dws.orchestrator.workflow.activity.EvaluateSetActivity;
import io.dws.orchestrator.workflow.activity.EvaluateSetRequest;
import io.serverlessworkflow.api.types.Workflow;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * A run whose definition declares {@code use.secrets} must not put a resolved {@code SECRET_*}
 * value into any log record: the definition is loaded, the secrets are resolved from the
 * environment exactly as at startup, and every logging activity plus its failure paths is exercised
 * while a Logback {@link ListAppender} captures all output. Both the raw record (message and stack
 * trace, what text mode prints) and the JSON line are checked.
 */
class SecretLoggingTest {

  private static final String SECRET_VALUE = "s3cr3t-resolved-value-9f2c41";
  private static final String KEY = "secret-workflow@v1";
  private static final String STORE = "dws-definitions";

  private static final String DEFINITION =
      """
      document:
        dsl: 1.0.0
        namespace: examples
        name: secret-workflow
        version: '1.0.0'
      use:
        secrets: [apitoken]
      do:
        - useSecret:
            set:
              header: '${ $secrets.apitoken }'
      """;

  private final ObjectMapper mapper = new ObjectMapper();
  private final DaprClient client = mock(DaprClient.class);
  private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private Level previousLevel;

  @BeforeEach
  void captureAllLogging() {
    previousLevel = root.getLevel();
    root.setLevel(Level.TRACE);
    appender.start();
    root.addAppender(appender);
  }

  @AfterEach
  void restoreLogging() {
    root.detachAppender(appender);
    root.setLevel(previousLevel);
  }

  @Test
  void aRunWithDeclaredSecretsLogsNoResolvedSecretValue() throws Exception {
    when(client.getConfiguration(eq(STORE), eq(KEY)))
        .thenReturn(Mono.just(new ConfigurationItem(KEY, DEFINITION, "1")));
    Workflow definition = new WorkflowDefinitionLoader(client, STORE, KEY, 1, Duration.ZERO).load();
    Map<String, JsonNode> secrets =
        WorkflowRuntimeBootstrap.secretScope(
            definition, mapper, name -> "SECRET_apitoken".equals(name) ? SECRET_VALUE : null);
    assertThat(secrets.get("apitoken").textValue()).isEqualTo(SECRET_VALUE);
    WorkflowSupport.init(
        definition,
        "secret-workflow",
        "secret-workflow",
        KEY,
        new JqEvaluator(mapper),
        mapper,
        client,
        mock(WorkflowTaskOptions.class),
        "pubsub",
        secrets);

    // The set task reads the secret into data; the data is the activity result, never a log arg.
    JsonNode data =
        EvaluateSetActivity.apply(
            new EvaluateSetRequest("useSecret", mapper.createObjectNode(), Map.of()));
    assertThat(data.path("header").textValue()).isEqualTo(SECRET_VALUE);

    // Success and failure of every activity that logs, including the logged exception paths.
    when(client.invokeMethod(
            eq("billing"), eq("run"), any(), any(HttpExtension.class), eq(JsonNode.class)))
        .thenReturn(Mono.just(data))
        .thenReturn(Mono.error(new IllegalStateException("UNAVAILABLE: connection refused")));
    CallServiceActivity call = new CallServiceActivity();
    call.run(
        activityContext(CallRequest.class, new CallRequest("billing", "run", data, "i-1", null)));
    assertThatThrownBy(
            () ->
                call.run(
                    activityContext(
                        CallRequest.class, new CallRequest("billing", "run", data, "i-1", null))))
        .isInstanceOf(StepInvocationException.class);

    when(client.publishEvent(eq("pubsub"), eq("orders"), any())).thenReturn(Mono.empty());
    new EmitEventActivity()
        .run(activityContext(EmitRequest.class, new EmitRequest("pubsub", "orders", data)));
    when(client.publishEvent(eq("pubsub"), eq("dws.events"), any()))
        .thenReturn(Mono.error(new IllegalStateException("sidecar down")));
    new AdminEventActivity()
        .run(
            activityContext(
                AdminEventRequest.class, new AdminEventRequest("pubsub", "dws.events", data)));

    List<ILoggingEvent> events = List.copyOf(appender.list);
    assertThat(events)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(message -> message.contains("Loading workflow definition"))
        .anyMatch(message -> message.contains("Invoking app-id"))
        .anyMatch(message -> message.contains("Publishing to pubsub"))
        .anyMatch(message -> message.contains("Failed to publish admin event"));

    DwsJsonLogFormatter json = new DwsJsonLogFormatter();
    for (ILoggingEvent event : events) {
      assertThat(event.getFormattedMessage()).doesNotContain(SECRET_VALUE);
      assertThat(event.getArgumentArray() == null ? List.of() : List.of(event.getArgumentArray()))
          .noneMatch(argument -> String.valueOf(argument).contains(SECRET_VALUE));
      if (event.getThrowableProxy() != null) {
        assertThat(ThrowableProxyUtil.asString(event.getThrowableProxy()))
            .doesNotContain(SECRET_VALUE);
      }
      assertThat(json.format(event)).doesNotContain(SECRET_VALUE);
    }
  }

  private static <T> WorkflowActivityContext activityContext(Class<T> type, T input) {
    WorkflowActivityContext context = mock(WorkflowActivityContext.class);
    when(context.getInput(type)).thenReturn(input);
    return context;
  }
}
