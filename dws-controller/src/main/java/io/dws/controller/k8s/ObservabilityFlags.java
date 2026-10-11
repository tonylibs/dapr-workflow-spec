package io.dws.controller.k8s;

import io.dapr.client.DaprClient;
import io.dapr.client.domain.ConfigurationItem;
import io.dws.controller.config.DaprConfigurationItem;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves {@link ObservabilitySettings} once per workflow deploy from the {@code
 * dws-controller-config} Dapr Configuration store, then reads {@code spec.tracing} from the
 * chart-rendered {@code dws-tracing} Configuration. That block is the single definition of the
 * tracing settings; the controller merges it into the workload's own Configuration rather than
 * pointing a pod at {@code dws-tracing}, so tracing no longer competes for {@code dapr.io/config}.
 *
 * <p>{@link #resolve} never throws: a missing sidecar, store error, empty response, timeout, absent
 * or non-{@code true} flag, absent Configuration, or a failed lookup all resolve to {@link
 * ObservabilitySettings#OFF}, so observability can never fail a deploy. Logging follows what could
 * matter: a store call that throws or times out, a missing {@code dws-tracing} or a failed
 * Configuration lookup each log one single-line WARN (the operator may have set the flag to {@code
 * true}); a simply absent or non-{@code true} flag or a missing sidecar logs nothing at WARN.
 */
@Slf4j
@ApplicationScoped
public class ObservabilityFlags {

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
          .flatMap(
              settings -> tracingBlock(namespace).map(tracing -> settings.withTracing(tracing)))
          .orElse(ObservabilitySettings.OFF);
    } catch (RuntimeException e) {
      log.warn("Observability settings could not be resolved; deploying without tracing", e);
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
                      true, valueOf(present, ObservabilitySettings.INSTRUMENTATION_KEY)));
    } catch (Exception e) {
      // The store call itself failed or timed out, so the operator may have set the flag to true:
      // say so, on one line (no stack trace), then deploy without tracing as always.
      log.warn(
          "Observability flags unavailable ({}); observability.enabled could not be read, so deploying the orchestrator without tracing",
          oneLine(e));
      return Optional.empty();
    }
  }

  private Optional<ObservabilitySettings> readStoreV2() {
    return Optional.ofNullable(daprClient)
        .map(
            dc ->
                dc.getConfiguration(
                        ObservabilitySettings.STORE,
                        ObservabilitySettings.ENABLED_KEY,
                        ObservabilitySettings.INSTRUMENTATION_KEY)
                    .map(
                        items ->
                            Optional.ofNullable(items)
                                .filter(
                                    present ->
                                        isOn(
                                            valueOf(present, ObservabilitySettings.ENABLED_KEY)
                                                .orElse(null)))
                                .map(
                                    present ->
                                        new ObservabilitySettings(
                                            true,
                                            valueOf(
                                                present,
                                                ObservabilitySettings.INSTRUMENTATION_KEY)))))
        .flatMap(opt -> Optional.ofNullable(opt.block()).flatMap(Function.identity()));
  }

  /** {@code e.toString()} collapsed onto a single line. */
  private static String oneLine(Exception e) {
    return e.toString().replaceAll("\\s*\\R\\s*", " ");
  }

  private static Optional<String> valueOf(Map<String, ConfigurationItem> items, String key) {
    return Optional.ofNullable(items.get(key))
        .map(DaprConfigurationItem::new)
        .flatMap(DaprConfigurationItem::getValue);
  }

  private static boolean isOn(String value) {
    return value != null && "true".equalsIgnoreCase(value.trim());
  }

  /**
   * The {@code spec.tracing} block of {@code dws-tracing}; empty, after one warning naming the
   * cause, when the Configuration is missing, unreadable or carries no tracing block (merging an
   * empty block would instrument the orchestrator without any tracing export).
   */
  private Optional<Map<String, Object>> tracingBlock(String namespace) {
    try {
      GenericKubernetesResource tracing =
          client
              .genericKubernetesResources(ResourceContexts.DAPR_CONFIGURATION)
              .inNamespace(namespace)
              .withName(ObservabilitySettings.TRACING_CONFIGURATION)
              .get();
      if (tracing == null) {
        log.warn(
            "observability.enabled is true but Dapr Configuration {} was not found in namespace {}; deploying the orchestrator without tracing",
            ObservabilitySettings.TRACING_CONFIGURATION,
            namespace);
        return Optional.empty();
      }
      Optional<Map<String, Object>> block = tracingOf(tracing);
      if (block.isEmpty()) {
        log.warn(
            "Dapr Configuration {} in namespace {} has no spec.tracing; deploying the orchestrator without tracing",
            ObservabilitySettings.TRACING_CONFIGURATION,
            namespace);
      }
      return block;
    } catch (RuntimeException e) {
      log.warn(
          "observability.enabled is true but Dapr Configuration {} could not be read in namespace {} ({}); deploying the orchestrator without tracing",
          ObservabilitySettings.TRACING_CONFIGURATION,
          namespace,
          oneLine(e));
      return Optional.empty();
    }
  }

  @SuppressWarnings("unchecked")
  private static Optional<Map<String, Object>> tracingOf(GenericKubernetesResource resource) {
    return Optional.ofNullable(resource.getAdditionalProperties().get("spec"))
        .filter(Map.class::isInstance)
        .map(spec -> ((Map<String, Object>) spec).get("tracing"))
        .filter(Map.class::isInstance)
        .map(tracing -> (Map<String, Object>) tracing)
        .filter(tracing -> !tracing.isEmpty());
  }
}
