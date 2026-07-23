package io.dws.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Externalised orchestrator configuration bound from {@code dws.*} in application.yaml
 * (and overridable via environment variables, e.g. {@code DWS_DEFINITIONS_PATH}).
 */
@ConfigurationProperties(prefix = "dws")
public class OrchestratorProperties {

  /** Directory holding workflow JSON definitions (mounted from a ConfigMap). */
  private String definitionsPath = "/etc/dws/definitions";

  /** Default Dapr pub/sub component used by EMIT tasks that omit an explicit one. */
  private String defaultPubsub = "pubsub";

  private final Retry retry = new Retry();

  public String getDefinitionsPath() {
    return definitionsPath;
  }

  public void setDefinitionsPath(String definitionsPath) {
    this.definitionsPath = definitionsPath;
  }

  public String getDefaultPubsub() {
    return defaultPubsub;
  }

  public void setDefaultPubsub(String defaultPubsub) {
    this.defaultPubsub = defaultPubsub;
  }

  public Retry getRetry() {
    return retry;
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
