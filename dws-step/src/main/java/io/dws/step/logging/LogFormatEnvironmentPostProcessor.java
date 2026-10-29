package io.dws.step.logging;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Maps {@code DWS_LOG_FORMAT=json} (read as {@code dws.log.format}) onto Spring Boot's structured
 * console logging. Any other value, including unset, leaves the default text output untouched. Runs
 * before the logging system initialises, so boot-time lines are JSON too.
 */
public class LogFormatEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  static final String FORMAT_PROPERTY = "dws.log.format";
  static final String CONSOLE_FORMAT_PROPERTY = "logging.structured.format.console";

  private static final String PROPERTY_SOURCE_NAME = "dwsLogFormat";

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication app) {
    String format = StringUtils.trimToEmpty(environment.getProperty(FORMAT_PROPERTY));
    if (!"json".equalsIgnoreCase(format)) {
      return;
    }
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                PROPERTY_SOURCE_NAME,
                Map.of(
                    CONSOLE_FORMAT_PROPERTY,
                    DwsJsonLogFormatter.class.getName(),
                    // The banner is written to stdout outside the logging system.
                    "spring.main.banner-mode",
                    "off")));
  }

  /** After {@code ConfigDataEnvironmentPostProcessor}, so {@code application.yaml} is loaded. */
  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
