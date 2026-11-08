namespace Dws.ScheduleTime;

/// <summary>What a job handler tells Dapr: success (HTTP 200, no retry) or retry (HTTP 500, retried per the failure policy).</summary>
public enum JobOutcome
{
    Success,
    Retry,
}
