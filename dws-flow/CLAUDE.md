# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this package.

Root-level cross-cutting rules (commits, contract-change etiquette, per-package gate commands) are in
the repo root [`CLAUDE.md`](../CLAUDE.md). This file is dws-flow-specific idioms only.

This is a small, early-phase package — a generic Dapr Workflow host for exactly one immutable
`kind: "flow"` single-node definition, loaded from `DWS_FLOW_DEFINITION_PATH`. A `main`/`do` node's
`FlowWorkflow.RunAsync` routes through `ScopeDispatch` to `SequencerRunner`, which runs the node's
tasks in source order, resolves each task's `then` directive, and dispatches each task to its Step
activity or Flow child workflow (`ChildClassifier`/`IChildCaller`/`InstanceIds`/`TaskTimeout`); a node
whose scope is `for`/`try-catch`/`fork` fails with a not-implemented configuration failure — those
controller scopes land in a later phase. Some patterns below are drawn from very few data points and
may need revisiting once that lands. CI is `.github/workflows/dws-flow.yml`: tests, then an image
build with a `/healthz` smoke test. The version lives in `version.txt` (release-please bumps it;
`dws-flow.csproj` reads it into `<Version>`), so don't hand-edit it.

## Commands

```shell
cd dws-flow
dotnet build
dotnet test test/dws-flow.Tests.csproj
dotnet test test/dws-flow.Tests.csproj --filter "FullyQualifiedName~RejectsMalformedJson"   # single test
```

Always name the test project. A bare `dotnet test` in `dws-flow/` resolves to `dws-flow.csproj` (the
only project file in that folder), which has no tests and compiles none of `test/**`, so it restores,
runs nothing and exits 0 — a silently vacuous gate.

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
// Program.cs:4-10
SingleNodeDefinition definition =
    new SingleNodeDefinitionLoader(Environment.GetEnvironmentVariable(SingleNodeDefinitionLoader.DefinitionPathEnvironmentVariable)).Load();
FlowDefinitionHolder.Initialize(definition);

WebApplicationBuilder builder = WebApplication.CreateBuilder(args);
builder.Services.AddSingleton(definition);
builder.Services.AddDaprWorkflow(options => options.RegisterWorkflow<FlowWorkflow>(FlowWorkflow.Name));
```

`FlowWorkflow` itself is **not** constructor-injected — `Dapr.Workflow`'s `RegisterWorkflow<T>`
requires a parameterless-constructible type, so it reads its definition through the static
`FlowDefinitionHolder.Definition` instead. This is a workaround forced by the Dapr SDK, not a general
license to reach for static state — every other class in this package still takes its dependencies
normally. `IChildCaller` is the package's one interface: `SequencerRunner`'s only seam onto Dapr's
`WorkflowContext`, so tests can substitute a recording stand-in instead of a real Dapr runtime. Don't
introduce a second interface for a type that already has exactly one implementation without a second
one already in view.

### Model: one `sealed record`, partially typed — `Tasks`/`Children` stay raw `JsonArray`/`JsonObject`

```csharp
// SingleNodeDefinition.cs:6-13
public sealed record SingleNodeDefinition(string Scope, JsonArray Tasks, JsonObject Children, string? Catch);
```

There's no DTO-vs-domain split because there's exactly one data-carrier type. Don't add a second,
fully-typed model of the same shape "for cleanliness" — `SequencerRunner`/`ChildClassifier`/`TaskTimeout`
walk `Tasks`' raw `JsonArray`/`JsonObject` entries directly (single-key `{taskName: taskBody}` objects)
rather than binding them to a typed task model; revisit only if a second consumer needs the same typed
shape.

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

### Async: `FlowWorkflow.RunAsync` chains, doesn't `await`; `SequencerRunner.Run` is genuinely `async`

```csharp
// FlowWorkflow.cs — no async keyword, just chains the one call that matters
public override Task<JsonNode?> RunAsync(WorkflowContext context, FlowInput? input)
{
    // ...
    return SequencerRunner.Run(resolvedInput, definition, new DaprChildCaller(context));
}
```

`FlowWorkflow.RunAsync` itself still has no work to `await` directly, so it returns the inner `Task`
rather than wrapping it in `async`/`await` for nothing. `SequencerRunner.Run`, by contrast, genuinely
awaits each dispatched task (`IChildCaller.CallStep`/`CallFlow`/`WithTimeout`), so it's a real `async`
method — don't read the outer "don't add async for nothing" convention as applying to the inner
sequencer loop too. No `CancellationToken` is threaded anywhere in the package — add one only when a
method does something genuinely cancellable, not preemptively.

### Sequencer: `main`/`do` tasks run in order, `then` picks the next task, children dispatch by shape

`SequencerRunner.Run` walks `SingleNodeDefinition.Tasks` (a `JsonArray` of single-key
`{taskName: taskBody}` objects) in source order, starting at program counter 0 and capped at 10,000
steps (mirrors v1's `InterpreterWorkflow`'s loop-guard wording verbatim: `"workflow exceeded 10000
steps; check for a definition loop"`). Each task body's static `then` directive (`ThenResolver.Next`)
picks the next program counter, ends the node normally, or exits — there is no dynamic `then`
selection from a task's own output (see "Known gap" below). `ChildClassifier.Classify` looks only at
which keys a task body has (`for`/`try`/`fork`/`wait`/`listen` route to the not-yet-implemented
controller scopes; everything else — `call`/`run`/`set`/`switch` — is a Step) to decide whether
`IChildCaller.CallStep` or `.CallFlow` dispatches it. Child instance IDs are
`InstanceIds.For(root, appId, iteration)` → `"<root>:<appId>:<iteration>"`, with `iteration` defaulting
to `"0"` when the node's own `FlowInput.IterationIndex` is absent (the Sequencer itself never
increments iteration — that's a `for`-scope concern for a later phase). A task's `timeout`
(`TaskTimeout.Parse`/`.Format`, backed by `System.Xml.XmlConvert`'s ISO-8601 duration support, chosen
because it's textually identical to v1's `java.time.Duration.toString()` for every case tested) races
the dispatched call via `IChildCaller.WithTimeout`; a timed-out task fails with v1's exact wording,
`"task '<name>' timed out after <duration>"`.

**Known gap (not yet implemented):** v1's `switch` task returns a `FlowOutcome{keyword, target}` from
its own evaluation, letting the task's *output* pick the next task dynamically in addition to any
static `then`. v2's Step activity contract (`dws-step`'s `StepActivity`) returns bare workflow data
with no directive channel, so this package only supports the static, pre-declared `then` read from the
task body — there is currently no mechanism for a `switch` task to choose its own `then` target at
runtime. Don't assume `switch` branching works end-to-end until that seam is designed.

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
