package io.dws.step.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes one JSON object per log record, following the shared DWS log format (see {@code
 * docs/log-format.md}). Trace correlation comes from the {@code trace_id}/{@code span_id} MDC
 * entries the OTel Java agent's Logback instrumentation sets; this class has no OTel dependency.
 */
public class DwsJsonLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

  static final String REDACTED = "[REDACTED]";

  private static final Pattern CREDENTIAL_KEY_VALUE =
      Pattern.compile(
          "([\\w.-]*(?:authorization|password|passwd|secret|token|api[-_]?key)[\\w.-]*)"
              + "(\"?\\s*[:=]\\s*\"?)(?:(?:bearer|basic)\\s+)?[^\\s\"',;&}]+",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern BARE_CREDENTIAL =
      Pattern.compile("\\b(bearer|basic)\\s+[A-Za-z0-9._~+/-]{8,}=*", Pattern.CASE_INSENSITIVE);
  private static final Pattern TRACE_ID = Pattern.compile("(?!0{32})[0-9a-f]{32}");
  private static final Pattern SPAN_ID = Pattern.compile("(?!0{16})[0-9a-f]{16}");

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @Override
  public String format(ILoggingEvent event) {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("timestamp", event.getInstant().toString());
    record.put("severity", event.getLevel().toString());
    record.put("logger", event.getLoggerName());
    record.put("message", scrub(event.getFormattedMessage()));
    addTraceContext(record, event.getMDCPropertyMap());
    Optional.ofNullable(event.getThrowableProxy()).ifPresent(t -> addException(record, t));
    return MAPPER.writeValueAsString(record) + "\n";
  }

  private static void addTraceContext(Map<String, Object> record, Map<String, String> mdc) {
    String traceId = mdc.get("trace_id");
    String spanId = mdc.get("span_id");
    if (traceId != null
        && spanId != null
        && TRACE_ID.matcher(traceId).matches()
        && SPAN_ID.matcher(spanId).matches()) {
      record.put("trace_id", traceId);
      record.put("span_id", spanId);
    }
  }

  private static void addException(Map<String, Object> record, IThrowableProxy throwable) {
    record.put("exception.type", throwable.getClassName());
    record.put("exception.message", scrub(throwable.getMessage()));
    record.put("exception.stacktrace", scrub(ThrowableProxyUtil.asString(throwable)));
  }

  static String scrub(String text) {
    if (text == null) {
      return "";
    }
    String keyed = CREDENTIAL_KEY_VALUE.matcher(text).replaceAll("$1$2" + REDACTED);
    return BARE_CREDENTIAL.matcher(keyed).replaceAll("$1 " + REDACTED);
  }
}
