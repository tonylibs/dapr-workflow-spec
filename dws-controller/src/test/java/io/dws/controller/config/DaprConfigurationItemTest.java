package io.dws.controller.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.dapr.client.domain.ConfigurationItem;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DaprConfigurationItemTest {

  @Test
  void exposesItemFieldsAndMetadata() {
    var item =
        new DaprConfigurationItem(
            new ConfigurationItem("compiler.version", "v2", "7", Map.of("source", "operator")));

    assertThat(item.getKey()).contains("compiler.version");
    assertThat(item.getValue()).contains("v2");
    assertThat(item.getVersion()).contains("7");
    assertThat(item.getMetadataByKey("source")).contains("operator");
    assertThat(item.getMetadataByKey("missing")).isEmpty();
  }

  @Test
  void absentFieldsAreEmpty() {
    var item = new DaprConfigurationItem(new ConfigurationItem(null, null, null));

    assertThat(item.getKey()).isEmpty();
    assertThat(item.getValue()).isEmpty();
    assertThat(item.getVersion()).isEmpty();
    assertThat(item.getMetadataByKey("missing")).isEmpty();
  }

  @Test
  void requiresAnItem() {
    assertThatNullPointerException().isThrownBy(() -> new DaprConfigurationItem(null));
  }
}
