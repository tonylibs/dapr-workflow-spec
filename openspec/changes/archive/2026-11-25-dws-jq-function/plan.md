# `dws-jq` Expression Function Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `dws-jq`, a Go service that evaluates the one jq expression fixed in its deployment.
It is checked at startup and served at `POST /run`. Alongside it, build the `conformance/expressions/`
corpus and its `dws-jq` runner, plus CI and release wiring.

**Architecture:**
- A plain `net/http` server, with no Dapr SDK. It holds one compiled gojq program.
- A startup pipeline: config → unwrap/parse → AST policy walk → compile → listen → optional secret
  fetch → ready.
- The corpus runner calls the same `expr` package that `/run` uses, so corpus verdicts describe the
  served behavior.

**Tech Stack:**
- Go 1.26.9 and `github.com/itchyny/gojq` v0.12.20.
- Stdlib `net/http`, `log/slog` and `testing`.
- Test-only: `gopkg.in/yaml.v3` and `github.com/santhosh-tekuri/jsonschema/v6`.

**Spec:** `openspec/changes/dws-jq-function/`, which holds:
- `design.md` (decisions D1–D14);
- `specs/jq-expression-function/spec.md`;
- `specs/jq-expression-conformance/spec.md`.

It implements ADR 0014 (`docs/adr/0014-dws-jq-expression-function.md`) Decisions 1, 3, 4, 5, 6 and
9 (O-2, O-5).

## Global Constraints

- **Toolchain**: Go `1.26.9` in `go.mod` and the Dockerfile (`golang:1.26.9`). Runtime image
  `gcr.io/distroless/static:nonroot`, port `8080`.
- **gojq**: pinned at `v0.12.20`. Compile with no `WithEnvironLoader`, no `WithInputIter`, no
  `WithModuleLoader`.
- **Env names**:
  - `JQ_EXPRESSION`: required.
  - `JQ_VARIABLES`: JSON array, default `[]`.
  - `JQ_TIMEOUT`: default `5s`.
  - `JQ_SECRET_STORE`.
  - `JQ_SITE`: default `dws-jq`.
  - `PORT`: default `8080`.
  - `DAPR_HTTP_PORT`: default `3500`.
  - `DAPR_API_TOKEN`.
  - `DWS_LOG_FORMAT`.
- **Variable names**: must match `^[A-Za-z_][A-Za-z0-9_]*$`, must be unique, and must not be
  `secrets`, `ENV` or `__loc__`.
- **Blocked builtins**: `env`, `$ENV`, `input`, `inputs`, `input_filename`, `import`, `include`,
  `halt`, `halt_error`, `debug`, `stderr`, at any arity. `now` and the date and local-time functions
  are allowed.
- **Unwrap**: trim; if the text starts with `${` and ends with `}`, take the text between them and
  trim it. Otherwise it is bare jq. There is no brace matching (v1 `JqEvaluator.java:145-154`).
- **Result and truthiness**: the result is the first output, or `null` when there is none.
  `truthy` is false only for `null` and `false`.
- **HTTP**:
  - `200 {"result":…,"truthy":…}`;
  - `400 {"error":"…"}`;
  - `422 {"fault":{"type":"expression","expression":"<JQ_EXPRESSION as configured>","detail":"…"}}`;
  - `503` before ready;
  - body cap `4 MiB` (`4 << 20`).
- **Secrets**:
  - `GET http://127.0.0.1:{DAPR_HTTP_PORT}/v1.0/secrets/{store}/{name}`, with a `dapr-api-token`
    header when the token is set.
  - Bind the response's `value` key.
  - Retry for up to `60s` in total.
  - Redact with `***`.
- **Static `$secrets` forms only**: `$secrets.<ident>`, `$secrets."<str>"` without interpolation, and
  `$secrets["<literal>"]`.
- **Log contract**: `docs/log-format.md`: the keys, the scrubber regexes, and the 12 golden cases.
  `logger` is `dws-jq`.
- **Commits**: the user said "do not commit or push unless asked". Every **Commit** step below runs
  only if the user has asked for commits. Otherwise skip it and leave the changes in the working
  tree.
- **Package gate**: `cd dws-jq && make lint && make test`. The local toolchain is windows/386 with
  CGO off, so `-race` is unavailable there. Locally, run `go vet ./... && go test ./...` and state the
  gap; CI runs `-race`.

## Review Focus

1. **`${ … }` that is not one wrapper.** For example, `${ .a } + ${ .b }` starts with `${` and ends
   with `}`. It unwraps to `.a } + ${ .b` and must fail startup with a parse error, as v1 fails.
   Pinned in Task 2.
2. **`$secrets` / `$ENV` reached through binding forms.** These must be rejected at startup:
   `{$ENV}`, `{$secrets}`, `. as $secrets | …`, `def f($secrets): …`, `. as [$ENV] | …`.
   Pinned in Task 2.
3. **Secrets inside a gojq error message** with JSON escaping, e.g. a value containing `"`. Both the
   raw and the escaped form must become `***` in `detail` and in logs. Pinned in Tasks 4 and 6.
4. **Empty or non-object request bodies.** An empty body, `null`, `[]` and `"x"` get `400`, not
   `500`, and are never evaluated. Pinned in Task 6.
5. **Concurrent requests on one compiled program.** 50 parallel `/run` calls with different inputs
   must each get their own result. The test runs under CI's `-race`. Pinned in Task 6.

---

### Task 1: Package scaffold and configuration contract

**Files:**
- Create: `dws-jq/go.mod`, `dws-jq/Makefile`, `dws-jq/.gitignore`, `dws-jq/.dockerignore`,
  `dws-jq/version.txt` (`0.0.0`), and a `dws-jq/main.go` stub (`package main; func main() {}`).
- Create: `dws-jq/internal/config/config.go`, `dws-jq/internal/config/config_test.go`.
- Create: `dws-jq/testdata/contract/env-minimal.json`, `env-variables.json`, `env-secrets.json`.

**Interfaces:**
- Produces:
  ```go
  type Config struct {
      Expression   string        // JQ_EXPRESSION, raw as configured (not unwrapped)
      Variables    []string      // JQ_VARIABLES, in order
      Timeout      time.Duration // JQ_TIMEOUT
      SecretStore  string        // JQ_SECRET_STORE, "" if unset
      Site         string        // JQ_SITE
      Port         string        // PORT
      DaprHTTPPort string        // DAPR_HTTP_PORT
      DaprAPIToken string        // DAPR_API_TOKEN
      LogFormat    string        // DWS_LOG_FORMAT, raw
  }
  type FieldError struct{ Name, Reason string } // Error(): "<Name>: <Reason>"
  func Load() (Config, error)  // returns *FieldError on invalid input
  var Names = []string{"JQ_EXPRESSION","JQ_VARIABLES","JQ_TIMEOUT","JQ_SECRET_STORE","JQ_SITE","PORT","DAPR_HTTP_PORT","DAPR_API_TOKEN","DWS_LOG_FORMAT"}
  ```
  `Load` does not check whether `JQ_SECRET_STORE` is needed; that depends on the expression (Task 6).

- [ ] **Step 1: Scaffold**
  - `go.mod`: `module github.com/dws/dws-jq`, `go 1.26.9`, `require github.com/itchyny/gojq v0.12.20`.
  - `Makefile`: copy `dws-call-http/Makefile`, setting `IMAGE ?= registry.io/dws/dws-jq:1.0` and
    `BINARY := dws-jq`.
  - `.dockerignore` and `.gitignore`: as in `dws-call-http`.
  - Run: `cd dws-jq && go mod tidy && go build ./...`. Expected: exit 0.
- [ ] **Step 2: Write the failing tests** in `config_test.go`. They are table-driven, use
  `t.Setenv`, and use `reflect.DeepEqual` (no testify).
  - `TestLoad_defaults`: with only `JQ_EXPRESSION=.`, the result is
    `Config{Expression: ".", Variables: []string{}, Timeout: 5*time.Second, Site: "dws-jq", Port: "8080", DaprHTTPPort: "3500"}`.
  - `TestLoad_variables`: `["context","item","index"]` keeps its order.
  - `TestLoad_rejects`: each row asserts a `*FieldError` via `errors.As` and its `Name`:
    - `JQ_EXPRESSION` unset or whitespace → `JQ_EXPRESSION`;
    - `JQ_VARIABLES` = `{"a":1}`, `[1]`, `["item","item"]`, `["1x"]`, `["secrets"]`, `["ENV"]`,
      `["__loc__"]`, `["$item"]` → `JQ_VARIABLES`;
    - `JQ_TIMEOUT` = `0s`, `-1s`, `soon` → `JQ_TIMEOUT`.
- [ ] **Step 3: Run** `go test ./internal/config/ -v`. Expected: FAIL (`Load` undefined).
- [ ] **Step 4: Implement `Load()`** using the `getenv`/`parseTimeout` helper style of
  `dws-call-http/internal/config/config.go`. There is no config library.
- [ ] **Step 5: Write `TestConfigContract`**.
  - Each fixture is a flat JSON object of env name → string:
    - `env-minimal.json`: `{"JQ_EXPRESSION": "${ .count > 0 }"}`.
    - `env-variables.json`: `JQ_EXPRESSION` `$item < 3`, `JQ_VARIABLES` `["context","item","index"]`,
      `JQ_TIMEOUT` `250ms`, `JQ_SITE` `processItems.jq.while`, `PORT` `8080`, `DWS_LOG_FORMAT` `json`.
    - `env-secrets.json`: `JQ_EXPRESSION` `$secrets["api-token"]`, `JQ_SECRET_STORE` `kubernetes`,
      `JQ_SITE` `fulfillOrder.jq.from`, `DAPR_HTTP_PORT` `3500`, `DAPR_API_TOKEN` `test-token`.
  - The test loads each fixture with `t.Setenv` and asserts the expected `Config`.
  - It then asserts that `sort(Names)` equals the sorted union of all fixture keys.
- [ ] **Step 6: Run** `go test ./internal/config/ -v`. Expected: PASS.
- [ ] **Step 7: Commit** (only if asked): `feat(jq): scaffold dws-jq and pin its configuration contract`.

### Task 2: Unwrap, AST policy walk and `$secrets` scan

**Files:**
- Create: `dws-jq/internal/expr/unwrap.go`, `dws-jq/internal/expr/policy.go`,
  `dws-jq/internal/expr/policy_test.go`, `dws-jq/internal/expr/unwrap_test.go`.

**Interfaces:**
- Produces:
  ```go
  func Unwrap(text string) string
  type PolicyError struct{ Builtin string }   // Error(): `blocked builtin "<Builtin>"` (Builtin is "env", "$ENV", "import", "include", ...)
  type SecretAccessError struct{ Form string } // Error(): `only static $secrets access is allowed ($secrets.<name> or $secrets["<name>"]); found <Form>`
  func Check(q *gojq.Query) (secretNames []string, err error) // sorted, de-duplicated names; first violation wins
  ```

- [ ] **Step 1: Write `TestUnwrap`**:

  | Input | Output |
  |---|---|
  | `${ .a }` | `.a` |
  | `  ${.a}  ` | `.a` |
  | `.a` | `.a` |
  | `${ { a: 1 } }` | `{ a: 1 }` |
  | `${ .a } + ${ .b }` | `.a } + ${ .b` |
  | `${ .a` | `${ .a` |
- [ ] **Step 2: Write `TestCheck_blocked`**. Parse each expression with `gojq.Parse` and expect a
  `*PolicyError` with the given `Builtin`:
  - `env.HOME` → `env`; `$ENV.HOME` → `$ENV`; `{$ENV}` → `$ENV`; `. as [$ENV] | 1` → `$ENV`;
  - `input` → `input`; `[inputs]` → `inputs`; `input_filename` → `input_filename`;
  - `halt` → `halt`; `"x" | halt_error` → `halt_error`; `halt_error(1)` → `halt_error`;
  - `debug` → `debug`; `debug("m")` → `debug`; `stderr` → `stderr`;
  - `import "a" as s; .` → `import`; `include "h"; .` → `include`.

  The same check applies in nested positions:
  - `def f: debug; f`;
  - `reduce .[] as $x (0; . + ($x | env | length))`;
  - `foreach .[] as $x (0; input; .)`;
  - `try halt catch .`;
  - `if .a then stderr else . end`;
  - `{a: (. | debug)}`;
  - `"\(env)"`;
  - `label $out | inputs`;
  - `.[] | select(. == (input))`;
  - `def debug: .; debug` → `debug` (name-based).
- [ ] **Step 3: Write `TestCheck_allowed`**. Each expression returns a nil error and no secret names:
  - `.env`, `{env: 1}`, `{debug: .x}`, `"input"`, `.["halt"]`;
  - `now`, `0 | todate`, `0 | gmtime | mktime`, `0 | strftime("%Y")`, `0 | localtime`.
- [ ] **Step 4: Write `TestCheck_secrets`**:
  - Allowed, with their name sets:
    - `$secrets.a` → `[a]`;
    - `$secrets."a"` → `[a]`;
    - `$secrets["api-token"]` → `[api-token]`;
    - `$secrets["a"].b` → `[a]`;
    - `$secrets.b + $secrets.a + $secrets.a` → `[a b]`.
  - Rejected with `*SecretAccessError`:
    - `$secrets`, `$secrets | keys`, `$secrets as $s | $s.a`;
    - `$secrets[.k]`, `$secrets[$k]`, `$secrets["a" + .x]`, `$secrets."\(.x)"`;
    - `$secrets[]`, `$secrets[0:1]`;
    - `{$secrets}`, `. as $secrets | 1`, `def f($secrets): 1; f(.)`.
- [ ] **Step 5: Run** `go test ./internal/expr/ -run 'TestUnwrap|TestCheck' -v`. Expected: FAIL.
- [ ] **Step 6: Implement `Unwrap` and `Check`** as a recursive walk over the gojq v0.12.20 AST.
  - Node types to visit:
    - `Query` (`Imports`, `FuncDefs`, `Term`, `Left`, `Right`, `Patterns`);
    - `FuncDef` (`Name`, `Args`, `Body`);
    - `Term` (`Index`, `Func`, `Object`, `Array`, `Unary`, `Str`, `If`, `Try`, `Reduce`, `Foreach`,
      `Label`, `Query`, `SuffixList`);
    - `Index` (`Str`, `Start`, `End`);
    - `Func.Args`;
    - `String.Queries`;
    - `ObjectKeyVal` (`Key`, `KeyString`, `KeyQuery`, `Val`);
    - `Pattern` (`Name`, `Array`, `Object` → `PatternObject`);
    - `If`/`IfElif`, `Try`, `Reduce`, `Foreach`, `Label.Body`.
  - Variables are `Func{Name:"$x"}`. The shorthand `{$x}` is `ObjectKeyVal.Key == "$x"`.
  - Any `Import` is `import` or `include`, according to `IncludePath != ""`.
  - Rejection rules:
    - Any function-call name in the blocked set gives `PolicyError`, including a `FuncDef.Name`.
    - Any variable named `$ENV` gives `PolicyError{"$ENV"}`, whether as a reference, a shorthand
      key, a pattern binding, or a `FuncDef` arg.
  - `$secrets` handling:
    - At a `Term` with `Func.Name == "$secrets"`, the first suffix must be an `Index` with a
      non-empty `Name`, a `Str` without `Queries`, or a `Start` that is a single string-literal term
      with `End == nil` and `!IsSlice`.
    - Record the name, then continue walking the remaining suffixes.
    - Every other `$secrets` occurrence gives `SecretAccessError`: bare use, a binding, a shorthand
      key, or a `FuncDef` arg.
- [ ] **Step 7: Run** `go test ./internal/expr/ -v`. Expected: PASS.
- [ ] **Step 8: Commit** (only if asked): `feat(jq): reject blocked builtins and dynamic $secrets by AST walk`.

### Task 3: Compile and evaluate

**Files:**
- Create: `dws-jq/internal/expr/program.go`, `dws-jq/internal/expr/json.go`,
  `dws-jq/internal/expr/program_test.go`, `dws-jq/internal/expr/json_test.go`.

**Interfaces:**
- Consumes: `Unwrap` and `Check` (Task 2).
- Produces:
  ```go
  type CompileError struct{ Reason string }    // parse/compile failure, policy and secret errors are wrapped as-is (errors.As still finds *PolicyError / *SecretAccessError)
  func Compile(expression string, variables []string) (*Program, error)
  func (p *Program) Expression() string   // as configured (not unwrapped)
  func (p *Program) Variables() []string  // declared names, without "$"
  func (p *Program) SecretNames() []string
  type Input struct { Data any; Variables map[string]any; Secrets map[string]any }
  type Result struct { Value any; Truthy bool }
  type EvaluationError struct{ Detail string }
  func (p *Program) Evaluate(ctx context.Context, timeout time.Duration, in Input) (Result, error) // error is *EvaluationError
  func DecodeJSON(data []byte) (any, error) // UseNumber → int / *big.Int / float64
  func Encode(v any) ([]byte, error)        // jq output rules: NaN→null, ±Inf→±1.7976931348623157e+308
  ```
  `Compile` declares the variables as `"$"+name` for each declared name, plus `$secrets` last.
  `Evaluate` binds them in the same order. A missing variable is `nil`, and `Secrets` nil means `{}`.

- [ ] **Step 1: Write `TestCompile`**:
  - `${ .a }` compiles, and `Expression()` returns `${ .a }`.
  - `.a |` → `*CompileError`.
  - `${ .a } + ${ .b }` → `*CompileError` (Review Focus 1).
  - `$item < 3` with no variables → `*CompileError` whose message contains `$item`.
  - The same expression with `[]string{"item"}` compiles.
  - `env` → `errors.As` finds a `*PolicyError`.
  - `keys_unsorted`, `$__loc__` and `input_line_number` → `*CompileError` (unsupported in gojq).
- [ ] **Step 2: Write `TestEvaluate`**. Each row is (expression, input, vars) → (Value, Truthy):
  - `.[]` on `[0,1]` → `0`, true;
  - `empty` → `nil`, false;
  - `false` → false, false; `null` → nil, false;
  - `0`, `""`, `[]` → truthy true;
  - `$item == null` with `item` declared and absent → true;
  - `$secrets.a | length` with `Secrets{"a":"abcd"}` → `4`;
  - `{ "id": ([ .id ] | .[0]), "source": "api" }` on `{"id":7}` → `{"id":7,"source":"api"}`.

  Errors:
  - `.a + 1` on `{"a":"x"}` → `*EvaluationError` with a non-empty `Detail`.
  - `error("boom")` → `*EvaluationError` whose `Detail` contains `boom`.
  - `last(range(1; infinite))` with a `50*time.Millisecond` timeout → an `*EvaluationError` whose
    `Detail` is `evaluation exceeded the 50ms time limit`, returned in under 1 s.
- [ ] **Step 3: Write `TestDecodeEncode`**:
  - `DecodeJSON("9007199254740993")` gives a `*big.Int` or `int` that compares equal to
    9007199254740993.
  - `. + 1` on it encodes to `9007199254740994`.
  - `Encode(math.NaN())` = `null`.
  - `Encode(math.Inf(1))` = `1.7976931348623157e+308`.
  - `Encode(map[string]any{"b":1,"a":2})` = `{"a":2,"b":1}`.
- [ ] **Step 4: Run** `go test ./internal/expr/ -v`. Expected: FAIL.
- [ ] **Step 5: Implement** the code. Notes:
  - `Compile`: `gojq.Parse(Unwrap(expr))` → `Check` → `gojq.Compile(q, gojq.WithVariables(...))`,
    with no other options.
  - `Evaluate`: `context.WithTimeout`, then `code.RunWithContext`, then a single `Next()`.
    - No value → `nil`.
    - An `error` value → `*EvaluationError`. When `errors.Is(err, context.DeadlineExceeded)` or
      `ctx.Err() != nil`, use the time-limit detail.
    - Return the gojq value unchanged. Callers serialize it with `Encode`, so NaN and ±Inf follow
      the jq output rules and never fail.
  - `Encode`: walk the value and replace non-finite floats before `json.Marshal`. Map keys are
    sorted by `encoding/json`.
- [ ] **Step 6: Run** `go test ./internal/expr/ -v`. Expected: PASS.
- [ ] **Step 7: Commit** (only if asked): `feat(jq): compile once and evaluate with v1 result and truthiness`.

### Task 4: Secret fetch and redaction

**Files:**
- Create: `dws-jq/internal/secrets/fetch.go`, `dws-jq/internal/secrets/redact.go`, and their
  `_test.go` files.

**Interfaces:**
- Produces:
  ```go
  type Fetcher struct {
      BaseURL string        // "http://127.0.0.1:" + DaprHTTPPort
      Token   string        // DAPR_API_TOKEN, header "dapr-api-token" when non-empty
      Client  *http.Client
      Bound   time.Duration // default 60s when zero
      Backoff time.Duration // initial; doubles, capped at 5s; default 250ms when zero
  }
  type FetchError struct{ Store, Name, Reason string } // Error() never contains a value
  func (f *Fetcher) Fetch(ctx context.Context, store string, names []string) (map[string]string, error)
  type Redactor struct{ /* unexported */ }
  func NewRedactor(values []string) *Redactor
  func (r *Redactor) Redact(s string) string // nil receiver returns s unchanged
  ```

- [ ] **Step 1: Write `TestFetch`** against an `httptest.Server` fake sidecar:
  - The request path is `/v1.0/secrets/kubernetes/api-token`.
  - The `dapr-api-token` header is present only when `Token` is set.
  - `{"value":"abcd"}` → `map{"api-token":"abcd"}`.
  - `{"other":"x"}` → `*FetchError{Reason contains "value"}`.
  - With `Bound: 200ms`, a 404 every time gives a `*FetchError` naming the store and secret, whose
    `Error()` does not contain the 404 body text `do-not-leak`.
  - 503 twice and then 200 → success.
  - Two names give two requests.
- [ ] **Step 2: Write `TestRedactor`**:
  - Values `["s3cr3t-value", "a\"b"]` redact `x s3cr3t-value y` → `x *** y`.
  - The JSON-escaped form `a\\\"b` inside `{"v":"a\"b"}` → `***`.
  - Values `["ab","abc"]` redact `abc` → `***`, not `***c`.
  - An empty value is ignored.
  - A nil `*Redactor` passes text through.
- [ ] **Step 3: Run** `go test ./internal/secrets/ -v`. Expected: FAIL.
- [ ] **Step 4: Implement both.** The redactor sorts the raw forms and the `json.Marshal` forms with
  quotes stripped, by length descending, and builds one `strings.Replacer`.
- [ ] **Step 5: Run** `go test ./internal/secrets/ -v`. Expected: PASS.
- [ ] **Step 6: Commit** (only if asked): `feat(jq): read referenced secrets from the Dapr secrets API and redact them`.

### Task 5: Structured logging (`DWS_LOG_FORMAT`)

**Files:**
- Create: `dws-jq/internal/logging/logging.go`, `dws-jq/internal/logging/trace.go`,
  `dws-jq/internal/logging/logging_test.go`.

**Interfaces:**
- Produces:
  ```go
  const LoggerName = "dws-jq"
  const LevelFatal = slog.Level(12)
  func New(w io.Writer, format string, redact func(string) string) *slog.Logger // format = raw DWS_LOG_FORMAT; redact may be nil
  func WithTraceparent(ctx context.Context, header string) context.Context     // stores IDs only if valid and non-zero
  ```
  In JSON mode, an attribute whose value is an `error` (any key) becomes `exception.*`. Other
  attributes are appended after the contract keys, without reusing any of the nine contract names.

- [ ] **Step 1: Write `TestLogFormatContract`** using the 12 golden cases of `docs/log-format.md`.
  - Compare the parsed object against the expected lines in that file.
  - `timestamp` must match `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$`.
  - `logger` is `dws-jq`.
  - Cases 5 and 11 log with an `error` attribute holding `errors.New("boom")` and
    `errors.New("401 for token=abc12345")`.
  - `exception.type` is `*errors.errorString`.
  - `exception.stacktrace` is a non-empty string that contains the (scrubbed) message.
  - Case 2 uses
    `WithTraceparent(ctx, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")` with
    `logger.InfoContext`.
  - Case 4 uses an all-zero `traceparent`.
  - Case 12 logs at `LevelFatal`.
  - Each case writes exactly one `\n`-terminated line, and absent keys are absent.
- [ ] **Step 2: Write the other tests:**
  - `TestNew_textFallback`: `""`, `"text"` and `"yaml"` produce non-JSON `key=value` text.
  - `TestNew_caseInsensitive`: `" JSON "` gives JSON.
  - `TestNew_redactsBeforeScrub`: with `redact` replacing `hunter3` with `***`, the message
    `value hunter3` contains `***` in both modes.
- [ ] **Step 3: Run** `go test ./internal/logging/ -v`. Expected: FAIL.
- [ ] **Step 4: Implement** an `slog.Handler` for JSON mode, wrapping `slog.TextHandler` for text
  mode with redaction applied to the message.
  - Scrubber (applied after `redact`, in JSON mode only):
    1. `(?i)([\w.-]*(?:authorization|password|passwd|secret|token|api[-_]?key)[\w.-]*)("?\s*[:=]\s*"?)(?:(?:bearer|basic)\s+)?[^\s"',;&}]+`
       → `${1}${2}[REDACTED]`
    2. `(?i)\b(bearer|basic)\s+[A-Za-z0-9._~+/-]{8,}=*` → `${1} [REDACTED]`
  - `exception.stacktrace` = `"<type>: <message>\n"` followed by `debug.Stack()`, both scrubbed.
- [ ] **Step 5: Run** `go test ./internal/logging/ -v`. Expected: PASS.
- [ ] **Step 6: Commit** (only if asked): `feat(jq): write the DWS structured log format`.

### Task 6: HTTP server and `main`

**Files:**
- Create: `dws-jq/internal/server/server.go`, `dws-jq/internal/server/server_test.go`,
  `dws-jq/main.go` (replace the stub), `dws-jq/main_test.go`.

**Interfaces:**
- Consumes: `config.Load`, `expr.Compile`/`Program`, `secrets.Fetcher`/`NewRedactor`, and
  `logging.New`/`WithTraceparent`/`LevelFatal`.
- Produces:
  ```go
  type Evaluator interface {
      Evaluate(ctx context.Context, timeout time.Duration, in expr.Input) (expr.Result, error)
      Variables() []string
      Expression() string
  }
  type Options struct { Site string; Timeout time.Duration }
  func New(ev Evaluator, opts Options, log *slog.Logger) *Server
  func (s *Server) Handler() http.Handler                     // GET /healthz, POST /run
  func (s *Server) MarkReady(secrets map[string]any, r *secrets.Redactor) // atomically flips readiness
  const MaxBodyBytes = 4 << 20
  ```

- [ ] **Step 1: Write `server_test.go`** with `httptest` and a real `expr.Program`:
  - `TestHealthz`:
    - before `MarkReady`: `503 {"status":"starting","site":"s"}`;
    - after: `200 {"status":"ok","site":"s"}`.
  - `TestRun_notReady`: `503`.
  - `TestRun_success`:
    - `.[]` on `{"input":[0,1]}` → `200 {"result":0,"truthy":true}`;
    - `empty` → `{"result":null,"truthy":false}`.
  - `TestRun_badRequest`: each body below gives `400` with a non-empty `error`, and the evaluator is
    not called (assert with a counting fake). Bodies:
    - `` (empty), `null`, `[]`, `"x"`, `{`;
    - `{"variables":[]}`;
    - `{"input":1,"expression":"."}` (unknown field);
    - `{"variables":{"other":1}}` with declared `["item"]`;
    - `{"variables":{"secrets":{}}}`;
    - a body of `MaxBodyBytes+1` bytes.
  - `TestRun_fault`: `.a + 1` on `{"input":{"a":"x"}}` → `422`.
    - `fault.type` is `expression`.
    - `fault.expression` is the configured text exactly (e.g. `${ .a + 1 }`).
    - `fault.detail` is non-empty.
  - `TestRun_timeout`: with `Timeout: 50ms` and `last(range(1; infinite))` → `422`, and the detail
    contains `time limit`.
  - `TestRun_redactsDetail`: `error($secrets.a)` with secret `a` = `s3"cret` gives a detail
    containing `***` and neither `s3"cret` nor `s3\"cret`. The captured log contains neither form
    (Review Focus 3).
  - `TestRun_logsTraceOnFailure`: a `422` request with a `traceparent` header yields one JSON log
    record with that `trace_id`/`span_id` and `severity` `INFO`. A `200` request yields no record.
  - `TestRun_concurrent`: 50 goroutines post `{"input":{"n":i}}` to `.n` and each receives
    `result == i` (Review Focus 5).
- [ ] **Step 2: Run** `go test ./internal/server/ -v`. Expected: FAIL.
- [ ] **Step 3: Implement the server.**
  - Decode with `http.MaxBytesReader` and `json.Decoder` with `DisallowUnknownFields`, into
    `struct{ Input json.RawMessage; Variables map[string]json.RawMessage }`.
  - Decode values with `expr.DecodeJSON`.
  - Check declared names against `ev.Variables()`.
  - Store the request's `traceparent` with `logging.WithTraceparent`.
  - Logging: one `INFO` per `422` and one `WARN` per `400`, each with `site` and the redacted
    detail.
- [ ] **Step 4: Run** `go test ./internal/server/ -v`. Expected: PASS.
- [ ] **Step 5: Write `main_test.go`.** `TestMain_startupFailure` builds the binary once
  (`go build -o <tmp>`) and runs it per row, with `DWS_LOG_FORMAT=json` and `PORT` set to a free
  port.
  - Each run must exit non-zero within 5 s and write exactly one stdout line with `severity` `ERROR`
    whose `message` contains the expression text and the expected reason fragment.
  - The port must never accept a connection.
  - Rows:

    | `JQ_EXPRESSION` | `JQ_VARIABLES` | Reason fragment |
    |---|---|---|
    | `.a \|` | — | — |
    | `${ { home: env.HOME } }` | — | `env` |
    | `$item < 3` | none | `$item` |
    | `$secrets.a` | — | `JQ_SECRET_STORE` (store unset) |
    | `$secrets[.k]` | — | `static` |
    | `.` | `["item","item"]` | `JQ_VARIABLES` |

    For the last row, the `ERROR` line names `JQ_VARIABLES` rather than the expression.
- [ ] **Step 6: Implement `main.go`.**
  1. `config.Load`. An error is logged at `ERROR` with a default JSON/text logger, then exit 1.
  2. Build the logger with a redact closure over an `atomic.Pointer[secrets.Redactor]`.
  3. `expr.Compile`. An error is logged as one `ERROR` with `site`, `expression` and the reason,
     then exit 1.
  4. If `SecretNames()` is non-empty and `SecretStore == ""`, log one `ERROR` naming
     `JQ_SECRET_STORE` and exit 1. If the store is set but unused, log one `WARN`.
  5. Start the `http.Server` (`ReadHeaderTimeout` 10 s) on `:PORT`.
  6. If names are referenced, call `Fetcher.Fetch`. An error is logged as one `ERROR`; shut down,
     then exit 1.
  7. Build the redactor, then `MarkReady`.
  8. Log one `INFO` `ready` with the site and variable names, never the expression's secrets.
  9. Shut down gracefully on SIGINT/SIGTERM.
- [ ] **Step 7: Run** `go test ./... -v`. Expected: PASS.
- [ ] **Step 8: Commit** (only if asked): `feat(jq): serve POST /run and gate readiness on startup checks`.

### Task 7: Expression corpus data

**Files:**
- Create: `conformance/expressions/README.md`, `conformance/expressions/case.schema.json`,
  `conformance/expressions/divergences/dws-jq.yaml`, and
  `conformance/expressions/cases/{contract,builtins,dialect,loops,catch,templates}/*.yaml`.

**Interfaces:**
- Produces: the case format consumed by Task 8.
  ```yaml
  name: <area>/<file>             # pattern ^[a-z0-9-]+/[a-z0-9-]+$
  test: givenX_whenY_thenZ        # same pattern as conformance/data-flow/case.schema.json
  area: contract|builtins|dialect|loops|catch|templates
  summary: …
  source: …                       # ADR 0012 D9 / ADR 0014 D3 / ADR 0014 D5 + gojq README / data-flow case / v1 test file:line
  engine: …                       # optional
  expression: '…'                 # single-quoted
  input: …                        # required, may be null
  variables: { … }                # optional
  expect: { result: …, truthy: … }   # or
  expect: { fault: { phase: compile|evaluate, detailContains: [ … ] } }
  ```
  Divergence file: `runtime: dws-jq (gojq)`, `divergences: [{case, reason, followUp}]`.

- [ ] **Step 1: Write `case.schema.json`.** Model it on `conformance/data-flow/case.schema.json`:
  2020-12, `additionalProperties: false`, and `expect` as `oneOf`. Its `$id` is
  `https://github.com/tonylibs/dapr-workflow-spec/conformance/expressions/case.schema.json`.
- [ ] **Step 2: Write `README.md`.** Mirror the sections of `conformance/data-flow/README.md`:
  - the layout;
  - the case format table;
  - the authoring rules: expectations come from ADR 0012 D9 and ADR 0014 D3/D5, never edited to fit
    an engine; no services, secrets or time-zone values; every expression single-quoted;
  - the runner rules, steps 1–5, with the four verdicts;
  - the divergence-list format;
  - engine notes: gojq (`dws-jq`) and jackson-jq (EX-1, not yet run);
  - how to run: `cd dws-jq && go test ./internal/conformance/ -v`.
- [ ] **Step 3: Write `contract/` cases.**
  - `wrapped`: `${ .a }` on `{"a":1}` → `1`, true.
  - `bare`: `.a` → the same.
  - `first-of-many`: `.[]` on `[0,1]` → `0`, true.
  - `no-output`: `empty` → `null`, false.
  - Truthiness: `truthy-false` (`false`), `truthy-null` (`null`), `truthy-zero` (`0`),
    `truthy-empty-string` (`""`), `truthy-empty-array` (`[]`).
  - `evaluate-error`: `.a + 1` on `{"a":"x"}` → fault `evaluate`.
- [ ] **Step 4: Write `builtins/`.** Convert the 13 files of `conformance/data-flow/cases/builtins/`,
  keeping each file name, `summary`, `source` and `engine`, with a new `test`.
  - The expression is the jq of the stage that held it (e.g. `env.yaml` → `${ { home: env.HOME } }`).
  - `input` is the original instance input.
  - Blocked cases: `fault: {phase: compile, detailContains: [<builtin>]}`.
  - `clock-now` and `clock-local-time`: `result` = the original `expect.output`, and `truthy: true`.
  - Add the guard cases:
    - `lookalike-field`: `.env` on `{"env":1}` → `1`;
    - `lookalike-key`: `{debug: 1}` → `{"debug":1}`;
    - `lookalike-string`: `"input"` → `"input"`.
- [ ] **Step 5: Write `dialect/`**, one case per ADR 0014 Decision 5 row. The source is ADR 0014 D5
  and the gojq README.
  - `key-order`: `{"b":1,"a":2} | tojson` → `"{\"a\":2,\"b\":1}"`.
  - `key-order-entries`: `{"b":1,"a":2} | to_entries | map(.key)` → `["a","b"]`.
  - `keys-unsorted`: `keys_unsorted` → compile fault, `[keys_unsorted]`.
  - `date-functions`: `0 | todate` → `"1970-01-01T00:00:00Z"`.
  - `integer-precision`: `9007199254740993 + 1` → `9007199254740994`.
  - `string-index`: `"abcde"[2]` → `"c"`.
  - `regex-lookaround`: `"ab" | test("a(?=b)")` → fault `evaluate`, `detailContains: []`. The phase
    is to confirm: gojq compiles regexes at call time.
  - `loc-variable`: `$__loc__` → compile fault, `[$__loc__]`.
- [ ] **Step 6: Write `loops/` and `catch/`** from v1's tests. The source is a
  `dws-orchestrator/src/test/...:<line>` reference.
  - `loops/`:
    - `in-field`: `.pets` → `[{"id":1},{"id":2}]`.
    - `in-wrapped`: `${ .pets }` → `[1,2,3]`.
    - `in-variable`: `$sample` with `{sample:[10,20,30]}` → `[10,20,30]`.
    - `in-not-array`: `.count` on `{"count":3}` → `3`, true. Note in `summary`: v1's loop rejects a
      non-array, and that check belongs to the caller.
    - `in-literal`: `[1, 2]` → `[1,2]`.
    - `while-count`: `.count > 0` on `{"count":3}` → true.
    - `while-flag-false`, `while-flag-null`, `while-flag-missing`: `.flag` → `false`/`null`/`null`,
      all with truthy false.
    - `while-item-below` (`item` 2 → true) and `while-item-above` (`item` 5 → false):
      `$item < 3`, `variables: {item: …}`.
    - `while-at-index`: `$i < 2` with `{i: 1}` → true.
  - `catch/`. Each binds `error` (or `oops`) to
    `{"type":"https://serverlessworkflow.io/spec/1.0.0/errors/communication","status":503,"instance":"/fetchOrder","title":"Communication error","detail":"step 'fetch-order' failed with status 503: upstream down"}`
    and `context` to `{}`, with input `{}`:
    - `when-429`: `$error.status == 429` → false.
    - `except-when-503`: `$error.status == 503` → true.
    - `as-variable`: `$oops.status == 503` → true.
    - `retry-when-429`: `$error.status == 429` → false.
- [ ] **Step 7: Write `templates/`**:
  - `adr-example`: `{ "id": ([ .id ] | .[0]), "source": "api" }` on `{"id":7}` →
    `{"id":7,"source":"api"}`.
  - `part-def`: `{ "a": ([ def inc: . + 1; .n | inc ] | .[0]) }` on `{"n":1}` → `{"a":2}`.
  - `part-as`: `{ "a": ([ .n as $x | $x * $x ] | .[0]), "b": ([ .n ] | .[0]) }` on `{"n":3}` →
    `{"a":9,"b":3}`.
  - `part-empty`: `{ "a": ([ empty ] | .[0]), "b": 1 }` → `{"a":null,"b":1}`.
  - `part-many`: `{ "a": ([ .[] ] | .[0]) }` on `[3,4]` → `{"a":3}`.
- [ ] **Step 8: Write `divergences/dws-jq.yaml`** with the header comment adapted from
  `conformance/data-flow/divergences/v1.yaml` and `divergences: []`.
- [ ] **Step 9: Commit** (only if asked): `test(jq): add the conformance/expressions corpus`.

### Task 8: Corpus runner

**Files:**
- Create: `dws-jq/internal/conformance/runner.go`, `dws-jq/internal/conformance/runner_test.go`,
  `dws-jq/internal/conformance/corpus_test.go`.
- Modify: `dws-jq/go.mod` (add `gopkg.in/yaml.v3` and `github.com/santhosh-tekuri/jsonschema/v6`).

**Interfaces:**
- Consumes: `expr.Compile`, `Program.Evaluate`, `*expr.EvaluationError` and `expr.Encode`.
- Produces:
  ```go
  type Verdict string // "pass" | "fail" | "known divergence" | "stale divergence"
  type CaseResult struct{ Name, Test string; Verdict Verdict; Reason string }
  type Report struct{ Results []CaseResult }
  func (r Report) OK() bool                    // false on any fail or stale divergence
  func Run(corpusDir, divergenceFile string) (Report, error) // error = structural problem (schema, path/area, duplicate test)
  ```

- [ ] **Step 1: Write `runner_test.go`** with self-tests on a temporary corpus built in `t.TempDir()`,
  copying `case.schema.json`:
  - `TestRun_areaMismatch`: a case with `area: loops` under `cases/catch/` → `Run` returns an error.
  - `TestRun_duplicateTest`: two cases sharing a `test` → error.
  - `TestRun_schemaInvalid`: a missing `expect` → error.
  - `TestRun_staleDivergence`: a passing case that is listed → verdict `stale divergence`, and
    `OK()` false.
  - `TestRun_knownDivergence`: a failing case that is listed → `known divergence`, and `OK()` true.
  - `TestRun_numbersByValue`: expectation `10` with expression `10.0` → pass.
  - `TestRun_faultPhase`: an expected `evaluate` fault on a compile failure → `fail`.
- [ ] **Step 2: Run** `go test ./internal/conformance/ -run TestRun -v`. Expected: FAIL.
- [ ] **Step 3: Implement `Run`**:
  1. Walk `cases/**/*.yaml`. Decode the YAML into `any` and convert to JSON-compatible values.
  2. Validate against `case.schema.json` with `jsonschema/v6`.
  3. Check `name == area/<file>`, `area == directory`, and that `test` is unique.
  4. Evaluate:
     - A compile error matches only `phase: compile`; an `*EvaluationError` matches only
       `phase: evaluate`.
     - `detailContains` is checked against `err.Error()` or the `Detail`.
     - Results compare after `expr.Encode` → `json.Unmarshal` into `float64`-number trees, so `10`
       equals `10.0`.
  5. Apply the divergences.
- [ ] **Step 4: Write `corpus_test.go`.** `TestExpressionCorpus` does the following:
  - sets `TZ` to `UTC` with `t.Setenv`, then sets `time.Local = time.UTC`;
  - runs `Run` with `DWS_CONFORMANCE_EXPRESSIONS` or `../../../conformance/expressions`;
  - prints one `t.Logf` line per case (`<verdict>  <test>  <reason>`) and the totals;
  - fails if `!report.OK()`.
- [ ] **Step 5: Run** `go test ./internal/conformance/ -v`. Expected: self-tests PASS. Every corpus
  case passes, or is a listed known divergence.
  - For each failure, decide which side is wrong. If the implementation is wrong, fix it (Tasks 2–3).
  - If gojq genuinely differs from a carried-over expectation (likely candidates:
    `builtins/clock-local-time`, `dialect/regex-lookaround` phase), add a divergence entry with a
    reason and follow-up. Never edit the expectation.
  - Note which ADR 0014 D5 "to confirm" cells (integer precision, string indexing, regex) the run
    confirmed on the gojq side.
- [ ] **Step 6: Commit** (only if asked): `test(jq): run the expression corpus in the dws-jq gate`.

### Task 9: Packaging, CI, release and docs

**Files:**
- Create:
  - `dws-jq/Dockerfile`;
  - `dws-jq/k8s/knative-service.yaml`;
  - `dws-jq/README.md`;
  - `dws-jq/CLAUDE.md`;
  - `.github/workflows/dws-jq.yml`.
- Modify:
  - `release-please-config.json`;
  - `.release-please-manifest.json`;
  - `.github/workflows/component-release.yml` (the `options:` list);
  - `docs/release-process.md` (the "Workflows involved" table and the "First release of a new
    component" paragraph);
  - `CLAUDE.md` (package map, gate table, cross-package contracts, structured-log bullet);
  - `docs/log-format.md` (intro list, severity table, trace-context table).

- [ ] **Step 1: Dockerfile.** Copy `dws-call-http/Dockerfile` with the binary renamed to `dws-jq`.
- [ ] **Step 2: Example Knative Service.**
  - Copy the shape of `dws-call-http/k8s/knative-service.yaml`: `dws.io/step-type: jq`, app-id
    `processitems-jq-while`, and `minScale "0"`.
  - Env: `JQ_EXPRESSION: '$item < 3'`, `JQ_VARIABLES: '["context","item","index"]'`,
    `JQ_SITE: processItems.jq.while`.
  - Readiness probe on `/healthz`.
- [ ] **Step 3: CI workflow.** Copy `.github/workflows/dws-call-http.yml`, replacing
  `dws-call-http` with `dws-jq`.
  - Add the path `conformance/expressions/**` to both `push` and `pull_request`.
  - Before push, add an image smoke step: `docker run -d -p 8080:8080 -e JQ_EXPRESSION=. <image>`,
    then poll `curl -fsS localhost:8080/healthz` for up to 20 s and expect `"status":"ok"`. Then
    `curl -fsS -X POST localhost:8080/run -d '{"input":1}'` must equal
    `{"result":1,"truthy":true}`. Build with `load: true` for the smoke test, then push off-PR.
- [ ] **Step 4: Release wiring.**
  - `release-please-config.json`: package
    `"dws-jq": {"release-type":"simple","component":"dws-jq","changelog-path":"CHANGELOG.md","version-file":"version.txt","initial-version":"0.1.0","skip-github-release":true}`.
  - Manifest: `"dws-jq": "0.0.0"`.
  - `component-release.yml`: add `- dws-jq` after `dws-call-http`.
  - `docs/release-process.md`:
    - a workflow row: same triggers on `dws-jq/**`, `conformance/expressions/**`, `dws-jq-v*`.
      Gate: `go vet`, gofmt, `go test -race` (includes the corpus). The image job smoke-tests
      `/healthz` and `/run`. It is not pinned by the chart: the controller deploys one per
      expression site (2m.2).
    - a sentence adding `dws-jq` to the `dws-mid-*` / `dws-schedule-time` first-release path.
- [ ] **Step 5: Validate the workflow YAML.** Run
  `python -c "import yaml,sys;yaml.safe_load(open('.github/workflows/dws-jq.yml'))"` (or
  `actionlint` if installed) and
  `jq . release-please-config.json .release-please-manifest.json > /dev/null`. Expected: exit 0.
- [ ] **Step 6: `dws-jq/README.md`.** Sections:
  - Purpose (ADR 0014).
  - Configuration contract: the D3 table, plus "pinned by `testdata/contract/`; the controller
    renders it (2m.2)".
  - HTTP contract: 200/400/422/503 with examples.
  - Startup checks.
  - Builtin policy, including the name-based `def` note.
  - `$secrets`: static only, allowed forms, the rejected forms, and why (answers the 2.0d
    follow-up).
  - Dialect: the gojq differences, with a pointer to `conformance/expressions/`.
  - Logging.
  - EX-3 results.
  - Local development (`make` targets; no `-race` on 386).
- [ ] **Step 7: `dws-jq/CLAUDE.md`.** Package idioms only, in the format of
  `dws-call-http/CLAUDE.md`:
  - commands;
  - typed errors with `errors.As`;
  - the config `getenv` style;
  - logging only in `main.go` and `internal/server`;
  - tests: stdlib, table-driven, same-package;
  - the AST walker is pinned to gojq v0.12.20, so re-run the policy tests when bumping;
  - the corpus runner path and env override;
  - no `-race` on windows/386.
- [ ] **Step 8: Root `CLAUDE.md`.**
  - Package map row: `dws-jq` | Go 1.26 | "Per-expression jq function (ADR 0014): one compiled
    expression, `POST /run`; not yet called or deployed".
  - Add `dws-jq` to the "not yet listed in `AGENTS.md`" sentence.
  - Gate row: `dws-jq` | `make lint && make test`.
  - New contract bullet: "**`dws-jq` configuration contract** (`dws-controller` 2m.2 → `dws-jq`)".
    It covers the env names, the fixtures in `dws-jq/testdata/contract/`, `TestConfigContract`, and
    "a drifted name is not a compile error: the function fails startup or ignores the knob". It
    also covers the `/run` request/response shape that 3b/3c/3f/3g/6b callers will rely on.
  - Structured-log bullet: add `dws-jq` to the package list.
- [ ] **Step 9: `docs/log-format.md`.**
  - Intro list: add `dws-jq`.
  - Severity table row: Go `slog` (`dws-jq`): `LevelFatal`→`FATAL`, `Error`→`ERROR`, `Warn`→`WARN`,
    `Info`→`INFO`, `Debug`→`DEBUG`.
  - Trace-source row: `dws-jq` | the inbound W3C `traceparent` header of the request being served
    (no OTel SDK).
- [ ] **Step 10: Full gate.** Run `cd dws-jq && make lint`, then `go test ./...`; use `make test` on
  linux/amd64. Expected: PASS. Record that `-race` did not run locally. If the Docker daemon is up,
  also run `make docker`, plus the smoke test from Step 3. Expected: the image builds and the smoke
  test passes. Otherwise record "image not built locally: Docker daemon down".
- [ ] **Step 11: Commit** (only if asked): `ci(jq): add dws-jq image workflow and release-please package`,
  plus `docs(jq): document dws-jq and its contracts`.

### Task 10: EX-3, ADR notes and final verification

**Files:**
- Modify: `dws-jq/README.md` (EX-3 section), `docs/adr/0014-dws-jq-expression-function.md`,
  `docs/roadmaps/workflow-runtime-architecture-roadmap.md` (row 2j status).

- [ ] **Step 1: Check the cluster.** Run `kubectl get nodes`,
  `kubectl get crd services.serving.knative.dev`, and `kubectl get crd components.dapr.io`. If any
  fails, go to Step 3.
- [ ] **Step 2: Measure** (only when Step 1 succeeds):
  1. Load the image (`docker-desktop` shares the daemon; for kind, `kind load docker-image`).
  2. Apply `k8s/knative-service.yaml` with the local image tag.
  3. Run a curl pod with `dapr.io/enabled: "true"`.
  4. Cold start: after `kubectl wait` confirms zero pods, time
     `curl -s -o /dev/null -w '%{time_total}' -X POST localhost:3500/v1.0/invoke/processitems-jq-while/method/run -d '{"input":{},"variables":{"item":1}}'`.
     Take 5 samples, each after scale-to-zero.
  5. Warm: run 200 sequential calls and compute p50/p95.
  6. Record the numbers, the cluster type, the Knative/Dapr versions and the method.
- [ ] **Step 3: Record EX-3** in `dws-jq/README.md`: either the numbers, or "Not measured on
  2026-11-24: no reachable cluster (Docker daemon down, `docker-desktop` context refused). EX-3
  stays open."
- [ ] **Step 4: ADR 0014.** Append a dated "### 2j notes (2026-11-24)" block under Follow-ups,
  without changing any decision:
  - the Decision 5 gojq-side cells now confirmed by `conformance/expressions/dialect/*`;
  - O-5 dynamic access: `dws-jq` rejects every non-static `$secrets` use at startup;
  - the EX-3 status;
  - anything that turned out wrong. Known candidate: Decision 4's table says gojq `input_filename`
    "returns `null`"; record only what the run shows.

  Set the EX-3 row status to match. Roadmap row 2j: status ✅ or 🟡, with the date.
- [ ] **Step 5: Final verification.** Run `openspec validate dws-jq-function`, then
  `cd dws-jq && make lint && go test ./...`. Expected: valid; PASS. Collect the report for the user:
  - the configuration contract;
  - the corpus totals and the divergence list;
  - the confirmed D5 cells;
  - the EX-3 numbers or why there are none;
  - the dynamic `$secrets` choice;
  - ADR findings.
- [ ] **Step 6: Commit** (only if asked): `docs(adr): record dws-jq 2j findings in ADR 0014`.
