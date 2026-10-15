# Brainstorm: Phase 3i, .NET `UseResolver`

Raw capture. The team's handoff for this change locked scope, behavior and acceptance up front
(`dws-flow` only, no other sub-phase), so no interactive Q&A round was held. The decision chain below
was resolved from the handoff plus read-only recon of ADR 0010 (Decisions 2 and 3), the roadmap rows
3i / "ADR 0010 integration", `dws-flow` (3a `TaskTimeout`, `SequencerRunner`, `Program`,
`SingleNodeDefinitionLoader`, `ReplayTests`) and v1 (`WorkflowSupport.namedTimeout`,
`CatchPolicy.resolvePolicy`, `RaiseErrorActivity.resolveError`). Nothing was implemented.

## Handoff (verbatim goal)
A named `use` entry (`errors`, `retries`, `timeouts`) must resolve inside `dws-flow`. Today a task
`timeout: fastCall` fails as "not a valid ISO-8601 duration". After this change the Flow loads the
entries its node references at boot and resolves "inline value, else look up by name" in one place.
First use: a named task `timeout`.

## Background
- ADR 0010 Decision 2: store keys are `<kind>.<name>`, values are JSON, the store is immutable per
  version. Decision 3: hosts load only the names they reference at boot into an immutable map; a
  missing entry fails the boot; workflow code reads only the map (replay-safe).
- `TaskTimeout.Parse` (3a) already parses an ISO-8601 string, a duration object, and an `after`
  wrapper; `Format` renders `PT24H`, never `P1D`. A v1 `use.timeouts` entry is `{"after": {...}}`, so
  an entry fed to `Parse` needs no new parsing.
- v1 resolves names against the whole document (`definition().getUse()`); a v2 node only has its own
  node definition, hence the store.
- The Java `UseResolver` (roadmap 2c) does not exist yet. No `UseResolver` exists anywhere in the repo.
- `SequencerRunner` calls `TaskTimeout.Parse(body["timeout"])` inside workflow code; `ReplayTests`
  scans `dws-flow/src/*.cs` (except `Program.cs`) for non-deterministic APIs including `Environment.`.

## Decisions
- Q1 Where does the inline-or-name rule live? One class, `UseResolver.Resolve(kind, reference)`. No
  call site tests "is it a string" itself.
- Q2 How is a string told apart as name vs inline? In OWS a string `timeout` is a reference, but 3a
  accepts a bare ISO-8601 string as inline and the handoff requires inline to behave exactly as in 3a.
  Decision: for `timeouts` only, a string that starts with an ISO-8601 duration designator
  (`^[+-]?P[0-9T]`) is inline; every other string is a name. Objects are always inline. For `errors`
  and `retries` a string is always a name.
- Q3 Which references does the node collect? Task bodies of this node only (no recursion; nested
  scopes are their own nodes): `timeout`, `raise.error`, `catch.retry`. Node-level `retry`/`timeout`
  (3c, 3j) are not in `SingleNodeDefinition` yet and are out of scope; the collector is one table so
  they add a row.
- Q4 Store seam. `IUseStore.Get(storeName, keys)` returns the found key to JSON-text pairs. A fake
  backs the tests; `DaprUseStore` is a thin adapter over `DaprClient.GetConfiguration`.
- Q5 Store name. Environment variable `DWS_FLOW_USE_STORE`, read in `Program.cs` (the replay scan
  forbids `Environment.` elsewhere). Required only when the node has references. Phase 4 must stamp it.
- Q6 Boot failure type. Reuse `DefinitionLoadException` (the package's one custom exception) and throw
  before `AddDaprWorkflow`, so the host never registers its workflow.
- Q7 Immutability. The resolver holds entry JSON as strings and parses per call, so no mutable
  `JsonNode` is shared across workflow replays.
- Q8 Failure wording (no Java resolver to copy): see design D6. Assumed, listed in the report.
- Q9 Sidecar readiness. Boot happens before the sidecar may be up; the adapter waits for it, and only
  when there are references.

## Rejected
- Reading the store lazily on first use (non-deterministic I/O in workflow code).
- Always treating a string as a name (breaks 3a's inline ISO string).
- A second duration parser for store entries (reuse `TaskTimeout`).
- Resolving at the controller / expanding names into the node definition (ADR 0004 and the roadmap
  require the node to carry the reference as written).
