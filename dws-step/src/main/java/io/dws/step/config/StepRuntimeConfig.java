package io.dws.step.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the immutable definition eagerly so any invalid file prevents application startup.
 *
 * <p>Spring Boot 4 auto-configures Jackson 3 ({@code tools.jackson}) only, so the Jackson 2 {@link
 * ObjectMapper} that the loader and jq evaluator use is declared here, as in {@code
 * dws-orchestrator}.
 */
@Configuration
public class StepRuntimeConfig {

  @Bean("stepObjectMapper")
  public ObjectMapper stepObjectMapper() {
    return new ObjectMapper();
  }

  /** The path comes from the {@code DWS_STEP_DEFINITION_PATH} environment variable. */
  @Bean
  public SingleNodeDefinitionLoader singleNodeDefinitionLoader(
      ObjectMapper stepObjectMapper,
      @Value("${" + SingleNodeDefinitionLoader.DEFINITION_PATH_ENV + ":#{null}}")
          String definitionPath) {
    return new SingleNodeDefinitionLoader(stepObjectMapper, definitionPath);
  }

  @Bean
  public SingleNodeDefinition singleNodeDefinition(SingleNodeDefinitionLoader loader) {
    return loader.load();
  }
}
