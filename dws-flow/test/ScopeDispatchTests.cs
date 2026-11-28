using Dws.Flow;
using FluentAssertions;
using Xunit;

namespace Dws.Flow.Tests;

public sealed class ScopeDispatchTests
{
    [Theory]
    [InlineData("main")]
    [InlineData("do")]
    public void RoutesSequencerScopesToSequencer(string scope)
    {
        ScopeDispatch.Resolve(scope).Should().Be(ScopeKind.Sequencer);
    }

    [Theory]
    [InlineData("for")]
    [InlineData("try-catch")]
    [InlineData("fork")]
    [InlineData("wait")]
    [InlineData("listen")]
    public void RoutesControllerScopesToNotImplemented(string scope)
    {
        ScopeDispatch.Resolve(scope).Should().Be(ScopeKind.NotImplemented);
    }

    [Theory]
    [InlineData("for", "config failure: scope 'for' is not implemented yet")]
    [InlineData("wait", "config failure: scope 'wait' is not implemented yet")]
    [InlineData("listen", "config failure: scope 'listen' is not implemented yet")]
    public void RendersNotImplementedMessageForControllerScope(string scope, string message)
    {
        ScopeDispatch.NotImplementedMessage(scope).Should().Be(message);
    }
}
