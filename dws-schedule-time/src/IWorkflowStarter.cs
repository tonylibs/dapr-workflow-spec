namespace Dws.ScheduleTime;

/// <summary>The result class of a start attempt.</summary>
public enum StartOutcome
{
    Started,
    Retryable,
    NonRetryable,
}

/// <summary>The outcome of a start attempt plus a short cause for the logs.</summary>
public sealed record StartResult(StartOutcome Outcome, string Detail);

/// <summary>What the scheduler needs to know about a workflow instance.</summary>
public enum InstanceStatus
{
    Running,
    Terminal,
    NotFound,
    Error,
}

/// <summary>The runtime-neutral start contract: start a workflow instance with a given ID, and read its status.</summary>
public interface IWorkflowStarter
{
    Task<StartResult> StartAsync(string appId, string workflow, string instanceId, string inputJson, CancellationToken cancellationToken);

    Task<InstanceStatus> GetStatusAsync(string appId, string instanceId, CancellationToken cancellationToken);
}
