## ADDED Requirements

### Requirement: Shared step-service HTTP contract
The `dws-run` component (all three images) SHALL implement the same step-service HTTP contract as
`dws-call-http` and `dws-call-openapi`: `POST /run` accepting the current workflow data as a JSON
object body, and `GET /healthz`. An empty request body MUST be treated as `{}` rather than an
error. A malformed JSON body MUST return `400`.

#### Scenario: Health endpoint responds
- **WHEN** a client sends `GET /healthz`
- **THEN** the service responds `200` with a JSON body containing `status` and `task`

#### Scenario: Empty body is empty workflow data
- **WHEN** a client sends `POST /run` with an empty body
- **THEN** the step executes with `{}` as the workflow data
- **AND** the response status is `200`

#### Scenario: Malformed body is rejected
- **WHEN** a client sends `POST /run` with a body that is not valid JSON
- **THEN** the service responds `400` with a JSON body containing `task` and `error`
- **AND** no subprocess is spawned

### Requirement: Workflow data is passed to the subprocess on stdin
The service SHALL write the full `POST /run` body, JSON-encoded, to the spawned subprocess's stdin,
and MUST close stdin after writing. DSL 1.0.0 defines no `stdin` property on `run`, so no
author-supplied expression selects a subset of the input.

#### Scenario: Full input reaches the subprocess
- **WHEN** `POST /run` is called with body `{"order":{"id":7},"total":42}`
- **THEN** the subprocess receives `{"order":{"id":7},"total":42}` on stdin
- **AND** stdin is closed so a reader blocking on EOF terminates

#### Scenario: Empty input is still valid JSON on stdin
- **WHEN** `POST /run` is called with an empty body
- **THEN** the subprocess receives `{}` on stdin

### Requirement: Subprocess execution per image
Each image SHALL spawn its runtime-specific subprocess: `dws-run-shell` executes the configured
`COMMAND` via `sh -c`; `dws-run-script-js` executes the configured `SCRIPT` via `node`; and
`dws-run-script-python` executes the configured `SCRIPT` via `python3`. Each service MUST capture
the subprocess's stdout, stderr, and exit code.

#### Scenario: Shell image runs the configured command
- **WHEN** `dws-run-shell` is configured with `COMMAND` and receives `POST /run`
- **THEN** the command is executed through `sh -c`
- **AND** its stdout, stderr, and exit code are captured

#### Scenario: Script images run the configured script
- **WHEN** `dws-run-script-js` (or `dws-run-script-python`) is configured with `SCRIPT` and
  receives `POST /run`
- **THEN** the script is executed by `node` (respectively `python3`)
- **AND** its stdout, stderr, and exit code are captured

#### Scenario: Timeout terminates the subprocess
- **WHEN** a subprocess runs longer than the configured `TIMEOUT`
- **THEN** the subprocess is terminated
- **AND** the service responds `502` so the orchestrator's retry policy engages

### Requirement: RETURN selects the raw result value
The service SHALL select the raw result value according to `RETURN`: `stdout` yields the captured
stdout, `stderr` yields the captured stderr, `code` yields the exit code as a JSON number, `all`
yields a JSON object `{code, stdout, stderr}`, and `none` yields an empty result.

#### Scenario: RETURN=stdout yields stdout
- **WHEN** `RETURN=stdout` and the subprocess writes `hello` to stdout and exits `0`
- **THEN** the raw result value is the string `hello`

#### Scenario: RETURN=code yields the exit code
- **WHEN** `RETURN=code` and the subprocess exits `3`
- **THEN** the raw result value is the number `3`

#### Scenario: RETURN=all yields the full triple
- **WHEN** `RETURN=all` and the subprocess writes `out` to stdout, `err` to stderr, and exits `1`
- **THEN** the raw result value is an object containing `code` = `1`, `stdout` = `out`, and
  `stderr` = `err`

#### Scenario: RETURN=none yields nothing
- **WHEN** `RETURN=none` and the subprocess exits `0`
- **THEN** the raw result value is empty
- **AND** the response status is `200`

### Requirement: OUTPUT shapes the raw value into the response
`OUTPUT` SHALL shape how the raw value selected by `RETURN` folds into the response, using the same
semantics as `dws-call-http`: `replace` responds with the raw value itself, and `merge`
shallow-merges the raw value into the input workflow data. `merge` MUST fail with `500` when the
raw value is not a JSON object, since a non-object cannot be merged.

#### Scenario: OUTPUT=replace returns the raw value
- **WHEN** `RETURN=stdout`, `OUTPUT=replace`, and the subprocess prints `{"ok":true}`
- **THEN** the response body is `{"ok":true}`

#### Scenario: OUTPUT=merge folds into the input
- **WHEN** `RETURN=stdout`, `OUTPUT=merge`, the input is `{"a":1}`, and the subprocess prints
  `{"b":2}`
- **THEN** the response body contains both `a` = `1` and `b` = `2`

#### Scenario: OUTPUT=merge rejects a non-object raw value
- **WHEN** `OUTPUT=merge` and the raw value is a string or a number
- **THEN** the service responds `500` with an error describing the merge failure

### Requirement: Non-JSON output falls back to a raw string
When `RETURN` is `stdout` or `stderr` and `OUTPUT` is `replace`, the service SHALL attempt to parse
the trimmed captured output as JSON and MUST fall back to returning it as a raw JSON string when it
does not parse. Unlike `dws-call-http`, unparseable output MUST NOT be an error, because plain text
is the normal output of a shell command or script.

#### Scenario: JSON output is parsed
- **WHEN** the subprocess prints `{"id":1}` and `RETURN=stdout`, `OUTPUT=replace`
- **THEN** the response body is the JSON object `{"id":1}`

#### Scenario: Plain-text output is returned as a string
- **WHEN** the subprocess prints `deployment complete` and `RETURN=stdout`, `OUTPUT=replace`
- **THEN** the response body is the JSON string `"deployment complete"`
- **AND** the response status is `200`

### Requirement: Exit-code semantics depend on RETURN
A non-zero subprocess exit SHALL be treated as data, not an error, when `RETURN` is `code` or
`all`, because the author explicitly asked to observe the exit code. For every other `RETURN`
value (`stdout`, `stderr`, `none`), a non-zero exit MUST be treated as a step failure and returned
as `502` with a JSON body containing `task`, `exitCode`, and `stderr`, so the orchestrator's retry
policy re-invokes the step.

#### Scenario: Non-zero exit is data under RETURN=code
- **WHEN** `RETURN=code` and the subprocess exits `2`
- **THEN** the response status is `200`
- **AND** the response body conveys the exit code `2`

#### Scenario: Non-zero exit is data under RETURN=all
- **WHEN** `RETURN=all` and the subprocess exits `2`
- **THEN** the response status is `200`
- **AND** the response body contains `code` = `2` alongside `stdout` and `stderr`

#### Scenario: Non-zero exit is a failure under RETURN=stdout
- **WHEN** `RETURN=stdout` and the subprocess exits `2` after writing `boom` to stderr
- **THEN** the response status is `502`
- **AND** the response body contains `task`, `exitCode` = `2`, and `stderr` = `boom`

#### Scenario: Non-zero exit is a failure under RETURN=none
- **WHEN** `RETURN=none` and the subprocess exits `1`
- **THEN** the response status is `502`

### Requirement: Spawn failures are retryable
A failure to spawn the subprocess at all — a missing interpreter, a permission error, or any other
transport-equivalent fault — SHALL be returned as `502`, matching `dws-call-http`'s handling of
transport errors, so the orchestrator retries rather than failing the workflow instance.

#### Scenario: Spawn failure returns 502
- **WHEN** the configured interpreter cannot be executed
- **THEN** the service responds `502` with a JSON body containing `task` and `error`
