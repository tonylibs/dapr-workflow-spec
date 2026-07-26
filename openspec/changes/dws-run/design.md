## Context

`dws-controller` compiles four task families into deployable resources. Three of them work
end-to-end: `call: http` and `call: openapi` map to prebuilt step images that exist in this repo,
and `switch`/`set`/`wait`/`for`/`try`/`raise` are interpreted in-process by `dws-orchestrator` with
nothing deployed. The fourth, `run`, is compiled but not deployable.

`WorkflowCompiler.runStep()` (`dws-controller/.../compile/WorkflowCompiler.java:214`) extracts
`COMMAND` from `run.shell` and `SCRIPT` from `run.script`'s inline code, then emits
`new StepService(Names.kebab(taskName), TaskKind.RUN, images.run(), env)`. Three gaps:

1. `dws.images.run` defaults to `sw-run:1.0` (`application.yaml:11`) — a placeholder. Nothing in
   this repo or in `ghcr.io/tonylibs/*` builds it.
2. `run.container` and `run.workflow` match neither branch and produce a `StepService` with an
   empty env map — a Knative Service that deploys and then fails with nothing pointing back at the
   definition.
3. Everything past the entrypoint is unspecified: `arguments`, `environment`, `return`, exit-code
   semantics, and `language` handling are all absent.

**Constraints inherited from the monorepo.** The step-service HTTP contract (`POST /run` with the
workflow data as the JSON body, `GET /healthz`, `OUTPUT=replace|merge`, and `502` — not `400`/`500`
— for failures so the orchestrator retries) is already implemented independently by `dws-call-http`
and `dws-call-openapi`. There is no persistence layer anywhere: `StackReader` answers every `GET`
from live cluster state selected by `dws.io/*` labels, so anything an operator needs to know about
a deployed step must be visible in its labels. Routing is name-derived: a task's Dapr app-id is its
kebab-cased name, and `with.endpoint` is schema-required but ignored.

**DSL facts, verified against `io.serverlessworkflow:serverlessworkflow-types:7.26.0.Final`** (the
version pinned in `dws-controller/pom.xml`) rather than assumed:

- `RunTaskConfiguration` exposes exactly two members: `isAwait()` and `getReturn()`. There is **no
  `stdin` property** anywhere in `io.serverlessworkflow.api.types`.
- `ProcessReturnType` is `STDOUT | STDERR | CODE | ALL | NONE`, defaulting to `STDOUT`.
- `ShellArguments` and `ScriptArguments` both expose `getAdditionalProperties() ->
  Map<String, Object>` — `arguments` is a **map**, not a list.
- `language` lives on the `Script` base class, which both `InlineScript` (`getCode()`) and
  `ExternalScript` (`getSource()`) extend, so it is readable before branching on which is present.
- `RunTask` inherits `getTimeout()` from `TaskBase`; `RunTaskConfiguration` has no `output`
  property (unlike `HTTPArguments`).

## Goals / Non-Goals

**Goals:**

- Make `run: shell` and `run: script` (languages `js` and `python`) deployable end-to-end.
- Build the missing step image as a new Go component, `dws-run`, following `dws-call-http`'s
  structure closely enough that the shared step-service contract stays a real invariant rather than
  a coincidence between two files.
- Keep a deployed step's runtime identifiable from cluster state alone, via `dws.io/step-type`.
- Convert the two unsupported `run` subtypes from silent half-implementations into compile-time
  rejections with messages that name the offending subtype.
- Keep the `dws-orchestrator` change minimal: one dispatch branch reusing the existing
  `CallServiceActivity`, plus a task-type label. (Originally stated as "leave it untouched" — see D8
  for why that was wrong.)

**Non-Goals:**

- `run.container` — spawning arbitrary user-specified images requires Kubernetes API access from
  the step or controller-side pod templating, a materially different security boundary that needs
  its own threat model.
- `run.workflow` — DWS-internal cross-workflow invocation belongs in `dws-orchestrator` as a local
  activity starting a child workflow instance, not in a step image reached over Dapr service
  invocation.
- `run.script.source` (external resource) — needs fetch-at-compile-time (as
  `OpenApiDocumentFetcher` does) plus a caching and content-addressing story.
- `await: false` — a step image is synchronous by construction; honoring it means changing whether
  `InterpreterWorkflow`/`CallServiceActivity` awaits the activity.
- Any change to `call: http` / `call: openapi` behavior or contract.

## Decisions

### D1: Cover `shell` and `script` only, and reject the rest at compile time

- **Choice**: implement `run.shell` and `run.script` (`js`, `python`). `run.container`,
  `run.workflow`, unsupported script languages, and `run.script.source` each throw
  `CompilationException` with a message naming what was rejected.
- **Rationale**: deferring the two hard subtypes is only safe if the compiler rejects them.
  Today's fall-through turns a definition error into a post-deployment mystery — the exact failure
  mode this codebase avoids elsewhere by validating at compile time.
- **Alternatives considered**: implement all four now (rejected — `container` and `workflow` belong
  to different security and component boundaries and would each expand this change severalfold);
  leave the fall-through in place and document the limitation (rejected — documentation does not
  reach the operator staring at an `ImagePullBackOff`).

### D2: One Go codebase, three images

- **Choice**: a single `dws-run` Go module produces three images from multi-stage Dockerfiles
  sharing the Go build stage, differing only in final-stage `FROM` and exec command.

  | Image | Base | Exec |
  |---|---|---|
  | `dws-run-shell` | distroless-equivalent minimal (mirrors `dws-call-http`) | `sh -c "$COMMAND"` |
  | `dws-run-script-js` | `node:<pinned>-slim` (ES2024, per DSL) | `node` |
  | `dws-run-script-python` | `python:3.13-slim` (DSL requires 3.13.x) | `python3` |

- **Rationale**: the three runtimes need genuinely different base layers, but share an identical
  HTTP surface, config parser, output shaper, and error mapper. One codebase keeps the shared
  contract in one place; three images keep the shell step's cold start cheap, which is what Knative
  scale-to-zero is most sensitive to.
- **Alternatives considered**: one image containing all three runtimes selected by env var
  (rejected — the most common and most latency-sensitive step, shell, would pay Node + Python pull
  cost on every cold start); three independent components (rejected — triplicates the step-service
  contract, the one thing this repo works hardest to keep consistent).

### D3: `ARGUMENTS` keeps the DSL's map shape and is rendered per runtime

- **Choice**: `ARGUMENTS` is a JSON **object**. Shell renders it as `--key value` pairs appended to
  the command in map insertion order. Script images inject it as in-scope variables through a
  generated prelude — `const` bindings for JS, module-level globals for Python.
- **Rationale**: the SDK models `arguments` as `Map<String, Object>`, and the DSL frames script
  arguments as values *passed to the script*, not as `process.argv` entries. An array contract
  would silently discard the argument names the workflow author wrote — `{message: "hi"}` and
  `{greeting: "hi"}` would compile to identical env.
- **Alternatives considered**: flatten the map's values into a JSON array of strings and pass as
  argv (rejected — simplest runner, but loses the keys); pass the object opaquely as `argv[1]` and
  let the command parse it (rejected — pushes JSON parsing into every workflow author's shell
  command).
- **Ordering**: insertion order comes from Jackson's `LinkedHashMap` population during parse, so it
  tracks document order. Shell argument order is therefore document order, and needs a test to keep
  it from regressing silently.

### D4: Full transformed input goes to the subprocess's stdin

- **Choice**: `dws-run` writes the entire `POST /run` body — the current workflow data, JSON-encoded
  — to the subprocess's stdin, unconditionally.
- **Rationale**: DSL 1.0.0 has no `stdin` property on `run`, so there is no author-supplied
  expression to honor or under-honor. This is the design, not a simplification with deferred debt.
  A workflow author who needs to narrow what the step sees uses the task-level `input.from`
  transformation, which the orchestrator already applies upstream exactly as it does for `call`
  tasks.
- **Alternatives considered**: an image-side jq evaluator over a `STDIN_EXPR` env var (rejected —
  invents a config surface with no DSL field behind it, and duplicates evaluation logic that
  already lives in the orchestrator).

### D5: Exit-code handling depends on what the author asked to observe

- **Choice**:
  - `RETURN=code` or `RETURN=all` → a non-zero exit is **not** an error. Return it as data with
    `200 OK`.
  - `RETURN=stdout`, `stderr`, or `none` → a non-zero exit is a failure. Map it like
    `dws-call-http`'s `UpstreamError` → `502` with `{task, exitCode, stderr}`, so the orchestrator's
    retry policy re-invokes the step.
- **Rationale**: an author who wrote `return: code` is explicitly asking to observe the exit code.
  Turning a non-zero exit into a 502 would make the requested value unobservable and would retry a
  command that is behaving exactly as intended.
- **Alternatives considered**: always 502 on non-zero (rejected — makes `return: code` and
  `return: all` useless); never 502 on non-zero (rejected — a failing shell step would silently
  pass its failure downstream as data, and the orchestrator's retry policy would never engage).
- This is the only genuinely `run`-specific semantics in the component; everything else is a
  transcription of the existing contract.

### D6: `RETURN` and `OUTPUT` compose as two independent stages

- **Choice**: `RETURN` picks the raw value (a stdout string, a stderr string, an exit-code int, or
  `{code, stdout, stderr}` for `all`); `OUTPUT` then shapes how that value folds into the response
  (`replace` default, or `merge`), identically to `dws-call-http`.
- **Rationale**: keeps `OUTPUT` meaning exactly one thing across all three step images.
- **JSON-parse fallback**: for `RETURN=stdout`/`stderr` with `OUTPUT=replace`, the trimmed output is
  JSON-parsed first and falls back to the raw string when it doesn't parse.
  `dws-call-http.shapeOutput()` hard-fails on unparseable bodies — correct for HTTP, where a JSON
  content type was requested, but wrong here, where plain text is the *normal* output of a shell
  command.
- **No DSL source for `OUTPUT`**: `RunTaskConfiguration` has no `output` property, so the compiler
  never sets `OUTPUT` and the image's `replace` default always applies. The env var stays supported
  for hand-written manifests and for symmetry with the other step images.

### D7: Split `TaskKind.RUN` and `ImageCatalog.run()` three ways

- **Choice**: `TaskKind` gains `RUN_SHELL`, `RUN_SCRIPT_JS`, `RUN_SCRIPT_PYTHON` (replacing `RUN`);
  `ImageCatalog` and `DwsConfig.Images` gain `runShell()`, `runScriptJs()`, `runScriptPython()`
  (replacing `run()`); `application.yaml`'s `dws.images.run` becomes three keys.
- **Rationale**: `dws.io/step-type` labels are derived from `TaskKind`, and with no persistence
  layer those labels are the only record of what a deployed step is. A single `RUN` kind would make
  a step's runtime unreadable from cluster state.
- **Alternatives considered**: keep one `RUN` kind and distinguish by image reference (rejected —
  requires parsing an image string to answer "what runtime is this step", and breaks when a
  registry override renames the image).

### D8: `dws-orchestrator` needs one dispatch branch — the original decision was wrong

- **Original choice (WRONG, corrected during implementation)**: `run` tasks reach `dws-run-*`
  through the same `CallServiceActivity` / Dapr service-invocation path that `call` tasks already
  use, with **no orchestrator change at all**. The stated rationale was that routing is derived from
  the kebab-cased task name rather than the task kind, so `run` was already covered.
- **Why that was wrong**: name-derived routing is true *inside* `CallServiceActivity` — but
  *reaching* that activity requires satisfying `task.getCallTask() != null` in
  `InterpreterWorkflow.dispatch()`. `Task.getRunTask()` is a distinct getter (verified via `javap` on
  `serverlessworkflow-types:7.26.0.Final`), so a `run` task matched no branch and fell through to
  `throw new IllegalStateException("task '<name>' has an unsupported type")`. The controller would
  have deployed a healthy `dws-run` Knative Service that the orchestrator failed the instance before
  ever invoking — the step sitting at scale-zero, never called.
- **Corrected choice**: add a `getRunTask()` branch to `dispatch()` that reuses the existing
  `CallServiceActivity` and `CallRequest` unchanged, plus a `taskTypeOf()` case returning `"run"` so
  `io.dws.task.*` lifecycle events label it correctly instead of `"unknown"`. No new activity, no
  change to the `call` path, no change to the step-service contract. The name-derived routing claim
  holds once the branch exists.
- **How the error survived to final review**: the design made "empty `dws-orchestrator` diff" an
  **acceptance criterion**, which inverted the intended tripwire. Every task dutifully confirmed the
  diff was empty, and that confirmation was read as evidence the assumption held rather than as the
  thing still needing proof. A criterion asserting the *absence* of a change can only ever confirm
  itself; the assumption should have been verified by tracing the dispatch path, or by an end-to-end
  test exercising a `run` task through the interpreter. The orchestrator had zero `run` coverage, so
  nothing caught it.
- **Lesson for future changes**: "component X needs no change" is a claim to be proven by reading
  X's code, never an acceptance criterion to be satisfied by a clean `git diff`.

## Risks / Trade-offs

- **[Risk]** Shell argument order depends on Jackson map insertion order, which is a parser
  implementation detail rather than a documented DSL guarantee. → **Mitigation**: pin it with a
  `WorkflowCompilerTest` case asserting multi-argument order, and a `dws-run` runner test asserting
  the rendered argv, so a Jackson upgrade that changed it would fail CI rather than silently reorder
  a production command.
- **[Risk]** `sh -c "$COMMAND"` executes an operator-authored command string with `ARGUMENTS`
  appended. A workflow author who can post a definition can already run arbitrary code by design —
  that is what `run: shell` *is* — but argument rendering must not let a value break out of its
  position. → **Mitigation**: render arguments as separate argv entries passed to `sh -c`'s
  positional parameters rather than string-concatenating them into the command, and cover
  shell-metacharacter values with a test.
- **[Risk]** Script preludes inject author-supplied argument names as identifiers. A name like
  `class` or `1foo` is a valid map key but not a valid JS/Python identifier. → **Mitigation**:
  validate argument names against the target language's identifier rules at compile time in
  `runStep()`, and reject with `CompilationException` — a definition error caught at post time, not
  a syntax error inside a deployed container.
- **[Risk]** Three images means three Dockerfiles that can drift apart. → **Mitigation**: the
  acceptance criterion is that diffing them shows differences confined to the final-stage `FROM` and
  entrypoint; the shared Go build stage is byte-identical.
- **[Trade-off]** Splitting `dws.images.run` into three config keys breaks any operator override of
  that key. → Accepted: the current value is a placeholder pointing at an image that does not
  exist, so no working deployment can be relying on it.
- **[Trade-off]** Node and Python base images are substantially larger than `dws-call-http`'s
  distroless static base, so script steps have slower cold starts than shell steps. → Accepted:
  unavoidable given the runtimes, and confined to the steps that actually need them — which is the
  whole point of D2.
- **[Trade-off]** `RETURN=code`/`all` returning `200` on a non-zero exit means a genuinely broken
  command can pass unnoticed if the author isn't inspecting the code downstream. → Accepted: the
  author opted into observing the code; a `switch` on it is the DSL-native way to branch.

## Migration Plan

1. **Build and publish first.** Merge `dws-run` and its CI workflow before the `dws-controller`
   changes, so `ghcr.io/tonylibs/dws-run-shell`, `dws-run-script-js`, and `dws-run-script-python`
   exist by the time the compiler starts referencing them. (Within a single PR, this is commit
   ordering; the images publish on merge to `main`.)
2. **Then switch the compiler.** `TaskKind`, `ImageCatalog`, `DwsConfig.Images`, `runStep()`, and
   `application.yaml` change together — they will not compile independently.
3. **Operator action**: anyone overriding `dws.images.run` must migrate to `dws.images.run-shell`,
   `dws.images.run-script-js`, and `dws.images.run-script-python`. Since the old default was a
   placeholder, this affects no working deployment.
4. **Redeploy of existing stacks**: `run` steps are not currently running anywhere (no image
   exists), so there is nothing to drain or reconcile. Knative Service names are not
   version-suffixed, so a redeploy updates in place as usual.
5. **Rollback**: revert the `dws-controller` commits. Definitions using `run` return to compiling
   into an undeployable stack — the pre-change state. The published images are inert if unreferenced
   and need no rollback. Definitions using `run.container`/`run.workflow` would go back to compiling
   silently, which is a regression to the old broken behavior rather than a new failure.

## Resolved Questions

- **Node base image version.** Resolved: `node:24-slim`, matching `dws-call-openapi`'s Node 24
  toolchain. The DSL requires ES2024, which Node 20+ satisfies, so no newer pin was needed.
  `Dockerfile.script-js` uses this base.
- **`TIMEOUT` source.** Resolved: `TIMEOUT` is **not** forwarded for `run` tasks. `RunTask` inherits
  `getTimeout()` (a `TaskTimeout`) from `TaskBase`, but the existing `call: http` path
  (`WorkflowCompiler.httpStep()`, `WorkflowCompiler.java:203-204`) forwards `CallTask.getTimeout()`
  via `toJson(call.getTimeout())`, which serializes the `TaskTimeout` object to JSON — not a Go
  duration string like `30s`. Reusing that same serialization for `run` would hand `dws-run` a
  `TIMEOUT` value it cannot parse. Rather than invent a new duration-formatting path for `run` alone,
  `runStep()`/`scriptStep()` leave `TIMEOUT` unset, and `dws-run`'s own 30s default applies. Impact
  scope: one env var, defaulted in the image either way; no compiler code forwards it.
