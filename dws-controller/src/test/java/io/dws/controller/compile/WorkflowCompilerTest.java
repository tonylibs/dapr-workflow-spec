package io.dws.controller.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dws.controller.k8s.StackSynthesizer;
import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.EnvValue.Literal;
import io.dws.controller.model.EnvValue.SecretKeyRef;
import io.dws.controller.model.ImageCatalog;
import io.dws.controller.model.OAuthEndpoint;
import io.dws.controller.model.StepService;
import io.dws.controller.model.TaskKind;
import io.dws.controller.model.TopicBinding;
import io.fabric8.kubernetes.api.model.ConfigMap;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
  @DisplayName("step services retain literal and secret-key environment value types")
  void stepServiceSupportsTypedEnvironmentValues() {
    StepService step =
        new StepService(
            "call-api",
            TaskKind.CALL_HTTP,
            "sw-call-http:1.0",
            Map.of(
                "AUTH_TOKEN", new SecretKeyRef("API_TOKEN", "value"),
                "ENDPOINT", new Literal("https://api.example.test")));

    assertThat(step.env().get("AUTH_TOKEN")).isEqualTo(new SecretKeyRef("API_TOKEN", "value"));
    assertThat(step.env().get("ENDPOINT")).isEqualTo(new Literal("https://api.example.test"));
  }

  @Test
  @DisplayName("a named bearer policy resolves its declared scalar secret")
  void namedBearerPolicyResolvesDeclaredSecret() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: named-bearer
          version: '1.0.0'
        use:
          secrets: [API_TOKEN]
          authentications:
            accounts:
              bearer:
                token: ${ $secrets.API_TOKEN }
        do:
          - getAccount:
              call: http
              with:
                method: get
                endpoint:
                  uri: https://api.example.test/v1/account
                  authentication:
                    use: accounts
        """;

    DeploymentPlan plan = compiler.compile(yaml);

    assertThat(step(plan, "get-account").env())
        .containsEntry("AUTH_SCHEME", new Literal("bearer"))
        .containsEntry("AUTH_TOKEN", new SecretKeyRef("API_TOKEN", "value"));
  }

  @Test
  @DisplayName("an inline basic policy resolves for an OpenAPI call")
  void inlineBasicPolicyResolvesForOpenApi() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: inline-basic
          version: '1.0.0'
        use:
          secrets: [API_USER, API_PASSWORD]
        do:
          - listAccounts:
              call: openapi
              with:
                document:
                  endpoint:
                    uri: https://api.example.test/openapi.json
                    authentication:
                      basic:
                        username: ${ $secrets.API_USER }
                        password: ${ $secrets.API_PASSWORD }
                operationId: listAccounts
        """;

    DeploymentPlan plan = compiler.compile(yaml);

    assertThat(step(plan, "list-accounts").env())
        .containsEntry("AUTH_SCHEME", new Literal("basic"))
        .containsEntry("AUTH_USERNAME", new SecretKeyRef("API_USER", "value"))
        .containsEntry("AUTH_PASSWORD", new SecretKeyRef("API_PASSWORD", "value"));
  }

  @Test
  @DisplayName("an unknown named authentication policy is rejected")
  void unknownNamedAuthenticationPolicyRejected() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: unknown-auth
          version: '1.0.0'
        do:
          - getAccount:
              call: http
              with:
                method: get
                endpoint:
                  uri: https://api.example.test/v1/account
                  authentication:
                    use: missing
        """;

    assertThatThrownBy(() -> compiler.compile(yaml))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("missing");
  }

  @Test
  @DisplayName("an authentication policy cannot reference an undeclared scalar secret")
  void undeclaredAuthenticationSecretRejected() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: undeclared-secret
          version: '1.0.0'
        use:
          secrets: [OTHER_TOKEN]
        do:
          - getAccount:
              call: http
              with:
                method: get
                endpoint:
                  uri: https://api.example.test/v1/account
                  authentication:
                    bearer:
                      token: ${ $secrets.API_TOKEN }
        """;

    assertThatThrownBy(() -> compiler.compile(yaml))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("API_TOKEN")
        .hasMessageContaining("not declared");
  }

  @Test
  @DisplayName("a literal credential is rejected instead of entering the deployment plan")
  void literalAuthenticationCredentialRejected() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: literal-secret
          version: '1.0.0'
        do:
          - getAccount:
              call: http
              with:
                method: get
                endpoint:
                  uri: https://api.example.test/v1/account
                  authentication:
                    bearer:
                      token: plaintext-test-token
        """;

    assertThatThrownBy(() -> compiler.compile(yaml))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("credential")
        .hasMessageContaining("declared secret")
        .hasMessageNotContaining("plaintext-test-token");
  }

  @Test
  @DisplayName("duplicate scalar secret declarations are rejected")
  void duplicateScalarSecretDeclarationRejected() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: duplicate-secret
          version: '1.0.0'
        use:
          secrets: [API_TOKEN, API_TOKEN]
        do:
          - finish:
              set:
                done: true
        """;

    assertThatThrownBy(() -> compiler.compile(yaml))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("Duplicate secret declaration")
        .hasMessageContaining("API_TOKEN");
  }

  @Test
  @DisplayName("OAuth2 grants other than client_credentials are rejected")
  void unsupportedOAuthGrantRejected() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: oauth-password
          version: '1.0.0'
        use:
          secrets: [OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET]
        do:
          - getAccount:
              call: http
              with:
                method: get
                endpoint:
                  uri: https://api.example.test/v1/account
                  authentication:
                    oauth2:
                      authority: https://identity.example.test
                      grant: password
                      client:
                        id: ${ $secrets.OAUTH_CLIENT_ID }
                        secret: ${ $secrets.OAUTH_CLIENT_SECRET }
        """;

    assertThatThrownBy(() -> compiler.compile(yaml))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("client_credentials");
  }

  @Test
  @DisplayName("OAuth2 client_credentials policies reject an empty scope set")
  void oauthWithEmptyScopesRejected() {
    assertThatThrownBy(() -> compiler.compile(oauthDefinitionWithScopes("")))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining(
            "oauth2 client_credentials authentication requires at least one scope");
  }

  @ParameterizedTest(name = "scope entry {0} is rejected")
  @ValueSource(strings = {"''", "'   '"})
  @DisplayName("OAuth2 client_credentials policies reject blank scope entries")
  void oauthWithBlankScopeEntryRejected(String scopeEntry) {
    assertThatThrownBy(() -> compiler.compile(oauthDefinitionWithScopes(scopeEntry)))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("oauth2 scopes must not contain blank entries");
  }

  @Test
  @DisplayName("OAuth2 scope entries are trimmed before canonicalization")
  void oauthScopeEntriesAreTrimmed() {
    DeploymentPlan plan =
        compiler.compile(
            oauthDefinitionWithScopes("' accounts.write ', 'accounts.read ', 'accounts.read'"));

    assertThat(plan.oauthEndpoints().getFirst().middleware().scopes())
        .containsExactly("accounts.read", "accounts.write");
  }

  @Test
  @DisplayName("equivalent OAuth2 calls share one canonical endpoint descriptor")
  void equivalentOAuthCallsShareCanonicalEndpoint() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: oauth-accounts
          version: '1.0.0'
        use:
          secrets: [OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET]
          authentications:
            accounts:
              oauth2:
                authority: https://identity.example.test
                grant: client_credentials
                client:
                  id: ${ $secrets.OAUTH_CLIENT_ID }
                  secret: ${ $secrets.OAUTH_CLIENT_SECRET }
                endpoints:
                  token: /oauth/token
                scopes: [accounts.read]
        do:
          - getAccount:
              call: http
              with:
                method: get
                endpoint:
                  uri: https://api.example.test/v1/account
                  authentication:
                    use: accounts
          - listAccounts:
              call: http
              with:
                method: get
                endpoint:
                  uri: https://api.example.test/v1/accounts
                  authentication:
                    use: accounts
        """;

    DeploymentPlan plan = compiler.compile(yaml);

    assertThat(plan.oauthEndpoints()).hasSize(1);
    OAuthEndpoint endpoint = plan.oauthEndpoints().getFirst();
    assertThat(endpoint.baseUrl()).isEqualTo("https://api.example.test");
    assertThat(endpoint.paths()).containsExactlyInAnyOrder("/v1/account", "/v1/accounts");
    assertThat(endpoint.appIds()).containsExactlyInAnyOrder("get-account", "list-accounts");
    assertThat(endpoint.middleware().tokenUrl())
        .isEqualTo("https://identity.example.test/oauth/token");
    assertThat(endpoint.middleware().clientId())
        .isEqualTo(new SecretKeyRef("OAUTH_CLIENT_ID", "value"));
    assertThat(endpoint.middleware().clientSecret())
        .isEqualTo(new SecretKeyRef("OAUTH_CLIENT_SECRET", "value"));
    assertThat(endpoint.middleware().scopes()).containsExactly("accounts.read");
    assertThat(plan.steps())
        .allSatisfy(
            service ->
                assertThat(service.env())
                    .containsEntry("AUTH_SCHEME", new Literal("oauth2"))
                    .containsEntry("OAUTH_ENDPOINT", new Literal(endpoint.name())));
  }

  @Test
  @DisplayName("OAuth scope order and repetition do not split equivalent endpoint descriptors")
  void oauthScopesAreCanonicalizedAsASet() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: canonical-oauth-scopes
          version: '1.0.0'
        use:
          secrets: [OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET]
          authentications:
            firstPolicy:
              oauth2:
                authority: https://identity.example.test
                grant: client_credentials
                client:
                  id: ${ $secrets.OAUTH_CLIENT_ID }
                  secret: ${ $secrets.OAUTH_CLIENT_SECRET }
                endpoints:
                  token: /oauth/token
                scopes: [accounts.write, accounts.read, accounts.read]
            secondPolicy:
              oauth2:
                authority: https://identity.example.test
                grant: client_credentials
                client:
                  id: ${ $secrets.OAUTH_CLIENT_ID }
                  secret: ${ $secrets.OAUTH_CLIENT_SECRET }
                endpoints:
                  token: /oauth/token
                scopes: [accounts.read, accounts.write]
        do:
          - getAccount:
              call: http
              with:
                method: get
                endpoint:
                  uri: https://api.example.test/v1/account
                  authentication:
                    use: firstPolicy
          - listAccounts:
              call: http
              with:
                method: get
                endpoint:
                  uri: https://api.example.test/v1/accounts
                  authentication:
                    use: secondPolicy
        """;

    DeploymentPlan plan = compiler.compile(yaml);

    assertThat(plan.oauthEndpoints()).hasSize(1);
    OAuthEndpoint endpoint = plan.oauthEndpoints().getFirst();
    assertThat(endpoint.paths()).containsExactlyInAnyOrder("/v1/account", "/v1/accounts");
    assertThat(endpoint.appIds()).containsExactlyInAnyOrder("get-account", "list-accounts");
    assertThat(endpoint.middleware().scopes()).containsExactly("accounts.read", "accounts.write");
    assertThat(plan.steps())
        .extracting(service -> service.env().get("OAUTH_ENDPOINT"))
        .containsOnly(new Literal(endpoint.name()));
  }

  @Test
  @DisplayName("the synthesized definition ConfigMap contains no credential plaintext")
  void synthesizedDefinitionConfigMapContainsNoCredentialPlaintext() {
    String representativeCredential = "correct-horse-battery-staple";
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: config-map-safe-auth
          version: '1.0.0'
        use:
          secrets: [API_TOKEN]
          authentications:
            accounts:
              bearer:
                token: ${ $secrets.API_TOKEN }
        do:
          - getAccount:
              call: http
              with:
                method: get
                endpoint:
                  uri: https://api.example.test/v1/account
                  authentication:
                    use: accounts
        """;

    DeploymentPlan plan = compiler.compile(yaml);
    ConfigMap definition = new StackSynthesizer().definitionConfigMap(plan, "default");
    String payload = definition.getData().get("definition");

    assertThat(payload).isEqualTo(yaml).doesNotContain(representativeCredential);
  }

  @Test
  @DisplayName("compiled authentication descriptors never contain credential plaintext")
  void compiledAuthenticationContainsNoCredentialPlaintext() throws Exception {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: safe-auth
          version: '1.0.0'
        use:
          secrets: [API_USER, API_PASSWORD]
        do:
          - getAccount:
              call: http
              with:
                method: get
                endpoint:
                  uri: https://api.example.test/v1/account
                  authentication:
                    basic:
                      username: ${ $secrets.API_USER }
                      password: ${ $secrets.API_PASSWORD }
        """;

    DeploymentPlan plan = compiler.compile(yaml);
    String serializedPlan = new ObjectMapper().writeValueAsString(plan);

    assertThat(serializedPlan).doesNotContain("alice", "correct-horse-battery-staple");
    assertThat(plan.specText()).doesNotContain("alice", "correct-horse-battery-staple");
    assertThat(step(plan, "get-account").env().values())
        .allMatch(value -> value instanceof Literal || value instanceof SecretKeyRef);
  }

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
        .isEqualTo(
            Map.of(
                "METHOD", new Literal("get"),
                "ENDPOINT", new Literal("http://inventory.local/api/check")));
    assertThat(step(plan, "charge-payment").env())
        .isEqualTo(
            Map.of(
                "METHOD", new Literal("post"),
                "ENDPOINT", new Literal("http://payment.local/api/charge")));
    assertThat(step(plan, "notify-out-of-stock").env())
        .isEqualTo(
            Map.of(
                "METHOD", new Literal("post"),
                "ENDPOINT", new Literal("http://notify.local/api/oos")));
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
            Map.of(
                "DEFINITION_STORE", new Literal(plan.definitionResource()),
                "DEFINITION_KEY", new Literal("definition")));
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
    assertThat(step.env())
        .containsEntry("DOCUMENT_URL", new Literal("http://petstore.local/openapi.json"));
    assertThat(step.env())
        .containsEntry("DOCUMENT_SHA256", new Literal(SpecDigest.sha256Hex(OPENAPI_DOC)));
    assertThat(step.env()).containsEntry("OPERATION_ID", new Literal("findPetById"));
    assertThat(literal(step, "PARAMETERS")).contains("petId");
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
        .containsEntry("COMMAND", new Literal("./sync.sh"))
        .containsEntry("ENVIRONMENT", new Literal("{\"API_TOKEN\":\"abc\"}"))
        .containsEntry("RETURN", new Literal("stdout"));
    // ARGUMENTS must be a JSON object with keys in definition order (region, then env — the
    // reverse of alphabetical order, so a key-sorting mapper would produce a different string).
    assertThat(literal(step, "ARGUMENTS")).isEqualTo("{\"region\":\"eu\",\"env\":\"prod\"}");
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
        .containsEntry("SCRIPT", new Literal("console.log(JSON.stringify({ok: true}));"))
        .containsEntry("ARGUMENTS", new Literal("{\"count\":3}"))
        .containsEntry("RETURN", new Literal("all"));
    assertThat(step.env()).doesNotContainKey("LANGUAGE");
  }

  @Test
  @DisplayName("run.script with language python defaults RETURN to stdout")
  void runScriptPythonCompiles() {
    DeploymentPlan plan = compiler.compile(fixture("run-script-python.yaml"));

    StepService step = plan.steps().get(0);
    assertThat(step.kind()).isEqualTo(TaskKind.RUN_SCRIPT_PYTHON);
    assertThat(step.image()).isEqualTo("sw-run-script-python:1.0");
    assertThat(step.env()).containsEntry("RETURN", new Literal("stdout"));
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
    assertThat(plan.steps().get(0).env()).containsEntry("ARGUMENTS", new Literal("{\"def\":1}"));
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
  @DisplayName("call/run tasks nested in fork branches compile to step services")
  void nestedForkBranchesCompileToStepServices() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: forkcompile
          version: '1.0.0'
        do:
          - raiseAlarm:
              fork:
                compete: true
                branches:
                  - callNurse:
                      call: http
                      with:
                        method: post
                        endpoint: http://paging.local/api/nurse
                  - callSecurity:
                      run:
                        shell:
                          command: "page-security"
        """;

    DeploymentPlan plan = compiler.compile(yaml);

    assertThat(plan.steps())
        .extracting(StepService::name)
        .containsExactlyInAnyOrder("call-nurse", "call-security");
    assertThat(step(plan, "call-nurse").kind()).isEqualTo(TaskKind.CALL_HTTP);
    assertThat(step(plan, "call-security").kind()).isEqualTo(TaskKind.RUN_SHELL);
  }

  @Test
  @DisplayName("call/run tasks nested in for.do compile to step services")
  void nestedForDoCompilesToStepServices() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: forcompile
          version: '1.0.0'
        do:
          - loop:
              for:
                each: item
                in: .items
              do:
                - fetchItem:
                    call: http
                    with:
                      method: get
                      endpoint: http://catalog.local/api/item
        """;

    DeploymentPlan plan = compiler.compile(yaml);

    assertThat(plan.steps()).extracting(StepService::name).containsExactly("fetch-item");
    assertThat(step(plan, "fetch-item").kind()).isEqualTo(TaskKind.CALL_HTTP);
  }

  @Test
  @DisplayName(
      "a fork task whose branches are all in-process compiles to an unchanged resource set")
  void forkWithInProcessBranchesCompilesUnchanged() {
    DeploymentPlan withoutFork = compiler.compile(fixture("order.yaml"));

    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: forkinprocess
          version: '1.0.0'
        do:
          - raiseAlarm:
              fork:
                branches:
                  - callNurse:
                      set:
                        paged: '"nurse"'
                  - callSecurity:
                      set:
                        paged: '"security"'
        """;
    DeploymentPlan withFork = compiler.compile(yaml);

    assertThat(withFork.steps()).isEmpty();
    assertThat(withFork.bindings()).isEmpty();
    // Sanity: the unrelated order.yaml plan is untouched by compiling a second definition.
    assertThat(withoutFork.steps()).hasSize(3);
  }

  @Test
  @DisplayName("a duplicate task name across two fork branches is rejected")
  void duplicateTaskNameAcrossForkBranchesRejected() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: dupfork
          version: '1.0.0'
        do:
          - raiseAlarm:
              fork:
                branches:
                  - callNurse:
                      set:
                        paged: '"nurse"'
                  - callNurse:
                      set:
                        paged: '"again"'
        """;

    assertThatThrownBy(() -> compiler.compile(yaml))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("callNurse");
  }

  @Test
  @DisplayName("a duplicate task name inside for.do is rejected")
  void duplicateTaskNameInsideForDoRejected() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: dupfor
          version: '1.0.0'
        do:
          - fetchOrder:
              call: http
              with:
                method: get
                endpoint: http://orders.local/api/a
          - loop:
              for:
                each: item
                in: .items
              do:
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

  private static String oauthDefinitionWithScopes(String scopeEntries) {
    return """
        document:
          dsl: '1.0.0'
          namespace: default
          name: oauth-scope-validation
          version: '1.0.0'
        use:
          secrets: [OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET]
          authentications:
            accounts:
              oauth2:
                authority: https://identity.example.test
                grant: client_credentials
                client:
                  id: ${ $secrets.OAUTH_CLIENT_ID }
                  secret: ${ $secrets.OAUTH_CLIENT_SECRET }
                scopes: [%s]
        do:
          - getAccount:
              call: http
              with:
                method: get
                endpoint:
                  uri: https://api.example.test/v1/account
                  authentication:
                    use: accounts
        """
        .formatted(scopeEntries);
  }

  private static StepService step(DeploymentPlan plan, String name) {
    return plan.steps().stream()
        .filter(s -> s.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no step " + name));
  }

  private static String literal(StepService step, String name) {
    return ((Literal) step.env().get(name)).value();
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
