package io.dws.orchestrator.config;

import io.dapr.client.DaprClient;
import io.dapr.client.domain.ConfigurationItem;
import io.serverlessworkflow.api.types.Workflow;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowDefinitionLoaderTest {

  private static final String STORE = "dws-definitions";
  private static final String KEY = "order-workflow@v1";

  private final DaprClient client = mock(DaprClient.class);

  private WorkflowDefinitionLoader loader(String key) {
    // maxAttempts=1, zero interval -> fail fast without sleeping in tests.
    return new WorkflowDefinitionLoader(client, STORE, key, 1, Duration.ZERO);
  }

  @Test
  void failsWhenDefinitionKeyIsMissing() {
    assertThatThrownBy(() -> loader("").load())
        .isInstanceOf(DefinitionLoadException.class)
        .hasMessageContaining("DEFINITION_KEY");
  }

  @Test
  void failsWhenKeyNotPresentInStore() {
    when(client.getConfiguration(eq(STORE), eq(KEY))).thenReturn(Mono.empty());

    assertThatThrownBy(() -> loader(KEY).load())
        .isInstanceOf(DefinitionLoadException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void failsWhenConfigurationFetchErrors() {
    when(client.getConfiguration(eq(STORE), eq(KEY)))
        .thenReturn(Mono.error(new IllegalStateException("sidecar not ready")));

    assertThatThrownBy(() -> loader(KEY).load())
        .isInstanceOf(DefinitionLoadException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void failsWhenDocumentIsInvalid() {
    when(client.getConfiguration(eq(STORE), eq(KEY)))
        .thenReturn(Mono.just(new ConfigurationItem(KEY, "{}", "1")));

    assertThatThrownBy(() -> loader(KEY).load())
        .isInstanceOf(DefinitionLoadException.class);
  }

  @Test
  void loadsAndValidatesAWellFormedDefinition() throws IOException {
    String yaml = new String(
        getClass().getClassLoader().getResourceAsStream("order.yaml").readAllBytes(), StandardCharsets.UTF_8);
    when(client.getConfiguration(eq(STORE), eq(KEY)))
        .thenReturn(Mono.just(new ConfigurationItem(KEY, yaml, "1")));

    Workflow workflow = loader(KEY).load();

    assertThat(workflow.getDocument().getName()).isEqualTo("order-workflow");
    assertThat(workflow.getDocument().getVersion()).isEqualTo("1.0.0");
    assertThat(workflow.getDo()).hasSize(4);
  }
}
