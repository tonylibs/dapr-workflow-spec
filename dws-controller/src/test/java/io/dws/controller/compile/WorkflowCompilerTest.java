package io.dws.controller.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.ImageCatalog;
import io.dws.controller.model.StepService;
import io.dws.controller.model.TaskKind;
import io.dws.controller.model.TopicBinding;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkflowCompilerTest {

  private static final ImageCatalog IMAGES =
      new ImageCatalog(
          "sw-call-http:1.0",
          "sw-call-openapi:1.0",
          "sw-run-shell:1.0",
          "sw-run-script-js:1.0",
          "sw-run-script-python:1.0",
          "sw-orchestrator:1.0");
  private static final byte[] OPENAPI_DOC =
      "OPENAPI-DOCUMENT-BYTES".getBytes(StandardCharsets.UTF_8);

  private final WorkflowCompiler compiler = new WorkflowCompiler(IMAGES, url -> OPENAPI_DOC);

  @Test
  @DisplayName("order.yaml compiles to three call-http steps with exact env")
  void orderCompilesToThreeHttpSteps() {
    DeploymentPlan plan = compiler.compile(fixture("order.yaml"));

    assertThat(plan.steps()).hasSize(3);
    assertThat(plan.steps())
        .allSatisfy(
            s -> {
              assertThat(s.kind()).isEqualTo(TaskKind.CALL_HTTP);
              assertThat(s.image()).isEqualTo("sw-call-http:1.0");
            });
    assertThat(plan.steps())
        .extracting(StepService::name)
        .containsExactly("check-inventory", "charge-payment", "notify-out-of-stock");

    assertThat(step(plan, "check-inventory").env())
        .isEqualTo(Map.of("METHOD", "get", "ENDPOINT", "http://inventory.local/api/check"));
    assertThat(step(plan, "charge-payment").env())
        .isEqualTo(Map.of("METHOD", "post", "ENDPOINT", "http://payment.local/api/charge"));
    assertThat(step(plan, "notify-out-of-stock").env())
        .isEqualTo(Map.of("METHOD", "post", "ENDPOINT", "http://notify.local/api/oos"));
  }

  @Test
  @DisplayName("order.yaml produces a one-replica orchestrator wired to the definition ConfigMap")
  void orderOrchestratorSpec() {
    DeploymentPlan plan = compiler.compile(fixture("order.yaml"));

    assertThat(plan.workflow()).isEqualTo("order");
    assertThat(plan.orchestrator().name()).isEqualTo("order-" + plan.versionId());
    assertThat(plan.orchestrator().image()).isEqualTo("sw-orchestrator:1.0");
    assertThat(plan.orchestrator().appId()).isEqualTo("order");
    assertThat(plan.orchestrator().appPort()).isEqualTo(8080);
    assertThat(plan.orchestrator().replicas()).isEqualTo(1);
    assertThat(plan.orchestrator().env())
        .isEqualTo(
            Map.of("DEFINITION_STORE", plan.definitionResource(), "DEFINITION_KEY", "definition"));
    assertThat(plan.definitionResource()).isEqualTo("dws-def-order-" + plan.versionId());
  }

  @Test
  @DisplayName("version is content-addressed and stable across reruns")
  void versionIsStable() {
    String text = fixture("order.yaml");

    DeploymentPlan first = compiler.compile(text);
    DeploymentPlan second = compiler.compile(text);

    assertThat(first.version()).isEqualTo(second.version());
    assertThat(first.version()).startsWith("order@v");
    assertThat(first.versionId()).hasSize(9).startsWith("v");
  }

  @Test
  @DisplayName("reordering keys / whitespace does not change the version")
  void versionIgnoresFormatting() {
    DeploymentPlan a = compiler.compile(fixture("order.yaml"));
    String reformatted = fixture("order.yaml").replace("  ", "   ").replaceAll("\r\n", "\n");

    DeploymentPlan b = compiler.compile(reformatted);

    assertThat(b.version()).isEqualTo(a.version());
  }

  @Test
  @DisplayName("call openapi pins the document by sha256 and injects operation/parameters")
  void openApiStepPinsDocumentHash() {
    DeploymentPlan plan = compiler.compile(fixture("petstore-openapi.yaml"));

    assertThat(plan.steps()).hasSize(1);
    StepService step = plan.steps().get(0);
    assertThat(step.name()).isEqualTo("find-pet");
    assertThat(step.kind()).isEqualTo(TaskKind.CALL_OPENAPI);
    assertThat(step.image()).isEqualTo("sw-call-openapi:1.0");
    assertThat(step.env()).containsEntry("DOCUMENT_URL", "http://petstore.local/openapi.json");
    assertThat(step.env()).containsEntry("DOCUMENT_SHA256", SpecDigest.sha256Hex(OPENAPI_DOC));
    assertThat(step.env()).containsEntry("OPERATION_ID", "findPetById");
    assertThat(step.env().get("PARAMETERS")).contains("petId");
  }

  @Test
  @DisplayName("run.shell compiles to a RUN_SHELL step with ordered arguments")
  void runShellCompiles() {
    DeploymentPlan plan = compiler.compile(fixture("run-shell.yaml"));

    assertThat(plan.steps()).hasSize(1);
    StepService step = plan.steps().get(0);
    assertThat(step.name()).isEqualTo("sync-inventory");
    assertThat(step.kind()).isEqualTo(TaskKind.RUN_SHELL);
    assertThat(step.image()).isEqualTo("sw-run-shell:1.0");
    assertThat(step.env())
        .containsEntry("COMMAND", "./sync.sh")
        .containsEntry("ENVIRONMENT", "{\"API_TOKEN\":\"abc\"}")
        .containsEntry("RETURN", "stdout");
    // ARGUMENTS must be a JSON object with keys in definition order (region, then env — the
    // reverse of alphabetical order, so a key-sorting mapper would produce a different string).
    assertThat(step.env().get("ARGUMENTS")).isEqualTo("{\"region\":\"eu\",\"env\":\"prod\"}");
  }

  @Test
  @DisplayName("run.script with language js compiles to a RUN_SCRIPT_JS step")
  void runScriptJsCompiles() {
    DeploymentPlan plan = compiler.compile(fixture("run-script-js.yaml"));

    StepService step = plan.steps().get(0);
    assertThat(step.name()).isEqualTo("transform-order");
    assertThat(step.kind()).isEqualTo(TaskKind.RUN_SCRIPT_JS);
    assertThat(step.image()).isEqualTo("sw-run-script-js:1.0");
    assertThat(step.env())
        .containsEntry("SCRIPT", "console.log(JSON.stringify({ok: true}));")
        .containsEntry("ARGUMENTS", "{\"count\":3}")
        .containsEntry("RETURN", "all");
    assertThat(step.env()).doesNotContainKey("LANGUAGE");
  }

  @Test
  @DisplayName("run.script with language python defaults RETURN to stdout")
  void runScriptPythonCompiles() {
    DeploymentPlan plan = compiler.compile(fixture("run-script-python.yaml"));

    StepService step = plan.steps().get(0);
    assertThat(step.kind()).isEqualTo(TaskKind.RUN_SCRIPT_PYTHON);
    assertThat(step.image()).isEqualTo("sw-run-script-python:1.0");
    assertThat(step.env()).containsEntry("RETURN", "stdout");
  }

  @Test
  @DisplayName("run.script with an unsupported language is rejected")
  void runScriptUnsupportedLanguageRejected() {
    assertThatThrownBy(() -> compiler.compile(fixture("run-script-bad-language.yaml")))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("ruby");
  }

  @Test
  @DisplayName("run.script with an external source is rejected")
  void runScriptExternalSourceRejected() {
    assertThatThrownBy(() -> compiler.compile(fixture("run-script-source.yaml")))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("external script");
  }

  @Test
  @DisplayName("run.container is rejected with a clear message")
  void runContainerRejected() {
    assertThatThrownBy(() -> compiler.compile(fixture("run-container.yaml")))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("container");
  }

  @Test
  @DisplayName("run.workflow is rejected with a clear message")
  void runWorkflowRejected() {
    assertThatThrownBy(() -> compiler.compile(fixture("run-workflow.yaml")))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("workflow");
  }

  @Test
  @DisplayName("an argument name that is not a valid identifier is rejected for script tasks")
  void runScriptInvalidArgumentNameRejected() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: badargs
          version: '1.0.0'
        do:
          - transformOrder:
              run:
                script:
                  language: js
                  code: "1"
                  arguments:
                    has-dash: 1
        """;
    assertThatThrownBy(() -> compiler.compile(yaml))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("has-dash");
  }

  @Test
  @DisplayName("run.script with a JS argument name that is a reserved JS keyword is rejected")
  void runScriptJsReservedKeywordRejected() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: jskeyword
          version: '1.0.0'
        do:
          - transformOrder:
              run:
                script:
                  language: js
                  code: "1"
                  arguments:
                    const: 1
        """;
    assertThatThrownBy(() -> compiler.compile(yaml))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("const")
        .hasMessageContaining("reserved");
  }

  @Test
  @DisplayName(
      "run.script with a Python argument name that is a reserved Python keyword is rejected")
  void runScriptPythonReservedKeywordRejected() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: pykeyword
          version: '1.0.0'
        do:
          - transformOrder:
              run:
                script:
                  language: python
                  code: "1"
                  arguments:
                    None: 1
        """;
    assertThatThrownBy(() -> compiler.compile(yaml))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("None")
        .hasMessageContaining("reserved");
  }

  @Test
  @DisplayName(
      "run.script with a JS argument name that is a reserved Python keyword (but valid JS) is accepted")
  void runScriptJsAllowsPythonOnlyKeyword() {
    // "def" is a Python keyword but a perfectly valid JS identifier -- only "class" appears in
    // both lists, so it wouldn't demonstrate cross-language independence.
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: crosslang
          version: '1.0.0'
        do:
          - transformOrder:
              run:
                script:
                  language: js
                  code: "1"
                  arguments:
                    def: 1
        """;
    DeploymentPlan plan = compiler.compile(yaml);

    assertThat(plan.steps()).hasSize(1);
    assertThat(plan.steps().get(0).env()).containsEntry("ARGUMENTS", "{\"def\":1}");
  }

  @Test
  @DisplayName(
      "run.script with an argument name colliding with an internal prelude identifier is rejected")
  void runScriptInternalNameCollisionRejected() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: internalcollision
          version: '1.0.0'
        do:
          - transformOrder:
              run:
                script:
                  language: js
                  code: "1"
                  arguments:
                    __dwsArgs: 1
        """;
    assertThatThrownBy(() -> compiler.compile(yaml))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("__dwsArgs")
        .hasMessageContaining("internal");
  }

  @Test
  @DisplayName("invalid definition throws with a non-empty error list and nothing is produced")
  void invalidDefinitionThrows() {
    assertThatThrownBy(() -> compiler.compile(fixture("broken.yaml")))
        .isInstanceOf(CompilationException.class)
        .satisfies(e -> assertThat(((CompilationException) e).errors()).isNotEmpty());
  }

  @Test
  @DisplayName("call/run tasks nested in try/catch.do compile to step services")
  void nestedTryTasksCompileToStepServices() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: trycompile
          version: '1.0.0'
        do:
          - guarded:
              try:
                - fetchOrder:
                    call: http
                    with:
                      method: get
                      endpoint: http://orders.local/api/one
              catch:
                errors:
                  with:
                    status: 503
                do:
                  - notifyFailure:
                      run:
                        shell:
                          command: "echo failed"
        """;

    DeploymentPlan plan = compiler.compile(yaml);

    assertThat(plan.steps())
        .extracting(StepService::name)
        .containsExactlyInAnyOrder("fetch-order", "notify-failure");
    assertThat(step(plan, "fetch-order").kind()).isEqualTo(TaskKind.CALL_HTTP);
    assertThat(step(plan, "fetch-order").image()).isEqualTo("sw-call-http:1.0");
    assertThat(step(plan, "notify-failure").kind()).isEqualTo(TaskKind.RUN_SHELL);
    assertThat(step(plan, "notify-failure").image()).isEqualTo("sw-run-shell:1.0");
  }

  @Test
  @DisplayName("emit/listen tasks nested in try/catch.do produce topic bindings")
  void nestedTryTasksProduceTopicBindings() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: trybindings
          version: '1.0.0'
        do:
          - guarded:
              try:
                - orderFetched:
                    emit:
                      event:
                        with:
                          source: https://shop.local
                          type: shop.order.fetched
              catch:
                do:
                  - awaitRetrySignal:
                      listen:
                        to:
                          one:
                            with:
                              type: shop.order.retry
        """;

    DeploymentPlan plan = compiler.compile(yaml);

    assertThat(plan.bindings())
        .extracting(TopicBinding::task)
        .containsExactlyInAnyOrder("orderFetched", "awaitRetrySignal");
    assertThat(plan.bindings())
        .filteredOn(b -> b.task().equals("orderFetched"))
        .singleElement()
        .extracting(TopicBinding::direction)
        .isEqualTo(TopicBinding.Direction.EMIT);
    assertThat(plan.bindings())
        .filteredOn(b -> b.task().equals("awaitRetrySignal"))
        .singleElement()
        .extracting(TopicBinding::direction)
        .isEqualTo(TopicBinding.Direction.LISTEN);
  }

  @Test
  @DisplayName("a task name duplicated across depths is rejected")
  void duplicateTaskNameAcrossDepthsRejected() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: dupnested
          version: '1.0.0'
        do:
          - fetchOrder:
              call: http
              with:
                method: get
                endpoint: http://orders.local/api/a
          - guarded:
              try:
                - fetchOrder:
                    call: http
                    with:
                      method: get
                      endpoint: http://orders.local/api/b
              catch: {}
        """;

    assertThatThrownBy(() -> compiler.compile(yaml))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("fetchOrder");
  }

  @Test
  @DisplayName("a task name duplicated at the same depth is rejected")
  void duplicateTaskNameAtSameDepthRejected() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: dupflat
          version: '1.0.0'
        do:
          - fetchOrder:
              call: http
              with:
                method: get
                endpoint: http://orders.local/api/a
          - fetchOrder:
              call: http
              with:
                method: get
                endpoint: http://orders.local/api/b
        """;

    assertThatThrownBy(() -> compiler.compile(yaml))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("fetchOrder");
  }

  @Test
  @DisplayName("a definition without a try task compiles to an unchanged resource set")
  void definitionWithoutTryCompilesUnchanged() {
    DeploymentPlan plan = compiler.compile(fixture("order.yaml"));

    assertThat(plan.steps())
        .extracting(StepService::name)
        .containsExactly("check-inventory", "charge-payment", "notify-out-of-stock");
    assertThat(plan.bindings()).isEmpty();
  }

  private static StepService step(DeploymentPlan plan, String name) {
    return plan.steps().stream()
        .filter(s -> s.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no step " + name));
  }

  private static String fixture(String name) {
    try (var in = WorkflowCompilerTest.class.getResourceAsStream("/fixtures/" + name)) {
      if (in == null) {
        throw new AssertionError("missing fixture " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
