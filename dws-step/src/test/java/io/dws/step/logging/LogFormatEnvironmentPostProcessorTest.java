package io.dws.step.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.mock.env.MockEnvironment;

class LogFormatEnvironmentPostProcessorTest {

  private final LogFormatEnvironmentPostProcessor processor =
      new LogFormatEnvironmentPostProcessor();

  private String consoleFormat(MockEnvironment environment) {
    processor.postProcessEnvironment(environment, null);
    return environment.getProperty(LogFormatEnvironmentPostProcessor.CONSOLE_FORMAT_PROPERTY);
  }

  @ParameterizedTest
  @ValueSource(strings = {"json", "JSON", "  Json "})
  void jsonSelectsStructuredFormatter(String value) {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty(LogFormatEnvironmentPostProcessor.FORMAT_PROPERTY, value);

    assertThat(consoleFormat(environment)).isEqualTo(DwsJsonLogFormatter.class.getName());
    assertThat(environment.getProperty("spring.main.banner-mode")).isEqualTo("off");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "text", "yaml"})
  void anythingElseKeepsTextLogging(String value) {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty(LogFormatEnvironmentPostProcessor.FORMAT_PROPERTY, value);

    assertThat(consoleFormat(environment)).isNull();
  }

  @Test
  void unsetKeepsTextLogging() {
    assertThat(consoleFormat(new MockEnvironment())).isNull();
  }

  @Test
  void isRegisteredInSpringFactories() throws IOException {
    try (InputStream factories =
        getClass().getClassLoader().getResourceAsStream("META-INF/spring.factories")) {
      assertThat(new String(factories.readAllBytes(), StandardCharsets.UTF_8))
          .contains(EnvironmentPostProcessor.class.getName())
          .contains(LogFormatEnvironmentPostProcessor.class.getName());
    }
  }
}
