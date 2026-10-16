## 1. Resolver core (dws-flow)

- [x] 1.1 `UseResolver` and `UseKind`: the single inline-or-name rule, `Timeout` via `TaskTimeout`, `Empty`
- [x] 1.2 `UseReferences.Collect`: names a node references (`timeout`, `raise.error`, `catch.retry`)
- [x] 1.3 `IUseStore` and `UseResolverLoader.Load`: boot-time fetch, charset/missing/unparseable/no-store failures, no store call without references

## 2. Wiring (dws-flow)

- [x] 2.1 `SequencerRunner` resolves task `timeout` through the resolver; holder carries it to `FlowWorkflow`; replay test with a named timeout
- [x] 2.2 `DaprUseStore` adapter and `Program.cs` boot (store name from `DWS_FLOW_USE_STORE`, before workflow registration)

## 3. Docs and gate

- [x] 3.1 Update `dws-flow/README.md` and `dws-flow/CLAUDE.md` (resolver, `DWS_FLOW_USE_STORE`, boot failures)
- [x] 3.2 Run `cd dws-flow && dotnet test test/dws-flow.Tests.csproj`
