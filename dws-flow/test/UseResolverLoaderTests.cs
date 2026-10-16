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
