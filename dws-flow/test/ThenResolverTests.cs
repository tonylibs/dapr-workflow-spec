using System.Text.Json.Nodes;
using Dws.Flow;
using FluentAssertions;
using Xunit;

namespace Dws.Flow.Tests;

public sealed class ThenResolverTests
{
    private static readonly Dictionary<string, int> IndexByName = new() { ["a"] = 0, ["b"] = 1, ["c"] = 2 };

    [Fact]
    public void AdvancesToNextTaskWhenThenIsAbsent()
    {
        ThenOutcome outcome = ThenResolver.Next(null, 0, IndexByName);

        outcome.Kind.Should().Be(ThenKind.Continue);
        outcome.Pc.Should().Be(1);
    }

    [Fact]
    public void AdvancesToNextTaskWhenThenIsContinue()
    {
        ThenOutcome outcome = ThenResolver.Next(JsonValue.Create("continue"), 0, IndexByName);

        outcome.Kind.Should().Be(ThenKind.Continue);
        outcome.Pc.Should().Be(1);
    }

    [Fact]
    public void JumpsToNamedTask()
    {
        ThenOutcome outcome = ThenResolver.Next(JsonValue.Create("c"), 0, IndexByName);

        outcome.Kind.Should().Be(ThenKind.Continue);
        outcome.Pc.Should().Be(2);
    }

    [Fact]
    public void EndsTheScopeWhenThenIsEnd()
    {
        ThenOutcome outcome = ThenResolver.Next(JsonValue.Create("end"), 0, IndexByName);

        outcome.Kind.Should().Be(ThenKind.End);
    }

    [Fact]
    public void ExitsTheWorkflowWhenThenIsExit()
    {
        ThenOutcome outcome = ThenResolver.Next(JsonValue.Create("exit"), 0, IndexByName);

        outcome.Kind.Should().Be(ThenKind.Exit);
    }

    [Fact]
    public void FailsWithNotDeclaredMessageForUnknownTarget()
    {
        Action action = () => ThenResolver.Next(JsonValue.Create("z"), 0, IndexByName);

        action.Should().Throw<InvalidOperationException>()
            .WithMessage("flow references task 'z', which is not declared in this task scope");
    }
}
