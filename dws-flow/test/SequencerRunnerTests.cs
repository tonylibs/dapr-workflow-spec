using System.Text.Json.Nodes;
using Dws.Flow;
using FluentAssertions;
using Xunit;

namespace Dws.Flow.Tests;

public sealed class SequencerRunnerTests
{
    [Fact]
    public async Task RunsTasksInSourceOrderPipingOutputToNextInput()
    {
        JsonArray tasks = JsonNode.Parse("""
            [ { "a": { "call": "http" } }, { "b": { "call": "http" } } ]
            """)!.AsArray();
        JsonObject children = JsonNode.Parse("""{ "a": "child-a", "b": "child-b" }""")!.AsObject();
        SingleNodeDefinition definition = Definition(tasks, children);
        RecordingChildCaller caller = new();
        caller.StepResults["child-a"] = JsonValue.Create("X");
        caller.StepResults["child-b"] = JsonValue.Create("Y");
        FlowInput input = new(JsonValue.Create("start"), null, "root", null);

        JsonNode? result = await SequencerRunner.Run(input, definition, caller, UnexpectedTimer);

        result!.GetValue<string>().Should().Be("Y");
        caller.StepCalls.Should().HaveCount(2);
        caller.StepCalls[0].Input.Data!.GetValue<string>().Should().Be("start");
        caller.StepCalls[1].Input.Data!.GetValue<string>().Should().Be("X");
    }

    [Fact]
    public async Task CallsStepChildOnItsOwnAppIdWithFullEnvelope()
    {
        JsonArray tasks = JsonNode.Parse("""[ { "validateOrder": { "set": { "validated": true } } } ]""")!.AsArray();
        JsonObject children = JsonNode.Parse("""{ "validateOrder": "validate-order" }""")!.AsObject();
        SingleNodeDefinition definition = Definition(tasks, children);
        RecordingChildCaller caller = new();
        Dictionary<string, JsonNode?> variables = new() { ["x"] = JsonValue.Create(1) };
        FlowInput input = new(JsonValue.Create("data"), variables, "root", "1.0");

        await SequencerRunner.Run(input, definition, caller, UnexpectedTimer);

        caller.StepCalls.Should().ContainSingle();
        caller.StepCalls[0].AppId.Should().Be("validate-order");
        caller.StepCalls[0].Input.Data!.GetValue<string>().Should().Be("data");
        caller.StepCalls[0].Input.Variables.Should().BeSameAs(variables);
        caller.StepCalls[0].Input.RootInstanceId.Should().Be("root");
        caller.StepCalls[0].Input.IterationIndex.Should().Be("1.0");
    }

    [Theory]
    [InlineData("for")]
    [InlineData("try")]
    [InlineData("fork")]
    [InlineData("wait")]
    [InlineData("listen")]
    public async Task CallsFlowChildForScopeTaskTypesAtDefaultIteration(string taskType)
    {
        JsonObject body = new() { [taskType] = JsonValue.Create(true) };
        JsonArray tasks = new JsonArray { new JsonObject { ["loopTask"] = body } };
        JsonObject children = new() { ["loopTask"] = "loop-task" };
        SingleNodeDefinition definition = Definition(tasks, children);
        RecordingChildCaller caller = new();
        FlowInput input = new(JsonValue.Create("data"), null, "root", null);

        await SequencerRunner.Run(input, definition, caller, UnexpectedTimer);

        caller.FlowCalls.Should().ContainSingle();
        caller.FlowCalls[0].AppId.Should().Be("loop-task");
        caller.FlowCalls[0].InstanceId.Should().Be("root:loop-task:0");
    }

    [Fact]
    public async Task CallsFlowChildAtTheEnclosingIterationIndex()
    {
        JsonObject body = new() { ["for"] = JsonValue.Create(true) };
        JsonArray tasks = new JsonArray { new JsonObject { ["loopTask"] = body } };
        JsonObject children = new() { ["loopTask"] = "loop-task" };
        SingleNodeDefinition definition = Definition(tasks, children);
        RecordingChildCaller caller = new();
        FlowInput input = new(JsonValue.Create("data"), null, "root", "1.0");

        await SequencerRunner.Run(input, definition, caller, UnexpectedTimer);

        caller.FlowCalls[0].InstanceId.Should().Be("root:loop-task:1.0");
    }

    [Fact]
    public async Task PropagatesChildFailureMessageUnchanged()
    {
        JsonArray tasks = JsonNode.Parse("""[ { "a": { "call": "http" } } ]""")!.AsArray();
        JsonObject children = JsonNode.Parse("""{ "a": "child-a" }""")!.AsObject();
        SingleNodeDefinition definition = Definition(tasks, children);
        RecordingChildCaller caller = new();
        caller.StepFailures["child-a"] = new InvalidOperationException("boom from child");
        FlowInput input = new(JsonValue.Create("data"), null, "root", null);

        Func<Task> action = () => SequencerRunner.Run(input, definition, caller, UnexpectedTimer);

        (await action.Should().ThrowAsync<InvalidOperationException>()).Which.Message.Should().Be("boom from child");
    }

    [Fact]
    public async Task CompletesWithInputDataWhenTasksAreEmpty()
    {
        SingleNodeDefinition definition = Definition(new JsonArray(), new JsonObject());
        RecordingChildCaller caller = new();
        FlowInput input = new(JsonValue.Create("unchanged"), null, "root", null);

        JsonNode? result = await SequencerRunner.Run(input, definition, caller, UnexpectedTimer);

        result!.GetValue<string>().Should().Be("unchanged");
        caller.StepCalls.Should().BeEmpty();
        caller.FlowCalls.Should().BeEmpty();
    }

    [Fact]
    public async Task FailsWithAFriendlyConfigMessageWhenATaskHasNoChildAppIdDeclared()
    {
        JsonArray tasks = JsonNode.Parse("""[ { "a": { "call": "http" } } ]""")!.AsArray();
        JsonObject children = new JsonObject();
        SingleNodeDefinition definition = Definition(tasks, children);
        RecordingChildCaller caller = new();
        FlowInput input = new(JsonValue.Create("data"), null, "root", null);

        Func<Task> action = () => SequencerRunner.Run(input, definition, caller, UnexpectedTimer);

        (await action.Should().ThrowAsync<InvalidOperationException>()).Which.Message
            .Should().Be("flow references task 'a', which has no app ID declared in children");
    }

    [Fact]
    public async Task FailsAfterTenThousandSteps()
    {
        JsonArray tasks = JsonNode.Parse("""
            [ { "a": { "call": "http", "then": "b" } }, { "b": { "call": "http", "then": "a" } } ]
            """)!.AsArray();
        JsonObject children = JsonNode.Parse("""{ "a": "child-a", "b": "child-b" }""")!.AsObject();
        SingleNodeDefinition definition = Definition(tasks, children);
        RecordingChildCaller caller = new();
        caller.StepResults["child-a"] = JsonValue.Create("x");
        caller.StepResults["child-b"] = JsonValue.Create("y");
        FlowInput input = new(JsonValue.Create("start"), null, "root", null);

        Func<Task> action = () => SequencerRunner.Run(input, definition, caller, UnexpectedTimer);

        (await action.Should().ThrowAsync<InvalidOperationException>()).Which.Message
            .Should().Be("workflow exceeded 10000 steps; check for a definition loop");
    }

    private static async Task<(string Message, TimeSpan? Requested)> RunNamedTimeout(string entryJson)
    {
        JsonArray tasks = JsonNode.Parse("""[ { "slow": { "call": "http", "timeout": "fastCall" } } ]""")!.AsArray();
        JsonObject children = JsonNode.Parse("""{ "slow": "child-slow" }""")!.AsObject();
        UseResolver resolver = new(new Dictionary<(UseKind, string), string> { [(UseKind.Timeouts, "fastCall")] = entryJson });
        RecordingChildCaller caller = new() { Pending = true };
        TimeSpan? requestedTimeout = null;
        Task CreateTimer(TimeSpan duration, CancellationToken token)
        {
            requestedTimeout = duration;
            return Task.CompletedTask;
        }

        Func<Task> act = () => SequencerRunner.Run(new FlowInput(null, null, "root", null), Definition(tasks, children), caller, CreateTimer, resolver);

        InvalidOperationException failure = (await act.Should().ThrowAsync<InvalidOperationException>()).Which;
        return (failure.Message, requestedTimeout);
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

        Func<Task> act = () => SequencerRunner.Run(new FlowInput(null, null, "root", null), Definition(tasks, children), new RecordingChildCaller(), UnexpectedTimer);

        (await act.Should().ThrowAsync<InvalidOperationException>()).Which.Message
            .Should().Contain("timeouts").And.Contain("fastCall");
    }

    [Fact]
    public async Task KeepsAnInlineTimeoutWorkingWithoutAResolver()
    {
        JsonArray tasks = JsonNode.Parse("""[ { "slow": { "call": "http", "timeout": "PT5S" } } ]""")!.AsArray();
        JsonObject children = JsonNode.Parse("""{ "slow": "child-slow" }""")!.AsObject();
        RecordingChildCaller caller = new() { Pending = true };

        Func<Task> act = () => SequencerRunner.Run(new FlowInput(null, null, "root", null), Definition(tasks, children), caller, (_, _) => Task.CompletedTask);

        (await act.Should().ThrowAsync<InvalidOperationException>()).Which.Message.Should().Be("task 'slow' timed out after PT5S");
    }

    private static SingleNodeDefinition Definition(JsonArray tasks, JsonObject children) =>
        new("order", "order@v1", "order-main", "main", tasks, children, null);

    private static Task UnexpectedTimer(TimeSpan timeout, CancellationToken token) =>
        throw new InvalidOperationException("no timer expected");

    private sealed class RecordingChildCaller : IChildCaller
    {
        public List<(string AppId, FlowInput Input)> StepCalls { get; } = new();

        public List<(string AppId, string InstanceId, FlowInput Input)> FlowCalls { get; } = new();

        public Dictionary<string, JsonNode?> StepResults { get; } = new();

        public Dictionary<string, Exception> StepFailures { get; } = new();

        public bool Pending { get; set; }

        public Task<JsonNode?> CallStep(string appId, FlowInput input)
        {
            StepCalls.Add((appId, input));
            if (StepFailures.TryGetValue(appId, out Exception? failure))
            {
                throw failure;
            }

            return Pending ? new TaskCompletionSource<JsonNode?>().Task
                : Task.FromResult(StepResults.TryGetValue(appId, out JsonNode? result) ? result : input.Data);
        }

        public Task<JsonNode?> CallFlow(string appId, string instanceId, FlowInput input)
        {
            FlowCalls.Add((appId, instanceId, input));
            return Pending ? new TaskCompletionSource<JsonNode?>().Task : Task.FromResult(input.Data);
        }
    }
}
