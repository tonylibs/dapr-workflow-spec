package io.dws.controller.k8s;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.dws.controller.config.DwsConfig;
import io.dws.controller.events.EventPublisher;
import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.OrchestratorSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused unit test for the apply-failure publishing wiring: when the apply pass throws, the
 * applier publishes {@code io.dws.deployment.failed} with the error and rethrows the original
 * exception unchanged. Uses mocked collaborators so no cluster or Dapr sidecar is required.
 */
class StackApplierFailurePublishTest {

    private static DeploymentPlan plan() {
        return new DeploymentPlan(
                "order", "vabc1234", "order@vabc1234", "dws-def-order-vabc1234", "spec: text",
                List.of(), List.of(),
                new OrchestratorSpec("orch-order", "img", "order", 8080, 1, Map.of()));
    }

    @Test
    @DisplayName("apply error publishes io.dws.deployment.failed and rethrows the original exception")
    void applyFailurePublishesDeploymentFailed() {
        KubernetesClient client = mock(KubernetesClient.class);
        StackSynthesizer synthesizer = mock(StackSynthesizer.class);
        EventPublisher events = mock(EventPublisher.class);
        DwsConfig config = mock(DwsConfig.class);
        when(config.namespace()).thenReturn("default");
        // Fail on the first cluster interaction inside apply's try block.
        when(client.configMaps()).thenThrow(new RuntimeException("boom"));

        StackApplier applier = new StackApplier(client, synthesizer, events, config);

        assertThatThrownBy(() -> applier.apply(plan()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("boom");

        verify(events).deploymentFailed(eq("order"), eq("vabc1234"), any(), eq("order"), contains("boom"));
    }
}
