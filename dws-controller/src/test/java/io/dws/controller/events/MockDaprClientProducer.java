package io.dws.controller.events;

import io.dapr.client.DaprClient;
import io.quarkus.test.Mock;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.mockito.Mockito;

/**
 * CDI alternative that replaces {@link DaprClientProducer} across {@code @QuarkusTest}s with a
 * single Mockito-mocked {@link DaprClient}, so tests can stub/verify {@code publishEvent} without a
 * real Dapr sidecar. The client is produced {@code @Singleton} so it is injected as the raw mock
 * (not an Arc client proxy), letting tests call {@code Mockito.reset/when/verify} on it directly.
 */
@Mock
@Singleton
public class MockDaprClientProducer {

    @Produces
    @Singleton
    public DaprClient daprClient() {
        return Mockito.mock(DaprClient.class);
    }
}
