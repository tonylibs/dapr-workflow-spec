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
