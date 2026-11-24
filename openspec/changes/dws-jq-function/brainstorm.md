# Brainstorm: `dws-jq` expression function (roadmap 2j)

Raw capture of the brainstorming pass for this change. The request was a detailed handoff
("Phase 2j, `dws-jq` expression function") that settles the what and most of the how, and hands
the remaining calls to the builder ("choose the configuration names", "decide whether dynamic
access is rejected"). Nothing was left whose answer only the user could give, so the calls below
are proposals made from the ADRs and the code, marked **[call]**, for review with the artifacts.

## Background (what exists today)

- **ADR 0014 (Accepted 2026-11-24)** decides one Go `dws-jq` function per expression site, built on
  gojq, deployed as a Dapr-enabled Knative Service serving plain HTTP. Decisions that bind this
  change: 1 (one function, expression fixed at deploy), 3 (`POST /run` contract, 200/400/422),
  4 (builtin policy enforced at startup by walking the AST), 5 (two engines, one documented dialect,
  `conformance/expressions/` corpus), 6 (bad expression fails startup), 9 with O-2 (combined
  template expressions) and O-5 (`dws-jq` reads its own secrets, redacts them as `***`).
- **ADR 0012 Decision 9** blocks `env`, `$ENV`, `input`, `inputs`, `input_filename`, `import`,
  `include`, `halt`, `halt_error`, `debug`, `stderr`; allows `now` and the date/local-time functions.
- **`conformance/data-flow/`** is the model corpus: `case.schema.json`, `cases/<area>/*.yaml`, one
  case per file, `divergences/<runtime>.yaml`, verdicts pass / fail / known divergence / stale
  divergence (stale fails the run). Its `cases/builtins/` holds 13 cases: 11 blocked builtins
  (`debug`, `env`, `env-variable`, `halt`, `halt-error`, `import`, `include`, `input`,
  `input-filename`, `inputs`, `stderr`) and 2 allowed (`clock-now`, `clock-local-time`).
- **v1 (`dws-orchestrator`, jackson-jq 1.2.0)** facts the contract must match:
  - Unwrap (`JqEvaluator.java:145-154`): trim; if it starts with `${` and ends with `}`, take the
    inside and trim again. No brace matching. Otherwise the text is bare jq.
  - First output, or `null` when there is none (`JqEvaluator.java:48-51`).
  - Truthiness: `null` or no output is false, a boolean is itself, anything else true
    (`JqEvaluator.java:113-123`).
  - Loop variables default to `item` / `index` (0-based); catch variable defaults to `error`, shaped
    `{type, status, instance, title, detail}`. `$context` is bound for data flow and catch
    conditions; `$secrets` only for `set` / `switch`, from `SECRET_<name>` env (scalar strings,
    Kubernetes Secret key `value`, per `openspec/specs/workflow-secrets`).
  - Test expressions to seed the corpus (all in `dws-orchestrator/src/test`):
    - `for.in`: `.pets`, `${ .pets }`, `$sample`, `.count` (not an array → v1 fails the loop;
      for `dws-jq` it is just a value), `[1, 2]`.
    - `while`: `.count > 0` (true), `.flag` with `false` / `null` / missing (all false),
      `$item < 3` with item 2 (true) and 5 (false), `$i < 2`.
    - catch: `$error.status == 429`, `$error.status == 503` (exceptWhen), `$oops.status == 503`
      (`as: oops`), `retry.when: $error.status == 429`, against the 503 error object above.
- **`dws-call-http`** is the packaging model: Go 1.26.9, `Makefile` (build/test -race/vet/fmt-check/
  lint/docker), distroless static nonroot Dockerfile on `golang:1.26.9`, path-filtered workflow
  `dws-call-http.yml` (vet, gofmt, build, `go test -race`, image build, push off-PR), release-please
  `simple` package with `version.txt`, `component-release.yml` option. Style: hand-rolled `getenv`
  config, typed errors with `%w`, no testify, table-driven same-package tests, logging only in
  `main.go`.
- **Release path for a new component** that arrives with `feat` history: the `dws-mid-*` /
  `dws-schedule-time` route (manifest `0.0.0`, `"initial-version": "0.1.0"`), not seed-and-force.
- **`docs/log-format.md`** is a contract with 12 golden cases and a two-regex scrubber; each package
  carries its own formatter. No Go package implements it yet (`dws-call-http` always writes slog
  JSON with its own keys). Root `CLAUDE.md` lists six packages.
- **gojq** latest is v0.12.20 (2026-11-01). Its README confirms: object keys always sorted, no
  `keys_unsorted`; arbitrary-precision integers for `+ - * %` and exact division; string indexing
  supported (`"abcde"[2]`); `input_line_number`, `$__loc__`, `get_*_origin`, `get_search_list`
  unsupported; no look-around / backreferences in regex; modules, `env`/`$ENV` (empty by default)
  and `input`/`inputs` are controlled by `WithModuleLoader`, `WithEnvironLoader`, `WithInputIter`;
  `WithVariables` declares `$name`s; `Code.RunWithContext` bounds execution time.
- **Local environment** at brainstorm time: Go 1.27.3 **windows/386, CGO off** (so `go test -race`
  cannot run locally), Docker daemon not running, `docker-desktop` kube context unreachable.

## Decision log

**Q1. Where does `dws-jq` live and what does it look like?**
A new top-level package `dws-jq/`, Go 1.26.9 like `dws-call-http`, module `github.com/dws/dws-jq`,
gojq pinned at v0.12.20. Layout: `main.go` plus `internal/config`, `internal/expr` (unwrap, compile,
policy walk, secret-reference scan, evaluate), `internal/secrets` (Dapr fetch, redaction),
`internal/server` (`/run`, `/healthz`), `internal/logging` (`DWS_LOG_FORMAT`). Unlike
`dws-call-http`, there is no Dapr Workflow worker: `dws-jq` serves real HTTP (`POST /run`), because
the `listen` bridge and `dws-schedule-event` call it by plain service invocation (ADR 0014
Decision 2).

**Q2. Configuration names (the contract the controller will render later).** **[call]**
Environment variables, prefixed `JQ_` so they cannot be mistaken for v1's `SECRET_<NAME>` convention:

| Name | Required | Meaning |
|---|---|---|
| `JQ_EXPRESSION` | yes | The expression, `${ … }` or bare jq |
| `JQ_VARIABLES` | no, default `[]` | JSON array of variable names without `$` (e.g. `["context","item","index"]`) |
| `JQ_TIMEOUT` | no, default `5s` | Per-evaluation limit, Go duration, > 0 |
| `JQ_SECRET_STORE` | when the expression uses `$secrets` | Dapr secret store component name |
| `JQ_SITE` | no, default `dws-jq` | The site ID (`<owner>.jq.<site>`), for logs and `/healthz` only |
| `PORT` | no, default `8080` | Listen port, as every image |
| `DAPR_HTTP_PORT` | no, default `3500` | Injected by Dapr |
| `DAPR_API_TOKEN` | no | Dapr's own name; sent as `dapr-api-token` when set |
| `DWS_LOG_FORMAT` | no | Shared log contract |

Variable names must be jq identifiers (`^[A-Za-z_][A-Za-z0-9_]*$`), unique, and not `secrets`,
`ENV` or `__loc__`. Pinned like the scheduler contract: fixtures under
`dws-jq/testdata/contract/*.json` (env maps) that the package's tests load, plus a test that every
name `config` reads appears in a fixture and every fixture key is a name `config` reads. 2m.2's
controller test will compare its rendering with the same fixtures. Documented in `dws-jq/README.md`
and as a cross-package contract bullet in root `CLAUDE.md`.
Alternatives: a mounted JSON file (needs a ConfigMap per site, more moving parts for the controller);
unprefixed `EXPRESSION`/`VARIABLES` (too generic, `SECRET_STORE` collides with v1's convention).

**Q3. Startup order and readiness.** **[call]**
1. Load config; unwrap; parse; walk AST for blocked builtins and `$secrets` references; compile with
   `WithVariables` (declared names plus `$secrets`). Any failure: one structured `ERROR` record
   naming the site, the expression and the reason, exit 1. No listener was opened.
2. Open the HTTP listener. `/healthz` answers `503 {"status":"starting"}` until step 3 completes.
3. If the expression references secrets, fetch them (bounded retry, 60 s total, because the sidecar
   may start after the app). Failure: one structured error naming the secret names (never values),
   exit 1.
4. Mark ready: `/healthz` answers `200 {"status":"ok","site":…}`; `/run` is served (before that,
   `/run` answers `503`).
The listener opens before the secret fetch so daprd, which waits for the app port, can come up.

**Q4. Builtin policy detection.**
Walk the parsed gojq AST (`*gojq.Query` and its terms, function definitions, imports) and reject:
any call to `env`, `input`, `inputs`, `input_filename`, `halt`, `halt_error`, `debug`, `stderr` at any
arity; any reference to the variable `$ENV`; any `import` or `include` directive. The error names the
builtin (`blocked builtin "env"`). Only function calls and variable references count: `.env`,
`{env: 1}` and `"env"` stay allowed. A user `def debug: .;` is rejected too (name-based, conservative;
documented). Second guard: compile with no `WithEnvironLoader`, `WithInputIter` or
`WithModuleLoader`. `now`, `todate`, `mktime`, `strftime`, `localtime`, … stay allowed.
`$__loc__`, `input_line_number`, `get_*_origin`, `keys_unsorted` fail compilation on their own (gojq
does not implement them), which is the documented dialect.

**Q5. Dynamic `$secrets` access (2.0d open follow-up).** **[call] Reject.**
Allowed: `$secrets.name`, `$secrets."name"`, `$secrets["name-with-hyphen"]` (string-literal index,
needed for DNS-1123 names per `workflow-secrets`), optionally followed by further suffixes. Rejected
at startup: bare `$secrets` (including `$secrets as $s`, `$secrets | keys`), any non-literal index
(`$secrets[.key]`, `$secrets[$k]`), `$secrets[]`, slices. Reason: O-5's least-privilege promise is
"exactly those names"; granting every `use.secrets` name to a function for one dynamic lookup widens
every such function's access and makes the controller's scope scan meaningless. A workflow that needs
a computed choice can write the alternatives statically (`if .env == "prod" then $secrets.a else
$secrets.b end`). The rejection names the form. Recorded in the design, the README, and as a 2j note
on ADR 0014 O-5.

**Q6. Secret retrieval and value shape.** **[call]**
Plain HTTP to the sidecar: `GET http://127.0.0.1:${DAPR_HTTP_PORT}/v1.0/secrets/{store}/{name}`, with
`dapr-api-token` when `DAPR_API_TOKEN` is set. The value bound is the response's `value` key, a
string, matching v1 (`use.secrets` names a Kubernetes Secret, key `value`). A missing secret or
missing `value` key fails startup naming the secret. Alternative: the Dapr Go SDK `GetSecret` —
rejected for this package because it pulls the gRPC stack into an image that otherwise needs none,
and the HTTP API is the same Dapr secrets API. An expression without `$secrets` never talks to the
sidecar, so it runs without Dapr (tests, local runs).

**Q7. `/run` contract details.**
- Body `{ "input": any, "variables": object }`, both optional (missing `input` → `null`). Not JSON,
  not an object, a non-object `variables`, unknown top-level fields **[call: rejected]**, or an
  undeclared variable name (including `secrets`) → `400 {"error": "<reason>"}`. Body capped at
  4 MiB **[call]** (Dapr's default max request body) → `400` above it.
- Declared but absent variable → `null`.
- Numbers decode preserving integers (int, or `*big.Int` beyond 64 bits) so gojq's arbitrary
  precision applies to request values too.
- Evaluate with `context.WithTimeout(JQ_TIMEOUT)`, take the first output only, stop the iterator.
  No output → `result: null`. First output is an error → `422`. Deadline → `422` with
  `detail: "evaluation exceeded the 5s time limit"`.
- `200 {"result": …, "truthy": …}`; `truthy` false only for `null` and `false`.
- Results that JSON cannot carry follow jq's output rules: `nan` → `null`, `±infinite` →
  `±1.7976931348623157e+308`.
- `422 {"fault": {"type": "expression", "expression": "<configured text>", "detail": "<reason>"}}`
  with secret values replaced by `***` (raw and JSON-escaped forms, longest first) in `detail`.
- The result itself is not redacted: returning a secret is the expression author's choice, as in v1
  `set`. Which expressions may use `$secrets` at all is the other open 2.0d follow-up; not decided here.

**Q8. Logging.**
`internal/logging` builds an `slog.Handler`: `DWS_LOG_FORMAT=json` (trimmed, case-insensitive) → the
contract object (`timestamp` RFC 3339 UTC ms, `severity`, `logger`, `message`, `trace_id`/`span_id`,
`exception.*`), with the two scrubber regexes (RE2-compatible, `(?i)`) applied to `message` and
`exception.*`, plus the `***` secret redaction. Anything else → `slog.TextHandler` (text mode is
what a new package writes). Go specifics **[call]**: a `FATAL` level above `ERROR`; an `error`
attribute becomes `exception.type` (`%T`), `exception.message`, `exception.stacktrace` (`"<type>:
<message>\n"` plus the logging goroutine's stack). Trace context **[call]**: `dws-jq` has no OTel
SDK; request-scoped records carry the IDs from the inbound W3C `traceparent` header that Dapr
forwards, omitted when absent or all-zero. The 12 golden cases run as `TestLogFormatContract`.
`dws-jq` added to `docs/log-format.md` (package list, severity table, trace-source table) and to the
root `CLAUDE.md` log-format bullet. Request logs: one `INFO` per evaluation failure (site, detail,
redacted), nothing per success, no payloads.

**Q9. Combined template expressions (O-2).**
Nothing special in `dws-jq`: they are expressions. Corpus cases prove it: a part with `def`, a part
with `as $x`, a part that is `empty` (→ `null` via `([ empty ] | .[0])`), a part with several results
(→ first), plus the ADR's own example `{ "id": ([ .id ] | .[0]), "source": "api" }`.

**Q10. Expression corpus shape.**
`conformance/expressions/` mirrors `conformance/data-flow/`:
- `case.schema.json` (2020-12). Fields: `name` (`<area>/<file>`), `test` (`givenX_whenY_thenZ`),
  `area`, `summary`, `source`, optional `engine`, `expression`, `input`, optional `variables` (object),
  `expect` = `{result, truthy}` or `{fault: {phase: compile|evaluate, detailContains: [..]}}`.
- Areas: `contract` (unwrap, first output, no output, truthiness), `builtins` (the 13),
  `dialect` (one per ADR 0014 Decision 5 row), `loops` (`for.in`, `while`), `catch`, `templates`.
- `README.md` with the runner rules (load, validate against the schema, check `name`/`area`/path and
  unique `test`, bind, compare with numbers by value, one verdict per case, stale divergence fails).
- `divergences/dws-jq.yaml` (runtime: `dws-jq (gojq)`), empty unless the run proves otherwise.
  `jackson-jq` gets its own file in EX-1, not here.
- **[call] Expectation source for dialect rows:** the dialect ADR 0014 Decision 5 names (gojq's,
  minus Decision 9), so expectations follow gojq's documented behavior, e.g. `{"b":1,"a":2} |
  tojson` → `{"a":2,"b":1}`; `keys_unsorted` → compile fault; `"abcde"[2]` → `"c"`;
  `9007199254740993 + 1` → `9007199254740994`; a look-around regex → compile/evaluate fault;
  `$__loc__` → compile fault. The jackson-jq runner (EX-1) will list where jackson-jq differs.
  Builtin cases keep the data-flow corpus's expected values (computed with jq 1.7); blocked ones
  become compile faults whose detail contains the builtin name.
- Runner: a Go test in `dws-jq` that runs every case through the same compile-and-evaluate path as
  `/run` (in-process, no HTTP), reports one line per case and totals. It reads
  `../conformance/expressions` (overridable by `DWS_CONFORMANCE_EXPRESSIONS`). Schema validation via
  `santhosh-tekuri/jsonschema/v6` and YAML via `gopkg.in/yaml.v3`, test-only **[call]**.
  `localtime` and `now` are checked by type only; the runner pins `TZ=UTC`.

**Q11. EX-3 measurements.**
Need Docker plus a cluster with Knative Serving and Dapr. Neither is reachable now (Docker daemon
down, `docker-desktop` context refuses). The plan keeps a conditional task: if the cluster comes up,
deploy the example Knative Service (scale to zero), measure cold start (first invocation after
scale-down, through Dapr service invocation) and warm latency (p50/p95 over N calls), record in the
package README and the ADR's EX-3 row; otherwise record "not measured: no cluster" and leave EX-3 open.

**Q12. Packaging.**
Mirror `dws-call-http`: `Makefile`, `Dockerfile` (`golang:1.26.9` → `distroless/static:nonroot`,
port 8080), `.dockerignore`, `.gitignore`, `version.txt`, `k8s/knative-service.yaml` example,
`CLAUDE.md`, `README.md`, `.github/workflows/dws-jq.yml` (paths `dws-jq/**`,
`conformance/expressions/**`, the workflow file; vet, gofmt, build, `go test -race`, image build,
`/healthz` smoke test, push off-PR), release-please package (`simple`, `version.txt`,
`initial-version: 0.1.0`, manifest `0.0.0`), `component-release.yml` option, `docs/release-process.md`
rows. Root `CLAUDE.md`: package map row, gate row (`make lint && make test`), contract bullets
(configuration contract; log format list). Not added to the Helm chart: the controller deploys it
per site (2m.2), like `dws-step` / `dws-flow`.

## Approaches considered

1. **Plain-HTTP Go service, expression in env, AST policy walk (chosen).** Matches ADR 0014 exactly,
   smallest surface, no Dapr dependency unless secrets are used.
2. **Dapr Workflow activity worker like `dws-call-http`.** Rejected: the `listen` bridge and
   `dws-schedule-event` are not workflows and call by service invocation (ADR 0014 Decision 2);
   `dws-flow` wraps the call in its own local activity anyway.
3. **Policy by deny-list at runtime (custom `env` etc. that error when called).** Rejected: ADR 0014
   Decision 4 requires rejection at startup naming the builtin; runtime errors would let a bad
   expression pass readiness.

## Risks noted

- `go test -race` cannot run on the local windows/386 toolchain; the gate runs fully in CI (linux/
  amd64). Locally, run `go test ./...` and say so.
- Image build and EX-3 need Docker; currently down.
- gojq AST types are public but not a stability promise; pinning v0.12.20 and testing the walker
  against every blocked builtin (the 13 corpus cases plus false-positive guards) contains it.
- `docs/log-format.md` grows a seventh, first Go implementation; the regexes are RE2-compatible as
  written (only `(?i)` and the `${1}` replacement syntax differ).
- `openspec/specs/structured-log-correlation` already lags (`dws-schedule-time` missing); this change
  states the `dws-jq` log requirement in its own spec instead of rewriting that one.
