## 1. Roadmap and controller secret-reference model

- [x] 1.1 Mark Phase 3 complete and Phase 4 current in `docs/roadmaps/openworkflow-features.md`, including the Phase 4 secret/auth scope and DWS `$secrets` extension note.
- [x] 1.2 Replace the literal-only environment representation in `dws-controller` `StepService`/deployment model with typed literal and Kubernetes `secretKeyRef` values; add focused model tests.
- [x] 1.3 Extend `StackSynthesizer` to render typed secret environment values for orchestrator and step services without widening controller Secret RBAC; add Kubernetes object-shape tests.
- [x] 1.4 Run `./mvnw test` from `dws-controller` for the secret-reference model and synthesizer tests.

## 2. Controller DSL authentication compilation

- [x] 2.1 Add validation and resolution for unique `use.secrets` scalar declarations using the `name`/`value` Kubernetes convention.
- [x] 2.2 Extend `WorkflowCompiler` to resolve inline and named basic, bearer, and OAuth2 `client_credentials` policies for HTTP and OpenAPI endpoints, rejecting literals, undeclared names, unknown policies, and unsupported grants.
- [x] 2.3 Compile normalized runner auth environment contracts and only the secret references required by each step; prove no credential plaintext is present in `StepService` or the definition ConfigMap.
- [x] 2.4 Synthesize canonical, version-scoped Dapr `HTTPEndpoint`, OAuth2 middleware Component, and scoped Configuration resources with secret-backed client credentials and narrow path filters.
- [x] 2.5 Add compiler and synthesis tests for inline/named policies, resource sharing by host plus policy, step-app scopes, and unauthenticated compatibility; run `./mvnw test` from `dws-controller`.

## 3. Orchestrator secret evaluation

- [x] 3.1 Load `SECRET_<NAME>` values once during `WorkflowRuntimeBootstrap` initialization and expose them as jq `$secrets` variables through `WorkflowSupport`.
- [x] 3.2 Thread secret variables through `set` and `switch` evaluation paths and document this behavior as the approved DWS extension with leakage warnings.
- [x] 3.3 Add tests for `$secrets.NAME` in `set` and `switch` plus definitions with no secrets; run `./mvnw verify` from `dws-orchestrator`.

## 4. HTTP runner authentication

- [x] 4.1 Extend `dws-call-http` configuration with the normalized generated auth contract for none, basic, bearer, and OAuth2 sidecar endpoint routing while retaining existing no-auth behavior.
- [x] 4.2 Update `dws-call-http` request construction to attach basic/bearer headers or invoke the configured OAuth Dapr endpoint without token handling in the runner.
- [x] 4.3 Add configuration and request tests for basic, bearer, OAuth sidecar URLs, and unauthenticated compatibility; run `go vet ./...` and `go test ./...` from `dws-call-http`.

## 5. OpenAPI runner authentication

- [x] 5.1 Add the normalized generated auth contract to `dws-call-openapi` configuration without removing standalone API-key or secret-store support.
- [x] 5.2 Apply basic/bearer auth after `swagger-client` builds the request and route OAuth2 calls through the local Dapr endpoint while preserving OpenAPI path/query/header serialization.
- [x] 5.3 Add unit tests for basic, bearer, OAuth routing, API-key compatibility, and unauthenticated calls; run `pnpm lint`, `pnpm test`, and `pnpm build` from `dws-call-openapi`.

## 6. Dapr integration and release validation

- [x] 6.1 Upgrade `charts/dws` to stable Dapr 1.18.1 and add a mock-IdP integration suite parameterized by Dapr version, defaulting to that runtime.
- [ ] 6.2 Verify OAuth middleware injects tokens only on the intended filtered external endpoint path and does not affect unrelated sidecar traffic. (Probe added; live cluster execution is environment-blocked.)
- [ ] 6.3 Run all component-specific validation commands and record the default-version integration result and any deployment prerequisites in the change verification artifact. (Go and live Dapr validation remain environment-blocked.)
