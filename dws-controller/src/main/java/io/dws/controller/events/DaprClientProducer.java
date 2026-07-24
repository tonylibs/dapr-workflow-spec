package io.dws.controller.events;

import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import org.jboss.logging.Logger;

/**
 * CDI producer for the Dapr client used to publish lifecycle events. Mirrors the orchestrator's
 * {@code daprClient()} bean: a single application-scoped client, closed on shutdown via the
 * disposer. The client reads {@code DAPR_GRPC_PORT}/{@code DAPR_HTTP_PORT} from the
 * sidecar-injected environment; when no sidecar is present, publishing simply fails and is
 * swallowed by {@link EventPublisher} (fire-and-forget).
 */
@ApplicationScoped
public class DaprClientProducer {

  private static final Logger LOG = Logger.getLogger(DaprClientProducer.class);

  @Produces
  @ApplicationScoped
  public DaprClient daprClient() {
    return new DaprClientBuilder().build();
  }

  void closeDaprClient(@Disposes DaprClient client) {
    try {
      client.close();
    } catch (Exception e) {
      LOG.warnf(e, "Error closing Dapr client");
    }
  }
}
