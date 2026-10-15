## Context

`dws-flow` (.NET 10, Dapr.Workflow / Dapr.Client 1.18.10) loads one `SingleNodeDefinition` from
`DWS_FLOW_DEFINITION_PATH` in `Program.cs`, then registers workflow `Flow`. 3a's `TaskTimeout` parses
and formats timeouts; `SequencerRunner` calls `TaskTimeout.Parse(body["timeout"])` for each task.
ADR 0010 puts named `errors`/`retries`/`timeouts` entries in a per-version Dapr configuration store
(keys `<kind>.<name>`, JSON values, immutable) that hosts read once at boot. v1 resolves names from the
whole document (`namedTimeout`, `resolvePolicy`, `resolveError`); that is not available to a v2 node.
The Java `UseResolver` (roadmap 2c) does not exist yet, so shapes below are taken from ADR 0010 and v1.

## Goals / Non-Goals

**Goals:** one resolver with the inline-or-name rule; boot-time load of exactly the referenced names;
fail the boot on a missing or invalid entry; named task `timeout` end to end; replay safety; a store
seam testable with a fake.

**Non-Goals:** named `retry` / `raise.error` consumers (3c, 2c), workflow-level `timeout` (3j),
`$secrets`, emitting the store or its RBAC (Phase 4), live-sidecar tests (3h, Phase 4), any change
outside `dws-flow/`.

## Decisions

### D1: One resolver, one rule
- **Choice:** `UseResolver.Resolve(UseKind kind, JsonNode? reference)` returns the inline value as
  written, or the named entry parsed from its JSON text, or `null` for an absent reference.
  `UseResolver.Timeout(reference)` is `TaskTimeout.Parse(Resolve(Timeouts, reference))`. The rule is
  not repeated at any call site, the collector or the loader (both call `UseResolver.IsName`).
- **Alternatives:** per-kind resolver classes (rejected: the rule would live three times, which is
  what ADR 0010 removes).

### D2: Name versus inline
- **Choice:** objects are inline. A string is a name, except for `timeouts`, where a string starting
  with an ISO-8601 duration designator (`^[+-]?P[0-9T]`) is an inline literal, to keep 3a behavior.
- **Consequence:** a timeout name that itself starts with `P` followed by a digit or `T` (for example
  `P2`) is read as an inline duration and fails with 3a's invalid-duration wording. A name such as
  `Premium` is unaffected. The controller (Phase 4) should reject such names; flagged, not done here.
- **Alternatives:** always treat strings as names (breaks "inline behaves exactly as in 3a"); try ISO
  parse then fall back to a name (a typo like `PT5X` would then report a missing entry instead of the
  3a wording).

### D3: What the node references
- **Choice:** `UseReferences.Collect(definition)` walks this node's task bodies only and reads
  `timeout` (timeouts), `raise.error` (errors) and `catch.retry` (retries) through `UseResolver.IsName`.
  One table; 3c and 3j add rows for node-level fields once the loader carries them.
- **Assumption:** these field paths follow OWS and v1's typed model; no recursion because nested
  scopes are separate nodes with their own definitions (ADR 0004).

### D4: Boot-time load, immutable map
- **Choice:** `UseResolverLoader.Load(definition, storeName, store)`: collect references; if none,
  return `UseResolver.Empty` without touching the store or requiring a store name; otherwise check
  charsets, require a store name, call `store.Get(storeName, keys)` once with keys `<kind>.<name>`,
  validate each entry, and build the resolver. Entry JSON is kept as text and parsed per call, so no
  mutable node is shared.
- **Validation:** every entry must be a JSON object; a `timeouts` entry must also parse through
  `TaskTimeout.Parse`. `errors`/`retries` get the object check only because Flow has no consumer yet.
- **Replay safety:** the resolver is built in `Program.cs` before `AddDaprWorkflow` and read by
  workflow code through a static holder (the same Dapr SDK workaround as `FlowDefinitionHolder`). Its
  `Resolve` has no I/O, clock or randomness. `ReplayTests` gets a named-timeout case.

### D5: Store seam and configuration
- **Choice:** `IUseStore.Get(string storeName, IReadOnlyCollection<string> keys)` returns found
  key to JSON-text pairs (absent keys are omitted). `DaprUseStore` implements it over
  `DaprClient.GetConfiguration`, waits for the sidecar first, and creates its client lazily so a node
  with no references never opens a connection. The store name comes from `DWS_FLOW_USE_STORE`, read in
  `Program.cs` (`ReplayTests` forbids `Environment.` in other source files). It is required only when
  the node has references.
- **Contract note:** Phase 4 must stamp `DWS_FLOW_USE_STORE=<definitionResource>-use` on every
  `dws-flow` Deployment. A mismatch fails the boot with the missing-store message, not silently.

### D6: Failure wording (assumed, no Java resolver to copy)
All are `DefinitionLoadException`, thrown before the workflow is registered:
- missing: `use.<kind> '<name>' is not defined in configuration store '<store>'`
- bad charset: `use.<kind> name '<name>' must match [-._a-zA-Z0-9]+`
- unparseable: `use.<kind> '<name>' is not a valid entry: <reason>`
- no store configured: `DWS_FLOW_USE_STORE is required because this node references use.<kind> '<name>'`
Kind is the plural key prefix (`errors`, `retries`, `timeouts`). Align with 2c's wording when it lands.
A runtime `Resolve` of a name that was not loaded (collector gap) throws
`config failure: use.<kind> '<name>' was not loaded at boot`.

### D7: Sequencer integration
- **Choice:** `SequencerRunner.Run` gains an optional `UseResolver? resolver = null` (defaults to
  `Empty`), and replaces `TaskTimeout.Parse(body["timeout"])` with `resolver.Timeout(body["timeout"])`.
  Existing tests and callers keep compiling. The timed-out wording stays
  `task '<name>' timed out after <TaskTimeout.Format>`.

## Risks / Trade-offs

- [Dapr configuration API is alpha in the .NET SDK and the exact `GetConfiguration` shape is
  unverified in this repo] -> confined to `DaprUseStore`; the plan verifies the signature against
  Dapr.Client 1.18.10 before coding; live coverage is 3h / Phase 4.
- [Sidecar not ready at boot] -> adapter waits with a bounded timeout, then fails the boot.
- [Store omits absent keys vs returns empty values] -> treat both as missing.
- [Name starting with `P<digit>`/`PT` is read as inline] -> documented (D2), controller rejection is a
  Phase 4 follow-up.
- [Shapes assumed ahead of 2c] -> listed in the final report; entry shapes follow ADR 0010's examples.

## Migration Plan

N/A: `dws-flow` is not deployed yet. A node with no references is unchanged. Rollback is reverting the
change.

## Open Questions

- Exact failure wording once the Java resolver (2c) is written.
- Whether Phase 4 stamps `DWS_FLOW_USE_STORE` or another name (D5).
- Where node-level `retry` / workflow `timeout` references live in the single-node definition (3c, 3j).
