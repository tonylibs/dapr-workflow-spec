# Brainstorm — `for` (Phase 2, slice 2.3)

Raw capture of the brainstorming session. Decision log format: background → codebase findings →
SDK findings → decision chain Q1–Q10 → design trade-offs. `design.md` reorganises this into a
structured design; this file is the record of *how* we got there.

## Background

`docs/roadmaps/openworkflow-features.md` Phase 2 is "Core Flow Completeness". Slices 2.1
(`try`/`catch`/`retry`, `openspec/changes/try-catch-retry`, merged) and 2.2 (`raise`,
`openspec/changes/raise-task`, merged) are done. Slice **2.3** is `for`. Slice 2.4 (`fork` +
generalised nested `do`) is later.

Today `dws-orchestrator` recognises `for` and throws:

```java
case ForTask _ ->
    throw new UnsupportedOperationException(
        "task '" + name + "' uses for, which is recognised but not yet interpreted");
```

Slice 2.1 landed the reusable scope-aware task-list runner (`runTaskList`) and the depth guard
(`MAX_DEPTH`); slice 2.2 shipped the `raise`/`RaisedErrorException` machinery. Both give this slice
what it needs to reuse rather than build: an iteration is just another scoped `runTaskList`
invocation with a couple of extra jq variable bindings on top of `catch.do`'s pattern.

Locked scope from the requester (5 points): implement `for.each`/`for.in`/`for.at` with defaults,
run `for.do` once per element with `$<each>`/`$<at>` bound in scope, evaluate the sibling `while`
between iterations and stop on false, keep controller untouched, cover with integration cases.
`fork` and generalising nested `do` (e.g. `call`/`run` inside `for.do`) are out.

## Codebase findings (verified, not assumed)

Read before proposing anything:

1. **`InterpreterWorkflow.dispatchBody` already dispatches on `task.getForTask()`**
   (`InterpreterWorkflow.java:248`). It routes into `dispatchConcreteTask`, whose `case ForTask _`
   at lines 343–345 throws `UnsupportedOperationException`. This is the one stub to replace.
2. **`taskTypeOf` already returns `"for"`** at lines 482–483, so lifecycle event labelling is
   already correct — no change needed there.
3. **`runTaskList` (lines 95–158)** takes `List<TaskItem> items`, `JsonNode data`,
   `JsonNode context`, `Map<String, JsonNode> variables`, `int depth`, and returns a `ScopeResult`.
   It already enforces `MAX_DEPTH` (16) and `MAX_STEPS` (10 000) per call — one call = one scope.
4. **`recover` (lines 442–464) is the exact pattern to copy** for scope-local variable binding:
   `Map<String, JsonNode> scoped = new HashMap<>(variables); scoped.put(name, value);` then
   `runTaskList(..., scoped, depth + 1, ...)`.
5. **`DefinitionLookup.search` recurses into `TryTask.getTry()` and `TryTask.getCatch().getDo()`
   only** (`DefinitionLookup.java:31–52`). It does NOT recurse into `ForTask.getDo()`. Every
   in-process activity (`EvaluateSetActivity`, `EvaluateSwitchActivity`, `RaiseErrorActivity`, both
   data-flow activities) resolves its task through this method, so a `set`/`switch`/`raise` nested
   inside `for.do` is currently invisible to the lookup and would fail with `"definition has no
   task named '<x>'"`. This must be extended.
6. **`JqEvaluator.evaluateBoolean(expr, input, variables)` already exists** (lines 108–123) with
   proper jq truthiness (`null`/`false` → false, everything else → true) and named-variable
   binding. Reusable verbatim for `while`.
7. **`JqEvaluator.unwrap`** (lines 145–154) strips a `${...}` wrapper if present, else returns the
   raw expression. `for.in` written as `.pets` or `${ .pets }` both work through
   `JqEvaluator.evaluate(...)` unchanged.
8. **`WorkflowCompiler.walk()`** (`dws-controller`) — its existing comment reads
   `switch/set/wait/for/raise (and the task lists nested under for/fork) deploy nothing.` So `for`
   itself is a no-op to deploy AND `for.do`'s inner tasks are NOT walked (a `call`/`run` inside
   `for.do` compiles to no `StepService`). No controller change is needed for this slice; nested
   `call`/`run` under `for.do` is 2.4's problem, not this one.
9. **Every activity's request record implements `StepRequest`** and is a Jackson-serialisable
   `record`. Existing pattern for a jq-eval activity: `(String taskName, JsonNode data,
   Map<String, JsonNode> variables)`.
10. **`WorkflowSupport.jq()`, `WorkflowSupport.mapper()`, `WorkflowSupport.definition()`,
    `WorkflowSupport.defaultTaskOptions()`** are the pod-scoped globals every activity already
    reads. No new plumbing needed.

## SDK findings (verified with `javap` against `serverlessworkflow-types:7.26.0.Final`)

Ran `javap -classpath …/serverlessworkflow-types-7.26.0.Final.jar
io.serverlessworkflow.api.types.ForTask io.serverlessworkflow.api.types.ForTaskConfiguration`:

```
public class ForTask extends TaskBase {
  public ForTaskConfiguration getFor();
  public java.lang.String getWhile();
  public java.util.List<TaskItem> getDo();
  ...
}
public class ForTaskConfiguration {
  public java.lang.String getEach();
  public java.lang.String getIn();
  public java.lang.String getAt();
}
```

Consequences:

- `ForTask extends TaskBase` — the existing data-flow pipeline (`dispatch`'s `input`/`output`/
  `export` wrap) applies to `for` unchanged, exactly like `try` and `raise`.
- `each`/`at` are **plain names** (no expression). Defaults per DSL 1.0 spec: `"item"` and
  `"index"`. Applied at read time in the activity/interpreter.
- `in` is a **plain string** — always a jq expression. Unlike `raise.error.type` which is a
  literal-or-expression one-of, `for.in` has no literal branch: whatever the author writes is a jq
  expression. `JqEvaluator.evaluate(in, data, variables)` handles it, `${...}` wrapper optional
  (see finding 7).
- `while` is a **plain string on `ForTask` (sibling of `for`, not nested under it)** — jq
  expression evaluated for truthiness. `JqEvaluator.evaluateBoolean` covers it.
- `do` is `List<TaskItem>` — feeds straight into `runTaskList`.
- No `TryTaskCatch`-style wrapper; `for` is much simpler than `try`.

## Decision chain

### Q1 — Controller change: needed or not?

Finding 8 confirms `WorkflowCompiler.walk()`'s existing comment already excludes both `for` itself
and everything under `for.do` from what it deploys. The slice's non-goal explicitly excludes
generalising nested `do` (that's 2.4). So a definition using `for` with only in-process bodies
(`set`/`switch`/`raise`/nested `for`/nested `try`) works out of the box; a definition trying to nest
`call`/`run` under `for.do` will *still* fail at runtime because no `StepService` was deployed —
consistent with today's behaviour, and to be closed by 2.4.

**Decision: no controller change.** Confirmed by reading the code, not assumed. Same finding as
`raise-task`'s D-nothing-for-controller.

### Q2 — Where does `for.in` get evaluated: in the workflow method or in an activity?

Every other jq eval in this codebase (`set`, `switch`, `catch`, `raise`) runs inside an activity
precisely so the workflow method's replay loop stays free of computation Dapr didn't record. Doing
`for.in`'s eval in the workflow method would break that pattern for no gain.

**Decision: a dedicated in-process activity, `EvaluateForActivity`, evaluates `for.in` once at
the start of the loop and returns the collection as a `JsonNode` (an array).** Same shape as
`EvaluateSwitchActivity`/`EvaluateSetActivity`/`RaiseErrorActivity`.

- **Alternative — reuse `EvaluateSetActivity`**: rejected. `EvaluateSetActivity` evaluates a
  structured `set` map, not a bare jq expression on `for.in`. Different input contract, wrong
  reuse.

### Q3 — Where does `while` get evaluated?

`while` re-evaluates each iteration with the current data and the iteration variables
(`$<each>`/`$<at>`) bound in scope. It's a boolean jq expression, exactly what
`JqEvaluator.evaluateBoolean` handles.

Two options considered:
- **(a) Fold `while` into `EvaluateForActivity` so a single activity call decides the whole loop
  up front.** Impossible: `while` may reference each iteration's own data (which is only known
  after that iteration's body ran, because iterations thread data), and the iteration variables
  themselves. It cannot be pre-evaluated.
- **(b) Add a separate activity `EvaluateWhileActivity` invoked per iteration**, taking
  `(taskName, data, variables)` and returning a boolean.

**Decision: (b), a per-iteration `EvaluateWhileActivity`.** One additional activity call per
iteration is a fixed, small cost — the same one-crossing-per-decision shape `EvaluateSwitchActivity`
already accepted. When `while` is absent the workflow method skips the call entirely (see Q7).

- **Alternative — inline `JqEvaluator.evaluateBoolean` inside the workflow method**: rejected.
  Breaks the "no jq eval in the replay loop" invariant that every other task type respects, for a
  single-line saving.

### Q4 — How is data threaded between iterations?

The requester's own question. The DSL 1.0 spec's example is illustrative:

```yaml
checkup:
  for: { each: pet, in: .pets, at: index }
  while: .vet != null
  do:
    - waitForCheckup:
        listen: { ... }
        output: { as: '.pets + [{ "id": $pet.id }]' }
```

`output.as` accumulates onto `.pets` — meaning `.pets` (in `data`) is *there* to accumulate onto
because the previous iteration's body output became this iteration's input `data`. The natural
reading is: **each iteration's `runTaskList` result becomes the next iteration's input `data`**,
identical to how sequential tasks in a `do` list thread data.

**Decision: iterations thread data forward.** The final iteration's output data is the `for`
task's body-output `data`, which then flows through the `for` task's *own* `output.as`/`export.as`
if declared (already provided by `dispatch`'s data-flow pipeline wrap around `dispatchBody`,
because `ForTask extends TaskBase`).

- **Alternative — each iteration starts from the same immutable input `data`, accumulating a
  parallel array of per-iteration outputs**: rejected. It's a `fork`/`map` semantics (parallel
  branches), which is slice 2.4's problem. `for` is sequential iteration; threading data forward
  is the sequential reading of "the DSL's own do-list convention applied element-by-element".

### Q5 — What's the iteration cap?

`MAX_STEPS` (10 000) guards runaway task-list execution *within one scope*. Each iteration invokes
`runTaskList` afresh, which starts its own `steps` counter at 0. So `MAX_STEPS` bounds the size of
one iteration's body, not the number of iterations.

Is that a gap? Only if a `for.in` could be unbounded. Per SDK, `for.in` is a jq expression, and jq
expressions over a finite input document produce finite arrays. `while` provides early-exit but
cannot extend beyond `for.in`'s length (the spec makes `in` required and models `for` as one pass
through the collection). So iteration count is bounded by `data`-derived array length, itself
bounded by whatever produced `data` (a step response, the workflow input, etc.).

**Decision: no new iteration cap.** `MAX_STEPS`'s existing purpose (a per-scope loop guard) is
preserved for `for.do`'s body scope; `MAX_DEPTH`'s existing purpose (call-stack nesting) is
preserved because each `for.do` iteration is `depth + 1` regardless of how many iterations happen
(iterations happen at the same depth — they are siblings in the workflow-method's iteration
loop, not nested).

- **Alternative — add `MAX_ITERATIONS` (say, 100 000)** to defend against a definition whose
  `for.in` evaluates to a pathologically large array: rejected as speculative. If it becomes a real
  problem, add it then; today it would be a guard against nothing observed.

### Q6 — `while` before or after the body?

Per DSL 1.0 spec: `while` is a *loop condition* — the loop stops when it becomes false, semantically
before running the next iteration. So the evaluation order per iteration is: **bind vars →
evaluate `while` (if declared) → if false, stop; else run body → advance**.

`while`'s evaluation must see the current iteration's variables (`$<each>`/`$<at>`) and the
current `data`. This matches "re-evaluated each iteration" in the spec.

**Decision: check `while` at the *top* of each iteration**, after binding `$<each>`/`$<at>` but
before running `for.do`. Consistent with `while(cond) { body; }` in every language the DSL author
is likely to have written in.

- **Alternative — check `while` at the end of an iteration (do-while shape)**: rejected. Contra
  spec; would run the body even when `while` was already false at entry.

### Q7 — What if `while` is absent?

Per spec, `while` is optional. When absent, iteration is bounded only by `for.in`'s length.

**Decision: when `while` is null/blank, skip the `EvaluateWhileActivity` call entirely** — no
activity crossing per iteration. Preserves the "small fixed cost" property of Q3 for the common
case (which is no `while`).

### Q8 — `DefinitionLookup.search` recursion into `for.do`

Finding 5: today `search` recurses into `try.try`/`try.catch.do` only. A `set`/`switch`/`raise`
nested inside `for.do` would fail lookup. The `for` slice cannot ship without extending the
search — otherwise even the trivial "iterate and `set` a field per element" case fails.

**Decision: extend `DefinitionLookup.search` to also recurse into `ForTask.getDo()`.** One added
branch in the same shape as the existing `TryTask` branch. Note that `fork` and general nested `do`
are 2.4's problem — 2.3 only adds the one recursion path the one new task type requires. This is
the minimum consistent with the finding.

- **Alternative — build a general-purpose "recurse into any container task" walker now**:
  rejected. Doing that correctly requires knowing what the containers are, and 2.4's `fork` is the
  container we don't yet know the shape of. YAGNI.

### Q9 — Where does the iteration loop live: workflow method or activity?

Options:
- **(a) Workflow method** loops, calling `EvaluateWhileActivity` and `runTaskList` per iteration.
- **(b) A super-activity** that runs the whole loop internally.

(b) is impossible: `runTaskList` is a workflow-method helper that itself calls activities and uses
`ctx.callActivity(...)`/`ctx.createTimer(...)`. Only the workflow method can do that.

**Decision: (a), loop in the workflow method** as a new private helper method `dispatchFor`,
mirroring `dispatchTry`'s shape (one method, called from `dispatchConcreteTask`'s `case ForTask ->`
branch). Uses `depth + 1` for each `runTaskList` invocation (each iteration's body is its own
scope, siblings at the same depth). Determinism on replay is fine: every crossing is a
`ctx.callActivity(...)` or `ctx.createTimer(...)` Dapr has already recorded.

### Q10 — Variable naming: default values applied where?

`each`/`at` default to `"item"`/`"index"` per DSL 1.0 spec. The defaults could live in the activity
(that returns the pre-resolved names to the workflow method) or in the workflow method itself
(reading `for.each`/`for.at` directly off the `ForTask`).

The workflow method already has the `ForTask` in hand (it's the switch-case payload). Reading
`for.each` and `for.at` with a `null`/blank fallback to the defaults is one line each; needing an
activity round-trip for two `String` lookups is overkill.

**Decision: defaults applied in the workflow method, in `dispatchFor`.** The activities
(`EvaluateForActivity`/`EvaluateWhileActivity`) never need to know the variable names — they take
`variables` pre-bound and evaluate against that scope. The workflow method binds them once at the
top of each iteration.

## Design trade-offs

- **Two new activities vs. one**: split by concern (`for.in` evaluated once, `while` evaluated per
  iteration). Merging them would either evaluate `while` too eagerly (Q3 rejected that) or make
  `EvaluateForActivity` a stateful iteration engine, which would need to itself hold across replays
  — impossible for a stateless activity.
- **`for.do`'s body threads data forward vs. accumulates per-iteration outputs**: threading forward
  (Q4) is the sequential-loop reading; accumulation is `fork`/`map` semantics (2.4). Threading
  forward also matches how the spec's example uses `output.as` (accumulation is expressed *in the
  body's own output-shaping*, not by the runtime — the runtime only threads).
- **No iteration cap beyond `MAX_STEPS`'s per-scope guard**: accepted as adequate given `for.in`'s
  finiteness (Q5). Revisit only if a real, observed pathology emerges.
- **`DefinitionLookup` gets one new recursion branch, not a general walker** (Q8): mirrors slice
  2.1's incremental approach (`try.try`/`try.catch.do` added when `try` shipped, not a general
  walker); 2.4 will add `fork.branches` (or whatever `fork` calls it) with the same one-branch
  discipline.
- **New capability spec vs. extending an existing one**: `workflow-error-handling` doesn't fit
  (`for` isn't an error concern); `workflow-data-flow` doesn't fit (data flow is per-task, applied
  around `for` by the existing wrap, not by `for` itself). `for` is genuinely a new capability:
  bounded iteration over a collection. → new `workflow-iteration` capability.

## Open Questions

None blocking. Every "what does the SDK actually give me" question is answered by the `javap`
output above; every "does this reuse existing machinery" question is answered by the codebase
findings.
