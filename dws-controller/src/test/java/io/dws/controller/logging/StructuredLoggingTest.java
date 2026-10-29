package io.dws.controller.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.client.DaprClient;
import io.dws.controller.k8s.ObservabilityFlags;
import io.dws.controller.k8s.ObservabilitySettings;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

/**
 * The {@code DWS_LOG_FORMAT} switch and the secret guarantee, through the real logging stack: what
 * a controller log call actually writes to stdout once JSON mode is installed.
 */
class StructuredLoggingTest {

  private static final String BEARER_TOKEN = "eyJhbGciOiJIUzI1NiJ9.e30.sig12345";
  private static final String[] PROPERTIES = {
    "quarkus.log.console.enabled", "quarkus.banner.enabled"
  };

  private final Logger root = Logger.getLogger("");
  private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
  private PrintStream originalOut;
  private List<Handler> handlersBefore;

  @BeforeEach
  void captureStdout() {
    originalOut = System.out;
    System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
    // Quarkus turns its own console handler off in JSON mode; mirror that so only ours writes.
    handlersBefore = List.of(root.getHandlers());
    handlersBefore.forEach(root::removeHandler);
  }

  @AfterEach
  void restore() {
    Arrays.stream(root.getHandlers()).forEach(root::removeHandler);
    handlersBefore.forEach(root::addHandler);
    for (String property : PROPERTIES) {
      System.clearProperty(property);
    }
    System.setOut(originalOut);
  }

  private List<String> stdoutLines() {
    return stdout.toString(StandardCharsets.UTF_8).lines().toList();
  }

  @ParameterizedTest
  @ValueSource(strings = {"json", "JSON", "Json", "  json  ", "\tjson\n"})
  @DisplayName("json is recognised trimmed and case-insensitively")
  void jsonIsRecognised(String value) {
    assertThat(StructuredLogging.isJson(value)).isTrue();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  ", "text", "yaml", "jsonl", "json5", "js on"})
  @DisplayName("unset, empty, text and anything else are text mode")
  void everythingElseIsText(String value) {
    assertThat(StructuredLogging.isJson(value)).isFalse();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "text", "yaml"})
  @DisplayName("text mode leaves logging untouched")
  void textModeChangesNothing(String value) {
    assertThat(StructuredLogging.install(value)).isFalse();

    assertThat(root.getHandlers()).isEmpty();
    assertThat(System.getProperty("quarkus.log.console.enabled")).isNull();
  }

  @Test
  @DisplayName(
      "json mode writes records to stdout as single JSON lines and turns the Quarkus console off")
  void jsonModeWritesJsonLines() throws Exception {
    assertThat(StructuredLogging.install("json")).isTrue();
    assertThat(System.getProperty("quarkus.log.console.enabled")).isEqualTo("false");

    org.jboss.logging.Logger.getLogger("dws.golden").warn("line one\nline two");

    assertThat(stdoutLines()).hasSize(1);
    JsonNode json = new ObjectMapper().readTree(stdoutLines().get(0));
    assertThat(json.path("severity").asText()).isEqualTo("WARN");
    assertThat(json.path("logger").asText()).isEqualTo("dws.golden");
    assertThat(json.path("message").asText()).isEqualTo("line one\nline two");
  }

  @Test
  @DisplayName("a bearer token in a controller log call never reaches JSON output")
  void bearerTokenNeverAppearsInJsonOutput() throws Exception {
    StructuredLogging.install("json");
    // The real call site: ObservabilityFlags logs the store failure's message, and a Dapr/HTTP
    // client error can carry the request's Authorization header.
    DaprClient daprClient = mock(DaprClient.class);
    when(daprClient.getConfiguration(
            ObservabilitySettings.STORE,
            ObservabilitySettings.ENABLED_KEY,
            ObservabilitySettings.INSTRUMENTATION_KEY))
        .thenReturn(
            Mono.error(
                new IllegalStateException(
                    "401 calling store, Authorization: Bearer " + BEARER_TOKEN)));

    new ObservabilityFlags(daprClient, mock(KubernetesClient.class)).resolve("dws");
    org.jboss.logging.Logger.getLogger("dws.golden")
        .error(
            "upstream said Bearer " + BEARER_TOKEN, new IllegalStateException("password=hunter2"));

    String output = stdout.toString(StandardCharsets.UTF_8);
    assertThat(stdoutLines()).hasSize(2);
    assertThat(output)
        .doesNotContain("eyJhbGciOi")
        .doesNotContain("sig12345")
        .doesNotContain("hunter2");
    for (String line : stdoutLines()) {
      assertThat(new ObjectMapper().readTree(line).path("message").asText()).contains("[REDACTED]");
    }
  }
}
