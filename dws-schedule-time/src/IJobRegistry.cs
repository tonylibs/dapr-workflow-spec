namespace Dws.ScheduleTime;

/// <summary>A job as read back from the Dapr Scheduler.</summary>
public sealed record RegisteredJob(string Name, string? Schedule, string? Payload)
{
    public JobPayload? ParsedPayload => JobPayload.TryParse(Payload);
}

/// <summary>A job to register: either a recurring <paramref name="Expression"/> or a one-shot <paramref name="DueTime"/>.</summary>
public sealed record JobRequest(string Name, string? Expression, DateTimeOffset? DueTime, string Payload);

/// <summary>The seam around the Dapr Jobs API, so reconcile and the handlers run without a sidecar.</summary>
public interface IJobRegistry
{
    Task<RegisteredJob?> GetAsync(string name, CancellationToken cancellationToken);

    Task<IReadOnlyList<RegisteredJob>> ListAsync(CancellationToken cancellationToken);

    /// <summary>Registers a job; with <paramref name="overwrite"/> false returns false (and changes nothing) when the name already exists.</summary>
    Task<bool> ScheduleAsync(JobRequest job, bool overwrite, CancellationToken cancellationToken);

    /// <summary>Deletes a job; a job that does not exist is not an error.</summary>
    Task DeleteAsync(string name, CancellationToken cancellationToken);
}
