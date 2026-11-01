package io.dws.orchestrator.logging;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Turns the {@code DWS_LOG_FORMAT} switch into Spring Boot's {@code
 * logging.structured.format.console}.
 *
 * <p>The environment variable carries a mode ({@code json}); Boot's property needs a formatter
 * class name, which a YAML placeholder cannot derive. {@code application.yaml} therefore exposes
 * the variable as {@code dws.log.format: ${DWS_LOG_FORMAT:text}} and this post-processor maps it.
 * Only a trimmed, case-insensitive {@code json} changes anything: unset, empty, {@code text} or an
 * unrecognised value leave {@code logging.structured.format.console} untouched, so output is
 * exactly what it was before this contract existed and startup never fails on a bad value.
 *
 * <p>In JSON mode the startup banner is switched off too ({@code spring.main.banner-mode=off}):
 * Boot prints it straight to stdout, outside the logging system, which would break the
 * one-JSON-object-per-line rule. Text mode keeps the banner as before.
 *
 * <p>Ordering matters twice. It must run after {@code ConfigDataEnvironmentPostProcessor} (which
 * loads {@code application.yaml}, hence {@link Ordered#LOWEST_PRECEDENCE}), and it must run before
 * {@code LoggingApplicationListener} initialises Logback so that the very first boot lines are
 * already JSON. Both hold because {@code EnvironmentPostProcessorApplicationListener} reacts to
 * {@code ApplicationEnvironmentPreparedEvent} at a higher precedence than the logging listener.
 */
public class LogFormatEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  static final String FORMAT_PROPERTY = "dws.log.format";
  static final String BOOT_FORMAT_PROPERTY = "logging.structured.format.console";

  static final String BANNER_PROPERTY = "spring.main.banner-mode";

  private static final String SOURCE_NAME = "dwsLogFormat";

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication app) {
    Optional.ofNullable(environment.getProperty(FORMAT_PROPERTY))
        .map(value -> value.trim().toLowerCase(Locale.ROOT))
        .filter("json"::equals)
        .ifPresent(
            json ->
                environment
                    .getPropertySources()
                    .addFirst(
                        new MapPropertySource(
                            SOURCE_NAME,
                            Map.of(
                                BOOT_FORMAT_PROPERTY,
                                DwsJsonLogFormatter.class.getName(),
                                BANNER_PROPERTY,
                                "off"))));
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
