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
