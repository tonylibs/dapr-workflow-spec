using System.Text.Json.Nodes;
using Dws.Flow;
using FluentAssertions;
using Xunit;

namespace Dws.Flow.Tests;

public sealed class TaskTimeoutTests
{
    [Fact]
    public void ReturnsNullWhenTimeoutIsAbsent()
    {
        TaskTimeout.Parse(null).Should().BeNull();
    }

    [Fact]
    public void ParsesAnIso8601StringTimeout()
    {
        TaskTimeout.Parse(JsonValue.Create("PT5S")).Should().Be(TimeSpan.FromSeconds(5));
    }

    [Fact]
    public void ParsesAnInlineDurationObjectTimeout()
    {
        JsonNode timeout = JsonNode.Parse("""{ "seconds": 5 }""")!;

        TaskTimeout.Parse(timeout).Should().Be(TimeSpan.FromSeconds(5));
    }

    [Fact]
    public void ParsesADurationObjectWrappedInAfter()
    {
        JsonNode timeout = JsonNode.Parse("""{ "after": { "minutes": 1, "seconds": 30 } }""")!;

        TaskTimeout.Parse(timeout).Should().Be(TimeSpan.FromSeconds(90));
    }

    [Fact]
    public void FormatsAsIso8601DurationLikeV1DurationToString()
    {
        TaskTimeout.Format(TimeSpan.FromSeconds(5)).Should().Be("PT5S");
        TaskTimeout.Format(TimeSpan.FromMinutes(90)).Should().Be("PT1H30M");
    }

    [Fact]
    public void FormatsOneDayWithoutADayComponentLikeJavaDurationToString()
    {
        TaskTimeout.Format(TimeSpan.FromDays(1)).Should().Be("PT24H");
    }

    [Fact]
    public void FormatsTwentyFiveHoursWithoutADayComponent()
    {
        TaskTimeout.Format(TimeSpan.FromHours(25)).Should().Be("PT25H");
    }

    [Fact]
    public void FormatsFractionalSecondsTrimmingTrailingZeros()
    {
        TaskTimeout.Format(TimeSpan.FromSeconds(1.5)).Should().Be("PT1.5S");
    }

    [Fact]
    public void FormatsZeroDurationAsPt0S()
    {
        TaskTimeout.Format(TimeSpan.Zero).Should().Be("PT0S");
    }

    [Fact]
    public void ThrowsAConfigFailureForMalformedIsoStringTimeouts()
    {
        Action action = () => TaskTimeout.Parse(JsonValue.Create("not-a-duration"));

        action.Should().Throw<InvalidOperationException>()
            .WithMessage("config failure: timeout 'not-a-duration' is not a valid ISO-8601 duration");
    }

    [Fact]
    public void ThrowsAConfigFailureForNonNumericDurationObjectComponents()
    {
        JsonNode timeout = JsonNode.Parse("""{ "seconds": "soon" }""")!;

        Action action = () => TaskTimeout.Parse(timeout);

        action.Should().Throw<InvalidOperationException>()
            .WithMessage("config failure: timeout.seconds must be a number");
    }

    [Fact]
    public async Task FailsWithTimedOutMessageWhenTimerWinsTheRace()
    {
        JsonArray tasks = JsonNode.Parse("""[ { "a": { "call": "http", "timeout": "PT5S" } } ]""")!.AsArray();
        JsonObject children = JsonNode.Parse("""{ "a": "child-a" }""")!.AsObject();
        SingleNodeDefinition definition = Definition(tasks, children);
        StubChildCaller caller = new(pending: true);
        FlowInput input = new(JsonValue.Create("data"), null, "root", null);

        Func<Task> action = () => SequencerRunner.Run(input, definition, caller, (_, _) => Task.CompletedTask);

        (await action.Should().ThrowAsync<InvalidOperationException>()).Which.Message
            .Should().Be("task 'a' timed out after PT5S");
    }

    [Fact]
    public async Task ReturnsTheChildResultWhenTheCallWinsTheRace()
    {
        JsonArray tasks = JsonNode.Parse("""[ { "a": { "call": "http", "timeout": "PT5S" } } ]""")!.AsArray();
        JsonObject children = JsonNode.Parse("""{ "a": "child-a" }""")!.AsObject();
        SingleNodeDefinition definition = Definition(tasks, children);
        StubChildCaller caller = new(pending: false);
        FlowInput input = new(JsonValue.Create("data"), null, "root", null);

        JsonNode? result = await SequencerRunner.Run(input, definition, caller, (_, _) => new TaskCompletionSource().Task);

        result!.GetValue<string>().Should().Be("data");
    }

    [Fact]
    public async Task PassesThroughAChildFailureMessageContainingTimedOutAfterUnchanged()
    {
        JsonArray tasks = JsonNode.Parse("""[ { "a": { "call": "http" } } ]""")!.AsArray();
        JsonObject children = JsonNode.Parse("""{ "a": "child-a" }""")!.AsObject();
        SingleNodeDefinition definition = Definition(tasks, children);
        StubChildCaller caller = new(pending: false, failure: new InvalidOperationException("task 'nested' timed out after PT1S"));
        FlowInput input = new(JsonValue.Create("data"), null, "root", null);

        Func<Task> action = () => SequencerRunner.Run(input, definition, caller,
            (_, _) => throw new InvalidOperationException("no timer expected"));

        (await action.Should().ThrowAsync<InvalidOperationException>()).Which.Message
            .Should().Be("task 'nested' timed out after PT1S");
    }

    private static SingleNodeDefinition Definition(JsonArray tasks, JsonObject children) =>
        new("order", "order@v1", "order-main", "main", tasks, children, null);

    /// <summary>Supplies pending, completed, or faulted child calls for the real timeout decorator.</summary>
    private sealed class StubChildCaller : IChildCaller
    {
        private readonly bool pending;
        private readonly Exception? failure;

        public StubChildCaller(bool pending, Exception? failure = null)
        {
            this.pending = pending;
            this.failure = failure;
        }

        public Task<JsonNode?> CallStep(string appId, FlowInput input) =>
            failure is not null ? Task.FromException<JsonNode?>(failure)
                : pending ? new TaskCompletionSource<JsonNode?>().Task : Task.FromResult(input.Data);

        public Task<JsonNode?> CallFlow(string appId, string instanceId, FlowInput input) =>
            CallStep(appId, input);
    }
}
