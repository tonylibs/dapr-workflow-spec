package io.dws.controller.k8s;

import io.dws.controller.compile.Names;
import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.OAuthEndpoint;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The one Dapr Configuration a workload's version owns. A pod names exactly one Configuration
 * through {@code dapr.io/config}, so everything a workload's sidecar needs from a Configuration
 * lives here and is rendered into a single resource: nothing is dropped because another feature got
 * to the slot first.
 *
 * <p>Today it carries the HTTP pipeline handlers (OAuth2 client-credentials middleware) and the
 * {@code tracing} block. Further sidecar settings, such as Wasm auth handlers or a {@code
 * secrets.scopes} allowlist, are further fields of this record rendered into the same {@code spec};
 * they need no new resource, name or annotation.
 *
 * @param name deterministic resource name, see {@link Names#daprConfiguration}
 * @param httpPipeline handlers in the order Dapr runs them; see {@link #forWorkload}
 * @param tracing the {@code spec.tracing} block, empty when the workload is not traced
 */
record SidecarConfiguration(
    String name, List<HttpPipelineHandler> httpPipeline, Map<String, Object> tracing) {

  /** Dapr component type of the OAuth2 client-credentials middleware. */
  static final String OAUTH_MIDDLEWARE_TYPE = "middleware.http.oauth2clientcredentials";

  /**
   * One {@code spec.httpPipeline.handlers} entry: {@code name} is the middleware Component and
   * {@code type} its Dapr component type.
   */
  record HttpPipelineHandler(String name, String type) {}

  SidecarConfiguration {
    httpPipeline = List.copyOf(httpPipeline);
    tracing = Map.copyOf(tracing);
  }

  /**
   * The Configuration for one workload of {@code plan}, or empty when the workload needs none (and
   * must then carry no {@code dapr.io/config} annotation).
   *
   * <p>Pipeline order is deterministic: OAuth2 handlers, sorted by their (version-scoped) Component
   * name. Handlers of a future kind slot in ahead of or behind that group in this one method.
   *
   * @param workload the workload's Kubernetes name; the Configuration name derives from it
   * @param appId the workload's Dapr app-id, matched against each endpoint's scopes
   * @param tracing the {@code spec.tracing} block to merge, empty for none
   */
  static Optional<SidecarConfiguration> forWorkload(
      DeploymentPlan plan, String workload, String appId, Map<String, Object> tracing) {
    List<HttpPipelineHandler> handlers =
        plan.oauthEndpoints().stream()
            .filter(endpoint -> endpoint.appIds().contains(appId))
            .sorted(Comparator.comparing(OAuthEndpoint::name))
            .map(endpoint -> new HttpPipelineHandler(endpoint.name(), OAUTH_MIDDLEWARE_TYPE))
            .toList();
    if (handlers.isEmpty() && tracing.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new SidecarConfiguration(
            Names.daprConfiguration(workload, plan.versionId()), handlers, tracing));
  }
}
