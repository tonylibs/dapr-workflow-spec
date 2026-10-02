using System.Text.Json.Nodes;
using Dapr.Workflow;
using Dws.Flow;
using FluentAssertions;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace Dws.Flow.Tests;

public sealed class ReplayTests
{
    private static readonly string SourceDirectory = Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "src");

    private static readonly string[] NonDeterministicApis = ["DateTime.Now", "DateTime.UtcNow", "Guid.NewGuid", "new Random("];

    [Fact]
    public async Task ReplayingRecordedHistoryReissuesTheSameCommandsAndResult()
    {
        JsonArray tasks = JsonNode.Parse("""[ { "a": { "call": "http" } }, { "b": { "call": "http" } } ]""")!.AsArray();
        JsonObject children = JsonNode.Parse("""{ "a": "child-a", "b": "child-b" }""")!.AsObject();
        SingleNodeDefinition definition = new("order", "order@v1", "order-main", "main", tasks, children, null);
        FlowDefinitionHolder.Initialize(definition);

        Dictionary<string, JsonNode?> cannedResults = new()
        {
            ["child-a"] = JsonValue.Create("after-a"),
            ["child-b"] = JsonValue.Create("after-b"),
        };
        FlowInput input = new(JsonValue.Create("start"), null, null, null);
        FlowWorkflow workflow = new();

        FakeWorkflowContext live = new("instance-1", isReplaying: false, cannedResults);
        JsonNode? liveResult = await workflow.RunAsync(live, input);

        FakeWorkflowContext replay = new("instance-1", isReplaying: true, cannedResults);
        JsonNode? replayResult = await workflow.RunAsync(replay, input);

        replay.Commands.Should().Equal(live.Commands);
        replayResult!.ToJsonString().Should().Be(liveResult!.ToJsonString());
    }

    [Fact]
    public void WorkflowSourceUsesNoNonDeterministicApis()
    {
        IEnumerable<(string Path, string Api)> offenders =
            from path in Directory.EnumerateFiles(Path.GetFullPath(SourceDirectory), "*.cs")
            let text = File.ReadAllText(path)
            from api in NonDeterministicApis
            where text.Contains(api, StringComparison.Ordinal)
            select (path, api);

        offenders.Should().BeEmpty();
    }

    /// <summary>A recording, scripted <see cref="WorkflowContext"/> stand-in: it never actually awaits
    /// real time or I/O, it returns the canned result configured for each app ID and records every issued
    /// command so a live run and a replay run can be compared for identical commands and result.</summary>
    private sealed class FakeWorkflowContext : WorkflowContext
    {
        private readonly bool isReplaying;
        private readonly IReadOnlyDictionary<string, JsonNode?> cannedResults;

        public FakeWorkflowContext(string instanceId, bool isReplaying, IReadOnlyDictionary<string, JsonNode?> cannedResults)
        {
            InstanceId = instanceId;
            this.isReplaying = isReplaying;
            this.cannedResults = cannedResults;
        }

        public List<string> Commands { get; } = new();

        public override string Name => FlowWorkflow.Name;

        public override string InstanceId { get; }

        public override DateTime CurrentUtcDateTime => DateTime.UnixEpoch;

        public override bool IsReplaying => isReplaying;

        public override bool IsPatched(string patchId) => false;

        public override Task<T> CallActivityAsync<T>(string name, object? input, WorkflowTaskOptions? options)
        {
            string appId = options?.TargetAppId ?? throw new InvalidOperationException("test expects a TargetAppId");
            Commands.Add($"activity:{name}:{appId}");
            return Task.FromResult((T)(object)cannedResults[appId]!);
        }

        public override Task CreateTimer(DateTime fireAt, CancellationToken cancellationToken) => Task.CompletedTask;

        public override Task<T> WaitForExternalEventAsync<T>(string eventName, CancellationToken cancellationToken) =>
            throw new NotSupportedException("ReplayTests does not exercise external events");

        public override void SendEvent(string instanceId, string eventName, object payload)
        {
        }

        public override void SetCustomStatus(object? customStatus)
        {
        }

        public override Task<TResult> CallChildWorkflowAsync<TResult>(string workflowName, object? input, ChildWorkflowTaskOptions? options)
        {
            string appId = options?.TargetAppId ?? throw new InvalidOperationException("test expects a TargetAppId");
            string instanceId = options?.InstanceId ?? throw new InvalidOperationException("test expects an InstanceId");
            Commands.Add($"childWorkflow:{workflowName}:{appId}:{instanceId}");
            return Task.FromResult((TResult)(object)cannedResults[appId]!);
        }

        public override ILogger CreateReplaySafeLogger(string name) => NullLogger.Instance;

        public override ILogger CreateReplaySafeLogger(Type type) => NullLogger.Instance;

        public override ILogger CreateReplaySafeLogger<T>() => NullLogger.Instance;

        public override void ContinueAsNew(object? newInput, bool preserveUnprocessedEvents)
        {
        }

        public override Guid NewGuid() => Guid.Empty;

        public override PropagatedHistory GetPropagatedHistory() => new(Array.Empty<PropagatedHistoryEvent>());
    }
}
