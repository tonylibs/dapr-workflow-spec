package io.dws.controller.k8s;

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
 */
public record ObservabilitySettings(boolean enabled, String instrumentation) {

  /** Dapr Configuration store (Component) holding the observability flags. */
  public static final String STORE = "dws-controller-config";

  /** Store key; observability is on only when its trimmed value equals {@code true} (any case). */
  public static final String ENABLED_KEY = "observability.enabled";

  /** Optional store key naming the OpenTelemetry Instrumentation to inject. */
  public static final String INSTRUMENTATION_KEY = "observability.instrumentation";

  /** Chart-rendered, tracing-only Dapr Configuration the orchestrator sidecar is pointed at. */
  public static final String TRACING_CONFIGURATION = "dws-tracing";

  /** Name of the orchestrator container; the only container the agent is injected into. */
  public static final String ORCHESTRATOR_CONTAINER = "orchestrator";

  /** Default {@code inject-java} value: the single Instrumentation in the namespace. */
  static final String DEFAULT_INSTRUMENTATION = "true";

  public static final ObservabilitySettings OFF =
      new ObservabilitySettings(false, DEFAULT_INSTRUMENTATION);

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
  }
}
