using Microsoft.Extensions.Logging;

namespace Dws.ScheduleTime;

/// <summary>Routes a Dapr job callback to its handler by the job-name prefix, after the first full cluster list.</summary>
public sealed class JobDispatcher(
    ScheduledJobHandler scheduled,
    AfterLifecycle after,
    IClusterState cluster,
    TimeProvider time,
    TimeSpan syncWait,
    ILogger<JobDispatcher> logger)
{
    public async Task<JobOutcome> DispatchAsync(string jobName, string payload, CancellationToken cancellationToken)
    {
        if (!JobSpecs.TryParseName(jobName, out string prefix, out string workflow))
        {
            logger.LogWarning("Ignoring callback for unknown job {Job}", jobName);
            return JobOutcome.Success;
        }

        // The fire key comes from the delivery time, so read it before any wait: a cron fire delivered at
        // 12:00:58 must keep its 12:00 key even if the first list only completes at 12:01:03.
        DateTimeOffset deliveredAt = time.GetUtcNow();
        if (!cluster.Synced && !await cluster.WaitForSyncAsync(syncWait, cancellationToken))
        {
            logger.LogWarning("Cluster view still not listed after {Wait}; job {Job} will be retried", syncWait, jobName);
        }

        return prefix switch
        {
            JobSpecs.ScheduledPrefix => await scheduled.HandleAsync(workflow, payload, deliveredAt, cancellationToken),
            JobSpecs.AfterPollPrefix => await after.PollAsync(workflow, cancellationToken),
            _ => await after.StartNextAsync(workflow, cancellationToken),
        };
    }
}
