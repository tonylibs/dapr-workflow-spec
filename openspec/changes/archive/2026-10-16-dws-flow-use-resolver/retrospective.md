# Retrospective: dws-flow-use-resolver (Phase 3i, .NET UseResolver)

> Written: 2026-10-16 (after verify passed with warnings)
> Commit range: `5839cb4..557528c` (plus verify/retro/archive commits)
> Branch: `claude/eager-albattani-5c23cj` (PR tonylibs/dapr-workflow-spec#160), no separate worktree

## 0. Evidence

- **Tasks done**: 7/7 (5 plan tasks)
- **Subagent dispatches**: 5 implementers (dotnet-developer), 5 task reviews, 1 Task 3 fix round + re-review, 1 final whole-branch review, 1 final fix wave + re-review
- **Diff size (whole branch incl. OpenSpec artifacts)**: 24 files, +1819 / -14
- **New external dependencies**: none (Dapr.Client 1.18.10 was already referenced)
- **Test coverage signal**: 45 → 79 xunit tests, build 0 warnings
- **OpenSpec validate**: this change valid; 3 unrelated pre-existing failures
- **CI**: `build & test`, `build & push image` green

```
99a2eb8 feat(flow): resolve use entries inline or by name in one place
c743451 feat(flow): collect the use names a node references
5111b0a feat(flow): load referenced use entries at boot and fail on a bad one
1646c7e fix(flow): reject newline-suffixed use names and overflowing timeout entries
93650b9 feat(flow): resolve a named task timeout through the use resolver
f83b28f feat(flow): load use entries from the Dapr configuration store at boot
c5a481b docs(flow): document the use resolver, DWS_FLOW_USE_STORE and boot failures
0c4be4b fix(flow): reject duplicate-key use entries at boot
557528c docs(flow): correct use resolver boot-failure and inline-rule docs
```

## 1. Wins

- [evidence: plan + task reviews] A plan with complete code let three of five tasks run on the cheapest model; every task review came back with no Critical finding.
- [evidence: Task 3 review, `1646c7e`] The task reviewer caught two spec gaps in the plan's own code (`$` accepts a trailing newline; `OverflowException` escaped validation) by probing .NET behavior, not by reading the plan.
- [evidence: final review, `0c4be4b`] The final review found the lazy-materialization duplicate-key gap with a scratch program and a false invariant in `dws-flow/CLAUDE.md` that this change had introduced.
- [evidence: Task 5 report] The Dapr configuration API matched the plan's sketch exactly; no adapter rework.

## 2. Misses

- 🟡 [evidence: Task 3 review] The plan's charset regex and catch list were wrong for .NET; the plan was written from intent, not from probing `Regex`/`TimeSpan` edge cases.
- 🟡 [evidence: Task 5 docs, final review] The docs step asserted "every boot failure is a DefinitionLoadException" without checking the adapter's failure modes.
- 🟢 [evidence: Task 2 hand-back] One implementer refused to write its report file because of its own system rules; the controller wrote it from the hand-back.
- 🟢 [evidence: sandbox] No .NET SDK in the container and `builds.dotnet.microsoft.com` blocked by the egress proxy; installed `dotnet-sdk-10.0` from Ubuntu noble instead.

## 3. Follow-ups (not in this change)

- `TaskTimeout` parses `{}` and mistyped keys (`{"after":{"second":10}}`) as zero for inline and named timeouts alike; tighten both together.
- `SingleNodeDefinitionLoader` should reject multi-key task objects (today `UseReferences`/`SequencerRunner` `.Single()` throws a raw exception), and its nodeId regex uses `$` (accepts a trailing newline).
- `SequencerRunner` resolves a task's timeout after starting the child call; resolve first.
- Nested duplicate keys inside `errors`/`retries` entries still pass boot; 3c/2c validators must walk or re-parse their entries.
- Phase 4 must stamp `DWS_FLOW_USE_STORE=<definitionResource>-use` on every `dws-flow` Deployment and reject timeout names starting `P<digit>`/`PT`.
- Align failure wording and entry shapes with the Java resolver (2c) when it lands.
