# Task 3 Report: Secret and Dapr OAuth Resource Synthesis

## Status

Complete. The controller now renders typed secret-backed environment variables, synthesizes and
applies the Dapr OAuth resource set described by Task 2, and projects declared workflow secrets into
the orchestrator environment for the Phase 4 `$secrets` binding.

## Implementation

### Typed environment rendering

- Knative step-service environment entries retain literal values in `value` and render
  `EnvValue.SecretKeyRef` through `valueFrom.secretKeyRef`.
- Fabric8 orchestrator Deployment environment entries use the same distinction.
- Secret selectors preserve the compiler contract: the Kubernetes Secret is the logical secret
  name and its key is `value`.
- No generated environment variable contains a credential literal.

### Dapr OAuth resources

For every canonical `DeploymentPlan.oauthEndpoints()` descriptor, synthesis now produces:

- a `dapr.io/v1alpha1` `HTTPEndpoint` with the canonical base URL and the requesting step app IDs
  in `scopes`;
- a `dapr.io/v1alpha1` `Component` of type
  `middleware.http.oauth2clientcredentials`, also scoped to the requesting app IDs;
- a `dapr.io/v1alpha1` `Configuration` whose HTTP pipeline names that middleware handler.

All three resources use the descriptor's version-scoped name, workflow/version labels, and target
namespace. Requesting Knative services select the generated Configuration through
`dapr.io/config`.

The OAuth Component metadata contains:

- secret references for `clientId` and `clientSecret`, with no credential `value` fields;
- normalized comma-separated scopes;
- the canonical token URL;
- the authorization header name;
- the Dapr auth-style value corresponding to `client_secret_post` or `client_secret_basic`;
- an anchored `pathFilter` matching only the complete local Dapr external-invocation route for the
  descriptor name and its canonical paths.

`StackApplier` now applies and label-deletes `HTTPEndpoint` and `Configuration` resources in
addition to the existing Component, Knative Service, access-policy, and orchestrator resources.
The existing WorkflowAccessPolicy behavior is unchanged.

### Orchestrator integration correction

`WorkflowCompiler` now adds `SECRET_<logical-name>` entries to the orchestrator environment for
every declared `use.secrets` value. Each entry is an `EnvValue.SecretKeyRef(logicalName, "value")`.
Workflows declaring no secrets keep exactly the prior `DEFINITION_STORE` and `DEFINITION_KEY`
environment contract.

## Dapr attachment semantics

The generated shape is grounded in Dapr's Kubernetes resource behavior:

- A sidecar selects one named Configuration using the workload annotation `dapr.io/config`; a
  Configuration resource does not expose a resource-level `scopes` field. Therefore requesting
  step services opt into the version-scoped Configuration through that annotation.
- `HTTPEndpoint` and Component resources expose `scopes`, so both are restricted to the requesting
  Dapr app IDs.
- The Configuration's `spec.httpPipeline.handlers` entry references the generated middleware
  Component by name and type.
- The current oauth2 client-credentials middleware evaluates `pathFilter` against the incoming
  sidecar request path. Consequently the filter anchors the full
  `/v1.0/invoke/<endpoint>/method<canonical-path>` route rather than only the upstream URL path.
- The middleware parser consumes OAuth scopes as a comma-separated metadata string and uses Go's
  `oauth2.AuthStyle` numeric values (`1` for parameters, `2` for HTTP Basic).

Primary references:

- Dapr OAuth2 client-credentials middleware:
  https://docs.dapr.io/reference/components-reference/supported-middleware/middleware-oauth2clientcredentials/
- Dapr HTTPEndpoint schema:
  https://docs.dapr.io/reference/resource-specs/httpendpoints-schema/
- Dapr Configuration overview:
  https://docs.dapr.io/operations/configuration/configuration-overview/
- Middleware implementation:
  https://github.com/dapr/components-contrib/blob/main/middleware/http/oauth2clientcredentials/oauth2clientcredentials_middleware.go

## TDD evidence

### RED

- Secret rendering tests failed in both real render paths with
  `Secret environment values require secret-key rendering`.
- OAuth synthesis tests failed to compile because `oauthHttpEndpoints`,
  `oauthMiddlewareComponents`, and `oauthConfigurations` did not exist.
- Apply-boundary tests failed to compile because the HTTPEndpoint and Configuration dynamic
  resource contexts did not exist.
- The orchestrator secret-projection test failed because the compiled environment contained only
  `DEFINITION_STORE` and `DEFINITION_KEY`.

### GREEN

- `StackSynthesizerTest`: 22 tests passed.
- `StackApplierTest`: 11 tests passed.
- Full `dws-controller` suite:
  `./mvnw -Dexec.skip=true test` -> 89 tests run, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.
- `git diff --check` passed.

## Test coverage added

- Knative and Fabric8 literal/secret environment shapes.
- Declared-secret orchestrator projection and no-secret compatibility.
- Same-origin/same-policy canonical resource sharing with combined app scopes.
- Different-policy resource separation and version-scoped identities.
- Workflow/version labels, canonical base URL, handler attachment, normalized scopes, auth style,
  and narrow path filter.
- Secret-reference-only Component metadata and absence of representative plaintext credentials
  from serialized manifests.
- Apply-boundary creation of the new Dapr resource kinds.

## Files changed

- `dws-controller/src/main/java/io/dws/controller/compile/WorkflowCompiler.java`
- `dws-controller/src/main/java/io/dws/controller/k8s/ResourceContexts.java`
- `dws-controller/src/main/java/io/dws/controller/k8s/StackApplier.java`
- `dws-controller/src/main/java/io/dws/controller/k8s/StackSynthesizer.java`
- `dws-controller/src/test/java/io/dws/controller/k8s/StackApplierTest.java`
- `dws-controller/src/test/java/io/dws/controller/k8s/StackSynthesizerTest.java`
- `dws-controller/src/test/resources/fixtures/oauth.yaml`

## Concerns for integration verification

- The repository currently pins Dapr 1.15.4. The 1.15 middleware implementation does not expose
  the current `pathFilter` option, while the current Dapr documentation and implementation do.
  Task 6's pinned-version mock-IdP integration must verify the intended isolation and decide whether
  the deployment requires a Dapr upgrade or another compatibility adjustment.
- The current Dapr middleware implementation rejects an empty OAuth scopes value, while the DSL
  compiler currently accepts an empty scope list. Pinned-runtime integration should settle whether
  the compiler must reject empty scopes or synthesize an accepted default.

Commit message: `feat: synthesize workflow secret and oauth resources`.
