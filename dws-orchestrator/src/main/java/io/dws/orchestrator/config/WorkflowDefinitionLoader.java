package io.dws.orchestrator.config;

import io.dapr.client.DaprClient;
import io.dapr.client.domain.ConfigurationItem;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Loads the single workflow definition for this pod, exactly once, from a Dapr Configuration store.
 *
 * <p>The definition is fetched by its immutable versioned key with retry/backoff (the sidecar may
 * still be starting), then parsed and structurally validated with the Serverless Workflow SDK.
 * Any missing key or invalid document raises {@link DefinitionLoadException}; the bootstrap turns
 * that into a clear log line and a non-zero exit. Configuration changes are deliberately not
 * subscribed to — new versions are rolled out by the controller as new deployments.
 */
public class WorkflowDefinitionLoader {

  private static final Logger LOG = LoggerFactory.getLogger(WorkflowDefinitionLoader.class);

  private final DaprClient client;
  private final String store;
  private final String key;
  private final int maxAttempts;
  private final Duration retryInterval;

  public WorkflowDefinitionLoader(DaprClient client, OrchestratorProperties props) {
    this(client,
        props.getConfigStore(),
        props.getDefinitionKey(),
        props.getConfigFetch().getMaxAttempts(),
        props.getConfigFetch().getRetryInterval());
  }

  public WorkflowDefinitionLoader(DaprClient client, String store, String key,
                                  int maxAttempts, Duration retryInterval) {
    this.client = client;
    this.store = store;
    this.key = key;
    this.maxAttempts = Math.max(1, maxAttempts);
    this.retryInterval = retryInterval;
  }

  /** Fetch, parse and validate the definition. Throws {@link DefinitionLoadException} on failure. */
  public Workflow load() {
    if (key == null || key.isBlank()) {
      throw new DefinitionLoadException("DEFINITION_KEY is required but was not set");
    }
    LOG.info("Loading workflow definition '{}' from configuration store '{}'", key, store);
    String text = fetchWithRetry();
    Workflow workflow = parse(text);
    validateStructure(workflow);
    LOG.info("Loaded workflow '{}' version '{}' with {} top-level task(s)",
        workflow.getDocument().getName(), workflow.getDocument().getVersion(), workflow.getDo().size());
    return workflow;
  }

  private String fetchWithRetry() {
    Exception lastError = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        ConfigurationItem item = client.getConfiguration(store, key).block();
        if (item != null && item.getValue() != null && !item.getValue().isBlank()) {
          return item.getValue();
        }
        LOG.warn("Definition key '{}' not present yet (attempt {}/{})", key, attempt, maxAttempts);
      } catch (Exception e) {
        lastError = e;
        LOG.warn("Configuration fetch failed (attempt {}/{}): {}", attempt, maxAttempts, e.getMessage());
      }
      sleepBeforeRetry(attempt);
    }
    throw new DefinitionLoadException(
        "definition key '" + key + "' not found in store '" + store + "' after " + maxAttempts + " attempt(s)",
        lastError);
  }

  private void sleepBeforeRetry(int attempt) {
    if (attempt >= maxAttempts || retryInterval == null || retryInterval.isZero() || retryInterval.isNegative()) {
      return;
    }
    try {
      Thread.sleep(retryInterval.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new DefinitionLoadException("interrupted while waiting to retry configuration fetch", e);
    }
  }

  private Workflow parse(String text) {
    WorkflowFormat format = text.stripLeading().startsWith("{") ? WorkflowFormat.JSON : WorkflowFormat.YAML;
    try {
      return WorkflowReader.readWorkflowFromString(text, format);
    } catch (Exception e) {
      throw new DefinitionLoadException("failed to parse workflow definition: " + e.getMessage(), e);
    }
  }

  private void validateStructure(Workflow workflow) {
    if (workflow.getDocument() == null
        || isBlank(workflow.getDocument().getName())
        || isBlank(workflow.getDocument().getVersion())) {
      throw new DefinitionLoadException("workflow document must define name and version");
    }
    if (workflow.getDo() == null || workflow.getDo().isEmpty()) {
      throw new DefinitionLoadException("workflow '" + workflow.getDocument().getName() + "' has no 'do' tasks");
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
