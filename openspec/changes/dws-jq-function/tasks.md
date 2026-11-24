## 1. dws-jq scaffold

- [ ] 1.1 Create `dws-jq/` with these files, mirroring `dws-call-http`:
  - `go.mod`: module `github.com/dws/dws-jq`, `go 1.26.9`, `github.com/itchyny/gojq v0.12.20`;
  - `Makefile`: targets build, test (`-race`), vet, fmt-check, lint, docker, clean;
  - `.gitignore` (`bin/`), `.dockerignore`;
  - `version.txt` (`0.0.0`).
- [ ] 1.2 Add a `main.go` stub that compiles. Verify with `cd dws-jq && go vet ./... && go build ./...`.

## 2. dws-jq configuration contract (D3)

- [ ] 2.1 Write failing tests in `internal/config/config_test.go` for:
  - defaults;
  - every variable-name rule (format, duplicate, `secrets`/`ENV`/`__loc__`, non-array JSON);
  - `JQ_TIMEOUT` parsing (`0s`, `-1s`, `soon`);
  - `JQ_SITE`, `PORT` and `DAPR_HTTP_PORT` defaults.
- [ ] 2.2 Implement `config.Load()`, the `getenv` style helpers, and the exported `config.Names`
  list.
- [ ] 2.3 Add fixtures `testdata/contract/env-minimal.json`, `env-variables.json` and
  `env-secrets.json`. Add `TestConfigContract`: every fixture loads, and `config.Names` equals the
  union of fixture keys. Run `go test ./internal/config/`.

## 3. dws-jq expression core (D4–D6, D8)

- [ ] 3.1 Write failing tests for `expr.Unwrap`:
  - `${ .a }`;
  - `  ${.a}  `;
  - bare `.a`;
  - `${ { a: 1 } }`;
  - text with only `${`.
- [ ] 3.2 Write failing tests for the policy walk. They cover:
  - each blocked builtin at each arity;
  - `$ENV`;
  - `import` and `include`;
  - nested positions: `def`, `reduce`, `foreach`, `try`, `if`, object values, string interpolation,
    `label`;
  - `def debug:`;
  - false-positive guards: `.env`, `{env: 1}`, `"input"`, `{debug: .x}`;
  - allowed clock functions.

  Each error must name the builtin.
- [ ] 3.3 Implement the AST walker over gojq's `Query` types (pinned v0.12.20) so the 3.2 tests
  pass.
- [ ] 3.4 Write failing tests for the `$secrets` scan:
  - allowed: `.a`, `."a"`, `["api-token"]`, `["a"].b`;
  - rejected: bare, `as $s`, `| keys`, `[.k]`, `[$k]`, `["a" + .x]`, `[]`, `[0:1]`;
  - the scan returns the exact name set.

  Then implement it.
- [ ] 3.5 Write failing tests for `expr.Compile(expression, variables)`:
  - an undeclared-variable failure names the variable;
  - a parse error is reported;
  - the compile installs no environ, input or module loader (`input`/`env` are already rejected,
    but assert the options anyway).

  Then implement it, returning a `*Program` that holds the code, the variable order and the secret
  names.
- [ ] 3.6 Write failing tests for `Program.Evaluate(ctx, input, vars, secrets)`:
  - first output only;
  - no output → `null`;
  - an error as the first output → `*EvaluationError`;
  - a deadline (`last(range(1; infinite))` with 50ms) → a timeout error naming the limit;
  - truthiness of `false`/`null`/`0`/`""`/`[]`;
  - big-integer precision (`9007199254740993 + 1`);
  - NaN/±Inf encoding.

  Then implement it, including JSON number decoding (`UseNumber` → int, `*big.Int` or float64).
- [ ] 3.7 Run `go test ./internal/expr/`.

## 4. dws-jq secrets and redaction (D7, D9)

- [ ] 4.1 Write failing tests for `secrets.Fetch` against an `httptest` fake sidecar:
  - the path is `/v1.0/secrets/{store}/{name}`;
  - the `dapr-api-token` header is sent when set and absent otherwise;
  - the `value` key is bound;
  - a missing `value` fails;
  - a 404 is retried until the bound, then fails naming the store and secret with no value in the
    error;
  - success arrives after transient errors.

  Make the retry bound and backoff injectable so the tests run fast.
- [ ] 4.2 Implement `secrets.Fetch` with a 60 s default bound.
- [ ] 4.3 Write failing tests for `secrets.Redactor`:
  - raw and JSON-escaped values;
  - longest first;
  - empty values ignored;
  - multiple occurrences.

  Then implement it.
- [ ] 4.4 Run `go test ./internal/secrets/`.

## 5. dws-jq logging (D10)

- [ ] 5.1 Write `TestLogFormatContract` with the 12 golden cases from `docs/log-format.md`:
  - parsed-object comparison;
  - the timestamp regex;
  - one line per record;
  - absent keys absent;
  - `FATAL` via `LevelFatal`;
  - `exception.*` from an `error` attribute;
  - trace IDs from a context-carried `traceparent`.

  Add tests for:
  - text-mode fallback (`yaml`, empty, unset);
  - case-insensitive `" JSON "`;
  - `***` redaction applied before the scrubber, in both modes.
- [ ] 5.2 Implement `internal/logging`:
  - the handler;
  - the RE2 scrubber with `(?i)` and `${1}${2}`;
  - `traceparent` parsing (valid, non-zero) and its context helpers.
- [ ] 5.3 Run `go test ./internal/logging/`.

## 6. dws-jq HTTP server and main (D4, D8)

- [ ] 6.1 Write failing `httptest` tests for `internal/server`:
  - `/healthz` returns 503 `starting`, then 200 `ok` with the site;
  - `/run` returns 503 before ready;
  - success returns 200 `{result, truthy}`;
  - 400 for non-JSON, a non-object body or `variables`, an unknown field, an undeclared variable,
    `secrets` in `variables`, and a body over 4 MiB;
  - 422 fault shape, with the configured expression text and a redacted detail;
  - a 422 on timeout;
  - the request log record carries the `traceparent` IDs;
  - no log record for a success.
- [ ] 6.2 Implement the server: the readiness gate, the handlers, and per-request logging.
- [ ] 6.3 Implement `main.go`:
  1. config;
  2. logger;
  3. compile — on failure, one `ERROR` record with site, expression and reason, then exit 1 with no
     listener;
  4. listen;
  5. fetch secrets if referenced — on failure, one `ERROR` record and exit 1;
  6. ready;
  7. graceful shutdown on SIGINT/SIGTERM.

  Log one `WARN` when `JQ_SECRET_STORE` is set but unused.
- [ ] 6.4 Add a `main` test that runs the built binary as a subprocess with a malformed expression, a
  blocked builtin and an undeclared variable. Each run must exit non-zero, write exactly one error
  line containing the expression and the reason, and never open the port.
- [ ] 6.5 Run `go test ./...`.

## 7. Expression corpus `conformance/expressions/` (D11)

- [ ] 7.1 Write `conformance/expressions/case.schema.json` (2020-12). Its fields, areas and `expect`
  variants are listed in design D11.
- [ ] 7.2 Write `conformance/expressions/README.md`. Cover:
  - the layout;
  - the case format;
  - the authoring rules (expectations from the documented dialect and ADRs, never edited to fit an
    engine);
  - the runner rules and verdicts, including the stale-divergence rule;
  - engine notes (gojq vs jackson-jq);
  - how to run the `dws-jq` runner.
- [ ] 7.3 Add `contract/` cases:
  - wrapped;
  - bare;
  - first-of-many;
  - empty;
  - truthiness of `false`, `null`, `0`, `""` and `[]`;
  - an evaluate error.
- [ ] 7.4 Add `builtins/`: the 13 cases converted from `conformance/data-flow/cases/builtins/`.
  - Use the same file names, and keep each case's `source` and `engine` text.
  - Each expression is the original stage's jq.
  - The 11 blocked cases expect a `compile` fault containing the builtin.
  - Add the false-positive guard cases (`.env`, `{debug: 1}`, `"input"`).
- [ ] 7.5 Add `dialect/`: one case per ADR 0014 Decision 5 row:
  - object key order (`tojson`, `to_entries`);
  - `keys_unsorted` (compile fault);
  - date/local-time functions;
  - integer precision;
  - string indexing (`"abcde"[2]`);
  - a regex look-around (fault);
  - `$__loc__` (compile fault).
- [ ] 7.6 Add `loops/` and `catch/` cases from v1's tests:
  - `for.in`: `.pets`, `${ .pets }`, `$sample`, `.count`, `[1, 2]`;
  - `while`: `.count > 0`, `.flag` (three inputs), `$item < 3` (two values), `$i < 2`;
  - catch: `$error.status == 429`, `$error.status == 503`, `$oops.status == 503`, and
    `retry.when` — all against the v1 503 error object.
- [ ] 7.7 Add `templates/` cases:
  - the O-2 example;
  - a part with `def`;
  - a part with `as $x`;
  - an `empty` part;
  - a part with several results.
- [ ] 7.8 Add `divergences/dws-jq.yaml` (`runtime: dws-jq (gojq)`, empty `divergences: []`), with
  the same header comment as `conformance/data-flow/divergences/v1.yaml`.

## 8. dws-jq corpus runner (D11)

- [ ] 8.1 Add the test-only dependencies `gopkg.in/yaml.v3` and
  `github.com/santhosh-tekuri/jsonschema/v6`.
- [ ] 8.2 Implement `internal/conformance` with a test that:
  1. loads the corpus, honouring `DWS_CONFORMANCE_EXPRESSIONS`;
  2. validates the schema, and checks `name`/`area`/path and that `test` is unique;
  3. sets `TZ=UTC`;
  4. runs `expr.Compile` and `Program.Evaluate`;
  5. compares numbers by value and checks the fault phase;
  6. applies the four verdicts;
  7. prints one line per case and the totals.
- [ ] 8.3 Add self-tests for the runner's own rules, using a temporary corpus:
  - a path/area mismatch fails;
  - a duplicate `test` fails;
  - a stale divergence fails;
  - `10` equals `10.0`.
- [ ] 8.4 Run the corpus. A case that fails because the implementation is wrong is fixed in the
  implementation. A case where gojq genuinely differs from the documented expectation is listed in
  `divergences/dws-jq.yaml` with a reason and follow-up. Record which ADR 0014 Decision 5 "to
  confirm" cells are now confirmed on the gojq side.

## 9. dws-jq packaging and docs (D12, D14)

- [ ] 9.1 Add the `Dockerfile` (`golang:1.26.9` → `gcr.io/distroless/static:nonroot`, `EXPOSE 8080`)
  and `k8s/knative-service.yaml`. The example uses `minScale "0"`, Dapr annotations, `JQ_*` env and
  a `/healthz` readiness probe.
- [ ] 9.2 Write `dws-jq/README.md`. Cover:
  - purpose;
  - the configuration contract table;
  - the HTTP contract (200/400/422/503);
  - the startup checks;
  - the builtin policy, including the name-based `def` note;
  - the `$secrets` static-only rule;
  - the dialect, with a pointer to the corpus;
  - logging;
  - EX-3 results.
- [ ] 9.3 Write `dws-jq/CLAUDE.md` with package-specific idioms only: commands, error style, config
  style, where logging is allowed, test style, the corpus runner location, and the local
  `-race`/386 note.
- [ ] 9.4 Add `.github/workflows/dws-jq.yml`, modelled on `dws-call-http.yml`:
  - paths: `dws-jq/**`, `conformance/expressions/**`, the workflow file;
  - tag `dws-jq-v*`;
  - test job: vet, gofmt, build, `go test -race ./...`;
  - image job: build, a `/healthz` smoke test with `JQ_EXPRESSION=.`, push off-PR.
- [ ] 9.5 Register the release:
  - `release-please-config.json`: package `dws-jq` (`simple`, `version.txt`,
    `initial-version: "0.1.0"`, `skip-github-release`);
  - `.release-please-manifest.json`: `"dws-jq": "0.0.0"`;
  - `component-release.yml`: option `dws-jq`;
  - `docs/release-process.md`: a workflow-table row and a first-release note.
- [ ] 9.6 Update the root `CLAUDE.md`:
  - package map row;
  - gate-table row (`make lint && make test`);
  - a new contract bullet: `dws-jq` configuration contract, controller 2m.2 → `dws-jq`, pinned by
    `dws-jq/testdata/contract/`;
  - `dws-jq` added to the structured-log-format bullet.
- [ ] 9.7 Update `docs/log-format.md`:
  - package list;
  - severity row (Go `slog`);
  - trace-source row (inbound `traceparent`).
- [ ] 9.8 Run the full gate: `cd dws-jq && make lint && make test`. On windows/386, `-race` is
  unavailable: run `go vet ./... && go test ./...` and record the gap. Run `make docker` if the
  Docker daemon is running.

## 10. EX-3 measurements and ADR notes (D13, D14)

- [ ] 10.1 Check whether a cluster with Knative Serving and Dapr is reachable (`kubectl get nodes`,
  Knative and Dapr CRDs).
  - If it is: load the image, apply the example service, and measure cold start (5 samples after
    scale-to-zero) and warm latency (p50/p95 over 200 calls) through Dapr service invocation from a
    sidecar-enabled curl pod.
  - If it is not: record "not measured: no cluster".
- [ ] 10.2 Record the EX-3 numbers, or their absence, in `dws-jq/README.md`.
- [ ] 10.3 Add a dated "2j notes" block to ADR 0014. It records:
  - the gojq-side Decision 5 cells the corpus confirmed;
  - O-5: dynamic `$secrets` access is rejected by `dws-jq`;
  - the EX-3 status;
  - anything in the ADR that turned out wrong.

  Also update the roadmap row 2j status.
- [ ] 10.4 Run `openspec validate dws-jq-function` and the final `dws-jq` gate.
