package io.dws.controller.config;

import io.dws.controller.model.ImageCatalog;
import io.dws.controller.model.TaskKind;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Optional;

/** Binds {@code dws.*} from application.yaml. */
@ConfigMapping(prefix = "dws")
public interface DwsConfig {

  /** Namespace the controller deploys managed stacks into. */
  @WithDefault("default")
  String namespace();

  Images images();

  Orchestrator orchestrator();

  Task task();

  Reconcile reconcile();

  Log log();

  /** Garbage-collection cadence for drained previous versions. */
  interface Reconcile {
    @WithDefault("30s")
    String every();
  }

  interface Log {
    /**
     * Log format ({@code DWS_LOG_FORMAT}): {@code json} or, for anything else, {@code text}. The
     * controller's own logging reads the environment variable directly before Quarkus starts (see
     * {@code StructuredLogging}); this binding exists so the value can be stamped on the
     * orchestrator Deployments it compiles.
     */
    @WithDefault("text")
    String format();
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

  interface Task {
    /**
     * ServiceAccount generated Knative step pods run as. The Helm chart and {@code
     * k8s/controller-rbac.yaml} create it without an RBAC grant and set {@code
     * DWS_TASK_SERVICE_ACCOUNT} to match.
     */
    @WithDefault("dws-task")
    String serviceAccount();

    /**
     * ServiceAccount for steps whose {@link TaskKind} has a per-image pull-secret override. It must
     * carry no {@code imagePullSecrets}: Kubernetes and Knative add an account's list to the pod's
     * rather than replacing it. The Helm chart and {@code k8s/controller-rbac.yaml} create it
     * without an RBAC grant and set {@code DWS_TASK_OVERRIDE_SERVICE_ACCOUNT} to match.
     */
    @WithDefault("dws-task-override")
    String overrideServiceAccount();
  }

  /**
   * Optional per-step-image replacement pull-secret lists, each a JSON array of Secret names
   * ({@code DWS_IMAGES_PULL_SECRETS_<KIND>}). Absent inherits the global list through {@link
   * Task#serviceAccount()}; an array, including {@code []}, replaces it. Read through {@link
   * ImagePullSecretPolicy#from(PullSecrets)}, which validates every entry.
   */
  interface PullSecrets {
    Optional<String> callHttp();

    Optional<String> callOpenapi();

    Optional<String> callGrpc();

    Optional<String> callAsyncapi();

    Optional<String> callA2a();

    Optional<String> runShell();

    Optional<String> runScriptJs();

    Optional<String> runScriptPython();
  }

  interface Images {
    PullSecrets pullSecrets();

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
