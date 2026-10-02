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

    /// <summary>APIs that would make the workflow's own decision-making non-deterministic across replay.
    /// <c>Program.cs</c> is excluded: it is process bootstrap (reads the definition path once at startup,
    /// before any workflow instance runs) and is never on a replayed execution path.</summary>
    private static readonly string[] NonDeterministicApis =
    [
        "DateTime.Now", "DateTime.UtcNow", "DateTime.Today", "Guid.NewGuid", "new Random(", "Random.Shared",
        "Task.Delay", "Environment.", "Stopwatch",
    ];

    private static readonly string[] ExcludedFiles = ["Program.cs"];

    [Fact]
    public async Task ReplayingRecordedHistoryReissuesTheSameCommandsAndResultIncludingATimedOutTimerPath()
    {
        JsonArray tasks = JsonNode.Parse("""
            [ { "a": { "call": "http", "timeout": "PT5S" } }, { "b": { "call": "http" } } ]
            """)!.AsArray();
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

        // Live pass: the workflow actually decides what to call and records every command it issues
        // (activity calls, child workflow calls, and timers) as its own, ordinary execution history.
        FakeWorkflowContext live = new("instance-1", isReplaying: false, cannedResults, history: null);
        JsonNode? liveResult = await workflow.RunAsync(live, input);

        // Replay pass: constructed with exactly the history the live pass recorded, with
        // IsReplaying == true. Each call is served from that recorded history in order rather than
        // recomputed from the canned-results lookup; a divergence (different call, different order, or
        // an extra/missing call) fails the dequeue assertion below instead of silently passing.
        FakeWorkflowContext replay = new("instance-1", isReplaying: true, cannedResults, history: live.History);
        JsonNode? replayResult = await workflow.RunAsync(replay, input);

        replay.Commands.Should().Equal(live.Commands);
        replayResult!.ToJsonString().Should().Be(liveResult!.ToJsonString());
        live.Commands.Should().Contain(command => command.StartsWith("timer:", StringComparison.Ordinal),
            "the timed-out task must exercise the timer path, not just the activity path");
    }

    [Fact]
    public void WorkflowSourceUsesNoNonDeterministicApis()
    {
        IEnumerable<(string Path, string Api)> offenders =
            from path in Directory.EnumerateFiles(Path.GetFullPath(SourceDirectory), "*.cs")
            where !ExcludedFiles.Contains(Path.GetFileName(path))
            let text = File.ReadAllText(path)
            from api in NonDeterministicApis
            where text.Contains(api, StringComparison.Ordinal)
            select (path, api);

        offenders.Should().BeEmpty();
    }

    /// <summary>One command the fake context issued or satisfied, with enough identity (kind + the
    /// relevant ids) to assert replay reissues precisely the same sequence, and the result it produced so
    /// replay can serve that exact result back out of history instead of recomputing it.</summary>
    private sealed record RecordedCommand(string Signature, JsonNode? Result);

    /// <summary>
    /// A scripted <see cref="WorkflowContext"/> stand-in driven two ways: a "live" pass (<paramref
    /// name="history"/> is <c>null</c>) resolves each call from <paramref name="cannedResults"/> and
    /// records what it did into <see cref="History"/>; a "replay" pass is constructed with that recorded
    /// history and <c>isReplaying: true</c>, and instead of consulting <paramref name="cannedResults"/>
    /// again it dequeues the next recorded command, asserts it matches what's being requested now (so a
    /// non-deterministic branch inside the workflow shows up as a mismatch, not a silent pass), and returns
    /// the result captured during the live pass. This is a hand-rolled fake, not an official Dapr
    /// Workflow replay-testing harness — none ships with the SDK as of 1.18.5.
    /// </summary>
    private sealed class FakeWorkflowContext : WorkflowContext
    {
        private readonly bool isReplaying;
        private readonly IReadOnlyDictionary<string, JsonNode?> cannedResults;
        private readonly Queue<RecordedCommand>? replayQueue;
        private readonly List<RecordedCommand> recorded = new();

        public FakeWorkflowContext(
            string instanceId,
            bool isReplaying,
            IReadOnlyDictionary<string, JsonNode?> cannedResults,
            IReadOnlyList<RecordedCommand>? history)
        {
            InstanceId = instanceId;
            this.isReplaying = isReplaying;
            this.cannedResults = cannedResults;
            replayQueue = history is null ? null : new Queue<RecordedCommand>(history);
        }

        public List<string> Commands { get; } = new();

        public IReadOnlyList<RecordedCommand> History => recorded;

        public override string Name => FlowWorkflow.Name;

        public override string InstanceId { get; }

        public override DateTime CurrentUtcDateTime => DateTime.UnixEpoch;

        public override bool IsReplaying => isReplaying;

        public override bool IsPatched(string patchId) => false;

        public override Task<T> CallActivityAsync<T>(string name, object? input, WorkflowTaskOptions? options)
        {
            string appId = options?.TargetAppId ?? throw new InvalidOperationException("test expects a TargetAppId");
            string signature = $"activity:{name}:{appId}";
            JsonNode? result = Resolve(signature, appId);
            Commands.Add(signature);
            return Task.FromResult((T)(object)result!);
        }

        public override Task CreateTimer(DateTime fireAt, CancellationToken cancellationToken)
        {
            string signature = $"timer:{fireAt:O}";
            Resolve(signature, resultKey: null);
            Commands.Add(signature);

            // A durable timer never actually fires before the race's winning call cancels it; modelling
            // it as a task that only completes on cancellation keeps the call deterministically winning
            // `Task.WhenAny` in both the live and replay passes.
            return Task.Delay(Timeout.InfiniteTimeSpan, cancellationToken);
        }

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
            string signature = $"childWorkflow:{workflowName}:{appId}:{instanceId}";
            JsonNode? result = Resolve(signature, appId);
            Commands.Add(signature);
            return Task.FromResult((TResult)(object)result!);
        }

        public override ILogger CreateReplaySafeLogger(string name) => NullLogger.Instance;

        public override ILogger CreateReplaySafeLogger(Type type) => NullLogger.Instance;

        public override ILogger CreateReplaySafeLogger<T>() => NullLogger.Instance;

        public override void ContinueAsNew(object? newInput, bool preserveUnprocessedEvents)
        {
        }

        public override Guid NewGuid() => Guid.Empty;

        public override PropagatedHistory GetPropagatedHistory() => new(Array.Empty<PropagatedHistoryEvent>());

        /// <summary>Live pass: looks the result up by <paramref name="resultKey"/> (an app ID, or
        /// <c>null</c> for a timer with no result) and records the command. Replay pass: dequeues the
        /// next recorded command and asserts its signature matches what's being requested now, failing
        /// loudly on any divergence instead of recomputing from <see cref="cannedResults"/> again.</summary>
        private JsonNode? Resolve(string signature, string? resultKey)
        {
            if (replayQueue is null)
            {
                JsonNode? result = resultKey is null ? null : cannedResults[resultKey];
                recorded.Add(new RecordedCommand(signature, result));
                return result;
            }

            if (replayQueue.Count == 0)
            {
                throw new InvalidOperationException($"replay issued '{signature}' but recorded history has no more commands");
            }

            RecordedCommand next = replayQueue.Dequeue();
            if (next.Signature != signature)
            {
                throw new InvalidOperationException(
                    $"replay issued '{signature}' but recorded history expected '{next.Signature}' next");
            }

            return next.Result;
        }
    }
}
