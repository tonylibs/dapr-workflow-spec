package io.dws.controller.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A version-scoped Dapr output-binding {@code Component} synthesized for one {@code call: asyncapi}
 * step. {@code name} is the {@code BINDING_NAME} pinned into the step service; {@code type} is the
 * Dapr binding component type selected from the AsyncAPI server protocol (for example {@code
 * bindings.kafka}); {@code metadata} carries the broker connection and destination values (literals
 * or Kubernetes {@code secretKeyRef}s); {@code appId} is the single requesting step's Dapr app-id
 * the Component is scoped to.
 */
public record BindingComponent(
    String name, String type, Map<String, EnvValue> metadata, String appId) {

  public BindingComponent {
    metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
  }
}
