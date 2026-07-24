package io.dws.controller.events;

import io.dapr.client.DaprClient;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * Publishes {@code dws-controller} lifecycle events to the shared {@code dws.events} topic as a
 * fire-and-forget side effect of the apply pass. A publish failure is logged and swallowed — it
 * never propagates out of this class, so it can never fail an apply pass (see
 * {@code controller-event-publishing} spec and {@code docs/events.md}).
 */
@ApplicationScoped
public class EventPublisher {

    static final String COMPONENT = "pubsub";
    static final String TOPIC = "dws.events";
    static final String SOURCE = "dws-controller";

    static final String DEFINITION_CREATED = "io.dws.definition.created";
    static final String DEFINITION_UPDATED = "io.dws.definition.updated";
    static final String DEPLOYMENT_APPLIED = "io.dws.deployment.applied";
    static final String DEPLOYMENT_FAILED = "io.dws.deployment.failed";
    static final String DEPLOYMENT_DRAINED = "io.dws.deployment.drained";
    static final String DEPLOYMENT_COLLECTED = "io.dws.deployment.collected";

    private static final Logger LOG = Logger.getLogger(EventPublisher.class);

    private final DaprClient client;

    public EventPublisher(DaprClient client) {
        this.client = client;
    }

    public void definitionCreated(String workflow, String version, String createdAt) {
        publish(DEFINITION_CREATED, definitionData(workflow, version, createdAt));
    }

    public void definitionUpdated(String workflow, String version, String createdAt) {
        publish(DEFINITION_UPDATED, definitionData(workflow, version, createdAt));
    }

    public void deploymentApplied(String workflow, String version, List<String> stepServices,
                                  String orchestratorAppId) {
        publish(DEPLOYMENT_APPLIED, deploymentData(workflow, version, stepServices, orchestratorAppId, null));
    }

    public void deploymentFailed(String workflow, String version, List<String> stepServices,
                                 String orchestratorAppId, String error) {
        publish(DEPLOYMENT_FAILED, deploymentData(workflow, version, stepServices, orchestratorAppId, error));
    }

    public void deploymentDrained(String workflow, String version, String orchestratorAppId) {
        publish(DEPLOYMENT_DRAINED, gcData(workflow, version, orchestratorAppId));
    }

    public void deploymentCollected(String workflow, String version, String orchestratorAppId) {
        publish(DEPLOYMENT_COLLECTED, gcData(workflow, version, orchestratorAppId));
    }

    private static Map<String, Object> definitionData(String workflow, String version, String createdAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workflow", workflow);
        data.put("version", version);
        data.put("createdAt", createdAt);
        return data;
    }

    private static Map<String, Object> deploymentData(String workflow, String version,
                                                      List<String> stepServices, String orchestratorAppId,
                                                      String error) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workflow", workflow);
        data.put("version", version);
        data.put("stepServices", stepServices == null ? List.of() : List.copyOf(stepServices));
        data.put("orchestratorAppId", orchestratorAppId);
        if (error != null) {
            data.put("error", error);
        }
        return data;
    }

    private static Map<String, Object> gcData(String workflow, String version, String orchestratorAppId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workflow", workflow);
        data.put("version", version);
        data.put("orchestratorAppId", orchestratorAppId);
        return data;
    }

    /** Publishes one envelope, swallowing any failure so publishing is never observable to callers. */
    private void publish(String type, Map<String, Object> data) {
        try {
            EventEnvelope envelope = EventEnvelope.create(type, SOURCE, data);
            client.publishEvent(COMPONENT, TOPIC, envelope.asMap()).block();
        } catch (Exception e) {
            LOG.warnf(e, "Failed to publish lifecycle event %s (swallowed)", type);
        }
    }
}
