package io.dws.orchestrator.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * The {@code DWS_LOG_FORMAT} -> {@code logging.structured.format.console} switch: only {@code json}
 * selects the structured formatter, everything else leaves the property unset (plain Boot logging).
 */
class LogFormatEnvironmentPostProcessorTest {

  private static final String BOOT_PROPERTY = "logging.structured.format.console";

  @Configuration
  static class EmptyConfig {}

  private static String mapped(String formatValue) {
    return mappedEnvironment(formatValue).getProperty(BOOT_PROPERTY);
  }

  private static StandardEnvironment mappedEnvironment(String formatValue) {
    StandardEnvironment environment = new StandardEnvironment();
    if (formatValue != null) {
      environment
          .getPropertySources()
          .addFirst(new MapPropertySource("test", Map.of("dws.log.format", formatValue)));
    }
    new LogFormatEnvironmentPostProcessor()
        .postProcessEnvironment(environment, new SpringApplication());
    return environment;
  }

  @ParameterizedTest
  @ValueSource(strings = {"json", "JSON", " Json ", "\tjson\n"})
  void jsonSelectsTheDwsFormatter(String value) {
    assertThat(mapped(value)).isEqualTo(DwsJsonLogFormatter.class.getName());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "text", "yaml", "jsonl", "json5"})
  void anythingElseLeavesPlainLogging(String value) {
    assertThat(mapped(value)).isNull();
  }

  @Test
  void jsonModeTurnsTheBannerOffAndTextModeLeavesItAlone() {
    assertThat(mappedEnvironment("json").getProperty("spring.main.banner-mode")).isEqualTo("off");
    assertThat(mappedEnvironment("text").getProperty("spring.main.banner-mode")).isNull();
    assertThat(mappedEnvironment(null).getProperty("spring.main.banner-mode")).isNull();
  }

  @Test
  void unsetLeavesPlainLogging() {
    assertThat(mapped(null)).isNull();
  }

  /**
   * End to end through the real registration ({@code META-INF/spring.factories}) and the real
   * {@code application.yaml}: the env var enters as {@code DWS_LOG_FORMAT}, travels through {@code
   * dws.log.format: ${DWS_LOG_FORMAT:text}}, and arrives as Boot's property before the context
   * exists. The logging listener is removed so the test JVM's Logback is left alone.
   */
  private static String bootedWith(Map<String, Object> defaults) {
    SpringApplication app = new SpringApplication(EmptyConfig.class);
    app.setWebApplicationType(WebApplicationType.NONE);
    app.setDefaultProperties(defaults);
    app.setListeners(
        app.getListeners().stream()
            .filter(listener -> !(listener instanceof LoggingApplicationListener))
            .toList());
    try (ConfigurableApplicationContext context = app.run()) {
      return context.getEnvironment().getProperty(BOOT_PROPERTY);
    }
  }

  @Test
  void applicationYamlMapsDwsLogFormatEnvVarToTheBootProperty() {
    assertThat(bootedWith(Map.of("DWS_LOG_FORMAT", "json")))
        .isEqualTo(DwsJsonLogFormatter.class.getName());
  }

  @Test
  void applicationYamlWithUnsetEnvVarKeepsPlainLogging() {
    assertThat(bootedWith(Map.of())).isNull();
  }

  @Test
  void applicationYamlWithUnrecognisedEnvVarStartsAndKeepsPlainLogging() {
    assertThat(bootedWith(Map.of("DWS_LOG_FORMAT", "yaml"))).isNull();
  }
}
