package io.dws.controller.k8s;

import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Resolved orchestrator-tracing settings for one workflow deploy. {@link #OFF} is the default and
 * makes {@link StackSynthesizer#orchestratorDeployment} take its unmodified code path.
 *
 * @param enabled whether the orchestrator Deployment is instrumented
 * @param instrumentation value of {@code instrumentation.opentelemetry.io/inject-java}: {@code
 *     "true"}, {@code <name>} or {@code <namespace>/<name>}; a null/blank value means {@code
 *     "true"}
 * @param tracing the {@code spec.tracing} block copied verbatim from the chart-rendered {@link
 *     #TRACING_CONFIGURATION} Configuration, which is the single definition of the endpoint,
 *     protocol and sampling values. It is merged into the orchestrator's own Dapr Configuration
 *     (see {@link SidecarConfiguration}); empty when tracing is off
 */
public record ObservabilitySettings(
    boolean enabled, String instrumentation, Map<String, Object> tracing) {

  /** Dapr Configuration store (Component) holding the observability flags. */
  public static final String STORE = "dws-controller-config";

  /** Store key; observability is on only when its trimmed value equals {@code true} (any case). */
  public static final String ENABLED_KEY = "observability.enabled";

  /** Optional store key naming the OpenTelemetry Instrumentation to inject. */
  public static final String INSTRUMENTATION_KEY = "observability.instrumentation";

  /**
   * Chart-rendered, tracing-only Dapr Configuration. No pod references it any more: it is the
   * source whose {@code spec.tracing} the controller copies into each traced workload's merged
   * Configuration, and its presence is what allows tracing to switch on.
   */
  public static final String TRACING_CONFIGURATION = "dws-tracing";

  /** Name of the orchestrator container; the only container the agent is injected into. */
  public static final String ORCHESTRATOR_CONTAINER = "orchestrator";

  /** Default {@code inject-java} value: the single Instrumentation in the namespace. */
  static final String DEFAULT_INSTRUMENTATION = "true";

  public static final ObservabilitySettings OFF =
      new ObservabilitySettings(false, DEFAULT_INSTRUMENTATION);

  public ObservabilitySettings(boolean enabled, String instrumentation) {
    this(enabled, instrumentation, Map.of());
  }

  public ObservabilitySettings(boolean enabled, Optional<String> instrumentation) {
    this(
        enabled,
        instrumentation
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .orElse(DEFAULT_INSTRUMENTATION));
  }

  public ObservabilitySettings {
    instrumentation =
        instrumentation == null || instrumentation.isBlank()
            ? DEFAULT_INSTRUMENTATION
            : instrumentation.trim();
    tracing = tracing == null ? Map.of() : Map.copyOf(tracing);
  }

  /** The same settings carrying {@code tracing} as the block to merge into the Configuration. */
  public ObservabilitySettings withTracing(Map<String, Object> tracing) {
    return new ObservabilitySettings(enabled, instrumentation, tracing);
  }
}
