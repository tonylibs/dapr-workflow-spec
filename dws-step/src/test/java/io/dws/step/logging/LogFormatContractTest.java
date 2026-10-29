package io.dws.step.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Cases 1-11 of the golden cases in {@code docs/log-format.md}; case 12 (FATAL) is not Logback. */
class LogFormatContractTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String SPAN_ID = "00f067aa0ba902b7";
  private static final String TIMESTAMP = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";
  private static final String JWT = "eyJhbGciOiJIUzI1NiJ9";

  private final JsonMapper mapper = JsonMapper.builder().build();
  private final DwsJsonLogFormatter formatter = new DwsJsonLogFormatter();
  private final Logger logger = new LoggerContext().getLogger("dws.golden");

  private String line(Level level, String message, Throwable error, Map<String, String> mdc) {
    LoggingEvent event =
        new LoggingEvent(Logger.class.getName(), logger, level, message, error, null);
    event.setMDCPropertyMap(mdc);
    return formatter.format(event);
  }

  private JsonNode parse(String line) {
    assertThat(line).endsWith("\n");
    assertThat(line.substring(0, line.length() - 1)).doesNotContain("\n");
    JsonNode json = mapper.readTree(line);
    assertThat(json.get("timestamp").asString()).matches(TIMESTAMP);
    assertThat(json.get("logger").asString()).isEqualTo("dws.golden");
    return json;
  }

  private void assertNoTraceContext(JsonNode json) {
    assertThat(json.has("trace_id")).isFalse();
    assertThat(json.has("span_id")).isFalse();
  }

  @Test
  void plain() {
    JsonNode json = parse(line(Level.INFO, "Starting Dapr workflow runtime", null, Map.of()));

    assertThat(json.get("severity").asString()).isEqualTo("INFO");
    assertThat(json.get("message").asString()).isEqualTo("Starting Dapr workflow runtime");
    assertNoTraceContext(json);
    assertThat(json.has("exception.type")).isFalse();
  }

  @Test
  void inSpan() {
    JsonNode json =
        parse(
            line(
                Level.INFO,
                "Invoking step",
                null,
                Map.of("trace_id", TRACE_ID, "span_id", SPAN_ID)));

    assertThat(json.get("message").asString()).isEqualTo("Invoking step");
    assertThat(json.get("trace_id").asString()).isEqualTo(TRACE_ID);
    assertThat(json.get("span_id").asString()).isEqualTo(SPAN_ID);
  }

  @Test
  void outOfSpan() {
    JsonNode json =
        parse(line(Level.WARN, "Definition store not reachable, retrying", null, Map.of()));

    assertThat(json.get("severity").asString()).isEqualTo("WARN");
    assertNoTraceContext(json);
  }

  @Test
  void invalidContext() {
    JsonNode allZero =
        parse(
            line(
                Level.INFO,
                "Invalid context",
                null,
                Map.of("trace_id", "0".repeat(32), "span_id", "0".repeat(16))));
    JsonNode traceOnly =
        parse(line(Level.INFO, "Invalid context", null, Map.of("trace_id", TRACE_ID)));

    assertNoTraceContext(allZero);
    assertNoTraceContext(traceOnly);
  }

  @Test
  void exception() {
    JsonNode json =
        parse(line(Level.ERROR, "Activity failed", new IllegalStateException("boom"), Map.of()));

    assertThat(json.get("severity").asString()).isEqualTo("ERROR");
    assertThat(json.get("exception.type").asString()).isEqualTo("java.lang.IllegalStateException");
    assertThat(json.get("exception.message").asString()).isEqualTo("boom");
    assertThat(json.get("exception.stacktrace").asString()).contains("boom").contains("\n\tat ");
  }

  @Test
  void multiLine() {
    JsonNode json = parse(line(Level.INFO, "line one\nline two", null, Map.of()));

    assertThat(json.get("message").asString()).isEqualTo("line one\nline two");
  }

  @Test
  void bearer() {
    String line =
        line(
            Level.INFO,
            "calling admin with Authorization: Bearer " + JWT + ".eyJzdWIiOiJkd3MifQ.c2lnbmF0dXJl",
            null,
            Map.of());

    assertThat(line).doesNotContain("eyJhbGciOi");
    assertThat(parse(line).get("message").asString())
        .isEqualTo("calling admin with Authorization: [REDACTED]");
  }

  @Test
  void bareBearer() {
    String line =
        line(Level.WARN, "upstream rejected Bearer " + JWT + ".e30.sig12345", null, Map.of());

    assertThat(line).doesNotContain("eyJhbGciOi");
    assertThat(parse(line).get("message").asString())
        .isEqualTo("upstream rejected Bearer [REDACTED]");
  }

  @Test
  void password() {
    String line = line(Level.INFO, "connecting with user=dws password=hunter2", null, Map.of());

    assertThat(line).doesNotContain("hunter2");
    assertThat(parse(line).get("message").asString())
        .isEqualTo("connecting with user=dws password=[REDACTED]");
  }

  @Test
  void jsonSecret() {
    String line =
        line(
            Level.INFO,
            "payload {\"client_secret\":\"s3cr3t-value\",\"scope\":\"read\"}",
            null,
            Map.of());

    assertThat(line).doesNotContain("s3cr3t-value");
    assertThat(parse(line).get("message").asString())
        .isEqualTo("payload {\"client_secret\":\"[REDACTED]\",\"scope\":\"read\"}");
  }

  @Test
  void exceptionSecret() {
    String line =
        line(
            Level.ERROR,
            "Call failed",
            new IllegalStateException("401 for token=abc12345"),
            Map.of());
    JsonNode json = parse(line);

    assertThat(line).doesNotContain("abc12345");
    assertThat(json.get("exception.message").asString()).isEqualTo("401 for token=[REDACTED]");
    assertThat(json.get("exception.stacktrace").asString()).contains("token=[REDACTED]");
  }

  /** Task 4.2: the secrets a failing call could carry never reach output from a step logger. */
  @Test
  void stepLoggerNeverEmitsBearerTokenOrPassword() {
    Logger bootstrap = new LoggerContext().getLogger("io.dws.step.config.StepRuntimeBootstrap");
    Exception failure =
        new IllegalStateException(
            "Authorization: Bearer " + JWT + ".e30.sig12345 rejected, password=hunter2");
    LoggingEvent event =
        new LoggingEvent(
            Logger.class.getName(),
            bootstrap,
            Level.ERROR,
            "Step activity worker terminated",
            failure,
            null);
    event.setMDCPropertyMap(Map.of());

    String line = formatter.format(event);

    assertThat(line).doesNotContain("eyJhbGciOi").doesNotContain("hunter2");
  }
}
