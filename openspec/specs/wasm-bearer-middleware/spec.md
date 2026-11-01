# wasm-bearer-middleware

## Purpose

Defines the `dws-mid-bearer` Wasm HTTP middleware that adds a bearer credential to a step's outbound
service invocation on its Dapr sidecar, and the image that delivers the module. Owned by the
`dws-mid-bearer` component; the controller (a later phase) only wires it.

## Requirements

### Requirement: Guest config carries an endpoint and a token, nothing else
The `dws-mid-bearer` module SHALL read its configuration from the Dapr `guestConfig` as a JSON object
with exactly two string fields, `endpoint` (the Dapr `HTTPEndpoint` name it applies to) and `token`.
It MUST NOT define a `scheme` field; which component Dapr loads is the scheme.

#### Scenario: valid config loads
- **WHEN** `guestConfig` is `{"endpoint":"pets-api","token":"abc123"}`
- **THEN** the module loads and handles requests for endpoint `pets-api`

#### Scenario: scheme-style fields are rejected
- **WHEN** `guestConfig` also contains `scheme`, `username`, `password` or any other field
- **THEN** the module fails to load (see "Invalid config fails closed at load")

### Requirement: Own-endpoint invocations receive the bearer header
The module SHALL set the `Authorization` header to `Bearer <token>` for a request whose URI is on its
own endpoint's invocation path, `/v1.0/invoke/<endpoint>/method` followed by `/…`, `?…` or the end of
the URI, replacing every caller-supplied `Authorization` value so exactly one is forwarded.

#### Scenario: header added
- **WHEN** a request for `/v1.0/invoke/pets-api/method/v1/pets?limit=2` carries no `Authorization`
- **THEN** the forwarded request has `Authorization: Bearer abc123`

#### Scenario: caller-supplied value is replaced
- **WHEN** the same request arrives with `Authorization: Bearer caller-supplied`
- **THEN** the forwarded request has the single value `Bearer abc123`

#### Scenario: bare method path
- **WHEN** a request for `/v1.0/invoke/pets-api/method` or `/v1.0/invoke/pets-api/method/` arrives
- **THEN** the forwarded request has `Authorization: Bearer abc123`

### Requirement: Every other request passes through untouched
A request that is not on the module's own endpoint's invocation path MUST be passed on to the next
handler with no header added, changed or removed, including any `Authorization` it already carries.

#### Scenario: other endpoint
- **WHEN** a request for `/v1.0/invoke/other-api/method/v1/pets` arrives
- **THEN** no `Authorization` is added and the request is otherwise unchanged

#### Scenario: look-alike endpoint name
- **WHEN** a request for `/v1.0/invoke/pets-api-2/method/x` or `/v1.0/invoke/pets-apix/method/x` arrives
- **THEN** it is treated as another endpoint and passed through

#### Scenario: not an invocation path
- **WHEN** a request for `/v1.0/state/store`, `/v1.0/invoke/pets-api/methods/x` or `/` arrives
- **THEN** it is passed through with its headers unchanged

#### Scenario: foreign Authorization survives
- **WHEN** a request for another endpoint carries `Authorization: Basic eDp5`
- **THEN** that header reaches the next handler unchanged

### Requirement: Invalid config fails closed at load
The module SHALL abort during initialisation, so daprd fails to load the component, if `guestConfig` is
absent, not valid JSON, has an unknown field, has a missing, empty or whitespace-only `endpoint` or
`token`, an `endpoint` containing `/`, `?`, `#` or a control character, or a `token` containing a
control character (including a trailing newline). It MUST NOT fall back to passing requests without
credentials.

#### Scenario: missing token
- **WHEN** `guestConfig` is `{"endpoint":"pets-api"}`
- **THEN** the module aborts at load; its error text names `token` as missing or empty
- **AND** daprd refuses the Component and the sidecar exits

#### Scenario: empty token
- **WHEN** `guestConfig` is `{"endpoint":"pets-api","token":""}` or a whitespace-only token
- **THEN** the module aborts at load and no request is ever forwarded by it

#### Scenario: what the operator sees
- **WHEN** any of these configs is loaded by daprd 1.18.1
- **THEN** daprd logs the component failure and the Wasm trap and exits, and does not show the guest's field name

#### Scenario: token with a trailing newline
- **WHEN** the token value ends in `\n` (a Secret created with `echo` instead of `echo -n`)
- **THEN** the module aborts at load rather than sending a malformed header

#### Scenario: malformed config
- **WHEN** `guestConfig` is empty, not JSON, or a JSON value that is not an object
- **THEN** the module aborts at load

#### Scenario: unusable endpoint
- **WHEN** `endpoint` is missing, empty, or contains `/`
- **THEN** the module aborts at load, so a typo cannot silently match nothing

### Requirement: The token never appears in logs or error text
The module MUST NOT write the token, or any part of `guestConfig`, to any log, panic message or error
string, whether the config is valid or not. It SHALL NOT log per-request data. Failure messages SHALL name
the failing field only.

#### Scenario: failure message omits the value
- **WHEN** any invalid config from the previous requirement is loaded with a recognisable token in it
- **THEN** the abort message names the problem field and does not contain the token value

#### Scenario: normal operation is silent
- **WHEN** requests are handled, matching or not
- **THEN** the module emits no log line containing the token, the URI or any header value

### Requirement: The image delivers only the Wasm module
The component SHALL publish an image `ghcr.io/<owner>/dws-mid-bearer` on a `busybox` base containing the
module at `/bearer.wasm`, built from the pinned TinyGo 0.34.0 toolchain, so an init container can run
`cp /bearer.wasm <shared volume>/bearer.wasm`. It SHALL run as a non-root user, define no entrypoint
behavior beyond that, and be pullable without credentials.

#### Scenario: file present and valid
- **WHEN** the image is run with `head -c 4 /bearer.wasm`
- **THEN** the output is the Wasm magic bytes `\0asm`

#### Scenario: init container copy
- **WHEN** an init container from this image runs `cp /bearer.wasm /mnt/dws-wasm/bearer.wasm` against an
  empty `emptyDir` as a non-root user
- **THEN** `bearer.wasm` exists in the volume and is readable by the sidecar

### Requirement: CI builds the image; releases are manual
A change under `dws-mid-bearer/` SHALL trigger CI that runs vet, format check and tests, builds the image
on every pull request, and pushes `:latest` and `:<short-sha>` only on a merge to `main`. A numbered
`:<version>` tag SHALL exist only after a deliberate `component-release` of `dws-mid-bearer`; the
`release-please` package SHALL NOT create a GitHub release.

#### Scenario: pull request
- **WHEN** a pull request changes `dws-mid-bearer/**`
- **THEN** the image is built and smoke-checked but not pushed

#### Scenario: merge to main
- **WHEN** such a change merges to `main`
- **THEN** `:latest` and `:<short-sha>` are pushed and no `:<version>` tag is created
