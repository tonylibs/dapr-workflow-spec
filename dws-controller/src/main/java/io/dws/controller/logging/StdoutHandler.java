package io.dws.controller.logging;

import io.quarkus.bootstrap.logging.InitialConfigurator;
import java.nio.charset.StandardCharsets;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Writes each formatted record to {@code System.out} as UTF-8, whatever the platform default
 * charset is (a JSON line may carry non-ASCII text, and a container often runs with a POSIX
 * locale). Looks {@code System.out} up per record rather than holding the stream.
 *
 * <p>Until Quarkus has applied the configured log levels it buffers and filters boot records
 * itself; this handler sees them first, so below INFO it stays quiet until then (otherwise every
 * start would print its config-source and bean-lookup DEBUG chatter). After that the logger levels
 * decide, as they do for the text format.
 */
final class StdoutHandler extends Handler {

  StdoutHandler(JsonLogFormatter formatter) {
    setFormatter(formatter);
  }

  @Override
  public synchronized void publish(LogRecord record) {
    if (!isLoggable(record) || bootChatter(record)) {
      return;
    }
    try {
      System.out.writeBytes(getFormatter().format(record).getBytes(StandardCharsets.UTF_8));
      System.out.flush();
    } catch (RuntimeException e) {
      reportError(null, e, ErrorManager.FORMAT_FAILURE);
    }
  }

  private static boolean bootChatter(LogRecord record) {
    return !InitialConfigurator.DELAYED_HANDLER.isActivated()
        && record.getLevel().intValue() < Level.INFO.intValue();
  }

  @Override
  public void flush() {
    System.out.flush();
  }

  @Override
  public void close() {}
}
