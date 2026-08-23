## ADDED Requirements

### Requirement: HTTP and OpenAPI endpoints resolve supported authentication policies

The controller SHALL resolve an endpoint authentication policy that is inline or references a name
under `use.authentications`. It SHALL support `basic`, `bearer`, and OAuth2 `client_credentials`
only. Every referenced scalar secret SHALL be declared in `use.secrets`, and controller-managed
credential fields SHALL reference declared secrets rather than contain literals.

#### Scenario: Named policy resolves for an HTTP endpoint
- **WHEN** an HTTP endpoint declares `authentication.use` for a named bearer policy
- **THEN** the compiled step service receives the normalized bearer scheme and the policy's typed
  secret reference

#### Scenario: Inline policy resolves for an OpenAPI operation
- **WHEN** an OpenAPI call declares an inline basic policy using two declared secrets
- **THEN** the compiled step service receives the normalized basic scheme and both typed secret
  references

#### Scenario: Invalid policy is rejected
- **WHEN** a policy references an undeclared secret, a missing named policy, a literal credential,
  or an OAuth grant other than `client_credentials`
- **THEN** the controller rejects the workflow before deployment

### Requirement: Basic and bearer runners attach Authorization headers

`dws-call-http` and `dws-call-openapi` SHALL consume the normalized generated authentication
environment contract and attach `Authorization: Basic <base64(username:password)>` or
`Authorization: Bearer <token>` before sending an outbound request.

#### Scenario: Basic call attaches encoded credentials
- **WHEN** a generated step service has the basic scheme and injected username and password values
- **THEN** the runner sends exactly one Basic Authorization header with the base64-encoded pair

#### Scenario: Bearer call attaches token
- **WHEN** a generated step service has the bearer scheme and injected token value
- **THEN** the runner sends exactly one Bearer Authorization header with that token

### Requirement: OAuth2 calls use Dapr external endpoint invocation

For OAuth2 `client_credentials`, the controller SHALL synthesize workflow-version-scoped Dapr
`HTTPEndpoint`, `middleware.http.oauth2clientcredentials` Component, and Configuration resources
for each canonical external-host plus policy combination. Resources SHALL be scoped to their step
app IDs, use secret references for client credentials, and apply a narrow path filter. The runner
SHALL invoke the configured endpoint through its local Dapr sidecar and SHALL NOT fetch or cache
OAuth tokens itself.

#### Scenario: Equivalent OAuth policies share generated resources
- **WHEN** two calls in the same workflow version use the same external host and canonical OAuth2
  policy
- **THEN** synthesis produces one corresponding endpoint, middleware Component, and Configuration
  resource set with scopes covering both requesting step app IDs

#### Scenario: OAuth runner uses sidecar invocation
- **WHEN** an OAuth2-configured runner executes a call
- **THEN** it sends the request through the local Dapr external-endpoint invocation path instead of
  directly to the raw endpoint URL

#### Scenario: OAuth path filtering is verified at the pinned version
- **WHEN** the mock-IdP integration suite runs with its default Dapr version
- **THEN** it runs against 1.18.1 and proves that the token is attached only to the intended
  external invocation path

### Requirement: Existing runner interfaces remain compatible

The new controller-generated authentication environment contract SHALL NOT remove standalone
`dws-call-openapi` API-key or secret-store behavior, and definitions with no authentication SHALL
preserve existing HTTP and OpenAPI request behavior.

#### Scenario: Existing standalone OpenAPI API-key configuration runs
- **WHEN** `dws-call-openapi` is configured through its existing API-key environment variables
- **THEN** it retains its prior request-building behavior

#### Scenario: Unauthenticated generated call runs unchanged
- **WHEN** a controller-generated HTTP or OpenAPI call has no authentication policy
- **THEN** the runner sends the request without Phase-4 authentication headers or sidecar OAuth
  routing
