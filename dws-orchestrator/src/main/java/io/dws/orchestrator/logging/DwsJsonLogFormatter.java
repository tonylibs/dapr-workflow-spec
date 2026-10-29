package io.dws.orchestrator.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.boot.logging.structured.StructuredLogFormatter;

/**
 * The DWS structured log line for Logback: one JSON object per record, in the shape specified by
 * {@code docs/log-format.md} (keys, severity map, scrubber). Selected by {@link
 * LogFormatEnvironmentPostProcessor} when {@code DWS_LOG_FORMAT=json}; Spring Boot instantiates it
 * from the class name in {@code logging.structured.format.console}, so it needs the public no-arg
 * constructor and nothing else.
 *
 * <p>Trace context is read from the MDC keys {@code trace_id} / {@code span_id} that the
 * OpenTelemetry Java agent's Logback instrumentation populates, rather than through the OTel API.
 * That keeps this package free of an OTel dependency and degrades to "no {@code trace_id}" when no
 * agent is attached. A context that is not 32/16 lowercase hex, or is all zeros, counts as absent
 * and both keys are omitted together.
 *
 * <p>The JSON is written by hand: this runs while the logging system itself is being initialised,
 * so it avoids loading a JSON mapper there. The scrubber runs on the raw text, before escaping, so
 * the line stays valid JSON.
 */
public class DwsJsonLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

  static final String REDACTED = "[REDACTED]";

  static final String MDC_TRACE_ID = "trace_id";
  static final String MDC_SPAN_ID = "span_id";

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private static final Pattern TRACE_ID = Pattern.compile("(?!0{32})[0-9a-f]{32}");
  private static final Pattern SPAN_ID = Pattern.compile("(?!0{16})[0-9a-f]{16}");

  /** Credential-named key/value, scheme and token of an {@code Authorization} value included. */
  private static final Pattern CREDENTIAL_KEY_VALUE =
      Pattern.compile(
          "([\\w.-]*(?:authorization|password|passwd|secret|token|api[-_]?key)[\\w.-]*)"
              + "(\"?\\s*[:=]\\s*\"?)(?:(?:bearer|basic)\\s+)?[^\\s\"',;&}]+",
          Pattern.CASE_INSENSITIVE);

  /** A bare {@code Bearer} / {@code Basic} credential with no key in front of it. */
  private static final Pattern BARE_CREDENTIAL =
      Pattern.compile("\\b(bearer|basic)\\s+[A-Za-z0-9._~+/-]{8,}=*", Pattern.CASE_INSENSITIVE);

  @Override
  public String format(ILoggingEvent event) {
    StringBuilder line = new StringBuilder(256);
    line.append('{');
    field(line, "timestamp", TIMESTAMP.format(event.getInstant()));
    line.append(',');
    field(line, "severity", severity(event.getLevel()));
    line.append(',');
    field(line, "logger", event.getLoggerName());
    line.append(',');
    field(line, "message", scrub(event.getFormattedMessage()));
    traceContext(event.getMDCPropertyMap())
        .ifPresent(
            ids -> {
              line.append(',');
              field(line, "trace_id", ids.traceId());
              line.append(',');
              field(line, "span_id", ids.spanId());
            });
    Optional.ofNullable(event.getThrowableProxy()).ifPresent(error -> exception(line, error));
    return line.append("}\n").toString();
  }

  private static void exception(StringBuilder line, IThrowableProxy error) {
    line.append(',');
    field(line, "exception.type", error.getClassName());
    line.append(',');
    field(line, "exception.message", scrub(error.getMessage()));
    line.append(',');
    field(line, "exception.stacktrace", scrub(ThrowableProxyUtil.asString(error)));
  }

  private record TraceContext(String traceId, String spanId) {
    boolean isValid() {
      return traceId != null
          && spanId != null
          && TRACE_ID.matcher(traceId).matches()
          && SPAN_ID.matcher(spanId).matches();
    }
  }

  private static Optional<TraceContext> traceContext(Map<String, String> mdc) {
    return Optional.ofNullable(mdc)
        .map(context -> new TraceContext(context.get(MDC_TRACE_ID), context.get(MDC_SPAN_ID)))
        .filter(TraceContext::isValid);
  }

  /**
   * Logback has no native FATAL; every other level maps onto the OTel severity text of its name.
   */
  private static String severity(Level level) {
    return switch (level.toInt()) {
      case Level.ERROR_INT -> "ERROR";
      case Level.WARN_INT -> "WARN";
      case Level.INFO_INT -> "INFO";
      case Level.DEBUG_INT -> "DEBUG";
      default -> "TRACE";
    };
  }

  /** Both contract regexes, in order; see {@code docs/log-format.md}, "Scrubber". */
  static String scrub(String text) {
    if (text == null) {
      return "";
    }
    String keyed = CREDENTIAL_KEY_VALUE.matcher(text).replaceAll("$1$2" + REDACTED);
    return BARE_CREDENTIAL.matcher(keyed).replaceAll("$1 " + REDACTED);
  }

  private static void field(StringBuilder line, String key, String value) {
    quote(line, key).append(':');
    quote(line, value);
  }

  private static StringBuilder quote(StringBuilder out, String value) {
    out.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        default -> {
          if (c < 0x20 || c == ' ' || c == ' ') {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"');
  }
}
