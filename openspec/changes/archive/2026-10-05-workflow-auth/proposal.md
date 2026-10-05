## Why

DWS workflows cannot safely call protected HTTP or OpenAPI services because the platform has no
standard way to declare credentials, project them from Kubernetes Secrets, or delegate OAuth2
client-credentials tokens. Phase 3 is complete, so the roadmap must be corrected and Phase 4 can
make protected service calls a first-class, version-scoped platform capability.

## What Changes

- Correct the Open Workflow roadmap to mark Phase 3 (RFC 7807 errors and timeouts) complete.
- Add scalar workflow secret declarations and secret-reference projection to the orchestrator and
  compiled step services; plaintext values never appear in compiled plans, ConfigMaps, or Dapr
  resource literals.
- Add inline and named `basic`, `bearer`, and OAuth2 `client_credentials` policies for HTTP and
  OpenAPI endpoints.
- Synthesize scoped Dapr `HTTPEndpoint`, OAuth2 middleware Component, and Configuration resources
  for OAuth2 calls; runners invoke those endpoints through their local sidecar.
- Expose declared secrets to jq as `$secrets`, including the approved DWS-specific `set` and
  `switch` extension, with an explicit leakage warning.
- Preserve existing standalone OpenAPI runner API-key and secret-store support; controller-managed
  workflows use the new secret-reference contract.

## Capabilities

### New Capabilities

- `workflow-secrets`: Declare scalar secrets, project them from Kubernetes, and expose them to the
  orchestrator runtime without storing plaintext in workflow artifacts.
- `workflow-authentication`: Resolve HTTP/OpenAPI authentication policies and execute basic,
  bearer, and Dapr-native OAuth2 client-credentials calls.

### Modified Capabilities

- None.

## Impact

Affected components are `dws-controller` (DSL resolution, typed deployment environment and Dapr
resource synthesis), `dws-orchestrator` (bootstrap secret variables), `dws-call-http` and
`dws-call-openapi` (auth execution). Kubernetes deployments gain `secretKeyRef` environment
entries and OAuth2 calls gain version-scoped Dapr resources. The Dapr chart upgrades to stable
1.18.1 for OAuth path filtering; the integration suite remains parameterized for later upgrades.
Existing definitions without secrets or authentication compile and run unchanged.
