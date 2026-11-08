using Microsoft.Extensions.Logging;

namespace Dws.ScheduleTime;

/// <summary>Routes a Dapr job callback to its handler by the job-name prefix.</summary>
public sealed class JobDispatcher(ScheduledJobHandler scheduled, AfterLifecycle after, ILogger<JobDispatcher> logger)
{
    public Task<JobOutcome> DispatchAsync(string jobName, string payload, CancellationToken cancellationToken)
    {
        if (!JobSpecs.TryParseName(jobName, out string prefix, out string workflow))
        {
            logger.LogWarning("Ignoring callback for unknown job {Job}", jobName);
            return Task.FromResult(JobOutcome.Success);
        }

        return prefix switch
        {
            JobSpecs.ScheduledPrefix => scheduled.HandleAsync(workflow, payload, cancellationToken),
            JobSpecs.AfterPollPrefix => after.PollAsync(workflow, cancellationToken),
            _ => after.StartNextAsync(workflow, cancellationToken),
        };
    }
}
