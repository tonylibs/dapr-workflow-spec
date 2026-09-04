# Brainstorm — Validation preview (dws-console Phase 2)

Raw capture. Starting point was the design already decided in
`docs/roadmaps/dws-console-submission.md` §6 (two-layer validation, 2026-09-03),
plus its three explicitly unresolved questions. This session's work was resolving
those three empirically against the repo — and one of them came back with an
answer that contradicts the roadmap's stated plan.

## Background (confirmed by reading the code, not assumed)

- `dws-controller`'s `POST /workflows?dryRun=true` compiles and returns a
  `DeploymentPlan` without applying (`WorkflowResource.deploy`, lines 42-45).
  `DeploymentPlan` is a record: `workflow`, `versionId`, `version`,
  `definitionResource`, `specText`, `steps[]` (`{name, kind, image, env}`),
  `bindings[]` (`{task, direction: EMIT|LISTEN, topic}`), `orchestrator`
  (`{name, image, appId, appPort, replicas, env}`), `oauthEndpoints[]`,
  `bindingComponents[]`.
- Errors from a rejected compile are a flat `List<String>` via
  `CompilationException` → 400 `{message, errors[]}`. No path, no line, no column.
- `dws-admin` already relays the write path verbatim: `ControllerRelayController`
  (`POST /workflows`) → `ControllerRelayService.relayDeploy` → local Dapr sidecar →
  controller. It already forwards `?dryRun=true` (the service takes a `dryRun`
  boolean and appends the query param), so **the dry-run path needs no new relay
  work at all** — only a console caller.
- `dws-console`'s `submitDefinition` (`src/lib/admin-client.ts`) hardcodes
  `dryRun=false`. The editor route is `src/routes/workflows/new.tsx`; it holds
  `definition`/`format` in `useState` and renders outcomes through `Banner`.
- `dws-admin` has no YAML parser, no JSON Schema validator, and no ajv today.

## Q1 — Does the controller's model match schema 1.0.3? **No. Resolved: it does not.**

The roadmap flagged this as "unconfirmed, nothing pins a DSL version." It is
pinned, just indirectly: `dws-controller` does not hand-write its DSL model at
all. It parses with `io.serverlessworkflow.api.WorkflowReader` from
`serverlessworkflow-api`, pinned in `pom.xml` as
`<serverlessworkflow.version>7.26.0.Final</serverlessworkflow.version>`. Every
type in `WorkflowCompiler`'s import block (`Document`, `Workflow`, `CallHTTP`,
`RunShell`, …) is generated from that SDK's own schema.

That SDK ships the schema it was generated from, inside the jar:
`serverlessworkflow-types-7.26.0.Final.jar!/schema/workflow.yaml` — 1,828 lines,
`$id: https://serverlessworkflow.io/schemas/1.0.1/workflow.yaml`.

So the real comparison is **1.0.1 (what the controller actually parses) vs 1.0.3
(what §6 proposed vendoring)**. Both files were fetched and diffed: 185 added /
20 removed lines. The differences are not cosmetic; several would produce exactly
the false positives §6 warned about.

| Difference | Direction | Consequence if we validate with 1.0.3 |
|---|---|---|
| `run.shell.arguments` and `run.script.arguments`: **object** (1.0.1) → **array of strings** (1.0.3) | false positive | **Rejects DWS's own valid definitions.** `dws-controller/src/test/resources/fixtures/run-shell.yaml` uses `arguments: {region: eu, env: prod}`; `WorkflowCompiler` reads it as `Map<String,Object>` via `getArguments().getAdditionalProperties()`, and dws-run renders `--key value` pairs from that map's document order. 1.0.3 says that must be a list. |
| `emit.event.with` required: `[source, type]` → `[type]` | false negative | admin accepts a definition missing `source` that the controller's model still requires. |
| inline `oauth2` data gains `required: [authority, grant]` | false positive | admin rejects OAuth shapes the controller accepts. |
| `for.in`: `string` → `oneOf[string, array]` | false negative | admin accepts inline arrays the 1.0.1 model cannot bind. |
| `uriTemplate` pattern loosened (scheme required → relative allowed) | false negative | admin accepts relative URIs the controller's stricter pattern rejects. |
| `call: mcp` added; `catch.then` added; `container.stdin`/`arguments`/`pullPolicy` added | false negative | admin passes constructs the controller has no model for. |

Verdict: adopting upstream 1.0.3 would have shipped a validator that disagrees
with the compiler in both directions on day one — including rejecting a
definition shape the repo's own fixtures use.

## Q3 — Vendoring approach. The Q1 finding reframes it.

§6 offered two options (build-time fetch of upstream + snapshot, vs. manual copy)
and both share the same defect: they treat the *spec repo* as the source of truth
when the thing layer 2 actually enforces is the *SDK's* schema. A third option
exists and dominates both:

**Vendor the schema out of the SDK jar the controller already depends on.**
Same artifact, same version, by construction. A version bump becomes a single
coordinated edit (`pom.xml`'s `serverlessworkflow.version` plus a re-run of the
vendor script), and drift becomes impossible to introduce silently rather than
merely visible in review.

Mechanics decided: a small Node script downloads
`serverlessworkflow-types-<version>.jar` from Maven Central, extracts
`schema/workflow.yaml`, and writes a checked-in snapshot converted to JSON (so the
runtime needs no YAML parse of the schema itself, and the diff of a bump is
reviewable). The SDK version is read from `dws-controller/pom.xml` rather than
duplicated. A unit test asserts the snapshot's recorded SDK version still matches
the pom, so a controller-side SDK bump that forgets the admin side fails
`pnpm test` instead of silently drifting.

Rejected: extracting at build time on every `pnpm build` (needs network in CI and
couples the Node build to Maven Central availability); committing a hand-copied
file (no mechanical link back to the pom at all).

## Q2 — Task-name uniqueness. Confirmed still needed, with a caveat worth recording.

Correct as stated: JSON Schema cannot express "unique across the flat `do` list
including nested `try`/`catch`/`for`/`fork` bodies," so ajv alone will not catch
it and a custom walk is needed. Caveat the roadmap did not note: the controller
*already* enforces this (`WorkflowCompiler.duplicateTaskNames` /
`collectTaskNames`), so this is not a hole in coverage — it is layer 1 reporting
it earlier and with a JSON pointer instead of a bare sentence. Worth doing (it is
the one case where the fast local layer would otherwise pass something the slow
remote layer rejects), but it should be framed as parity, not as a missing check.

## Layer boundary — reaffirmed, with one clarification

Non-goal stands: no port of `semanticErrors()` into dws-admin. But `semanticErrors()`
is not purely deployability. It mixes both layers:

- `Missing 'document'` / `document.name` / `document.version`, `do` empty → the
  1.0.1 schema already covers these (`required: [document, do]`, and `taskList`'s
  own constraints). ajv reports them with a pointer; no port needed. Overlap here
  is fine and expected — both layers reporting the same defect is not a conflict.
- DNS-1123 secret naming, duplicate secret declarations, `run: container`
  rejection, script language, image resolution → deployability, stays put.

So §6's split table holds; the only correction is that "duplicate task name"
belongs to *both* layers, not just to a custom check in admin.

## Console flow

Sequential, not parallel: spec-check first (fast, local to admin, no controller
hop), and only on pass call `dryRun=true`. Rationale: if the document is not valid
DSL at all, the controller's flat error list is strictly worse feedback than ajv's
pointer, so there is no value in showing both — and a definition that fails layer 1
will fail layer 2 anyway. Render one of three outcomes: spec errors (path +
message), deployability errors (flat strings, as today), or the plan.

`Preview` is added alongside `Submit definition`, not replacing it. Preview never
mutates the cluster; both actions read the same buffer.

## Deliberately out of scope

- No change to `dws-controller`. Not one line.
- No inline CodeMirror diagnostics or gutter markers from ajv pointers — the
  pointer is a JSON path, and mapping it back to a YAML source line is a separate
  problem (the console has no YAML CST today). Phase 2 renders the path as text.
- No re-implementation of `WorkflowCompiler` deployability logic in dws-admin.
