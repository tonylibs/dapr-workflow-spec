## Why

`dws-flow` cannot resolve a named `use` entry. A task `timeout: fastCall` fails at run time as "not a
valid ISO-8601 duration", because a v2 node only holds its own node definition and v1's
whole-document lookup does not exist there. ADR 0010 moves these names into a version-scoped
configuration store read once at boot. Phase 3c (named `retry`) and 3j (workflow `timeout`) both need
that resolver first, so 3i unblocks them.

## What Changes

**Named `use` entries in `dws-flow`**
- From: only a literal task `timeout` works; any name fails mid-run.
- To: at boot the Flow collects the `errors`/`retries`/`timeouts` names its node references, fetches
  exactly those from the version's configuration store, and keeps them in an immutable map. One
  `UseResolver` answers "inline value, else look up by name". A named task `timeout` resolves with the
  same parsing, ISO-8601 rendering and failure wording as an inline one.
- Reason: ADR 0010 Decisions 2 and 3; prerequisite for 3c and 3j.
- Impact: non-breaking. Inline values and nodes without references behave as today and touch no store.

**Boot failure**
- From: n/a.
- To: an unknown name, a name outside `[-._a-zA-Z0-9]`, an unparseable entry, or a missing store name
  fails startup before the workflow is registered, naming the kind and the name.

## Capabilities

### New Capabilities
- `dws-flow-use-resolver`: collecting referenced names, boot-time load from the configuration store,
  the single inline-or-name rule, boot failures, replay safety, the store seam.

### Modified Capabilities
- `dws-flow-sequencer`: the "Task timeout" requirement now also covers a named timeout.

## Impact

`dws-flow/` only (`src/`, `test/`, README/CLAUDE.md notes). No change to `dws-controller`, `dws-step`,
`dws-orchestrator` (v1), roadmaps, ADRs or Notion. Non-goals: using errors/retries in a controller
(3c), workflow-level `timeout` (3j), `$secrets`, emitting the store (Phase 4), a live-sidecar test (3h
and Phase 4), the Java resolver (2c).
