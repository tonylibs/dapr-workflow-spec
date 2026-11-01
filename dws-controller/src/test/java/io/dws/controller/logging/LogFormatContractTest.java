package io.dws.controller.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Conformance of {@link JsonLogFormatter} to {@code docs/log-format.md}: the twelve golden cases,
 * compared as parsed objects. Every case must also be exactly one {@code \n}-terminated line.
 */
class LogFormatContractTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String SPAN_ID = "00f067aa0ba902b7";
  private static final String LOGGER = "dws.golden";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final JsonLogFormatter FORMATTER = new JsonLogFormatter();

  private static ExtLogRecord record(Level level, String message) {
    ExtLogRecord record = new ExtLogRecord(level, message, ExtLogRecord.FormatStyle.PRINTF, LOGGER);
    record.setLoggerName(LOGGER);
    return record;
  }

  private static ExtLogRecord inSpan(ExtLogRecord record, String traceId, String spanId) {
    record.putMdc("trace_id", traceId);
    record.putMdc("span_id", spanId);
    return record;
  }

  /** Formats {@code record}, asserting the one-line rule, and returns the raw line. */
  private static String line(LogRecord record) {
    String line = FORMATTER.format(record);
    assertThat(line).endsWith("\n");
    assertThat(line.indexOf('\n')).as("exactly one line").isEqualTo(line.length() - 1);
    return line;
  }

  private static JsonNode parse(String line) throws Exception {
    return JSON.readTree(line);
  }

  private static List<String> keys(JsonNode node) {
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  /** The four required keys, exact severity/message, no trace context unless the case adds it. */
  private static void assertEnvelope(JsonNode json, String severity, String message) {
    assertThat(json.path("timestamp").asText())
        .matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$");
    assertThat(json.path("severity").asText()).isEqualTo(severity);
    assertThat(json.path("logger").asText()).isEqualTo(LOGGER);
    assertThat(json.path("message").asText()).isEqualTo(message);
  }

  @Test
  @DisplayName("1 plain: four keys, no trace context")
  void plain() throws Exception {
    JsonNode json =
        parse(line(record(org.jboss.logmanager.Level.INFO, "Starting Dapr workflow runtime")));

    assertEnvelope(json, "INFO", "Starting Dapr workflow runtime");
    assertThat(keys(json)).containsExactly("timestamp", "severity", "logger", "message");
  }

  @Test
  @DisplayName("2 in-span: trace_id and span_id are exact")
  void inSpan() throws Exception {
    JsonNode json =
        parse(
            line(
                inSpan(
                    record(org.jboss.logmanager.Level.INFO, "Invoking step"), TRACE_ID, SPAN_ID)));

    assertEnvelope(json, "INFO", "Invoking step");
    assertThat(json.path("trace_id").asText()).isEqualTo(TRACE_ID);
    assertThat(json.path("span_id").asText()).isEqualTo(SPAN_ID);
  }

  @Test
  @DisplayName("3 out-of-span: WARN, no ids")
  void outOfSpan() throws Exception {
    JsonNode json =
        parse(
            line(
                record(
                    org.jboss.logmanager.Level.WARN, "Definition store not reachable, retrying")));

    assertEnvelope(json, "WARN", "Definition store not reachable, retrying");
    assertThat(json.has("trace_id")).isFalse();
    assertThat(json.has("span_id")).isFalse();
  }

  @ParameterizedTest(name = "4 invalid context: trace={0} span={1}")
  @CsvSource({
    "00000000000000000000000000000000,0000000000000000",
    "00000000000000000000000000000000,00f067aa0ba902b7",
    "4bf92f3577b34da6a3ce929d0e0e4736,0000000000000000",
    "4BF92F3577B34DA6A3CE929D0E0E4736,00f067aa0ba902b7",
    "4bf92f35,00f067aa0ba902b7",
    "not-a-trace-id,not-a-span-id"
  })
  void invalidContext(String traceId, String spanId) throws Exception {
    JsonNode json =
        parse(
            line(
                inSpan(
                    record(org.jboss.logmanager.Level.INFO, "Invalid context"), traceId, spanId)));

    assertEnvelope(json, "INFO", "Invalid context");
    assertThat(json.has("trace_id")).isFalse();
    assertThat(json.has("span_id")).isFalse();
  }

  @Test
  @DisplayName("5 exception: exception.* keys carry type, message and stack trace in one line")
  void exception() throws Exception {
    ExtLogRecord record = record(org.jboss.logmanager.Level.ERROR, "Activity failed");
    record.setThrown(new IllegalStateException("boom"));

    JsonNode json = parse(line(record));

    assertEnvelope(json, "ERROR", "Activity failed");
    assertThat(json.path("exception.type").asText()).isEqualTo("java.lang.IllegalStateException");
    assertThat(json.path("exception.message").asText()).isEqualTo("boom");
    assertThat(json.path("exception.stacktrace").asText())
        .startsWith("java.lang.IllegalStateException: boom\n\tat ")
        .contains("LogFormatContractTest");
  }

  @Test
  @DisplayName("5b exception without a message still has all three exception keys")
  void exceptionWithoutMessage() throws Exception {
    ExtLogRecord record = record(org.jboss.logmanager.Level.ERROR, "Activity failed");
    record.setThrown(new IllegalStateException());

    JsonNode json = parse(line(record));

    assertThat(json.path("exception.type").asText()).isEqualTo("java.lang.IllegalStateException");
    assertThat(json.path("exception.message").asText()).isEmpty();
    assertThat(json.path("exception.stacktrace").asText()).isNotEmpty();
  }

  @Test
  @DisplayName("6 multi-line: the newline is escaped inside message")
  void multiLine() throws Exception {
    JsonNode json = parse(line(record(org.jboss.logmanager.Level.INFO, "line one\nline two")));

    assertEnvelope(json, "INFO", "line one\nline two");
  }

  @Test
  @DisplayName("7 bearer: Authorization header value is redacted")
  void bearer() throws Exception {
    String line =
        line(
            record(
                org.jboss.logmanager.Level.INFO,
                "calling admin with Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkd3MifQ.c2lnbmF0dXJl"));

    assertThat(line).doesNotContain("eyJhbGciOi");
    assertEnvelope(parse(line), "INFO", "calling admin with Authorization: [REDACTED]");
  }

  @Test
  @DisplayName("8 bare-bearer: a Bearer credential with no key is redacted")
  void bareBearer() throws Exception {
    String line =
        line(
            record(
                org.jboss.logmanager.Level.WARN,
                "upstream rejected Bearer eyJhbGciOiJIUzI1NiJ9.e30.sig12345"));

    assertThat(line).doesNotContain("eyJhbGciOi");
    assertEnvelope(parse(line), "WARN", "upstream rejected Bearer [REDACTED]");
  }

  @Test
  @DisplayName("9 password: a credential-named key's value is redacted")
  void password() throws Exception {
    String line =
        line(record(org.jboss.logmanager.Level.INFO, "connecting with user=dws password=hunter2"));

    assertThat(line).doesNotContain("hunter2");
    assertEnvelope(parse(line), "INFO", "connecting with user=dws password=[REDACTED]");
  }

  @Test
  @DisplayName("10 json-secret: the value of a JSON credential key is redacted")
  void jsonSecret() throws Exception {
    String line =
        line(
            record(
                org.jboss.logmanager.Level.INFO,
                "payload {\"client_secret\":\"s3cr3t-value\",\"scope\":\"read\"}"));

    assertThat(line).doesNotContain("s3cr3t-value");
    assertEnvelope(
        parse(line), "INFO", "payload {\"client_secret\":\"[REDACTED]\",\"scope\":\"read\"}");
  }

  @Test
  @DisplayName("11 exception-secret: exception message and stack trace are redacted")
  void exceptionSecret() throws Exception {
    ExtLogRecord record = record(org.jboss.logmanager.Level.ERROR, "Call failed");
    record.setThrown(new IllegalStateException("401 for token=abc12345"));

    String line = line(record);
    JsonNode json = parse(line);

    assertThat(line).doesNotContain("abc12345");
    assertEnvelope(json, "ERROR", "Call failed");
    assertThat(json.path("exception.message").asText()).isEqualTo("401 for token=[REDACTED]");
    assertThat(json.path("exception.stacktrace").asText())
        .startsWith("java.lang.IllegalStateException: 401 for token=[REDACTED]\n\tat ");
  }

  @Test
  @DisplayName("12 fatal: JBoss FATAL maps to FATAL")
  void fatal() throws Exception {
    JsonNode json = parse(line(record(org.jboss.logmanager.Level.FATAL, "Runtime terminated")));

    assertEnvelope(json, "FATAL", "Runtime terminated");
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "FATAL,FATAL",
    "ERROR,ERROR",
    "SEVERE,ERROR",
    "WARN,WARN",
    "WARNING,WARN",
    "INFO,INFO",
    "CONFIG,DEBUG",
    "DEBUG,DEBUG",
    "FINE,DEBUG",
    "TRACE,TRACE",
    "FINER,TRACE",
    "FINEST,TRACE"
  })
  @DisplayName("severity mapping covers every JBoss and java.util.logging level")
  void severityMapping(String level, String expected) {
    Level resolved =
        switch (level) {
          case "FATAL" -> org.jboss.logmanager.Level.FATAL;
          case "ERROR" -> org.jboss.logmanager.Level.ERROR;
          case "WARN" -> org.jboss.logmanager.Level.WARN;
          case "DEBUG" -> org.jboss.logmanager.Level.DEBUG;
          case "TRACE" -> org.jboss.logmanager.Level.TRACE;
          default -> Level.parse(level);
        };

    assertThat(JsonLogFormatter.severity(resolved)).isEqualTo(expected);
  }

  @Test
  @DisplayName("a plain java.util.logging record formats too, with parameters applied")
  void plainJulRecord() throws Exception {
    LogRecord record = new LogRecord(Level.WARNING, "applied {0}");
    record.setParameters(new Object[] {"x"});
    record.setLoggerName(LOGGER);

    assertEnvelope(parse(line(record)), "WARN", "applied x");
  }
}
