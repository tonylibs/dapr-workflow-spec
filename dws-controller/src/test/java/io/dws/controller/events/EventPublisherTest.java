package io.dws.controller.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.dapr.client.DaprClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

/**
 * Verifies {@link EventPublisher} assembles the CloudEvents-style envelope correctly and always
 * publishes to component {@code pubsub}, topic {@code dws.events}, against a mocked Dapr client.
 */
class EventPublisherTest {

    private DaprClient client;
    private EventPublisher publisher;

    @BeforeEach
    void setUp() {
        client = mock(DaprClient.class);
        when(client.publishEvent(anyString(), anyString(), any())).thenReturn(Mono.empty());
        publisher = new EventPublisher(client);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureEnvelope() {
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(client).publishEvent(eq("pubsub"), eq("dws.events"), body.capture());
        return (Map<String, Object>) body.getValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> envelope) {
        return (Map<String, Object>) envelope.get("data");
    }

    @Test
    @DisplayName("definitionCreated publishes a well-formed envelope with the created type")
    void definitionCreated_envelopeShape() {
        publisher.definitionCreated("order", "vabc1234", "2026-07-24T00:00:00Z");

        Map<String, Object> envelope = captureEnvelope();
        assertThat(envelope)
                .containsEntry("type", "io.dws.definition.created")
                .containsEntry("source", "dws-controller")
                .containsEntry("datacontenttype", "application/json")
                .containsKeys("id", "time", "data");
        assertThat(data(envelope))
                .containsEntry("workflow", "order")
                .containsEntry("version", "vabc1234")
                .containsEntry("createdAt", "2026-07-24T00:00:00Z");
    }

    @Test
    @DisplayName("definitionUpdated uses the updated type")
    void definitionUpdated_type() {
        publisher.definitionUpdated("order", "vabc1234", "2026-07-24T00:00:00Z");
        assertThat(captureEnvelope()).containsEntry("type", "io.dws.definition.updated");
    }

    @Test
    @DisplayName("deploymentApplied carries stepServices and orchestratorAppId, no error")
    void deploymentApplied_payload() {
        publisher.deploymentApplied("order", "vabc1234",
                List.of("check-inventory", "charge-payment"), "order");

        Map<String, Object> envelope = captureEnvelope();
        assertThat(envelope).containsEntry("type", "io.dws.deployment.applied");
        assertThat(data(envelope))
                .containsEntry("workflow", "order")
                .containsEntry("version", "vabc1234")
                .containsEntry("stepServices", List.of("check-inventory", "charge-payment"))
                .containsEntry("orchestratorAppId", "order")
                .doesNotContainKey("error");
    }

    @Test
    @DisplayName("deploymentFailed adds the error field")
    void deploymentFailed_payload() {
        publisher.deploymentFailed("order", "vabc1234", List.of(), "order", "boom");

        Map<String, Object> envelope = captureEnvelope();
        assertThat(envelope).containsEntry("type", "io.dws.deployment.failed");
        assertThat(data(envelope)).containsEntry("error", "boom");
    }

    @Test
    @DisplayName("deploymentDrained/collected carry workflow, version, orchestratorAppId")
    void gcEvents_payload() {
        publisher.deploymentDrained("order", "vabc1234", "order");
        Map<String, Object> drained = captureEnvelope();
        assertThat(drained).containsEntry("type", "io.dws.deployment.drained");
        assertThat(data(drained))
                .containsOnlyKeys("workflow", "version", "orchestratorAppId");
    }

    @Test
    @DisplayName("a publish failure is swallowed, never propagated to the caller")
    void publishFailure_isSwallowed() {
        when(client.publishEvent(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("sidecar down"));

        // Must not throw.
        publisher.deploymentApplied("order", "vabc1234", List.of(), "order");
    }
}
