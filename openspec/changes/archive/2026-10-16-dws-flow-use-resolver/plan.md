# Phase 3i .NET `UseResolver` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A named `use` entry (`errors`, `retries`, `timeouts`) resolves inside `dws-flow`, first for a named task `timeout`.

**Architecture:** A `UseResolver` holds an immutable map of the entries the node references, loaded once at boot through an `IUseStore` seam (fake in tests, a thin Dapr configuration adapter in production), and is the only place that decides "inline value, else look up by name". `SequencerRunner` asks it for task timeouts. Boot failures are `DefinitionLoadException`s thrown from `Program.cs` before the workflow is registered.

**Tech Stack:** .NET 10, Dapr.Workflow / Dapr.Client 1.18.10, xunit 2.9.3, FluentAssertions 8.10.0.

**Spec:** `specs/dws-flow-use-resolver/spec.md`, `specs/dws-flow-sequencer/spec.md`, `design.md`, `tasks.md` in this change directory.

## Global Constraints

- Edit only `dws-flow/` (and this change directory's `tasks.md` checkboxes). No change to `dws-controller`, `dws-step`, `dws-orchestrator`, roadmaps, ADRs or Notion.
- Store keys `<kind>.<name>` with kind in `errors|retries|timeouts`; names match `[-._a-zA-Z0-9]+`; entries are JSON objects.
- Store name comes from env var `DWS_FLOW_USE_STORE`, read only in `Program.cs` (`ReplayTests` forbids `Environment.` elsewhere in `src/`).
- Reuse `TaskTimeout.Parse`/`Format`; no second duration parser. Rendering stays `PT24H`, never `P1D`.
- Workflow code deterministic: `UseResolver` does no I/O, clock or randomness. The store is called only from `Program.cs` boot.
- Timed-out wording unchanged: `task '<name>' timed out after <ISO-8601>`.
- Style: `dws-flow/CLAUDE.md` (no `== null`/`!`, `DefinitionLoadException` only, one `<summary>` per public type, xunit `[Fact]` + FluentAssertions, present-tense test names, no mocking library, raw-string JSON fixtures).
- Gate: `cd dws-flow && dotnet test test/dws-flow.Tests.csproj` (always name the test project).
- Do not push or open a PR from the implementation session unless asked.

## Review Focus

- Timeout string `P2` / `PT5X` -> read as an inline duration and fail with 3a's invalid-duration wording, never a missing-entry message.
- A name that is both a valid ISO string shape and a valid key (`PT5S`) for kind `errors` -> looked up as a name.
- Two tasks naming the same entry -> the store is asked for the key once.
- Store returns an empty string or omits the key -> both are "not defined".
- Entry `{"after": "soon"}` or `[]` or `"PT5S"` -> boot fails naming kind and name.
- Node with only inline timeouts and no `DWS_FLOW_USE_STORE` -> starts, store never called.

---

### Task 1: `UseResolver` (tasks.md 1.1)

**Files:**
- Create: `dws-flow/src/UseResolver.cs`
- Test: `dws-flow/test/UseResolverTests.cs`

**Interfaces:**
- Produces: `enum UseKind { Errors, Retries, Timeouts }`; `UseResolver(IReadOnlyDictionary<(UseKind Kind, string Name), string> entries)` (entry JSON text); `static UseResolver Empty`; `static string KindName(UseKind)` -> `errors|retries|timeouts`; `static bool IsName(UseKind kind, JsonNode? reference)`; `JsonNode? Resolve(UseKind kind, JsonNode? reference)`; `TimeSpan? Timeout(JsonNode? reference)`.

- [ ] **Step 1: Write the failing tests**

```csharp
using System.Text.Json.Nodes;
using Dws.Flow;
using FluentAssertions;
using Xunit;

namespace Dws.Flow.Tests;

public sealed class UseResolverTests
{
    private static UseResolver Resolver() => new(new Dictionary<(UseKind, string), string>
    {
        [(UseKind.Timeouts, "fastCall")] = """{"after":{"seconds":10}}""",
        [(UseKind.Timeouts, "oneDay")] = """{"after":{"days":1}}""",
        [(UseKind.Errors, "PT5S")] = """{"type":"https://example.com/errors/x","status":409,"title":"x"}""",
    });

    [Fact]
    public void ReturnsNothingForAnAbsentReference()
    {
        Resolver().Resolve(UseKind.Timeouts, null).Should().BeNull();
        Resolver().Timeout(null).Should().BeNull();
    }

    [Fact]
    public void ReturnsAnInlineObjectAsWritten()
    {
        JsonNode inline = JsonNode.Parse("""{ "after": { "seconds": 3 } }""")!;

        Resolver().Resolve(UseKind.Timeouts, inline).Should().BeSameAs(inline);
    }

    [Fact]
    public void ReturnsAnIsoStringTimeoutAsWritten()
    {
        JsonNode inline = JsonValue.Create("PT5S");

        Resolver().Resolve(UseKind.Timeouts, inline).Should().BeSameAs(inline);
        Resolver().Timeout(inline).Should().Be(TimeSpan.FromSeconds(5));
    }

    [Fact]
    public void LooksUpANamedTimeout()
    {
        Resolver().Timeout(JsonValue.Create("fastCall")).Should().Be(TimeSpan.FromSeconds(10));
    }

    [Fact]
    public void ReadsANamedTimeoutOfOneDayAsTwentyFourHours()
    {
        TimeSpan? timeout = Resolver().Timeout(JsonValue.Create("oneDay"));

        TaskTimeout.Format(timeout!.Value).Should().Be("PT24H");
    }

    [Fact]
    public void TreatsAnIsoShapedStringAsANameForErrors()
    {
        Resolver().Resolve(UseKind.Errors, JsonValue.Create("PT5S"))!["status"]!.GetValue<int>().Should().Be(409);
    }

    [Theory]
    [InlineData("P2")]
    [InlineData("PT5X")]
    public void TreatsAnIsoDesignatorStringAsAnInlineTimeoutEvenWhenMalformed(string text)
    {
        UseResolver.IsName(UseKind.Timeouts, JsonValue.Create(text)).Should().BeFalse();

        Action act = () => Resolver().Timeout(JsonValue.Create(text));

        act.Should().Throw<InvalidOperationException>().Which.Message.Should().Contain("not a valid ISO-8601 duration");
    }

    [Fact]
    public void FailsWhenANameWasNotLoadedAtBoot()
    {
        Action act = () => Resolver().Resolve(UseKind.Timeouts, JsonValue.Create("nope"));

        act.Should().Throw<InvalidOperationException>().Which.Message.Should().Contain("timeouts").And.Contain("nope");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd dws-flow && dotnet test test/dws-flow.Tests.csproj --filter "FullyQualifiedName~UseResolverTests"`
Expected: compile failure, `UseResolver`/`UseKind` not defined.

- [ ] **Step 3: Write the minimal implementation**

```csharp
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace Dws.Flow;

/// <summary>The three <c>use</c> kinds a Flow node can reference by name; each is a key prefix in the version's store.</summary>
public enum UseKind
{
    Errors,
    Retries,
    Timeouts,
}

/// <summary>Resolves a <c>use</c> reference to its entry: the inline value as written, or the named entry loaded at
/// boot. This is the only place that decides whether a reference is inline or a name, and it does no I/O.</summary>
public sealed partial class UseResolver
{
    public static readonly UseResolver Empty = new(new Dictionary<(UseKind, string), string>());

    private readonly IReadOnlyDictionary<(UseKind Kind, string Name), string> entries;

    public UseResolver(IReadOnlyDictionary<(UseKind Kind, string Name), string> entries)
    {
        this.entries = entries;
    }

    public static string KindName(UseKind kind) => kind switch
    {
        UseKind.Errors => "errors",
        UseKind.Retries => "retries",
        _ => "timeouts",
    };

    public static bool IsName(UseKind kind, JsonNode? reference) =>
        reference is JsonValue value
        && value.TryGetValue<string>(out string? text)
        && !(kind == UseKind.Timeouts && DurationDesignator().IsMatch(text));

    public JsonNode? Resolve(UseKind kind, JsonNode? reference)
    {
        if (!IsName(kind, reference))
        {
            return reference;
        }

        string name = reference!.GetValue<string>();
        return entries.TryGetValue((kind, name), out string? json)
            ? JsonNode.Parse(json)
            : throw new InvalidOperationException($"config failure: use.{KindName(kind)} '{name}' was not loaded at boot");
    }

    public TimeSpan? Timeout(JsonNode? reference) => TaskTimeout.Parse(Resolve(UseKind.Timeouts, reference));

    [GeneratedRegex("^[+-]?P[0-9T]")]
    private static partial Regex DurationDesignator();
}
```

Note: `reference!` breaks the package's "no `!`" rule only if copied literally; replace with `reference is JsonValue named && named.TryGetValue<string>(out string? name)` pattern matching if the implementer prefers (behavior identical).

- [ ] **Step 4: Run to verify it passes**

Run: same command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add dws-flow/src/UseResolver.cs dws-flow/test/UseResolverTests.cs
git commit -m "feat(flow): resolve use entries inline or by name in one place"
```

### Task 2: `UseReferences.Collect` (1.2)

**Files:**
- Create: `dws-flow/src/UseReferences.cs`
- Test: `dws-flow/test/UseReferencesTests.cs`

**Interfaces:**
- Consumes: `UseResolver.IsName(UseKind, JsonNode?)`, `UseKind`, `SingleNodeDefinition(Workflow, Version, NodeId, Scope, Tasks, Children, Catch)`.
- Produces: `static IReadOnlySet<(UseKind Kind, string Name)> UseReferences.Collect(SingleNodeDefinition definition)`.

- [ ] **Step 1: Write the failing tests**

```csharp
using System.Text.Json.Nodes;
using Dws.Flow;
using FluentAssertions;
using Xunit;

namespace Dws.Flow.Tests;

public sealed class UseReferencesTests
{
    private static SingleNodeDefinition Node(string tasksJson) =>
        new("order", "order@v1", "order-main", "main", JsonNode.Parse(tasksJson)!.AsArray(), new JsonObject(), null);

    [Fact]
    public void CollectsNamedTimeoutErrorAndRetryReferences()
    {
        SingleNodeDefinition node = Node("""
            [
              { "a": { "call": "http", "timeout": "fastCall" } },
              { "b": { "raise": { "error": "outOfStock" } } },
              { "c": { "try": [], "catch": { "retry": "transientBackoff" } } }
            ]
            """);

        UseReferences.Collect(node).Should().BeEquivalentTo(new[]
        {
            (UseKind.Timeouts, "fastCall"),
            (UseKind.Errors, "outOfStock"),
            (UseKind.Retries, "transientBackoff"),
        });
    }

    [Fact]
    public void IgnoresInlineValues()
    {
        SingleNodeDefinition node = Node("""
            [
              { "a": { "call": "http", "timeout": "PT5S" } },
              { "b": { "call": "http", "timeout": { "after": { "seconds": 3 } } } },
              { "c": { "raise": { "error": { "type": "x", "status": 400, "title": "t" } } } }
            ]
            """);

        UseReferences.Collect(node).Should().BeEmpty();
    }

    [Fact]
    public void CollectsASharedNameOnce()
    {
        SingleNodeDefinition node = Node("""
            [ { "a": { "timeout": "fastCall" } }, { "b": { "timeout": "fastCall" } } ]
            """);

        UseReferences.Collect(node).Should().ContainSingle();
    }

    [Fact]
    public void ToleratesScalarRaiseAndCatchBodies()
    {
        SingleNodeDefinition node = Node("""[ { "a": { "raise": "oops", "catch": true } } ]""");

        UseReferences.Collect(node).Should().BeEmpty();
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd dws-flow && dotnet test test/dws-flow.Tests.csproj --filter "FullyQualifiedName~UseReferencesTests"`
Expected: compile failure, `UseReferences` not defined.

- [ ] **Step 3: Write the minimal implementation**

```csharp
using System.Text.Json.Nodes;

namespace Dws.Flow;

/// <summary>Finds the <c>use</c> names this node's own tasks reference, so only those are fetched at boot.</summary>
public static class UseReferences
{
    public static IReadOnlySet<(UseKind Kind, string Name)> Collect(SingleNodeDefinition definition)
    {
        HashSet<(UseKind Kind, string Name)> references = new();
        foreach (JsonNode? task in definition.Tasks)
        {
            if (task is JsonObject taskObject && taskObject.Single().Value is JsonObject body)
            {
                Add(references, UseKind.Timeouts, body["timeout"]);
                Add(references, UseKind.Errors, (body["raise"] as JsonObject)?["error"]);
                Add(references, UseKind.Retries, (body["catch"] as JsonObject)?["retry"]);
            }
        }

        return references;
    }

    private static void Add(HashSet<(UseKind Kind, string Name)> references, UseKind kind, JsonNode? reference)
    {
        if (UseResolver.IsName(kind, reference) && reference is JsonValue value && value.TryGetValue<string>(out string? name))
        {
            references.Add((kind, name));
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes** — same command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add dws-flow/src/UseReferences.cs dws-flow/test/UseReferencesTests.cs
git commit -m "feat(flow): collect the use names a node references"
```

### Task 3: Store seam and boot-time load (1.3)

**Files:**
- Create: `dws-flow/src/IUseStore.cs`, `dws-flow/src/UseResolverLoader.cs`
- Test: `dws-flow/test/UseResolverLoaderTests.cs`

**Interfaces:**
- Consumes: `UseReferences.Collect`, `UseResolver(entries)`, `UseResolver.KindName`, `TaskTimeout.Parse`, `DefinitionLoadException(string)`.
- Produces: `interface IUseStore { Task<IReadOnlyDictionary<string, string>> Get(string storeName, IReadOnlyCollection<string> keys); }` (found key -> JSON text; absent keys omitted); `static class UseResolverLoader { const string StoreEnvironmentVariable = "DWS_FLOW_USE_STORE"; static Task<UseResolver> Load(SingleNodeDefinition definition, string? storeName, IUseStore store); }`.

- [ ] **Step 1: Write the failing tests** (the fake lives in the test file)

```csharp
using System.Text.Json.Nodes;
using Dws.Flow;
using FluentAssertions;
using Xunit;

namespace Dws.Flow.Tests;

public sealed class UseResolverLoaderTests
{
    private static SingleNodeDefinition Node(string tasksJson) =>
        new("order", "order@v1", "order-main", "main", JsonNode.Parse(tasksJson)!.AsArray(), new JsonObject(), null);

    private const string NamedTimeout = """[ { "a": { "call": "http", "timeout": "fastCall" } } ]""";

    [Fact]
    public async Task LoadsExactlyTheReferencedKeysFromTheNamedStoreInOneCall()
    {
        FakeUseStore store = new() { ["timeouts.fastCall"] = """{"after":{"seconds":10}}""", ["timeouts.other"] = """{"after":{"seconds":1}}""" };

        UseResolver resolver = await UseResolverLoader.Load(Node(NamedTimeout), "order-flow-use", store);

        store.Calls.Should().ContainSingle();
        store.Calls[0].StoreName.Should().Be("order-flow-use");
        store.Calls[0].Keys.Should().Equal("timeouts.fastCall");
        resolver.Timeout(JsonValue.Create("fastCall")).Should().Be(TimeSpan.FromSeconds(10));
    }

    [Fact]
    public async Task RequestsASharedNameOnce()
    {
        FakeUseStore store = new() { ["timeouts.fastCall"] = """{"after":{"seconds":10}}""" };
        SingleNodeDefinition node = Node("""[ { "a": { "timeout": "fastCall" } }, { "b": { "timeout": "fastCall" } } ]""");

        await UseResolverLoader.Load(node, "s", store);

        store.Calls[0].Keys.Should().Equal("timeouts.fastCall");
    }

    [Fact]
    public async Task StartsWithoutAStoreNameOrStoreCallWhenThereAreNoReferences()
    {
        FakeUseStore store = new();
        SingleNodeDefinition node = Node("""[ { "a": { "call": "http", "timeout": "PT5S" } } ]""");

        UseResolver resolver = await UseResolverLoader.Load(node, null, store);

        store.Calls.Should().BeEmpty();
        resolver.Should().BeSameAs(UseResolver.Empty);
    }

    [Fact]
    public async Task FailsNamingKindAndNameWhenTheEntryIsMissing()
    {
        Func<Task> act = () => UseResolverLoader.Load(Node(NamedTimeout), "order-flow-use", new FakeUseStore());

        (await act.Should().ThrowAsync<DefinitionLoadException>()).Which.Message
            .Should().Contain("timeouts").And.Contain("fastCall").And.Contain("order-flow-use");
    }

    [Fact]
    public async Task TreatsAnEmptyStoreValueAsMissing()
    {
        FakeUseStore store = new() { ["timeouts.fastCall"] = "" };

        Func<Task> act = () => UseResolverLoader.Load(Node(NamedTimeout), "s", store);

        await act.Should().ThrowAsync<DefinitionLoadException>();
    }

    [Fact]
    public async Task FailsWithoutCallingTheStoreWhenANameIsOutsideTheCharset()
    {
        FakeUseStore store = new();
        SingleNodeDefinition node = Node("""[ { "a": { "timeout": "bad name" } } ]""");

        Func<Task> act = () => UseResolverLoader.Load(node, "s", store);

        (await act.Should().ThrowAsync<DefinitionLoadException>()).Which.Message.Should().Contain("timeouts").And.Contain("bad name");
        store.Calls.Should().BeEmpty();
    }

    [Theory]
    [InlineData("""{"after":"soon"}""")]
    [InlineData("""[]""")]
    [InlineData("\"PT5S\"")]
    [InlineData("not json")]
    public async Task FailsNamingKindAndNameWhenTheEntryIsUnparseable(string entry)
    {
        FakeUseStore store = new() { ["timeouts.fastCall"] = entry };

        Func<Task> act = () => UseResolverLoader.Load(Node(NamedTimeout), "s", store);

        (await act.Should().ThrowAsync<DefinitionLoadException>()).Which.Message.Should().Contain("timeouts").And.Contain("fastCall");
    }

    [Fact]
    public async Task FailsNamingTheStoreVariableWhenReferencesExistAndNoStoreIsConfigured()
    {
        Func<Task> act = () => UseResolverLoader.Load(Node(NamedTimeout), "  ", new FakeUseStore());

        (await act.Should().ThrowAsync<DefinitionLoadException>()).Which.Message
            .Should().Contain("DWS_FLOW_USE_STORE").And.Contain("timeouts").And.Contain("fastCall");
    }

    [Fact]
    public async Task LoadsErrorAndRetryEntriesAsObjects()
    {
        FakeUseStore store = new()
        {
            ["errors.outOfStock"] = """{"type":"https://example.com/errors/out-of-stock","status":409,"title":"Out of stock"}""",
            ["retries.transientBackoff"] = """{"delay":{"seconds":2},"limit":{"attempt":{"count":5}}}""",
        };
        SingleNodeDefinition node = Node("""
            [ { "b": { "raise": { "error": "outOfStock" } } }, { "c": { "try": [], "catch": { "retry": "transientBackoff" } } } ]
            """);

        UseResolver resolver = await UseResolverLoader.Load(node, "s", store);

        resolver.Resolve(UseKind.Errors, JsonValue.Create("outOfStock"))!["status"]!.GetValue<int>().Should().Be(409);
        resolver.Resolve(UseKind.Retries, JsonValue.Create("transientBackoff"))!["limit"].Should().NotBeNull();
    }

    private sealed class FakeUseStore : Dictionary<string, string>, IUseStore
    {
        public List<(string StoreName, string[] Keys)> Calls { get; } = new();

        public Task<IReadOnlyDictionary<string, string>> Get(string storeName, IReadOnlyCollection<string> keys)
        {
            Calls.Add((storeName, keys.ToArray()));
            IReadOnlyDictionary<string, string> found = keys.Where(ContainsKey).ToDictionary(key => key, key => this[key]);
            return Task.FromResult(found);
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd dws-flow && dotnet test test/dws-flow.Tests.csproj --filter "FullyQualifiedName~UseResolverLoaderTests"`
Expected: compile failure, `IUseStore`/`UseResolverLoader` not defined.

- [ ] **Step 3: Write the minimal implementation**

`dws-flow/src/IUseStore.cs`:

```csharp
namespace Dws.Flow;

/// <summary>Reads named <c>use</c> entries from the version's configuration store; keys that are absent are omitted.</summary>
public interface IUseStore
{
    Task<IReadOnlyDictionary<string, string>> Get(string storeName, IReadOnlyCollection<string> keys);
}
```

`dws-flow/src/UseResolverLoader.cs`:

```csharp
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace Dws.Flow;

/// <summary>Builds the boot-time <see cref="UseResolver"/>: fetches exactly the names the node references and fails
/// the boot, naming kind and name, when any is missing or invalid.</summary>
public static partial class UseResolverLoader
{
    public const string StoreEnvironmentVariable = "DWS_FLOW_USE_STORE";

    public static async Task<UseResolver> Load(SingleNodeDefinition definition, string? storeName, IUseStore store)
    {
        IReadOnlyList<(UseKind Kind, string Name)> references = UseReferences.Collect(definition)
            .OrderBy(reference => reference.Kind).ThenBy(reference => reference.Name, StringComparer.Ordinal).ToList();
        if (references.Count == 0)
        {
            return UseResolver.Empty;
        }

        foreach ((UseKind kind, string name) in references.Where(reference => !KeyName().IsMatch(reference.Name)))
        {
            throw new DefinitionLoadException($"use.{UseResolver.KindName(kind)} name '{name}' must match [-._a-zA-Z0-9]+");
        }

        if (string.IsNullOrWhiteSpace(storeName))
        {
            (UseKind firstKind, string firstName) = references[0];
            throw new DefinitionLoadException(
                $"{StoreEnvironmentVariable} is required because this node references use.{UseResolver.KindName(firstKind)} '{firstName}'");
        }

        string[] keys = references.Select(reference => $"{UseResolver.KindName(reference.Kind)}.{reference.Name}").ToArray();
        IReadOnlyDictionary<string, string> found = await store.Get(storeName, keys);

        Dictionary<(UseKind Kind, string Name), string> entries = new();
        foreach ((UseKind kind, string name) in references)
        {
            string key = $"{UseResolver.KindName(kind)}.{name}";
            if (!found.TryGetValue(key, out string? json) || string.IsNullOrWhiteSpace(json))
            {
                throw new DefinitionLoadException($"use.{UseResolver.KindName(kind)} '{name}' is not defined in configuration store '{storeName}'");
            }

            Validate(kind, name, json);
            entries[(kind, name)] = json;
        }

        return new UseResolver(entries);
    }

    private static void Validate(UseKind kind, string name, string json)
    {
        try
        {
            if (JsonNode.Parse(json) is not JsonObject entry)
            {
                throw new DefinitionLoadException($"use.{UseResolver.KindName(kind)} '{name}' is not a valid entry: expected a JSON object");
            }

            if (kind == UseKind.Timeouts)
            {
                TaskTimeout.Parse(entry);
            }
        }
        catch (DefinitionLoadException)
        {
            throw; // don't double-wrap
        }
        catch (Exception exception) when (exception is System.Text.Json.JsonException or InvalidOperationException)
        {
            throw new DefinitionLoadException($"use.{UseResolver.KindName(kind)} '{name}' is not a valid entry: {exception.Message}", exception);
        }
    }

    [GeneratedRegex("^[-._a-zA-Z0-9]+$")]
    private static partial Regex KeyName();
}
```

- [ ] **Step 4: Run to verify it passes** — same command. Expected: PASS. Fix any case where `{"after":"soon"}` does not throw: `TaskTimeout.Parse` on string `"soon"` throws `InvalidOperationException` "not a valid ISO-8601 duration", which is caught above.

- [ ] **Step 5: Commit**

```bash
git add dws-flow/src/IUseStore.cs dws-flow/src/UseResolverLoader.cs dws-flow/test/UseResolverLoaderTests.cs
git commit -m "feat(flow): load referenced use entries at boot and fail on a bad one"
```

### Task 4: Sequencer resolves named timeouts (2.1)

**Files:**
- Modify: `dws-flow/src/SequencerRunner.cs` (the `Run` signature and `DispatchTask`), `dws-flow/src/FlowDefinitionHolder.cs`, `dws-flow/src/FlowWorkflow.cs`
- Test: `dws-flow/test/SequencerRunnerTests.cs`, `dws-flow/test/ReplayTests.cs`

**Interfaces:**
- Consumes: `UseResolver.Timeout(JsonNode?)`, `UseResolver.Empty`.
- Produces: `SequencerRunner.Run(FlowInput input, SingleNodeDefinition definition, IChildCaller caller, UseResolver? resolver = null)`; `FlowDefinitionHolder.Initialize(SingleNodeDefinition loadedDefinition, UseResolver? loadedResolver = null)`; `FlowDefinitionHolder.UseResolver` (defaults to `UseResolver.Empty`).

- [ ] **Step 1: Write the failing tests**

In `SequencerRunnerTests.cs`, extend the file's `RecordingChildCaller` stand-in (its `WithTimeout` currently always returns `(false, await call)`) so a test can make the timer win and see the duration it was given:

```csharp
public bool TimerWins { get; set; }

public TimeSpan? RequestedTimeout { get; private set; }

public async Task<(bool TimedOut, JsonNode? Result)> WithTimeout(TimeSpan timeout, Task<JsonNode?> call)
{
    RequestedTimeout = timeout;
    return TimerWins ? (true, null) : (false, await call);
}
```

Then add the tests (they reuse the file's `Definition(tasks, children)` helper):

```csharp
private static async Task<(string Message, TimeSpan? Requested)> RunNamedTimeout(string entryJson)
{
    JsonArray tasks = JsonNode.Parse("""[ { "slow": { "call": "http", "timeout": "fastCall" } } ]""")!.AsArray();
    JsonObject children = JsonNode.Parse("""{ "slow": "child-slow" }""")!.AsObject();
    UseResolver resolver = new(new Dictionary<(UseKind, string), string> { [(UseKind.Timeouts, "fastCall")] = entryJson });
    RecordingChildCaller caller = new() { TimerWins = true };

    Func<Task> act = () => SequencerRunner.Run(new FlowInput(null, null, "root", null), Definition(tasks, children), caller, resolver);

    InvalidOperationException failure = (await act.Should().ThrowAsync<InvalidOperationException>()).Which;
    return (failure.Message, caller.RequestedTimeout);
}

[Fact]
public async Task FailsANamedTimeoutWithTheSameWordingAsAnInlineOne()
{
    (string message, TimeSpan? requested) = await RunNamedTimeout("""{"after":{"seconds":10}}""");

    message.Should().Be("task 'slow' timed out after PT10S");
    requested.Should().Be(TimeSpan.FromSeconds(10));
}

[Fact]
public async Task RendersANamedOneDayTimeoutAsTwentyFourHours()
{
    (string message, _) = await RunNamedTimeout("""{"after":{"days":1}}""");

    message.Should().Be("task 'slow' timed out after PT24H");
}

[Fact]
public async Task FailsAnUnloadedTimeoutNameWhenNoResolverIsGiven()
{
    JsonArray tasks = JsonNode.Parse("""[ { "slow": { "call": "http", "timeout": "fastCall" } } ]""")!.AsArray();
    JsonObject children = JsonNode.Parse("""{ "slow": "child-slow" }""")!.AsObject();

    Func<Task> act = () => SequencerRunner.Run(new FlowInput(null, null, "root", null), Definition(tasks, children), new RecordingChildCaller());

    (await act.Should().ThrowAsync<InvalidOperationException>()).Which.Message
        .Should().Contain("timeouts").And.Contain("fastCall");
}

[Fact]
public async Task KeepsAnInlineTimeoutWorkingWithoutAResolver()
{
    JsonArray tasks = JsonNode.Parse("""[ { "slow": { "call": "http", "timeout": "PT5S" } } ]""")!.AsArray();
    JsonObject children = JsonNode.Parse("""{ "slow": "child-slow" }""")!.AsObject();
    RecordingChildCaller caller = new() { TimerWins = true };

    Func<Task> act = () => SequencerRunner.Run(new FlowInput(null, null, "root", null), Definition(tasks, children), caller);

    (await act.Should().ThrowAsync<InvalidOperationException>()).Which.Message.Should().Be("task 'slow' timed out after PT5S");
}
```

In `ReplayTests.cs` add a sibling of the existing replay fact using `"timeout": "fastCall"` and:

```csharp
FlowDefinitionHolder.Initialize(definition, new UseResolver(new Dictionary<(UseKind, string), string>
{
    [(UseKind.Timeouts, "fastCall")] = """{"after":{"seconds":5}}""",
}));
```

with the same live-then-replay assertions (commands equal, results equal, a `timer:` command present). Reset the holder at the end of both replay facts with `FlowDefinitionHolder.Initialize(definition)` so tests do not leak a resolver into each other.

- [ ] **Step 2: Run to verify it fails**

Run: `cd dws-flow && dotnet test test/dws-flow.Tests.csproj --filter "FullyQualifiedName~SequencerRunnerTests|FullyQualifiedName~ReplayTests"`
Expected: compile failure (extra argument / overload not found).

- [ ] **Step 3: Write the minimal implementation**

`SequencerRunner.Run` gains `UseResolver? resolver = null`, normalised once with `UseResolver effectiveResolver = resolver ?? UseResolver.Empty;`, and passed to `DispatchTask`, which replaces `TaskTimeout.Parse(body["timeout"])` with `resolver.Timeout(body["timeout"])`. `FlowDefinitionHolder` stores the resolver beside the definition:

```csharp
private static UseResolver? resolver;

public static void Initialize(SingleNodeDefinition loadedDefinition, UseResolver? loadedResolver = null)
{
    definition = loadedDefinition;
    resolver = loadedResolver;
}

public static UseResolver UseResolver => resolver ?? UseResolver.Empty;
```

`FlowWorkflow.RunAsync` passes `FlowDefinitionHolder.UseResolver` as the fourth argument to `SequencerRunner.Run`.

- [ ] **Step 4: Run to verify it passes**

Run: `cd dws-flow && dotnet test test/dws-flow.Tests.csproj`
Expected: all tests PASS, including the unchanged 3a tests (inline timeouts untouched).

- [ ] **Step 5: Commit**

```bash
git add dws-flow/src dws-flow/test
git commit -m "feat(flow): resolve a named task timeout through the use resolver"
```

### Task 5: Dapr adapter, boot wiring, docs, gate (2.2, 3.1, 3.2)

**Files:**
- Create: `dws-flow/src/DaprUseStore.cs`
- Modify: `dws-flow/src/Program.cs`, `dws-flow/README.md`, `dws-flow/CLAUDE.md`
- Test: `dws-flow/test/ReplayTests.cs` (existing source scan covers the new file)

**Interfaces:**
- Consumes: `IUseStore`, `UseResolverLoader.Load`, `UseResolverLoader.StoreEnvironmentVariable`, `FlowDefinitionHolder.Initialize(definition, resolver)`.
- Produces: `DaprUseStore : IUseStore` (parameterless constructor, lazy client).

- [ ] **Step 1: Confirm the Dapr API before coding.** Check `Dapr.Client` 1.18.10 for `DaprClient.GetConfiguration(string storeName, IReadOnlyList<string> keys, IReadOnlyDictionary<string,string>? metadata, CancellationToken)` returning a response with `Items` (`IReadOnlyDictionary<string, ConfigurationItem>`, `Value` string) and for `WaitForSidecarAsync(CancellationToken)`. Use the `docs-researcher` agent or `context7` if unsure. If the shape differs, adjust only `DaprUseStore`; nothing else depends on it.

- [ ] **Step 2: Write `DaprUseStore`** (no unit test: it needs a sidecar, covered by 3h / Phase 4)

```csharp
using Dapr.Client;

namespace Dws.Flow;

/// <summary>Production <see cref="IUseStore"/>: reads the version's store through the sidecar's configuration API.
/// The client is created on first use, so a node with no references never connects.</summary>
public sealed class DaprUseStore : IUseStore
{
    private static readonly TimeSpan SidecarWait = TimeSpan.FromSeconds(30);

    public async Task<IReadOnlyDictionary<string, string>> Get(string storeName, IReadOnlyCollection<string> keys)
    {
        using DaprClient client = new DaprClientBuilder().Build();
        using CancellationTokenSource wait = new(SidecarWait);
        await client.WaitForSidecarAsync(wait.Token);

        GetConfigurationResponse response = await client.GetConfiguration(storeName, keys.ToList());
        return response.Items.ToDictionary(item => item.Key, item => item.Value.Value);
    }
}
```

- [ ] **Step 3: Wire `Program.cs`** (boot, before `AddDaprWorkflow`; top-level `await` is allowed)

```csharp
using Dapr.Workflow;
using Dws.Flow;

SingleNodeDefinition definition =
    new SingleNodeDefinitionLoader(Environment.GetEnvironmentVariable(SingleNodeDefinitionLoader.DefinitionPathEnvironmentVariable)).Load();
UseResolver useResolver = await UseResolverLoader.Load(
    definition,
    Environment.GetEnvironmentVariable(UseResolverLoader.StoreEnvironmentVariable),
    new DaprUseStore());
FlowDefinitionHolder.Initialize(definition, useResolver);

WebApplicationBuilder builder = WebApplication.CreateBuilder(args);
builder.Services.AddSingleton(definition);
builder.Services.AddSingleton(useResolver);
// ... AddDaprWorkflow, /healthz unchanged
```

- [ ] **Step 4: Update docs.** `dws-flow/README.md`: env var `DWS_FLOW_USE_STORE` (required only when the node references a name), the entry keys/shapes, the boot failures. `dws-flow/CLAUDE.md`: a short "UseResolver" subsection (one rule in one class, boot-time load, `Environment.` only in `Program.cs`, `DefinitionLoadException` for boot failures) and fix the sequencer paragraph that says a name fails as an invalid duration.

- [ ] **Step 5: Run the full gate**

Run: `cd dws-flow && dotnet test test/dws-flow.Tests.csproj`
Expected: all PASS, including `WorkflowSourceUsesNoNonDeterministicApis` (no `Environment.`/clock/random in `src/` outside `Program.cs`). Also run `dotnet build` so the adapter compiles against the real Dapr.Client.

- [ ] **Step 6: Commit**

```bash
git add dws-flow
git commit -m "feat(flow): load use entries from the Dapr configuration store at boot"
```

## Self-review

- Spec coverage: collect (T2), boot load/keys/store name (T3, T5), no-references-no-store (T3), failures incl. missing store name (T3), single rule (T1), replay safety (T4, existing source scan), adapter behind the interface (T3, T5), modified Task timeout requirement (T4).
- Final report must list: files changed, gate result, deviations from v1 or the handoff, every assumed entry shape and wording (D3, D6, entry JSON per ADR 0010), and the `DWS_FLOW_USE_STORE` contract for Phase 4.
