# Brainstorm: V2StructuralCompiler classification pass

Date: 2026-09-07
Path classification: **architectural** (new graph-shaped compiler output, a new cross-runtime wire
contract, and a JSON-schema delta that other runtimes consume).

## Background

Phase 1's first half landed as `phase-1-compiler-strategy-split` (archived 2026-09-06): the
`WorkflowCompiler` interface, `V1OrchestratorCompiler` (verbatim rename, zero behavior change),
an empty `V2StructuralCompiler` stub, the sealed `CompiledNode`/`FlowNode`/`StepNode` model, and
`CompilerProducer` reading a v1/v2 flag off the `dws-controller-config` Dapr Configuration store
with `v1` as the default.

This change is Phase 1's second half: make `V2StructuralCompiler` actually compile something. Up
to now it returns a `DeploymentPlan` whose every field is empty. The inputs that constrain it:

- **ADR 0001** — one Dapr app per graph node; dots sanitize to dashes; the `-fn` naming-collision
  rule for `call`/`run`.
- **ADR 0002** — the `CompiledNode` Composite, `key()` derived from `nodeId()`'s last dotted
  segment, and the compatibility contract (v2 populates `flowStepGraph`, never `steps`/`orchestrator`).
- **ADR 0003** — fork is its own `FlowNode`, `forkMode: all|any`, empty `tasks`, children = branches.
- **`openspec/schemas/single-node-definition.schema.json`** — the per-node wire format each
  compiled node's `specText` must satisfy.
- **`docs/roadmaps/workflow-runtime-architecture.md`** — the worked examples the golden tests
  assert against, and the acceptance criteria that pin the derived-identifier formats.

## Decision chain

### Q1 — How is `nodeId` derived for a *named* nested scope?

Acceptance criterion #2 pins only the scopes with no DSL name of their own: `<workflow>.main`,
`<try-task>.catch`, `<fork-task>.branch.<branch-root-task>`. It says nothing about a `for` or
`try` task that already carries a name.

Options weighed:

1. **Flat DSL name** — a named scope keeps its own task name (`fulfillOrder`, `reserveItems`);
   dotted derived ids appear only where the DSL supplies no name.
2. **Parent-qualified path** — every node carries its full path from main
   (`order-fulfillment.main.fulfillOrder.reserveItems`).
3. **Flat, qualify only on collision** — hybrid.

**Decision: flat DSL name.** It reads acceptance criterion #2 literally and matches the labels in
the worked examples' own mermaid diagrams. Option 2 breaches DNS-1123's 63-character app-ID cap on
any realistic nesting depth and contradicts the criterion's examples. Option 3 makes a node's
app ID depend on the rest of the document — renaming an unrelated task elsewhere silently
re-addresses a node — and it deletes the explicit duplicate-rejection behavior the requirement
asks for. Under option 1, a collision can only come from a genuinely duplicated DSL name, which is
exactly what the rejection rule should catch.

Resulting ids for the nested `try`/`for`/`catch` example:

```
order-fulfillment.main   -> order-fulfillment-main
fulfillOrder    (try)    -> fulfill-order
reserveItems    (for)    -> reserve-items
reserveItem     (step)   -> reserve-item
fulfillOrder.catch       -> fulfill-order-catch
markOrderFailed (step)   -> mark-order-failed
```

### Q2 — A fork branch whose root task is itself structural: one node or two?

In the `notify-order` example, branch `notifyRecipients` *is* a `for` task. The branch is a scope,
and the `for` is a scope — do they collapse into one node?

**Decision: two nodes**, matching the example's own mermaid (`BranchNotify -> For`). A branch stays
a scope holding a task list regardless of what that list contains, so no caller has to ask what a
branch is made of. Collapsing would merge `forkBranch`'s and `for`'s schema fields into one node
whose shape depends on its contents — a special case in exactly the place ADR 0003 was written to
remove one. Cost is +1 pod per structural-rooted branch, on top of the pod growth ADR 0001 and
ADR 0003 already flagged for Phase 4's capacity check.

The inner scope needs an id, and the branch id has already consumed the `for`'s DSL name. Rule:
a structural task at a branch root appends its kind rather than repeating the consumed name.

```
notifyChannels                              (fork,       forkMode: all)
  notifyChannels.branch.notifyRecipients    (forkBranch)
    notifyChannels.branch.notifyRecipients.for  (for)
      sendEmail                             (step)
  notifyChannels.branch.writeAudit          (forkBranch)
    writeAudit                              (step)
```

### Q3 — How far does the schema/doc delta go?

ADR 0003 recorded its schema impact as "documented, not yet implemented": the flow branch's
`scope` enum is still `main|for|try|catch|forkBranch` with no `forkMode`, so a fork node's
`specText` cannot validate at all today. Meanwhile the roadmap doc's classification table still
says fork is an "Inline parallel region — do not render a separate `fork` flow node", and its
acceptance criteria #6 and #7 still say no standalone fork invocation is created. That doc is the
source for the golden fixtures, so leaving it contradicting ADR 0003 means writing fixtures
against text that says the opposite.

**Decision: schema + full doc rewrite.** Add `fork` to the scope enum and `forkMode: all|any`
required exactly when `scope: fork`; rewrite the classification table row, acceptance criteria #6
and #7, and redraw the fork example's mermaid so the fork renders as its own node with the
branches beneath it.

### Q4 — Five worked examples or six?

The task description says five; `docs/roadmaps/workflow-runtime-architecture.md` carries six
(nested try/for/catch, parallel fork, state and decision, timing and event, external call and run,
raise and recovery).

**Decision: all six.** The sixth is the only example with a `catch` carrying an `errors.with`
filter and the only one with a `raise` task, so dropping it is a real coverage loss rather than
trimming a duplicate.

### Q5 — Does v2 populate `DeploymentPlan`'s top-level identity fields?

The stub leaves `workflow`, `versionId`, `version`, `definitionResource`, and `specText` as empty
strings.

**Decision: populate them**, computed exactly as v1 does (`Names.kebab(document.name)`,
`SpecDigest.versionId`, `WorkflowCompiler.version`, `Names.definitionResource`). The compatibility
contract in the `workflow-compiler-strategy` spec names only `steps` and `orchestrator` as the
fields v2 leaves empty — identity is shared between the strategies, not v1 legacy. Every node's
envelope needs `workflow` and `version` anyway, and Phase 4 synthesis will need them at the plan
level.

### Q6 — What shape is a node's `definitionResource`?

**Decision: `dws-def-<workflow>-<versionId>-<appId>`**, via a new
`Names.nodeDefinitionResource(w, versionId, appId)` beside the existing helper. Unique per node per
version, stays inside the 253-character ConfigMap name cap even at depth. Rejected: `<appId>-<versionId>`
(two workflows can own the same task name, so keys collide inside one namespace) and a single
shared whole-definition key (contradicts ADR 0002's "this node's *own* ConfigMap key" and defeats
per-node pinned definitions).

## Design trade-offs

**Four collaborators, not one class.** `SpecParser` (shared parse, extracted verbatim from
`V1OrchestratorCompiler`, which then delegates), `NodeClassifier` (the recursive descent and the
classification table), `NodeNaming` (id derivation, sanitization, `-fn`, collision rejection),
`SingleNodeDefinition` (the `specText` renderer). The alternative — one recursive method inside
`V2StructuralCompiler` — merges four concerns into a class that only golden tests can exercise. A
visitor with pluggable per-kind emitters was rejected as indirection Phase 1 does not need.

**Extracting `SpecParser` touches v1.** It is a behavior-preserving move of two private methods
with v1 delegating to them, and `WorkflowCompilerTest` is the guard. The alternative,
duplicating format detection into v2, drifts the two parsers apart the first time either changes.

**Schema validation in tests.** Validating generated `specText` against
`single-node-definition.schema.json` — rather than only matching a hand-written fixture — needs a
JSON Schema validator. Checked during planning: `com.networknt:json-schema-validator:2.0.0` already
resolves on `dws-controller`'s compile classpath, so this costs no new dependency. Byte-exact
fixture equality alone would pass happily against a fixture that is itself invalid.

**`single-node-definition-contract` is still owned by the unarchived phase-0 change.** So the
schema delta lands here as ADDED requirements in this change's own delta spec rather than editing
phase-0's in-flight artifacts.

## Out of scope

Confirmed unchanged by this change: `dws-step`/`dws-flow` runtime execution (Phases 2 and 3),
`StackSynthesizer` deploy synthesis (Phase 4), fork's `allOf`/`anyOf` execution semantics and
`anyOf` losing-branch cancellation (Phase 3 — this pass classifies fork, it does not run it), and
the `CompilerProducer` default, which stays v1.
