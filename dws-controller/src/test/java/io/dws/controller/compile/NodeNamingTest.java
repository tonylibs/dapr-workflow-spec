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
  }

  @Test
  void sanitizesDottedAndCamelCaseIdentifiers() {
    assertThat(NodeNaming.appId("fulfillOrder.catch")).isEqualTo("fulfill-order-catch");
    assertThat(NodeNaming.appId("order-fulfillment.main")).isEqualTo("order-fulfillment-main");
    assertThat(NodeNaming.appId("reserveItems")).isEqualTo("reserve-items");
    assertThat(NodeNaming.appId("notifyChannels.branch.notifyRecipients"))
        .isEqualTo("notify-channels-branch-notify-recipients");
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

  /**
   * {@code Names.kebab} splits on {@code Character.isLetterOrDigit}, which passes non-ASCII
   * letters, so the sanitized id can still fall outside the DNS-1123 character class the
   * single-node definition schema pins for {@code nodeId}.
   */
  @Test
  void rejectsADerivedAppIdOutsideTheDns1123CharacterClass() {
    assertThatThrownBy(() -> NodeNaming.appId("na\u00efveStep"))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("na\u00efveStep")
        .hasMessageContaining("na\u00efve-step")
        .hasMessageContaining("DNS-1123");
  }

  @Test
  void buildsPerNodeDefinitionResources() {
    assertThat(
            Names.nodeDefinitionResource("order-fulfillment", "v1a2b3c4d", "fulfill-order-catch"))
        .isEqualTo("dws-def-order-fulfillment-v1a2b3c4d-fulfill-order-catch");
  }

  @Test
  void rejectsFunctionAppIdThatExceedsSixtyThreeCharacters() {
    // An appId of 61 characters + "-fn" (3 chars) = 64 chars, exceeding the limit
    String appIdAt61Chars = "a".repeat(61);
    assertThatThrownBy(() -> NodeNaming.functionAppId(appIdAt61Chars))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining(appIdAt61Chars)
        .hasMessageContaining(appIdAt61Chars + "-fn");
  }

  @Test
  void rejectsBlankDerivedAppId() {
    // A nodeId with only symbols (no alphanumerics) produces an empty appId
    String symbolOnlyNodeId = "___";
    assertThatThrownBy(() -> NodeNaming.appId(symbolOnlyNodeId))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining(symbolOnlyNodeId);
  }
}
