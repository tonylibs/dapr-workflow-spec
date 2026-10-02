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

        JsonNode? result = await SequencerRunner.Run(input, definition, caller);

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

        await SequencerRunner.Run(input, definition, caller);

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

        await SequencerRunner.Run(input, definition, caller);

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

        await SequencerRunner.Run(input, definition, caller);

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

        Func<Task> action = () => SequencerRunner.Run(input, definition, caller);

        (await action.Should().ThrowAsync<InvalidOperationException>()).Which.Message.Should().Be("boom from child");
    }

    [Fact]
    public async Task CompletesWithInputDataWhenTasksAreEmpty()
    {
        SingleNodeDefinition definition = Definition(new JsonArray(), new JsonObject());
        RecordingChildCaller caller = new();
        FlowInput input = new(JsonValue.Create("unchanged"), null, "root", null);

        JsonNode? result = await SequencerRunner.Run(input, definition, caller);

        result!.GetValue<string>().Should().Be("unchanged");
        caller.StepCalls.Should().BeEmpty();
        caller.FlowCalls.Should().BeEmpty();
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

        Func<Task> action = () => SequencerRunner.Run(input, definition, caller);

        (await action.Should().ThrowAsync<InvalidOperationException>()).Which.Message
            .Should().Be("workflow exceeded 10000 steps; check for a definition loop");
    }

    private static SingleNodeDefinition Definition(JsonArray tasks, JsonObject children) =>
        new("order", "order@v1", "order-main", "main", tasks, children, null);

    private sealed class RecordingChildCaller : IChildCaller
    {
        public List<(string AppId, FlowInput Input)> StepCalls { get; } = new();

        public List<(string AppId, string InstanceId, FlowInput Input)> FlowCalls { get; } = new();

        public Dictionary<string, JsonNode?> StepResults { get; } = new();

        public Dictionary<string, Exception> StepFailures { get; } = new();

        public Task<JsonNode?> CallStep(string appId, FlowInput input)
        {
            StepCalls.Add((appId, input));
            if (StepFailures.TryGetValue(appId, out Exception? failure))
            {
                throw failure;
            }

            return Task.FromResult(StepResults.TryGetValue(appId, out JsonNode? result) ? result : input.Data);
        }

        public Task<JsonNode?> CallFlow(string appId, string instanceId, FlowInput input)
        {
            FlowCalls.Add((appId, instanceId, input));
            return Task.FromResult(input.Data);
        }

        public async Task<(bool TimedOut, JsonNode? Result)> WithTimeout(TimeSpan timeout, Task<JsonNode?> call) => (false, await call);
    }
}
