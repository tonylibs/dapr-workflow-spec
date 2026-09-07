package io.dws.controller.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dws.controller.model.CompiledNode;
import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.FlowNode;
import io.dws.controller.model.StepNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class V2StructuralCompilerTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final String NESTED =
      """
      document:
        dsl: '1.0.0'
        namespace: default
        name: order-fulfillment
        version: '1.0.0'
      do:
        - validateOrder:
            set:
              status: validating
            then: fulfillOrder
        - fulfillOrder:
            try:
              - reserveItems:
                  for:
                    each: item
                    in: .items
                  do:
                    - reserveItem:
                        call: http
                        with:
                          method: post
                          endpoint: https://inventory.example.com/reservations
            catch:
              do:
                - markOrderFailed:
                    set:
                      status: failed
            then: end
      """;

  private static final String FORKED =
      """
      document:
        dsl: '1.0.0'
        namespace: default
        name: notify-order
        version: '1.0.0'
      do:
        - prepareNotification:
            set:
              status: ready
            then: notifyChannels
        - notifyChannels:
            fork:
              compete: false
              branches:
                - notifyRecipients:
                    for:
                      each: recipient
                      in: .recipients
                    do:
                      - sendEmail:
                          call: http
                          with:
                            method: post
                            endpoint: https://email.example.com/send
                - writeAudit:
                    set:
                      auditStatus: recorded
            then: end
      """;

  private final V2StructuralCompiler compiler = new V2StructuralCompiler();

  /**
   * The invariant the single-node-definition contract rests on: a flow dispatches its children
   * through a map keyed by task name, so every {@code children} key must be the name of the
   * corresponding {@code tasks} entry — the dedicated {@code catch} child aside. A fork node
   * sequences no tasks of its own, fanning out to branch children instead, so it is skipped.
   */
  private static void assertChildrenKeysMatchTaskNames(CompiledNode root) throws Exception {
    for (CompiledNode node : root.flatten()) {
      if (!(node instanceof FlowNode)) {
        continue;
      }
      JsonNode spec = JSON.readTree(node.specText());
      if (spec.get("tasks").isEmpty()) {
        continue;
      }
      List<String> taskNames = new ArrayList<>();
      spec.get("tasks")
          .forEach(task -> taskNames.add(task.properties().iterator().next().getKey()));
      List<String> childKeys = new ArrayList<>();
      for (Map.Entry<String, JsonNode> child : spec.get("children").properties()) {
        if (!(spec.has("catch") && child.getKey().equals("catch"))) {
          childKeys.add(child.getKey());
        }
      }
      assertThat(childKeys).describedAs(node.appId()).isEqualTo(taskNames);
    }
  }

  private static CompiledNode node(CompiledNode root, String appId) {
    return root.flatten().stream().filter(n -> n.appId().equals(appId)).findFirst().orElseThrow();
  }

  @Test
  void classifiesNestedTryForAndCatch() {
    DeploymentPlan plan = compiler.compile(NESTED);

    assertThat(plan.flowStepGraph()).hasSize(1);
    CompiledNode main = plan.flowStepGraph().get(0);
    assertThat(main).isInstanceOf(FlowNode.class);
    assertThat(main.nodeId()).isEqualTo("order-fulfillment.main");
    assertThat(main.appId()).isEqualTo("order-fulfillment-main");

    assertThat(main.flatten())
        .extracting(CompiledNode::appId)
        .containsExactly(
            "order-fulfillment-main",
            "validate-order",
            "fulfill-order",
            "reserve-items",
            "reserve-item",
            "fulfill-order-catch",
            "mark-order-failed");

    CompiledNode reserveItem =
        main.flatten().stream()
            .filter(n -> n.appId().equals("reserve-item"))
            .findFirst()
            .orElseThrow();
    assertThat(reserveItem).isInstanceOf(StepNode.class);
    assertThat(((StepNode) reserveItem).functionAppId()).contains("reserve-item-fn");

    CompiledNode validateOrder =
        main.flatten().stream()
            .filter(n -> n.appId().equals("validate-order"))
            .findFirst()
            .orElseThrow();
    assertThat(((StepNode) validateOrder).functionAppId()).isEmpty();
  }

  @Test
  void classifiesForkAsItsOwnFlowNodeWithBranchNodes() {
    DeploymentPlan plan = compiler.compile(FORKED);
    CompiledNode main = plan.flowStepGraph().get(0);

    assertThat(main.flatten())
        .extracting(CompiledNode::appId)
        .containsExactly(
            "notify-order-main",
            "prepare-notification",
            "notify-channels",
            "notify-channels-branch-notify-recipients",
            "notify-recipients",
            "send-email",
            "notify-channels-branch-write-audit",
            "write-audit");

    CompiledNode fork = node(main, "notify-channels");
    assertThat(fork).isInstanceOf(FlowNode.class);
    assertThat(fork.specText()).contains("\"forkMode\" : \"all\"").contains("\"tasks\" : [ ]");
    assertThat(fork.children())
        .extracting(CompiledNode::key)
        .containsExactly("notifyRecipients", "writeAudit");

    CompiledNode branchFor = node(main, "notify-recipients");
    assertThat(branchFor.nodeId()).isEqualTo("notifyRecipients");
    assertThat(branchFor.key()).isEqualTo("notifyRecipients");
  }

  @Test
  void keysEveryFlowNodesChildrenByItsOwnTaskNames() throws Exception {
    for (String specText : new String[] {NESTED, FORKED}) {
      assertChildrenKeysMatchTaskNames(compiler.compile(specText).flowStepGraph().get(0));
    }
  }

  @Test
  void populatesIdentityAndLeavesLegacyFieldsEmpty() {
    DeploymentPlan plan = compiler.compile(NESTED);

    assertThat(plan.workflow()).isEqualTo("order-fulfillment");
    assertThat(plan.versionId()).startsWith("v");
    assertThat(plan.version()).isEqualTo("order-fulfillment@" + plan.versionId());
    assertThat(plan.definitionResource())
        .isEqualTo("dws-def-order-fulfillment-" + plan.versionId());
    assertThat(plan.specText()).isEqualTo(NESTED);
    assertThat(plan.steps()).isEmpty();
    assertThat(plan.bindings()).isEmpty();
    assertThat(plan.orchestrator()).isNull();
    assertThat(plan.oauthEndpoints()).isEmpty();
    assertThat(plan.bindingComponents()).isEmpty();
  }

  @Test
  void givesEachNodeItsOwnDefinitionResource() {
    DeploymentPlan plan = compiler.compile(NESTED);
    CompiledNode catchNode =
        plan.flowStepGraph().get(0).flatten().stream()
            .filter(n -> n.appId().equals("fulfill-order-catch"))
            .findFirst()
            .orElseThrow();
    assertThat(catchNode.definitionResource())
        .isEqualTo("dws-def-order-fulfillment-" + plan.versionId() + "-fulfill-order-catch");
  }

  @Test
  void rejectsAnEmptyDefinition() {
    assertThatThrownBy(() -> compiler.compile("  "))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void rejectsCollidingDerivedIdentifiers() {
    String colliding =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: collide
          version: '1.0.0'
        do:
          - reserveItem:
              set:
                a: 1
          - reserve-item:
              set:
                b: 2
        """;
    assertThatThrownBy(() -> compiler.compile(colliding))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("reserve-item");
  }
}
