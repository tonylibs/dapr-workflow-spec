package io.dws.controller.k8s;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures the records a class logs through JBoss Logging (which sits on {@code java.util.logging}
 * under Quarkus) for the lifetime of a try-with-resources block, so tests can assert on log levels
 * and counts. The logger is forced to {@link Level#ALL} while capturing and restored on close.
 */
final class LogCapture implements AutoCloseable {

  private final Logger logger;
  private final Level previousLevel;
  private final boolean previousUseParentHandlers;
  private final List<LogRecord> records = new CopyOnWriteArrayList<>();
  private final Handler handler =
      new Handler() {
        @Override
        public void publish(LogRecord record) {
          records.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
      };

  LogCapture(Class<?> source) {
    this.logger = Logger.getLogger(source.getName());
    this.previousLevel = logger.getLevel();
    this.previousUseParentHandlers = logger.getUseParentHandlers();
    logger.setLevel(Level.ALL);
    // Keep test output quiet; the records are asserted on instead.
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);
  }

  /** Every captured record at exactly {@code level}. */
  List<LogRecord> at(Level level) {
    return records.stream().filter(record -> record.getLevel().equals(level)).toList();
  }

  /** The formatted message of {@code record} (parameters applied). */
  static String message(LogRecord record) {
    Object[] parameters = record.getParameters();
    String message = record.getMessage();
    if (parameters == null || parameters.length == 0) {
      return message;
    }
    try {
      return String.format(message, parameters);
    } catch (RuntimeException e) {
      return java.text.MessageFormat.format(message, parameters);
    }
  }

  @Override
  public void close() {
    logger.removeHandler(handler);
    logger.setLevel(previousLevel);
    logger.setUseParentHandlers(previousUseParentHandlers);
  }
}
