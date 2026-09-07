# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this package.

Root-level cross-cutting rules (commits, contract-change etiquette, per-package gate commands) are in
the repo root [`CLAUDE.md`](../CLAUDE.md). This file is dws-flow-specific idioms only.

This is a small, early-phase package (6 source files) — a generic Dapr Workflow host for exactly one
immutable `kind: "flow"` single-node definition, loaded from `DWS_FLOW_DEFINITION_PATH`. Its workflow
run body is presently a documented no-op ("Phase 0" — see `FlowWorkflow.cs`); real task
sequencing/child dispatch lands in a later phase. Some patterns below are drawn from very few data
points and may need revisiting once that lands. No CI workflow exists for this package yet, unlike
every sibling component.

## Commands

```shell
cd dws-flow
dotnet build
dotnet test
dotnet test test/dws-flow.Tests.csproj --filter "FullyQualifiedName~RejectsMalformedJson"   # single test
```

No lint/format gate is configured — no `.editorconfig`, no analyzers referenced in either `.csproj`,
no `<TreatWarningsAsErrors>`. Don't assume one exists; if you add one, that's a project-wide decision
worth raising first, not something to bolt on silently in a feature change.

## Style guide

### Nullable reference types are on — use `IsNullOrWhiteSpace`/pattern matching/`??`, not `== null` or `!`

`<Nullable>enable</Nullable>` in both `.csproj` files. No `== null`/`!= null` comparisons and no
null-forgiving `!` operator exist anywhere in the package — nullability is expressed through the
type system and idiomatic null-safe operators instead:

```csharp
// FlowDefinitionHolder.cs:13 — turn null into a real exception via ??, not an if
public static SingleNodeDefinition Definition =>
    definition ?? throw new InvalidOperationException("definition not yet loaded");

// SingleNodeDefinitionLoader.cs:22 — IsNullOrWhiteSpace, not == null
if (string.IsNullOrWhiteSpace(definitionPath))
{
    throw new DefinitionLoadException("DWS_FLOW_DEFINITION_PATH is required");
}

// SingleNodeDefinitionLoader.cs:85,95,102 — pattern matching over a null check
if (node.TryGetValue<string>(out string? text) && !string.IsNullOrWhiteSpace(text))
{
    // use text
}

// SingleNodeDefinition.cs:13 — nullability on the type itself
public sealed record SingleNodeDefinition(string Scope, JsonArray Tasks, JsonObject Children, string? Catch);
```

### Loops: LINQ predicates for validation, plain `foreach` for iterate-and-check-each

```csharp
// SingleNodeDefinitionLoader.cs:47 — .Any() as a validation predicate
if (tasks.Any(task => task is not JsonObject taskObject || taskObject.Count == 0))
{
    throw new DefinitionLoadException("each task must be a non-empty JSON object");
}

// SingleNodeDefinitionLoader.cs:100 — plain foreach with tuple deconstruction, discard the key if unused
foreach ((string _, JsonNode? value) in children)
{
    // validate value
}
```

### DI: `Microsoft.Extensions.DependencyInjection` via minimal hosting, registration-by-instance where the value is already built

```csharp
// Program.cs:8-10
WebApplicationBuilder builder = WebApplication.CreateBuilder(args);
SingleNodeDefinition definition = SingleNodeDefinitionLoader.Load(definitionPath);
builder.Services.AddSingleton(definition);
builder.Services.AddDaprWorkflow(options => options.RegisterWorkflow<FlowWorkflow>(FlowWorkflow.Name));
```

`FlowWorkflow` itself is **not** constructor-injected — `Dapr.Workflow`'s `RegisterWorkflow<T>`
requires a parameterless-constructible type, so it reads its definition through the static
`FlowDefinitionHolder.Definition` instead. This is a workaround forced by the Dapr SDK, not a general
license to reach for static state — every other class in this package still takes its dependencies
normally. No interfaces/abstractions exist anywhere yet; everything is a concrete `sealed` class —
don't introduce an interface for a single implementation without a second one already in view.

### Model: one `sealed record`, partially typed — `Tasks`/`Children` stay raw `JsonArray`/`JsonObject`

```csharp
// SingleNodeDefinition.cs:6-13
public sealed record SingleNodeDefinition(string Scope, JsonArray Tasks, JsonObject Children, string? Catch);
```

There's no DTO-vs-domain split because there's exactly one data-carrier type. Don't add a second,
fully-typed model of the same shape "for cleanliness" — the raw `JsonArray`/`JsonObject` fields are
intentional given Phase 0 doesn't interpret task/child contents yet; revisit only when real
task-walking logic needs it.

### Exceptions: one custom type, catch the custom type first and rethrow bare, translate only specific known types

```csharp
// SingleNodeDefinitionLoader.cs:66-81
try
{
    return JsonNode.Parse(File.ReadAllText(definitionPath!)) as JsonObject
        ?? throw new DefinitionLoadException("single-node definition must be a JSON object");
}
catch (DefinitionLoadException)
{
    throw; // don't double-wrap
}
catch (Exception exception) when (exception is IOException or JsonException or ArgumentException)
{
    throw new DefinitionLoadException($"failed to load definition '{definitionPath}': {exception.Message}", exception);
}
```

No bare `catch (Exception)` anywhere — only a `when`-filtered catch listing the specific exception
types this operation can actually throw. Everything else (missing field, wrong shape) is a direct
`throw new DefinitionLoadException(...)` guard clause, no nested try/catch.

### Async: don't add `async`/await or a `CancellationToken` speculatively

`FlowWorkflow.RunAsync` overrides the Dapr SDK's abstract `Task<object?> RunAsync(...)` signature
but its Phase 0 body has no actual async work, so it returns `Task.FromResult<object?>(null)` rather
than using `async`/`await` for nothing. No `CancellationToken` is threaded anywhere in the package —
add one only when a method does something genuinely cancellable, not preemptively.

### Tests: xUnit `[Fact]` + FluentAssertions, present-tense verb test names, `IDisposable` for teardown, raw string literal fixtures

```csharp
// SingleNodeDefinitionLoaderTests.cs pattern
public class SingleNodeDefinitionLoaderTests : IDisposable
{
    private readonly string tempDir = Directory.CreateTempSubdirectory().FullName;

    [Fact]
    public void RejectsMalformedJson()
    {
        string path = Path.Combine(tempDir, "def.json");
        File.WriteAllText(path, """{ "kind": "flow", """);

        Action act = () => SingleNodeDefinitionLoader.Load(path);

        act.Should().Throw<DefinitionLoadException>().Which.Message.Should().Contain("kind");
    }

    public void Dispose() => Directory.Delete(tempDir, recursive: true);
}
```

Test names: `Rejects…`/`Accepts…`, present tense, no `Should_When`/underscore naming, no
Arrange/Act/Assert comments. No mocking library referenced — nothing here needs one, since the
loader only touches the real filesystem via a real temp directory; don't introduce Moq/NSubstitute
for this package without a dependency that actually needs faking.

### Doc comments: one `<summary>` line per public type, nothing more

```csharp
// FlowDefinitionHolder.cs:3
/// <summary>Makes the already validated definition available to Dapr's workflow instance.</summary>
public static class FlowDefinitionHolder { /* ... */ }
```

Every public type gets exactly one plain-sentence `<summary>`; individual members don't get
`<param>`/`<returns>` XML-doc blocks. Match this — don't add verbose per-member XML docs to a type
that only has a type-level summary today.
