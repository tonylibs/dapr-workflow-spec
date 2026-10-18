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

    [Theory]
    [InlineData(false, false, false)]
    [InlineData(false, false, true)]
    [InlineData(false, true, false)]
    [InlineData(false, true, true)]
    [InlineData(true, false, false)]
    [InlineData(true, false, true)]
    [InlineData(true, true, false)]
    [InlineData(true, true, true)]
    public async Task ReplayingRecordedHistoryPreservesChildAndTimerOrderingAndOutcome(bool timerWins, bool flow, bool namedTimeout)
    {
        JsonArray tasks = new(
            new JsonObject
            {
                ["a"] = new JsonObject
                {
                    [flow ? "for" : "call"] = flow ? JsonValue.Create(true) : JsonValue.Create("http"),
                    ["timeout"] = namedTimeout ? "fastCall" : "PT5S",
                },
            },
            new JsonObject { ["b"] = new JsonObject { ["call"] = "http" } });
        JsonObject children = JsonNode.Parse("""{ "a": "child-a", "b": "child-b" }""")!.AsObject();
        SingleNodeDefinition definition = new("order", "order@v1", "order-main", "main", tasks, children, null);
        UseResolver resolver = namedTimeout
            ? new(new Dictionary<(UseKind, string), string>
            {
                [(UseKind.Timeouts, "fastCall")] = """{"after":{"seconds":5}}""",
            })
            : UseResolver.Empty;
        FlowDefinitionHolder.Initialize(definition, resolver);

        Dictionary<string, JsonNode?> cannedResults = new()
        {
            ["child-a"] = JsonValue.Create("after-a"),
            ["child-b"] = JsonValue.Create("after-b"),
        };
        FlowInput input = new(JsonValue.Create("start"), null, null, null);
        FlowWorkflow workflow = new();

        try
        {
            FakeWorkflowContext live = new("instance-1", isReplaying: false, cannedResults, history: null, timerWins);
            Task<JsonNode?> liveCall = workflow.RunAsync(live, input);
            JsonNode? liveResult = null;
            if (timerWins)
            {
                Func<Task> action = () => liveCall;
                await action.Should().ThrowAsync<InvalidOperationException>().WithMessage("task 'a' timed out after PT5S");
            }
            else
            {
                liveResult = await liveCall;
                liveResult!.GetValue<string>().Should().Be("after-b");
            }

            // Replay obtains completion state from history, not the live timerWins setting.
            FakeWorkflowContext replay = new("instance-1", isReplaying: true, cannedResults, history: live.History);
            Task<JsonNode?> replayCall = workflow.RunAsync(replay, input);
            if (timerWins)
            {
                Func<Task> action = () => replayCall;
                await action.Should().ThrowAsync<InvalidOperationException>().WithMessage("task 'a' timed out after PT5S");
            }
            else
            {
                (await replayCall)!.ToJsonString().Should().Be(liveResult!.ToJsonString());
            }

            replay.Commands.Should().Equal(live.Commands);
            replay.RemainingCommands.Should().Be(0);
            live.Commands.Should().HaveCount(timerWins ? 2 : 3);
            live.Commands[0].Should().Be(flow ? "childWorkflow:Flow:child-a:instance-1:child-a:0" : "activity:Step:child-a");
            live.Commands[1].Should().Be($"timer:{DateTime.UnixEpoch.AddSeconds(5):O}");
            if (!timerWins)
            {
                live.Commands[2].Should().Be("activity:Step:child-b");
            }
        }
        finally
        {
            FlowDefinitionHolder.Initialize(definition);
        }
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
    private sealed record RecordedCommand(string Signature, JsonNode? Result, bool Pending);

    /// <summary>
    /// A scripted <see cref="WorkflowContext"/> stand-in driven two ways: a "live" pass (<paramref
    /// name="history"/> is <c>null</c>) resolves each call from <paramref name="cannedResults"/> and
    /// records what it did into <see cref="History"/>; a "replay" pass is constructed with that recorded
    /// history and <c>isReplaying: true</c>, and instead of consulting <paramref name="cannedResults"/>
    /// again it dequeues the next recorded command, asserts it matches what's being requested now (so a
    /// non-deterministic branch inside the workflow shows up as a mismatch, not a silent pass), and returns
    /// the result captured during the live pass. This is a hand-rolled fake, not an official Dapr
    /// Workflow replay-testing harness — none ships with the SDK as of 1.18.10.
    /// </summary>
    private sealed class FakeWorkflowContext : WorkflowContext
    {
        private readonly bool isReplaying;
        private readonly bool timerWins;
        private readonly IReadOnlyDictionary<string, JsonNode?> cannedResults;
        private readonly Queue<RecordedCommand>? replayQueue;
        private readonly List<RecordedCommand> recorded = new();

        public FakeWorkflowContext(
            string instanceId,
            bool isReplaying,
            IReadOnlyDictionary<string, JsonNode?> cannedResults,
            IReadOnlyList<RecordedCommand>? history,
            bool timerWins = false)
        {
            InstanceId = instanceId;
            this.isReplaying = isReplaying;
            this.timerWins = timerWins;
            this.cannedResults = cannedResults;
            replayQueue = history is null ? null : new Queue<RecordedCommand>(history);
        }

        public List<string> Commands { get; } = new();

        public IReadOnlyList<RecordedCommand> History => recorded;

        public int RemainingCommands => replayQueue?.Count ?? 0;

        public override string Name => FlowWorkflow.Name;

        public override string InstanceId { get; }

        public override DateTime CurrentUtcDateTime => DateTime.UnixEpoch;

        public override bool IsReplaying => isReplaying;

        public override bool IsPatched(string patchId) => false;

        public override Task<T> CallActivityAsync<T>(string name, object? input, WorkflowTaskOptions? options)
        {
            string appId = options?.TargetAppId ?? throw new InvalidOperationException("test expects a TargetAppId");
            string signature = $"activity:{name}:{appId}";
            RecordedCommand command = Resolve(signature, appId);
            Commands.Add(signature);
            return command.Pending ? new TaskCompletionSource<T>().Task : Task.FromResult((T)(object)command.Result!);
        }

        public override Task CreateTimer(DateTime fireAt, CancellationToken cancellationToken)
        {
            string signature = $"timer:{fireAt:O}";
            RecordedCommand command = Resolve(signature, resultKey: null);
            Commands.Add(signature);

            return command.Pending ? Task.Delay(Timeout.InfiniteTimeSpan, cancellationToken) : Task.CompletedTask;
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
            RecordedCommand command = Resolve(signature, appId);
            Commands.Add(signature);
            return command.Pending ? new TaskCompletionSource<TResult>().Task : Task.FromResult((TResult)(object)command.Result!);
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
        private RecordedCommand Resolve(string signature, string? resultKey)
        {
            if (replayQueue is null)
            {
                JsonNode? result = resultKey is null ? null : cannedResults[resultKey];
                RecordedCommand command = new(signature, result, resultKey is null ? !timerWins : timerWins);
                recorded.Add(command);
                return command;
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

            return next;
        }
    }
}
