package io.dws.controller.logging;

import java.util.logging.Handler;
import java.util.logging.Logger;
import lombok.experimental.UtilityClass;

/**
 * Switches the controller's own logging to the structured JSON format when {@code DWS_LOG_FORMAT}
 * is {@code json}. Quarkus builds its console handler at runtime init, and a formatter cannot be
 * plugged into it from application code, so JSON mode turns that handler off and puts a JSON
 * console handler on the root logger instead. {@link ControllerMain} does this before {@code
 * Quarkus.run}, which is why records logged while Quarkus boots are JSON too. Any other value —
 * unset, empty, {@code text}, a typo — leaves logging exactly as it was.
 */
@UtilityClass
public class StructuredLogging {

  /** Environment variable selecting the format; the chart renders it on the controller. */
  public static final String FORMAT_ENV = "DWS_LOG_FORMAT";

  /** Case-insensitive, trimmed {@code json}; anything else is text mode. */
  public static boolean isJson(String format) {
    return format != null && "json".equalsIgnoreCase(format.trim());
  }

  /** Installs JSON logging on the root logger when {@code format} asks for it. */
  public static boolean install(String format) {
    if (!isJson(format)) {
      return false;
    }
    System.setProperty("quarkus.log.console.enabled", "false");
    System.setProperty("quarkus.banner.enabled", "false");
    Logger.getLogger("").addHandler(jsonConsoleHandler());
    return true;
  }

  static Handler jsonConsoleHandler() {
    return new StdoutHandler(new JsonLogFormatter());
  }
}
