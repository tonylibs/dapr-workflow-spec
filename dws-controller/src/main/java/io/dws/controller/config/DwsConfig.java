package io.dws.controller.config;

import io.dws.controller.model.ImageCatalog;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Binds {@code dws.*} from application.yaml. */
@ConfigMapping(prefix = "dws")
public interface DwsConfig {

  /** Namespace the controller deploys managed stacks into. */
  @WithDefault("default")
  String namespace();

  Images images();

  Orchestrator orchestrator();

  Reconcile reconcile();

  /** Garbage-collection cadence for drained previous versions. */
  interface Reconcile {
    @WithDefault("30s")
    String every();
  }

  interface Orchestrator {
    /**
     * ServiceAccount orchestrator pods run as. It must be able to read ConfigMaps in {@link
     * #namespace()}: the definition Component reads its ConfigMap as the pod's service account. The
     * Helm chart and {@code k8s/controller-rbac.yaml} create it and set {@code
     * DWS_ORCHESTRATOR_SERVICE_ACCOUNT} to match.
     */
    @WithDefault("dws-orchestrator")
    String serviceAccount();
  }

  interface Images {
    String callHttp();

    String callOpenapi();

    String callGrpc();

    String callAsyncapi();

    String callA2a();

    String runShell();

    String runScriptJs();

    String runScriptPython();

    String orchestrator();
  }

  default ImageCatalog catalog() {
    return new ImageCatalog(
        images().callHttp(),
        images().callOpenapi(),
        images().callGrpc(),
        images().callAsyncapi(),
        images().callA2a(),
        images().runShell(),
        images().runScriptJs(),
        images().runScriptPython(),
        images().orchestrator());
  }
}
