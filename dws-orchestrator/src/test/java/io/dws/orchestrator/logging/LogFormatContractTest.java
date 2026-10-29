package io.dws.orchestrator.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

/**
 * Conformance of {@link DwsJsonLogFormatter} to {@code docs/log-format.md}: golden cases 1-11 (case
 * 12, FATAL, does not exist in Logback). Parsed objects are compared, not bytes; every case must be
 * exactly one {@code \n}-terminated line.
 */
class LogFormatContractTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String SPAN_ID = "00f067aa0ba902b7";
  private static final String BEARER_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkd3MifQ.c2lnbmF0dXJl";
  private static final String TIMESTAMP_PATTERN =
      "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

  private final ObjectMapper mapper = new ObjectMapper();
  private final LoggerContext context = new LoggerContext();
  private final Logger logger = context.getLogger("dws.golden");
  private final DwsJsonLogFormatter formatter = new DwsJsonLogFormatter();

  private record GoldenCase(
      String name,
      Level level,
      String message,
      Throwable error,
      Map<String, String> mdc,
      Consumer<Parsed> extra) {}

  private record Parsed(String line, JsonNode json) {}

  private static Map<String, String> inSpan() {
    return Map.of("trace_id", TRACE_ID, "span_id", SPAN_ID);
  }

  static List<GoldenCase> goldenCases() {
    return List.of(
        new GoldenCase(
            "1 plain",
            Level.INFO,
            "Starting Dapr workflow runtime",
            null,
            Map.of(),
            parsed -> assertThat(parsed.json().has("trace_id")).isFalse()),
        new GoldenCase(
            "2 in-span",
            Level.INFO,
            "Invoking step",
            null,
            inSpan(),
            parsed -> {
              assertThat(parsed.json().get("trace_id").asText()).isEqualTo(TRACE_ID);
              assertThat(parsed.json().get("span_id").asText()).isEqualTo(SPAN_ID);
            }),
        new GoldenCase(
            "3 out-of-span",
            Level.WARN,
            "Definition store not reachable, retrying",
            null,
            Map.of(),
            parsed -> assertThat(parsed.json().get("severity").asText()).isEqualTo("WARN")),
        new GoldenCase(
            "4 invalid-context",
            Level.INFO,
            "Invalid context",
            null,
            Map.of("trace_id", "0".repeat(32), "span_id", "0".repeat(16)),
            parsed -> assertThat(parsed.json().has("trace_id")).isFalse()),
        new GoldenCase(
            "5 exception",
            Level.ERROR,
            "Activity failed",
            new IllegalStateException("boom"),
            Map.of(),
            parsed -> {
              assertThat(parsed.json().get("exception.type").asText())
                  .isEqualTo("java.lang.IllegalStateException");
              assertThat(parsed.json().get("exception.message").asText()).isEqualTo("boom");
              assertThat(parsed.json().get("exception.stacktrace").asText()).contains("boom");
            }),
        new GoldenCase(
            "6 multi-line",
            Level.INFO,
            "line one\nline two",
            null,
            Map.of(),
            parsed ->
                assertThat(parsed.json().get("message").asText()).isEqualTo("line one\nline two")),
        new GoldenCase(
            "7 bearer",
            Level.INFO,
            "calling admin with Authorization: Bearer " + BEARER_TOKEN,
            null,
            Map.of(),
            parsed -> {
              assertThat(parsed.line()).doesNotContain("eyJhbGciOi");
              assertThat(parsed.json().get("message").asText())
                  .isEqualTo("calling admin with Authorization: [REDACTED]");
            }),
        new GoldenCase(
            "8 bare-bearer",
            Level.WARN,
            "upstream rejected Bearer eyJhbGciOiJIUzI1NiJ9.e30.sig12345",
            null,
            Map.of(),
            parsed -> {
              assertThat(parsed.line()).doesNotContain("eyJhbGciOi");
              assertThat(parsed.json().get("message").asText())
                  .isEqualTo("upstream rejected Bearer [REDACTED]");
            }),
        new GoldenCase(
            "9 password",
            Level.INFO,
            "connecting with user=dws password=hunter2",
            null,
            Map.of(),
            parsed -> {
              assertThat(parsed.line()).doesNotContain("hunter2");
              assertThat(parsed.json().get("message").asText())
                  .isEqualTo("connecting with user=dws password=[REDACTED]");
            }),
        new GoldenCase(
            "10 json-secret",
            Level.INFO,
            "payload {\"client_secret\":\"s3cr3t-value\",\"scope\":\"read\"}",
            null,
            Map.of(),
            parsed -> {
              assertThat(parsed.line()).doesNotContain("s3cr3t-value");
              assertThat(parsed.json().get("message").asText())
                  .isEqualTo("payload {\"client_secret\":\"[REDACTED]\",\"scope\":\"read\"}");
            }),
        new GoldenCase(
            "11 exception-secret",
            Level.ERROR,
            "Call failed",
            new IllegalStateException("401 for token=abc12345"),
            Map.of(),
            parsed -> {
              assertThat(parsed.line()).doesNotContain("abc12345");
              assertThat(parsed.json().get("exception.message").asText())
                  .isEqualTo("401 for token=[REDACTED]");
              assertThat(parsed.json().get("exception.stacktrace").asText())
                  .contains("401 for token=[REDACTED]");
            }));
  }

  static List<Arguments> goldenArguments() {
    return goldenCases().stream().map(golden -> Arguments.of(golden.name(), golden)).toList();
  }

  private Parsed format(GoldenCase golden, Map<String, String> mdc) throws Exception {
    LoggingEvent event =
        new LoggingEvent(
            Logger.class.getName(), logger, golden.level(), golden.message(), golden.error(), null);
    event.setMDCPropertyMap(mdc);
    String line = formatter.format(event);
    assertThat(line).endsWith("}\n");
    assertThat(line.indexOf('\n'))
        .as("exactly one newline, at the end")
        .isEqualTo(line.length() - 1);
    JsonNode json = mapper.readTree(line);
    assertThat(json.isObject()).isTrue();
    assertThat(json.get("timestamp").asText()).matches(TIMESTAMP_PATTERN);
    assertThat(json.get("logger").asText()).isEqualTo("dws.golden");
    assertThat(json.get("severity").asText()).isEqualTo(golden.level().toString());
    assertThat(json.has("exception.type")).isEqualTo(golden.error() != null);
    return new Parsed(line, json);
  }

  @ParameterizedTest(name = "case {0}")
  @MethodSource("goldenArguments")
  void goldenCaseWithItsOwnContext(String name, GoldenCase golden) throws Exception {
    golden.extra().accept(format(golden, golden.mdc()));
  }

  @ParameterizedTest(name = "case {0}: valid MDC trace ids are carried")
  @MethodSource("goldenArguments")
  void everyCaseCarriesValidMdcTraceIds(String name, GoldenCase golden) throws Exception {
    JsonNode json = format(golden, inSpan()).json();

    assertThat(json.get("trace_id").asText()).isEqualTo(TRACE_ID);
    assertThat(json.get("span_id").asText()).isEqualTo(SPAN_ID);
  }

  @ParameterizedTest(name = "case {0}: no MDC trace ids are omitted")
  @MethodSource("goldenArguments")
  void everyCaseOmitsAbsentMdcTraceIds(String name, GoldenCase golden) throws Exception {
    JsonNode json = format(golden, Map.of()).json();

    assertThat(json.has("trace_id")).isFalse();
    assertThat(json.has("span_id")).isFalse();
  }

  @ParameterizedTest
  @MethodSource("invalidContexts")
  void invalidOrIncompleteContextOmitsBothIds(Map<String, String> mdc) throws Exception {
    JsonNode json = format(goldenCases().getFirst(), mdc).json();

    assertThat(json.has("trace_id")).isFalse();
    assertThat(json.has("span_id")).isFalse();
  }

  static List<Map<String, String>> invalidContexts() {
    return List.of(
        Map.of("trace_id", TRACE_ID),
        Map.of("span_id", SPAN_ID),
        Map.of("trace_id", TRACE_ID, "span_id", "0".repeat(16)),
        Map.of("trace_id", "0".repeat(32), "span_id", SPAN_ID),
        Map.of("trace_id", TRACE_ID.toUpperCase(), "span_id", SPAN_ID),
        Map.of("trace_id", TRACE_ID.substring(1), "span_id", SPAN_ID),
        Map.of("trace_id", "", "span_id", ""));
  }

  @Test
  void severityMapsEveryLogbackLevelOntoOtelText() throws Exception {
    for (Level level : List.of(Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG, Level.TRACE)) {
      GoldenCase golden = new GoldenCase("level", level, "msg", null, Map.of(), parsed -> {});
      assertThat(format(golden, Map.of()).json().get("severity").asText())
          .isEqualTo(level.toString());
    }
  }

  @Test
  void controlCharactersAndQuotesStayEscapedOnOneLine() throws Exception {
    GoldenCase golden =
        new GoldenCase(
            "escapes",
            Level.INFO,
            "tab\there \"quoted\" back\\slash\r\nbell\u0007",
            null,
            Map.of(),
            parsed -> {});

    assertThat(format(golden, Map.of()).json().get("message").asText())
        .isEqualTo("tab\there \"quoted\" back\\slash\r\nbell\u0007");
  }

  @Test
  void bootCanInstantiateTheFormatterByClassNameThroughItsStructuredEncoder() throws Exception {
    StructuredLogEncoder encoder = new StructuredLogEncoder();
    context.putObject(Environment.class.getName(), new StandardEnvironment());
    encoder.setContext(context);
    encoder.setFormat(DwsJsonLogFormatter.class.getName());
    encoder.start();
    LoggingEvent event =
        new LoggingEvent(Logger.class.getName(), logger, Level.INFO, "via boot", null, null);
    event.setMDCPropertyMap(inSpan());

    String line = new String(encoder.encode(event), StandardCharsets.UTF_8);

    JsonNode json = mapper.readTree(line);
    assertThat(json.get("message").asText()).isEqualTo("via boot");
    assertThat(json.get("trace_id").asText()).isEqualTo(TRACE_ID);
    assertThat(line).endsWith("}\n");
  }
}
