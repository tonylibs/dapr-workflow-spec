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
    public void RoutesControllerScopesToNotImplemented(string scope)
    {
        ScopeDispatch.Resolve(scope).Should().Be(ScopeKind.NotImplemented);
    }

    [Fact]
    public void RendersNotImplementedMessageForForScope()
    {
        ScopeDispatch.NotImplementedMessage("for").Should().Be("config failure: scope 'for' is not implemented yet");
    }
}
