# PR Review: #72 — Scaffolds Dapr Workflow Runtime v2 components

**Reviewed**: 2026-08-31
**Author**: DuongVu98
**Branch**: feat/interpreter-v2 → main
**Decision**: REQUEST CHANGES (one HIGH, non-blocking for a phase-0 no-op merge; safe to fix now or as immediate follow-up)

## Summary

Clean phase-0 scaffold. `dws-flow` (.NET) and `dws-step` (Java/Spring) both load and validate
a pinned single-node definition, fail fast on invalid input, and register a no-op
workflow/activity. Loader validation and error-path tests are solid on both sides. The bulk of
the diff (33k of ~33k additions) is duplicated `.agents/skills` → `.claude/skills` /
`.codex/agents` → `.github/agents` scaffolding content, not reviewed line-by-line as it's
non-executable documentation mirrored across harnesses.

## Findings

### CRITICAL
None.

### HIGH

**`dws-step/.../SingleNodeDefinition.java` — `taskKind()` assumes the task-type key is always the first field.**

```java
public String taskKind() {
  return task.fieldNames().next();
}
```

This works today only because every example task object (`{"call":"http","with":{...}}`) happens
to have its type key first. Nothing enforces that. Per `CLAUDE.md`'s documented
content-addressed versioning, workflow definitions are canonicalized by **key-sorting** before
storage — a `switch` task shaped like `{"switch": ..., "cases": [...]}` would sort as
`cases, switch`, so `taskKind()` would silently return `"cases"` instead of `"switch"` once that
canonicalized shape reaches this loader. `SingleNodeDefinitionLoader.load()` already does the
right thing elsewhere (`task.has("call") || task.has("run")` — checks explicit known keys), so
`taskKind()` should do the same: check for known task-kind keys (`call`, `run`, `set`, `switch`,
`wait`, …) rather than taking the first field. Currently only feeds a log line (no-op activity),
so no runtime impact yet, but it's already asserted by a test (`acceptsCallStepOnlyWithFunctionAppId`)
and will silently misroute dispatch once execution logic lands in a later phase.

### MEDIUM

**Full duplication of skill/doc content between `.agents/skills/**` and `.claude/skills/**`.**
Verified several files are byte-identical (same git blob SHA, e.g.
`helm-chart-scaffolding/references/chart-structure.md`). This mirrors the existing repo
convention (agents already tripled across `.codex/agents` / `.github/agents` / `.claude/agents`),
so it's consistent with prior decisions rather than a new pattern — but each is a full
independent copy of large doc trees (thousands of lines) that must be hand-kept in sync going
forward. Worth a follow-up: generate/symlink from one source of truth instead of copy-pasting.

### LOW

- `dws-step/pom.xml` adds `lombok` and `streamex` dependencies that are unused by any file in
  this PR. Harmless now, but dead until phase-1 actually uses them — fine to defer adding them
  until needed (YAGNI).
- `dws-flow.Tests.csproj` pins `FluentAssertions 8.10.0`. FluentAssertions v8+ moved to a
  commercial license (Xceed) for companies above a revenue threshold — worth a conscious org
  decision, not a blocker for a personal/OSS repo.

## Validation Results

| Check | Result |
|---|---|
| Type check | Skipped — no local toolchain run (.NET/Java build not executed in this review) |
| Lint | Skipped |
| Tests | Skipped (not executed locally; read in full instead) |
| Build | Skipped |

Not run locally per this review's scope (read-only GitHub review of a 187-file scaffold PR).
Recommend CI results on the PR be the gating signal for build/test — both `dws-flow` and
`dws-step` have their own path-filtered CI workflows per `CLAUDE.md`.

## Files Reviewed

Read in full (actual source/config, not doc scaffolding):
- `dws-flow/src/*.cs`, `dws-flow/Program.cs`, `dws-flow/Dockerfile`, `dws-flow/*.csproj`, `dws-flow/test/*.cs`
- `dws-step/src/main/java/io/dws/step/**/*.java`, `dws-step/Dockerfile`, `dws-step/pom.xml`, `dws-step/src/test/**/*.java`
- `openspec/schemas/single-node-definition.schema.json` and examples
- Spot-checked `.claude/skills/**` vs `.agents/skills/**` for byte-identical duplication

Not reviewed line-by-line (non-executable, mirrored doc/agent scaffolding — ~33k of the ~33k additions):
- `.agents/skills/**`, `.claude/skills/**`, `.claude/agents/**`, `.codex/agents/**`, `.github/agents/**`
