## 1. dws-flow regression tests

- [x] 1.1 Extend `test/TaskTimeoutTests.cs` for empty direct and wrapped objects, a misspelled unit, an unknown property beside a valid unit, wrapper siblings, and null unit or wrapper values. Check the configuration error reason. Exercise the sequencer with a malformed inline timeout and verify zero child calls and zero timer calls.
- [x] 1.2 Add parser compatibility checks for direct and wrapped numeric zero and an ISO-8601 string under `after`. Extend `test/UseResolverLoaderTests.cs` to verify that empty and mistyped referenced entries fail with `DefinitionLoadException`, including the kind, name and parser reason. Verify a referenced explicit zero still loads and resolves to zero.

## 2. dws-flow parser and verification

- [x] 2.1 Implement the shared object validation in `src/TaskTimeout.cs` per the [design](design.md) and the [sequencer](specs/dws-flow-sequencer/spec.md) and [resolver](specs/dws-flow-use-resolver/spec.md) requirements. Reuse the existing startup error wrapping and boot-time resolver described in the [ADR review](adr.md).
- [x] 2.2 Run `dotnet test test/dws-flow.Tests.csproj` from `dws-flow/`. Run `openspec validate fix-flow-timeout-duration-validation --type change --strict` from the repository root before archive.
