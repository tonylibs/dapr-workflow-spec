# dws-jq

A small Go service that evaluates **one jq expression, fixed at deployment**, over HTTP. The same
image serves every expression site of a workflow: the controller deploys one Knative Service per
site and differs only in its env block (roadmap row 2m.2; until then the example in
[`k8s/knative-service.yaml`](k8s/knative-service.yaml) is hand-written). The decision record is
[ADR 0014](../docs/adr/0014-dws-jq-expression-function.md); the design is
`openspec/changes/dws-jq-function/`.

The engine is [gojq](https://github.com/itchyny/gojq) (pinned in `go.mod`). The expression is
compiled once at startup, so a bad expression fails the deploy instead of the first request.

## Configuration contract

All configuration is environment variables.

| Name | Required | Default | Meaning |
|---|---|---|---|
| `JQ_EXPRESSION` | yes | none | The expression, `${ ... }` or bare jq. A single outer `${ ... }` is unwrapped. |
| `JQ_VARIABLES` | no | `[]` | JSON array of variable names, without the `$`, e.g. `["context","item","index"]`. |
| `JQ_TIMEOUT` | no | `5s` | Per-evaluation limit; a Go duration greater than 0. |
| `JQ_SECRET_STORE` | when the expression reads `$secrets` | none | Dapr secret store component name. |
| `JQ_SITE` | no | `dws-jq` | Site ID `<owner>.jq.<site>`; used in `/healthz` and logs only. |
| `PORT` | no | `8080` | Listen port. |
| `DAPR_HTTP_PORT` | no | `3500` | Sidecar HTTP port (injected by Dapr). |
| `DAPR_API_TOKEN` | no | none | Sent as the `dapr-api-token` header on secret reads. |
| `DWS_LOG_FORMAT` | no | text | `json` selects the shared structured log format. |

Variable names must match `^[A-Za-z_][A-Za-z0-9_]*$`, be unique, and not be `secrets`, `ENV` or
`__loc__`. `JQ_SECRET_STORE` set without any `$secrets` use logs one `WARN` and continues.

This table is pinned by the fixtures in [`testdata/contract/`](testdata/contract) and
`TestConfigContract` (see the root `CLAUDE.md` contract bullet). The controller renders it in 2m.2.

## HTTP contract

`POST /run` with `{"input": <any>, "variables": {"<declared name>": <any>}}`. Both fields are
optional: an absent `input` is `null`, an absent declared variable is `null`, and an absent or
`null` `variables` binds every declared variable to `null`. The body is at most
4 MiB. Only the exact keys `input` and `variables` are accepted. The result is the **first output**
of the expression; no output gives `result: null`.

| Status | Body | When |
|---|---|---|
| 200 | `{"result": <value>, "truthy": <bool>}` | evaluated; `truthy` follows jq (only `null`, `false` and no output are falsy) |
| 400 | `{"error": "<reason>"}` | not a JSON object, unknown field, `variables` neither an object nor `null`, undeclared variable (`secrets` included), body over 4 MiB |
| 422 | `{"fault": {"type": "expression", "expression": "<as configured>", "detail": "<reason>"}}` | a jq runtime error, an `error(...)` call, the time limit, or a result that cannot be encoded |
| 500 | `{"error": "internal error"}` | an evaluation error that is not a jq fault (logged with the site) |
| 503 | `{"error": "not ready"}` | before startup finished |

```shell
$ curl -s -X POST localhost:8080/run -d '{"input":{"n":3},"variables":{"item":2}}'   # JQ_EXPRESSION='$item < .n', JQ_VARIABLES='["item"]'
{"result":true,"truthy":true}
$ curl -s -X POST localhost:8080/run -d '{"expression":"."}'
{"error":"unknown field \"expression\"; the body accepts only \"input\" and \"variables\""}
$ curl -s -X POST localhost:8080/run -d '{"input":"x"}'                              # JQ_EXPRESSION='.a'
{"fault":{"detail":"expected an object but got: string (\"x\")","expression":".a","type":"expression"}}
```

While startup is in progress, `POST /run` responds with HTTP 503 and `{"error":"not ready"}`.

Numbers keep integer precision; results are encoded with jq's rules (`nan` becomes `null`,
infinities become `+-1.7976931348623157e+308`). Object keys come out sorted (gojq).

`GET /healthz` is `200 {"site":...,"status":"ok"}` once ready and `503 {"site":...,"status":"starting"}`
before.

## Startup checks

In order; any failure writes one `ERROR` record and exits 1:

1. Load and validate the environment (`invalid configuration: ...`). The record carries `site` and
   `expression` as configured (the expression is empty when `JQ_EXPRESSION` is unset), like the
   expression and `JQ_SECRET_STORE` failures below; the listener and secret-read failures carry `site`
   but not the expression.
2. Unwrap, parse and policy-check the expression, then compile it with the declared variables plus
   `$secrets` (`invalid expression: ...`, with the expression text and site on the record). An
   undeclared `$variable` fails here.
3. If the expression reads `$secrets` but `JQ_SECRET_STORE` is empty, fail.
4. Open the listener (so the Dapr sidecar sees the app port), then read each referenced secret,
   retrying with backoff for up to 60 s in total. A store or secret that cannot be read, or a reply
   without a string `value`, fails startup naming the store and secret (never a value). Only the exact
   lowercase `value` key counts, and a secret named `bulk` is refused without a request because
   `GET /v1.0/secrets/{store}/bulk` is Dapr's bulk route, which would return the whole store.
5. Become ready: `/healthz` turns `200`, `/run` starts evaluating.

On SIGINT/SIGTERM the server shuts down gracefully (10 s).

## Builtin policy

The parsed query is walked as a whole (terms, suffixes, definitions, bindings, object keys and
values, `reduce`/`foreach`/`if`/`try`/`label` bodies). Rejected at startup, any arity:

- calls to `env`, `input`, `inputs`, `input_filename`, `halt`, `halt_error`, `debug`, `stderr`;
- the variable `$ENV`;
- any `import` or `include`.

The error is `blocked builtin "<name>"`. Field access (`.env`), object keys (`{debug: 1}`) and
strings (`"input"`) are not rejected. The check is **by name**: a user definition such as
`def debug: ...;` is rejected too. The compile step has no environ, input or module loader as a
second guard. The AST walker follows gojq's `Query` types and is pinned to gojq v0.12.20.

## `$secrets`

`$secrets` is bound to a map loaded once at startup from the Dapr secrets API
(`GET /v1.0/secrets/{store}/{name}`, the `value` key). The function keeps that map in memory and
supplies it to the expression directly. An expression without `$secrets` skips the sidecar call.

Only **static** access is allowed, so the set of names the function needs is known before it runs:

| Allowed | Rejected at startup |
|---|---|
| `$secrets.name` | bare `$secrets` (`$secrets as $s`, `$secrets \| keys`, `$secrets \| tojson`, `{$secrets}`) |
| `$secrets."name"` | non-literal index: `$secrets[.k]`, `$secrets[$k]`, `$secrets["a" + .x]` |
| `$secrets["api-token"]` (needed for names with hyphens) | iteration `$secrets[]` and slices |
|  | binding the name: `$secrets` as a function parameter, pattern or object-pattern key |

The error is `only static $secrets access is allowed ($secrets.<name> or $secrets["<name>"]); found <form>`.

Why: each function is granted read access to exactly the names its expression uses (O-5). Allowing
`$secrets[.key]` would mean granting every secret in `use.secrets`, widening the blast radius and
making the controller's static scan unsound. This answers the 2.0d dynamic-access follow-up:
**rejected**. A computed choice can still be written statically:
`if .x then $secrets.a else $secrets.b end`.

Secret values are replaced by `***` in fault details, 400 reasons and log records, in raw,
`encoding/json` and gojq-escaped forms. gojq's error messages show a value through a 30-byte preview
that cuts a long value and appends ` ...` (`string ("ABCDEFGHIJKLMNOPQRSTUVWX ...")`), so any prefix
of a secret (up to 30 bytes) directly followed by ` ...` is masked too, keeping the marker:
`string ("*** ...")`. This can mask ordinary text that starts like a secret and ends in ` ...`.
Secrets that overlap in a message (one ends where another starts, or one inside another) are masked
as one `***`. The `result` itself is **not** redacted: returning a secret is the expression author's
explicit choice.

## Dialect

The dialect is gojq's, minus the builtins above. It differs from jackson-jq (Step nodes, v1):

| Behavior | `dws-jq` (gojq) |
|---|---|
| Object key order | always sorted; affects `to_entries`, `tojson`/`tostring` of objects |
| `keys_unsorted` | missing: the expression fails at startup |
| Date and local-time functions | full set (`todate`, `mktime`, `strftime`, `localtime`, ...) |
| Integer precision | arbitrary precision; `9007199254740993 + 1` is `9007199254740994` |
| String indexing | `"abcde"[2]` is `"c"` (intentionally unlike jq) |
| Regex | RE2: no look-around or backreferences. `test("a(?=b)")` is a 422 evaluate fault (`invalid or unsupported Perl syntax`), not a startup error |
| `$__loc__`, `input_line_number`, `get_*_origin` | unsupported (compile fault); the corpus pins `$__loc__`, and `input_line_number` is pinned in `internal/expr/program_test.go` |

The behavior is measured, not just described: the language-neutral corpus
[`conformance/expressions/`](../conformance/expressions) holds a case for each row above except
`input_line_number` and `get_*_origin` (see that row), plus the v1 expression shapes.
`internal/conformance` runs it inside `go test`; known divergences live in
`conformance/expressions/divergences/dws-jq.yaml` (currently none).

## Logging

`internal/logging` implements [`docs/log-format.md`](../docs/log-format.md). With
`DWS_LOG_FORMAT=json`: one JSON object per line with `timestamp`, `severity`, `logger` (`dws-jq`),
`message`, optional `trace_id`/`span_id` (from the inbound W3C `traceparent` header; no OTel SDK) and
`exception.*`, then the contract scrubber. Anything else: slog text. Secret values are redacted in
both modes.

Per request: one `INFO` line per 422 and one `WARN` line per 400, each with `site` and `detail`. No
line for a success, and payloads are never logged. `ERROR` is for startup and server failures.

## EX-3 results

Measured on 2026-11-25 for [ADR 0014](../docs/adr/0014-dws-jq-expression-function.md) follow-up EX-3
(cold start and per-evaluation latency). These are single-run numbers from a local cluster, to size
the question, not a benchmark.

**Setup.** Docker Desktop Kubernetes (kind-style, three nodes, v1.36.1), Knative Serving 1.24.0 with
Kourier, Dapr 1.19.1. Default autoscaler settings (60 s stable window, 30 s scale-to-zero grace). The
image was built from commit `37496e44` (18.2 MB, linux/amd64) and imported into every node's
containerd, so a cold start includes **no image pull**. Its tag used the `kind.local/` registry, which
Knative skips when resolving tags, so no cluster configuration was touched. The service was
[`k8s/knative-service.yaml`](k8s/knative-service.yaml) (`$item < 3`, `minScale: 0`, Dapr sidecar,
`dapr.io/metrics-port: "9095"`) in a throwaway namespace, since deleted. The caller was a `curl` pod
with its own Dapr sidecar. Every time below is curl's `time_total` inside that pod, one new
connection per call, so it includes the caller's sidecar, the callee's sidecar and the request itself.
Percentiles are nearest-rank over the 200 sorted samples (p50 is the 100th, p95 the 190th).

| Measurement | Path | Result |
|---|---|---|
| Warm, 200 sequential calls (5 warm-up calls first) | Dapr service invocation (`localhost:3500/v1.0/invoke/<app-id>/method/run`) | all `200`; min 1.7 ms, **p50 2.4 ms, p95 3.5 ms**, p99 3.8 ms, max 5.1 ms |
| Warm, 200 sequential calls | Knative route (`http://<service>.<ns>.svc.cluster.local/run`, through Kourier) | all `200`; min 3.8 ms, p50 4.7 ms, p95 6.4 ms, p99 7.2 ms, max 7.7 ms |
| Cold, 5 samples, each after the revision had scaled to zero pods | Dapr service invocation | **5 of 5 failed**: `HTTP 500`, `ERR_DIRECT_INVOKE`, "failed to resolve address for '<app-id>-dapr.<ns>.svc.cluster.local' ... no such host", after 3.1 to 5.5 ms. No pod was started |
| Cold, 5 samples, each after the revision had scaled to zero pods | Knative route | `200` after **0.923, 0.932, 0.968, 0.995, 1.020 s** (median 0.968 s) |

**What the numbers say.**

- A warm evaluation costs a few milliseconds end to end. The expression is not the cost; the two
  sidecar hops are.
- A scaled-to-zero `dws-jq` revision **cannot be woken by Dapr service invocation.** Dapr resolves the
  callee through the `<app-id>-dapr` headless Service. The error names that host as unresolvable, so with zero pods it has no address and
  the call fails at once, and the Knative activator never sees a request. (The error is measured, 5 of 5; the Service-has-no-address reading is the likely cause.) Only a request through the
  Knative route starts a pod.
- Once a pod exists, Dapr invocation does not work immediately. In four of the five cold samples a
  Dapr call sent right after the Knative route answered still returned the same `500` (the fifth
  call's output was lost to a `kubectl exec` stream error). In one extra run it began to succeed
  roughly 20 s after the pod was created. The cause was not isolated: the headless Service gaining an address and DNS negative caching (cluster-DNS dependent) would both fit.
  That was one observation, not a measured distribution.
- A cold start through the Knative route is about one second here: pod scheduling plus three
  containers (`dws-jq`, `queue-proxy`, `daprd`), with the image already on the node. A real pull and a
  busier cluster add to it. How much of the second is `daprd` was not measured.

**Not measured:** a loaded cluster, concurrency above one, a node without the image, `min-scale: 1`
(a warm pod needs no wake-up, so the warm rows apply) and Dapr with mTLS or access policies.

## Local development

```shell
cd dws-jq
make build   # compile bin/dws-jq
make test    # go test -race -count=1 ./...  (includes the expression corpus)
make lint    # go vet + gofmt check (+ golangci-lint if installed)
make docker  # build registry.io/dws/dws-jq:1.0
go test -count=1 ./internal/conformance/ -v   # the corpus alone, one line per case
```

The corpus runner reads `conformance/expressions/` at the repository root. Set
`DWS_CONFORMANCE_EXPRESSIONS` to point at another copy. The `-count=1` flag makes Go reread corpus
files outside the module. On windows/386, use `go vet ./...`, `gofmt -l .` and
`go test -count=1 ./...`. The race detector is unavailable on that target. Run `make test` on
Linux/amd64.

Run it locally without a cluster:

```shell
JQ_EXPRESSION='.a + 1' PORT=8080 go run .
curl -s -X POST localhost:8080/run -d '{"input":{"a":1}}'
```
