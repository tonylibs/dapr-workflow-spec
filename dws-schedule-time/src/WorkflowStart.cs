using System.Text.Json.Nodes;

namespace Dws.ScheduleTime;

/// <summary>The workflow a trigger starts and the Dapr app-id of its active version's orchestrator.</summary>
public sealed record StartTarget(string Workflow, string AppId);

/// <summary>One start of the workflow start contract: target, deterministic instance ID and input.</summary>
public sealed record TriggerStart(StartTarget Target, string InstanceId, JsonObject Input)
{
    public static TriggerStart ForTimeTrigger(StartTarget target, string instanceId) => new(target, instanceId, []);
}

/// <summary>How a start ended.</summary>
public enum StartOutcome
{
    Started,
    AlreadyExisted,
    RetryableFailure,
    PermanentFailure,
}

/// <summary>Result of <see cref="IWorkflowStarter.StartAsync"/>; failures carry the HTTP status when there was one.</summary>
public sealed record StartResult(StartOutcome Outcome, int? HttpStatus = null, string? Detail = null)
{
    public bool Succeeded => Outcome is StartOutcome.Started or StartOutcome.AlreadyExisted;

    public bool Retryable => Outcome == StartOutcome.RetryableFailure;
}

/// <summary>How a status lookup ended.</summary>
public enum StatusOutcome
{
    Found,
    NotFound,
    RetryableFailure,
    PermanentFailure,
}

/// <summary>Result of <see cref="IWorkflowStarter.GetStatusAsync"/>; <c>RuntimeStatus</c> is set when the instance was found.</summary>
public sealed record StatusResult(StatusOutcome Outcome, string? RuntimeStatus = null, int? HttpStatus = null, string? Detail = null);

/// <summary>The scheduler's single seam for starting workflow instances and reading their status.</summary>
public interface IWorkflowStarter
{
    Task<StartResult> StartAsync(TriggerStart start, CancellationToken cancellationToken);

    Task<StatusResult> GetStatusAsync(StartTarget target, string instanceId, CancellationToken cancellationToken);
}
