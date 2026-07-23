package io.dws.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Externalised orchestrator configuration bound from {@code dws.*} in application.yaml
 * (and overridable via environment variables).
 *
 * <p>The definition is loaded exactly once at startup from a Dapr Configuration store; each pod
 * serves the single immutable, versioned {@code definitionKey} for its lifetime.
 */
@ConfigurationProperties(prefix = "dws")
public class OrchestratorProperties {

  /** Dapr Configuration store component name ({@code DAPR_CONFIG_STORE}). */
  private String configStore = "dws-definitions";

  /** Immutable, versioned definition key, e.g. {@code order-workflow@v3} ({@code DEFINITION_KEY}). */
  private String definitionKey;

  /** Default Dapr pub/sub component used by EMIT tasks. */
  private String defaultPubsub = "pubsub";

  private final ConfigFetch configFetch = new ConfigFetch();

  private final Retry retry = new Retry();

  public String getConfigStore() {
    return configStore;
  }

  public void setConfigStore(String configStore) {
    this.configStore = configStore;
  }

  public String getDefinitionKey() {
    return definitionKey;
  }

  public void setDefinitionKey(String definitionKey) {
    this.definitionKey = definitionKey;
  }

  public String getDefaultPubsub() {
    return defaultPubsub;
  }

  public void setDefaultPubsub(String defaultPubsub) {
    this.defaultPubsub = defaultPubsub;
  }

  public ConfigFetch getConfigFetch() {
    return configFetch;
  }

  public Retry getRetry() {
    return retry;
  }

  /** Retry/backoff for fetching the definition while the Dapr sidecar boots. */
  public static class ConfigFetch {
    private int maxAttempts = 30;
    private Duration retryInterval = Duration.ofSeconds(2);

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
    }

    public Duration getRetryInterval() {
      return retryInterval;
    }

    public void setRetryInterval(Duration retryInterval) {
      this.retryInterval = retryInterval;
    }
  }

  /** Default activity retry policy applied to CALL and EMIT tasks. */
  public static class Retry {
    private int maxAttempts = 3;
    private Duration firstRetryInterval = Duration.ofSeconds(1);
    private double backoffCoefficient = 2.0;
    private Duration maxRetryInterval = Duration.ofSeconds(30);

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
    }

    public Duration getFirstRetryInterval() {
      return firstRetryInterval;
    }

    public void setFirstRetryInterval(Duration firstRetryInterval) {
      this.firstRetryInterval = firstRetryInterval;
    }

    public double getBackoffCoefficient() {
      return backoffCoefficient;
    }

    public void setBackoffCoefficient(double backoffCoefficient) {
      this.backoffCoefficient = backoffCoefficient;
    }

    public Duration getMaxRetryInterval() {
      return maxRetryInterval;
    }

    public void setMaxRetryInterval(Duration maxRetryInterval) {
      this.maxRetryInterval = maxRetryInterval;
    }
  }
}
