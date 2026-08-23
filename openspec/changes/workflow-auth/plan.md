# Workflow Authentication and Secrets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add controller-compiled scalar secret references and basic, bearer, and Dapr-native OAuth2 client-credentials authentication for HTTP and OpenAPI workflow calls.

**Architecture:** The controller resolves declared secret names and authentication policies into typed environment references and version-scoped Dapr resources. The synthesizer renders Kubernetes `secretKeyRef` projections; the orchestrator binds projected values as `$secrets`; Go and TypeScript runners attach local headers or invoke Dapr external endpoints. No component reads Secret values during compilation.

**Tech Stack:** Java 21/Quarkus/Fabric8 Kubernetes client, Spring Boot/Dapr Workflows/Jackson/jq, Go `net/http`, TypeScript/Fastify/undici/swagger-client, Kubernetes Secrets, Dapr `HTTPEndpoint` and OAuth2 middleware.

**Spec:** `openspec/changes/workflow-auth/design.md`; `openspec/changes/workflow-auth/specs/workflow-secrets/spec.md`; `openspec/changes/workflow-auth/specs/workflow-authentication/spec.md`

## Global Constraints

- Keep definitions content-addressed; auth and secret resource names include the same immutable workflow-version identity.
- A declared secret `NAME` always maps to Kubernetes Secret `NAME`, data key `value`.
- Never serialize a credential value into `StepService`, a ConfigMap, a controller log, or a generated Dapr resource literal.
- Support only basic, bearer, and OAuth2 `client_credentials`; preserve standalone OpenAPI API-key and secret-store configuration.
- Upgrade `charts/dws` to stable Dapr 1.18.1, which provides OAuth middleware `pathFilter`; integration tests accept an override for later Dapr upgrades.
- `$secrets` in `set` and `switch` is an explicit DWS extension and must be documented as potentially leaking material.

---

### Task 1: Update the roadmap and introduce typed controller environment values

**Files:**
- Modify: `docs/roadmaps/openworkflow-features.md`
- Modify: `dws-controller/src/main/java/io/dws/controller/model/StepService.java`
- Modify: `dws-controller/src/main/java/io/dws/controller/model/OrchestratorSpec.java`
- Modify: controller model call sites and `dws-controller/src/test/java/io/dws/controller/compile/WorkflowCompilerTest.java`

**Interfaces:**
- Produces: an environment value model equivalent to `Literal(String value)` or `SecretKeyRef(String name, String key)`.
- Consumes: current literal `Map<String, String>` environment contract.

- [ ] **Step 1: Write the failing model tests for literal and secret values**

```java
assertThat(step.env().get("AUTH_TOKEN")).isEqualTo(new SecretKeyRef("apitoken", "value"));
assertThat(step.env().get("ENDPOINT")).isEqualTo(new Literal("https://api.example.test"));
```

- [ ] **Step 2: Run the targeted controller test and verify compilation fails**

Run: `./mvnw -Dtest=WorkflowCompilerTest test`

Expected: FAIL because `SecretKeyRef` and `Literal` do not exist and `env()` remains literal-only.

- [ ] **Step 3: Replace the literal-only map with a sealed environment value type and update constructors**

```java
public sealed interface EnvValue permits EnvValue.Literal, EnvValue.SecretKeyRef {
  record Literal(String value) implements EnvValue {}
  record SecretKeyRef(String name, String key) implements EnvValue {}
}
```

Use `EnvValue.Literal` at every existing compiler call site so current definitions retain exactly
their old values.

- [ ] **Step 4: Correct the Phase 3/4 roadmap status and record the DWS secret extension**

Change the Phase 3 table row and diagram label from pending/next to complete, mark Phase 4 as
current, and add a concise Phase 4 note that `$secrets` in `set`/`switch` is DWS-specific and can
expose data.

- [ ] **Step 5: Run focused verification and commit**

Run: `./mvnw -Dtest=WorkflowCompilerTest test`

Expected: PASS.

```bash
git add docs/roadmaps/openworkflow-features.md dws-controller/src/main/java/io/dws/controller/model dws-controller/src/test/java/io/dws/controller/compile/WorkflowCompilerTest.java
git commit -m "feat: add typed step environment values"
```

### Task 2: Compile declared scalar secrets and authentication policies

**Files:**
- Modify: `dws-controller/src/main/java/io/dws/controller/compile/WorkflowCompiler.java:251-310`
- Modify: `dws-controller/src/main/java/io/dws/controller/model/DeploymentPlan.java`
- Create: `dws-controller/src/main/java/io/dws/controller/model/OAuthEndpoint.java`
- Create: `dws-controller/src/main/java/io/dws/controller/model/OAuthMiddleware.java`
- Test: `dws-controller/src/test/java/io/dws/controller/compile/WorkflowCompilerTest.java`

**Interfaces:**
- Produces: normalized auth env keys `AUTH_SCHEME`, `AUTH_USERNAME`, `AUTH_PASSWORD`,
  `AUTH_TOKEN`, or OAuth endpoint metadata, with credential entries represented only by
  `EnvValue.SecretKeyRef`.
- Produces: canonical OAuth resource definitions keyed by host plus normalized policy.

- [ ] **Step 1: Add failing compiler fixtures for named, inline, and invalid policies**

```yaml
use:
  secrets: [apiuser, apipassword]
  authentications:
    accounts:
      basic:
        username: ${ $secrets.apiuser }
        password: ${ $secrets.apipassword }
```

Assert a named policy resolves, an inline policy resolves, and unknown policies, undeclared secret
names, literal credentials, duplicate declarations, and non-`client_credentials` OAuth grants fail.

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./mvnw -Dtest=WorkflowCompilerTest test`

Expected: FAIL because the compiler does not yet inspect `use.secrets` or endpoint authentication.

- [ ] **Step 3: Add pure secret/auth resolution helpers and wire HTTP/OpenAPI compilation**

Implement a resolver returning a normalized immutable descriptor, conceptually:

```java
record ResolvedAuth(AuthScheme scheme, Map<String, EnvValue.SecretKeyRef> credentials,
                    Optional<OAuthEndpoint> oauthEndpoint) {}
```

Use it from both `httpStep` and `openApiStep`; reuse a named policy exactly as the existing
retry/timeout helpers reuse named definitions. Do not evaluate or log secret values.

- [ ] **Step 4: Add no-plaintext and unchanged-definition assertions**

Serialize the plan and generated definition ConfigMap input in tests; assert neither contains test
secret values. Compile an existing no-auth fixture and assert the prior runner environment contract
is unchanged.

- [ ] **Step 5: Run controller tests and commit**

Run: `./mvnw -Dtest=WorkflowCompilerTest test`

Expected: PASS.

```bash
git add dws-controller/src/main/java/io/dws/controller/compile/WorkflowCompiler.java dws-controller/src/main/java/io/dws/controller/model dws-controller/src/test/java/io/dws/controller/compile/WorkflowCompilerTest.java
git commit -m "feat: compile workflow auth policies"
```

### Task 3: Render secret and Dapr OAuth resources

**Files:**
- Modify: `dws-controller/src/main/java/io/dws/controller/k8s/StackSynthesizer.java:297-301`
- Modify: `dws-controller/src/main/java/io/dws/controller/model/DeploymentPlan.java`
- Test: `dws-controller/src/test/java/io/dws/controller/k8s/StackSynthesizerTest.java`

**Interfaces:**
- Consumes: `EnvValue` and canonical OAuth resource descriptors from Task 2.
- Produces: Fabric8 `EnvVar` objects with `EnvVarSource.secretKeyRef`, plus `HTTPEndpoint`, OAuth
  Component, and Configuration manifests scoped to requesting app IDs.

- [ ] **Step 1: Write failing synthesizer tests for secret projections**

```java
assertThat(container.getEnv().get(0).getValueFrom().getSecretKeyRef().getName()).isEqualTo("apitoken");
assertThat(container.getEnv().get(0).getValueFrom().getSecretKeyRef().getKey()).isEqualTo("value");
```

Also assert literal environment values keep `valueFrom == null`.

- [ ] **Step 2: Run the synthesizer test and verify it fails**

Run: `./mvnw -Dtest=StackSynthesizerTest test`

Expected: FAIL because `envVars` constructs `new EnvVar(name, value, null)` for every entry.

- [ ] **Step 3: Render typed values and OAuth manifests**

Branch in `envVars` on `EnvValue` and create `EnvVarSource` with `SecretKeySelector` for a secret.
Render the OAuth resources with no credential `value` fields: Component metadata references the
same Kubernetes Secret keys, `HTTPEndpoint` uses the canonical base URL and step-app scopes, and
Configuration contains the normalized handler plus the narrow generated `pathFilter`.

- [ ] **Step 4: Add resource canonicalization tests**

Compile two same-host/same-policy calls and assert one resource set with both app IDs in scope;
compile different policy content and assert distinct version-scoped names. Assert the generated
resource serialization contains secret names but not test values.

- [ ] **Step 5: Run controller verification and commit**

Run: `./mvnw test`

Expected: PASS.

```bash
git add dws-controller/src/main/java/io/dws/controller/k8s/StackSynthesizer.java dws-controller/src/main/java/io/dws/controller/model/DeploymentPlan.java dws-controller/src/test/java/io/dws/controller/k8s/StackSynthesizerTest.java
git commit -m "feat: synthesize workflow secret and oauth resources"
```

### Task 4: Bind startup secrets into orchestrator jq evaluation

**Files:**
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/config/WorkflowRuntimeBootstrap.java`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/WorkflowSupport.java`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/EvaluateSetActivity.java:95-96`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/EvaluateSwitchActivity.java:41`
- Test: `dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/activity/EvaluateSetActivityTest.java`
- Test: `dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/activity/EvaluateSwitchActivityTest.java`

**Interfaces:**
- Produces: `WorkflowSupport.secrets(): Map<String, JsonNode>` initialized once before activities run.
- Consumes: environment names `SECRET_<logical-name>` projected in Task 3.

- [ ] **Step 1: Write failing set and switch tests with a `$secrets` binding**

```java
assertThat(EvaluateSetActivity.evaluate(setTask("value", "${ $secrets.apitoken }"), data, Map.of()))
    .containsEntry("value", mapper.getNodeFactory().textNode("token"));
```

Add a switch fixture whose condition is `${ $secrets.FLAG == "enabled" }` and a no-secret fixture
that preserves existing behavior.

- [ ] **Step 2: Run the focused orchestrator tests and verify failure**

Run: `./mvnw -Dtest=EvaluateSetActivityTest,EvaluateSwitchActivityTest test`

Expected: FAIL because activity scopes contain only request-local variables.

- [ ] **Step 3: Load and merge the immutable secret scope**

At bootstrap, enumerate declared secret names, read the already-projected `SECRET_<NAME>` process
environment, convert present values to `TextNode`, and initialize `WorkflowSupport`. Merge this map
with request-local scope such that task-local variables retain their existing semantics while the
reserved `secrets` jq variable always points to the immutable map.

- [ ] **Step 4: Add leak-warning documentation close to the binding**

Document in `WorkflowRuntimeBootstrap`/`WorkflowSupport` that set and switch exposure is DWS
specific and values must not be exported or logged by workflow authors.

- [ ] **Step 5: Verify and commit**

Run: `./mvnw verify`

Expected: PASS.

```bash
git add dws-orchestrator/src/main/java/io/dws/orchestrator/config/WorkflowRuntimeBootstrap.java dws-orchestrator/src/main/java/io/dws/orchestrator/workflow dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/activity
git commit -m "feat: expose workflow secrets to jq"
```

### Task 5: Add Go HTTP runner authentication and Dapr routing

**Files:**
- Modify: `dws-call-http/internal/config/config.go:45-90`
- Modify: `dws-call-http/internal/runner/runner.go:95-130`
- Test: `dws-call-http/internal/config/config_test.go`
- Test: `dws-call-http/internal/runner/runner_test.go`

**Interfaces:**
- Consumes: generated `AUTH_SCHEME`, secret-backed `AUTH_USERNAME`, `AUTH_PASSWORD`, `AUTH_TOKEN`,
  and OAuth endpoint/sidecar configuration.
- Produces: an outbound request with one Basic/Bearer header or a Dapr endpoint invocation URL.

- [ ] **Step 1: Add failing config and request tests**

```go
want := "Basic " + base64.StdEncoding.EncodeToString([]byte("alice:pw"))
if got := req.Header.Get("Authorization"); got != want { t.Fatalf("got %q", got) }
```

Add bearer, OAuth sidecar URL, missing required auth field, and no-auth cases.

- [ ] **Step 2: Run the focused Go tests and verify failure**

Run: `go test ./internal/config ./internal/runner`

Expected: FAIL because `Config` has no authentication fields and `buildRequest` does not route or
set headers.

- [ ] **Step 3: Parse the normalized contract and apply it in buildRequest**

Add an `Auth` configuration value with `none`, `basic`, `bearer`, and `oauth2` cases. For OAuth2,
replace the direct base URL with `http://localhost:${DAPR_HTTP_PORT}/v1.0/invoke/<endpoint>/method/<path-and-query>`;
for basic/bearer, use `req.Header.Set("Authorization", value)` after existing configured headers.

- [ ] **Step 4: Preserve existing request features and test them together**

Run tests proving endpoint interpolation, headers, query, body, output mode, timeout, and TLS
configuration retain their current behavior when `AUTH_SCHEME` is absent.

- [ ] **Step 5: Vet, test, and commit**

Run: `go vet ./...` and `go test ./...`

Expected: PASS.

```bash
git add dws-call-http/internal/config dws-call-http/internal/runner
git commit -m "feat: authenticate http workflow calls"
```

### Task 6: Add TypeScript OpenAPI runner authentication and Dapr routing

**Files:**
- Modify: `dws-call-openapi/src/config/config.ts:23-210`
- Modify: `dws-call-openapi/src/auth.ts`
- Modify: `dws-call-openapi/src/request.ts:22-61`
- Test: `dws-call-openapi/test/config.test.ts`
- Test: `dws-call-openapi/test/auth.test.ts`
- Test: `dws-call-openapi/test/request.test.ts`

**Interfaces:**
- Consumes: the same controller-generated auth environment contract as Task 5.
- Produces: `OutboundRequest` after swagger-client serialization, with auth layered after its
path/query/header construction.

- [ ] **Step 1: Add failing runner-contract tests**

```ts
expect(req.headers.Authorization).toBe(`Bearer ${token}`);
expect(req.url).toBe('http://localhost:3500/v1.0/invoke/accounts-oauth/method/v1/orders?limit=1');
```

Cover Basic encoding and explicit assertions that the existing `apiKey` and Dapr secret-store
configuration cases still parse and build as before.

- [ ] **Step 2: Run focused tests and verify failure**

Run: `pnpm test -- --runInBand test/config.test.ts test/auth.test.ts test/request.test.ts`

Expected: FAIL because generated auth fields and OAuth endpoint routing are not recognized.

- [ ] **Step 3: Add generated-auth parsing without deleting legacy paths**

Add normalized `basic`, `bearer`, and `oauth2` variants beside the existing standalone `none`,
`apiKey`, and store-backed cases. Reject incomplete generated values with `ConfigError`; do not
allow a generated controller workflow to fall back to inline secret material.

- [ ] **Step 4: Apply auth after swagger-client request construction**

Keep `SwaggerClient.buildRequest` responsible for OpenAPI serialization. Extend `applyAuth` so
basic/bearer merges one Authorization header and OAuth rewrites only the base routing target to the
local Dapr invocation URL while preserving the operation's path and query.

- [ ] **Step 5: Lint, test, build, and commit**

Run: `pnpm lint`, `pnpm test`, and `pnpm build`

Expected: PASS.

```bash
git add dws-call-openapi/src dws-call-openapi/test
git commit -m "feat: authenticate openapi workflow calls"
```

### Task 7: Validate the Dapr OAuth path and document rollout behavior

**Files:**
- Create: `dws-controller/src/test/resources/oauth/mock-idp/` test resources or the repository's
  established integration-test location after inspecting existing Dapr test harnesses
- Modify: `charts/dws/Chart.yaml` and `Chart.lock` to use stable Dapr `1.18.1`, plus component CI/integration configuration for a `DAPR_VERSION` parameter defaulting to `1.18.1`
- Modify: `docs/roadmaps/openworkflow-features.md`

**Interfaces:**
- Consumes: generated endpoint/middleware/configuration resources and both runner invocation shapes.
- Produces: an integration assertion that a token is added only on the configured path filter.

- [ ] **Step 1: Write the failing mock-IdP scenario**

The fixture must expose a token endpoint and two protected paths. Assert the intended path receives
`Authorization: Bearer <issued-token>` while an unrelated path receives no injected OAuth token.

- [ ] **Step 2: Run the integration target at the default version and capture the initial failure**

Run: the repository-specific integration command with `DAPR_VERSION=1.18.1`.

Expected: FAIL before generated OAuth resources and runner routing are available.

- [ ] **Step 3: Wire the version parameter and resources into the harness**

Default the parameter to `1.18.1`, permit an explicit newer override, and ensure the chart lock
matches the upgraded dependency. Deploy the version-scoped resource set generated by the controller.

- [ ] **Step 4: Record startup and rollback prerequisites**

Document that operators create each scalar Secret with the `value` data key before deploying a
definition; a missing reference blocks workload startup. Document deletion of the version-scoped
workflow resources as rollback.

- [ ] **Step 5: Run the full validation matrix and commit**

Run: `./mvnw test` in `dws-controller`; `./mvnw verify` in `dws-orchestrator`; `go vet ./...` and
`go test ./...` in `dws-call-http`; `pnpm lint && pnpm test && pnpm build` in `dws-call-openapi`;
then the mock-IdP integration command at `DAPR_VERSION=1.18.1`.

Expected: all commands PASS.

```bash
git add dws-controller docs/roadmaps/openworkflow-features.md
git commit -m "test: verify oauth endpoint isolation"
```

## Plan Self-Review

- **Spec coverage:** Tasks 1–4 cover all `workflow-secrets` requirements; Tasks 2–3 and 5–7 cover
  all `workflow-authentication` requirements, including compatibility and the Dapr filter test.
- **Placeholder scan:** No unresolved implementation placeholders remain; Task 7 requires selecting
  the repository's existing integration harness location rather than introducing a duplicate one.
- **Type consistency:** All generated runner contracts use `AUTH_SCHEME` plus typed secret-backed
  environment entries, with `EnvValue.SecretKeyRef` as the sole controller-to-synthesizer secret
  representation.
