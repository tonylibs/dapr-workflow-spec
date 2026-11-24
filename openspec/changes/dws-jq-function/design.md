## Context

ADR 0014 (Accepted 2026-11-24) makes `dws-jq` the single jq evaluator of DWS: one Go function per
expression site, built on gojq, deployed as a Dapr-enabled Knative Service serving plain HTTP. Today
only `dws-step` and v1 evaluate jq (jackson-jq 1.2.0). `dws-flow` and the future `dws-schedule-event`
have no engine, and roadmap rows 3b, 3c, 3f, 3g and 6b wait on this component (row 2j).

Constraints this design inherits:

- **ADR 0014 Decisions 1, 3, 4, 5, 6, 9 (O-2, O-5)**:
  - one expression per function, fixed at deploy;
  - `POST /run` returns 200, 400 or 422;
  - the builtin policy is enforced at startup on the AST;
  - one documented dialect, measured by a corpus;
  - a bad expression fails readiness;
  - combined template expressions are ordinary expressions;
  - `dws-jq` reads its own secrets and redacts them as `***`.
- **ADR 0012 Decision 9**: the block list (`env`, `$ENV`, `input`, `inputs`, `input_filename`,
  `import`, `include`, `halt`, `halt_error`, `debug`, `stderr`). `now` and the date and local-time
  functions stay allowed.
- **v1 semantics to match** (`dws-orchestrator` `JqEvaluator`):
  - Unwrap: trim; if the text starts with `${` and ends with `}`, take the inner text and trim it.
    There is no brace matching.
  - The result is the first output, or `null` when there is none.
  - Truthiness: `null` and `false` are false; everything else is true.
- **Packaging model**: `dws-call-http` (Go 1.26.9, Makefile, distroless Dockerfile, path-filtered
  workflow, release-please `simple` package). Its package style is `getenv` config, typed errors,
  stdlib tests, and logging only in `main.go`.
- **Log contract**: `docs/log-format.md` (`DWS_LOG_FORMAT`, six keys, scrubber, 12 golden cases). No
  Go package implements it yet.
- **Local toolchain**: Go 1.27.3 windows/386 with CGO off, so the race detector is unavailable
  locally. The Docker daemon is down and no cluster is reachable.

Stakeholders: the 2m controller work (renders this configuration), the 3b/3c/3f/3g/6b callers
(consume the HTTP contract), EX-1 (reuses the corpus), and 2.0d (consumes EX-3).

## Goals / Non-Goals

**Goals:**
- A `dws-jq` image that compiles and checks its one expression at startup, enforces the builtin
  policy, and serves the ADR 0014 Decision 3 contract with v1's first-result and truthiness rules.
- A documented, fixture-pinned configuration contract that the controller can render later.
- Static `$secrets.<name>` support through the Dapr secrets API, with `***` redaction in faults and
  logs. Dynamic access is rejected.
- `DWS_LOG_FORMAT` support, passing the 12 golden cases.
- The `conformance/expressions/` corpus with its schema, README, divergence lists and a `dws-jq`
  runner inside `make test`.
- CI, image and release wiring identical in shape to `dws-call-http`.
- EX-3 numbers, if a cluster is available.

**Non-Goals:**
- Any caller, controller, chart or access-policy change.
- A jackson-jq corpus runner (EX-1).
- A submit-time expression check (EX-2).
- Deciding which expressions may use `$secrets` and which sites are busy (2.0d follow-ups).
- Request batching, multiple expressions per function, or expression text in requests (ADR 0014
  rejects all three).

## Decisions

### D1: Plain-HTTP Go service, not a Dapr Workflow worker
- **Choice**: a `net/http` server with `POST /run` and `GET /healthz`. It has no workflow worker and
  no Dapr SDK.
- **Rationale**: the `listen` bridge and `dws-schedule-event` call by plain service invocation (ADR
  0014 Decision 2). `dws-flow` wraps the call in its own local activity.
- **Alternatives**: an `activity.Handler` worker like `dws-call-http`. This was rejected because
  non-workflow callers cannot dispatch activities.

### D2: Package layout
- **Choice**: `dws-jq/` with module `github.com/dws/dws-jq` and these parts:
  - `main.go`: wiring, logging, exit codes.
  - `internal/config`: env loader and validation.
  - `internal/expr`: unwrap, parse, policy walk, secret-reference scan, compile, evaluate, truthy,
    and JSON number handling.
  - `internal/secrets`: Dapr HTTP fetch and the redactor.
  - `internal/server`: handlers and the readiness gate.
  - `internal/logging`: `DWS_LOG_FORMAT` handler, scrubber, and `traceparent` context.
  - `internal/conformance`: the corpus runner test.

  Packages follow `dws-call-http`'s idioms: typed errors classified with `errors.As`, no testify,
  table-driven same-package tests, a narrow interface at each consumer boundary with fakes in tests,
  and logging only in `main.go` and the server's request logger.
- **Rationale**: this mirrors the existing Go images, and keeps the corpus runner on the same
  `expr` path that `/run` uses.

### D3: Configuration contract (env vars, `JQ_` prefix)

| Name | Required | Default | Meaning |
|---|---|---|---|
| `JQ_EXPRESSION` | yes | — | The expression: `${ … }` or bare jq |
| `JQ_VARIABLES` | no | `[]` | JSON array of variable names, without the `$` |
| `JQ_TIMEOUT` | no | `5s` | Per-evaluation limit; a Go duration > 0 |
| `JQ_SECRET_STORE` | when the expression uses `$secrets` | — | Dapr secret store component name |
| `JQ_SITE` | no | `dws-jq` | Site ID `<owner>.jq.<site>`; used for logs and `/healthz` only |
| `PORT` | no | `8080` | Listen port |
| `DAPR_HTTP_PORT` | no | `3500` | Sidecar HTTP port (injected by Dapr) |
| `DAPR_API_TOKEN` | no | — | Sent as `dapr-api-token` on secret reads |
| `DWS_LOG_FORMAT` | no | text | Shared log contract |

- **Validation**:
  - A variable name must match `^[A-Za-z_][A-Za-z0-9_]*$`.
  - Names must be unique.
  - The names `secrets`, `ENV` and `__loc__` are not allowed.
  - `JQ_SECRET_STORE` is required when the expression references `$secrets`. If it is set but
    unused, startup logs one `WARN` and continues.
- **Pinning**:
  - Fixtures `dws-jq/testdata/contract/env-*.json` hold env maps for three cases: minimal; variables
    with a timeout; secrets.
  - `TestConfigContract` loads each fixture and asserts the parsed config.
  - The same test asserts that the set of names `config` reads equals the union of fixture keys
    (`config.Names`). Adding a name without a fixture, or a fixture key that `config` does not read,
    fails the test.
  - 2m.2's controller test is expected to compare its rendering with these fixtures, as
    `SchedulerContractTest` does for the scheduler.
  - A root `CLAUDE.md` contract bullet documents the pairing.
- **Alternatives**:
  - A mounted JSON config file was rejected: it costs an extra ConfigMap per site.
  - Unprefixed names were rejected: `SECRET_STORE` reads like v1's `SECRET_<NAME>` convention, and
    `VARIABLES`/`SITE` are too generic.

### D4: Startup sequence and readiness
1. Load the config, unwrap the expression, and parse it with `gojq.Parse`.
2. Walk the AST for blocked builtins and `$secrets` access (D5, D6).
3. Compile with `gojq.WithVariables` (the declared names plus `$secrets`), and with no environ, input
   or module loader. A reference to an undeclared variable fails compilation here.
4. On any failure in steps 1–3, write one structured `ERROR` record with the site, the expression
   text and the reason, then exit 1 without opening a listener.
5. Open the listener. Until the function is ready, `/healthz` returns
   `503 {"status":"starting","site":…}` and `/run` returns `503`.
6. If secrets are referenced, fetch each one (D7), retrying with backoff for up to 60 s in total. If
   that fails, write one `ERROR` naming the store and the secret names (never values), then exit 1.
7. Become ready. `/healthz` returns `200 {"status":"ok","site":…}`.
- **Rationale**: daprd waits for the app port, so the listener must open before the sidecar
  dependency. The readiness gate keeps Knative from routing to an instance that cannot evaluate yet.
- **Alternative**: fetching secrets lazily on the first request. Rejected: a missing secret would
  surface as a runtime fault instead of a failed deploy, against ADR 0014 Decision 6.

### D5: Builtin policy by AST walk
- **Choice**: walk the whole `*gojq.Query`: terms, suffixes, function definitions, bindings, object
  keys and values, `reduce`/`foreach`/`if`/`try`/`label` bodies, and imports.
  - **Reject**:
    - a function call named `env`, `input`, `inputs`, `input_filename`, `halt`, `halt_error`,
      `debug` or `stderr`, at any arity;
    - the variable `$ENV`;
    - any `import` or `include`.
  - **Error message**: `blocked builtin "<name>"`, or `"$ENV"`, `"import"`, `"include"`.
  - **Not rejected**: field access (`.env`), object keys (`{env: 1}`) and strings.
  - **User definitions**: a `def debug:` is rejected too. The check is by name, which is
    conservative, and the README documents it.
  - **Second guard**: no `WithEnvironLoader`, `WithInputIter` or `WithModuleLoader`.
- **Rationale**: ADR 0014 Decision 4 requires a startup rejection that names the builtin.
- **Alternatives**: overriding the builtins with `WithFunction` stubs that error when called.
  Rejected: an unused branch would pass startup, and the failure would happen at runtime.

### D6: `$secrets` access: static only (answers the 2.0d dynamic-access follow-up)
- **Choice**:
  - **Allowed**: `$secrets.<ident>`, `$secrets."<string>"`, and `$secrets["<string literal>"]`,
    each optionally followed by more suffixes. The bracket form is needed for DNS-1123 names with
    hyphens (`workflow-secrets` spec).
  - **Rejected at startup**: every other use, with a reason naming the form:
    - bare `$secrets` (`$secrets as $s`, `$secrets | keys`, `$secrets | tojson`);
    - non-literal indexes (`$secrets[.k]`, `$secrets[$k]`, `$secrets["a" + .x]`);
    - iteration (`$secrets[]`) and slices.
  - The referenced names form the exact set to fetch.
- **Rationale**: O-5 promises each function reads "exactly those names". Allowing dynamic access
  would mean granting every `use.secrets` name to the function, and would make the controller's
  static scan (2m.1) unsound. A computed choice can still be written statically:
  `if … then $secrets.a else $secrets.b end`.
- **Alternatives**: granting all of `use.secrets` for dynamic access. Rejected: it widens the blast
  radius and breaks the scope scan.

### D7: Secret retrieval over the Dapr HTTP API
- **Choice**:
  - Request: `GET http://127.0.0.1:${DAPR_HTTP_PORT}/v1.0/secrets/{store}/{name}`, with a
    `dapr-api-token` header when `DAPR_API_TOKEN` is set.
  - Bound value: the response's `value` key, as a string. This matches v1 (Kubernetes Secret key
    `value`).
  - A non-2xx response, a missing `value`, or a timeout fails startup, naming the secret.
- **Rationale**: this is the same Dapr secrets API without the gRPC dependency tree. An expression
  without `$secrets` never contacts the sidecar.
- **Alternatives**: `dapr/go-sdk` `GetSecret`. Rejected: it pulls in gRPC for one call.

### D8: `/run` semantics
- **Request**:
  - The body is `{"input": any, "variables": object}`. Both fields are optional; an absent `input`
    is `null`.
  - The body is limited to 4 MiB, Dapr's default maximum request body.
  - These return `400 {"error":"…"}`: invalid JSON, a non-object body, a non-object `variables`,
    unknown top-level fields, a body over the limit, and an undeclared variable name (`secrets`
    included).
- **Binding**:
  - `input` is bound to `.`, and each declared variable to `$name`, in `JQ_VARIABLES` order. A
    declared variable missing from the request is `null`.
  - `$secrets` is bound to the startup-loaded map.
  - Numbers are decoded with `UseNumber` and converted to `int`, to `*big.Int` beyond 64 bits, or to
    `float64` for non-integers, so integer precision survives.
- **Evaluation**:
  - Run `Code.RunWithContext` under a `JQ_TIMEOUT` deadline, read the first `Next()` only, and stop.
  - No output gives `result: null`.
  - These return 422:
    - the first output is an error;
    - the deadline is exceeded: detail `evaluation exceeded the <timeout> time limit`;
    - a result that cannot be encoded.
  - `truthy = !(result == nil || result == false)`.
  - Encoding follows jq's output rules: `nan` → `null`, `±infinite` → `±1.7976931348623157e+308`.
- **Response**:
  - `200 {"result": …, "truthy": …}`.
  - `422 {"fault": {"type":"expression","expression":"<JQ_EXPRESSION as configured>","detail":"…"}}`.
  - The detail has secret values replaced by `***`.
- **Rationale**: ADR 0014 Decision 3 and v1 parity. Rejecting unknown fields catches caller drift,
  for example a caller sending `expression`.
- **Alternatives**: ignoring unknown fields. Rejected: silent drift would hide caller bugs.

### D9: Redaction
- **Choice**: a `Redactor` built once from the loaded secret values. It drops empty values, matches
  longest first, and covers both the raw value and its JSON-escaped form without quotes. It is
  applied to:
  - fault `detail`;
  - every log `message` and `exception.*`, in both text and JSON mode.

  The `result` is not redacted. Returning a secret is the expression author's explicit choice, as in
  v1 `set`. Which expressions may reference `$secrets` at all stays a 2.0d follow-up.
- **Rationale**: O-5 asks for redaction of every value the function holds. The log scrubber alone
  cannot recognise unlabelled values.

### D10: Logging
- **Choice**: `internal/logging.New(os.Stdout, os.Getenv("DWS_LOG_FORMAT"), redactor)` returns an
  `slog.Logger`.
  - **JSON mode**: `json`, trimmed and case-insensitive. Each record is one object with
    `timestamp` (UTC, millisecond precision, `Z`), `severity`, `logger` (`dws-jq` or a sub-name),
    `message`, and optionally `trace_id`/`span_id` and `exception.type`/`.message`/`.stacktrace`.
    - Scrubber: the two `docs/log-format.md` regexes, prefixed with `(?i)` and using `${1}${2}`
      replacements, are applied after the `***` redaction.
    - Severity: a custom `LevelFatal` above `ERROR` maps to `FATAL`.
    - Errors: an `error` attribute becomes `exception.type` (`%T`), `exception.message`, and
      `exception.stacktrace` (`"<type>: <message>\n"` followed by the logging goroutine's stack).
    - Trace context: the server stores the inbound W3C `traceparent` in the request context, and the
      handler emits the IDs only when they are valid and non-zero.
  - **Text mode**: anything else gives `slog.TextHandler`, with redaction still applied.
  - `TestLogFormatContract` runs the 12 golden cases.
  - Per-request logging: one `INFO` line per 422 and one `WARN` line per 400, each with the site and
    the detail. No line for a success, and no payloads.
- **Rationale**: the shared contract; `dws-jq` has no OTel SDK, and Dapr forwards `traceparent`.
- **Alternatives**:
  - Adding the OTel SDK only to read the span. Rejected as needless weight.
  - Always writing JSON, as `dws-call-http` does. Rejected because the contract requires the switch.

### D11: Expression corpus `conformance/expressions/`
- **Layout**:
  - `README.md`;
  - `case.schema.json` (JSON Schema 2020-12);
  - `cases/<area>/<name>.yaml`;
  - `divergences/dws-jq.yaml` (`runtime: dws-jq (gojq)`). The jackson-jq list arrives with EX-1.
- **Case fields**:
  - `name` (`<area>/<file>`), `test` (`givenX_whenY_thenZ`), `area`, `summary`, `source`;
  - optional `engine`;
  - `expression` (string), `input` (any), optional `variables` (object);
  - `expect`: either `{result, truthy}` or `{fault: {phase: compile|evaluate, detailContains: [...]}}`.
- **Areas and seed set**:
  - `contract`:
    - `${ }` unwrap;
    - bare jq;
    - no output → `null` / false;
    - several outputs → first;
    - `false`, `null`, `0`, `""` and `[]` truthiness;
    - an evaluation error → evaluate fault.
  - `builtins`: the 13 data-flow cases, carried over with the same names.
    - The 11 blocked cases become compile faults whose detail contains the builtin.
    - `clock-now` and `clock-local-time` keep their expected values, which were computed with jq 1.7.
    - In each case, the expression is the jq inside the original stage.
  - `dialect`: one case per ADR 0014 Decision 5 row:
    - key order (`tojson` of an object);
    - `keys_unsorted` (compile fault);
    - date functions;
    - integer precision;
    - string indexing;
    - regex look-around (fault);
    - `$__loc__` (compile fault).
  - `loops`: v1's `for.in` and `while` expressions, with their inputs and variables.
  - `catch`: v1's `when` / `exceptWhen` / `as: oops` / `retry.when` expressions, run against the 503
    error object.
  - `templates`: the O-2 combined forms:
    - the ADR example;
    - a part using `def`;
    - a part using `as $x`;
    - an `empty` part;
    - a part with several results.
  - **False-positive guards** for the policy walk, in the `builtins` area: `.env`, `{debug: 1}`, and
    `"input"`.
- **Expectation source**:
  - Dialect rows follow the dialect ADR 0014 Decision 5 declares (gojq minus Decision 9), checked
    against gojq's documentation.
  - Builtin rows keep the data-flow corpus's values.
  - A disagreement goes into a divergence list; an expectation is never edited to match a runtime.
- **Runner**: `internal/conformance`, a Go test.
  1. Load `../../conformance/expressions/cases/**/*.yaml`. `DWS_CONFORMANCE_EXPRESSIONS` overrides
     the location.
  2. Validate each case against the schema (`santhosh-tekuri/jsonschema/v6`), and check
     `name`/`area`/path and that `test` is unique.
  3. Run the same `expr.Compile` + `Evaluate` path as `/run`, with `TZ=UTC`.
  4. Compare numbers by value. A compile fault must come from `Compile`; an evaluate fault from
     `Evaluate`.
  5. Apply the verdicts: pass, fail, known divergence, and stale divergence, which fails the run.
  6. Log one line per case, then the totals.
- **Rationale**: the same shape as `conformance/data-flow/`, so EX-1's jackson-jq runner follows the
  same README.

### D12: Packaging and release
- **Files**:
  - `Makefile`, the same targets as `dws-call-http`;
  - `Dockerfile`: `golang:1.26.9` → `gcr.io/distroless/static:nonroot`, `EXPOSE 8080`;
  - `.dockerignore`, `.gitignore`, `version.txt`, `README.md`, `CLAUDE.md`;
  - `k8s/knative-service.yaml`: an example with `minScale: "0"`, Dapr annotations, and a
    `/healthz` readiness probe.
- **CI**: `.github/workflows/dws-jq.yml`.
  - Triggers: push to main, the `dws-jq-v*` tag, PRs, and `workflow_dispatch`.
  - Path filters: `dws-jq/**`, `conformance/expressions/**`, and the workflow file.
  - Test job: vet, gofmt, build, and `go test -race ./...`. The corpus lives outside the Docker
    context, so it runs here, not in the image.
  - Image job: build, smoke-test `/healthz` with `JQ_EXPRESSION='.'`, and push off-PR.
- **Release**:
  - `release-please-config.json` package: `simple`, `version.txt`, `initial-version: "0.1.0"`,
    `skip-github-release`.
  - Manifest `0.0.0`, following the `dws-mid-*` / `dws-schedule-time` path.
  - `component-release.yml` option.
  - `docs/release-process.md` rows.
- **Chart**: `dws-jq` is not pinned. The controller deploys it per site (2m.2), as it does
  `dws-step` and `dws-flow`.

### D13: EX-3 procedure
- **When a cluster is reachable** (Knative Serving + Dapr; `docker-desktop` or kind):
  1. Load the image and apply the example Knative Service with `minScale: "0"`.
  2. Invoke it from a curl pod through its own Dapr sidecar:
     `POST http://localhost:3500/v1.0/invoke/<app-id>/method/run`.
  3. Cold start: wait for scale to zero, then time the first call. Take 5 samples.
  4. Warm: p50/p95 over 200 sequential calls.
  5. Record the numbers, the environment and the method in `dws-jq/README.md` and in ADR 0014's
     EX-3 row.
- **Otherwise**: record "not measured: no cluster available on 2026-11-24" and leave EX-3 open.

### D14: Doc updates
- **Root `CLAUDE.md`**:
  - package map row;
  - gate row `make lint && make test`;
  - a new contract bullet "**`dws-jq` configuration contract** (controller 2m.2 → `dws-jq`)";
  - `dws-jq` added to the structured-log bullet's package list.
- **`docs/log-format.md`**:
  - package list;
  - a severity row (Go `slog`: `LevelFatal`→`FATAL`, `Error`→`ERROR`, `Warn`→`WARN`, `Info`→`INFO`,
    `Debug`→`DEBUG`);
  - a trace-source row (inbound `traceparent`).
- **ADR 0014**, without changing any decision:
  - a dated "2j notes" block confirming the gojq-side "to confirm" cells the corpus proved;
  - O-5 dynamic access: rejected;
  - EX-3 status.
- **Roadmap row 2j**: status.

## Risks / Trade-offs

- [Risk] The race detector cannot run on the local windows/386 toolchain → Mitigation: run
  `go vet`, the gofmt check and `go test ./...` locally, state the gap in the verify report, and let
  CI (linux/amd64) run `go test -race` as the real gate.
- [Risk] Docker and the cluster are unavailable, so the image build and EX-3 cannot run locally →
  Mitigation: the CI image job builds and smoke-tests on every PR. The image build is re-attempted
  locally if Docker comes up. EX-3 is recorded as not measured rather than guessed.
- [Risk] gojq's AST types are public but carry no stability promise → Mitigation: pin v0.12.20. The
  policy walker is covered by every blocked builtin, the false-positive guards, and nested positions
  (inside `def`, `reduce`, `try`, object values, string interpolation).
- [Risk] Name-based rejection also blocks a user `def debug:` → accepted: it is conservative and
  documented, and keeps the walk simple and sound.
- [Risk] The startup secret fetch depends on sidecar timing → Mitigation: the listener opens first,
  readiness is gated, retries are bounded to 60 s, and failure exits with a clear record so the
  revision fails.
- [Trade-off] Dynamic `$secrets` access is rejected → accepted: static alternatives exist, and
  least privilege holds. This answers the 2.0d follow-up and is recorded in ADR 0014.
- [Trade-off] Results are not redacted, so an expression can return a secret into the caller's
  history → accepted for v1 parity. The scope question stays open in 2.0d.
- [Trade-off] Corpus dialect expectations follow gojq → accepted: ADR 0014 Decision 5 names gojq's
  dialect as the reference. jackson-jq differences will show up as EX-1 divergences.
- [Risk] The log-format contract gains its first Go implementation, and the Go regex syntax differs
  slightly from the others → Mitigation: the 12 golden cases run in `make test`.
- [Risk] The 4 MiB body cap could reject a legitimate large `for.in` input → accepted: Dapr's default
  invocation limit already rejects such bodies upstream. A larger limit is a future config knob.

## Migration Plan

There is no deployed behavior to migrate: no caller and no controller emission exist yet.
1. Merge the change with a non-releasable PR title (`ci`/`docs`/`chore`) or a `feat(jq)` commit. Its
   manifest entry is `0.0.0` with `initial-version: 0.1.0`, so release-please opens a
   `chore(release): dws-jq 0.1.0` PR.
2. Merge that release PR (cut ①). Then run `component-release` for `dws-jq` without `force` (cut ②)
   to publish `ghcr.io/tonylibs/dws-jq:0.1.0`, and make the GHCR package public once.
3. Rollback: revert the change. Nothing depends on the image yet.

Acceptance for this change:
- the package gate passes, including the corpus runner, the contract fixtures and the log golden
  cases;
- the blocked builtin cases fault naming the builtin;
- the combined-template cases pass;
- the image builds, in CI at least.

## Open Questions

- Which expressions may reference `$secrets` (v1's `set`/`switch` set, OWS `input.from`, or both)
  remains an open 2.0d follow-up. `dws-jq` itself allows `$secrets` wherever it is configured; the
  controller decides.
- Which sites count as busy (`min-scale: 1`) is decided by 2.0d from the EX-3 numbers, which may not
  exist yet.
- Whether gojq's `gmtime` / `mktime` values match the jq 1.7-computed `builtins/clock-local-time`
  expectations exactly. The corpus run will show this; a mismatch becomes a listed `dws-jq`
  divergence, not an edited expectation.
