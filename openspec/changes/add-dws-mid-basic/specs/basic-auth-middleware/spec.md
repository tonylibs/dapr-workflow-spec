# Spec Delta

## Purpose

Defines how the `dws-mid-basic` Wasm guest, loaded by a step's Dapr sidecar as an HTTP middleware,
adds an HTTP Basic credential to that step's calls to one endpoint, and how its binary is shipped.

## ADDED Requirements

### Requirement: Guest config carries one endpoint and one Basic credential
`dws-mid-basic` SHALL read its guest config as a JSON object with exactly the string fields
`endpoint`, `username` and `password`. The config SHALL NOT carry a scheme: the component always
produces `Basic`. Any other field, including `scheme` or `token`, SHALL make the config invalid.

#### Scenario: Valid config loads
- **WHEN** daprd loads the guest with `{"endpoint":"pets","username":"alice","password":"s3cret"}`
- **THEN** the middleware loads and serves requests

#### Scenario: Scheme field is rejected
- **WHEN** the guest config contains a `scheme` field, with any value
- **THEN** the guest refuses to start and reports that `scheme` is not a known field

#### Scenario: Bearer-shaped config is rejected
- **WHEN** the guest config is `{"endpoint":"pets","token":"abc"}`
- **THEN** the guest refuses to start and reports the unknown `token` field and the missing `username` and `password`

### Requirement: Invalid credentials fail at load, never at request time
`dws-mid-basic` SHALL refuse to start when `username` or `password` is absent, null or empty, when
`username` contains `:`, when either contains a control character, when `endpoint` is empty or
contains `/`, `?` or `#`, or when the config is not a JSON object. It SHALL NOT forward any request
without the credential it was configured to add.

#### Scenario: Missing password
- **WHEN** the guest config omits `password`
- **THEN** the guest refuses to start with an error naming `password` as required

#### Scenario: Empty username
- **WHEN** the guest config has `"username":""`
- **THEN** the guest refuses to start with an error naming `username` as required

#### Scenario: Colon in username
- **WHEN** the guest config has `"username":"ali:ce"`
- **THEN** the guest refuses to start with an error stating that `username` must not contain `:` (RFC 7617)

#### Scenario: Control character in password
- **WHEN** the guest config has a password containing a newline
- **THEN** the guest refuses to start with an error stating that `password` must not contain control characters

#### Scenario: Malformed JSON
- **WHEN** the guest config is not valid JSON
- **THEN** the guest refuses to start with an error saying the config is not valid JSON, without quoting any part of it

#### Scenario: No unauthenticated call after a load failure
- **WHEN** the guest refused to start and the step calls its endpoint through the sidecar
- **THEN** the target receives no request from that call

### Requirement: Only the endpoint's invocation path is matched
`dws-mid-basic` SHALL treat a request as matching when its path, ignoring any query string, equals
`/v1.0/invoke/<endpoint>/method` or starts with `/v1.0/invoke/<endpoint>/method/`, where
`<endpoint>` is the configured name compared exactly. A path holding a `.` or `..` segment, raw or
percent-encoded, SHALL NOT match.

#### Scenario: Method path matches
- **WHEN** a request targets `/v1.0/invoke/pets/method/v1/pets/42`
- **THEN** it matches

#### Scenario: Query string is ignored for matching
- **WHEN** a request targets `/v1.0/invoke/pets/method/search?q=a`
- **THEN** it matches

#### Scenario: Endpoint name prefix does not match
- **WHEN** the endpoint is `pets` and a request targets `/v1.0/invoke/pets-v2/method/x`
- **THEN** it does not match

#### Scenario: Another endpoint does not match
- **WHEN** the endpoint is `pets` and a request targets `/v1.0/invoke/orders/method/x`
- **THEN** it does not match

#### Scenario: Dot segment does not match
- **WHEN** a request targets `/v1.0/invoke/pets/method/../../orders/method/x` or the same path with `%2e%2e`
- **THEN** it does not match

#### Scenario: Non-invocation path does not match
- **WHEN** a request targets `/v1.0/state/store` or `/healthz`
- **THEN** it does not match

### Requirement: Matching requests carry exactly one Basic Authorization header
For a matching request, `dws-mid-basic` SHALL set the request header
`Authorization: Basic <base64(username ":" password)>` using standard padded base64, replacing any
`Authorization` value the caller sent, so the forwarded request carries exactly one such header.
It SHALL change nothing else on the request.

#### Scenario: Header appended to a request without one
- **WHEN** a matching request has no `Authorization` header and the credential is `alice` / `s3cret`
- **THEN** the forwarded request carries `Authorization: Basic YWxpY2U6czNjcmV0`

#### Scenario: Caller-supplied header is replaced
- **WHEN** a matching request already carries `Authorization: Bearer caller-supplied`
- **THEN** the forwarded request carries only `Authorization: Basic <configured credential>`

#### Scenario: Other parts of the request are untouched
- **WHEN** a matching request carries other headers, a query string and a body
- **THEN** the forwarded request carries the same method, URI, other headers and body

### Requirement: Non-matching requests pass through untouched
For a request that does not match, `dws-mid-basic` SHALL forward it without adding, removing or
changing any header, the URI or the body.

#### Scenario: Unrelated endpoint keeps its own header
- **WHEN** a request to another endpoint carries `Authorization: Bearer other`
- **THEN** it is forwarded with `Authorization: Bearer other` unchanged

#### Scenario: Unrelated request stays unauthenticated
- **WHEN** a request to another endpoint carries no `Authorization` header
- **THEN** it is forwarded with no `Authorization` header

### Requirement: Credentials are encoded as RFC 7617 describes
`dws-mid-basic` SHALL build the user-pass as `username ":" password`, normalize each part to Unicode
NFC, encode it as UTF-8, then base64 it. A `:` in the password SHALL be kept verbatim.

#### Scenario: Colon in password
- **WHEN** the credential is `alice` / `pa:ss:word`
- **THEN** the header value is `Basic` followed by base64 of the bytes `alice:pa:ss:word`

#### Scenario: Non-ASCII credential
- **WHEN** the credential is `test` / `123£`
- **THEN** the header value is `Basic dGVzdDoxMjPCow==` (RFC 7617 §2.1 example)

#### Scenario: Non-NFC input is normalized
- **WHEN** the username is `e` followed by U+0301 (combining acute accent)
- **THEN** the encoded bytes contain the single code point U+00E9 encoded as UTF-8

### Requirement: Credentials never appear in logs or error text
`dws-mid-basic` SHALL NOT write the username, the password, the composed user-pass or the header
value to any log line, panic message or error text, including on config errors.

#### Scenario: Load error omits values
- **WHEN** the guest refuses to start because the config is invalid
- **THEN** the error text names fields and rules only, and contains no part of any configured value

#### Scenario: Normal operation logs nothing secret
- **WHEN** the guest handles matching and non-matching requests at any daprd log level
- **THEN** no daprd log line contains the username, the password or the encoded credential

### Requirement: The binary ships in a minimal public image
`dws-mid-basic` SHALL be published as the public image `ghcr.io/tonylibs/dws-mid-basic`, holding
the guest at `/basic.wasm` on a `busybox` base so an init container can copy it with `cp`. The
image SHALL contain no other application files and no credential.

#### Scenario: Init container copies the binary
- **WHEN** an init container from the image runs `cp /basic.wasm /mnt/dws-wasm/basic.wasm`
- **THEN** the file is written and daprd loads it from `file:///mnt/dws-wasm/basic.wasm`

#### Scenario: Image is pullable without credentials
- **WHEN** a cluster with no image pull secret pulls `ghcr.io/tonylibs/dws-mid-basic` by digest
- **THEN** the pull succeeds
