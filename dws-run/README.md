# dws-run

Generic, prebuilt step image for `run: shell` and `run: script` tasks in the
DWS platform.

**One Go codebase produces three images.** They share the same build stage and
binary; only the final Docker stage (base image) and the `MODE` env var it
sets differ. `MODE` selects which interpreter the binary execs — everything
else is defined entirely by environment configuration, so there is no
per-step code or rebuild.

| Image                  | Base                     | `MODE`          | DSL subtype              | Interpreter |
| ----------------------- | ------------------------ | ---------------- | ------------------------- | ----------- |
| `dws-run-shell`         | `busybox:stable-glibc`   | `shell`           | `run: shell`               | `sh -c`     |
| `dws-run-script-js`     | `node:24-slim`           | `script-js`       | `run: script` (`language: js`)     | `node -e`   |
| `dws-run-script-python` | `python:3.13-slim`       | `script-python`   | `run: script` (`language: python`) | `python3 -c` |

`dws-run-shell` uses `busybox:stable-glibc` rather than a distroless/static
base because shell mode needs a real `sh` to exec.

The image is deployed as a [Knative](https://knative.dev/) service
(scale-to-zero) with a [Dapr](https://dapr.io/) sidecar, and is invoked by
`dws-orchestrator` via Dapr service invocation.

## How it works

The orchestrator POSTs the current workflow data (a JSON object) to `/run`.
The step marshals that data to JSON and writes it to the subprocess's stdin,
spawns the configured interpreter (`sh`, `node`, or `python3`) with `COMMAND`
or `SCRIPT`, captures stdout/stderr/exit code under `TIMEOUT`, and shapes the
result per `RETURN` and `OUTPUT`.

## Routes

| Method | Path       | Purpose                                                       |
| ------ | ---------- | -------------------------------------------------------------- |
| `POST` | `/run`     | Step entrypoint. Body = workflow data (JSON). Runs the subprocess. |
| `GET`  | `/healthz` | Liveness / readiness probe.                                    |

### `POST /run`

- **Request body**: the current workflow data as a JSON object. An empty body
  is treated as `{}`.
- **Success (`200`)**: the shaped result (see RETURN/OUTPUT below).
- **Non-zero exit, retryable (`502`)**: when the exit code is *not* treated as
  data (`RETURN` is `stdout`, `stderr`, or `none`) —
  `{"task": <task>, "exitCode": <code>, "stderr": <stderr>}`.
- **Spawn failure (`502`)**: interpreter missing, permission error, or
  timeout — `{"task": <task>, "error": <detail>}`.
- **Bad request body (`400`)**: malformed JSON — `{"task": <task>, "error": <detail>}`.
- **Shaping/config error (`500`)**: e.g. `OUTPUT=merge` when the selected
  value is not a JSON object — `{"task": <task>, "error": <detail>}`.

`502` is used specifically for the retryable cases (non-zero exit as failure,
spawn failure) so the orchestrator's retry policy re-invokes the step; `500`
is used where a retry would not help. `502` means retryable, `500` does not.

## Environment variables

| Var           | Required | Default   | Description                                                                 |
| -------------- | -------- | --------- | ----------------------------------------------------------------------------- |
| `MODE`         | no       | `shell`   | `shell` \| `script-js` \| `script-python`. Set by the image's Dockerfile, not the workflow definition. |
| `PORT`         | no       | `8080`    | HTTP listen port.                                                              |
| `TASK`         | no       | `run`     | Task/step name. Appears in error bodies and logs; set to the Dapr app-id.       |
| `COMMAND`      | cond.    | —         | Required when `MODE=shell`. The shell command line executed via `sh -c`.        |
| `SCRIPT`       | cond.    | —         | Required when `MODE=script-js` or `MODE=script-python`. The script source.       |
| `ARGUMENTS`    | no       | —         | JSON **object** (key/value map) of arguments, order preserved. e.g. `{"env":"prod","region":"eu"}`. |
| `ENVIRONMENT`  | no       | —         | JSON object of string environment variables added to (not replacing) the subprocess's environment. |
| `RETURN`       | no       | `stdout`  | `stdout` \| `stderr` \| `code` \| `all` \| `none`. Selects the raw value.        |
| `OUTPUT`       | no       | `replace` | `replace` \| `merge`. Shapes how the raw value folds into the response.          |
| `TIMEOUT`      | no       | `30s`     | Go duration for the whole subprocess run. e.g. `5s`, `1m`. Must be positive.     |

Invalid config (missing `COMMAND`/`SCRIPT` for the mode, an unknown `MODE`,
unknown `RETURN`/`OUTPUT`, `ARGUMENTS` that isn't a JSON object, `ENVIRONMENT`
with a non-string value, or a malformed/non-positive `TIMEOUT`) causes a
**non-zero exit at startup** with a message naming the problem.

### `ARGUMENTS` is a JSON object, not an array

DSL 1.0.0 models a task's `arguments` as a key/value map, so `ARGUMENTS` must
be a JSON **object** — `{"env":"prod","region":"eu"}` — not an array. Key
order in the source JSON is preserved end-to-end (it drives the order shell
flags are rendered in); an array or any other JSON shape is rejected at
startup with an error naming the requirement.

### Argument rendering per runtime

How `ARGUMENTS` reaches the subprocess differs by `MODE`:

- **Shell** (`MODE=shell`): each argument becomes an ordered `--<name> <value>`
  pair appended as positional parameters to `sh -c '<command> "$@"' sh`, so a
  value containing shell metacharacters cannot alter the command's structure.
  Scalar values render as their natural text (numbers without a trailing
  `.0`, booleans as `true`/`false`); objects and arrays render as compact
  JSON text.
- **Script** (`MODE=script-js` / `MODE=script-python`): arguments are exposed
  to the subprocess via a `DWS_ARGUMENTS` environment variable (a JSON
  object), and a generated **prelude** is prepended to the author's script
  that parses `DWS_ARGUMENTS` and binds each entry as an in-scope variable
  (`const <name> = ...;` in JS, a module-level `<name> = ...` in Python)
  before the author's own script source runs. Values pass through as real
  JSON types (not string-interpolated into source), so quoting is exact.

  **This prelude shifts the author's script line numbers** — the script's
  line 1 is no longer the interpreter's line 1, since the prelude occupies
  one or more lines above it. This is worth accounting for when reading a
  stack trace from a failed script step.

#### Argument name constraints

Argument names must be:

- **Valid identifiers**: `[A-Za-z_][A-Za-z0-9_]*` — the same character rule
  in both script languages.
- **Not a reserved word in the target language.** A name that's a keyword in
  one script language may be a perfectly fine identifier in the other (e.g.
  `const` is a JavaScript reserved word but a legal Python name; `None` is a
  Python reserved word but a legal JavaScript name) — the check is applied
  per `MODE`, only against the reserved-word list for that mode.
- **Not one of the prelude's own internal names**, regardless of mode:
  `__dwsArgs`, `__dws_args`, `__dws_json`, `__dws_os`. Using one of these as
  an argument name would redeclare an identifier the generated prelude
  itself declares — a `SyntaxError` in both target languages.

`dws-controller` is expected to reject these at compile time; `dws-run`
independently re-validates at request time as defense in depth for
hand-written manifests.

### `RETURN` → `OUTPUT` composition

`RETURN` and `OUTPUT` compose in two steps:

1. **`RETURN` selects the raw value** from the subprocess result:
   - `stdout` (default): captured stdout.
   - `stderr`: captured stderr.
   - `code`: the numeric exit code.
   - `all`: `{"code": <int>, "stdout": <string>, "stderr": <string>}`.
   - `none`: no value (an empty response under `OUTPUT=replace`, or the
     unmodified input under `OUTPUT=merge`).

   For `stdout`/`stderr`, the trimmed text is parsed as JSON when it parses
   (so a script that prints a JSON object or array yields that structure) and
   left as a plain string otherwise — unlike `dws-call-http`, unparseable
   text is not an error, since plain text is a script's normal output.

2. **`OUTPUT` shapes how that value folds into the response**:
   - `replace` (default): respond with the value as-is.
   - `merge`: the value must be a JSON object; it's shallow-merged into the
     input workflow data (its keys win on conflict) and the merged object is
     returned. A non-object value under `OUTPUT=merge` is a `500` error.

### Exit-code rule

A non-zero exit is treated differently depending on `RETURN`:

- Under `RETURN=code` or `RETURN=all`, the author explicitly asked to observe
  the exit code, so a non-zero exit is **data**, not a failure: the response
  is `200` and the exit code appears in the shaped value.
- Under `RETURN=stdout`, `RETURN=stderr`, or `RETURN=none`, a non-zero exit is
  a **failure**: the response is `502` (`{"task", "exitCode", "stderr"}`) so
  the orchestrator's retry policy re-invokes the step.

### Captured output trimming

Captured stdout and stderr are both trimmed of trailing newline(s) with the
same rule (`strings.TrimRight(..., "\n")`) — not leading whitespace, not
interior blank lines, and not full whitespace trimming, so a script that
deliberately emits trailing spaces keeps them. The trim is applied
identically to both streams, so `RETURN=all` and the `502` error body's
`stderr` field are never asymmetric.

## Examples

### `sync-inventory` (shell, replace)

```sh
MODE=shell \
TASK=sync-inventory \
COMMAND='echo "{\"synced\":true}"' \
RETURN=stdout \
OUTPUT=replace \
./dws-run
```

```sh
curl -sX POST localhost:8080/run -d '{}'
# -> {"synced":true}
```

### `score-lead` (script-js, with arguments)

```sh
MODE=script-js \
TASK=score-lead \
SCRIPT='console.log(JSON.stringify({score: threshold * 2}));' \
ARGUMENTS='{"threshold":21}' \
RETURN=stdout \
OUTPUT=merge \
./dws-run
```

```sh
curl -sX POST localhost:8080/run -d '{"leadId":"L-1"}'
# -> {"leadId":"L-1","score":42}
```

### Observing the exit code (`RETURN=code`)

```sh
MODE=shell \
TASK=check \
COMMAND='exit 3' \
RETURN=code \
./dws-run
```

```sh
curl -sX POST localhost:8080/run -d '{}'
# -> 200, body: 3   (not a 502 — RETURN=code makes the exit code data)
```

## Development

```sh
make build              # compile bin/dws-run
make test                # go test -race ./...
make vet                 # go vet ./...
make lint                 # vet + gofmt check (+ golangci-lint if installed)
make docker-shell          # build registry.io/dws/dws-run-shell:1.0
make docker-script-js       # build registry.io/dws/dws-run-script-js:1.0
make docker-script-python   # build registry.io/dws/dws-run-script-python:1.0
make docker                 # build all three images
```

Build gate:

```sh
go vet ./... && go test ./...
```

## Deployment

See [`k8s/knative-service.yaml`](k8s/knative-service.yaml) for example Knative
Services — one per image (`run-shell`, `run-script-js`, `run-script-python`).
Key points:

- Each `run` subtype in a workflow definition deploys exactly one of the
  three images, chosen by `dws-controller` at compile time.
- `dapr.io/enabled: "true"` and `dapr.io/app-id` set to the **task name**.
- `minScale: "0"` for scale-to-zero.
- Step behavior configured entirely through the `env` block.

To deploy another step, copy the matching manifest and change
`metadata.name`, the Dapr `app-id`, the `TASK` env, and the
`COMMAND`/`SCRIPT`/`ARGUMENTS` configuration.
