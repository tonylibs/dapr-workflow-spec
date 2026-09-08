package io.dws.controller.compile;

import io.dapr.client.DaprClient;
import io.dapr.client.domain.ConfigurationItem;
import io.dws.controller.compile.v1.OpenApiDocumentFetcher;
import io.dws.controller.config.DwsConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.jboss.logging.Logger;

/**
 * Selects and wires the active {@link WorkflowCompiler} strategy (ADR 0002).
 *
 * <p>The v1/v2 flag lives on a Dapr Configuration store (the {@code dws-controller-config} {@code
 * configuration.redis} Component, scoped to {@code dws-controller} only), read via the
 * Configuration API — not a Quarkus property or env var — mirroring how {@code dws-orchestrator}
 * reads its definition from the scoped {@code dws-definitions} store. The default is v1 until the
 * Phase 5 cutover; any missing store, missing key, absent sidecar, or fetch error resolves to v1 so
 * strategy resolution can never fail a deploy.
 *
 * <p>The strategy is resolved once at startup (an {@code @ApplicationScoped} singleton). Live flag
 * flips via {@code subscribeConfiguration} are a deliberately deferred Phase 1 choice (they would
 * need re-checking per {@code compile()} call).
 */
@ApplicationScoped
public class CompilerProducer {

  private static final Logger LOG = Logger.getLogger(CompilerProducer.class);

  /** Dapr Configuration store (Component) holding the compiler-version flag. */
  static final String CONFIG_STORE = "dws-controller-config";

  /** Configuration key selecting the compiler strategy. A single global key for now (ADR 0002). */
  static final String VERSION_KEY = "compiler.version";

  /** The only value that opts in to v2; everything else (including absence/errors) means v1. */
  static final String V2_VALUE = "v2";

  @Produces
  @ApplicationScoped
  public WorkflowCompiler workflowCompiler(
      DwsConfig config, OpenApiDocumentFetcher documentFetcher, DaprClient daprClient) {
    V1OrchestratorCompiler v1 = new V1OrchestratorCompiler(config.catalog(), documentFetcher);
    if (selectsV2(daprClient)) {
      LOG.info(
          "Compiler strategy: v2 (structural) selected via " + CONFIG_STORE + '/' + VERSION_KEY);
      return new V2StructuralCompiler();
    }
    return v1;
  }

  /**
   * Reads the compiler-version flag and reports whether it selects v2. Defaults to {@code false}
   * (v1) on any missing store/key/sidecar or fetch error.
   */
  private static boolean selectsV2(DaprClient daprClient) {
    if (daprClient == null) {
      return false;
    }
    try {
      ConfigurationItem item = daprClient.getConfiguration(CONFIG_STORE, VERSION_KEY).block();
      String value = item == null ? null : item.getValue();
      return value != null && V2_VALUE.equalsIgnoreCase(value.trim());
    } catch (Exception e) {
      LOG.debugf(e, "Compiler-version flag unavailable; defaulting to v1");
      return false;
    }
  }
}
