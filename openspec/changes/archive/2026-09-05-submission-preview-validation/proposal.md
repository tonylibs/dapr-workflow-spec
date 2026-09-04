## Why

Today an operator only learns a definition is wrong by applying it: `dws-console`'s
editor posts `dryRun=false` and renders whatever flat `errors[]` string list
`dws-controller` returns — no field paths, no preview of what would be deployed,
and no way to check a draft without touching the cluster. The controller's
`?dryRun=true` compile-only path already exists and `dws-admin` already relays it,
so the missing piece is entirely on the client side plus one cheap, fast
spec-conformance check that can answer "is this even valid DSL?" before a network
round-trip to the compiler. Adding both gives operators path-precise structural
errors and a "what will be deployed" view before they commit anything.

## What Changes

**Definition feedback in the console**
- From: one action (`Submit definition`) that applies to the cluster and reports a
  flat list of controller error strings.
- To: a second, non-mutating `Preview` action that runs spec conformance in
  `dws-admin` first, then `dryRun=true` against `dws-controller`, and renders
  either the resulting `DeploymentPlan` or the errors from whichever layer
  rejected it.
- Reason: operators need to see structural errors with field paths, and see the
  deployment shape, without applying.
- Impact: non-breaking; `Submit definition` is untouched.

**Spec conformance authority**
- From: `dws-controller` is the only validator; every error is discovered after a
  full compile and reported as an unlocated sentence.
- To: `dws-admin` additionally validates the parsed document against the OWS/
  Serverless Workflow DSL JSON Schema with ajv, reporting `instancePath` pointers.
  `dws-controller` remains the sole authority on deployability.
- Reason: JSON Schema already encodes document/task shape; ajv gives path
  precision for free.
- Impact: additive endpoint; no controller change.

**Schema provenance — corrected relative to the roadmap**
- From: roadmap §6 planned to vendor upstream
  `open-workflow-specification.org/schemas/1.0.3/workflow.yaml`.
- To: vendor `schema/workflow.yaml` out of `serverlessworkflow-types`, the same
  SDK jar (`7.26.0.Final`, schema `$id` version `1.0.1`) that `dws-controller`'s
  `pom.xml` pins and whose generated types `WorkflowCompiler` parses with.
- Reason: 1.0.1 and 1.0.3 differ materially. Most decisively, `run.shell`/
  `run.script` `arguments` is an **object** in 1.0.1 and an **array** in 1.0.3, so
  a 1.0.3 validator rejects `dws-controller`'s own passing fixture
  (`run-shell.yaml`) — a false positive against a definition that deploys today.
  See `brainstorm.md` for the full diff table.
- Impact: layer 1 agrees with layer 2 by construction rather than by assumption.

Also new: a Node vendor script that reads the SDK version from
`dws-controller/pom.xml`, downloads that jar, and writes the checked-in schema
snapshot; plus a test that fails when the pom moves and the snapshot does not.

## Capabilities

### New Capabilities
- `admin-definition-validation`: `dws-admin`'s spec-conformance validation of a raw
  DSL definition — YAML/JSON parse, ajv validation against the vendored DSL JSON
  Schema, cross-referential task-name uniqueness, and the vendored schema's
  provenance and drift guard.

### Modified Capabilities
- `console-definition-submission`: adds a non-mutating preview action to the
  definition editor, the two-layer validation order it runs, and the rendering of
  a returned `DeploymentPlan` and of path-carrying spec errors.

## Impact

- `dws-admin`: new validation module (`POST /definitions/validate`), new deps
  `ajv` + `ajv-formats` + `yaml`, vendored `schema/` snapshot, vendor script,
  drift test. No change to the existing relay or read API.
- `dws-console`: new client calls (spec check, `dryRun=true` preview), preview
  outcome types, editor route UI, plan rendering. `submitDefinition` unchanged.
- `dws-controller`: **no change**. Deployability stays where it is.
- Docs: `docs/roadmaps/dws-console-submission.md` Phase 2 row + §6, and the Notion
  mirror.
