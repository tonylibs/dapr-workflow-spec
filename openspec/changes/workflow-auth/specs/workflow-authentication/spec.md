## Purpose

Lets a workflow author declare `basic` / `bearer` / `oauth2` (grant `client_credentials`) on a
`call: http` or `call: openapi` endpoint — inline on the endpoint or by naming a policy defined
under `document.use.authentications` — and lets `dws-controller`, the two step images, and Dapr
cooperate so the outbound request carries the credential without the definition, the compiled
`StepService`, or the controller ever handling a secret **value**.

## ADDED Requirements

### Requirement: Endpoint authentication accepts inline or named form
`dws-controller` SHALL accept an endpoint's `authentication` in either an inline form —
`authentication: { basic | bearer | oauth2: {...} }` — or a named form —
`authentication: { use: <name> }` that resolves against `document.use.authentications.<name>` — for
`call: http` and `call: openapi` tasks. A named reference to an undefined policy MUST fail
compilation with a diagnostic naming both the endpoint and the missing policy. Owning component:
`dws-controller`.

#### Scenario: Inline authentication compiles onto the step
- **WHEN** a `call: http` endpoint declares `authentication: { bearer: { token: { use.secret: T } } }` inline
- **THEN** the compiled `StepService` for that task carries the authentication as env

#### Scenario: Named authentication resolves against document.use.authentications
- **WHEN** an endpoint declares `authentication: { use: my-policy }` and
  `document.use.authentications.my-policy` defines a `bearer` scheme
- **THEN** the compiled `StepService` for that task carries the resolved `bearer` scheme as env,
  identical in shape to the inline form

#### Scenario: Named reference to a missing policy fails compilation
- **WHEN** an endpoint declares `authentication: { use: missing }` and no
  `document.use.authentications.missing` exists
- **THEN** compilation fails with an error naming the endpoint and `missing`

#### Scenario: No authentication declared is unaffected
- **WHEN** an endpoint declares no `authentication`
- **THEN** the compiled `StepService` carries no `AUTH_*` env and the outbound request goes out
  unauthenticated exactly as today

### Requirement: basic and bearer compile to header-driving env on the step
For `basic` and `bearer` schemes, `dws-controller` SHALL compile onto the `StepService` an
`AUTH_SCHEME` env (`basic` or `bearer`) plus the required credential env entries:
`AUTH_USERNAME` and `AUTH_PASSWORD` for `basic`, `AUTH_TOKEN` for `bearer`. Credential entries
SHALL be produced as `secretKeyRef`-backed `EnvVar`s naming the K8s `Secret` referenced by the
DSL's `{ use.secret: NAME }` form; no plaintext credential value SHALL appear on the compiled
`StepService`. Owning components: `dws-controller`, `dws-call-http`, `dws-call-openapi`.

#### Scenario: Bearer compiles to AUTH_SCHEME and secret-backed AUTH_TOKEN
- **WHEN** an endpoint declares `bearer: { token: { use.secret: T } }`
- **THEN** the compiled `StepService` has `AUTH_SCHEME=bearer`
- **AND** `AUTH_TOKEN` is mounted via `secretKeyRef` naming the K8s `Secret` for `T`
- **AND** no plaintext token value appears anywhere in the compile output

#### Scenario: Basic compiles to AUTH_SCHEME and secret-backed AUTH_USERNAME plus AUTH_PASSWORD
- **WHEN** an endpoint declares `basic: { username: { use.secret: U }, password: { use.secret: P } }`
- **THEN** the compiled `StepService` has `AUTH_SCHEME=basic`
- **AND** both `AUTH_USERNAME` and `AUTH_PASSWORD` are mounted via `secretKeyRef`

### Requirement: basic and bearer step images set the Authorization header
`dws-call-http` and `dws-call-openapi` SHALL read `AUTH_SCHEME` at request time and, for
`basic` or `bearer`, attach the corresponding `Authorization` header to the outbound request:
`Basic <base64(username:password)>` for `basic`, `Bearer <token>` for `bearer`. When
`AUTH_SCHEME` is unset, no `Authorization` header SHALL be added by the step image. Owning
components: `dws-call-http`, `dws-call-openapi`.

#### Scenario: Bearer header is attached
- **WHEN** the step runs with `AUTH_SCHEME=bearer` and `AUTH_TOKEN=abc`
- **THEN** the outbound request carries `Authorization: Bearer abc`

#### Scenario: Basic header is base64-encoded
- **WHEN** the step runs with `AUTH_SCHEME=basic`, `AUTH_USERNAME=alice`, `AUTH_PASSWORD=s3cret`
- **THEN** the outbound request carries `Authorization: Basic <base64('alice:s3cret')>`

#### Scenario: No AUTH_SCHEME means no header attached
- **WHEN** the step runs with `AUTH_SCHEME` unset
- **THEN** the outbound request has no `Authorization` header added by the step image

### Requirement: oauth2 compiles to a Dapr HTTPEndpoint plus middleware Component plus Configuration
For an `oauth2` scheme, `dws-controller` SHALL synthesise, per unique (external-host, IdP,
`client_id` secret-ref, `client_secret` secret-ref, sorted-scopes) tuple, one Dapr `HTTPEndpoint`
naming the external host, one `Component` of type `middleware.http.oauth2clientcredentials`
whose `clientId` / `clientSecret` metadata pull from their K8s `Secret` keys via `secretKeyRef`,
and one `Configuration` per step attaching the middleware to the step sidecar's
`appHttpPipeline` with `pathFilter` narrowed to the specific `HTTPEndpoint`'s invoke path only.
All three resources MUST carry the same `dws.io/*` labels as the rest of the workflow's stack so
they are garbage-collected with the version. Two `oauth2` policies with an identical dedup tuple
SHALL share the same `HTTPEndpoint` and `Component` rather than being deployed twice. Owning
component: `dws-controller`.

#### Scenario: oauth2 endpoint deploys the three Dapr resources
- **WHEN** a `call: http` endpoint declares `oauth2` for host `api.example.com`
- **THEN** the stack includes one `HTTPEndpoint` for that host, one
  `middleware.http.oauth2clientcredentials` `Component`, and a `Configuration` attaching it via
  `appHttpPipeline`

#### Scenario: pathFilter is scoped to the HTTPEndpoint invoke prefix
- **WHEN** the `Configuration` is synthesised for the oauth2 step
- **THEN** its `appHttpPipeline` `pathFilter` matches only
  `/v1.0/invoke/<httpendpoint-name>/method/*` and no wider path

#### Scenario: Dedup collapses identical oauth2 policies
- **WHEN** two endpoints on the same host reference the same `use.authentications.<name>` oauth2
  policy
- **THEN** exactly one `HTTPEndpoint` and one middleware `Component` are synthesised, shared by
  both

#### Scenario: Distinct hosts get distinct HTTPEndpoints
- **WHEN** two endpoints reference the same oauth2 policy but target different external hosts
- **THEN** each host gets its own `HTTPEndpoint`, and the middleware `Component` is deployed
  separately per host

#### Scenario: Resources are labelled and garbage-collected with the version
- **WHEN** a workflow version is drained
- **THEN** its `HTTPEndpoint`, oauth2 `Component`, and `Configuration` resources are removed by
  the same `dws.io/*` label selector as the rest of the stack

### Requirement: oauth2 step images route the call through the sidecar
When `AUTH_SCHEME=oauth2` and `AUTH_HTTPENDPOINT_NAME=<name>` are set, `dws-call-http` and
`dws-call-openapi` SHALL issue the outbound request to
`http://localhost:${DAPR_HTTP_PORT}/v1.0/invoke/${AUTH_HTTPENDPOINT_NAME}/method/<path+query>`
rather than to the endpoint's raw URL, and SHALL NOT attach an `Authorization` header of their
own. The Dapr sidecar's `oauth2clientcredentials` middleware is responsible for fetching,
caching, and injecting the bearer token onto the request before it leaves the pod. Owning
components: `dws-call-http`, `dws-call-openapi`.

#### Scenario: oauth2 call goes to the sidecar invoke URL
- **WHEN** the step runs with `AUTH_SCHEME=oauth2`, `AUTH_HTTPENDPOINT_NAME=ep-api-abc12345`,
  and the endpoint URL is `https://api.example.com/v1/things?x=1`
- **THEN** the outbound request goes to
  `http://localhost:${DAPR_HTTP_PORT}/v1.0/invoke/ep-api-abc12345/method/v1/things?x=1`
- **AND** the step image itself adds no `Authorization` header

#### Scenario: oauth2 step image does not read AUTH_TOKEN
- **WHEN** the step runs with `AUTH_SCHEME=oauth2`
- **THEN** no `AUTH_TOKEN` env is required by the step image and none is set by the controller
  for `oauth2` scheme
