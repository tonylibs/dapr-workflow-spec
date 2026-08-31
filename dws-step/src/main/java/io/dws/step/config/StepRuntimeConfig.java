package io.dws.step.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Loads the immutable definition eagerly so any invalid file prevents application startup. */
@Configuration
public class StepRuntimeConfig {

  @Bean
  public SingleNodeDefinitionLoader singleNodeDefinitionLoader(ObjectMapper objectMapper) {
    return new SingleNodeDefinitionLoader(
        objectMapper, System.getenv(SingleNodeDefinitionLoader.DEFINITION_PATH_ENV));
  }

  @Bean
  public SingleNodeDefinition singleNodeDefinition(SingleNodeDefinitionLoader loader) {
    return loader.load();
  }
}
