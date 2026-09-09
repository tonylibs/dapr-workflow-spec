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

  private static final String GUARDED =
      """
      document:
        dsl: '1.0.0'
        namespace: default
        name: guarded-payment
        version: '1.0.0'
      do:
        - processPayment:
            try:
              - rejectPayment:
                  raise:
                    error:
                      type: https://example.com/errors/payment-rejected
                      status: 402
                      title: Payment rejected
                      detail: Payment authorization failed
            catch:
              errors:
                with:
                  status: 402
              do:
                - recordFailure:
                    set:
                      status: failed
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
            "fulfill-order-try",
            "reserve-items",
            "reserve-items-do",
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
            "notify-recipients-do",
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
  void classifiesAForScopeAsAControllerOverADoChild() throws Exception {
    CompiledNode main = compiler.compile(NESTED).flowStepGraph().get(0);

    CompiledNode loop = node(main, "reserve-items");
    JsonNode spec = JSON.readTree(loop.specText());
    assertThat(spec.get("scope").asText()).isEqualTo("for");
    assertThat(spec.get("tasks")).isEmpty();
    assertThat(spec.get("each").asText()).isEqualTo("item");
    assertThat(spec.get("in").asText()).isEqualTo(".items");
    assertThat(spec.get("children").get("do").asText()).isEqualTo("reserve-items-do");

    CompiledNode body = node(main, "reserve-items-do");
    JsonNode bodySpec = JSON.readTree(body.specText());
    assertThat(bodySpec.get("scope").asText()).isEqualTo("do");
    assertThat(bodySpec.get("tasks")).hasSize(1);
    assertThat(bodySpec.get("children").get("reserveItem").asText()).isEqualTo("reserve-item");
    assertThat(body.children()).singleElement().isInstanceOf(StepNode.class);
  }

  @Test
  void omitsLoopFieldsTheDefinitionDoesNotWrite() throws Exception {
    CompiledNode main = compiler.compile(NESTED).flowStepGraph().get(0);

    JsonNode spec = JSON.readTree(node(main, "reserve-items").specText());
    assertThat(spec.has("at")).isFalse();
    assertThat(spec.has("while")).isFalse();
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

  /**
   * A {@code call}/{@code run} step's {@code -fn} function shares the Dapr app-id namespace with
   * every node, so a sibling task whose own app ID lands on that suffixed name is a collision
   * between two deployables, not a valid definition.
   */
  @Test
  void rejectsANodeAppIdCollidingWithAFunctionAppId() {
    String colliding =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: collide-fn
          version: '1.0.0'
        do:
          - reserveItem:   { call: http, with: { method: get, endpoint: https://x/a } }
          - reserveItemFn: { set: { a: 1 } }
        """;

    assertThatThrownBy(() -> compiler.compile(colliding))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("the function app ID of node 'reserveItem'")
        .hasMessageContaining("node 'reserveItemFn'")
        .hasMessageContaining("reserve-item-fn");
  }

  /**
   * A non-ASCII task name survives {@code Names.kebab} (which splits on {@code
   * Character.isLetterOrDigit}) into an app ID no DNS-1123 label may carry, and which the
   * single-node definition schema's own {@code nodeId} pattern rejects. Compilation must fail
   * rather than render a node whose {@code specText} violates the schema.
   */
  @Test
  void rejectsANonAsciiTaskNameThatSanitizesOutsideDns1123() {
    String nonAscii =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: accented
          version: '1.0.0'
        do:
          - na\u00efveStep:
              set:
                a: 1
        """;
    assertThatThrownBy(() -> compiler.compile(nonAscii))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("na\u00efve-step")
        .hasMessageContaining("DNS-1123");
  }

  /** Finding 1: a task name containing '.' would collide with a derived dotted node id. */
  @Test
  void rejectsATaskNameContainingADot() {
    String dotted =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: dotted
          version: '1.0.0'
        do:
          - alpha.x:
              set:
                a: 1
        """;
    assertThatThrownBy(() -> compiler.compile(dotted))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("alpha.x");
  }

  /**
   * Finding 2, superseded by ADR 0004: a task named {@code catch} inside a {@code try} list used to
   * shadow the dedicated catch child's key because both hung off the same try node. Under ADR 0004
   * the guarded list lives under the {@code try-catch} controller's {@code try} child (a {@code do}
   * sequencer) while the dedicated catch node hangs off the controller itself — different parents,
   * so the two can no longer share a key. This is the evidence: both nodes exist, under different
   * parents, and compilation succeeds.
   */
  @Test
  void aTaskNamedCatchInsideTheGuardedListNoLongerCollidesWithTheDedicatedCatchNode()
      throws Exception {
    String shadowed =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: guard
          version: '1.0.0'
        do:
          - guard:
              try:
                - catch:
                    set:
                      a: 1
              catch:
                do:
                  - markFailed:
                      set:
                        status: failed
        """;

    CompiledNode main = compiler.compile(shadowed).flowStepGraph().get(0);

    JsonNode guardTrySpec = JSON.readTree(node(main, "guard-try").specText());
    assertThat(guardTrySpec.get("children").get("catch").asText()).isEqualTo("catch");

    CompiledNode guardCatch = node(main, "guard-catch");
    JsonNode guardCatchSpec = JSON.readTree(guardCatch.specText());
    assertThat(guardCatchSpec.get("children").get("markFailed").asText()).isEqualTo("mark-failed");

    assertThat(main.flatten())
        .extracting(CompiledNode::appId)
        .contains("guard-try", "catch", "guard-catch", "mark-failed");
  }

  /**
   * Finding 3: a task item carrying two properties silently keeps only the first in the typed
   * model; the raw walk must reject it rather than emit a step whose {@code task} JSON smuggles in
   * a second, node-less task.
   */
  @Test
  void rejectsAMultiPropertyTaskItem() {
    String multi =
        """
        {
          "document": {"dsl": "1.0.0", "namespace": "default", "name": "multi", "version": "1.0.0"},
          "do": [
            { "foo": { "set": { "a": 1 } }, "bar": { "set": { "b": 2 } } }
          ]
        }
        """;
    assertThatThrownBy(() -> compiler.compile(multi))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("foo");
  }

  @Test
  void classifiesATryScopeAsAControllerCarryingItsOwnErrorFilter() throws Exception {
    CompiledNode main = compiler.compile(GUARDED).flowStepGraph().get(0);

    CompiledNode tryCatch = node(main, "process-payment");
    JsonNode spec = JSON.readTree(tryCatch.specText());
    assertThat(spec.get("scope").asText()).isEqualTo("try-catch");
    assertThat(spec.get("tasks")).isEmpty();
    assertThat(spec.get("errors").get("with").get("status").asInt()).isEqualTo(402);
    assertThat(spec.get("catch").asText()).isEqualTo("process-payment-catch");
    assertThat(spec.get("children").get("try").asText()).isEqualTo("process-payment-try");
    assertThat(spec.get("children").get("catch").asText()).isEqualTo("process-payment-catch");

    JsonNode guarded = JSON.readTree(node(main, "process-payment-try").specText());
    assertThat(guarded.get("scope").asText()).isEqualTo("do");
    assertThat(guarded.get("children").get("rejectPayment").asText()).isEqualTo("reject-payment");

    JsonNode recovery = JSON.readTree(node(main, "process-payment-catch").specText());
    assertThat(recovery.get("scope").asText()).isEqualTo("do");
    assertThat(recovery.get("children").get("recordFailure").asText()).isEqualTo("record-failure");
  }

  @Test
  void aTryWithNoErrorFilterOmitsTheErrorsField() throws Exception {
    CompiledNode main = compiler.compile(NESTED).flowStepGraph().get(0);

    JsonNode spec = JSON.readTree(node(main, "fulfill-order").specText());
    assertThat(spec.get("scope").asText()).isEqualTo("try-catch");
    assertThat(spec.has("errors")).isFalse();
    assertThat(spec.has("retry")).isFalse();
    assertThat(spec.get("children").get("try").asText()).isEqualTo("fulfill-order-try");
    assertThat(spec.get("children").get("catch").asText()).isEqualTo("fulfill-order-catch");
  }

  /**
   * A retry-only {@code catch} — no {@code do} — still produces no catch node and no {@code catch}
   * field, but the retry policy now lands on the try-catch controller itself rather than surviving
   * only in the parent's verbatim task entry (ADR 0004).
   */
  @Test
  void aRetryOnlyCatchKeepsItsPolicyOnTheControllerWithNoCatchNode() throws Exception {
    String retryOnly =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: retry-only
          version: '1.0.0'
        do:
          - guarded:
              try:
                - callOut:
                    call: http
                    with:
                      method: get
                      endpoint: https://example.com/thing
              catch:
                retry: myRetryPolicy
        """;

    CompiledNode main = compiler.compile(retryOnly).flowStepGraph().get(0);
    JsonNode spec = JSON.readTree(node(main, "guarded").specText());

    assertThat(spec.get("scope").asText()).isEqualTo("try-catch");
    assertThat(spec.get("retry").asText()).isEqualTo("myRetryPolicy");
    assertThat(spec.has("catch")).isFalse();
    assertThat(spec.get("children").properties()).hasSize(1);
    assertThat(spec.get("children").get("try").asText()).isEqualTo("guarded-try");
  }

  /**
   * A {@code try} with no {@code catch} key at all compiles the same way: scope {@code try-catch},
   * no {@code errors}/{@code retry}/{@code catch} field, one child keyed {@code try}.
   */
  @Test
  void aTryWithNoCatchAtAllHasNoCatchNodeOrField() throws Exception {
    String noCatch =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: unguarded
          version: '1.0.0'
        do:
          - guard:
              try:
                - attempt:
                    set:
                      a: 1
        """;
    DeploymentPlan plan = compiler.compile(noCatch);
    CompiledNode main = plan.flowStepGraph().get(0);

    assertThat(main.flatten())
        .extracting(CompiledNode::appId)
        .containsExactly("unguarded-main", "guard", "guard-try", "attempt");

    CompiledNode guard = node(main, "guard");
    JsonNode spec = JSON.readTree(guard.specText());
    assertThat(spec.get("scope").asText()).isEqualTo("try-catch");
    assertThat(spec.has("catch")).isFalse();
    assertThat(spec.has("errors")).isFalse();
    assertThat(spec.has("retry")).isFalse();
    assertThat(spec.get("children").properties()).hasSize(1);
    assertThat(spec.get("children").get("try").asText()).isEqualTo("guard-try");
  }

  /**
   * A nested {@code do} task owns a task list, which is exactly what a Step does not: it is a Flow
   * scope with its own {@code do} scope value, keeping its own DSL task name as its node id.
   */
  @Test
  void classifiesANestedDoTaskAsItsOwnFlowNode() throws Exception {
    String nested =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: grouped
          version: '1.0.0'
        do:
          - prepareOrder:
              do:
                - stampOrder:
                    set:
                      stamped: true
                - loadCustomer:
                    call: http
                    with:
                      method: get
                      endpoint: https://customers.example.com/1
        """;
    DeploymentPlan plan = compiler.compile(nested);
    CompiledNode main = plan.flowStepGraph().get(0);

    assertThat(main.flatten())
        .extracting(CompiledNode::appId)
        .containsExactly("grouped-main", "prepare-order", "stamp-order", "load-customer");

    CompiledNode prepareOrder = node(main, "prepare-order");
    assertThat(prepareOrder).isInstanceOf(FlowNode.class);
    assertThat(prepareOrder.nodeId()).isEqualTo("prepareOrder");
    assertThat(prepareOrder.children())
        .extracting(CompiledNode::appId)
        .containsExactly("stamp-order", "load-customer");
    assertThat(prepareOrder.children()).allMatch(child -> child instanceof StepNode);

    JsonNode spec = JSON.readTree(prepareOrder.specText());
    assertThat(spec.get("kind").asText()).isEqualTo("flow");
    assertThat(spec.get("scope").asText()).isEqualTo("do");
    assertThat(spec.get("children").get("stampOrder").asText()).isEqualTo("stamp-order");
    assertThat(spec.get("children").get("loadCustomer").asText()).isEqualTo("load-customer");
    assertThat(spec.at("/tasks/0/stampOrder/set/stamped").asBoolean()).isTrue();
    assertChildrenKeysMatchTaskNames(main);
  }

  /** A {@code run} task, like {@code call}, gets a {@code -fn} companion function app ID. */
  @Test
  void classifiesRunTaskWithFunctionAppId() {
    String withRun =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: runner
          version: '1.0.0'
        do:
          - runScript:
              run:
                shell:
                  command: "echo hi"
        """;
    DeploymentPlan plan = compiler.compile(withRun);
    CompiledNode main = plan.flowStepGraph().get(0);
    CompiledNode runScript = node(main, "run-script");
    assertThat(runScript).isInstanceOf(StepNode.class);
    assertThat(((StepNode) runScript).functionAppId()).contains("run-script-fn");
  }

  /**
   * Headline requirement of design §D5: {@code tasks}/{@code task} carry the definition's own JSON
   * verbatim: key order unchanged from the source document (which a reserialized typed-model
   * round-trip would not preserve — Jackson would emit the Java class's own property order
   * instead), and an unknown-to-any-fixed-schema key (inside {@code metadata}'s open map) intact.
   */
  @Test
  void preservesAnUnusualTaskBodyVerbatim() throws Exception {
    String unusual =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: verbatim
          version: '1.0.0'
        do:
          - validateOrder:
              then: end
              metadata:
                x-vendor-note: keep-me
              set:
                zebra: 1
                apple: 2
        """;
    DeploymentPlan plan = compiler.compile(unusual);
    CompiledNode main = plan.flowStepGraph().get(0);
    CompiledNode validateOrder = node(main, "validate-order");
    JsonNode task = JSON.readTree(validateOrder.specText()).get("task").get("validateOrder");

    List<String> keys = new ArrayList<>();
    task.fieldNames().forEachRemaining(keys::add);
    assertThat(keys).containsExactly("then", "metadata", "set");
    assertThat(task.at("/metadata/x-vendor-note").asText()).isEqualTo("keep-me");

    List<String> setKeys = new ArrayList<>();
    task.get("set").fieldNames().forEachRemaining(setKeys::add);
    assertThat(setKeys).containsExactly("zebra", "apple");
  }
}
