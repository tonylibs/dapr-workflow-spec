# jq-expression-function Specification

## Purpose
TBD - created by archiving change dws-jq-function. Update Purpose after archive.

## Requirements

### Requirement: Deploy-time configuration contract
`dws-jq` SHALL read its configuration only from these environment variables:
- `JQ_EXPRESSION`: required.
- `JQ_VARIABLES`: a JSON array of variable names. Default `[]`.
- `JQ_TIMEOUT`: a Go duration greater than zero. Default `5s`.
- `JQ_SECRET_STORE`: required when the expression references `$secrets`.
- `JQ_SITE`: default `dws-jq`.
- `PORT`: default `8080`.
- `DAPR_HTTP_PORT`: default `3500`.
- `DAPR_API_TOKEN`: optional.
- `DWS_LOG_FORMAT`: optional.

Each variable name MUST match `^[A-Za-z_][A-Za-z0-9_]*$`, MUST be unique, and MUST NOT be `secrets`,
`ENV` or `__loc__`.

The names SHALL be pinned by fixtures under `dws-jq/testdata/contract/`. The package tests SHALL fail
in either of these cases:
- the loader reads a name that no fixture contains;
- a fixture contains a name the loader does not read.

Owning component: `dws-jq`.

#### Scenario: Minimal configuration loads with defaults
- **WHEN** only `JQ_EXPRESSION='.'` is set
- **THEN** the configuration has no variables, a 5 s time limit, site `dws-jq`, port `8080` and no secret store

#### Scenario: Invalid variable list fails startup
- **WHEN** `JQ_VARIABLES` is `["item","item"]`, `["secrets"]`, `["1x"]` or not a JSON array of strings
- **THEN** the process logs one structured error naming `JQ_VARIABLES` and the reason, and exits non-zero without serving

#### Scenario: Non-positive time limit fails startup
- **WHEN** `JQ_TIMEOUT` is `0s`, `-1s` or `soon`
- **THEN** the process logs one structured error naming `JQ_TIMEOUT` and exits non-zero

#### Scenario: Contract fixtures and loader agree
- **WHEN** the package tests run
- **THEN** every fixture in `testdata/contract/` loads successfully
- **AND** the set of names the loader reads equals the union of fixture keys

### Requirement: Expression is unwrapped and compiled once at startup
`dws-jq` SHALL unwrap `JQ_EXPRESSION` the way v1 does:
- trim the text;
- if it starts with `${` and ends with `}`, take the text between them and trim it;
- otherwise use the text as bare jq.

`dws-jq` SHALL compile the result once at startup. The compile SHALL declare the configured variables
and `$secrets`, and SHALL install no environment loader, input iterator or module loader.

If any of the following happens, the process MUST write exactly one structured error record naming
the site, the expression text and the reason, and MUST exit non-zero without becoming ready:
- the expression is malformed;
- it references an undeclared variable;
- it fails any startup check.

Owning component: `dws-jq`.

#### Scenario: Wrapped and bare forms are equivalent
- **WHEN** `JQ_EXPRESSION` is `${ .a }` in one deployment and `.a` in another
- **THEN** both start, and `POST /run` with input `{"a": 1}` returns result `1` from each

#### Scenario: Malformed expression never becomes ready
- **WHEN** `JQ_EXPRESSION` is `.a |`
- **THEN** the process writes one error record containing the expression and the parse reason
- **AND** it exits non-zero
- **AND** `GET /healthz` never answers `200`

#### Scenario: Undeclared variable fails startup
- **WHEN** `JQ_EXPRESSION` is `$item < 3` and `JQ_VARIABLES` does not contain `item`
- **THEN** startup fails with a record whose reason names `$item`

### Requirement: Blocked builtins are rejected by inspecting the parsed expression
At startup, `dws-jq` SHALL walk the parsed expression, including nested definitions, bindings,
reductions, `try` bodies, object values and string interpolations. It MUST reject:
- any call to `env`, `input`, `inputs`, `input_filename`, `halt`, `halt_error`, `debug` or `stderr`,
  at any arity;
- any reference to `$ENV`;
- any `import` or `include` directive.

The reason MUST name the builtin. Field access, object keys and string literals with these names
MUST NOT be rejected. `now` and the date and local-time functions MUST stay allowed.

Owning component: `dws-jq`.

#### Scenario: Blocked builtin is named
- **WHEN** `JQ_EXPRESSION` is `${ { home: env.HOME } }`
- **THEN** startup fails with a reason containing `env`

#### Scenario: Nested use is found
- **WHEN** `JQ_EXPRESSION` is `def f: . | debug; [.[] | f]`
- **THEN** startup fails with a reason containing `debug`

#### Scenario: Module directive is rejected before any loading
- **WHEN** `JQ_EXPRESSION` is `import "secrets" as s; .`
- **THEN** startup fails with a reason containing `import`
- **AND** no file is read

#### Scenario: Look-alike names are allowed
- **WHEN** `JQ_EXPRESSION` is `{ env: .env, debug: "input" }`
- **THEN** the function starts and evaluates normally

#### Scenario: Clock functions are allowed
- **WHEN** `JQ_EXPRESSION` is `0 | todate`
- **THEN** `POST /run` returns result `"1970-01-01T00:00:00Z"`

### Requirement: Secrets are read statically from the configured store
An expression that references `$secrets` MUST use only static access:
- `$secrets.<identifier>`;
- `$secrets."<string>"`;
- `$secrets["<string literal>"]`.

Each form MAY be followed by further suffixes. Any other use MUST fail startup with a reason naming
the form, including:
- bare `$secrets`;
- a non-literal index;
- iteration;
- a slice.

For each referenced name, `dws-jq` SHALL read the secret once, before becoming ready, from
`JQ_SECRET_STORE` through the Dapr HTTP secrets API:
- it SHALL send `dapr-api-token` when `DAPR_API_TOKEN` is set;
- it SHALL bind the response's `value` key as a string;
- it SHALL retry for a bounded time.

The process MUST exit non-zero and never become ready in any of these cases:
- `JQ_SECRET_STORE` is missing;
- a secret cannot be read;
- a secret has no `value` key.

The error record MUST name the store and the secret names, never values. An expression without
`$secrets` SHALL NOT contact the sidecar.

Owning component: `dws-jq`.

#### Scenario: Referenced secret is fetched and bound
- **WHEN** `JQ_EXPRESSION` is `$secrets["api-token"] | length` and `JQ_SECRET_STORE` is `kubernetes`
- **AND** the store holds `api-token` with `value` `abcd`
- **THEN** exactly one secret read for `api-token` is made
- **AND** `POST /run` returns result `4`

#### Scenario: Dynamic access is rejected
- **WHEN** `JQ_EXPRESSION` is `$secrets[.key]`, `$secrets | keys` or `$secrets as $s | $s.a`
- **THEN** startup fails with a reason stating that only static `$secrets` access is allowed

#### Scenario: Missing store fails startup
- **WHEN** the expression references `$secrets.a` and `JQ_SECRET_STORE` is unset
- **THEN** startup fails naming `JQ_SECRET_STORE`

#### Scenario: Unreadable secret fails startup without leaking
- **WHEN** the sidecar keeps answering `404` for secret `a` until the retry bound
- **THEN** the process exits non-zero with one record naming store and secret `a`
- **AND** the record contains no secret value

#### Scenario: Readiness waits for secrets
- **WHEN** a secret read is still in progress
- **THEN** `GET /healthz` answers `503` and `POST /run` answers `503`

### Requirement: POST /run evaluates the configured expression
`POST /run` SHALL accept a JSON object `{ "input": any, "variables": object }`. Both fields are
optional; an absent `input` is `null`, and `"variables": null` is the same as no `variables`.
- `input` SHALL be bound to `.`.
- Each declared variable SHALL be bound to `$name`. A declared variable absent from the request
  SHALL be `null`.
- `$secrets` SHALL only ever hold the startup-loaded values.

Integer values in the request SHALL keep full precision.

The function SHALL take the first output only. On success it SHALL respond `200` with
`{ "result": <first output or null if none>, "truthy": <boolean> }`, where `truthy` is false only
for `null` and `false`. Values JSON cannot represent SHALL follow jq's output rules: NaN becomes
`null`, and ±infinity becomes ±1.7976931348623157e+308.

Each of the following MUST be answered `400` with `{ "error": "<reason>" }`, without evaluating:
- the body is not a JSON object;
- `variables` is neither an object nor `null`;
- an unknown top-level field is present;
- the body is larger than 4 MiB;
- `variables` names an undeclared variable, `secrets` included.

Owning component: `dws-jq`.

#### Scenario: First result and truthiness
- **WHEN** `JQ_EXPRESSION` is `.[]` and the request input is `[0, 1]`
- **THEN** the response is `200 {"result": 0, "truthy": true}`

#### Scenario: No output is null and false
- **WHEN** `JQ_EXPRESSION` is `empty`
- **THEN** the response is `200 {"result": null, "truthy": false}`

#### Scenario: Declared variable left out is null
- **WHEN** `JQ_VARIABLES` is `["item"]`, `JQ_EXPRESSION` is `$item == null`, and the request has no `variables`
- **THEN** the response is `200 {"result": true, "truthy": true}`

#### Scenario: Undeclared variable is a bad request
- **WHEN** `JQ_VARIABLES` is `["item"]` and the request sends `{"variables": {"other": 1}}`
- **THEN** the response is `400` and the expression is not evaluated

#### Scenario: Secrets cannot be injected
- **WHEN** a request sends `{"variables": {"secrets": {"a": "x"}}}`
- **THEN** the response is `400`

#### Scenario: Large integers keep precision
- **WHEN** `JQ_EXPRESSION` is `. + 1` and the request input is `9007199254740993`
- **THEN** the result is `9007199254740994`

#### Scenario: Combined template expression
- **WHEN** `JQ_EXPRESSION` is `{ "id": ([ .id ] | .[0]), "source": "api" }` and the input is `{"id": 7}`
- **THEN** the response is `200 {"result": {"id": 7, "source": "api"}, "truthy": true}`

### Requirement: Expression failures are 422 faults
An evaluation error in the first output SHALL be answered `422` with
`{ "fault": { "type": "expression", "expression": "<JQ_EXPRESSION as configured>", "detail": "<reason>" } }`.
Exceeding the configured time limit SHALL produce the same fault, with a detail stating the limit.

`dws-jq` MUST NOT answer `422` for request-shape errors or for its own unavailability.

Owning component: `dws-jq`.

#### Scenario: Evaluation error
- **WHEN** `JQ_EXPRESSION` is `.a + 1` and the input is `{"a": "x"}`
- **THEN** the response is `422` with `fault.type` `expression`
- **AND** `fault.expression` equals the configured text
- **AND** `fault.detail` is non-empty

#### Scenario: Time limit
- **WHEN** `JQ_TIMEOUT` is `100ms` and `JQ_EXPRESSION` is `last(range(1; infinite))`
- **THEN** the response is `422` within about the limit, with a detail naming the time limit

### Requirement: Secret values never leave in faults or logs
`dws-jq` MUST replace every non-empty secret value it holds with `***` in `fault.detail` and in every
log record, in both log modes. Both the raw value and its JSON-escaped form MUST be replaced. The
`result` of a successful evaluation is not altered.

Owning component: `dws-jq`.

#### Scenario: Secret in an error message is redacted
- **WHEN** secret `a` has value `s3cr3t-value` and `JQ_EXPRESSION` is `error($secrets.a)`
- **THEN** the `422` detail contains `***` and not `s3cr3t-value`
- **AND** no log line contains `s3cr3t-value`

### Requirement: Health endpoint reports readiness
`GET /healthz` SHALL answer `503` with `{"status":"starting","site":<JQ_SITE>}` until the
expression is compiled and every referenced secret is loaded. After that, it SHALL answer `200`
with `{"status":"ok","site":<JQ_SITE>}`.

Owning component: `dws-jq`.

#### Scenario: Ready without secrets
- **WHEN** `JQ_EXPRESSION` is `.` and the process has started
- **THEN** `GET /healthz` answers `200` with status `ok`

### Requirement: Structured logs follow the shared DWS log format
`dws-jq` SHALL select its log format with `DWS_LOG_FORMAT`.
- **JSON mode** (`json`, trimmed and case-insensitive): it SHALL write one JSON object per line with
  the `docs/log-format.md` keys, severity vocabulary and scrubber. A record logged with an error
  SHALL carry `exception.type`, `exception.message` and `exception.stacktrace`.
- **Text mode**: any other value SHALL give text mode.
- **Trace context**: request-scoped records SHALL carry `trace_id` and `span_id` from a valid
  inbound W3C `traceparent` header. The keys SHALL be omitted when the header is absent, invalid, or
  all-zero.
- The package SHALL pass the 12 golden cases of `docs/log-format.md`.

Owning component: `dws-jq`.

#### Scenario: Golden cases pass
- **WHEN** the package tests run the 12 golden cases in JSON mode
- **THEN** every case produces exactly one line matching its expected object

#### Scenario: Unrecognised format falls back to text
- **WHEN** `DWS_LOG_FORMAT` is `yaml`
- **THEN** startup succeeds and records are written in text mode

#### Scenario: Request trace context is attached
- **WHEN** a `/run` request carries `traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01` and fails with `422`
- **THEN** the JSON log record for that failure has that `trace_id` and `span_id`
