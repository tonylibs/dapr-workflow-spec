package io.dws.controller.config;

import io.dapr.client.domain.ConfigurationItem;
import java.util.Objects;
import java.util.Optional;

/** Null-safe access to one item returned by the Dapr Configuration API. */
public final class DaprConfigurationItem {

  private final ConfigurationItem item;

  public DaprConfigurationItem(ConfigurationItem item) {
    this.item = Objects.requireNonNull(item, "item");
  }

  public Optional<String> getKey() {
    return Optional.ofNullable(item.getKey());
  }

  public Optional<String> getValue() {
    return Optional.ofNullable(item.getValue());
  }

  public Optional<String> getVersion() {
    return Optional.ofNullable(item.getVersion());
  }

  public Optional<String> getMetadataByKey(String key) {
    return Optional.ofNullable(item.getMetadata()).map(metadata -> metadata.get(key));
  }
}
