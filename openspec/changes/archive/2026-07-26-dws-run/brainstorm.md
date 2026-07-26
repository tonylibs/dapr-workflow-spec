<!--
Raw capture of superpowers:brainstorming output.

This change entered via a fully-specified request rather than an open-ended
exploration, so the capture below is a decision log: background, the decision
chain (Q1-Q9), and the design trade-offs that were weighed and rejected.

design.md reorganizes this material into structured sections. Do not copy this
file's content there.
-->

# Brainstorm — dws-run

## Background

`WorkflowCompiler.runStep()` (`dws-controller/src/main/java/io/dws/controller/compile/WorkflowCompiler.java:214`)
already recognizes `run` tasks: it extracts `COMMAND` from `run.shell` and `SCRIPT` from
`run.script`'s inline code, then emits `new StepService(Names.kebab(taskName), TaskKind.RUN,
images.run(), env)`.

Three problems with the current state:

1. **`images.run()` points at nothing.** `dws-controller/src/main/resources/application.yaml:11`
   defaults it to `sw-run:1.0` — a placeholder. No component in this repo builds that image, and
   nothing in `ghcr.io/tonylibs/*` publishes it. Any workflow using `run` compiles cleanly and
   deploys a Knative Service that can never pull.
2. **Two of four `run` subtypes fall through silently.** `run.container` and `run.workflow` hit
   neither branch, producing a `StepService` with an empty env map — a deployment that fails only
   at runtime, with no diagnostic pointing back at the definition.
3. **Everything past the entrypoint is unspecified.** No `arguments`, no `environment`, no
   `return`, no exit-code semantics, no `language` handling.

DSL 1.0.0 defines four `run` subtypes: `container`, `script`, `shell`, `workflow`.

## Decision chain

### Q1 — Which `run` subtypes does this change cover?

**Decision: `shell` and `script` only.** `container` and `workflow` are out, and get an explicit
compile-time rejection rather than a silent half-implementation.

Rationale for each exclusion:

- **`container`** spawns arbitrary user-specified images. That needs Kubernetes API access from
  the step (or controller-side pod templating) — a materially different security boundary from
  "a prebuilt image runs a configured command." It deserves its own threat model and its own
  change.
- **`workflow`** is DWS-internal cross-workflow invocation. Structurally it belongs in
  `dws-orchestrator` as a local Activity that starts a child workflow instance, not in a step
  image reached over Dapr service invocation. Wrong component entirely.

Deferring them is only safe if the compiler *rejects* them. Falling through to an empty-env
`StepService` (today's behavior) converts a definition error into a runtime mystery.

### Q2 — One image or three?

**Decision: one Go codebase (`dws-run`), three images.**

The three runtimes need genuinely different base layers: shell needs almost nothing, `js` needs a
Node runtime, `python` needs CPython 3.13. Baking all three into one image would triple the pull
size of a scale-to-zero step for no benefit — cold-start cost is exactly what Knative
scale-to-zero is sensitive to.

But the HTTP surface, config parsing, output shaping, and error mapping are identical across all
three. Three separate codebases would triple the maintenance of the shared step-service contract.

So: one Go module, one server, one runner; three Dockerfile targets sharing the Go build stage and
differing only in final-stage `FROM` and the exec command:

| Image | Base | Exec |
|---|---|---|
| `dws-run-shell` | distroless-equivalent minimal (mirrors `dws-call-http`) | `sh -c "$COMMAND"` |
| `dws-run-script-js` | `node:<pinned>-slim` (ES2024, per DSL) | `node` |
| `dws-run-script-python` | `python:3.13-slim` (DSL requires 3.13.x) | `python3` |

Rejected alternative: one image with all three runtimes, selected by env var. Simpler CI, but the
shell step — the most common and most latency-sensitive — would pay Node + Python pull cost on
every cold start.

Rejected alternative: three independent components (`dws-run-shell/`, `dws-run-script-js/`, ...).
Honors the monorepo's "independently built components" convention most literally, but triplicates
the shared HTTP contract, which is precisely the thing the repo works hardest to keep consistent.

### Q3 — Does `dws-run` follow the existing step-service contract?

**Decision: yes, verbatim.** `POST /run` with the current workflow data as the JSON body (empty
body ⇒ `{}`), `GET /healthz`, and `502` — not `400`/`500` — for execution failures, specifically so
the orchestrator's retry policy re-invokes the step.

`dws-call-http` and `dws-call-openapi` already implement this contract independently. A third
implementation is the point at which the contract stops being "two files that happen to agree" and
becomes a real invariant, so `dws-run` copies `internal/server/server.go`'s shape closely rather
than inventing its own error mapping.

### Q4 — How does `arguments` reach the subprocess?

This is where the original request and DSL 1.0.0 disagreed, and the disagreement was verified
against the actual SDK rather than assumed.

The request specified `ARGUMENTS` as "a JSON array of strings, passed as argv". The SDK
(`io.serverlessworkflow:serverlessworkflow-types:7.26.0.Final`, the version pinned in
`dws-controller/pom.xml`) models it as a **map**:

```
ShellArguments.getAdditionalProperties()  -> Map<String, Object>
ScriptArguments.getAdditionalProperties() -> Map<String, Object>
```

An array contract would have silently discarded the argument names the workflow author wrote.

**Decision: keep the DSL's map shape, render it conventionally per runtime.**

- `ARGUMENTS` is a **JSON object**, not an array.
- **Shell**: rendered as `--key value` pairs appended to the command, in map insertion order.
- **Script (js/python)**: injected as in-scope variables through a generated prelude — `const`
  bindings for JS, module-level globals for Python. This matches the DSL's framing of script
  arguments as values "passed to the script," not as `process.argv` entries.

Rejected alternative: flatten the map's values into an argv array in the compiler. Simplest runner,
matches the original request text, but throws away the keys — `{message: "hi"}` and
`{greeting: "hi"}` would compile to identical env.

Rejected alternative: pass the JSON object opaquely as `argv[1]` and let the command parse it. No
rendering logic at all, but pushes JSON parsing into every workflow author's shell command.

**Ordering caveat:** map insertion order comes from Jackson's `LinkedHashMap` population during
parse, so it tracks document order for YAML/JSON definitions. Argument order for shell commands is
therefore document order, and must be covered by a test to keep it from regressing silently.

### Q5 — What does `stdin` mean here?

The original request framed "always pass the whole input to stdin" as a **v1 simplification**,
with `stdin: ${ .some.path }` (a partial jq expression) as deferred scope.

**Finding: DSL 1.0.0 has no `stdin` property on `run`.** `RunTaskConfiguration` exposes exactly
two members: `isAwait()` and `getReturn()`. A search of the entire `io.serverlessworkflow.api.types`
package for `stdin` returns nothing.

**Decision: passing the full transformed input to the subprocess's stdin is simply the design.**
There is no DSL field being under-honored, so there is nothing to defer and no caveat to record.
The deferred item from the original request is withdrawn as moot rather than carried as debt.

(The task-level `input.from` transformation still applies upstream, in the orchestrator, exactly as
it does for `call` tasks — so a workflow author *can* narrow what the step receives, just not
through a `run`-specific field.)

### Q6 — How does `return` map, and what happens on a non-zero exit?

`RunTaskConfiguration.ProcessReturnType` is an enum: `STDOUT`, `STDERR`, `CODE`, `ALL`, `NONE`,
with `STDOUT` as the DSL default. `RETURN` maps to it one-to-one.

**Decision: exit-code handling depends on what the author asked to observe.**

- `RETURN=code` or `RETURN=all` → a non-zero exit is **not** an error. Return it as data with
  `200 OK`. The author explicitly asked to see the exit code; turning it into a 502 would make the
  requested value unobservable and would trigger pointless retries of a command that is behaving
  exactly as intended.
- `RETURN=stdout`, `stderr`, or `none` → a non-zero exit is a failure. Map it like
  `dws-call-http`'s `UpstreamError` → `502` with `{task, exitCode, stderr}`, so the orchestrator
  retries.

This split is the one piece of genuinely `run`-specific semantics in the component; everything else
is a transcription of the existing contract.

### Q7 — How does `OUTPUT` interact with `RETURN`?

Two independent stages, composed:

1. `RETURN` picks the **raw value** — a stdout string, a stderr string, an exit-code int, or
   `{code, stdout, stderr}` for `all`.
2. `OUTPUT` shapes how that raw value folds into the response — `replace` (default) or `merge` —
   identical to how `dws-call-http` already shapes its upstream response.

For `RETURN=stdout`/`stderr` with `OUTPUT=replace`, the trimmed output is JSON-parsed first and
falls back to the raw string when it doesn't parse. `dws-call-http.shapeOutput()` hard-fails on
unparseable bodies; that's correct for HTTP, where a JSON content type was requested, but wrong
here — plain text is the *normal* output of a shell command.

**Note:** no DSL field maps to `OUTPUT` for `run` tasks (`HTTPArguments` has an `output` property;
`RunTaskConfiguration` does not). The compiler therefore never sets `OUTPUT`, and the image's
`replace` default always applies. The env var stays supported in the image for manual manifests and
for symmetry with the other step images.

### Q8 — What breaks in `dws-controller`?

`TaskKind.RUN` and `ImageCatalog.run()` are both singular, and now need to name one of three
images. `dws.io/step-type` labels are derived from `TaskKind`, so collapsing all three into `RUN`
would make it impossible to tell from cluster state which runtime a step is using — and
`StackReader` answers every `GET` from exactly that state, since there's no persistence layer.

**Decision: split both.** `TaskKind` gains `RUN_SHELL`, `RUN_SCRIPT_JS`, `RUN_SCRIPT_PYTHON`
(replacing `RUN`); `ImageCatalog`/`DwsConfig.Images` gain `runShell()`, `runScriptJs()`,
`runScriptPython()` (replacing `run()`).

Compile-time rejections `runStep()` must add, each with a message naming the offending subtype:

| Input | Outcome |
|---|---|
| `run.shell` | `RUN_SHELL` + `images.runShell()` |
| `run.script` with `language: js` | `RUN_SCRIPT_JS` + `images.runScriptJs()` |
| `run.script` with `language: python` | `RUN_SCRIPT_PYTHON` + `images.runScriptPython()` |
| `run.script` with any other `language` | `CompilationException` — unsupported language |
| `run.script` with `source:` instead of `code:` | `CompilationException` — external script sources out of scope |
| `run.container` | `CompilationException` — not yet supported |
| `run.workflow` | `CompilationException` — not yet supported |

`language` lives on the `Script` base class, which both `InlineScript` and `ExternalScript` extend,
so it is readable before deciding which of the two is present.

### Q9 — Does `dws-orchestrator` change?

**Decision: no.** `run` tasks reach `dws-run-*` through the same `CallServiceActivity` / Dapr
service-invocation path that `call` tasks already use — the task name kebab-cases to the Dapr
app-id, and the step is invoked at `POST /run`. That convention is name-derived, not kind-derived,
so it already covers `run` without modification.

"No changes to `dws-orchestrator`" is therefore an acceptance criterion, not just an observation:
if the implementation finds itself editing the orchestrator, an assumption in this design is wrong.

## Deferred, with reasons

| Item | Why deferred |
|---|---|
| `run.container` | Needs a Kubernetes-API security boundary; separate threat model |
| `run.workflow` | Belongs in `dws-orchestrator` as a child-workflow Activity, not a step image |
| `run.script.source` (external resource) | Requires fetch-at-compile-time (like `OpenApiDocumentFetcher`) plus a caching/immutability story |
| `await: false` | Orchestrator-side: whether `InterpreterWorkflow`/`CallServiceActivity` awaits the activity. Not expressible in a step image, which is synchronous by construction |

Withdrawn as moot: partial-input `stdin` (Q5 — no such DSL field).

## Acceptance criteria

- `cd dws-run && make test` green (Go race detector), and `make lint` clean.
- `cd dws-controller && ./mvnw test` green, including new `WorkflowCompilerTest` cases for: shell
  with arguments/environment; script js and python with arguments/environment/return; unsupported
  script language; `run.container`; `run.workflow`; and `run.script` with `source` instead of
  `code`.
- All three Dockerfiles build, and diffing them shows differences confined to the final-stage
  `FROM` and entrypoint.
- `dws-orchestrator` diff is empty.
