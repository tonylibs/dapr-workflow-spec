package io.dws.controller.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NodeNamingTest {

  @Test
  void derivesUnnamedScopeIdentifiers() {
    assertThat(NodeNaming.mainNodeId("order-fulfillment")).isEqualTo("order-fulfillment.main");
    assertThat(NodeNaming.catchNodeId("fulfillOrder")).isEqualTo("fulfillOrder.catch");
    assertThat(NodeNaming.branchNodeId("notifyChannels", "notifyRecipients"))
        .isEqualTo("notifyChannels.branch.notifyRecipients");
    assertThat(NodeNaming.branchScopeNodeId("notifyChannels.branch.notifyRecipients", "for"))
        .isEqualTo("notifyChannels.branch.notifyRecipients.for");
  }

  @Test
  void sanitizesDottedAndCamelCaseIdentifiers() {
    assertThat(NodeNaming.appId("fulfillOrder.catch")).isEqualTo("fulfill-order-catch");
    assertThat(NodeNaming.appId("order-fulfillment.main")).isEqualTo("order-fulfillment-main");
    assertThat(NodeNaming.appId("reserveItems")).isEqualTo("reserve-items");
    assertThat(NodeNaming.appId("notifyChannels.branch.notifyRecipients.for"))
        .isEqualTo("notify-channels-branch-notify-recipients-for");
  }

  @Test
  void suffixesFunctionAppIds() {
    assertThat(NodeNaming.functionAppId("reserve-item")).isEqualTo("reserve-item-fn");
  }

  @Test
  void rejectsAnAppIdOverSixtyThreeCharacters() {
    String longName = "a".repeat(70);
    assertThatThrownBy(() -> NodeNaming.appId(longName))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining(longName);
  }

  @Test
  void buildsPerNodeDefinitionResources() {
    assertThat(
            Names.nodeDefinitionResource("order-fulfillment", "v1a2b3c4d", "fulfill-order-catch"))
        .isEqualTo("dws-def-order-fulfillment-v1a2b3c4d-fulfill-order-catch");
  }
}
