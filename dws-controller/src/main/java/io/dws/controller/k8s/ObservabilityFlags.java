package io.dws.controller.k8s;

import io.dapr.client.DaprClient;
import io.dapr.client.domain.ConfigurationItem;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.jboss.logging.Logger;

/**
 * Resolves {@link ObservabilitySettings} once per workflow deploy from the {@code
 * dws-controller-config} Dapr Configuration store, then confirms the chart-rendered {@code
 * dws-tracing} Configuration exists (a pod referencing a missing Configuration crash-loops daprd).
 *
 * <p>{@link #resolve} never throws: a missing sidecar, store error, empty response, timeout, absent
 * or non-{@code true} flag, absent Configuration, or a failed lookup all resolve to {@link
 * ObservabilitySettings#OFF}, so observability can never fail a deploy. Logging follows what could
 * matter: a store call that throws or times out, a missing {@code dws-tracing} or a failed
 * Configuration lookup each log one single-line WARN (the operator may have set the flag to {@code
 * true}); a simply absent or non-{@code true} flag or a missing sidecar logs nothing at WARN.
 */
@ApplicationScoped
public class ObservabilityFlags {

  private static final Logger LOG = Logger.getLogger(ObservabilityFlags.class);

  /** Upper bound on the single store round trip. */
  static final Duration STORE_TIMEOUT = Duration.ofSeconds(1);

  private final DaprClient daprClient;
  private final KubernetesClient client;

  public ObservabilityFlags(DaprClient daprClient, KubernetesClient client) {
    this.daprClient = daprClient;
    this.client = client;
  }

  /**
   * Settings for a deploy into {@code namespace}; {@link ObservabilitySettings#OFF} on any doubt.
   */
  public ObservabilitySettings resolve(String namespace) {
    try {
      return readStore()
          .filter(ignored -> tracingConfigurationPresent(namespace))
          .orElse(ObservabilitySettings.OFF);
    } catch (RuntimeException e) {
      LOG.warnf(e, "Observability settings could not be resolved; deploying without tracing");
      return ObservabilitySettings.OFF;
    }
  }

  /** Enabled settings read from the store, or empty when the flag is not on or unreadable. */
  private Optional<ObservabilitySettings> readStore() {
    if (daprClient == null) {
      return Optional.empty();
    }
    try {
      Map<String, ConfigurationItem> items =
          daprClient
              .getConfiguration(
                  ObservabilitySettings.STORE,
                  ObservabilitySettings.ENABLED_KEY,
                  ObservabilitySettings.INSTRUMENTATION_KEY)
              .block(STORE_TIMEOUT);
      return Optional.ofNullable(items)
          .filter(present -> isOn(valueOf(present, ObservabilitySettings.ENABLED_KEY).orElse(null)))
          .map(
              present ->
                  new ObservabilitySettings(
                      true,
                      valueOf(present, ObservabilitySettings.INSTRUMENTATION_KEY).orElse(null)));
    } catch (Exception e) {
      // The store call itself failed or timed out, so the operator may have set the flag to true:
      // say so, on one line (no stack trace), then deploy without tracing as always.
      LOG.warnf(
          "Observability flags unavailable (%s); observability.enabled could not be read, so"
              + " deploying the orchestrator without tracing",
          oneLine(e));
      return Optional.empty();
    }
  }

  /** {@code e.toString()} collapsed onto a single line. */
  private static String oneLine(Exception e) {
    return e.toString().replaceAll("\\s*\\R\\s*", " ");
  }

  private static Optional<String> valueOf(Map<String, ConfigurationItem> items, String key) {
    return Optional.ofNullable(items.get(key)).map(ConfigurationItem::getValue);
  }

  private static boolean isOn(String value) {
    return value != null && "true".equalsIgnoreCase(value.trim());
  }

  /** True when {@code dws-tracing} exists; otherwise logs one warning naming the cause. */
  private boolean tracingConfigurationPresent(String namespace) {
    try {
      boolean present =
          client
                  .genericKubernetesResources(ResourceContexts.DAPR_CONFIGURATION)
                  .inNamespace(namespace)
                  .withName(ObservabilitySettings.TRACING_CONFIGURATION)
                  .get()
              != null;
      if (!present) {
        LOG.warnf(
            "observability.enabled is true but Dapr Configuration %s was not found in namespace %s;"
                + " deploying the orchestrator without tracing",
            ObservabilitySettings.TRACING_CONFIGURATION, namespace);
      }
      return present;
    } catch (RuntimeException e) {
      LOG.warnf(
          "observability.enabled is true but Dapr Configuration %s could not be read in namespace"
              + " %s (%s); deploying the orchestrator without tracing",
          ObservabilitySettings.TRACING_CONFIGURATION, namespace, e);
      return false;
    }
  }
}
