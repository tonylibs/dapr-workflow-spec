package io.dws.controller.logging;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.jboss.logmanager.ExtLogRecord;

/**
 * Formats a record as the one-line JSON object specified in {@code docs/log-format.md}. Trace
 * context comes from the MDC keys {@code trace_id} / {@code span_id}, which the OpenTelemetry Java
 * agent's JBoss logmanager instrumentation populates; with no agent, or no valid span, both keys
 * are omitted. Message and exception text are scrubbed ({@link LogScrubber}) before escaping.
 */
public final class JsonLogFormatter extends Formatter {

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
  private static final JsonStringEncoder ENCODER = JsonStringEncoder.getInstance();
  private static final int TRACE_ID_LENGTH = 32;
  private static final int SPAN_ID_LENGTH = 16;

  @Override
  public String format(LogRecord record) {
    StringBuilder line = new StringBuilder(256);
    line.append('{');
    field(line, "timestamp", TIMESTAMP.format(record.getInstant()));
    field(line.append(','), "severity", severity(record.getLevel()));
    field(line.append(','), "logger", record.getLoggerName() == null ? "" : record.getLoggerName());
    field(line.append(','), "message", LogScrubber.scrub(message(record)));
    if (record instanceof ExtLogRecord ext) {
      String traceId = ext.getMdc("trace_id");
      String spanId = ext.getMdc("span_id");
      if (isValidId(traceId, TRACE_ID_LENGTH) && isValidId(spanId, SPAN_ID_LENGTH)) {
        field(line.append(','), "trace_id", traceId);
        field(line.append(','), "span_id", spanId);
      }
    }
    Throwable thrown = record.getThrown();
    if (thrown != null) {
      field(line.append(','), "exception.type", thrown.getClass().getName());
      field(
          line.append(','),
          "exception.message",
          LogScrubber.scrub(thrown.getMessage() == null ? "" : thrown.getMessage()));
      field(line.append(','), "exception.stacktrace", LogScrubber.scrub(stackTrace(thrown)));
    }
    return line.append("}\n").toString();
  }

  /** JBoss levels by numeric value, so FATAL/CONFIG/FINE and friends need no per-level table. */
  static String severity(Level level) {
    int value = level.intValue();
    if (value >= 1100) {
      return "FATAL";
    }
    if (value >= 1000) {
      return "ERROR";
    }
    if (value >= 900) {
      return "WARN";
    }
    if (value >= 800) {
      return "INFO";
    }
    return value >= 500 ? "DEBUG" : "TRACE";
  }

  private String message(LogRecord record) {
    return record instanceof ExtLogRecord ext ? ext.getFormattedMessage() : formatMessage(record);
  }

  private static String stackTrace(Throwable thrown) {
    StringWriter out = new StringWriter();
    thrown.printStackTrace(new PrintWriter(out));
    return out.toString();
  }

  /** Lowercase hex of exactly {@code length} characters that is not all zeros. */
  private static boolean isValidId(String id, int length) {
    if (id == null || id.length() != length) {
      return false;
    }
    boolean nonZero = false;
    for (int i = 0; i < length; i++) {
      char c = id.charAt(i);
      if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
        return false;
      }
      nonZero |= c != '0';
    }
    return nonZero;
  }

  private static void field(StringBuilder line, String key, String value) {
    line.append('"').append(key).append("\":\"").append(ENCODER.quoteAsString(value)).append('"');
  }
}
