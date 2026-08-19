# Brainstorm — `try`/`catch`/`retry` (Phase 2, slice 1)

Raw capture of the brainstorming session. Decision log format: background → codebase findings →
decision chain Q1–Q10 → design trade-offs. `design.md` reorganises this into a structured design;
this file is the record of *how* we got there.

## Background

`docs/roadmap.md` Phase 2 is "Core Flow Completeness — `try`/`catch`/`retry`, `raise`, `fork`,
nested `do`". This is slice 1: `try`/`catch`/`retry` only.

Today `dws-orchestrator` recognises `try` and throws:

```java
} else if (task.getForTask() != null || task.getTryTask() != null) {
  throw new UnsupportedOperationException(
      "task '" + name + "' uses for/try, which is recognised but not yet interpreted");
}
```

Phase 1 (`2026-07-27-data-flow-pipeline`) landed the per-task data-flow pipeline and the workflow
`$context` document, which the roadmap called the prerequisite for this phase. That prerequisite is
met, so the fault surface can now be built on a real pipeline.

Locked scope from the requester (7 points): run the `try` list; filter caught errors statically and
dynamically with an exclusion escape hatch; expose the error under a configurable name; support an
inline or named retry policy with attempts/duration limits, backoff and jitter; run `catch.do` when
retries are exhausted and propagate with the existing fault shape otherwise; reuse the per-task
input/output/validation pipeline for both blocks; exclude `raise`, `fork`, and nested `do` for other
task types.

## Codebase findings (verified, not assumed)

Read before proposing anything:

1. **`InterpreterWorkflow.execute()` is a flat program-counter loop** over
   `WorkflowSupport.definition().getDo()` — a single `List<TaskItem>`, an `indexByName` map built
   from that one list, and `advance()` resolving `FlowOutcome` against it. There is no notion of a
   task *scope*; every jump target is a top-level index.
2. **`dispatch()` already wraps every task body** with the data-flow pipeline
   (`DataFlowInputActivity` → body → `DataFlowOutputActivity`), reading `input`/`output`/`export`
   off `TaskBase` via `DataFlowPipeline.baseOf(task)`. Requirement 6 ("both blocks go through the
   same pipeline") is therefore free *if* nested tasks are dispatched through this same method.
3. **`DefinitionLookup.taskByName()` only scans the top-level `do` list** and throws
   `"definition has no task named '<x>'"` otherwise. Every in-process activity
   (`EvaluateSetActivity`, `EvaluateSwitchActivity`, both data-flow activities) resolves its task
   this way, so nested tasks are invisible to all of them as written.
4. **`WorkflowCompiler.walk()` (dws-controller) also only walks the top-level list** — its own
   comment says `// switch/set/wait/for/try/raise (and do/fork) deploy nothing.` A `call` or `run`
   nested inside `try` compiles to **no** `StepService`, so the orchestrator would invoke a
   Dapr app-id that was never deployed.
5. **Only an exception's *message* survives the Dapr activity boundary** — established by
   `DataFlowException`, which folds task name, phase and detail into the message string for exactly
   this reason.
6. **`FlowOutcome`** flattens a `FlowDirective` to `{keyword, target}` because the SDK's generated
   one-of type does not round-trip through Jackson. Any new activity result carrying a directive
   must reuse it.
7. **`JqEvaluator.evaluate(expr, input, variables)`** already binds named jq variables
   (`Scope.setValue`), which Phase 1 added for `$context`. Binding a `$error` variable needs no new
   evaluator capability, only new call sites.

## Spec facts (from the OWS DSL 1.0 schema and reference, fetched and read)

- `tryTask` requires **both** `try` (a task list) and `catch`.
- `catch` = `errors.with` (an `errorFilter`), `as` (default `"error"`), `when`, `exceptWhen`,
  `retry` (an inline `retryPolicy` **or** a string naming one in `use.retries`), `do` (a task list),
  `then` (a flow directive for the error path, overriding the try task's own `then`).
- `errorFilter` filters on static values only: `type`, `status`, `instance`, `title`, `detail`
  (`minProperties: 1`). Dynamic filtering is what `catch.when` is for. These five fields are exactly
  the OWS `error` object's fields — so whatever error object we synthesise must carry them or the
  static filter has nothing to match against.
- `retryPolicy` = `when`, `exceptWhen`, `delay` (duration), `backoff`
  (`constant` | `exponential` | `linear`, one-of), `limit.attempt.count`,
  `limit.attempt.duration`, `limit.duration`, `jitter.from`/`jitter.to` (both required together).
- `use.retries` is `map[string, retryPolicy]` — the document-level reusable policy set.
- Flow directives "may only redirect to tasks declared within their own scope… they cannot target
  tasks at a different depth" — an explicit spec constraint on nested lists.

## Decision chain

### Q1 — Does the controller have to change, or is this orchestrator-only?

The requester framed the change as "in dws-orchestrator". Finding 4 says otherwise: the motivating
case for `try`/`catch`/`retry` is retrying a flaky HTTP call, and a `call` inside `try` deploys
nothing today. Orchestrator-only would ship a feature that works for `set`/`switch`/`wait` bodies
and silently 500s on the one body anyone wants to wrap.

Options put to the requester:
- **(a) Recurse in the controller** — `walk()` descends into `try` and `catch.do`; the change spans
  two components.
- **(b) Orchestrator only** — reject `call`/`run` inside `try` at compile time, defer recursion.

**Decision: (a).** Confirmed by the requester. The slice is worth shipping only if the body can do
I/O. Consequence: `dws-controller` is in scope, and a second capability is needed for the
compile-side behavior so later Phase 2 slices (`for`, `fork`) extend it rather than an
error-handling spec.

### Q2 — What is the unit of retry: the whole `try` block, or just the failing task?

The schema attaches `retry` to `catch`, and `catch` belongs to the *try task*, not to an inner task.
OWS wording is "retrying failed tasks before proceeding with alternate ones" — ambiguous in
isolation, but the policy's owner is unambiguous.

Options put to the requester:
- **(a) Re-run the whole `try` list** from its first task each attempt — matches "the retry policy
  retries the try task" and matches other DSL runtimes; side-effecting earlier tasks re-execute.
- **(b) Resume at the failing task** — avoids re-running side effects, diverges from the spec.

**Decision: (a).** Confirmed. Recorded as an explicit risk (re-executed side effects) rather than
smoothed over: a `try` block whose first task is non-idempotent will repeat it. The mitigation is
documentation, not machinery — the DSL gives the author the block boundary, so the author chooses
what is inside it.

### Q3 — How is `jitter` produced without breaking replay determinism?

`execute()` must stay replay-deterministic (no `Instant.now()`, no RNG) — the constraint Phase 1
already worked under. Jitter is by definition random.

Options put to the requester:
- **(a) Draw the random value inside an activity.** Dapr records the activity result in the instance
  history, so replay returns the recorded delay rather than re-drawing. Real randomness, still
  deterministic on replay.
- **(b) Derive from `hash(instanceId, attempt)`** — pure, but the "jitter" is fixed per instance and
  attempt, which defeats the thundering-herd purpose across replays of the same instance.
- **(c) Defer jitter entirely** to a later slice.

**Decision: (a).** Confirmed. This also settles where the whole retry *decision* lives: the same
activity that draws jitter can do the filter matching, condition evaluation, policy resolution,
limit accounting and backoff maths, and return one small verdict. That keeps every impure or
compute-heavy step out of the workflow method, exactly as `EvaluateSwitchActivity` does.

### Q4 — How does a nested task list get executed at all?

`execute()`'s loop is welded to the top-level list (finding 1). Two shapes considered:

- **(a) Extract the loop into a reusable `runTaskList(ctx, items, …)`** that takes any
  `List<TaskItem>` and returns a scope result; `execute()` calls it for `do`, the try dispatcher
  calls it for `try` and for `catch.do`. Recursion depth = nesting depth.
- **(b) Flatten nested lists into the top-level program at load time**, with synthetic jump targets.

**Chose (a).** (b) makes the program counter global across scopes, which directly contradicts the
spec's own scope rule for flow directives, and it makes lifecycle events and error `instance`
pointers lie about where a task lives. (a) is a mechanical extraction of code that already exists
and gives each scope its own `indexByName`, which *is* the scope rule.

Consequence: `end` vs `exit` finally differ. `end` terminates the instance from any depth; `exit`
completes the *current scope* — at top level that is the same thing, nested it returns to the
enclosing task. `runTaskList` must therefore return "how the scope ended" (ran off the end / `exit` /
`end`), not just data.

### Q5 — How do activities resolve a nested task by name?

Finding 3: every in-process activity resolves its task through `DefinitionLookup.taskByName()`
against the top-level list. Options:

- **(a) Keep passing the task name; make the lookup recursive** over `try`/`catch.do` lists, and
  require task names to be unique across the whole definition.
- **(b) Pass a path (e.g. `/do/2/try/0`)** instead of a name, so names need not be unique.

**Chose (a).** Uniqueness is already an unstated invariant of the platform: a `call` task's Dapr
app-id *is* its kebab-cased name (`TaskNaming`), so two tasks with the same name at different depths
would collide on a deployed Knative Service name regardless of anything the orchestrator does.
Making that invariant explicit and enforced at compile time is strictly better than routing around
it. (b) would also churn every existing activity request record for no benefit.

Consequence: the controller validates global task-name uniqueness and rejects duplicates with a
compile error — cheap, and it converts a confusing post-deployment collision into a `POST`-time
rejection.

### Q6 — What error object do we filter on, given Problem Details is Phase 3?

`errorFilter` matches on `type`/`status`/`instance`/`title`/`detail`. Today a task failure is a Java
`RuntimeException` whose message is the only thing that crosses the activity boundary (finding 5).
Something must synthesise the five fields.

Considered: (a) filter only on the message text; (b) build the full RFC 7807 taxonomy now;
(c) synthesise a **minimal** five-field error object now, with a small exception→type/status mapping,
and let Phase 3 formalise the taxonomy and the wire format.

**Chose (c).** (a) makes `errors.with.status: 503` — the example straight out of the spec reference —
unexpressible. (b) is explicitly Phase 3 and drags in the standard error-type catalogue, timeouts and
the wire format. (c) is the smallest thing that makes the *filter* meaningful, and it is the same
"minimal fault shape now, Problem Details later" split Phase 1 already made for `DataFlowException`.

Mapping agreed: `DataFlowException` → `validation` type; a step-service/service-invocation failure →
`communication` type (the step contract already reserves `502` for exactly this); anything else →
`runtime`. `status` is taken from the upstream HTTP status when one can be recovered, else a default
per type. `instance` is a JSON-Pointer-shaped path to the failing task. `detail` is the exception
message — which is why the message must stay self-contained.

### Q7 — Where is the caught error visible, and how?

Requirement 3: available by name (default `error`) to the recovery block **and** to the retry
condition. `catch.when`/`exceptWhen` and `retry.when`/`exceptWhen` are jq expressions; so are
`input.from`/`output.as`/`export.as` on every task inside `catch.do`.

Options: (a) merge the error into the `data` document; (b) write it into `$context`; (c) bind it as a
jq **variable** named by `catch.as`, threaded down the scope.

**Chose (c).** (a) corrupts the data document the recovery block is supposed to repair and would
leak into `ctx.complete(data)`. (b) is worse — `$context` persists for the whole instance, so the
error would outlive the `catch` block and be visible to unrelated later tasks. (c) matches the DSL
("the name of the runtime expression variable to save the error as"), and `JqEvaluator` already
supports arbitrary named variables (finding 7) — only the plumbing to carry a scope-local variable
map into the data-flow activities is new.

### Q8 — Inline vs named retry policy

`catch.retry` is a one-of: an inline policy object or a string naming one in `use.retries`.
No real fork here — both are required by requirement 4. Decision is only about failure behavior for
an unresolvable name: **fail loudly**, naming the missing policy, consistent with how Phase 1 rejects
`schema.external` rather than silently skipping it.

### Q9 — Which retry knobs ship in this slice?

- `delay`, `backoff.constant`/`linear`/`exponential`, `jitter.from`/`to`, `limit.attempt.count`,
  `limit.duration` — all in.
- `limit.attempt.duration` ("the maximum duration for each retry attempt") is a **per-attempt
  timeout**. Timeouts are Phase 3 and need cancellation machinery the interpreter does not have.
  Decision: **reject it loudly** at interpretation rather than accept and ignore it — accepting a
  timeout knob that does nothing is the "post-deployment mystery" failure mode this repo avoids.

Backoff maths agreed: `constant` → `delay`; `linear` → `delay × attempt`; `exponential` →
`delay × 2^(attempt−1)`. Jitter is a uniform draw in `[from, to]` added to the computed delay. The
OWS schema defines the three backoff kinds as empty objects, so there are no per-kind parameters to
read — the multiplier convention is ours and must be written down.

### Q10 — What about `raise`, `fork`, and nested `do` elsewhere?

Out, per requirement 7. Worth stating the consequence explicitly: without `raise`, the only way to
enter a `catch` is a genuine task failure, so the tests must fail a task for real (a step-service
error or a schema-validation failure) rather than raising a synthetic one. `for`/`fork` keep their
`UnsupportedOperationException`; the controller's recursion covers `try`/`catch.do` only, so a `call`
nested inside a `for` stays undeployable and unreachable in the same way it is today.

### Q11 — Late finding: the SDK model is narrower than the published schema

After the decisions above were settled, the shipped SDK types were disassembled (`javap` against
`serverlessworkflow-types:7.26.0.Final`, the version this repo pins) rather than assumed from the
schema text. Three gaps surfaced that change the design:

1. **`TryTaskCatch` has no `getThen()`.** The published OWS schema defines `catch.then`, but the
   generated Java model exposes only `errors`/`as`/`when`/`exceptWhen`/`retry`/`do`. There is no way
   to read a catch-path flow directive from a parsed definition.
   **Decision:** the handled path continues with the **try task's own `then`**, i.e. a caught-and-
   recovered `try` behaves like any other completed task. Documented as a known spec-vs-SDK gap,
   revisited when the SDK regenerates. Rejected alternative: read `catch.then` out of the task's
   free-form metadata — inventing a parallel syntax for one field is worse than the gap.
2. **`ErrorFilter` names the field `details`, the current spec names it `detail`.** The filter is
   matched against our synthesised error object, so the mapping is ours to define: SDK
   `ErrorFilter.getDetails()` is matched against the error object's `detail`. Called out so nobody
   "fixes" the apparent typo later.
3. **`ErrorFilter.getStatus()` and `RetryLimitAttempt.getCount()` are primitive `int`, not
   `Integer`** — an omitted value reads as `0`, indistinguishable from an explicit zero. Both are
   meaningless as `0` (no HTTP status 0; a zero-attempt retry policy is not a retry policy), so
   **`0` is treated as "not specified"**. Recorded because it is a silent-wrong-behaviour trap.

Also confirmed by disassembly: `ConstantBackoff`/`ExponentialBackOff`/`LinearBackoff` wrap types that
carry **no parameters at all** (free-form `additionalProperties` only), so the multiplier convention
from Q9 genuinely has to be ours; `catch.retry` is `Retry` with `getRetryPolicyDefinition()` /
`getRetryPolicyReference()`; `use.retries` is `UseRetries.getAdditionalProperties()` →
`Map<String, RetryPolicy>`; and every duration knob (`delay`, `jitter.from`/`to`, `limit.duration`)
is a `TimeoutAfter`, the exact type `InterpreterWorkflow.durationOf()` already converts.

The SDK also ships an `Error` type, but it is a *definition-side* model (one-of wrappers around
`ErrorType`/`ErrorInstance`/`ErrorTitle`/`ErrorDetails`) built for authoring `raise`, not for
carrying a runtime error value. The runtime error is therefore a plain Jackson `ObjectNode` with the
five fields — which is also what has to reach jq as `$error` anyway.

## Design trade-offs accepted

- **Re-running the whole `try` block re-runs side effects** (Q2). Accepted as spec-conformant;
  documented rather than mitigated in code.
- **Recursion depth is unbounded** in `runTaskList`. The existing `MAX_STEPS` guard counts steps in
  one scope only. Add a nesting-depth cap so a pathological definition fails fast instead of blowing
  the stack — cheap, same spirit as `MAX_STEPS`.
- **Global task-name uniqueness becomes a hard rule** (Q5). It was already implied by app-id
  derivation; making it explicit could reject a definition that "worked" before only because its
  duplicate names were in unreachable nested lists that never compiled to anything. Acceptable — such
  a definition was already broken.
- **The retry decision is one activity record per failed attempt** (Q3). Instance history grows with
  attempt count. Inherent to doing anything impure replay-safely; bounded by `limit`.
- **A minimal error object now means a second pass in Phase 3** (Q6). Accepted: the five fields are
  the DSL's own, so Phase 3 enriches the object rather than replacing the concept.

## Agreed approach

Extract a scope-aware `runTaskList` from `execute()`; dispatch `try` through it; on failure consult a
single in-process `CatchDecisionActivity` that synthesises the error object, applies the static
filter, evaluates `when`/`exceptWhen`, resolves the (inline or named) retry policy, enforces limits
and returns either "not caught" (rethrow), "retry after *d*" (durable timer, then re-run the whole
`try` list), or "handled" (run `catch.do` in the same scope-aware runner with the error bound as a jq
variable, then follow `catch.then`). In parallel, `dws-controller` recurses into `try`/`catch.do`
when walking tasks, so nested `call`/`run` deploy their step services, and rejects duplicate task
names.
