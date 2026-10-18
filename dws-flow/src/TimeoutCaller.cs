using System.Text.Json.Nodes;

namespace Dws.Flow;

/// <summary>Races each delegated child call against a durable timer for the task's declared timeout.</summary>
public sealed class TimeoutCaller : ChildCallerDecorator
{
    private readonly string taskName;
    private readonly TimeSpan timeout;
    private readonly Func<TimeSpan, CancellationToken, Task> createTimer;

    public TimeoutCaller(
        IChildCaller caller,
        string taskName,
        TimeSpan timeout,
        Func<TimeSpan, CancellationToken, Task> createTimer) : base(caller)
    {
        this.taskName = taskName;
        this.timeout = timeout;
        this.createTimer = createTimer;
    }

    public override Task<JsonNode?> CallStep(string appId, FlowInput input) =>
        AwaitWithTimeout(Delegate.CallStep(appId, input));

    public override Task<JsonNode?> CallFlow(string appId, string instanceId, FlowInput input) =>
        AwaitWithTimeout(Delegate.CallFlow(appId, instanceId, input));

    private async Task<JsonNode?> AwaitWithTimeout(Task<JsonNode?> call)
    {
        using CancellationTokenSource timerCancellation = new();
        Task timer = createTimer(timeout, timerCancellation.Token);
        Task winner = await Task.WhenAny(call, timer);
        if (winner == timer)
        {
            throw new InvalidOperationException($"task '{taskName}' timed out after {TaskTimeout.Format(timeout)}");
        }

        timerCancellation.Cancel();
        return await call;
    }
}
