# Verification — submission-preview-validation

Verified 2026-09-05 against commit range `fd0eecf1..3aae1bd1` (8 implementation
commits on branch `submission-preview-validation`, branched from
`submission-validate-files`).

## 1. Structural validation

`openspec validate --all --json`: **45 valid, 2 invalid**. Both failures are
pre-existing and unrelated to this change:

| Item | Type | Issue |
|---|---|---|
| `helm-admin-gateway` | spec | `Spec must have at least one requirement` |
| `ows-phase3-errors-timeouts` | change | two ADDED requirements in `workflow-timeouts/spec.md` lack SHALL/MUST |

Neither touches this change's capabilities and neither was introduced here.
`openspec validate submission-preview-validation --strict` passes.
**Not blocking archive of this change**; worth fixing in their own changes.

## 2. Task completion

All **27** checkboxes in `tasks.md` are `- [x]`; **0** remain open. Nothing was
deferred, skipped, or reduced in scope.

## 3. Delta spec sync state

- `admin-definition-validation` — ✗ **Needs sync.** New capability;
  `openspec/specs/admin-definition-validation/` does not exist yet.
- `console-definition-submission` — ✗ **Needs sync.** The existing spec still
  carries only its four Phase 1 requirements; this change's five ADDED
  requirements are not merged in.

Both are the expected pre-archive state for a change whose deltas have not been
applied. `/opsx:sync` (or archive) is the next step.

## 4. Design / specs coherence

Spot-checked; no drift found.

| design.md decision | Backing requirement |
|---|---|
| D1 (vendor the SDK's schema, not the spec repo's) | *Definitions are validated against the schema the controller's parser was generated from* |
| D2 (vendor script, provenance, drift test) | *The vendored schema records its provenance and fails on drift* |
| D3 (`POST /definitions/validate`, 200 report, size cap) | *Admin exposes a non-mutating definition spec-validation endpoint* |
| D4 (sequential, spec layer first) | *Preview validates spec conformance before requesting a deployment plan* |
| D5 (uniqueness walk on top of ajv) | *Task names are checked for uniqueness across nested bodies* |
| D6/D7 (ajv config, error cap; parse errors as their own kind) | *Error list is capped*; *Unparseable definitions are reported with a source position* |
| D8 (parse the plan, don't cast it) | *Preview renders the deployment plan a valid definition would produce* |
| D9 (transport boundary; submit untouched) | *Preview renders deployability rejections distinctly from spec errors* |

Two design.md Open Questions are now closed by the implementation: preview
results auto-clear on buffer change (task 5.4), and the checked DSL version is
**not** surfaced in the console UI — still deferred, unchanged.

## 5. Implementation signal

Commit range `fd0eecf1..3aae1bd1`:

| SHA | Subject |
|---|---|
| `fd0eecf1` | docs(openspec): propose validation preview change |
| `6d02cb98` | chore: vendor the DSL JSON Schema from the SDK dws-controller pins |
| `22b9decd` | feat: add spec-validation and dry-run preview calls to the admin client |
| `6c5ec610` | feat: detect duplicate task names across nested definition bodies |
| `aa95e2d0` | feat: validate definitions against the vendored DSL schema |
| `2cc9f2df` | feat: preview a definition's deployment plan before submitting it |
| `566f3385` | feat: expose POST /definitions/validate on dws-admin |
| `7e79d200` | fix: lift the definition body cap to the documented 1 MiB |
| `3aae1bd1` | docs: record Phase 2 validation preview as shipped |

Working tree carries two uncommitted files, neither a code change:

- `openspec/changes/submission-preview-validation/tasks.md` — the checkbox
  updates recorded in §2, committed alongside this artifact.
- `dws-console/src/routeTree.gen.ts` — 9 added lines of TanStack router
  generator churn (a `declare module '@tanstack/react-start'` Register block)
  from the router-cli version `pnpm install` resolved. **Unrelated to this
  change**; deliberately left uncommitted rather than folded into a feature
  commit. Worth a separate commit or a lockfile decision, not part of this one.

## 6. Component gates

| Component | lint | test | build |
|---|---|---|---|
| `dws-admin` | clean | **17 suites / 108 tests passed** | `nest build` clean; `dist/definition-validation/schema/workflow-schema.json` emitted, so the runtime schema import resolves in the built image |
| `dws-console` | `Checked 45 files. No fixes applied.` | **8 files / 92 tests passed** (baseline was 7 / 79 — 13 added) | `✓ built` |

`dws-console` also runs `tsc --noEmit` as a separate script: clean.

`dws-controller` was not modified, so its gate was not re-run. Verified by
`git diff --stat fd0eecf1..HEAD -- dws-controller/` returning empty.

## 7. Front-door routing leak detector

`docs/superpowers/` does not exist. Brainstorm and plan output landed in this
change directory as the schema's redirection requires. ✓

## 8. Key correctness evidence

The one decision this change turns on is D1's schema provenance. Evidence it
holds, beyond the argument in `design.md`:

- `provenance.json` records `sdkVersion 7.26.0.Final`, `schemaId
  https://serverlessworkflow.io/schemas/1.0.1/workflow.yaml`, sha256
  `9790eb39…463b31` — the SDK jar, not the spec repo's 1.0.3.
- `run.shell.arguments` and `run.script.arguments` in the vendored schema are
  typed **`object`**, matching `WorkflowCompiler`'s `Map<String,Object>` read.
  This is the specific check that would have caught the 1.0.3 mistake.
- Fixture parity: all **11** `dws-controller` fixtures are asserted, none
  excluded. Ten are spec-valid, including `run-shell.yaml`.
  `run-container.yaml` and `run-script-bad-language.yaml` are asserted
  spec-valid **despite** the controller rejecting them — the layer boundary
  encoded as a test. `broken.yaml` is asserted rejected. A guard test asserts
  every name in those lists still exists on disk, so a fixture rename cannot
  quietly drop coverage.

## 9. Warnings carried forward (non-blocking)

1. **Two plan defects found during implementation, both corrected in the code
   and worth noting for future plans.** Task 1 Step 3's verification command used
   a wrong JSON path (`$defs.runTask.properties.run` is undefined; `run` lives
   under `$defs.runTask.allOf[1].properties.run`) — it would have printed
   `check-manually` and read as a failure. Task 4 Step 1's `VALID` fixture was
   itself invalid DSL (no `document.dsl`/`namespace`, non-semver `version`); it
   was replaced with a complete definition, assertion intact.
2. **Body-cap gap, found and fixed.** `main.ts` registered its parsers with no
   `limit`, so both definition paths were capped at the parsers' 100 kB default
   rather than the documented 1 MiB — the endpoint advertised a limit it could
   never apply. Fixed in `7e79d200` for both the YAML raw parser and Nest's json
   parser. This deliberately widens the `POST /workflows` relay too: preview and
   submit read the same buffer, so a definition that previews must be
   submittable.
3. **Router code-split warning.** Exporting `DefinitionEditor` from a route file
   (required so the route tests can import it) makes the TanStack router plugin
   warn that it will not be code-split. Build passes. Moving the component under
   `src/components/` would silence it.
4. **Console test-stack substitutions.** `@testing-library/user-event` and
   `@testing-library/jest-dom` are not in this repo and were not added; the route
   tests use `fireEvent.paste` with a fake `clipboardData` (verified with a probe
   to drive the real CodeMirror document and the real `onChange`) and plain
   assertions. jsdom also needs a `Range.prototype.getClientRects` stub or CM6's
   layout measurement throws inside `requestAnimationFrame`.
5. **No end-to-end run.** Every test mocks its boundary; nothing exercised
   console → dws-admin → dws-controller against a live cluster. The contract
   between layers is covered by unit tests on both sides and by fixture parity,
   but a first real preview against a running stack is still unproven.

## Verdict

**Ready to archive**, after syncing the two delta specs. No blocking issue. The
two `openspec validate --all` failures are pre-existing and belong to other
changes.
